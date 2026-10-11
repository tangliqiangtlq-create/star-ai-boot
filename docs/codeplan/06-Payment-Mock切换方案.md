# Phase 1 · Payment Mock → 真实渠道切换方案

> 文件：`docs/codeplan/06-Payment-Mock切换方案.md`
> 场景：Phase 1 先用 Mock 跑通全链路，Payment 渠道（微信 Native / Stripe Checkout / 其他）确定后 Step 5 替换
> **核心目标：接口签名完全不变，调用方（stream / creator / 前端）无感知**

---

## 一、当前架构（Mock 模式 · Phase 1 Step 0 启用）

```
PaymentClient (接口 · 固定签名)
├── MockPaymentServiceImpl   ← @ConditionalOnProperty("payment.mock.enabled=true")
│   ├── createOrder()    → 直接落库 ai_payment_order(status=paid)
│   ├── query()          → 查库返回 paid
│   ├── close()          → 关单
│   ├── refund()         → 退款
│   └── 发 MQ payment.success  ← 异步立即发（3s 后）
│
├── WechatNativePayment      ← Phase 2 启用
│   ├── 调微信统一下单 API   → 返回 code_url（二维码）
│   └── 微信回调 → /internal/payment/callback/wechat
│
└── StripeCheckoutPayment    ← Phase 2 启用
    ├── 创建 Stripe Session → 返回 checkout.session.url
    └── Stripe Webhook → /internal/payment/callback/stripe
```

**切换只需改一个 Nacos 配置**：`payment.mock.enabled` 从 `true` → `false`

---

## 二、PaymentClient 接口定义（固定，永不修改）

位置：`star-ai-module-payment-api/src/main/java/org/jeecg/payment/api/PaymentClient.java`

```java
package org.jeecg.payment.api;

import org.jeecg.common.api.dto.R;
import org.jeecg.payment.api.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * Payment 公共服务 Feign Client（跨模块调用入口）
 * 接口签名固定：Phase 1 Mock → Phase 2 真实渠道，实现层替换，调用方无感
 */
@FeignClient(name = "star-ai-module-payment", fallbackFactory = PaymentClientFallbackFactory.class)
public interface PaymentClient {

    /**
     * 创建支付订单
     * @param req 业务方（stream / creator）创建订单请求
     * @return 支付订单号 + 支付方式（Mock 模式直接返回 paid）+ 支付 URL（真实模式）
     */
    @PostMapping("/payment/orders")
    R<CreateOrderResp> createOrder(@RequestBody CreateOrderReq req);

    /**
     * 查询订单状态
     */
    @GetMapping("/payment/orders/{orderNo}")
    R<OrderQueryResp> query(@PathVariable String orderNo);

    /**
     * 关闭订单
     */
    @PostMapping("/payment/orders/{orderNo}/close")
    R<?> close(@PathVariable String orderNo);

    /**
     * 退款（真实渠道调渠道 SDK 退款接口）
     */
    @PostMapping("/payment/orders/refund")
    R<RefundResp> refund(@RequestBody RefundReq req);
}
```

### 请求/响应 DTO（固定）

```java
// 创建订单请求（stream / creator 调 PaymentClient.createOrder(req)）
@Data
public class CreateOrderReq {
    /** 业务订单号（stream_purchase / creator_withdrawal 的业务号）*/
    private String businessOrderNo;
    /** 业务类型：STREAM_EPISODE_PURCHASE / STREAM_COLLECTION_UNLOCK / STREAM_CREATOR_GLOBAL_UNLOCK / CREATOR_WITHDRAWAL */
    private String businessType;
    /** 业务方：STREAM / CREATOR */
    private String businessModule;
    /** 用户 ID */
    private Long userId;
    /** 金额（元） */
    private BigDecimal amount;
    /** 币种：CNY / USD */
    private String currency = "CNY";
    /** 支付渠道（可选，不传由服务端选）：WECHAT_NATIVE / STRIPE_CHECKOUT */
    private String channelType;
    /** 订单超时时间（分钟） */
    private Integer expireMinutes = 30;
    /** 商品描述 */
    private String subject;
}

// 创建订单响应
@Data
public class CreateOrderResp {
    /** 支付中心订单号（全局唯一 P 开头） */
    private String orderNo;
    /** 状态：CREATED / PAYING / PAID / CLOSED / REFUNDED
     *  Mock 模式直接返回 PAID
     *  真实模式返回 CREATED，等第三方回调 */
    private String status;
    /** 支付 URL（Mock 模式为 null，真实模式为 code_url / checkout_url） */
    private String payUrl;
    /** 支付二维码图片 URL（微信 Native 模式） */
    private String qrCodeUrl;
    /** 渠道类型 */
    private String channelType;
}
```

---

## 三、Mock 实现（Phase 1 Step 0 起用）

位置：`star-ai-module-payment-biz/src/main/java/org/jeecg/payment/service/impl/MockPaymentServiceImpl.java`

```java
@Slf4j
@Service
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "true", matchIfMissing = true)
public class MockPaymentServiceImpl implements PaymentClient {

    @Autowired private AiPaymentOrderMapper orderMapper;
    @Autowired private RabbitTemplate rabbit;
    
    @Override
    @Transactional
    public R<CreateOrderResp> createOrder(CreateOrderReq req) {
        // 1. 生成 orderNo（雪花前缀 P）
        String orderNo = "P" + IdWorker.nextId();
        
        // 2. 落库 ai_payment_order（直接 PAID，跳过第三方支付）
        AiPaymentOrder order = new AiPaymentOrder();
        order.setOrderNo(orderNo);
        order.setBusinessOrderNo(req.getBusinessOrderNo());
        order.setBusinessType(req.getBusinessType());
        order.setBusinessModule(req.getBusinessModule());
        order.setUserId(req.getUserId());
        order.setAmount(req.getAmount());
        order.setCurrency(req.getCurrency());
        order.setChannelType("MOCK");
        order.setStatus("PAID");  // ⚠️ 直接跳过 CREATED → PAYING → PAID
        order.setPaidAt(LocalDateTime.now());
        order.setPaidFee(BigDecimal.ZERO);
        order.setNetAmount(req.getAmount());
        order.setSubject(req.getSubject());
        order.setExpireAt(LocalDateTime.now().plusMinutes(req.getExpireMinutes()));
        orderMapper.insert(order);
        
        // 3. 异步发 MQ payment.success（模拟第三方回调）
        // 用 @Async 延迟 3s，让调用方有机会拿到 CreateOrderResp
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(3000); } catch (InterruptedException ignored) {}
            PaymentSuccessEvent evt = new PaymentSuccessEvent();
            evt.setEventId(UUID.randomUUID().toString());
            evt.setEventType("payment.success");
            evt.setTimestamp(Instant.now());
            evt.setPayload(Map.of(
                "order_no", orderNo,
                "user_id", req.getUserId(),
                "amount", req.getAmount()
            ));
            rabbit.convertAndSend("x.direct.payment", "payment.success", evt);
            log.info("[Mock] payment.success MQ sent, orderNo={}", orderNo);
        });
        
        // 4. 返回
        CreateOrderResp resp = new CreateOrderResp();
        resp.setOrderNo(orderNo);
        resp.setStatus("PAID");
        resp.setChannelType("MOCK");
        // Mock 模式 payUrl 为空 —— 前端不走任何支付页面
        return R.OK(resp);
    }
    
    @Override
    public R<OrderQueryResp> query(String orderNo) {
        AiPaymentOrder order = orderMapper.selectByOrderNo(orderNo);
        OrderQueryResp resp = new OrderQueryResp();
        resp.setOrderNo(order.getOrderNo());
        resp.setStatus(order.getStatus());
        resp.setPaidAt(order.getPaidAt());
        resp.setAmount(order.getAmount());
        return R.OK(resp);
    }
    
    @Override
    @Transactional
    public R<?> close(String orderNo) {
        AiPaymentOrder order = orderMapper.selectByOrderNo(orderNo);
        if ("PAID".equals(order.getStatus())) return R.error("已支付，不能关闭");
        order.setStatus("CLOSED");
        orderMapper.updateById(order);
        return R.OK();
    }
    
    @Override
    @Transactional
    public R<RefundResp> refund(RefundReq req) {
        AiPaymentOrder order = orderMapper.selectByOrderNo(req.getOrderNo());
        order.setStatus("REFUNDED");
        orderMapper.updateById(order);
        
        RefundResp resp = new RefundResp();
        resp.setRefundNo("R" + IdWorker.nextId());
        resp.setStatus("SUCCESS");
        return R.OK(resp);
    }
}
```

---

## 四、真实渠道实现（Phase 2 Step 5 启用）

### 配置（Nacos）

```yaml
# application-prod.yml（真实渠道）
payment:
  mock:
    enabled: false
  wechat:
    appId: wx_xxx
    mchId: 16xxxxx
    apiKey: xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
    notifyUrl: https://your-domain.com/internal/payment/callback/wechat
  stripe:
    secretKey: sk_live_xxxxxxx
    webhookSecret: whsec_xxxxxxxx
    notifyUrl: https://your-domain.com/internal/payment/callback/stripe
```

### WechatNativePayment 实现

```java
@Service
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "false")
@Primary
public class WechatNativePayment implements PaymentClient {
    
    @Value("${payment.wechat.appId}") private String appId;
    @Value("${payment.wechat.mchId}") private String mchId;
    @Value("${payment.wechat.apiKey}") private String apiKey;
    @Value("${payment.wechat.notifyUrl}") private String notifyUrl;
    
    @Override
    @Transactional
    public R<CreateOrderResp> createOrder(CreateOrderReq req) {
        String orderNo = "P" + IdWorker.nextId();
        
        // 1. 落库 CREATED
        AiPaymentOrder order = new AiPaymentOrder();
        order.setOrderNo(orderNo);
        order.setStatus("CREATED");
        order.setChannelType("WECHAT_NATIVE");
        order.setExpireAt(LocalDateTime.now().plusMinutes(req.getExpireMinutes()));
        orderMapper.insert(order);
        
        // 2. 调微信统一下单 API
        // POST https://api.mch.weixin.qq.com/pay/unifiedorder
        WechatUnifiedOrderRequest wechatReq = new WechatUnifiedOrderRequest();
        wechatReq.setAppid(appId);
        wechatReq.setMchId(mchId);
        wechatReq.setOutTradeNo(orderNo);
        wechatReq.setTotalFee(req.getAmount().multiply(BigDecimal.valueOf(100)).intValue()); // 分
        wechatReq.setNotifyUrl(notifyUrl);
        wechatReq.setTradeType("NATIVE");
        // ... XML 签名 + HTTP POST
        
        // 3. 拿到 code_url（二维码内容）
        String codeUrl = wechatResp.getCodeUrl();
        
        // 4. 生成二维码图片 URL（存 MinIO stream-qrcode bucket）
        String qrCodeUrl = generateQrcodeToMinio(codeUrl, orderNo);
        
        // 5. 回填
        order.setChannelTradeNo(wechatResp.getTransactionId());
        order.setStatus("PAYING");
        order.setPayUrl(qrCodeUrl);
        orderMapper.updateById(order);
        
        // 6. 返回（真实模式状态是 CREATED，不是 PAID）
        CreateOrderResp resp = new CreateOrderResp();
        resp.setOrderNo(orderNo);
        resp.setStatus("CREATED");  // ⚠️ 和 Mock 不同！
        resp.setPayUrl(qrCodeUrl);
        resp.setQrCodeUrl(qrCodeUrl);
        resp.setChannelType("WECHAT_NATIVE");
        return R.OK(resp);
    }
}
```

### StripeCheckoutPayment 实现

```java
@Service
@ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "false")
public class StripeCheckoutPayment implements PaymentClient {
    
    @Override
    @Transactional
    public R<CreateOrderResp> createOrder(CreateOrderReq req) {
        // 1. 落库 CREATED
        String orderNo = "P" + IdWorker.nextId();
        AiPaymentOrder order = ...;  // CREATED → PAYING
        
        // 2. Stripe Checkout Session
        Session session = Session.create(SessionCreateParams.builder()
            .addLineItem(SessionCreateParams.LineItem.builder()
                .setPriceData(...)
                .setQuantity(1L).build())
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl("https://your-domain.com/pay/success?order_no=" + orderNo)
            .setCancelUrl("https://your-domain.com/pay/cancel")
            .build());
        
        // 3. 回填 + 返回
        order.setPayUrl(session.getUrl());
        CreateOrderResp resp = new CreateOrderResp();
        resp.setOrderNo(orderNo);
        resp.setStatus("CREATED");
        resp.setPayUrl(session.getUrl());  // Stripe Checkout URL
        resp.setChannelType("STRIPE_CHECKOUT");
        return R.OK(resp);
    }
}
```

### 统一回调 Controller（真实渠道）

```java
@RestController
@RequestMapping("/internal/payment/callback")
@Slf4j
public class PaymentCallbackController {
    
    @Autowired private AiPaymentOrderMapper orderMapper;
    @Autowired private RabbitTemplate rabbit;
    
    @PostMapping("/wechat")
    public String wechatCallback(@RequestBody String xml) {
        // 1. XML 解析 + 验签（用 apiKey + MD5）
        WechatNotify notify = WechatXmlParser.parse(xml);
        if (!WechatSigner.verify(notify, apiKey)) return WechatXmlBuilder.fail();
        
        // 2. 查 ai_payment_order
        AiPaymentOrder order = orderMapper.selectByOrderNo(notify.getOutTradeNo());
        
        // 3. 幂等：非 CREATED/PAYING 直接返回成功
        if (!"CREATED".equals(order.getStatus()) && !"PAYING".equals(order.getStatus())) {
            return WechatXmlBuilder.success();
        }
        
        // 4. 更新 PAID
        order.setStatus("PAID");
        order.setPaidAt(LocalDateTime.now());
        order.setPaidFee(new BigDecimal(notify.getTotalFee()).divide(BigDecimal.valueOf(100)));
        orderMapper.updateById(order);
        
        // 5. 发 MQ payment.success
        PaymentSuccessEvent evt = new PaymentSuccessEvent();
        evt.setEventType("payment.success");
        evt.setPayload(Map.of("order_no", order.getOrderNo()));
        rabbit.convertAndSend("x.direct.payment", "payment.success", evt);
        
        return WechatXmlBuilder.success();
    }
    
    @PostMapping("/stripe")
    public ResponseEntity<?> stripeCallback(@RequestBody String body, 
                                             @RequestHeader("Stripe-Signature") String sig) {
        // 1. Stripe Webhook 验签
        Event event = Webhook.constructEvent(body, sig, webhookSecret);
        
        if ("checkout.session.completed".equals(event.getType())) {
            Session session = (Session) event.getData().getObject();
            String orderNo = session.getMetadata().get("order_no");
            
            AiPaymentOrder order = orderMapper.selectByOrderNo(orderNo);
            if (!"PAID".equals(order.getStatus())) {  // 幂等
                order.setStatus("PAID");
                order.setPaidAt(LocalDateTime.now());
                orderMapper.updateById(order);
                
                PaymentSuccessEvent evt = new PaymentSuccessEvent();
                evt.setEventType("payment.success");
                evt.setPayload(Map.of("order_no", orderNo));
                rabbit.convertAndSend("x.direct.payment", "payment.success", evt);
            }
        }
        return ResponseEntity.ok().build();
    }
}
```

---

## 五、调用方（stream / creator）代码示例

**无论 Mock 还是真实，这段代码** **完全不需要改**：

```java
// stream-service 里付费下单
@Service
public class StreamPurchaseServiceImpl implements IStreamPurchaseService {
    
    @Autowired private PaymentClient paymentClient;  // ← Feign Client，Mock/真实 透明切换
    @Autowired private StreamPurchaseMapper purchaseMapper;
    
    @Transactional
    public R<StreamPurchaseResp> createPurchase(Long userId, Long videoId, String unlockScope) {
        // 1. 查价格
        StreamVideo video = videoMapper.selectById(videoId);
        BigDecimal priceAmount = getPriceByScope(video, unlockScope); // episode / collection / creator
        
        // 2. 落 stream_purchase（status=created）
        StreamPurchase purchase = new StreamPurchase();
        purchase.setUserId(userId);
        purchase.setVideoId(videoId);
        purchase.setCollectionId(video.getCollectionId());
        purchase.setUnlockScope(unlockScope);
        purchase.setAmount(priceAmount);
        purchase.setStatus("created");
        purchaseMapper.insert(purchase);
        
        // 3. 调 PaymentClient.createOrder(req) —— 这一行 Mock/真实 签名一样
        CreateOrderReq req = new CreateOrderReq();
        req.setBusinessOrderNo(purchase.getBusinessOrderNo());
        req.setBusinessType(deriveBusinessType(unlockScope));
        req.setBusinessModule("STREAM");
        req.setUserId(userId);
        req.setAmount(priceAmount);
        req.setSubject("视频解锁: " + video.getTitle());
        
        R<CreateOrderResp> paymentResp = paymentClient.createOrder(req);
        CreateOrderResp order = paymentResp.getResult();
        
        // 4. 回填 paymentOrderNo
        purchase.setPaymentOrderNo(order.getOrderNo());
        purchaseMapper.updateById(purchase);
        
        // 5. 返回给前端（Mock 模式 status=PAID，真实模式 status=CREATED + payUrl）
        StreamPurchaseResp resp = new StreamPurchaseResp();
        resp.setOrderNo(order.getOrderNo());
        resp.setStatus(order.getStatus());       // ← 关键：前端根据这个决定是直接解锁还是跳支付页面
        resp.setPayUrl(order.getPayUrl());
        resp.setQrCodeUrl(order.getQrCodeUrl());
        return R.OK(resp);
    }
}
```

---

## 六、前端（star-ai-app）如何兼容两种模式

前端不关心 Mock / 真实，只看 `CreateOrderResp.status`：

```tsx
// src/components/paywall/PaywallModal.tsx
async function handlePurchase(unlockScope: string) {
  const order = await api.stream.createPurchase(videoId, unlockScope);
  
  if (order.status === 'PAID') {
    // Mock 模式：直接解锁
    message.success('解锁成功！');
    onSuccess();  // 刷新 canPlay → hls 切 play URL
  } else if (order.status === 'CREATED') {
    // 真实模式：跳支付页面
    if (order.qrCodeUrl) {
      // 微信 Native：弹窗展示二维码，用户用微信扫
      showWechatQr(order.qrCodeUrl);
      // 轮询 paymentClient.query() → 等 PAID
    } else if (order.payUrl) {
      // Stripe Checkout：跳转到 Stripe 页面
      window.location.href = order.payUrl;
      // 支付完成后 Stripe redirect 回 success 页面 → 再查询订单状态
    }
  }
}
```

---

## 七、切换步骤清单（渠道确认后执行）

| # | 步骤 | 风险点 | 验证 |
|---|------|--------|------|
| 1 | 确定支付渠道组合（国内：微信 Native；国际：Stripe Checkout 或 Stripe Elements） | 合规（需 ICP / 外汇资质） | 法务/财务确认 |
| 2 | 配置 Nacos `payment.mock.enabled=false` + 各渠道密钥 | 密钥用 AES-GCM TypeHandler 加密 | 启动 payment-biz 无报错 |
| 3 | 移除 `MockPaymentServiceImpl` 上的 `@ConditionalOnProperty` 或改成 Profile | Mock Bean 不再被注入 | 查 Spring 容器里 PaymentClient 只有 1 个 Bean |
| 4 | 实现 `WechatNativePayment` / `StripeCheckoutPayment` | 第三方 SDK 版本兼容性 | 单元测试 Mock HTTP Server |
| 5 | 实现 `/internal/payment/callback/wechat` + `/stripe` | 回调幂等 + 验签必须正确 | 用 Postman/Mock HTTP 打回调 |
| 6 | 前端 PaywallModal 加 `order.status === 'CREATED'` 分支（二维码 / Stripe 跳转） | 前端不用改 stream / creator 其他代码 | 手动下单一笔真实测试订单 |
| 7 | 跑 Phase 1 验收 Checklist 全部 19 步（见 `04-三仓联调Checklist.md`） | 确保 stream_revenue_record 分润固化链路完整 | 第 14-19 步分润快照验证 |
| 8 | XXL-JOB 启动关闭超时订单 Job | 避免 CREATED/CLOSED 订单堆积 | Job 跑一次清理测试订单 |

**关键验证点**：切换后 Mock 模式下 `status=PAID` 的自动解锁流程会变，前端必须能正确处理 `status=CREATED` + `payUrl` 的场景。这是 Step 5 最重要的改动点。

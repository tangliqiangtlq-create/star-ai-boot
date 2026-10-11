# Phase 1 · 后端服务端 Code Plan

> 仓库：`star-ai-boot` | 工期：11 天（并行前端）| 核心：Creator 入驻上传定价 + Stream 消费分润（Payment 延后）

---

## Step 0 · 基础设施（Day 1，1 天）

| # | 任务 | 产出 |
|---|------|------|
| 0.1 | `star-ai-server-cloud/pom.xml` 新增 4 个 module 声明 | pom.xml |
| 0.2 | 按 billing 模板复制骨架：payment / creator / stream / transcode-worker，改 artifactId + packageName（`org.jeecg.payment` / `org.jeecg.creator` / `org.jeecg.stream` / `org.jeecg.transcode`） | 4 模块可 `mvn compile` |
| 0.3 | biz 模块启动注解：`@SpringBootApplication` + `@EnableFeignClients` + `@MapperScan("org.jeecg.**.mapper")` | 能单独启动 |
| 0.4 | Gateway 路由 YAML（`star-ai-cloud-gateway/src/main/resources/bootstrap.yml`）| Path=/creator/\*\*,/stream/\*\*,/payment/\*\*,/internal/stream/\*\*,/internal/payment/\*\* |
| 0.5 | RabbitMQ Exchange/Queue/Binding 配置类（读 Nacos，启动时声明） | `x.direct.video`, `x.direct.payment` + 对应 Queue |
| 0.6 | 执行 SQL DDL：`schema/08` + `09` + `10` + Part 11 + `99_seed_data` | 22 张表 + seed 数据 |
| 0.7 | Sentinel 新增 4 个资源限流规则 | Nacos `sentinel-rules.json` |

**模块骨架目录**（复制 billing 模板后改名）：

```
star-ai-server-cloud/
├── pom.xml                           ← 新增 module:
│                                         star-ai-module-payment
│                                         star-ai-module-creator
│                                         star-ai-module-stream
│                                         star-ai-transcode-worker
│
├── star-ai-module-payment/
│   ├── pom.xml                       ← parent: star-ai-server-cloud, artifactId: star-ai-module-payment
│   ├── star-ai-module-payment-api/   ← artifactId: star-ai-module-payment-api
│   │   └── src/main/java/org/jeecg/payment/api/PaymentClient.java
│   └── star-ai-module-payment-biz/   ← artifactId: star-ai-module-payment-biz
│       └── src/main/java/org/jeecg/payment/PaymentApplication.java
│
├── star-ai-module-creator/           ← 同上结构
│   └── org/jeecg/creator/...
│
├── star-ai-module-stream/            ← 同上结构
│   └── org/jeecg/stream/...
│
└── star-ai-transcode-worker/         ← 先只建 biz 子模块，Phase 2 再拆
    └── org/jeecg/transcode/...
```

---

## Step 1 · Creator 创作者中心（Day 2-5，4 天）

**API 契约 Day 3 前必须出 Swagger**，前端开始对接。

### Controller 清单

| 路径 | 方法 | Controller | 前端谁用 |
|------|------|------------|----------|
| `/creators/apply` | POST | `CreatorController` | app 创作者入驻 |
| `/creators/{id}` | GET | `CreatorController` | app 创作者中心 |
| `/creator/videos/upload-init` | POST | `CreatorVideoController` | app MinIO 分片上传 |
| `/creator/videos/{id}/complete` | POST | `CreatorVideoController` | app 上传完成合并 |
| `/creator/videos` | GET | `CreatorVideoController` | app 创作者视频列表 |
| `/creator/videos/{id}` | GET/PUT | `CreatorVideoController` | app 视频详情 + 编辑 + **定价** |
| `/creator/videos/{id}/publish` | POST | `CreatorVideoController` | app 发布 |
| `/creator/global-unlock` | PUT | `CreatorController` | app 全局解锁定价 |
| `/creator/collections` | POST/PUT/GET | `CreatorCollectionController` | app 剧集管理 |
| `/creator/earnings` | GET | `CreatorEarningsController` | app 收益看板 |
| `/withdrawals` | POST/GET | `CreatorWithdrawalController` | app 提现申请（先 Mock） |

### Entity + Mapper

| Entity | 对应表 | Mapper |
|--------|--------|--------|
| `CreatorAccount` | `creator_account` | `CreatorAccountMapper` |
| `CreatorVideo` | `stream_video`（创作者侧视图，主表仍是 stream_video）| `StreamVideoMapper`（复用 stream 模块） |
| `CreatorVideoCategory` | `creator_video_category` | `CreatorVideoCategoryMapper` |
| `CreatorVideoTag` | `creator_video_tag` | `CreatorVideoTagMapper` |
| `CreatorWithdrawal` | `creator_withdrawal` | `CreatorWithdrawalMapper` |

### 核心业务规则 1：创作者定价校验

```java
// 封装成 PricingValidator 静态方法，单元测试覆盖边界
public class PricingValidator {
    
    public static void validate(CreatorAccount account, StreamVideo video) {
        // ① 定价开关
        Assert.isTrue(account.getPricingEnabled() == 1, "未开启自助定价权限");
        
        // ② 区间校验（闭区间）
        BigDecimal min = account.getPriceAmountMin();  // 例: 9.99
        BigDecimal max = account.getPriceAmountMax();  // 例: 99.99
        Assert.isTrue(
            video.getPriceAmount().compareTo(min) >= 0 
            && video.getPriceAmount().compareTo(max) <= 0,
            "定价必须在 ¥" + min + " - ¥" + max + " 范围内"
        );
    }
}
```

### 核心业务规则 2：发布时固化抽成快照

```java
@Service
public class CreatorVideoServiceImpl implements ICreatorVideoService {
    
    @Transactional
    public void publish(Long videoId) {
        StreamVideo video = videoMapper.selectById(videoId);
        CreatorAccount account = accountMapper.selectById(video.getCreatorId());
        
        // ⚠️ 关键：固化快照 —— 取发布当下的 creator_account.revenue_rate
        video.setRevenueRateSnapshot(account.getRevenueRate());
        
        // 定价审批
        if (account.getAutoPublishPrice() == 1 
            && PricingValidator.isInRange(account, video)) {
            video.setPriceStatus("approved");
        } else {
            video.setPriceStatus("pending");
        }
        
        video.setPublishStatus("pending_review");
        videoMapper.updateById(video);
    }
}
```

### 核心业务规则 3：上传 → 发布状态机

```
upload-init
  ↓
upload (分片并行 PUT MinIO presigned URL)
  ↓
upload-complete → 后端合并分片 → 写 stream_video(publish_status=draft, storage_bucket=draft)
  ↓ 发 MQ: video.uploaded
stream-service 消费 → FFmpeg 转码 → transcode_status=completed
  ↓ 自动审核 (Mock: 随机 passed/flagged)
  ↓
创作者编辑（标题+封面+分类+定价+发布）
  ↓ publish()
publish_status=pending_review (固化 snapshot)
  ↓
人工审核 (Step 3 管理后台)
  ├─ approve → publish_status=published
  └─ reject  → publish_status=rejected + reject_reason
```

### MinIO presigned URL 生成

```java
@Service
public class MinioPresignedService {
    
    /**
     * 分片上传：每片独立 presigned PUT URL（15min 有效期）
     * upload-init 时一次性生成所有分片 URL 给前端
     */
    public UploadInitResp generateChunkUploadUrls(String filename, long totalSize, long chunkSize) {
        long chunkCount = (totalSize + chunkSize - 1) / chunkSize;
        List<ChunkPresigned> chunks = new ArrayList<>();
        
        for (int i = 0; i < chunkCount; i++) {
            String objectKey = "stream-creator-draft/" + UUID.randomUUID() 
                             + "/chunk_" + String.format("%05d", i);
            String url = minioClient.getPresignedObjectUrl(
                PutObjectArgs.builder()
                    .bucket("stream-creator-draft")
                    .object(objectKey)
                    .expires(15, TimeUnit.MINUTES)
                    .build());
            chunks.add(new ChunkPresigned(i, url, objectKey));
        }
        return new UploadInitResp(UUID.randomUUID().toString(), chunkSize, chunks);
    }
    
    /**
     * upload-complete：服务端合并分片（composeObject）
     */
    public String mergeChunks(String uploadId, String finalFilename) {
        List<ComposeSource> sources = chunkObjects(uploadId).stream()
            .map(obj -> ComposeSource.builder()
                .bucket("stream-creator-draft")
                .object(obj.objectKey).build())
            .collect(Collectors.toList());
        
        String finalKey = "stream-creator/" + uploadId + "/" + finalFilename;
        minioClient.composeObject(
            ComposeObjectArgs.builder()
                .bucket("stream-creator")
                .object(finalKey)
                .sources(sources)
                .build());
        return finalKey;
    }
}
```

### RabbitMQ 生产者

```java
@Component
public class VideoEventPublisher {
    
    @Autowired private RabbitTemplate rabbit;
    
    public void publishVideoUploaded(Long videoId) {
        VideoUploadedEvent evt = new VideoUploadedEvent();
        evt.setEventId(UUID.randomUUID().toString());
        evt.setEventType("video.uploaded");
        evt.setTimestamp(Instant.now());
        evt.setPayload(Map.of("video_id", videoId));
        
        rabbit.convertAndSend("x.direct.video", "video.uploaded", evt);
    }
}
```

### Feign Client（creator-api 模块）

```java
@FeignClient(name = "star-ai-module-creator", 
             fallbackFactory = CreatorClientFallbackFactory.class)
public interface CreatorClient {
    
    @GetMapping("/creators/{id}")
    R<CreatorSimpleResp> getById(@PathVariable Long id);
    
    @GetMapping("/creators/{id}/revenue-rate")
    R<BigDecimal> getRevenueRate(@PathVariable Long id);
}
```

---

## Step 2 · Stream 视频消费 + 分润（Day 5-10，6 天）

**核心模块**。API 契约 Day 7 前 Swagger 齐全，前端对接。

### Controller 清单

| 路径 | 方法 | Controller | 前端谁用 |
|------|------|------------|----------|
| `/stream/videos` | GET | `StreamVideoController` | app 首页 Browse |
| `/stream/videos/{id}` | GET | `StreamVideoController` | app 视频详情页 |
| `/stream/collections` | GET | `StreamCollectionController` | app Browse |
| `/stream/collections/{id}` | GET | `StreamCollectionController` | app 剧集详情 |
| `/stream/videos/{id}/preview` | GET | `StreamPlayController` | app hls.js 30s 试看 |
| `/stream/videos/{id}/play` | GET | `StreamPlayController` | app hls.js 完整播放 |
| `/stream/videos/{id}/watch-progress` | POST | `StreamHistoryController` | app 播放进度 |
| `/stream/videos/{id}/watch-end` | POST | `StreamHistoryController` | app 播放结束埋点 |
| `/stream/videos/{id}/like` | POST/DELETE | `StreamInteractionController` | app 点赞 |
| `/stream/videos/{id}/favorite` | POST/DELETE | `StreamInteractionController` | app 收藏 |
| `/stream/orders` | POST | `StreamPurchaseController` | app 付费下单 |
| `/stream/orders/{no}` | GET | `StreamPurchaseController` | app 订单查询 |
| `/stream/creators/{id}` | GET | `StreamCreatorPageController` | app 创作者主页 |

### Entity + Mapper

| Entity | 对应表 | 说明 |
|--------|--------|------|
| `StreamVideo` | `stream_video` | 核心表 |
| `StreamVideoCollection` | `stream_video_collection` | 剧集 |
| `StreamVideoCollectionRel` | `stream_video_collection_rel` | 剧集-视频关联 |
| `StreamVideoTagRel` | `stream_video_tag_rel` | 视频-标签关联 |
| `StreamPurchase` | `stream_purchase` | 购买记录 |
| `StreamCreatorUnlock` | `stream_creator_unlock` | 创作者全局解锁 |
| `StreamPlayHistory` | `stream_play_history` | 观看历史 |
| `StreamInteraction` | `stream_interaction` | 点赞/收藏 |
| **`StreamRevenueRecord`** | **`stream_revenue_record`** | **分润固化（核心）** |
| **`CreatorEarningsRecord`** | **`creator_earnings_record`** | **创作者收益累计** |

### 核心业务：解锁路径判断（5 级优先级）

```java
@Service
public class UnlockPermissionService {
    
    /**
     * 5 级优先级，从快到慢匹配
     * 返回 permissionType 让前端渲染不同 UI
     */
    public PlayPermission canPlay(Long userId, Long videoId) {
        StreamVideo video = videoMapper.selectById(videoId);
        
        // ① 免费
        if ("free".equals(video.getPriceType())) {
            return PlayPermission.granted("free");
        }
        
        // ② 会员免费（member_only 类型）
        if ("member_only".equals(video.getPriceType())) {
            if (subscriptionMapper.hasActiveSubscription(userId)) {
                return PlayPermission.granted("member");
            }
        }
        
        // ③ 创作者全局解锁 — 查 stream_creator_unlock
        if (creatorUnlockMapper.exists(userId, video.getCreatorId())) {
            return PlayPermission.granted("creator_global");
        }
        
        // ④ 剧集解锁
        if (video.getCollectionId() != null
            && purchaseMapper.existsUnlock(userId, video.getCollectionId(), "collection")) {
            return PlayPermission.granted("collection");
        }
        
        // ⑤ 单集购买
        if (purchaseMapper.existsUnlock(userId, videoId, "episode")) {
            return PlayPermission.granted("episode");
        }
        
        return PlayPermission.locked(video.getPriceType(), video.getPriceAmount());
    }
}
```

### 核心业务：分润固化（PaymentSuccessConsumer 消费 MQ）

```java
@Component
public class PaymentSuccessConsumer {
    
    @Autowired private StreamPurchaseMapper purchaseMapper;
    @Autowired private StreamVideoMapper videoMapper;
    @Autowired private StreamRevenueRecordMapper revenueMapper;
    @Autowired private CreatorEarningsRecordMapper earningsMapper;
    
    @RabbitListener(queues = "q.payment.success")
    @Transactional
    public void handle(PaymentSuccessEvent evt) {
        String paymentOrderNo = evt.getPayload().getOrderNo();
        
        // 幂等
        if (revenueMapper.existsByOrderNo(paymentOrderNo)) {
            log.info("分润已固化，跳过 orderNo={}", paymentOrderNo);
            return;
        }
        
        // 1. 查 stream_purchase（创建订单时已 insert）
        StreamPurchase purchase = purchaseMapper.selectByPaymentOrderNo(paymentOrderNo);
        Assert.notNull(purchase, "purchase 不存在");
        purchase.setStatus("paid");
        
        // 2. 查 video → 拿 revenue_rate_snapshot（发布时已固化！）
        StreamVideo video = videoMapper.selectById(purchase.getVideoId());
        Assert.notNull(video.getRevenueRateSnapshot(), "快照不能为 null，发布时必须固化");
        
        // 3. 固化分润记录
        BigDecimal creatorShare = purchase.getAmount()
            .multiply(video.getRevenueRateSnapshot())
            .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal platformShare = purchase.getAmount().subtract(creatorShare);
        
        StreamRevenueRecord record = new StreamRevenueRecord();
        record.setRevenueSource("DIRECT");
        record.setPaymentOrderNo(paymentOrderNo);
        record.setCreatorId(video.getCreatorId());
        record.setVideoId(video.getId());
        record.setCollectionId(video.getCollectionId());
        record.setUnlockScope(purchase.getUnlockScope()); // episode / collection / creator
        record.setOrderAmount(purchase.getAmount());
        record.setRevenueRateSnapshot(video.getRevenueRateSnapshot()); // ← 关键！
        record.setCreatorShare(creatorShare);
        record.setPlatformShare(platformShare);
        record.setCreatedAt(LocalDateTime.now());
        revenueMapper.insert(record);
        
        // 4. 累加创作者收益
        earningsMapper.addCreatorEarnings(
            video.getCreatorId(), 
            creatorShare, 
            video.getId(),
            paymentOrderNo
        );
    }
}
```

### 试看 URL（FFmpeg 截流 30s）

```java
@Service
public class PreviewUrlService {
    
    public String generatePreviewUrl(Long videoId) {
        StreamVideo video = videoMapper.selectById(videoId);
        
        // MinIO 上原视频的 presigned URL（15min）
        String originUrl = minioPresigned.getPresignedGetUrl(
            "stream-creator", video.getStorageKey(), 15);
        
        // FFmpeg 截流：取前 30s 的 1080p HLS（MVP 单码率）
        // 转码完成后存到 bucket "stream-preview"，key = videoId/preview/master.m3u8
        String previewKey = videoId + "/preview/master.m3u8";
        
        if (!minioClient.objectExists("stream-preview", previewKey)) {
            ffmpegProcess(originUrl, "stream-preview/" + previewKey, 30);
        }
        
        return minioPresigned.getPresignedGetUrl("stream-preview", previewKey, 15);
    }
}
```

### 内嵌 FFmpeg 转码 Worker

```java
@Service
public class VideoTranscodeService {
    
    /**
     * 多码率自适应 HLS
     * @Async 线程池（最多 2 并发，semaphore 限流）
     */
    @Async("transcodeExecutor")
    @Transactional
    public void transcode(Long videoId) {
        StreamVideo video = videoMapper.selectById(videoId);
        video.setTranscodeStatus("transcoding");
        videoMapper.updateById(video);
        
        String inputUrl = minioPresigned.getPresignedGetUrl(
            "stream-creator", video.getStorageKey(), 60);
        String outputKey = video.getCreatorId() + "/" + videoId + "/";
        
        // 构建命令
        String[] cmd = new String[]{
            "ffmpeg", "-i", inputUrl,
            "-filter_complex", 
                "[v:0]split=3[v1][v2][v3];" +
                "[v1]scale=-2:1080[v1out];" +
                "[v2]scale=-2:720[v2out];" +
                "[v3]scale=-2:480[v3out]",
            "-map", "[v1out]", "-map", "0:a",
                "-c:v:0", "libx264", "-b:v:0", "5000k", "-maxrate", "6000k",
            "-map", "[v2out]", "-map", "0:a",
                "-c:v:1", "libx264", "-b:v:1", "2500k", "-maxrate", "3000k",
            "-map", "[v3out]", "-map", "0:a",
                "-c:v:2", "libx264", "-b:v:2", "1200k", "-maxrate", "1500k",
            "-c:a", "aac", "-b:a", "160k",
            "-hls_time", "10", "-hls_list_size", "0",
            "-master_pl_name", "master.m3u8",
            "-var_stream_map", "v:0,name:1080p v:1,name:720p v:2,name:480p",
            "output_%v.m3u8"
        };
        
        // 执行 + 上传分片到 MinIO stream-hls bucket
        // ... ProcessBuilder + upload stream
        
        video.setTranscodeStatus("completed");
        video.setHlsMasterKey(outputKey + "master.m3u8");
        video.setHlsPresetsJson("1080p=5000k,720p=2500k,480p=1200k");
        videoMapper.updateById(video);
        
        // 触发自动审核（Mock）
        autoReviewService.review(videoId);
    }
}
```

### RabbitMQ 消费者（video.uploaded → 转码）

```java
@Component
public class VideoUploadedConsumer {
    
    @Autowired private VideoTranscodeService transcodeService;
    
    @RabbitListener(queues = "q.video.uploaded")
    public void handle(VideoUploadedEvent evt) {
        Long videoId = evt.getPayload().getVideoId();
        StreamVideo video = videoMapper.selectById(videoId);
        video.setPublishStatus("transcode_pending");
        videoMapper.updateById(video);
        
        // 异步转码
        transcodeService.transcode(videoId);
    }
}
```

### 签名 URL 安全

```java
@Service
public class StreamSignedUrlService {
    
    /**
     * IP 绑定 + Referer 校验 + 15min 时效
     * 前端不能把 URL 分享给别人（换 IP 就失效）
     */
    public SignResult signPlayUrl(Long videoId, Long userId, String clientIp) {
        String originUrl = minioPresigned.getPresignedGetUrl(
            "stream-hls", video.getHlsMasterKey(), 15);
        
        String signature = HmacUtils.hmacSha256Hex(secretKey, 
            videoId + "|" + userId + "|" + clientIp + "|" + now + "+15min");
        
        String signedUrl = originUrl 
            + "&x-signature=" + signature 
            + "&x-user=" + userId 
            + "&x-ip=" + clientIp;
        
        return new SignResult(signedUrl, now + 15 * 60);
    }
}
```

---

## Step 3 · 管理后台 API（Day 10-11，2 天）

所有 `/internal/**` 路由由 Gateway InternalPathDenyFilter 保护。

| 路径 | 方法 | Controller |
|------|------|------------|
| `/internal/stream/review/queue` | GET | `StreamReviewInternalController` |
| `/internal/stream/review/{id}/approve` | POST | `StreamReviewInternalController` |
| `/internal/stream/review/{id}/reject` | POST | `StreamReviewInternalController` |
| `/internal/stream/withdrawals` | GET | `WithdrawalInternalController` |
| `/internal/stream/withdrawals/{id}/approve` | POST | `WithdrawalInternalController` |
| `/internal/creator/accounts` | GET | `CreatorInternalController` |
| `/internal/creator/accounts/{id}` | PUT | `CreatorInternalController` |
| `/internal/stream/reconciliation` | GET | `StreamReportInternalController` |
| `/internal/stream/reports/daily` | GET | `StreamReportInternalController` |

---

## Step 4 · 18+ 合规 + Gateway 增强（Day 11，1 天）

| # | 任务 | 实现方式 |
|---|------|---------|
| 4.1 | 新用户注册强制 age_verified | system-cloud-start 注册逻辑改：region=cn 走身份证校验接口；region=sg/us 预留 `IAgeVerifySPI` 接口 |
| 4.2 | Gateway Filter 拦截未验证用户 | `AgeVerifyGatewayFilterFactory`：Sa-Token 鉴权后，`GET redis.user:age:{userId}`，未验证返回 403 + `{"code":403,"msg":"请先完成年龄验证"}` |
| 4.3 | InternalPathDenyFilter | 新 Gateway Filter：`if (request.getPath().startsWith("/internal/") && clientIp != GatewayIP) → 403` |
| 4.4 | Sentinel 限流 | Nacos sentinel-rules.json + 启动时加载 |

---

## Step 5 · Payment 真实实现（延后，4 天）

**前提**：支付渠道确认（微信 Native / Stripe Checkout 或组合）。

PaymentClient 接口签名不变，Mock 切真实只需改 Spring Profile。详见 `06-Payment-Mock切换方案.md`。

---

## DDL 执行顺序

```sql
-- 1. 系统扩展
schema/08_stream_creator_domain.sql   -- Part 11: sys_user_age_verification

-- 2. 支付公共（表先建，Step 5 才写 Service）
schema/09_payment_domain.sql          -- 4 张表: ai_payment_order, ai_payment_channel, ai_payment_callback_log, ai_payment_refund

-- 3. 创作者 + 视频（核心 13 张）
schema/08_stream_creator_domain.sql   -- Part 08: creator_account + creator_video_category + creator_video_tag + stream_video + stream_video_collection + stream_video_collection_rel + stream_video_tag_rel + stream_purchase + stream_creator_unlock + stream_play_history + stream_interaction + stream_revenue_record + creator_earnings_record

-- 4. 订阅池（Phase 1 只建表）
schema/10_subscription_pool_domain.sql

-- 5. Seed Data
schema/99_seed_data.sql
```

**共 22 张表**。

---

## RabbitMQ Exchange + Queue

| Exchange | Queue | 生产者 | 消费者 | Routing Key |
|----------|-------|--------|--------|-------------|
| `x.direct.video` | `q.video.uploaded` | creator | stream | `video.uploaded` |
| `x.direct.video` | `q.video.transcode` | stream | stream(self) | `video.transcode` |
| `x.direct.payment` | `q.payment.success` | payment(Mock→真实) | stream | `payment.success` |
| `x.direct.payment` | `q.payment.notify` | payment | 业务方 | `payment.notify` |

消息体统一格式：
```json
{
  "event_id": "UUID",
  "event_type": "payment.success",
  "timestamp": "2026-10-11T21:30:00.123Z",
  "payload": { "order_no": "P123456789", "video_id": 5001 },
  "tenant_id": 0
}
```

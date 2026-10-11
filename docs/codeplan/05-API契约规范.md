# Phase 1 · API 契约规范

> 所有后端微服务（star-ai-boot）必须遵守；前端（star-ai-admin / star-ai-app）按此规范解析

---

## 统一返回结构（R\<T\>）

```jsonc
// 成功
{
  "code": 200,
  "message": "success",
  "success": true,
  "result": { /* T */ }
}

// 失败（业务异常）
{
  "code": 418,
  "message": "请先完成年龄验证",
  "success": false,
  "result": null
}

// 失败（系统异常）
{
  "code": 500,
  "message": "NullPointerException at ...",
  "success": false,
  "result": null
}
```

**前端 axios/feign 统一拦截**：按 `code` 分支处理（见第 4 节 Error Code 表）。

## 分页结构

```jsonc
{
  "code": 200,
  "success": true,
  "result": {
    "records": [ /* 记录数组 */ ],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

**后端**：使用 JeecgBoot 封装的 `Page<T>`（extends `IPage<T>`），Controller 返回 `R<Page<T>>`。

## 请求参数规范

### 查询参数

| 类型 | 位置 | 命名 | 示例 |
|------|------|------|------|
| 分页 | Query | `current`, `size` | `GET /stream/videos?current=1&size=10` |
| 排序 | Query | `sort`, `order` | `GET /stream/videos?sort=hot&order=desc` |
| 过滤 | Query | snake_case | `GET /stream/videos?category_id=1&publish_status=published` |
| 路径参数 | Path | camelCase | `GET /stream/videos/{id}` |

### 请求体

- Content-Type: `application/json;charset=UTF-8`
- JSON 字段用 **snake_case** 或 **camelCase** 都可，但必须统一（建议 snake_case 对齐 DB 字段）
- DTO 类加 `@ApiModel` + Swagger 注解

### 标准请求 DTO

```java
@Data
@ApiModel("视频定价更新请求")
public class VideoPricingUpdateReq {
    
    @ApiModelProperty(value = "价格", required = true, example = "19.99")
    @NotNull @DecimalMin("0.01")
    private BigDecimal priceAmount;
    
    @ApiModelProperty("价格类型：free / member_only / paid")
    private String priceType;
    
    @ApiModelProperty("全局解锁价格")
    private BigDecimal globalUnlockPrice;
}
```

### 标准响应 DTO

```java
@Data
@ApiModel("视频详情响应")
public class VideoDetailResp {
    @ApiModelProperty("视频 ID")
    private Long id;
    
    @ApiModelProperty("标题")
    private String title;
    
    @ApiModelProperty("封面 URL")
    private String coverUrl;
    
    @ApiModelProperty("创建者")
    private CreatorSimpleResp creator;
    
    @ApiModelProperty("解锁类型：free / member / creator_global / collection / episode / locked")
    private String permissionType;
    
    @ApiModelProperty("完整播放鉴权 URL（canPlay=true 时返回）")
    private String playUrl;
    
    @ApiModelProperty("试看 URL（canPlay=false 时返回）")
    private String previewUrl;
    
    @ApiModelProperty("分润快照，发布时固化")
    private BigDecimal revenueRateSnapshot;
}
```

---

## Feign Client 跨模块调用

### 定义位置

| Client | 所在 api 模块 | 被谁调用 |
|--------|--------------|---------|
| `PaymentClient` | `star-ai-module-payment-api` | stream-api, creator-api |
| `CreatorClient` | `star-ai-module-creator-api` | stream-api |
| `StreamClient` | `star-ai-module-stream-api` | creator-api |
| `SystemClient` | 已有 `star-ai-system-cloud-api` | 所有新模块 |

### 模板

```java
// stream-api 模块
@FeignClient(
    name = "star-ai-module-stream",
    fallbackFactory = StreamClientFallbackFactory.class,
    configuration = FeignConfig.class
)
public interface StreamClient {
    
    @PostMapping("/internal/stream/video/review-callback")
    R<?> videoReviewCallback(@RequestBody VideoReviewCallbackDTO dto);
    
    @GetMapping("/internal/stream/videos/{id}")
    R<StreamVideoSimpleResp> getById(@PathVariable Long id);
}
```

### Fallback Factory 必须写

```java
@Component
@Slf4j
class StreamClientFallbackFactory implements FallbackFactory<StreamClient> {
    @Override
    public StreamClient create(Throwable cause) {
        return new StreamClient() {
            @Override
            public R<?> videoReviewCallback(VideoReviewCallbackDTO dto) {
                log.warn("stream videoReviewCallback fallback, videoId={}", dto.getVideoId(), cause);
                return R.error("stream-service 不可用");
            }
            @Override
            public R<StreamVideoSimpleResp> getById(Long id) {
                log.warn("stream getById fallback, id={}", id, cause);
                return R.error("stream-service 不可用");
            }
        };
    }
}
```

### Sentinel 熔断规则

每个 Feign Client 对应 Sentinel 资源名：`feign:<service-name>:<method>`.

---

## Error Code 表（前端根据 code 弹不同提示）

| code | HTTP Status | 含义 | 前端行为 | 谁抛出 |
|------|-------------|------|----------|--------|
| **200** | 200 | 成功 | 正常渲染 | 所有 |
| **400** | 400 | 参数校验失败 | Toast 显示 `message` | `@Valid` BindingResult |
| **401** | 401 | Sa-Token 过期 / 未登录 | 清 Token → 跳 `/login` | Sa-Token 框架 |
| **403** | 403 | 无权限（角色/权限码不匹配） | Toast "无权限" | Sa-Token / Sentinel |
| **418** | 403(业务) | **年龄未验证** | 弹年龄验证 Modal | Gateway `AgeVerifyFilter` |
| **402** | 402 | **需要付费** | 弹 PaywallModal | StreamPlayController.canPlay=false |
| **404** | 404 | 资源不存在 | 展示 Empty 页面 | 所有 |
| **409** | 409 | 冲突（重复发布等） | Toast `message` | 业务 Service |
| **451** | 451 | **InternalPathDenyFilter 拦截** | 后端内部错误（前端不可见） | Gateway |
| **500** | 500 | 后端异常 | Toast + 打 Sentry/Console | 全局异常处理 `@RestControllerAdvice` |
| **503** | 503 | 下游服务不可用（Feign 熔断） | Toast "服务暂不可用，请稍后" | Sentinel / Feign |

**建议常量类**：
```java
public class ResultCode {
    public static final int SUCCESS = 200;
    public static final int VALIDATE_ERROR = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int PERMISSION_DENIED = 403;
    public static final int AGE_UNVERIFIED = 418;   // 新增
    public static final int PAYWALL_REQUIRED = 402;  // 新增
    public static final int NOT_FOUND = 404;
    public static final int CONFLICT = 409;
    public static final int INTERNAL_ERROR = 500;
}
```

**全局异常处理**（必须配置）：
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(NotLoginException.class)
    public R<?> handleNotLogin(NotLoginException e) {
        return R.error(401, "未登录或登录已过期");
    }
    
    @ExceptionHandler(AgeUnverifiedException.class)
    public R<?> handleAgeUnverified(AgeUnverifiedException e) {
        return R.error(418, "请先完成年龄验证");
    }
    
    @ExceptionHandler(PaywallRequiredException.class)
    public R<?> handlePaywall(PaywallRequiredException e) {
        return R.error(402, e.getMessage());
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public R<?> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .collect(Collectors.joining("; "));
        return R.error(400, msg);
    }
    
    @ExceptionHandler(Exception.class)
    public R<?> handleUnknown(Exception e) {
        log.error("未知异常", e);
        return R.error(500, e.getMessage());
    }
}
```

---

## 鉴权规范（Sa-Token）

### 请求头

前端必须带：`X-Access-Token: <Sa-Token值>`

### Sa-Token 配置

Nacos 配置中心 `application.yml`：
```yaml
sa-token:
  token-name: X-Access-Token
  timeout: 86400          # 24h
  active-timeout: 3600    # 1h 无活跃过期
  is-concurrent: true
  is-share: false
  token-style: uuid
  is-log: true
```

### Gateway Sa-Token 集成

```yaml
# star-ai-cloud-gateway bootstrap.yml
sa-token:
  check-login: true
  exclude-paths:
    - /sys/login
    - /sys/register
    - /public/**
    - /internal/payment/callback/**
```

### 年龄验证 Filter（Gateway 自定义）

```java
@Component
public class AgeVerifyGatewayFilterFactory 
    extends AbstractGatewayFilterFactory<AgeVerifyConfig> {
    
    @Override
    public GatewayFilter apply(AgeVerifyConfig config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getURI().getPath();
            
            // 只拦截业务路径
            if (!path.startsWith("/stream/") 
                && !path.startsWith("/creator/") 
                && !path.startsWith("/pay/")) {
                return chain.filter(exchange);
            }
            
            String token = request.getHeaders().getFirst("X-Access-Token");
            if (StrUtil.isBlank(token)) return chain.filter(exchange); // Sa-Token 会拦
            
            // 查 Redis user:age:{userId}
            String userId = StpUtil.getLoginIdByToken(token).toString();
            String ageVerified = redis.get("user:age:" + userId);
            
            if (!"1".equals(ageVerified)) {
                return unauthorized(exchange, 418, "请先完成年龄验证");
            }
            return chain.filter(exchange);
        };
    }
}
```

---

## InternalPathDenyFilter（Gateway 内部路由保护）

```java
@Component
public class InternalPathDenyFilter implements GlobalFilter, Ordered {
    
    @Value("${gateway.internal.ip}")
    private String gatewayIp;  // Gateway 自身 IP
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        
        // 只保护 /internal/**
        if (path.startsWith("/internal/")) {
            String clientIp = exchange.getRequest().getRemoteAddress()
                .map(a -> a.getAddress().getHostAddress())
                .orElse("");
            
            // 允许：Gateway 自身转发 / Docker 内部网络 / 本机回环
            boolean allowed = clientIp.equals(gatewayIp) 
                           || clientIp.startsWith("172.") 
                           || clientIp.startsWith("10.")
                           || clientIp.equals("127.0.0.1");
            
            if (!allowed) {
                ServerHttpResponse response = exchange.getResponse();
                response.setStatusCode(HttpStatus.FORBIDDEN);
                response.getHeaders().add("Content-Type", "application/json");
                DataBuffer buffer = response.bufferFactory()
                    .wrap("{\"code\":451,\"message\":\"Internal API 禁止外部访问\",\"success\":false}".getBytes());
                return response.writeWith(Mono.just(buffer));
            }
        }
        return chain.filter(exchange);
    }
    
    @Override
    public int getOrder() { return -100; }  // 在 Sa-Token 之后
}
```

---

## Swagger 注解规范

所有 Controller 必须加完整 Swagger 注解，便于前端 Day 1 就提取 DTO 类型。

```java
@RestController
@RequestMapping("/stream/videos")
@Api(tags = "视频消费")
public class StreamVideoController {
    
    @ApiOperation("视频列表")
    @ApiImplicitParams({
        @ApiImplicitParam(name = "current", value = "页码", defaultValue = "1"),
        @ApiImplicitParam(name = "size", value = "每页大小", defaultValue = "10"),
        @ApiImplicitParam(name = "category_id", value = "分类 ID"),
        @ApiImplicitParam(name = "sort", value = "排序：hot / latest / views"),
    })
    @GetMapping
    public R<Page<StreamVideoListResp>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(defaultValue = "hot") String sort) {
        return R.OK();
    }
    
    @ApiOperation("视频详情")
    @ApiImplicitParam(name = "id", value = "视频 ID", required = true)
    @GetMapping("/{id}")
    public R<StreamVideoDetailResp> getById(@PathVariable Long id) {
        return R.OK();
    }
}
```

Swagger UI 地址：`http://localhost:{service-port}/doc.html`（每个微服务独立）。

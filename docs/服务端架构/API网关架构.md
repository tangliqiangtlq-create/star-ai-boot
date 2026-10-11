# API 网关架构

> 技术栈：Spring Cloud Gateway 4.1.8 (WebFlux) + Nacos Discovery + Sentinel 限流 + GlobalAccessTokenFilter 统一鉴权

---

## 一、网关架构定位

```
                        ┌─────────────────────────────────┐
                        │     star-ai-cloud-gateway        │
                        │        (port: 9999)              │
                        ├─────────────────────────────────┤
  Client Request ───►   │  ① CORS                        │
  (Next.js SPA)          │  ② Sentienl 限流/熔断           │
  (第三方 API)           │  ③ 安全响应头                    │
                        │  ④ GlobalAccessTokenFilter       │
                        │  ⑤ 路由转发 (Nacos Discovery)    │
                        │  ⑥ 内部路径拦截                  │
                        │  ⑦ S2S 签名校验                 │
                        └─────────────────────────────────┘
                                     │
                                     ▼
                        ┌─────────────────────────────────┐
                        │     下游微服务集群 (Nacos)       │
                        └─────────────────────────────────┘
```

---

## 二、核心 Filter 链

Gateway Filter 基于 WebFlux 响应式编程模型，**执行顺序 = 注册顺序**（先注册先执行）：

| 顺序 | Filter 类 | 优先级 | 类型 | 功能 |
|------|----------|--------|------|------|
| 1 | `SecurityHeadersGlobalWebFilter` | HIGHEST | Global WebFilter | 注入 CSP/HSTS/XFO/PP |
| 2 | `CorsChainDiagWebFilter` | HIGHEST-1 | Global WebFilter | CORS Chain 诊断 |
| 3 | `InternalTrustedS2sFilter` | HIGH | Global WebFilter | S2S 签名校验（内部调用） |
| 4 | `InternalPathDenyFilter` | MEDIUM | Global WebFilter | 拦截 `/internal/**` 外部请求 |
| 5 | `InternalSysApiBlockWebFilter` | MEDIUM | Global WebFilter | 拦截 `/sys/api/**` 外部请求 |
| 6 | `GatewayForwardedHeadersSanitizerFilter` | MEDIUM | Global WebFilter | 清洗 Forwarded/XFF 头 |
| 7 | `GatewaySignatureWriteFilter` | LOW | Global WebFilter | 写入 S2S 签名到下游请求 |
| 8 | `ApiServiceRouteWebFilter` | LOW | Global WebFilter | API 服务路由校验 |
| 9 | `GatewayRequestDiagFilter` | LOWEST | Global WebFilter | 请求日志 + tracing |
| - | `GlobalAccessTokenFilter` | - | Gateway Filter | JWT Token 校验 + 注入用户信息 |

---

## 三、动态路由

### 3.1 路由数据源

路由配置来源优先级：

```
1. 数据库表: sys_gateway_route (动态管理)
2. Nacos DataId: star-ai-gateway-routes.yaml (远程配置)
3. application.yml (静态默认)
```

### 3.2 路由结构

```java
// org.jeecg.loader.vo.MyRouteDefinition
public class MyRouteDefinition {
    private String id;              // 路由 ID
    private String uri;             // 下游 URI (lb://{service})
    private Integer order;          // 优先级
    private List<PredicatesVo> predicates;  // 匹配条件
    private List<FilterDefinition> filters; // 过滤器
    private Boolean enabled;        // 启用状态
}
```

### 3.3 示例路由

```yaml
# star-ai-gateway-routes.yaml (Nacos)
spring:
  cloud:
    gateway:
      routes:
        - id: star-ai-generate
          uri: lb://star-ai-module-generate
          predicates:
            - Path=/videos/**,/generations/**,/tools/**
          filters:
            - StripPrefix=0
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200

        - id: star-ai-content
          uri: lb://star-ai-module-content
          predicates:
            - Path=/scripts/**,/storyboards/**,/assets/**,/*-library/**

        - id: star-ai-billing
          uri: lb://star-ai-module-billing
          predicates:
            - Path=/billing/**,/recharge/**,/subscription/**

        - id: star-ai-team
          uri: lb://star-ai-module-team
          predicates:
            - Path=/team-admin/**

        - id: star-ai-system
          uri: lb://jeecg-system
          predicates:
            - Path=/sys/**,/invite-center,/url/**,/change-password

        # ===== 🆕 支付公共服务 =====
        - id: payment-service
          uri: lb://star-ai-module-payment
          predicates:
            - Path=/payment/**,/pay/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 50
                redis-rate-limiter.burstCapacity: 100

        # ===== 🆕 视频消费域 =====
        - id: stream-service
          uri: lb://star-ai-module-stream
          predicates:
            - Path=/stream/**,/videos/**,/play-history/**,/purchases/**,/interactions/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 200
                redis-rate-limiter.burstCapacity: 400

        # ===== 🆕 创作者中心 =====
        - id: creator-service
          uri: lb://star-ai-module-creator
          predicates:
            - Path=/creator/**,/creators/**,/video-categories/**,/video-tags/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 100
                redis-rate-limiter.burstCapacity: 200

        # ===== 🆕 创作者收益/提现（走 stream-service 鉴权）=====
        - id: creator-earnings
          uri: lb://star-ai-module-stream
          predicates:
            - Path=/withdrawals/**,/creator/earnings/**
          filters:
            - name: RequestRateLimiter
              args:
                redis-rate-limiter.replenishRate: 50
                redis-rate-limiter.burstCapacity: 100

        # ===== 🆕 内部回调（InternalPathDenyFilter 保护，外部无法直接调用）=====
        - id: payment-internal
          uri: lb://star-ai-module-payment
          predicates:
            - Path=/internal/payment/**

        - id: stream-internal
          uri: lb://star-ai-module-stream
          predicates:
            - Path=/internal/stream/**
```

### 3.4 路由热更新

```java
// Redis Pub/Sub 通知
// 当 sys_gateway_route 表变更时，发布消息：
redisTemplate.convertAndSend("router-refresh", "ALL");

// DynamicRouteLoader 订阅后重新加载
// 支持全量刷新 + 增量更新
```

---

## 四、统一鉴权流程

```
Client Request
  │  Header: Authorization: Bearer {jwt}
  │  Cookie: satoken={satoken_value}
  ▼
GlobalAccessTokenFilter
  │
  ├── 1. 判断路径是否需要鉴权
  │      (白名单: /sys/login, /sys/captcha, /_/csp-report, ...)
  │
  ├── 2. 提取 Token
  │      ├── Sa-Token: satoken header/cookie
  │      └── JWT: Authorization: Bearer xxx
  │
  ├── 3. Token 校验
  │      ├── Sa-Token: StpUtil.checkLogin()
  │      └── JWT: JwtUtil.verify()
  │
  ├── 4. 从 Token 解析用户信息
  │      ├── userId / username / tenantId
  │      ├── roleList / permissionList
  │      └── 可信设备 MFA 状态
  │
  ├── 5. 注入到下游请求头
  │      ├── X-User-Id
  │      ├── X-Tenant-Id
  │      ├── X-User-Roles
  │      └── X-Request-Trace-Id (trace)
  │
  └── 6. 放行或拒绝 (401/403)
```

---

## 五、Sentinel 限流配置

### 5.1 Gateway 专用规则

```json
// star-ai-gateway-flow-rules (Nacos DataId, SENTINEL_GROUP)
[
  {
    "resource": "star-ai-module-generate",
    "resourceMode": 0,
    "grade": 1,
    "count": 1000,
    "intervalSec": 1,
    "controlBehavior": 0,
    "clusterMode": false
  },
  {
    "resource": "POST:/videos/generate",
    "resourceMode": 1,
    "grade": 1,
    "count": 50,
    "intervalSec": 1,
    "controlBehavior": 2
  }
]
```

| 字段 | 说明 | 值 |
|------|------|---|
| `resourceMode` | 资源模式 | 0=按 API 分组, 1=按单独 API |
| `grade` | 限流阈值类型 | 1=QPS, 0=线程数 |
| `controlBehavior` | 流控效果 | 0=快速失败, 2=匀速排队 |
| `clusterMode` | 集群模式 | false=单机, true=集群 |

### 5.2 降级规则

```json
// star-ai-gateway-degrade-rules (Nacos)
[
  {
    "resource": "star-ai-module-generate",
    "grade": 0,
    "count": 0.5,
    "timeWindow": 10,
    "minRequestAmount": 5,
    "statIntervalMs": 1000,
    "slowRatioThreshold": 0.5
  }
]
```

| 降级策略 | 说明 |
|---------|------|
| 慢调用比例 (`grade=0`) | 超过 `count` 的请求 → 熔断 |
| 异常比例 (`grade=1`) | 异常率超过 `count` → 熔断 |
| 异常数 (`grade=2`) | 异常数超过 `count` → 熔断 |

---

## 六、安全响应头 (CSP 专项)

### 6.1 网关 CSP 配置

```yaml
gateway:
  security:
    headers:
      enabled: true
      csp:
        mode: Enforce        # ReportOnly → Enforce
        enforceTrafficPct: 100
        reportUri: "/_/csp-report"
```

### 6.2 CSP 违规上报

```java
// org.jeecg.handler.CspReportController
@RestController
@RequestMapping("/_")
public class CspReportController {
    @PostMapping("/csp-report")
    public Mono<Void> report(ServerWebExchange exchange) {
        // 记录 CSP violation report → Sentry/日志
        return exchange.getResponse().setComplete();
    }
}
```

### 6.3 其他安全响应头

| Header | 值 | 作用 |
|--------|-----|------|
| `Strict-Transport-Security` | `max-age=63072000; includeSubDomains; preload` | 强制 HTTPS 两年 |
| `X-Frame-Options` | `DENY` | 禁止 iframe |
| `X-Content-Type-Options` | `nosniff` | 禁止 MIME 嗅探 |
| `Referrer-Policy` | `strict-origin-when-cross-origin` | 控制 Referer |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), usb=(), payment=(), clipboard-read=(self), clipboard-write=(self)` | 禁用高风险 API |
| `X-XSS-Protection` | `0` | 禁用浏览器 XSS filter（现代浏览器已废弃，避免误报） |
| `Cache-Control` | `no-store` (敏感接口) | 禁止浏览器缓存 |

---

## 七、故障转移

### 7.1 Sentinel FallbackHandler

```java
// org.jeecg.fallback.sentinel.GatewaySentinelExceptionConfig
@Configuration
public class GatewaySentinelExceptionConfig {
    @Bean
    public GlobalBlockRequestHandler sentinelBlockHandler() {
        return (exchange, t) -> {
            ServerResponse.status(HttpStatus.TOO_MANY_REQUESTS)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(Map.of(
                    "code", 429,
                    "message", "服务繁忙，请稍后重试",
                    "traceId", exchange.getAttribute("traceId")
                )));
        };
    }
}
```

### 7.2 Hystrix/Resilience4j Fallback

```yaml
# 路由级别降级
filters:
  - name: Fallback
    args:
      name: generateFallback
```

---

## 八、网关性能优化

### 8.1 连接池

```yaml
# Gateway 底层 Reactor Netty 配置
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 5000
        response-timeout: 30s
        pool:
          type: fixed
          max-connections: 500
          acquire-timeout: 3000
          max-idle-time: 60000
```

### 8.2 Netty 线程

```yaml
# GatewayNettyUriCompatConfig
# Reactor Netty EventLoop 配置
reactor:
  netty:
    io:
      workerCount: 16
```

### 8.3 CORS 配置

```yaml
gateway:
  cors:
    allowedOrigins:
      - "https://*.xnow.xyz"
    allowedMethods:
      - GET
      - POST
      - PUT
      - DELETE
      - OPTIONS
    allowedHeaders: "*"
    allowCredentials: true
    maxAge: 3600
```

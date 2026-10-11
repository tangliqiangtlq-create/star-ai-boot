# Star AI Boot 架构文档索引

> 版本：v3.11.0 | 最后更新：2026-10-11

---

## 📚 文档列表

| 序号 | 文档 | 说明 | 读者 |
|------|------|------|------|
| 1 | [系统架构总览](系统架构总览.md) | 整体架构图、模块依赖、技术栈、端口规划、启动顺序 | **所有人** |
| 2 | [业务架构](业务架构.md) | 六大业务域、视频生成时序、素材引用占位符、API 路由 | 产品经理/后端 |
| 3 | [安全架构](安全架构.md) | 六层纵深防御、网关安全、SQL 防火墙、安全加固清单 | 安全工程师/架构师 |
| 4 | [数据架构](数据架构.md) | MySQL 表结构、多租户实现、Redis 结构、消息拓扑 | DBA/后端 |
| 5 | [API 网关架构](API网关架构.md) | Gateway Filter 链、动态路由、Sentinel 限流、CSP | SRE/架构师 |
| 6 | [部署架构](部署架构.md) | Docker Compose / K8s / CI-CD / 灰度 / 备份灾备 | SRE/DBA |
| 7 | [框架核心扩展架构](框架核心扩展架构.md) | base-core 详解、starter-secrets、AOP 切面、可扩展点 | 后端开发 |
| 8 | [18+ 视频付费分润方案](18+ 视频付费分润方案.md) | 🆕 payment/stream/creator 三模块设计、18+ 合规、端到端时序、部署资源 | 架构师/后端 |
| 9 | [三方配置位置说明](三方配置位置说明.md) | 🆕 所有三方服务配置位置总览（Layer 1 env / Layer 2 DB / Layer 3 sys_dict）、加密层级、缺口改进 | 架构师/SRE/后端 |

---

## 🏗️ 系统一句话描述

**基于 JeecgBoot 3.7.0 + Spring Cloud 2023.0.4 + Nacos + Sa-Token 构建的 AI 短剧 SaaS + 18+ 视频付费分润平台**，服务于视频生成、剧本分镜、团队协作、计费订阅、创作者自助定价上传（三级定价：单集/剧集/全局）、用户付费观看（五种消费路径 + 会员权益体系）、双轨分润结算等业务场景，支持国内（MinIO + 微信/支付宝）和国际（AWS S3 + CloudFront + Stripe）双 Region 部署。

---

## 📊 关键指标

| 维度 | 数字 |
|------|------|
| Java 版本 | 17 |
| Spring Boot | 3.3.11 |
| Spring Cloud | 2023.0.4 |
| 微服务数量 | **10** (Gateway + System + 7 业务：generate/content/provider/task/billing/team + 🆕 payment/stream/creator) |
| 独立进程 | 🆕 transcode-worker (FFmpeg HLS 转码) |
| 框架基础模块 | 2 (base-core + starter-secrets) |
| AI 厂商支持 | 5 家 (火山/阿里/快手/OpenAI/Google) |
| AI 模型数量 | 15+ |
| 业务域 | **9** (generate/content/provider/billing/team/task + 🆕 payment/stream/creator) |
| 安全过滤器 | 10+ |
| CVE 修复 | 30+ |
| 🆕 新增数据库表 | **29 张** (08_stream_creator v1.2 + 10_subscription_pool v1.2.1 + 09_payment + stream_platform_config + stream_creator_unlock + sys_user_age_verification) |
| 🆕 新增 MinIO/S3 Bucket | 3 (stream-creator-draft / stream-published / stream-temp) |
| 🆕 新增 RabbitMQ 消息 | **10 条** (v1.0 5 条 + v1.1 3 条 + **v1.2 2 条**: stream.global_unlock.success / subscription.gift_points) |

---

## 🔗 快速导航

### 我想了解...

<details>
<summary>整体架构怎么分层？</summary>
→ 读 [系统架构总览 · 二、整体架构图](系统架构总览.md#二整体架构图)
</details>

<details>
<summary>新微服务怎么接入？</summary>
→ 读 [框架核心扩展架构 · 六、可扩展点 · 6.1 新增业务微服务](框架核心扩展架构.md#六可扩展点)
</details>

<details>
<summary>租户隔离怎么实现？</summary>
→ 读 [数据架构 · 四、多租户实现细节](数据架构.md#四多租户实现细节)
→ 读 [框架核心扩展架构 · 2.10 Sa-Token 集成](框架核心扩展架构.md#二核心扩展点)
</details>

<details>
<summary>视频生成全流程？</summary>
→ 读 [业务架构 · 三、端到端业务流程 · 3.1 视频生成完整时序](业务架构.md#三端到端业务流程)
</details>

<details>
<summary>网关安全 Filter 顺序？</summary>
→ 读 [API 网关架构 · 二、核心 Filter 链](API网关架构.md#二核心-filter-链)
</details>

<details>
<summary>敏感数据怎么加密？</summary>
→ 读 [安全架构 · 五、数据安全层 · 5.1 敏感字段加密](安全架构.md#五数据安全层)
→ 读 [框架核心扩展架构 · 2.7 AesEncryptUtil](框架核心扩展架构.md#二核心扩展点)
</details>

<details>
<summary>部署到 Kubernetes 怎么做？</summary>
→ 读 [部署架构 · 三、Kubernetes 生产部署架构](部署架构.md#三kubernetes-生产部署架构)
→ 读 [部署架构 · 四、Dockerfile 多阶段构建](部署架构.md#四dockerfile-多阶段构建)
</details>

<details>
<summary>Secrets/KMS 怎么配？</summary>
→ 读 [框架核心扩展架构 · 三、starter-secrets 模块](框架核心扩展架构.md#三starter-secrets-模块)
→ 读 [系统架构总览 · 七、配置外部化](系统架构总览.md#七配置外部化)
</details>

<details>
<summary>数据库连接池怎么调？</summary>
→ 读 [数据架构 · 五、索引策略](数据架构.md#五索引策略) 的连接池配置
→ 读 `application.yml` 中的 `spring.datasource.hikari.*`
</details>

<details>
<summary>🆕 18+ 视频付费分润平台怎么设计？</summary>
→ 读 [18+ 视频付费分润方案](18+ 视频付费分润方案.md)
→ 读 [18+ 视频付费分润方案 · 二、微服务矩阵](18+ 视频付费分润方案.md#二微服务矩阵)
→ 读 [数据架构 · 2.5 18+ 视频付费分润域](数据架构.md#25-18+ 视频付费分润域)
</details>

<details>
<summary>🆕 支付模块怎么抽象？</summary>
→ 读 [18+ 视频付费分润方案 · 三、模块职责 · 3.1 payment](18+ 视频付费分润方案.md#三模块职责)
→ 读 [schema/09_payment_domain.sql](schema/09_payment_domain.sql) (4 张表 DDL)
</details>

<details>
<summary>🆕 18+ 合规怎么落地？</summary>
→ 读 [18+ 视频付费分润方案 · 六、18+ 合规架构](18+ 视频付费分润方案.md#六18-合规架构)
→ 读 [数据架构 · 九、对象存储 · 9.2 视频内容存储策略](数据架构.md#九对象存储-minio)
</details>

---

## 📁 相关资料

| 位置 | 文件 | 说明 |
|------|------|------|
| `docs/` | `star-ai-技术尽职调查报告.pdf` | 外部技术尽调报告 |
| `docs/` | `star-ai-系统架构分析报告.md` | 前端 RSC Payload 逆向分析 |
| 根目录 | `pom.xml` | 父 POM，管理版本 + CVE 修复 |
| `star-ai-boot-base-core/pom.xml` | 核心依赖 | base-core 完整依赖树 |
| 各微服务 | `Dockerfile` | 生产级镜像构建模板 |

---

## 🛠️ 技术栈速查

```
Spring Boot 3.3.11
├── Spring Cloud 2023.0.4
│   ├── Spring Cloud Alibaba 2023.0.3.4
│   │   ├── Nacos 2.4.3        ← 注册中心 + 配置中心
│   │   ├── Sentinel           ← 限流熔断
│   │   └── Seata 2.0.0        ← 分布式事务
│   ├── Spring Cloud Gateway 4.1.8  ← API 网关 (WebFlux)
│   └── OpenFeign 13.5 + OkHttp     ← 声明式 HTTP 客户端
├── Sa-Token 1.44.0 + JWT      ← 鉴权/权限/2FA
├── MyBatis-Plus 3.5.5         ← ORM + 多租户
├── Druid 1.2.22 + HikariCP    ← 连接池
├── MySQL 8.4.0                ← 主存储
├── Redis + Redisson 3.27.2    ← 缓存/锁/队列
├── RabbitMQ 5.29.0            ← 异步消息
├── XXL-JOB 3.2.0              ← 定时任务
├── MinIO 8.6.0                ← 对象存储
├── BouncyCastle 1.85          ← 加密库
├── OWASP Java HTML Sanitizer  ← XSS 防护
├── Flyway                     ← DB 迁移
└── Knife4j 4.5.0              ← API 文档 (dev)
```

---

## 📞 架构维护

本架构文档由后端架构团队维护。变更时请同步更新：

1. 新增/删除微服务 → 更新 `系统架构总览 · 二、整体架构图` + `模块依赖关系`
2. 新增业务域 → 更新 `业务架构`
3. 新增安全措施 → 更新 `安全架构`
4. 变更部署方式 → 更新 `部署架构`

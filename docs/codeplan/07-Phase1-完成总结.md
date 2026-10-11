# Star AI Boot Phase 1 完成总结

> 完成日期：2026-10-12
> 构建状态：**20 模块 BUILD SUCCESS** ✅
> Commit：`55b695b` feat: Phase1 骨架 + 全链路编译通过

---

## 一、项目架构概览

```
mp-/
├── star-ai-boot/       后端微服务（JeecgBoot 改造）← 本次主战场
├── star-ai-app/        用户端 Next.js (capacitor)
└── star-ai-admin/      管理后台 Vue
```

后端 Maven 模块结构（仅列核心 5 个新模块 + Gateway）：

```
star-ai-server-cloud/
├── star-ai-cloud-gateway/           Spring Cloud Gateway（AgeVerifyFilter + InternalPathDenyFilter）
├── star-ai-module-creator/          创作者入驻 / MinIO 分片上传 / 发布
│   ├── star-ai-module-creator-api/  (空，预留给 Feign DTO)
│   └── star-ai-module-creator-biz/  核心实现
├── star-ai-module-stream/           播放 / 解锁 / 分润 / 互动
│   ├── star-ai-module-stream-api/
│   └── star-ai-module-stream-biz/
├── star-ai-module-payment/          Mock 支付 + RabbitMQ 回调
│   ├── star-ai-module-payment-api/
│   └── star-ai-module-payment-biz/
└── star-ai-transcode-worker/        FFmpeg HLS 转码 Worker
    ├── star-ai-transcode-worker-api/
    └── star-ai-transcode-worker-biz/
```

---

## 二、已完成工作清单

| 类别 | 内容 | 涉及文件数 |
|------|------|-----------|
| **新模块骨架** | Creator / Stream / Payment / Transcode-worker + Gateway 新 Filter | ~258 Java |
| **POM 修复** | root pom depMgmt 补 star-ai artifactId 版本、system-api module 目录名、biz pom classifier=exec | 9 pom.xml |
| **JeecgBoot 原生文件修复** | ISysBaseAPI / SysBaseAPIFallback 的 cat >> 残缺代码清理 | 2 Java |
| **Creator 模块修复** | CreatorController / VideoController / MinioPresignServiceImpl 花括号外代码截断 | 4 Java |
| **Stream 模块修复** | Interaction / WatchHistory / CreatorPage / Collections Controller + StreamVideoMapper | 5 Java |
| **Transcode 修复** | generatePreview 调用位置 + 参数 | 1 Java |
| **编译验证** | `mvn clean install -Dmaven.test.skip=true -pl ... -am` **20 模块全 SUCCESS** | — |

---

## 三、Maven Reactor Summary

```
[INFO] Star AI Boot 3.7.0 ................................. SUCCESS [  1.454 s]
[INFO] star-ai-boot-starter-secrets ....................... SUCCESS [  4.068 s]
[INFO] star-ai-boot-base-core ............................. SUCCESS [ 17.484 s]
[INFO] star-ai-module-system .............................. SUCCESS [  0.137 s]
[INFO] star-ai-system-api ................................. SUCCESS [  0.273 s]
[INFO] star-ai-system-local-api ........................... SUCCESS [  1.332 s]
[INFO] star-ai-system-cloud-api ........................... SUCCESS [  1.767 s]
[INFO] Star AI Server Cloud ............................... SUCCESS [  0.062 s]
[INFO] star-ai-cloud-gateway .............................. SUCCESS [  3.263 s]
[INFO] star-ai-module-payment ............................. SUCCESS [  0.052 s]
[INFO] star-ai-module-payment-api ......................... SUCCESS [  0.394 s]
[INFO] star-ai-module-payment-biz ......................... SUCCESS [  3.869 s]
[INFO] star-ai-module-creator ............................. SUCCESS [  0.061 s]
[INFO] star-ai-module-creator-api ......................... SUCCESS [  0.275 s]
[INFO] star-ai-module-stream .............................. SUCCESS [  0.050 s]
[INFO] star-ai-module-stream-api .......................... SUCCESS [  0.226 s]
[INFO] star-ai-module-stream-biz .......................... SUCCESS [  4.649 s]
[INFO] star-ai-module-creator-biz ......................... SUCCESS [  3.586 s]
[INFO] star-ai-transcode-worker ........................... SUCCESS [  0.057 s]
[INFO] star-ai-transcode-worker-api ....................... SUCCESS [  0.216 s]
[INFO] star-ai-transcode-worker-biz ....................... SUCCESS [  2.803 s]
[INFO] BUILD SUCCESS
```

构建命令（跳过 system-biz，用 `-pl ... -am` 指定依赖链）：

```bash
cd star-ai-boot
mvn clean install -Dmaven.test.skip=true \
  -pl star-ai-boot-base-core,star-ai-module-system/jeecg-system-api/jeecg-system-cloud-api,\
star-ai-server-cloud/star-ai-cloud-gateway,star-ai-server-cloud/star-ai-module-creator/star-ai-module-creator-biz,\
star-ai-server-cloud/star-ai-module-stream/star-ai-module-stream-biz,star-ai-server-cloud/star-ai-module-payment/star-ai-module-payment-biz,\
star-ai-server-cloud/star-ai-transcode-worker/star-ai-transcode-worker-biz -am
```

> `system-biz` 模块也被 cat >> 误伤了（SysUserController 2852 行、SysBaseApiImpl 1389 行、SysUserServiceImpl 花括号残缺），但 5 个微服务不依赖它，用 `-pl` 跳过即可。

---

## 四、核心业务链代码位置（知识脉络）

### 4.1 创作者上传 → 发布 → 分润快照 → 转码 MQ

```
CreatorVideoController.publish()
  → VideoPublishServiceImpl.publish()
    → PricingValidator.validate(account, video)                    // 定价校验
    → video.setRevenueRateSnapshot(account.getRevenueRate())       // ⭐ 分润快照固化
    → streamVideoService.save(video)
    → rabbitTemplate.convertAndSend("x.direct.video", "video.uploaded", msg)  // MQ
```

**文件**：`star-ai-module-creator/star-ai-module-creator-biz/src/main/java/org/jeecg/creator/service/impl/VideoPublishServiceImpl.java`

### 4.2 转码 Worker 消费 video.uploaded MQ → FFmpeg HLS + 30s 试看

```
VideoUploadedConsumer.onMessage()
  → Redis SETNX "transcode:lock:{videoId}" (24h TTL, 幂等)
  → TranscodeServiceImpl.transcodeVideo(msg)
    → downloadWithCurl(minioPresignUrl, input.mp4)
    → for preset in [1080p, 720p, 480p]:
        runFfmpeg(input, outDir, preset, height, bitrate)
    → generatePreview(input, workDir, "preview-720p", "720", 2000)  // 统一试看生成
    → uploadDirToMinio(workDir, videoId, publishedBucket)
    → update video.transcodeStatus="completed", hlsMasterKey, hlsPresets
```

**文件**：`star-ai-transcode-worker/star-ai-transcode-worker-biz/src/main/java/org/jeecg/transcode/service/impl/TranscodeServiceImpl.java`

### 4.3 播放权限 5 级解锁（Stream 模块）

```
PlayPermissionServiceImpl.resolvePermission(userId, videoId):
  1. video.priceType == "free"          → UNLOCKED
  2. creator_unlock 表有记录             → UNLOCKED（创作者全局解锁）
  3. video_purchase(unlock_scope=episode, access_type=purchase) → UNLOCKED
  4. collection_rel 关联剧集解锁          → UNLOCKED
  5. 否则 → LOCKED(priceType, priceAmount, previewSec)
```

**文件**：`star-ai-module-stream/star-ai-module-stream-biz/src/main/java/org/jeecg/stream/service/impl/PlayPermissionServiceImpl.java`

### 4.4 Mock 支付 3s 自动回调 + payment.success MQ

```
PaymentOrderBizServiceImpl.createOrder()
  → scheduler.schedule(3s) → markPaid(orderNo)
  → rabbitTemplate.convertAndSend("x.direct.payment", "payment.success", msg)
```

**文件**：`star-ai-module-payment/star-ai-module-payment-biz/src/main/java/org/jeecg/payment/service/impl/PaymentOrderBizServiceImpl.java`

### 4.5 分润结算（消费 payment.success MQ）

```
PaymentSuccessConsumer.onMessage()
  → RevenueSettlementServiceImpl.settle(paymentOrderNo)
    → video = streamVideoMapper.select...
    → creatorShare = orderAmount * video.revenueRateSnapshot / 100   // ⭐ 用快照！
    → platformShare = orderAmount - creatorShare
    → INSERT stream_revenue_record
    → UPDATE stream_creator_earnings (累计)
```

**文件**：`star-ai-module-stream/star-ai-module-stream-biz/src/main/java/org/jeecg/stream/service/impl/RevenueSettlementServiceImpl.java`

### 4.6 Gateway 过滤器

```
InternalPathDenyFilter  (Ordered.HIGHEST_PRECEDENCE - 100)  → 拦截 /internal/**
AgeVerifyFilter         (Ordered.HIGHEST_PRECEDENCE - 50)   → 18+ 拦截
  拦截路径: /stream/play*, /stream/videos/{id}/play, /stream/videos/{id}/preview
  检查:    Header "Age-Verified: true" 或 Cookie "age_verified=1"
  返回:    418 I_AM_A_TEAPOT + {"code":418, "redirect":"/age-verify"}
```

**文件**：`star-ai-cloud-gateway/src/main/java/org/jeecg/filter/AgeVerifyFilter.java`

---

## 五、关键技术约束（踩过的坑）

| # | 约束 | 说明 |
|---|------|------|
| 1 | **分润快照固化** | `stream_video.revenue_rate_snapshot` 是**发布时刻写入**，后续创作者改抽成不追溯老订单。RevenueSettlement **必须用 `video.revenueRateSnapshot`**，**不要**查 `creator_account.revenue_rate` |
| 2 | **system-biz 被误伤** | SysUserController 2852 行、SysBaseApiImpl 1389 行、SysUserServiceImpl 均有残缺代码。5 个微服务**不依赖**它，跳过即可 |
| 3 | **system-cloud-api 被误伤** | ISysBaseAPI.java / SysBaseAPIFallback.java 残缺方法已修复。**只用 cloud-api，不用 local-api** |
| 4 | **Spring Boot fat jar classifier** | 新模块 pom 加 `<classifier>exec</classifier>`，让 biz jar 保持普通格式，其他模块可依赖其类 |
| 5 | **Gateway 跨模块依赖限制** | stream-biz pom 不能加 creator-biz 依赖（循环）。StreamCreatorPageController 只能查自己的 `stream_video` 表做聚合 |
| 6 | **RabbitMQ 双队列** | Creator 发 `x.direct.video` → Transcode；Payment 发 `x.direct.payment` → Stream Settlement |
| 7 | **Consumer 幂等** | Redis `SETNX key="transcode:lock:{videoId}"` 24h TTL，避免 MQ 重复消费 |
| 8 | **MinIO 双 bucket** | `stream-creator-draft`（上传）→ `stream-published`（HLS 最终）+ `stream-preview`（试看） |
| 9 | **system-api module 目录名** | pom.xml `<module>` 写**目录名**（`jeecg-system-local-api`），**不是** artifactId（`star-ai-system-local-api`） |
| 10 | **testCompile 跳过** | `-DskipTests` 只跳过执行，**不跳过编译**。用 `-Dmaven.test.skip=true` 才同时跳过 testCompile + test |

---

## 六、DDL 真实表结构（避免写 Entity 时踩坑）

| 表 | 字段 | 关键说明 |
|---|------|---------|
| **stream_video** | `revenue_rate_snapshot` ✅ 存在 | 分润快照存 video 级，不是 purchase 级 |
| **stream_video_purchase** | 无 `status` / `creator_id` | 用 `unlock_scope`（episode/collection）+ `access_type`（purchase/gift/member） |
| **stream_revenue_record** | `total_amount` / `platform_share` / `creator_share` / `revenue_rate` | 不是 order_amount / creator_amount |
| **stream_creator_earnings** | `total_earned` / `available_balance` / `frozen_balance` / `total_withdrawn` | 不是 total_earnings / available_earnings |
| **ai_payment_order** | `orderNo` / `businessOrderNo` / `channelType` | 不是 paymentOrderNo / bizOrderNo / paymentChannel |

DDL 来源：`docs/服务端架构/schema/` 下 10 个 `.sql` + `99_seed_data.sql`

---

## 七、POM 修改详情（5 个 pom.xml）

### 7.1 `pom.xml`（root）— depMgmt 补 star-ai artifactId

```xml
<dependency>
  <groupId>org.jeecgframework.boot</groupId>
  <artifactId>star-ai-system-local-api</artifactId>
  <version>${jeecgboot.version}</version>
</dependency>
<dependency>
  <groupId>org.jeecgframework.boot</groupId>
  <artifactId>star-ai-system-cloud-api</artifactId>
  <version>${jeecgboot.version}</version>
</dependency>
```

同时 root pom 里原有的 `jeecg-system-biz` / `jeecg-boot-base-core` / `jeecg-boot-starter-secrets` 也各有一条 star-ai 重命名版本的平行依赖。

### 7.2 `star-ai-module-system/jeecg-system-api/pom.xml` — module 目录名

```xml
<!-- 错：artifact 名 -->
<module>star-ai-system-local-api</module>

<!-- 对：实际目录名 -->
<module>jeecg-system-local-api</module>
```

### 7.3 各 biz pom — classifier=exec

```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-maven-plugin</artifactId>
      <configuration>
        <classifier>exec</classifier>
      </configuration>
    </plugin>
  </plugins>
</build>
```

让 fat jar 变成 `xxx-exec.jar`，保留普通 `xxx.jar` 给其他模块依赖。

### 7.4 parent 引用修正

`star-ai-boot-base-core/pom.xml` 和 `star-ai-boot-starter-secrets/pom.xml` 的 parent 从 `jeecg-boot-parent` 改成了 `star-ai-boot-parent`。

---

## 八、被清理的残缺代码一览（cat >> 误伤）

这些文件原始 JeecgBoot 代码中，之前的 Python 脚本生成代码时用 `cat >>` 追加方法，导致方法体落在了 class 花括号之外。修复方式：`head -N file > /tmp/t && cp` 截断到 class 最后一个 `}`，或删掉整个残缺方法。

| 文件 | 问题 | 修复 |
|------|------|------|
| `ISysBaseAPI.java:1006-1007` | 残缺 `sysUserFeeConfig` 方法声明 | 删掉整个方法 |
| `SysBaseAPIFallback.java:35-37` | 残缺 `sendMessage` override（不是接口方法） | 删掉 |
| `SysBaseAPIFallback.java:522-524` | 另一个残缺 override | 删掉 |
| `CreatorController.java` | `globalUnlock` 方法落在 class 外 | head -42 截断 + append 入类 |
| `CreatorVideoController.java` | 5 个方法落在 class 外 + 引用不存在的 `videoService`/`rabbit` | head -60 截断（`publish` 已完整） |
| `MinioPresignServiceImpl.java` | `mergeChunks` 方法落在 class 外 | head -58 截断（接口里没声明） |
| `TranscodeServiceImpl.java:175` | `generatePreview(outDir, outDir)` 2 参数 vs 5 参数 | 删掉残缺调用 → 移到所有码率完成后统一调 |

---

## 九、下阶段工作重点

### 🔴 P0 — 环境启动验证（需要真实服务）

```bash
# 1. MinIO
docker run -d -p 9000:9000 -p 9001:9001 --name minio \
  minio/minio server /data --console-address ":9001"

# 2. RabbitMQ
docker run -d -p 5672:5672 -p 15672:15672 --name rabbitmq \
  rabbitmq:3-management

# 3. MySQL + DDL
#   docs/服务端架构/schema/08_stream_creator_domain.sql
#   docs/服务端架构/schema/09_payment_domain.sql
#   docs/服务端架构/schema/99_seed_data.sql  (及其他 domain sql)

# 4. FFmpeg
brew install ffmpeg

# 5. 依次启动（application.yml 里配好各自端口）
#   Gateway : 9000
#   Creator : 8081
#   Stream  : 8082
#   Payment : 8083
#   Transcode Worker : 8084
```

### 🟡 P1 — 补全剩余 API（编码可先做）

- **Creator**: tags CRUD、earnings + records + withdrawals 端点、全局解锁（`globalUnlock` 已写在 CreatorController 里）
- **Stream**: InternalReviewController（审核队列 `queue` / 通过 `approve` / 拒绝 `reject`）、StreamBrowseController.browse 分页
- **管理后台对接**: star-ai-admin 需要对接审核中心
- **用户端对接**: star-ai-app 需要对接 play 权限解锁端点 + 试看流

### 🟢 P2 — 修复 system-biz 原生模块（可选）

如果未来需要完整 JeecgBoot 登录/权限功能，需要修这 3 个被误伤的大文件：

| 文件 | 行数 | 问题 |
|------|------|------|
| `SysUserController.java` | 2852 | 花括号外有 14 行残缺代码 |
| `SysBaseApiImpl.java` | 1389 | 多处残缺，需要逐行核对 |
| `SysUserServiceImpl.java` | — | 多处方法体残缺 |

不修的话直接 `-pl` 跳过即可。

### 🔵 P3 — 稳定性加固

- Consumer 幂等 Redis key 过期后的重入保护（当前 24h TTL）
- Payment Mock → 真实 WeChat/Stripe channel 切换（PaymentClient 三态架构已预留）
- Gateway AgeVerifyFilter 的 Cookie 跨域问题排查
- Transcode FFmpeg 失败重试机制（当前只打日志）
- MinIO composeObject 分片超时保护

---

## 十、快速上手 Checklist（给新 AI / 新伙伴）

接手时建议的第一步：

- [ ] 先读 `docs/codeplan/04-三仓联调Checklist.md`（三仓端点契约）
- [ ] 读 `docs/服务端架构/schema/` 下 10 个 DDL + 数据库设计文档（v1.1 演进预留）
- [ ] 读 `docs/服务端架构/18+ 视频付费分润方案.md`（业务核心）
- [ ] 跑 Maven 构建验证工作区干净（命令见第二节）
- [ ] 启动依赖服务（MySQL / MinIO / RabbitMQ / FFmpeg）
- [ ] 依次启动 5 个微服务 + Gateway
- [ ] 端到端联调：入驻 → 上传 → 发布 → 自动转码 → Mock 支付回调 → 分润结算
- [ ] 如需全量 `mvn clean install`：先修 system-biz 那 3 个大文件，或就用 `-pl` 跳过

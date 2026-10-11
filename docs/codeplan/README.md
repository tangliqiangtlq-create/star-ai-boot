# Phase 1 · 全栈 Code Plan 索引

> 阶段：Phase 1 MVP · 非 AI 编辑器全链路
> 日期：2026-10-11
> 三仓联动：`star-ai-boot`（后端）· `star-ai-admin`（管理后台）· `star-ai-app`（用户端+创作者端）

---

## 包含的功能

| 域 | 功能 |
|----|------|
| 创作者端 creator | 入驻 / MinIO 分片直传 / FFmpeg 转码 / 发布审核 / 自助定价（三级区间校验）/ 全局解锁 / 剧集管理 / 收益看板 / 提现申请（Mock） |
| 用户消费端 stream | 视频列表/搜索/详情 / 30s 试看 / HLS 多码率播放 / 单集&剧集&全局解锁付费 / 观看历史 / 点赞收藏 / 播放埋点 |
| 平台运营后台 | 数据看板 / 审核中心 / 订单管理 / 创作者管理 / 提现审核 / 对账报表 / 定价区间配置 |
| 分润 stream_revenue | 交易驱动分润，订单成功固化 `revenue_rate_snapshot`（快照不可变） |
| Payment | 接口定义 + Mock 实现，渠道确认后 Step 5 替换 |
| 18+ 合规 | 注册强制 age_verified + Gateway Filter 拦截 |

## 不在本阶段

自动审核 NSFW、订阅池月度分润跑批、transcode-worker 独立进程、CloudFront/AWS S3、Payment 真实 SDK、提现真实打款。

---

## 文件清单

| 文件 | 内容 |
|------|------|
| [`00-阶段总览.md`](./00-阶段总览.md) | 工期 14 天 + 并行甘特图 + 里程碑 + 端口汇总 |
| [`01-后端-服务端CodePlan.md`](./01-后端-服务端CodePlan.md) | star-ai-boot · 7 Steps · 模块骨架 + Entity/Mapper/Service/Controller/MQ 到类级别 |
| [`02-前端-管理后台CodePlan.md`](./02-前端-管理后台CodePlan.md) | star-ai-admin · 3 Steps · 8 个页面（每个页面对应 prototype 原型文件） |
| [`03-前端-用户端创作者端CodePlan.md`](./03-前端-用户端创作者端CodePlan.md) | star-ai-app · 5 Steps · hls.js 多码率播放器 + MinIO 分片上传 + 付费引导 + 创作者端 |
| [`04-三仓联调Checklist.md`](./04-三仓联调Checklist.md) | 端到端 19 步完整链路验证 + 分润快照固化专项验证 |
| [`05-API契约规范.md`](./05-API契约规范.md) | 统一 R\<T\> 返回 + 分页 + Error Code 表 + Feign Client 规范 |
| [`06-Payment-Mock切换方案.md`](./06-Payment-Mock切换方案.md) | PaymentClient 接口 + Mock 实现 + Spring Profile 切换真实渠道 |

---

## 核心设计原则（三个文件都要对齐）

1. **后端 Day 1-3 必须产出 API 契约**（Swagger + DTO），前端不依赖后端业务逻辑完成，Mock 数据先行
2. **分润快照固化**：`stream_video.revenue_rate_snapshot` 在发布时固化，订单分润取快照不取实时 `creator_account.revenue_rate`
3. **Payment 延后**：PaymentClient 接口先定，Mock 实现先跑通全链路，渠道确认后 Step 5 换真实实现
4. **Sa-Token 三仓共享**：前端统一带 `X-Access-Token`，Gateway Redis 校验
5. **InternalPathDenyFilter**：所有 `/internal/**` 路由外部 IP 直接 403

---

## 快速跳转

```
想知道整体工期？        → 00-阶段总览.md
想写后端？             → 01-后端-服务端CodePlan.md
想写管理后台？         → 02-前端-管理后台CodePlan.md
想写用户端+创作者端？  → 03-前端-用户端创作者端CodePlan.md
想联调验收？           → 04-三仓联调Checklist.md
想查 API 返回格式？    → 05-API契约规范.md
想切 Payment 真实渠道？→ 06-Payment-Mock切换方案.md
```

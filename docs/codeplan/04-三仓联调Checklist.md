# Phase 1 · 三仓联调 Checklist

> 位置：`docs/codeplan/04-三仓联调Checklist.md`
> Day 13-14 执行，所有步骤通过才算 Phase 1 完成

---

## 前置条件

- [ ] 6 个基础服务启动：MySQL + Redis + RabbitMQ + Nacos + MinIO + Sentinel
- [ ] 13 个后端服务启动顺序完成（见 `00-阶段总览.md` 端口汇总）
- [ ] Gateway 8080 启动，路由可达
- [ ] star-ai-admin 3002 启动，star-ai-app 3000 启动
- [ ] DDL 22 张表已执行 + seed_data

---

## Part 1 · 端到端完整链路（19 步）

### 阶段 A：创作者端

| # | 操作 | 预期结果 | 验证点 |
|---|------|----------|--------|
| 1 | 创作者注册 → 强制年龄验证（身份证） → 入驻申请 POST /creators/apply | `creator_account.status=pending`，`age_verified=1` | 未验证会被 Gateway 403 拦截 |
| 2 | 创作者进入 Dashboard → 选择 mp4 文件上传 | `POST /creator/videos/upload-init` 返回分片 presigned URL；前端分片 5MB × 3 并发；进度条 0-100% | **浏览器网络面板看到 PUT MinIO presigned URL（不是打后端）** |
| 3 | 上传完成 → 点"完成上传" | `POST /creator/videos/{id}/complete` → 后端合并 MinIO composeObject → 发 MQ `video.uploaded` | RabbitMQ Management 看到 `q.video.uploaded` 有消息 |
| 4 | 等待 ~30s | stream-service 消费 MQ → `VideoTranscodeService` 执行 FFmpeg → 多码率 HLS 落 MinIO `stream-hls` bucket → `stream_video.transcode_status=completed` | MinIO `stream-hls/{creatorId}/{videoId}/` 下有 master.m3u8 + output_0.m3u8/1/2 |

### 阶段 B：创作者编辑 + 发布

| # | 操作 | 预期结果 | 验证点 |
|---|------|----------|--------|
| 5 | 创作者进入 `/creator/videos/[id]/edit` → 填标题"测试视频1" → 选分类 → 填定价 ¥19.99 | 前端实时提示"建议定价区间：¥9.99 - ¥99.99"；不显示红色 | 区间来自 `creator_account.price_amount_min/max` |
| 6 | 定价 ¥1999（超出）→ 看前端提示 | 红色提示"超出最高定价 ¥99.99" | 前端校验 + 后端 `PricingValidator` 双重校验 |
| 7 | 改回 ¥19.99 → 点击发布 | `POST /creator/videos/{id}/publish` → **后端固化 `revenue_rate_snapshot`** → `publish_status=pending_review` | 查 stream_video 表，`revenue_rate_snapshot=30.00`（creator_account.revenue_rate 的当前值） |

### 阶段 C：管理后台审核

| # | 操作 | 预期结果 | 验证点 |
|---|------|----------|--------|
| 8 | admin 登录 → 审核中心 `/review` | Table 看到"测试视频1"，状态"待审核" | 数据来自 `GET /internal/stream/review/queue` |
| 9 | admin 点"通过" | `POST /internal/stream/review/{id}/approve` → `publish_status=published` | 查 DB 确认状态变化 |

### 阶段 D：用户端消费

| # | 操作 | 预期结果 | 验证点 |
|---|------|----------|--------|
| 10 | 用户进入首页 `/` | 瀑布流看到"测试视频1"，标签显示"¥19.99" | 数据来自 `GET /stream/videos?sort=hot&publish_status=published` |
| 11 | 点击视频进入 `/video/[id]` | hls.js 拉 `stream-preview/{videoId}/preview/master.m3u8` 试看流；视频开始播放 | Chrome DevTools Network 看到 preview m3u8 + ts 分片请求 |
| 12 | 播放到 29s | 视频自动 pause；弹出 PaywallModal | hls.js 30s 截断（后端 FFmpeg 截流） + 前端 timeupdate 监听 |
| 13 | PaywallModal → 选"单集 ¥19.99" | `POST /stream/orders` → Mock PaymentClient 直接返回 orderNo + paid 状态 | Mock 模式下**不调微信/Stripe SDK**，直接落库 `ai_payment_order(status=paid)` |
| 14 | Mock 异步发 MQ `payment.success` | `PaymentSuccessConsumer` 消费 → `stream_purchase.status=paid` + **固化分润** + `creator_earnings` 累加 | RabbitMQ → q.payment.success 有消费 |
| 15 | PaywallModal 自动关闭 → hls.js 重新拉 `play` URL | hls.loadSource(newPlayUrl) → 完整 1080p m3u8 + ts 分片；播放不再被 30s 截断 | Network 看到完整 `stream-hls/{creatorId}/{videoId}/master.m3u8`（不是 preview） |
| 16 | 播放完整个视频 | 自动调 `POST /stream/videos/{id}/watch-end` → `stream_play_history(is_effective=true, completion_rate=100, is_member_view=false)` | 查 DB `stream_play_history` 表有新记录 |

### 阶段 E：创作者收益看板 + 分润快照固化验证

| # | 操作 | 预期结果 | 验证点 |
|---|------|----------|--------|
| 17 | 创作者进入 `/creator/earnings` | 看到 `total_earnings: ¥5.99`（¥19.99 × 30%）；月度表有 2026-10 行；订单列表有刚卖的这单 | 数据来自 `GET /creator/earnings` → 聚合 `stream_revenue_record` |
| 18 | **admin 改创作者抽成率** | admin 定价区间配置页 → 把 revenue_rate 从 30% 改成 40% | 查 DB `creator_account.revenue_rate=40.00` |
| 19 | **创作者发布新视频 → 新用户再买老视频** | 新视频 snapshot=40% ✅（生效）；老视频再有人付费 → **stream_revenue_record.snapshot=30%** ✅（固化不变！） | **这是 Phase 1 核心验证**。后端取的是 video.revenue_rate_snapshot（发布时固化），不是 creator_account.revenue_rate（实时值） |

---

## Part 2 · Gateway + 安全

| # | 测试 | 命令 | 预期 |
|---|------|------|------|
| G1 | Gateway 路由 creator | `curl http://localhost:8080/creators/apply -X POST -H "X-Access-Token: xxx"` | 正确转发到 7012，不是 404 |
| G2 | Gateway 路由 stream | `curl http://localhost:8080/stream/videos?current=1&size=10 -H "X-Access-Token: xxx"` | 正确转发到 7011 |
| G3 | InternalPathDenyFilter 拦截 | `curl http://localhost:8080/internal/stream/review/queue -H "X-Access-Token: xxx"` | **403 Forbidden**（InternalPathDenyFilter，外部 IP 不能直调） |
| G4 | 未登录 Sa-Token | `curl http://localhost:8080/stream/videos`（不带 token） | 401 |
| G5 | 未年龄验证 | 用 age_verified=0 的用户 token 调 `/stream/videos/{id}/play` | 403 + `{"code":418,"msg":"请先完成年龄验证"}` |

---

## Part 3 · RabbitMQ 消息流

| # | 测试 | 观察点 |
|---|------|--------|
| M1 | 上传完成 → video.uploaded | RabbitMQ Management → q.video.uploaded 有消息 ready → unacked → ack 完成 |
| M2 | 支付成功 → payment.success | q.payment.success 同上 |
| M3 | Consumer 幂等性 | 用同一事件重复发 2 次，stream_revenue_record 只新增 1 条（有幂等 check） |

---

## Part 4 · FFmpeg 转码产物

| # | 验证 |
|---|------|
| F1 | MinIO `stream-hls/{creatorId}/{videoId}/` 下有 master.m3u8 + output_0.m3u8(1080p) + output_1.m3u8(720p) + output_2.m3u8(480p) + 对应 ts 分片 |
| F2 | master.m3u8 里有 3 个 #EXT-X-STREAM-INF variant |
| F3 | iOS Safari 手机浏览器打开 hls.js 播放页 → 看到多码率下拉切换 |

---

## Part 5 · 前端 UI 完整性

| # | 验证 |
|---|------|
| FE1 | admin 8 页面全部能看到数据（接真实 API 后） |
| FE2 | PaywallModal 4 态：单集¥ / 剧集¥ / 全局¥ / 免费（根据 unlockScope 不同） |
| FE3 | MinIO 分片上传：3 并发时显示 3 条进度条 |
| FE4 | Capacitor Android APK 能安装到模拟器 → hls.js 能播放 |

---

## 一键联调脚本（建议写一个）

```bash
#!/bin/bash
# scripts/phase1-e2e.sh
# 跑完整 19 步端到端链路

BASE=http://localhost:8080
TOKEN=""  # 先调 /sys/login 拿

echo "=== 1. 登录拿 token ==="
TOKEN=$(curl -s -X POST $BASE/sys/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"xxx"}' \
  | jq -r '.result.token')

echo "=== 2. 创建创作者账号 ==="
curl -X POST $BASE/creators/apply -H "X-Access-Token: $TOKEN" -H "Content-Type: application/json" \
  -d '{"nickname":"test_creator","age_verified":1}'

echo "=== 3. 检查 19 步每步预期 ==="
for i in $(seq 1 19); do
  echo "[CHECK $i] pending..."
done
```

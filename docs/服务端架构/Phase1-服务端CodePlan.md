# Star AI Boot · Phase 1 全栈 Code Plan

> 日期：2026-10-11 v3 | 三仓联动版
> **Phase 1 目标**：创作者端 + 平台运营后台 + 用户消费端 + 分润 —— 全链路（非 AI 编辑器）
> 三仓联动：`star-ai-boot`（后端）· `star-ai-admin`（管理后台）· `star-ai-app`（用户端+创作者端）
> ⚠️ Payment 延后到最后（渠道未确定，先 Mock）

---

## 总体工期与并行关系

```
Day  1   2   3   4   5   6   7   8   9   10  11  12  13  14
     ├───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┼───┤
后端 │  Step 0 基础设施 + 模块骨架  │                   │
     │  Step 1 Creator 入驻/上传/定价 │                 │
     │              Step 2 Stream 消费+分润  │         │
     │                      Step 3 管理后台 API Step4 │
     │                                    Step5 Payment│
     └─────────────────────────────────────────────────┘

前端 ┌─────────────────────────────┐┌──────────────────┐
admin│ Step A 搭建 Layout + 菜单   ││ Step C 接真实 API │
     │ Step B 8 个页面 (Mock 数据) ││ 联调 + UI 打磨    │
     └─────────────────────────────┘└──────────────────┘

前端 ┌─────────────────────────────────┐┌────────────────┐
app  │ Step A 首页/剧集/订阅 3 页面     ││ Step D 接 API   │
     │ Step B 视频详情 + hls.js 播放器  ││ Step E 联调     │
     │ Step C 创作者端（入驻/上传/定价） ││                 │
     └─────────────────────────────────┘└────────────────┘
                                   ↓ 后端 API ready 后
                            前端从 Mock baseURL 切真实 baseURL
```

**核心策略**：后端 Day 1-3 必须产出 **API 契约（DTO + Controller 接口 + Swagger）**，前端从 Day 1 就开始写页面 + Mock 数据，不需要等后端业务逻辑写完。接口签名固定后，前端改 `VITE_API_BASE_URL` 就切真实接口。

---

## Part 1 · 后端 star-ai-boot（7 Steps，约 11 天）

### Step 0 · 基础设施（Day 1，1 天）

| # | 任务 |
|---|------|
| 0.1 | `star-ai-server-cloud/pom.xml` 新增 4 个 module |
| 0.2 | 按 billing 模板复制骨架（payment / creator / stream / transcode-worker），改 artifactId + package |
| 0.3 | biz 模块启动注解 `@SpringBootApplication` + `@EnableFeignClients` + `@MapperScan` |
| 0.4 | Gateway 路由 YAML（creator/stream/payment + internal 保护路径） |
| 0.5 | RabbitMQ Exchange/Queue/Binding 配置类 |
| 0.6 | 执行 DDL：08 + 09 + 10 + Part 11（22 张表）+ 99_seed_data |
| 0.7 | Sentinel 4 个资源限流规则 + Nacos 配置 |

### Step 1 · Creator 创作者中心（Day 2-5，4 天）

**核心 API 产出（Day 3 前必须出 Swagger，前端开始对接）**：

| 路径 | 方法 | Controller | 说明 | 前端谁用 |
|------|------|------------|------|----------|
| `/creators/apply` | POST | CreatorController | 入驻申请（age_verified 强制） | app 创作者入驻页 |
| `/creator/videos/upload-init` | POST | CreatorVideoController | MinIO presigned 分片 URL | app 创作者上传组件 |
| `/creator/videos/{id}/complete` | POST | CreatorVideoController | 合并分片 + 发 MQ | app 创作者上传组件 |
| `/creator/videos/{id}` | PUT | CreatorVideoController | 创作者定价（区间校验） | app 视频编辑表单 |
| `/creator/videos/{id}/publish` | POST | CreatorVideoController | 发布（固化 snapshot → 自动审核） | app 发布按钮 |
| `/creator/global-unlock` | PUT | CreatorController | 全局解锁定价 | app 创作者中心 |
| `/creator/collections` | POST/PUT | CreatorCollectionController | 剧集管理 | app 剧集管理 |
| `/creator/earnings` | GET | CreatorEarningsController | 收益看板 | app 创作者中心 |
| `/withdrawals` | POST | CreatorWithdrawalController | 提现申请（先 Mock） | app 创作者中心 |

**核心业务规则**（Day 3 前实现完）：

```
① 创作者定价校验：
   creator_account.pricing_enabled = 1
   AND price_amount BETWEEN price_amount_min AND price_amount_max
   → auto_publish_price=1 则 price_status=approved，否则 pending

② 发布时固化抽成快照：
   stream_video.revenue_rate_snapshot = creator_account.revenue_rate
   （取创建 video 当下的 creator_account.revenue_rate，后续修改不影响已发布视频）

③ 上传 → 发布 状态机：
   draft → transcode_pending → transcoding → transcode_failed/transcode_completed
   → publishing → pending_review → published / rejected
```

### Step 2 · Stream 消费 + 分润（Day 5-10，6 天）

**核心 API 产出（Day 7 前 Swagger 齐全，前端对接）**：

| 路径 | 方法 | Controller | 说明 | 前端谁用 |
|------|------|------------|------|----------|
| `/stream/videos` | GET | StreamVideoController | 分页 + 分类 + 排序列表 | app 首页 Browse |
| `/stream/videos/{id}` | GET | StreamVideoController | 详情（含作者/剧集/解锁状态） | app 视频详情页 |
| `/stream/videos/{id}/preview` | GET | StreamVideoPlayController | 30s 试看 URL（FFmpeg 截取） | app hls.js 播放器 |
| `/stream/videos/{id}/play` | GET | StreamVideoPlayController | 鉴权播放 URL（canPlay 判断） | app hls.js 播放器 |
| `/stream/videos/{id}/watch-progress` | POST | StreamHistoryController | 观看进度上报 | app 播放器 end 事件 |
| `/stream/videos/{id}/like\|favorite` | POST | StreamInteractionController | 点赞/收藏 | app 视频详情页 |
| `/stream/collections` | GET | StreamCollectionController | 剧集列表 | app Browse |
| `/stream/collections/{id}` | GET | StreamCollectionController | 剧集详情 + 分集 | app 视频详情页 |
| `/stream/orders` | POST | StreamPurchaseController | 付费下单（PaymentClient Mock） | app 支付引导弹窗 |
| `/stream/orders/{no}` | GET | StreamPurchaseController | 订单查询 | app 支付结果轮询 |
| `/stream/orders/{no}/close` | POST | StreamPurchaseController | 关单 | app 超时关闭 |

**核心业务逻辑**：

```java
// 解锁路径判断（5 级优先级）
public boolean canPlay(Long userId, Long videoId) {
    if ("free".equals(video.getPriceType())) return true;
    if ("member_only".equals(video.getPriceType())) return subscriptionMapper.hasActive(userId);
    if (creatorUnlockMapper.exists(userId, video.getCreatorId())) return true;  // 全局解锁
    if (video.getCollectionId() != null
        && purchaseMapper.exists(userId, video.getCollectionId(), "collection")) return true;
    return purchaseMapper.exists(userId, videoId, "episode");
}

// 分润固化（PaymentSuccessConsumer 消费 MQ 时）
StreamRevenueRecord record = new StreamRevenueRecord();
record.setRevenueRateSnapshot(video.getRevenueRateSnapshot()); // ← 关键：取快照
record.setCreatorShare(orderAmount * video.getRevenueRateSnapshot() / 100);
streamRevenueRecordMapper.insert(record);
creatorEarningsMapper.addCreatorEarnings(video.getCreatorId(), record.getCreatorShare());
```

**FFmpeg 多码率 HLS 命令**（内嵌 Worker，@Async 线程池）：
```bash
ffmpeg -i input.mp4 \
  -filter_complex "[v:0]split=3[v1][v2][v3];[v1]scale=-2:1080[v1out];[v2]scale=-2:720[v2out];[v3]scale=-2:480[v3out]" \
  -map [v1out] -map 0:a -c:v:0 libx264 -b:v:0 5000k -maxrate 6000k \
  -map [v2out] -map 0:a -c:v:1 libx264 -b:v:1 2500k -maxrate 3000k \
  -map [v3out] -map 0:a -c:v:2 libx264 -b:v:2 1200k -maxrate 1500k \
  -c:a aac -b:a 160k -hls_time 10 -hls_list_size 0 \
  -master_pl_name master.m3u8 \
  -var_stream_map "v:0,name:1080p v:1,name:720p v:2,name:480p" \
  output_%v.m3u8
```

### Step 3 · 管理后台 API（Day 10-11，2 天）

| 路径 | 方法 | Controller | 前端谁用 |
|------|------|------------|----------|
| `/internal/stream/review/queue` | GET | StreamReviewInternalController | admin 审核中心 |
| `/internal/stream/review/{id}/approve\|reject` | POST | StreamReviewInternalController | admin 审核操作 |
| `/internal/stream/withdrawals` | GET | WithdrawalInternalController | admin 提现审核 |
| `/internal/stream/withdrawals/{id}/approve\|reject` | POST | WithdrawalInternalController | admin 提现审批 |
| `/internal/creator/accounts` | GET/PUT | CreatorInternalController | admin 创作者管理 |
| `/internal/stream/reconciliation` | GET | StreamReportInternalController | admin 对账报表 |
| `/internal/stream/reports/daily` | GET | StreamReportInternalController | admin 数据看板 |

所有 `/internal/**` 路由由 Gateway InternalPathDenyFilter 保护，外部 IP 直接返回 403。

### Step 4 · 18+ 合规 + Gateway 增强（Day 11，1 天）

- 注册强制 age_verified 检查
- Gateway Filter：Sa-Token 后 + Redis 查 `user:age:{userId}` → 未验证返回 403
- Sentinel 限流：`/stream/**` QPS=500，`/creator/**` QPS=200
- InternalPathDenyFilter

### Step 5 · Payment 真实实现（延后，4 天）

前提：支付渠道确认。PaymentClient 接口签名不变，Mock 切真实实现只需改 Spring Profile。

---

## Part 2 · 管理后台 star-ai-admin（3 Steps，约 8 天，与后端并行）

### 现有脚手架（已搭好）

```
star-ai-admin/
├── package.json           Ant Design Vue 4 + Pinia + Axios + Vite 5 + Less
├── src/
│   ├── main.ts            ✅ 启动入口
│   ├── App.vue            ✅
│   ├── router/index.ts    ✅ 路由
│   ├── stores/user.ts     ✅ Pinia 用户 store
│   ├── utils/request.ts   ✅ Axios 封装（拦截器 + Sa-Token X-Access-Token）
│   ├── layouts/BasicLayout.vue  ✅ 侧边栏 + Header
│   └── views/
│       ├── login/index.vue    ✅ 页面壳子
│       ├── dashboard/index.vue ✅ 页面壳子
│       ├── review/index.vue    ✅ 页面壳子
│       ├── order/index.vue     ✅ 页面壳子
│       └── user/index.vue      ✅ 页面壳子
```

### 后端 API 对接点（Sa-Token 共用）

```
前端请求 → Gateway (8080) → 各微服务
请求头：X-Access-Token: <Sa-Token>
Sa-Token 共享 Redis：spring.redis.host
未登录：后端返回 401 → 前端跳转 /login
```

### Step A · 完善 Layout + API 客户端（Day 1-2，2 天）

| # | 任务 | 文件 |
|---|------|------|
| A.1 | 侧边栏菜单配置（对应 prototype admin-*.html 结构） | `src/layouts/BasicLayout.vue` |
| A.2 | 8 个页面路由补齐 | `src/router/index.ts` |
| A.3 | 分模块 API 客户端（替换原来集中式 request.ts） | `src/api/creator.ts`, `src/api/stream.ts`, `src/api/report.ts`, `src/api/user.ts`, `src/api/withdrawal.ts` |
| A.4 | TypeScript 类型定义 | `src/types/creator.ts`, `src/types/stream.ts`, `src/types/revenue.ts` |
| A.5 | Login 对接 system-cloud-start 的 `/sys/login` + 持久化 X-Access-Token | `src/views/login/index.vue` + `stores/user.ts` |
| A.6 | Mock 数据 JSON（后端未 ready 时用） | `src/mock/*.json` |

### Step B · 8 个核心页面实现（Day 3-7，5 天，Mock 数据先）

每个页面对应 prototype 里的 HTML 原型，按原型结构翻译成 Ant Design Vue 组件：

| 页面 | 路径 | 原型参考 | 核心组件 | 后端 API |
|------|------|----------|----------|----------|
| **数据看板** | `/dashboard` | `admin-dashboard.html` | Statistic 统计卡 + Line 图（日收入）+ Table（Top10 创作者） | `GET /internal/stream/reports/daily` |
| **审核中心** | `/review` | `admin-review.html` | Table（publish_status=pending_review）+ Modal（视频预览）+ Space（通过/驳回按钮 + 驳回原因 Modal） | `GET /internal/stream/review/queue` + `POST /internal/stream/review/{id}/approve\|reject` |
| **订单列表** | `/order` | `admin-order.html` | Table（状态筛选 + 时间范围 + 导出 CSV）+ 详情 Drawer | `GET /internal/stream/orders` |
| **创作者管理** | `/creator` | (无独立原型，从 creator.html 提取) | Table（入驻状态筛选）+ Form（定价区间配置）+ Modal（冻结/解禁） | `GET/PUT /internal/creator/accounts` |
| **提现审核** | `/withdrawal` | (无独立原型) | Table（状态筛选）+ Modal（银行账户信息 + 审批） | `GET /internal/stream/withdrawals` + `POST /internal/stream/withdrawals/{id}/approve\|reject` |
| **用户管理** | `/user` | prototype 无 | Table（age_verified 筛选 + VIP 标识）+ Drawer（观看历史） | `GET /sys/user/list`（已有）+ 扩展 stream_play_history |
| **对账报表** | `/report/reconciliation` | prototype 无 | Table（按月分润）+ 导出 Excel | `GET /internal/stream/reconciliation` |
| **定价区间配置** | `/pricing` | prototype 无 | Form（分类/标签 + 价格区间 + auto_publish_price 开关） | `GET/PUT /internal/creator/pricing` |

**关键前端逻辑示例（审核中心）**：
```tsx
// src/views/review/index.vue 核心结构
<template>
  <a-table :columns="columns" :data-source="queue" row-key="id">
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'action'">
        <a-space>
          <a-button type="primary" @click="approve(record.id)">通过</a-button>
          <a-button danger @click="openRejectModal(record.id)">驳回</a-button>
        </a-space>
      </template>
    </template>
  </a-table>
  
  <!-- 驳回原因 Modal -->
  <a-modal v-model:open="rejectModal" title="驳回原因">
    <a-textarea v-model:value="rejectReason" :rows="3" />
  </a-modal>
</template>
```

**关键前端逻辑示例（定价区间配置）**：
```tsx
// admin 改 creator_account.price_amount_min/max
const updatePricing = async (creatorId, min, max) => {
  await creatorApi.updatePricing(creatorId, { priceAmountMin: min, priceAmountMax: max });
  message.success('定价区间已更新');
}
```

### Step C · 接真实 API + UI 打磨（Day 8，1 天）

| # | 任务 |
|---|------|
| C.1 | `VITE_API_BASE_URL` 从 `http://localhost:3002/mock` 切到 `http://localhost:8080`（Gateway） |
| C.2 | 8 个页面全部跑通真实接口 |
| C.3 | 表单校验补全 + Loading 状态 + Empty 状态 |
| C.4 | ECharts 图表（收入曲线 + 平台/创作者分润饼图） |

---

## Part 3 · 用户端 + 创作者端 star-ai-app（5 Steps，约 10 天，与后端并行）

### 现有脚手架（已搭好）

```
star-ai-app/
├── package.json           Next.js 15 + React 18 + Tailwind 3 + Capacitor 6 + hls.js
├── src/
│   ├── app/
│   │   ├── layout.tsx           ✅ 根布局
│   │   ├── page.tsx             ✅ 首页壳子
│   │   ├── browse/page.tsx      ✅ 剧集浏览壳子
│   │   ├── video/[id]/page.tsx  ✅ 视频详情壳子
│   │   ├── subscription/page.tsx ✅ 会员方案壳子
│   │   └── creator/dashboard/page.tsx ✅ 创作者中心壳子
│   ├── lib/api/client.ts        ✅ API 客户端
│   ├── lib/config/env.ts        ✅ 环境配置
│   └── store/auth.ts            ✅ 登录状态
```

### Step A · 用户端基础页面（Day 1-3，3 天，Mock 数据）

| 页面 | 路径 | 原型参考 | 核心组件 | 后端 API |
|------|------|----------|----------|----------|
| **首页** | `/` | `index.html` | VideoGrid（免费/热门瀑布流）+ 筛选标签 + Hero Banner | `GET /stream/videos?sort=hot` |
| **剧集浏览** | `/browse` | `gallery.html` | CategorySidebar + VideoCard Grid + Pagination | `GET /stream/videos?category=xxx` |
| **创作者主页** | `/creator/[id]` | `creator-page.html` | CreatorHeader（头像+简介+全局解锁入口）+ 视频列表 | `GET /stream/creators/{id}` + `GET /stream/videos?creator_id={id}` |
| **会员方案** | `/subscription` | `subscription.html` | 三档方案卡片 + 开通按钮 | `GET /internal/subscription/plans` |

### Step B · 视频详情 + hls.js 播放器（Day 4-6，3 天）

**这是最复杂的页面**，涉及：hls.js 多码率自适应 + 试看/付费引导状态切换 + 付费流程。

| # | 任务 | 文件 |
|---|------|------|
| B.1 | hls.js 播放器组件封装 | `src/components/player/HlsPlayer.tsx` |
| B.2 | 试看 30s 自动暂停 + 弹付费引导 Modal | HlsPlayer 里监听 `timeupdate`，`currentTime >= 29` 时 pause + dispatch custom event |
| B.3 | 播放鉴权 4 态处理：free / member / paid / locked | `usePlayPermission(userId, videoId)` hook |
| B.4 | 付费引导 Modal（三档：单集¥19.99 / 剧集¥99 / 创作者全局¥199） | `components/paywall/PaywallModal.tsx` |
| B.5 | 支付结果轮询 + 解锁后自动切 play URL 重拉 m3u8 | |
| B.6 | 观看历史自动上报（播放 end 事件 → 调 `POST /stream/videos/{id}/watch-end`） | |
| B.7 | 剧集浏览：分集切换 + 解锁标记 + 剧集级付费入口 | |
| B.8 | 点赞 / 收藏按钮交互 | |

**hls.js 播放器核心逻辑**：
```tsx
// src/components/player/HlsPlayer.tsx
import Hls from 'hls.js';

interface HlsPlayerProps {
  videoId: number;
  canPlay: boolean;         // ← 来自后端解锁判断
  isPreview: boolean;       // ← true 时用 preview URL + 30s 截断
}

function HlsPlayer({ videoId, canPlay, isPreview }) {
  const videoRef = useRef<HTMLVideoElement>();
  const [needPaywall, setNeedPaywall] = useState(false);
  
  useEffect(() => {
    // 根据 canPlay + isPreview 决定 URL
    const url = canPlay 
      ? `/api/stream/videos/${videoId}/play`       // 完整鉴权 URL
      : `/api/stream/videos/${videoId}/preview`;    // 试看 URL
    
    if (Hls.isSupported()) {
      const hls = new Hls({ enableWorker: true });
      hls.loadSource(url);
      hls.attachMedia(videoRef.current!);
    } else {
      videoRef.current!.src = url;
    }
    
    // 试看 30s 自动暂停 → 弹付费引导
    if (isPreview) {
      videoRef.current!.addEventListener('timeupdate', () => {
        if (videoRef.current!.currentTime >= 29) {
          videoRef.current!.pause();
          setNeedPaywall(true);
        }
      });
    }
  }, [videoId, canPlay, isPreview]);
  
  return (
    <>
      <video ref={videoRef} controls className="w-full" />
      {needPaywall && <PaywallModal videoId={videoId} onSuccess={() => { setNeedPaywall(false); /* 刷新 canPlay */ }} />}
    </>
  );
}
```

**解锁路径 Hook**：
```tsx
// src/hooks/usePlayPermission.ts
export function usePlayPermission(userId: number, videoId: number) {
  const [canPlay, setCanPlay] = useState(false);
  const [permissionType, setPermissionType] = useState<'free'|'member'|'creator_global'|'collection'|'episode'|'locked'>('locked');
  
  useEffect(() => {
    api.getStreamVideo(videoId).then(video => {
      if (video.price_type === 'free') {
        setCanPlay(true); setPermissionType('free');
      }
      // ... 其他解锁路径由后端 /play 接口统一判断，前端只管渲染
    });
    // 更简单的方式：直接调 /play 接口
    api.getStreamVideoPlay(videoId).then(res => {
      setCanPlay(res.canPlay);
      setPermissionType(res.permissionType);
    });
  }, [userId, videoId]);
  
  return { canPlay, permissionType };
}
```

### Step C · 创作者端（Day 6-9，4 天）

**这里有 MinIO 分片上传 + 视频编辑表单 + 定价 + 发布 4 大块**。

| 页面 | 路径 | 原型参考 | 核心组件 | 后端 API |
|------|------|----------|----------|----------|
| **创作者入驻** | `/creator/onboarding` | (从 creator.html 提取) | 入驻表单 + age_verify 校验 + 条款勾选 | `POST /creators/apply` |
| **创作者中心 Dashboard** | `/creator/dashboard` | `creator.html` | 统计卡（视频数/总收入/本月新粉）+ 收入曲线 + 最近上传列表 | `GET /creator/earnings` + `GET /creator/videos` |
| **视频上传组件** | (创作者中心内) | `creator.html` 上传区 | 拖拽上传 + 分片进度条 + MinIO presigned URL 直传 | `POST /creator/videos/upload-init` + `POST /creator/videos/{id}/complete` |
| **视频编辑表单** | `/creator/videos/[id]/edit` | `creator.html` 编辑区 | 封面上传 + 标题/描述 + 分类/标签选择 + **定价（¥输入 + 区间提示）** + 全局解锁开关 | `PUT /creator/videos/{id}` |
| **创作者视频列表** | `/creator/videos` | `creator.html` 列表区 | Table（发布状态筛选 + 定价状态筛选）+ Publish 按钮 | `GET /creator/videos` + `POST /creator/videos/{id}/publish` |
| **创作者剧集管理** | `/creator/collections` | `creator.html` 剧集区 | Form（创建剧集 + 添加分集 + 剧集级定价） | `POST /creator/collections` |
| **收益看板** | `/creator/earnings` | `creator.html` 收益区 | 月度分润表格 + 累计收益 + 提现按钮 | `GET /creator/earnings` + `POST /withdrawals` |

**MinIO 分片上传前端核心逻辑**：
```tsx
// src/components/creator/VideoUploader.tsx
import axios from 'axios';

async function uploadVideo(file: File, onProgress: (chunkIdx: number, total: number, pct: number) => void) {
  // 1. 拿 upload-init 返回的分片 presigned URL
  const initRes = await api.creator.videoUploadInit({
    filename: file.name,
    size: file.size,
    chunkSize: 5 * 1024 * 1024,  // 5MB 一片
  });
  // initRes = { uploadId, chunkSize, chunks: [{ chunkIndex, presignedUrl }, ...] }
  
  // 2. 并行上传分片（最多 3 并发）
  const CHUNK_SIZE = 5 * 1024 * 1024;
  const chunks: Blob[] = [];
  for (let i = 0; i < file.size; i += CHUNK_SIZE) {
    chunks.push(file.slice(i, i + CHUNK_SIZE));
  }
  
  // 3. 用 axios PUT 直接打 MinIO presigned URL（不走后端！）
  const uploadChunk = (chunk: Blob, url: string, idx: number) =>
    axios.put(url, chunk, { headers: { 'Content-Type': 'video/mp4' } })
      .then(() => onProgress(idx, chunks.length, 100));
  
  // 3 并发队列
  for (let i = 0; i < chunks.length; i += 3) {
    await Promise.all(chunks.slice(i, i + 3).map((c, j) => 
      uploadChunk(c, initRes.chunks[i + j].presignedUrl, i + j)
    ));
  }
  
  // 4. 通知后端合并分片 + 发 MQ
  const completeRes = await api.creator.videoUploadComplete(initRes.uploadId, file.name);
  return completeRes.videoId;
}
```

### Step D · 接真实 API（Day 10，1 天）

| # | 任务 |
|---|------|
| D.1 | `NEXT_PUBLIC_API_BASE_URL` 从 Mock 切到 `http://localhost:8080` |
| D.2 | 所有页面接真实后端 API |
| D.3 | hls.js 多码率切换测试 |
| D.4 | 视频付费完整流程（下单 → Mock 支付 → 自动解锁播放） |
| D.5 | 创作者上传 → 转码 → 发布 → 审核 通过完整流程 |

### Step E · 联调 + 打磨（Day 11-12，2 天）

| # | 任务 |
|---|------|
| E.1 | Capacitor 6 Android / iOS 构建（本地先 Android 模拟器） |
| E.2 | 响应式适配（移动端 H5 为主） |
| E.3 | 支付引导弹窗 4 态打磨 |
| E.4 | 错误边界 + 空状态 + Loading 骨架屏 |

---

## Part 4 · 三仓联调 Checklist（Day 13-14）

### 端到端完整链路

```
[创作者端]
  1. 注册 → 强制 age_verified 检查 → 入驻申请 → status=pending
  2. MinIO 直传分片上传 → 进度条 0-100% → video.uploaded MQ
  3. 后端消费 → FFmpeg 转码 → 多码率 HLS 生成 → transcode_completed
  4. 创作者编辑视频：标题 + 封面 + 分类 + 定价（¥19.99）
     → 后端校验区间 → auto_publish_price=1 → price_status=approved
     → 发布 → 固化 revenue_rate_snapshot=30% → pending_review
     
[管理后台]
  5. 审核中心看到视频 → 预览 → 点击"通过"
     → publish_status=published
     
[用户端]
  6. 首页 Browse 看到新视频（price_type=paid，价格 ¥19.99）
  7. 点击进入 video/[id] → hls.js 拉 preview URL → 30s 试看
  8. 30s 自动暂停 → 弹 PaywallModal → 选"单集 ¥19.99"
  9. POST /stream/orders → PaymentClient Mock 返回 order_no + 已支付
  10. Mock 发 payment.success MQ → 后端消费
      → stream_purchase(paid)
      → stream_revenue_record 固化 snapshot=30%
      → creator_earnings_record 累加 ¥19.99 × 30% = ¥5.99
  11. 付费引导 Modal 自动关闭 → hls.js 切到 play URL → 完整播放
  12. 播放结束 → 自动调用 watch-end → stream_play_history(is_effective=true)
  
[创作者收益看板]
  13. GET /creator/earnings → 看到：
      total_earnings: ¥5.99
      monthly_breakdown: [{ month: '2026-10', revenue: ¥5.99 }]
      orders: [{ order_no: '...', video_title: '...', creator_share: ¥5.99 }]
      
[分润快照验证]
  14. 创作者在 admin 后台修改 revenue_rate=40%
  15. 创作者发布新视频 → 新视频 snapshot=40% ✅
  16. 老视频再有人付费 → 后端查老 video.revenue_rate_snapshot=30%
      → stream_revenue_record 里 snapshot 字段仍是 30% ✅（固化不变）
```

---

## Part 5 · API 契约规范（后端 Day 1-3 必须产出）

### 返回结构（统一 R<T>）

```json
{
  "code": 200,
  "message": "success",
  "result": { ... },
  "success": true
}
```

### 分页结构

```json
{
  "code": 200,
  "result": {
    "records": [ ... ],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10
  }
}
```

### Feign Client（跨模块调用）

```java
// stream-api 里定义，creator 模块调
@FeignClient(name = "star-ai-module-stream", fallbackFactory = StreamClientFallbackFactory.class)
public interface StreamClient {
    @PostMapping("/internal/stream/video/review-callback")
    R<?> videoReviewCallback(@RequestBody VideoReviewCallbackDTO dto);
    
    @PostMapping("/internal/stream/creator-stats")
    R<?> updateCreatorStats(@RequestBody CreatorStatsUpdateDTO dto);
}
```

### Error Code（前端根据 code 弹不同提示）

| code | 含义 | 前端行为 |
|------|------|----------|
| 200 | 成功 | 正常渲染 |
| 401 | Sa-Token 过期 | 跳登录页 |
| **403** | **年龄未验证** | 弹年龄验证 Modal |
| 402 | 需要付费 | 弹 PaywallModal |
| 404 | 资源不存在 | 展示 Empty 状态 |
| 409 | 冲突（如重复发布） | Toast 提示 |
| 500 | 后端异常 | Toast + 打日志 |

---

## Part 6 · Payment Mock → 真实切换点

Mock 实现写在 `payment-biz`，用 Spring Profile 隔离：

```yaml
# application-mock.yml (Step 0 起用这个)
payment:
  mock:
    enabled: true

# application-prod.yml (Step 5 切换)
payment:
  mock:
    enabled: false
  wechat:
    appId: xxx
    mchId: xxx
    apiKey: xxx
  stripe:
    secretKey: sk_xxx
```

```java
@Configuration
public class PaymentAutoConfiguration {
    @ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "true")
    @Bean
    public PaymentClient mockPaymentClient() { ... }  // Mock
    
    @ConditionalOnProperty(name = "payment.mock.enabled", havingValue = "false")
    @Bean
    public PaymentClient realPaymentClient() { ... }  // Wechat + Stripe
}
```

**切换只需改一个配置**，stream / creator 前端调用无感。

---

## Part 7 · 后端 DDL 执行顺序

```sql
-- 1. 系统扩展
schema/08_stream_creator_domain.sql  -- Part 11 sys_user_age_verification

-- 2. 支付公共（表先建，Step 5 才写 Service）
schema/09_payment_domain.sql         -- 4 张表

-- 3. 创作者 + 视频（核心 13 张）
schema/08_stream_creator_domain.sql  -- Part 08 creator_* + stream_*

-- 4. 订阅池（Phase 1 只建表）
schema/10_subscription_pool_domain.sql

-- 5. Seed Data
schema/99_seed_data.sql
```

**共 22 张表**：creator_* 3 + stream_* 10 + 分润 2 + payment 4 + subscription 4 + age_verification 1。

---

## Part 8 · 风险与技术债

| 风险 | 缓解 |
|------|------|
| MinIO presigned URL 有效期短 + 大文件上传 | upload-init 每片独立有效期（15min），前端分片并行续传 |
| FFmpeg 内嵌吃 CPU 拖垮容器 | @Async 线程池 + semaphore 限流（最多 2 并发转码），Phase 2 拆 transcode-worker |
| hls.js 在 iOS Safari 的自适应 HLS 兼容性 | 降级策略：不支持则用单码率 HLS master.m3u8 |
| 分润快照固化漏写 | 单元测试 `testRevenueRateSnapshotImmutability`：老视频 snapshot 30%，创作者改 40% → 新分润记录仍 30% |
| Payment Mock → 真实切换时遗漏业务逻辑 | Step 5 切换后跑完整端到端 Checklist 第 1-19 步 |
| RabbitMQ 消费幂等 | consumer 入口 Redis SETNX `idempotency_key = event_id`，已处理直接 ack |
| Gateway /internal/** 路由被外部穿透 | InternalPathDenyFilter 必须在 Gateway 启动时就配置，Nacos 改不了代码级逻辑 |

---

## Part 9 · 各仓端口汇总

| 服务 | 端口 | 备注 |
|------|------|------|
| MySQL | 3306 | |
| Redis | 6379 | Sa-Token 共享 |
| RabbitMQ | 5672 | Management 15672 |
| Nacos | 8848 | 配置中心 |
| MinIO | 9000 | 视频存储 |
| Sentinel Dashboard | 8858 | |
| Gateway | 8080 | Sa-Token /api/** + /internal/** 路由 |
| star-ai-system-cloud-start | 7001 | 用户/权限基础 |
| star-ai-module-payment | 7010 | Mock 模式先跑 |
| star-ai-module-creator | 7012 | |
| star-ai-module-stream | 7011 | |
| **star-ai-admin（前端）** | **3002** | `npm run dev` |
| **star-ai-app（前端）** | **3000** | `npm run dev` |

---

## Part 10 · 验收 Checklist（全部通过才算 Phase 1 完成）

### 后端 star-ai-boot
- [ ] `mvn clean compile` 4 新模块无报错
- [ ] Gateway 启动后 5 个路由可达
- [ ] Sa-Token 鉴权 `/creator/**`, `/stream/**`, `/pay/**` 生效
- [ ] Gateway Filter 拦截未年龄验证用户返回 403
- [ ] InternalPathDenyFilter 拦截外部 `/internal/**`
- [ ] **分润快照固化验证**（验收第 17-19 步）
- [ ] Payment Mock 模式下单 → MQ → stream_purchase(paid) 全链路
- [ ] FFmpeg 多码率 HLS 生成 master.m3u8 可播放器测试

### 管理后台 star-ai-admin
- [ ] `npm run build` 无 TS 错误
- [ ] 登录对接 Sa-Token，Token 持久化到 localStorage
- [ ] 8 个页面全部接真实 API 正常渲染
- [ ] 审核中心：通过/驳回 → 后端 publish_status 变化
- [ ] 创作者定价区间改 → 前端表单校验实时提示

### 用户端 + 创作者端 star-ai-app
- [ ] `npm run build` 无错误 + Capacitor Android 构建成功
- [ ] 首页 /browse 免费视频瀑布流正常
- [ ] hls.js 多码率自适应切换正常（1080p → 720p → 480p）
- [ ] 试看 30s 自动暂停 → PaywallModal 弹出
- [ ] 付费引导 Modal 4 态：单集¥ / 剧集¥ / 全局¥ / 免费 → 正常引导
- [ ] MinIO 分片上传进度条 0-100% 实时更新
- [ ] 创作者发布 → 后端固化 snapshot
- [ ] 观看结束自动上报 watch-end → is_effective=true

### 三仓联调
- [ ] 完整端到端 19 步（Part 4）全部跑通
- [ ] 创作者收益看板实时看到分润累计
- [ ] 管理后台审核通过 → 视频在用户端可见
- [ ] 创作者定价区间修改 → 超出区间时前端实时提示"请在 ¥X - ¥Y 范围内"
- [ ] Revenue 快照固化验证（老视频 30% / 新视频 40%）
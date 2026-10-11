# Phase 1 · 前端用户端 + 创作者端 Code Plan

> 仓库：`star-ai-app` | 技术栈：Next.js 15 + React 18 + TypeScript 5 + Tailwind 3 + Capacitor 6 + hls.js | 工期：12 天（与后端并行）

---

## 现有脚手架

```
star-ai-app/
├── package.json              ✅ Next.js 15 + React 18 + Tailwind 3 + Capacitor 6 + hls.js
├── src/
│   ├── app/
│   │   ├── layout.tsx                ✅ 根布局
│   │   ├── page.tsx                  ✅ 首页壳子 → 视频瀑布流
│   │   ├── browse/page.tsx           ✅ 剧集浏览壳子
│   │   ├── video/[id]/page.tsx       ✅ 视频详情壳子 → 最复杂页面
│   │   ├── subscription/page.tsx    ✅ 会员方案壳子
│   │   └── creator/dashboard/page.tsx ✅ 创作者中心壳子
│   ├── lib/api/client.ts             ✅ API 客户端
│   ├── lib/config/env.ts             ✅ 环境配置（含 NEXT_PUBLIC_API_BASE_URL）
│   └── store/auth.ts                 ✅ 登录状态
├── capacitor.config.ts              ✅ Capacitor 6 配置
└── tailwind.config.ts               ✅ Tailwind 3
```

## 后端对接

```
前端 Next.js SSR → API Route Proxy → Gateway (8080)
或客户端直接 fetch → http://localhost:8080 (开发模式)
NEXT_PUBLIC_API_BASE_URL:
  Day 1-3:  http://localhost:3000/api/mock/   (Mock)
  Day 10+:  http://localhost:8080             (Gateway)

登录态：store/auth.ts → 持久化 Sa-Token (localStorage)
请求头：X-Access-Token: <token>
未登录：401 → 弹登录 Modal（走 system-cloud-start /sys/login）
未年龄验证：403 (code=418) → 弹年龄验证 Modal
```

---

## Step A · 用户端基础页面（Day 1-3，3 天，Mock 数据）

| 页面 | 路径 | 原型参考 | 核心组件 | 后端 API |
|------|------|----------|----------|----------|
| **首页** | `/` | `index.html` | VideoGrid（免费/热门瀑布流）+ Hero Banner + 分类标签筛选 | `GET /stream/videos?sort=hot&publish_status=published` |
| **剧集浏览** | `/browse` | `gallery.html` | CategorySidebar + VideoCard Grid + Pagination | `GET /stream/videos?category_id=xxx` + `GET /creator/video-categories` |
| **创作者主页** | `/creator/[id]` | `creator-page.html` | CreatorHeader（头像+简介+全局解锁入口）+ 视频列表 | `GET /stream/creators/{id}` + `GET /stream/videos?creator_id={id}` |
| **会员方案** | `/subscription` | `subscription.html` | 三档方案卡片 + 开通按钮 | `GET /internal/subscription/plans` |

---

## Step B · 视频详情 + hls.js 播放器（Day 4-6，3 天 · 最复杂页面）

### 页面：`/video/[id]`

对应 prototype `video.html`，7 大任务：

| # | 任务 | 文件 | 核心难点 |
|---|------|------|---------|
| B.1 | hls.js 多码率自适应播放器封装 | `src/components/player/HlsPlayer.tsx` | master.m3u8 解析 + `levels` 切换 |
| B.2 | 试看 30s 自动暂停 + 弹付费引导 Modal | HlsPlayer 内 `timeupdate` 监听 `currentTime >= 29` → `pause()` + 触发 PaywallModal | 暂停不能 freeze video element |
| B.3 | 播放鉴权 4 态渲染 | `usePlayPermission(userId, videoId)` hook | free / member / unlocked / locked |
| B.4 | PaywallModal 付费引导 4 态 | `src/components/paywall/PaywallModal.tsx` | 单集¥ / 剧集¥ / 创作者全局¥ / 免费 四种 UI |
| B.5 | 付费成功自动切 play URL 重拉 m3u8 | PaywallModal → onSuccess callback → 重新获取 canPlay → hls.loadSource(newUrl) | |
| B.6 | 观看历史自动上报 | 播放器 end 事件 → `POST /stream/videos/{id}/watch-end` | 带 `completion_rate` + `min_effective_sec=10` |
| B.7 | 点赞 / 收藏按钮交互 | 点赞后重新渲染 + localStorage 缓存 | |

### hls.js 播放器核心代码

```tsx
// src/components/player/HlsPlayer.tsx
'use client';

import { useEffect, useRef, useState } from 'react';
import Hls from 'hls.js';
import { usePlayPermission } from '@/hooks/usePlayPermission';
import { PaywallModal } from '@/components/paywall/PaywallModal';
import { watchEnd } from '@/lib/api/stream';

interface HlsPlayerProps { videoId: number; }

export function HlsPlayer({ videoId }: HlsPlayerProps) {
  const videoRef = useRef<HTMLVideoElement>(null);
  const hlsRef = useRef<Hls | null>(null);
  const [needPaywall, setNeedPaywall] = useState(false);
  const [watchEndReported, setWatchEndReported] = useState(false);
  const { canPlay, permissionType, priceAmount } = usePlayPermission(videoId);

  useEffect(() => {
    const video = videoRef.current!;
    if (!video) return;

    // 试看 30s 自动暂停 → 弹付费引导
    const isPreview = !canPlay;
    const onTimeUpdate = () => {
      if (isPreview && video.currentTime >= 29 && !needPaywall) {
        video.pause();
        setNeedPaywall(true);
      }
    };
    
    const onEnd = () => {
      if (!watchEndReported && permissionType !== 'locked') {
        watchEnd(videoId, Math.round(video.duration));
        setWatchEndReported(true);
      }
    };
    
    video.addEventListener('timeupdate', onTimeUpdate);
    video.addEventListener('ended', onEnd);

    // 决定拉哪个 URL
    const url = canPlay
      ? `/api/stream/videos/${videoId}/play`      // 完整鉴权 URL（含签名）
      : `/api/stream/videos/${videoId}/preview`;  // 试看 URL（后端已 FFmpeg 截 30s）

    if (Hls.isSupported()) {
      const hls = new Hls({
        enableWorker: true,
        lowLatencyMode: false,
        backBufferLength: 60,
      });
      hlsRef.current = hls;
      hls.loadSource(url);
      hls.attachMedia(video);
      hls.on(Hls.Events.MANIFEST_PARSED, () => video.play());
    } else if (video.canPlayType('application/vnd.apple.mpegurl')) {
      // iOS Safari 原生 HLS 支持
      video.src = url;
      video.play();
    }

    return () => {
      video.removeEventListener('timeupdate', onTimeUpdate);
      video.removeEventListener('ended', onEnd);
      hlsRef.current?.destroy();
    };
  }, [videoId, canPlay, needPaywall, watchEndReported, permissionType]);

  return (
    <>
      <video ref={videoRef} controls playsInline className="w-full bg-black rounded-lg" />
      {needPaywall && (
        <PaywallModal
          videoId={videoId}
          permissionType={permissionType}
          priceAmount={priceAmount}
          onSuccess={() => {
            setNeedPaywall(false);
            setWatchEndReported(false);
          }}
        />
      )}
    </>
  );
}
```

### 解锁路径 Hook

```tsx
// src/hooks/usePlayPermission.ts
import { useEffect, useState } from 'react';
import { getStreamVideoPlay } from '@/lib/api/stream';

export type PermissionType = 'free' | 'member' | 'creator_global' | 'collection' | 'episode' | 'locked';

export interface PlayPermission {
  canPlay: boolean;
  permissionType: PermissionType;
  priceType: string;      // free / member_only / paid
  priceAmount: number;    // 解锁价格（仅 locked 时有意义）
  unlockScope?: string;    // episode / collection / creator
}

export function usePlayPermission(videoId: number) {
  const [perm, setPerm] = useState<PlayPermission>({
    canPlay: false, permissionType: 'locked', priceType: 'paid', priceAmount: 0,
  });

  useEffect(() => {
    getStreamVideoPlay(videoId).then(res => {
      setPerm({
        canPlay: res.canPlay,
        permissionType: res.permissionType,
        priceType: res.priceType,
        priceAmount: res.priceAmount,
        unlockScope: res.unlockScope,
      });
    });
  }, [videoId]);

  return perm;
}
```

### PaywallModal 付费引导 4 态

```tsx
// src/components/paywall/PaywallModal.tsx
interface PaywallModalProps {
  videoId: number;
  permissionType: PermissionType;
  priceAmount: number;
  onSuccess: () => void;
}

// 4 态渲染：
// ① free       → 不出现（canPlay 已 true）
// ② member     → 引导开通会员（跳转 /subscription）
// ③ creator_global/collection/episode → 三档付费卡片
// ④ locked     → 引导付费
export function PaywallModal({ videoId, permissionType, priceAmount, onSuccess }: PaywallModalProps) {
  const [paying, setPaying] = useState(false);
  
  const handlePurchase = async (unlockScope: 'episode'|'collection'|'creator') => {
    setPaying(true);
    try {
      // POST /stream/orders → 返回 orderNo + payUrl
      const order = await createStreamOrder(videoId, unlockScope, priceAmount);
      // Mock 模式下直接返回已支付
      // 真实模式：Stripe Checkout / 微信 Native code_url
      
      // 轮询订单状态（Mock 3s 后 paid）
      while (true) {
        const status = await queryStreamOrder(order.orderNo);
        if (status === 'paid') break;
        await sleep(1000);
      }
      
      onSuccess();  // video 重新拉 canPlay → true → hls 切 play URL
      message.success('解锁成功！');
    } finally {
      setPaying(false);
    }
  };
  
  return (
    <a-modal open title="解锁观看" width={500} footer={null}>
      {permissionType === 'member' ? (
        <MemberPaywall />           // 引导开通会员
      ) : (
        <div className="grid grid-cols-3 gap-4">
          <PayCard scope="episode" price={priceAmount} label="单集" onPurchase={handlePurchase} />
          <PayCard scope="collection" price={collectionPrice} label="全剧集" onPurchase={handlePurchase} />
          <PayCard scope="creator" price={globalUnlockPrice} label="创作者全局" onPurchase={handlePurchase} />
        </div>
      )}
    </a-modal>
  );
}
```

---

## Step C · 创作者端（Day 6-9，4 天 · MinIO 分片上传核心）

### 页面清单

| 页面 | 路径 | 原型参考 | 核心组件 | 后端 API |
|------|------|----------|----------|----------|
| **创作者入驻** | `/creator/onboarding` | creator.html 提取 | 入驻表单 + age_verify + 条款勾选 | `POST /creators/apply` |
| **创作者中心 Dashboard** | `/creator/dashboard` | `creator.html` | 统计卡 + 收入曲线 + 最近上传列表 | `GET /creator/earnings` + `GET /creator/videos` |
| **视频上传组件** | (Dashboard 内) | `creator.html` 上传区 | 拖拽上传 + **分片进度条** | `POST /creator/videos/upload-init` + `/complete` |
| **视频编辑** | `/creator/videos/[id]/edit` | `creator.html` 编辑区 | 封面 + 标题 + 分类/标签 + **定价（¥输入 + 区间实时提示）** | `PUT /creator/videos/{id}` |
| **创作者视频列表** | `/creator/videos` | `creator.html` 列表 | Table（publish_status/price_status 筛选）+ Publish 按钮 | `GET /creator/videos` + `POST /creator/videos/{id}/publish` |
| **剧集管理** | `/creator/collections` | `creator.html` 剧集 | Form（创建 + 分集 + 剧集级定价） | `POST /creator/collections` |
| **收益看板** | `/creator/earnings` | `creator.html` 收益 | 月度分润表 + 累计 + 提现按钮 | `GET /creator/earnings` + `POST /withdrawals` |

### MinIO 分片直传（前端直接 PUT，不走后端）

```tsx
// src/components/creator/VideoUploader.tsx
import axios from 'axios';

interface UploadChunkResult {
  videoId: number;
}

interface InitResponse {
  uploadId: string;
  chunkSize: number;
  totalChunks: number;
  chunks: Array<{ chunkIndex: number; presignedUrl: string; objectKey: string }>;
}

export async function uploadVideo(
  file: File,
  onChunkProgress: (chunkIdx: number, total: number, pct: number) => void,
): Promise<UploadChunkResult> {
  const CHUNK_SIZE = 5 * 1024 * 1024; // 5MB

  // 1. 请求后端生成所有分片的 presigned PUT URL（15min 有效期）
  const initRes = await api.creator.videoUploadInit({
    filename: file.name,
    size: file.size,
    chunkSize: CHUNK_SIZE,
  }) as InitResponse;

  // 2. 切分本地文件
  const chunks: Blob[] = [];
  for (let i = 0; i < file.size; i += CHUNK_SIZE) {
    chunks.push(file.slice(i, i + CHUNK_SIZE));
  }

  // 3. 并行 PUT（最多 3 并发，防止浏览器网络拥塞）
  // ⚠️ 注意：axios PUT 打 MinIO presigned URL 时 Content-Type 必须和后端生成时一致
  const CONCURRENCY = 3;
  let completedChunks = 0;

  const uploadOne = async (chunk: Blob, chunkIdx: number) => {
    const url = initRes.chunks[chunkIdx].presignedUrl;
    await axios.put(url, chunk, {
      headers: { 'Content-Type': 'video/mp4' },
      onUploadProgress: (e) => {
        if (e.total) {
          onChunkProgress(chunkIdx, chunks.length, Math.round((e.loaded / e.total) * 100));
        }
      },
    });
  };

  // 滑动窗口并发控制
  for (let i = 0; i < chunks.length; i += CONCURRENCY) {
    const window = chunks.slice(i, i + CONCURRENCY);
    const indices = Array.from({ length: window.length }, (_, k) => i + k);
    await Promise.all(window.map((chunk, k) => uploadOne(chunk, indices[k])));
  }

  // 4. 通知后端合并分片 + 发 MQ video.uploaded
  const completeRes = await api.creator.videoUploadComplete({
    uploadId: initRes.uploadId,
    filename: file.name,
  });

  return { videoId: completeRes.videoId };
}
```

### 视频定价区间前端实时提示

```tsx
// src/app/creator/videos/[id]/edit/page.tsx
const [priceAmount, setPriceAmount] = useState(19.99);
const [pricingRange, setPricingRange] = useState<{min: number; max: number} | null>(null);

// 加载创作者定价区间
useEffect(() => {
  api.creator.getMyAccount().then(account => {
    setPricingRange({ min: account.price_amount_min, max: account.price_amount_max });
  });
}, []);

const priceHint = useMemo(() => {
  if (!pricingRange) return '';
  if (priceAmount < pricingRange.min) return `低于最低定价 ¥${pricingRange.min}`;
  if (priceAmount > pricingRange.max) return `超出最高定价 ¥${pricingRange.max}`;
  return `建议定价区间：¥${pricingRange.min} - ¥${pricingRange.max}`;
}, [priceAmount, pricingRange]);

return (
  <div className="space-y-4">
    <InputNumber
      value={priceAmount}
      onChange={v => setPriceAmount(v || 0)}
      addonBefore="¥"
      min={pricingRange?.min}
      max={pricingRange?.max}
      step={0.99}
    />
    <p className={`text-sm ${
      priceHint.startsWith('低于') || priceHint.startsWith('超出')
        ? 'text-red-500'
        : 'text-gray-500'
    }`}>{priceHint}</p>
  </div>
);
```

---

## Step D · 接真实 API（Day 10，1 天）

| # | 任务 |
|---|------|
| D.1 | `NEXT_PUBLIC_API_BASE_URL` 从 Mock 切到 `http://localhost:8080` |
| D.2 | 全页面真实 API 联调 |
| D.3 | hls.js 多码率切换测试（1080p → 720p → 480p） |
| D.4 | MinIO 分片上传 → 后端 FFmpeg 转码 → 完成 → 前端刷新 transcode_status |
| D.5 | 付费完整流程：下单 → Mock 支付 → 自动解锁 → hls 重拉 play URL |

---

## Step E · 联调 + Capacitor 构建（Day 11-12，2 天）

| # | 任务 |
|---|------|
| E.1 | Capacitor 6 Android 构建：`npx cap add android` → `npx cap copy` → `cd android && ./gradlew assembleDebug` |
| E.2 | 响应式适配（移动端 H5 为主，Tailwind `md:` 断点） |
| E.3 | PaywallModal 4 态打磨（单集/剧集/全局/免费 → 不同按钮颜色 + 价格高亮） |
| E.4 | 错误边界 + 空状态 + Skeleton 骨架屏 |
| E.5 | 创作者上传进度条 UI 优化（3 并发分片显示为 3 条进度条） |

---

## 验收 Checklist

- [ ] `npm run build` 无错误
- [ ] Capacitor Android 构建成功 → APK `app-debug.apk`
- [ ] 首页 /browse 免费视频瀑布流正常
- [ ] hls.js 多码率自适应切换正常（Chrome DevTools 模拟网络降级）
- [ ] 试看 30s 自动暂停 → PaywallModal 弹出
- [ ] PaywallModal 4 态正确渲染
- [ ] MinIO 分片上传进度条实时更新 0-100%
- [ ] 创作者定价超出区间 → 实时红色提示
- [ ] 播放结束自动上报 watch-end
- [ ] 未登录 → 401 → 弹登录 Modal
- [ ] 未年龄验证 → 403(code=418) → 弹年龄验证 Modal

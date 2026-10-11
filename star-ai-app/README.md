# Star AI · 客户端 (独立前端仓库)

> ⚠️ **本目录是独立前端仓库的快照**，实际生产中应拆分为独立 Git 仓库 `star-ai-app`。
> 当前暂放在 star-ai-boot 根目录下便于统一开发，**Maven pom.xml 不会扫描此目录**（无 `<module>` 引用）。

## 技术栈

| 层 | 选型 | 版本 |
|---|------|------|
| **框架** | Next.js (App Router) | 15.0.3 |
| **UI** | React + Tailwind CSS | React 19 RC / Tailwind 3.4 |
| **App 打包** | Capacitor (iOS + Android + PWA) | 6.x |
| **视频播放** | hls.js | 1.5 |
| **状态管理** | Zustand | 5.x |
| **表单** | react-hook-form + zod | 7.53 / 3.23 |
| **HTTP** | Axios | 1.7 |
| **i18n** | i18next + react-i18next | 24 / 15 |
| **日期** | dayjs | 1.11 |
| **Toast** | sonner | 1.7 |
| **Node** | ≥ 20 | - |

## 快速开始

```bash
# 1. 安装依赖
cd star-ai-app
npm install

# 2. 本地开发 (H5 网页, 默认端口 3000)
npm run dev

# 3. 构建 H5 静态站点 (Next.js export)
npm run build
npm run export        # 产物在 ./out/

# 4. 打包 iOS App
npm run cap:build:ios    # export → cap sync → 自动打开 Xcode

# 5. 打包 Android App
npm run cap:build:android # export → cap sync → 自动打开 Android Studio
```

## 环境变量 (.env.local)

```bash
NEXT_PUBLIC_API_BASE=http://localhost:8080/jeecg-boot
NEXT_PUBLIC_REGION=cn   # cn / sg / us
```

## 目录结构

```
star-ai-app/
├── src/
│   ├── app/                        # App Router 页面
│   │   ├── layout.tsx              # 全局 Layout (Providers + Toaster)
│   │   ├── page.tsx                # 首页 (SSG)
│   │   ├── globals.css              # Tailwind + 自定义样式
│   │   ├── (public)/
│   │   │   ├── browse/             # 剧集列表 (SSR)
│   │   │   ├── video/[id]/         # 视频详情 (SSR)
│   │   │   ├── creator/[id]/       # 创作者主页 (SSR)
│   │   │   └── subscription/       # 会员方案 (SSR)
│   │   ├── (auth)/
│   │   │   ├── login/
│   │   │   └── register/
│   │   ├── (creator)/
│   │   │   ├── dashboard/          # 创作者中心 (CSR, AuthGuard)
│   │   │   └── pricing/            # 创作者定价
│   │   └── (user)/
│   │       ├── my-purchases/
│   │       └── my-subscription/
│   ├── components/
│   │   ├── common/Providers.tsx    # QueryClient + Capacitor 检测
│   │   ├── video/
│   │   │   ├── VideoPlayer.tsx     # hls.js + 试看逻辑
│   │   │   └── VideoCard.tsx       # 封面 + 价格 + VIP 锁定态
│   │   ├── pricing/PricingOptions.tsx  # 五种消费路径动态组合
│   │   └── auth/AuthGuard.tsx      # 路由守卫 + 18+ 检查
│   ├── lib/
│   │   ├── api/                    # Axios Client + Stream API
│   │   ├── auth/                   # JWT + Sa-Token 适配
│   │   ├── config/env.ts           # 环境检测 (Capacitor / iOS / Android)
│   │   └── utils/
│   └── store/auth.ts               # Zustand (token + user + persist)
├── capacitor.config.ts             # Capacitor 配置
├── next.config.js                  # Next.js (output: 'export' + unoptimized images)
├── tailwind.config.ts
├── tsconfig.json
└── package.json
```

## 混合架构运行时检测

```typescript
// src/lib/config/env.ts
export const env = {
  IS_CAPACITOR: typeof document !== 'undefined' && !!document.querySelector('meta[name="capacitor"]'),
  IS_IOS: /iPhone|iPad|iPod/.test(navigator.userAgent),
  IS_ANDROID: /Android/.test(navigator.userAgent)
};

// Providers.tsx 自动在 <html> 上加 .app class
// Tailwind 里可以写:
// .app .video-player { height: 60vh; }  // App 壳内全屏
```

## 打包流程

```
H5 网页:
  next build → next export → ./out/ → Nginx 直接托管 (CND 加速)

iOS App:
  next build → next export → ./out/ → npx cap sync ios
  → npx cap open ios → Xcode Archive → App Store

Android App:
  next build → next export → ./out/ → npx cap sync android
  → npx cap open android → Android Studio Build → Google Play
```

## 与服务端对接

| 前端 | 服务端 | 协议 |
|------|--------|------|
| Axios Client | JeecgBoot Gateway | HTTP REST (`/jeecg-boot/*`) |
| Token Header | Sa-Token / JeecgAuth | `X-Access-Token: xxx` |
| 视频播放 URL | stream-service 签名 | 15min 时效 HLS 签名 URL |
| 支付 | payment-service | 订单创建 → 微信/支付宝/Stripe WebView |
| 18+ 验证 | sys_user_age_verification | KYC + 前端弹窗二次确认 |

## 后端 API 参考

完整 Schema 见 `docs/服务端架构/schema/`，核心接口清单：

```
GET  /stream/videos              视频列表
GET  /stream/videos/{id}         视频详情 (含解锁选项组合)
GET  /stream/series              剧集列表
GET  /stream/creators/{id}       创作者主页
GET  /stream/creators/pricing-bounds   创作者定价区间 (仅自己)
GET  /stream/subscription/plans  会员方案 (按 region)
POST /stream/orders              创建订单 (单集/剧集/全局)
POST /login                      JeecgBoot 登录
POST /register                   注册
```

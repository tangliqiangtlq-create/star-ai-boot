# Phase 1 · 前端管理后台 Code Plan

> 仓库：`star-ai-admin` | 技术栈：Vue 3.5 + Ant Design Vue 4 + Vite 5 + Pinia + Less | 工期：8 天（与后端并行）

---

## 现有脚手架

```
star-ai-admin/
├── package.json              ✅ Ant Design Vue 4.2.5 + Vue 3.5 + Vite 5.4
├── src/
│   ├── main.ts               ✅ 启动入口
│   ├── App.vue               ✅
│   ├── router/index.ts       ✅ 路由（需补齐 8 个页面）
│   ├── stores/user.ts        ✅ Pinia 用户 store（需接 Sa-Token）
│   ├── utils/request.ts      ✅ Axios 封装（需改 Sa-Token 拦截）
│   ├── layouts/BasicLayout.vue ✅ 侧边栏 + Header（需改菜单）
│   └── views/
│       ├── login/index.vue       ✅ 页面壳子 → 需接 system-cloud-start /sys/login
│       ├── dashboard/index.vue   ✅ 页面壳子
│       ├── review/index.vue      ✅ 页面壳子
│       ├── order/index.vue       ✅ 页面壳子
│       └── user/index.vue        ✅ 页面壳子
```

## 后端对接（Sa-Token 共享）

```
前端 Axios → Gateway (8080) → 各微服务
请求头：X-Access-Token: <Sa-Token>
Sa-Token Redis 共享：spring.redis.host（Nacos 配置）
未登录 → 后端返回 401 → 前端 stores/user.ts 清 token → 跳 /login
未年龄验证 → 后端返回 403(code=418) → 前端弹年龄验证 Modal
```

---

## Step A · 完善 Layout + API 客户端（Day 1-2，2 天）

| # | 任务 | 文件 | 产出 |
|---|------|------|------|
| A.1 | 侧边栏菜单配置 | `src/layouts/BasicLayout.vue` | 8 菜单 + Icon 对齐 prototype admin-*.html |
| A.2 | 8 页面路由补齐 | `src/router/index.ts` | `/dashboard`, `/review`, `/order`, `/creator`, `/withdrawal`, `/user`, `/report/reconciliation`, `/pricing` |
| A.3 | 分模块 API 客户端（替换集中式 request.ts） | `src/api/creator.ts`, `src/api/stream.ts`, `src/api/report.ts`, `src/api/withdrawal.ts`, `src/api/pricing.ts` | 每个 API 函数带类型定义 |
| A.4 | TypeScript 类型定义 | `src/types/creator.ts`, `src/types/stream.ts`, `src/types/revenue.ts`, `src/types/withdrawal.ts` | 从后端 Swagger 提取 |
| A.5 | Login 对接 system-cloud-start `/sys/login` + 持久化 X-Access-Token | `src/views/login/index.vue` + `stores/user.ts` | 登录后 Token 存 localStorage，axios 拦截器自动带 `X-Access-Token` |
| A.6 | Mock 数据 JSON（后端未 ready 时用） | `src/mock/*.json` | 审核队列/订单列表/创作者列表等 |

**Sa-Token 拦截器关键代码**：
```ts
// src/utils/request.ts
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('satoken:X-Access-Token');
  if (token) config.headers['X-Access-Token'] = token;
  return config;
});

axios.interceptors.response.use(
  res => res.data,
  err => {
    if (err.response?.data?.code === 401) {
      localStorage.removeItem('satoken:X-Access-Token');
      router.push('/login');
    }
    return Promise.reject(err);
  }
);
```

**侧边栏菜单结构**：
```
Layout
├── 📊 数据看板 /dashboard
├── ✍️ 审核中心 /review
├── 📦 订单管理 /order
├── 👤 创作者管理 /creator
├── 💰 提现审核 /withdrawal
├── 📝 定价区间配置 /pricing
├── 🧑 用户管理 /user
└── 📈 对账报表 /report/reconciliation
```

---

## Step B · 8 页面实现（Day 3-7，5 天，Mock 数据先）

每个页面对应 prototype 里的 HTML 原型，按原型结构翻译成 Ant Design Vue 组件。

| 页面 | 路径 | 原型参考 | 核心组件 | 后端 API |
|------|------|----------|----------|----------|
| **数据看板** | `/dashboard` | `admin-dashboard.html` | Statistic 统计卡 + Line 图（日收入）+ Table（Top10 创作者） | `GET /internal/stream/reports/daily` |
| **审核中心** | `/review` | `admin-review.html` | Table（publish_status=pending_review）+ Modal（视频预览）+ Space（通过/驳回按钮 + 驳回原因 Modal） | `GET /internal/stream/review/queue` + `POST /internal/stream/review/{id}/approve\|reject` |
| **订单列表** | `/order` | `admin-order.html` | Table（状态筛选 + 时间范围 + 导出 CSV）+ 详情 Drawer | `GET /internal/stream/orders` |
| **创作者管理** | `/creator` | creator.html 提取 | Table（入驻状态筛选）+ Drawer（定价区间配置 + 冻结/解禁） | `GET/PUT /internal/creator/accounts` |
| **提现审核** | `/withdrawal` | — | Table（状态筛选）+ Modal（银行账户 + 审批） | `GET /internal/stream/withdrawals` + `POST /internal/stream/withdrawals/{id}/approve\|reject` |
| **定价区间配置** | `/pricing` | — | Form（创作者 + min/max + auto_publish_price 开关 + 全局解锁定价） | `PUT /internal/creator/accounts/{id}/pricing` |
| **用户管理** | `/user` | — | Table（age_verified 筛选 + VIP 标识）+ Drawer（观看历史） | `GET /sys/user/list`（已有）+ `GET /stream/play-history?user_id={id}` |
| **对账报表** | `/report/reconciliation` | — | Table（按月分润聚合 + 平台/创作者分润饼图）+ 导出 Excel | `GET /internal/stream/reconciliation` |

### 审核中心核心代码（参考 prototype admin-review.html）

```tsx
// src/views/review/index.vue
<script setup lang="ts">
import { fetchReviewQueue, approveReview, rejectReview } from '@/api/stream';
import { message, Modal } from 'ant-design-vue';

const queue = ref<ReviewItem[]>([]);
const rejectModal = ref(false);
const rejectReason = ref('');
const currentId = ref<number>();

const columns = [
  { title: '视频标题', dataIndex: 'title', width: 200 },
  { title: '创作者', dataIndex: 'creator_name', width: 150 },
  { title: '价格', dataIndex: 'price_amount', width: 100 },
  { title: '提交时间', dataIndex: 'submitted_at', width: 180 },
  { title: '操作', key: 'action', width: 200 },
];

const load = async () => {
  queue.value = await fetchReviewQueue({ publish_status: 'pending_review' });
};
const approve = async (id: number) => {
  await approveReview(id); message.success('审核通过'); load();
};
const submitReject = async () => {
  await rejectReview(currentId.value, rejectReason.value);
  message.success('已驳回'); rejectModal.value = false; load();
};
</script>

<template>
  <a-page-header title="审核中心" @back="router.back()" />
  <a-table :columns="columns" :data-source="queue" row-key="id">
    <template #bodyCell="{ column, record }">
      <template v-if="column.key === 'action'">
        <a-space>
          <a-button type="primary" size="small" @click="approve(record.id)">通过</a-button>
          <a-button danger size="small" @click="currentId=record.id; rejectModal=true">驳回</a-button>
        </a-space>
      </template>
    </template>
  </a-table>
  <a-modal v-model:open="rejectModal" title="驳回原因" :confirm-loading="loading" @ok="submitReject">
    <a-textarea v-model:value="rejectReason" :rows="3" placeholder="请填写驳回原因..." />
  </a-modal>
</template>
```

### 创作者定价区间配置（`/pricing`）

```tsx
// src/views/pricing/index.vue
const pricingForm = reactive({
  creatorId: undefined as number | undefined,
  priceAmountMin: 9.99,
  priceAmountMax: 99.99,
  autoPublishPrice: true,
  globalUnlockPrice: 199.00,
});

const submit = async () => {
  await creatorApi.updatePricing(pricingForm.creatorId!, {
    priceAmountMin: pricingForm.priceAmountMin,
    priceAmountMax: pricingForm.priceAmountMax,
    autoPublishPrice: pricingForm.autoPublishPrice,
    globalUnlockPrice: pricingForm.globalUnlockPrice,
  });
  message.success('定价区间已更新');
};
```

### Mock → 真实切换点

```ts
// vite.config.ts
export default defineConfig({
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',  // Gateway
        changeOrigin: true,
        rewrite: p => p.replace(/^\/api/, ''),
      },
    },
  },
});

// Day 1-3: 后端 API 还没好，前端用 Mock
// Day 8: 把所有 src/api/*.ts 里的 Mock 数据删掉，直接走真实接口
```

---

## Step C · 接真实 API + UI 打磨（Day 8，1 天）

| # | 任务 |
|---|------|
| C.1 | 删除所有 `src/mock/*.json` + API 客户端里的 Mock fallback，走 Gateway 8080 |
| C.2 | 8 页面全部跑通真实接口（审核通过 → publish_status=published 等） |
| C.3 | 表单校验补全 + Loading + Empty + 错误边界 |
| C.4 | 数据看板加 ECharts（日收入曲线图 + 平台/创作者分润饼图） |

---

## 验收 Checklist

- [ ] `npm run build` 无 TS 错误
- [ ] 登录对接 Sa-Token，Token 持久化到 localStorage
- [ ] 侧边栏 8 菜单可点击跳转
- [ ] 审核中心：通过/驳回 → 后端 `publish_status` 变化可查
- [ ] 创作者管理：定价区间改 → 前端表单校验实时提示
- [ ] 订单列表：导出 CSV 正常
- [ ] 数据看板：ECharts 曲线渲染正常
- [ ] 未登录 → 401 → 自动跳 /login

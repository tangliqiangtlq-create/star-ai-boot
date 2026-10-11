import os

ROOT = '/Users/luohuan/Documents/trae_projects/mp-'

FILES_ADMIN = {
    'star-ai-admin/vite.config.ts': """import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import path from 'node:path'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return {
    plugins: [vue()],
    resolve: {
      alias: { '@': path.resolve(__dirname, 'src') }
    },
    server: {
      port: 3002,
      proxy: {
        '/jeecg-boot': {
          target: env.VITE_API_BASE_URL || 'http://localhost:8080',
          changeOrigin: true
        }
      }
    }
  }
})
""",
    'star-ai-admin/tsconfig.json': """{
  "compilerOptions": {
    "target": "ES2022",
    "useDefineForClassFields": true,
    "module": "ESNext",
    "lib": ["ES2022", "DOM", "DOM.Iterable"],
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "isolatedModules": true,
    "moduleDetection": "force",
    "noEmit": true,
    "jsx": "preserve",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true,
    "baseUrl": ".",
    "paths": { "@/*": ["src/*"] },
    "types": ["node"]
  },
  "include": ["src/**/*.ts", "src/**/*.tsx", "src/**/*.vue", "vite.config.ts"]
}
""",
    'star-ai-admin/tsconfig.node.json': """{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true
  },
  "include": ["vite.config.ts"]
}
""",
    'star-ai-admin/index.html': """<!DOCTYPE html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <link rel="icon" type="image/svg+xml" href="/vite.svg" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>榴莲TV 管理后台</title>
  </head>
  <body>
    <div id="app"></div>
    <script type="module" src="/src/main.ts"></script>
  </body>
</html>
""",
    'star-ai-admin/.env.development': "VITE_API_BASE_URL=http://localhost:8080\nVITE_APP_TITLE=榴莲TV 管理后台 (Dev)\n",
    'star-ai-admin/.env.production': "VITE_API_BASE_URL=https://api.starai.video\nVITE_APP_TITLE=榴莲TV 管理后台\n",
    'star-ai-admin/.gitignore': "node_modules\ndist\n.DS_Store\n*.log\n.env.local\n.vscode/*\n!.vscode/extensions.json\n",
    'star-ai-admin/src/main.ts': """import { createApp } from 'vue'
import { createPinia } from 'pinia'
import piniaPluginPersistedstate from 'pinia-plugin-persistedstate'
import Antd from 'ant-design-vue'
import 'ant-design-vue/dist/reset.css'
import App from './App.vue'
import router from './router'
import './styles/global.less'

const pinia = createPinia()
pinia.use(piniaPluginPersistedstate)

const app = createApp(App)
app.use(pinia)
app.use(router)
app.use(Antd)
app.mount('#app')
""",
    'star-ai-admin/src/App.vue': """<template>
  <a-config-provider :locale="zhCN">
    <router-view />
  </a-config-provider>
</template>

<script setup lang="ts">
import zhCN from 'ant-design-vue/es/locale/zh_CN'
</script>
""",
    'star-ai-admin/src/vite-env.d.ts': "/// <reference types=\"vite/client\" />\n",
    'star-ai-admin/src/styles/global.less': """html, body, #app {
  height: 100%;
  margin: 0;
  padding: 0;
}

body {
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, 'Helvetica Neue', Arial, sans-serif;
}
""",
    'star-ai-admin/src/router/index.ts': """import { createRouter, createWebHashHistory, type RouteRecordRaw } from 'vue-router'

const routes: RouteRecordRaw[] = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue'),
    meta: { title: '登录', public: true }
  },
  {
    path: '/',
    component: () => import('@/layouts/BasicLayout.vue'),
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue'),
        meta: { title: '数据看板', icon: 'DashboardOutlined' }
      },
      {
        path: 'review',
        name: 'Review',
        component: () => import('@/views/review/index.vue'),
        meta: { title: '审核中心', icon: 'AuditOutlined' }
      },
      {
        path: 'order',
        name: 'Order',
        component: () => import('@/views/order/index.vue'),
        meta: { title: '订单管理', icon: 'ShoppingCartOutlined' }
      },
      {
        path: 'user',
        name: 'User',
        component: () => import('@/views/user/index.vue'),
        meta: { title: '用户管理', icon: 'UserOutlined' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('X-Access-Token')
  if (to.meta.public || token) {
    next()
  } else {
    next({ path: '/login', query: { redirect: to.fullPath } })
  }
})

export default router
""",
    'star-ai-admin/src/utils/request.ts': """import axios from 'axios'
import { message } from 'ant-design-vue'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
  timeout: 15000
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('X-Access-Token')
  if (token) {
    config.headers['X-Access-Token'] = token
    config.headers['Authorization'] = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    if (res.success === false && res.code !== 200) {
      message.error(res.message || '请求失败')
      return Promise.reject(res)
    }
    return res
  },
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem('X-Access-Token')
      window.location.hash = '#/login'
    } else {
      message.error(error?.response?.data?.message || error.message || '网络异常')
    }
    return Promise.reject(error)
  }
)

export default request
""",
    'star-ai-admin/src/stores/user.ts': """import { defineStore } from 'pinia'
import { ref } from 'vue'
import request from '@/utils/request'

export const useUserStore = defineStore(
  'user',
  () => {
    const token = ref<string>(localStorage.getItem('X-Access-Token') || '')
    const username = ref('')
    const avatar = ref('')

    async function login(username: string, password: string) {
      const res = await request.post('/jeecg-boot/sys/login', { username, password, captcha: 'check' })
      if (res.success) {
        token.value = res.result?.token || ''
        localStorage.setItem('X-Access-Token', token.value)
      }
      return res
    }

    async function logout() {
      try { await request.post('/jeecg-boot/sys/logout') } catch {}
      token.value = ''
      username.value = ''
      avatar.value = ''
      localStorage.removeItem('X-Access-Token')
    }

    return { token, username, avatar, login, logout }
  },
  { persist: true }
)
""",
    'star-ai-admin/src/layouts/BasicLayout.vue': """<template>
  <a-layout class="basic-layout">
    <a-layout-sider v-model:collapsed="collapsed" collapsible :trigger="null" width="220">
      <div class="logo">
        <span v-if="!collapsed">榴莲TV Admin</span>
        <span v-else>🥝</span>
      </div>
      <a-menu theme="dark" mode="inline" :selected-keys="selectedKeys" @click="onMenuClick">
        <a-menu-item v-for="item in menuItems" :key="item.path">
          <component :is="item.icon" />
          <span>{{ item.title }}</span>
        </a-menu-item>
      </a-menu>
    </a-layout-sider>
    <a-layout>
      <a-layout-header class="header">
        <menu-fold-outlined class="trigger" @click="collapsed = !collapsed" />
        <div class="spacer" />
        <a-dropdown>
          <div class="user-info">
            <a-avatar :size="32" style="background-color: #1890ff">A</a-avatar>
            <span class="username">Admin</span>
          </div>
          <template #overlay>
            <a-menu @click="onUserMenuClick">
              <a-menu-item key="logout">退出登录</a-menu-item>
            </a-menu>
          </template>
        </a-dropdown>
      </a-layout-header>
      <a-layout-content class="content">
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </a-layout-content>
    </a-layout>
  </a-layout>
</template>

<script setup lang="ts">
import { ref, computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  DashboardOutlined,
  AuditOutlined,
  ShoppingCartOutlined,
  UserOutlined,
  MenuFoldOutlined
} from '@ant-design/icons-vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)

const menuItems = [
  { path: '/dashboard', title: '数据看板', icon: () => h(DashboardOutlined) },
  { path: '/review', title: '审核中心', icon: () => h(AuditOutlined) },
  { path: '/order', title: '订单管理', icon: () => h(ShoppingCartOutlined) },
  { path: '/user', title: '用户管理', icon: () => h(UserOutlined) }
]

const selectedKeys = computed(() => [route.path])

function onMenuClick({ key }: { key: string }) {
  router.push(key)
}

function onUserMenuClick({ key }: { key: string }) {
  if (key === 'logout') {
    userStore.logout().then(() => router.push('/login'))
  }
}
</script>

<style lang="less" scoped>
.basic-layout { height: 100vh; }
.logo {
  height: 64px; display: flex; align-items: center; justify-content: center;
  color: #fff; font-weight: bold; font-size: 16px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}
.header {
  background: #fff; padding: 0 16px; display: flex; align-items: center;
  box-shadow: 0 1px 4px rgba(0,21,41,0.08);
}
.trigger { font-size: 18px; cursor: pointer; }
.spacer { flex: 1; }
.user-info { display: flex; align-items: center; gap: 8px; cursor: pointer; }
.username { color: #333; }
.content { margin: 16px; background: #fff; padding: 24px; min-height: calc(100vh - 112px); }
.fade-enter-active, .fade-leave-active { transition: opacity 0.2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
""",
    'star-ai-admin/src/views/login/index.vue': """<template>
  <div class="login-container">
    <div class="login-card">
      <h1 class="title">榴莲TV 管理后台</h1>
      <p class="subtitle">Star AI Admin</p>
      <a-form :model="form" :rules="rules" ref="formRef" layout="vertical" @finish="onSubmit">
        <a-form-item label="用户名" name="username">
          <a-input v-model:value="form.username" placeholder="请输入用户名" size="large">
            <template #prefix><user-outlined /></template>
          </a-input>
        </a-form-item>
        <a-form-item label="密码" name="password">
          <a-input-password v-model:value="form.password" placeholder="请输入密码" size="large">
            <template #prefix><lock-outlined /></template>
          </a-input-password>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" html-type="submit" size="large" block :loading="loading">登录</a-button>
        </a-form-item>
      </a-form>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { UserOutlined, LockOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名' }],
  password: [{ required: true, message: '请输入密码' }]
}

async function onSubmit() {
  try {
    loading.value = true
    await formRef.value.validate()
    const res = await userStore.login(form.username, form.password)
    if (res.success) {
      message.success('登录成功')
      const redirect = (route.query.redirect as string) || '/dashboard'
      router.push(redirect)
    }
  } catch {}
  finally { loading.value = false }
}
</script>

<style lang="less" scoped>
.login-container {
  height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
}
.login-card {
  width: 400px; padding: 40px; background: #fff; border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0,0,0,0.2);
}
.title { text-align: center; margin: 0 0 4px; font-size: 24px; }
.subtitle { text-align: center; margin: 0 0 32px; color: #999; font-size: 14px; }
</style>
""",
    'star-ai-admin/src/views/dashboard/index.vue': """<template>
  <div>
    <a-row :gutter="16">
      <a-col :span="6" v-for="stat in stats" :key="stat.title">
        <a-card :bordered="false" class="stat-card">
          <div class="stat-title">{{ stat.title }}</div>
          <div class="stat-value" :style="{ color: stat.color }">{{ stat.value }}</div>
          <div class="stat-desc">较昨日 {{ stat.delta }}</div>
        </a-card>
      </a-col>
    </a-row>
    <a-row :gutter="16" style="margin-top: 16px">
      <a-col :span="12">
        <a-card title="近7天订单趋势" :bordered="false">
          <div class="chart-placeholder">📊 图表区域（待接入 ECharts）</div>
        </a-card>
      </a-col>
      <a-col :span="12">
        <a-card title="最新待审核" :bordered="false">
          <a-table :columns="reviewCols" :data-source="reviewData" :pagination="false" size="small">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'status'">
                <a-tag :color="record.status === '待审核' ? 'orange' : 'green'">{{ record.status }}</a-tag>
              </template>
            </template>
          </a-table>
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
const stats = [
  { title: '今日新增用户', value: '1,234', delta: '+12.5%', color: '#1890ff' },
  { title: '今日订单数', value: '567', delta: '+8.2%', color: '#52c41a' },
  { title: '今日营收', value: '$12,345', delta: '+23.1%', color: '#fa8c16' },
  { title: '待审核内容', value: '89', delta: '-5', color: '#eb2f96' }
]
const reviewCols = [
  { title: '内容标题', dataIndex: 'title', key: 'title' },
  { title: '提交者', dataIndex: 'author', key: 'author' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '时间', dataIndex: 'time', key: 'time' }
]
const reviewData = [
  { title: '示例视频 #1', author: 'creator_01', status: '待审核', time: '10:23' },
  { title: '示例视频 #2', author: 'creator_02', status: '已通过', time: '09:15' },
  { title: '示例视频 #3', author: 'creator_03', status: '待审核', time: '08:47' }
]
</script>

<style lang="less" scoped>
.stat-card { border-radius: 8px; }
.stat-title { color: #999; font-size: 14px; }
.stat-value { font-size: 28px; font-weight: bold; margin: 8px 0; }
.stat-desc { color: #bbb; font-size: 12px; }
.chart-placeholder { height: 200px; display: flex; align-items: center; justify-content: center; color: #999; background: #fafafa; border-radius: 4px; }
</style>
""",
    'star-ai-admin/src/views/review/index.vue': """<template>
  <div>
    <a-card :bordered="false">
      <template #title>
        <a-space>
          <span>审核中心</span>
          <a-tag color="orange">待处理 {{ total }}</a-tag>
        </a-space>
      </template>
      <a-form layout="inline" :model="query" style="margin-bottom: 16px">
        <a-form-item label="审核状态">
          <a-select v-model:value="query.status" style="width: 150px" allow-clear>
            <a-select-option value="待审核">待审核</a-select-option>
            <a-select-option value="已通过">已通过</a-select-option>
            <a-select-option value="已拒绝">已拒绝</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" @click="onSearch">查询</a-button>
        </a-form-item>
      </a-form>
      <a-table :columns="columns" :data-source="data" :pagination="pagination">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status)">{{ record.status }}</a-tag>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="onApprove(record)">通过</a-button>
              <a-button type="link" size="small" danger @click="onReject(record)">拒绝</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'
import { message } from 'ant-design-vue'

const query = reactive({ status: '待审核' })
const total = ref(89)
const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '内容标题', dataIndex: 'title', key: 'title' },
  { title: '创作者', dataIndex: 'creator', key: 'creator' },
  { title: '提交时间', dataIndex: 'submitTime', key: 'submitTime' },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '操作', key: 'action', width: 150 }
]
const data = ref([
  { id: 1, title: '示例视频 A', creator: 'creator_01', submitTime: '2026-10-11 10:23', status: '待审核' },
  { id: 2, title: '示例视频 B', creator: 'creator_02', submitTime: '2026-10-11 09:15', status: '待审核' }
])
const pagination = { current: 1, pageSize: 10, total: 89 }

function statusColor(s: string) {
  return { '待审核': 'orange', '已通过': 'green', '已拒绝': 'red' }[s] || 'default'
}
function onSearch() { message.info(`查询: ${query.status}`) }
function onApprove(r: any) { message.success(`已通过 ${r.title}`) }
function onReject(r: any) { message.warning(`已拒绝 ${r.title}`) }
</script>
""",
    'star-ai-admin/src/views/order/index.vue': """<template>
  <div>
    <a-card :bordered="false" title="订单管理">
      <a-form layout="inline" :model="query" style="margin-bottom: 16px">
        <a-form-item label="订单号">
          <a-input v-model:value="query.orderNo" placeholder="请输入订单号" allow-clear />
        </a-form-item>
        <a-form-item label="状态">
          <a-select v-model:value="query.status" style="width: 150px" allow-clear>
            <a-select-option value="PAID">已支付</a-select-option>
            <a-select-option value="PENDING">待支付</a-select-option>
            <a-select-option value="REFUNDED">已退款</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary">查询</a-button>
          <a-button style="margin-left: 8px">导出</a-button>
        </a-form-item>
      </a-form>
      <a-table :columns="columns" :data-source="data" :pagination="pagination" row-key="id">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="orderStatusColor(record.status)">{{ record.status }}</a-tag>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { reactive, ref } from 'vue'

const query = reactive({ orderNo: '', status: undefined })
const columns = [
  { title: '订单号', dataIndex: 'orderNo', key: 'orderNo' },
  { title: '用户', dataIndex: 'user', key: 'user' },
  { title: '商品', dataIndex: 'product', key: 'product' },
  { title: '金额', dataIndex: 'amount', key: 'amount' },
  { title: '支付方式', dataIndex: 'payMethod', key: 'payMethod' },
  { title: '状态', dataIndex: 'status', key: 'status' },
  { title: '时间', dataIndex: 'time', key: 'time' }
]
const data = ref([
  { orderNo: 'ORD20261011001', user: 'user_01', product: '月度会员', amount: '$9.99', payMethod: 'Stripe', status: 'PAID', time: '2026-10-11 10:30' },
  { orderNo: 'ORD20261011002', user: 'user_02', product: '单集解锁', amount: '$1.99', payMethod: '微信', status: 'PENDING', time: '2026-10-11 10:28' }
])
const pagination = { current: 1, pageSize: 10, total: 1523 }

function orderStatusColor(s: string) {
  return { PAID: 'green', PENDING: 'orange', REFUNDED: 'red' }[s] || 'default'
}
</script>
""",
    'star-ai-admin/src/views/user/index.vue': """<template>
  <div>
    <a-card :bordered="false" title="用户管理">
      <a-table :columns="columns" :data-source="data" :pagination="pagination" row-key="id">
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'vip'">
            <a-tag v-if="record.vip" color="gold">VIP</a-tag>
            <span v-else style="color: #bbb">普通</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small">详情</a-button>
              <a-button type="link" size="small" danger>封禁</a-button>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>
  </div>
</template>

<script setup lang="ts">
const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '用户名', dataIndex: 'username', key: 'username' },
  { title: '邮箱', dataIndex: 'email', key: 'email' },
  { title: 'Region', dataIndex: 'region', key: 'region' },
  { title: '会员状态', key: 'vip', width: 100 },
  { title: '注册时间', dataIndex: 'regTime', key: 'regTime' },
  { title: '操作', key: 'action', width: 150 }
]
const data = [
  { id: 1, username: 'user_01', email: 'a@b.com', region: 'cn', vip: true, regTime: '2026-09-01' },
  { id: 2, username: 'user_02', email: 'c@d.com', region: 'sg', vip: false, regTime: '2026-09-15' }
]
const pagination = { current: 1, pageSize: 10, total: 998 }
</script>
"""
}

FILES_APP = {
    'star-ai-app/package.json': """{
  "name": "star-ai-app",
  "version": "0.1.0",
  "private": true,
  "scripts": {
    "dev": "next dev -p 3000",
    "build": "next build",
    "start": "next start -p 3000",
    "lint": "next lint",
    "cap:init": "cap init",
    "cap:sync": "cap sync",
    "cap:ios": "cap open ios",
    "cap:android": "cap open android"
  },
  "dependencies": {
    "next": "15.0.3",
    "react": "19.0.0-rc-66855b96-20241106",
    "react-dom": "19.0.0-rc-66855b96-20241106",
    "tailwindcss": "3.4.14",
    "hls.js": "1.5.17",
    "@capacitor/core": "6.1.2",
    "@capacitor/cli": "6.1.2",
    "@capacitor/ios": "6.1.2",
    "@capacitor/android": "6.1.2",
    "zustand": "5.0.1",
    "axios": "1.7.7",
    "i18next": "24.0.0",
    "react-hook-form": "7.53.0",
    "zod": "3.23.8",
    "@hookform/resolvers": "3.9.1",
    "sonner": "1.7.0",
    "next-auth": "4.24.10"
  },
  "devDependencies": {
    "typescript": "5.6.3",
    "@types/node": "22.8.1",
    "@types/react": "19.0.0-rc",
    "@types/react-dom": "19.0.0-rc",
    "autoprefixer": "10.4.20",
    "postcss": "8.4.49",
    "eslint": "9.13.0",
    "eslint-config-next": "15.0.3"
  }
}
""",
    'star-ai-app/tsconfig.json': """{
  "compilerOptions": {
    "target": "ES2022",
    "lib": ["dom", "dom.iterable", "esnext"],
    "allowJs": true,
    "skipLibCheck": true,
    "strict": true,
    "noEmit": true,
    "esModuleInterop": true,
    "module": "esnext",
    "moduleResolution": "bundler",
    "resolveJsonModule": true,
    "isolatedModules": true,
    "jsx": "preserve",
    "incremental": true,
    "plugins": [{ "name": "next" }],
    "paths": { "@/*": ["./src/*"] }
  },
  "include": ["next-env.d.ts", "**/*.ts", "**/*.tsx", ".next/types/**/*.ts"],
  "exclude": ["node_modules"]
}
""",
    'star-ai-app/next.config.ts': """import type { NextConfig } from 'next'

const nextConfig: NextConfig = {
  images: {
    remotePatterns: [
      { protocol: 'https', hostname: '**.starai.video' },
      { protocol: 'https', hostname: '**.amazonaws.com' },
      { protocol: 'https', hostname: '**.cloudfront.net' }
    ]
  },
  output: 'standalone'
}

export default nextConfig
""",
    'star-ai-app/tailwind.config.ts': """import type { Config } from 'tailwindcss'

const config: Config = {
  content: ['./src/**/*.{js,ts,jsx,tsx,mdx}'],
  theme: {
    extend: {
      colors: {
        primary: '#ff6b9d',
        bg: '#0a0a0f'
      }
    }
  },
  plugins: []
}
export default config
""",
    'star-ai-app/postcss.config.mjs': """export default {
  plugins: {
    tailwindcss: {},
    autoprefixer: {}
  }
}
""",
    'star-ai-app/capacitor.config.ts': """import type { CapacitorConfig } from '@capacitor/cli'

const config: CapacitorConfig = {
  appId: 'video.starai.app',
  appName: '榴莲TV',
  webDir: 'out',
  android: {
    allowMixedContent: true,
    captureInput: true,
    webContentsDebuggingEnabled: false
  },
  server: {
    androidScheme: 'https',
    allowNavigation: ['**.starai.video', '**.amazonaws.com', '**.cloudfront.net']
  },
  plugins: {
    SplashScreen: { launchShowDuration: 2000 }
  }
}

export default config
""",
    'star-ai-app/.env.development': "NEXT_PUBLIC_API_BASE_URL=http://localhost:8080\nNEXT_PUBLIC_APP_ENV=development\n",
    'star-ai-app/.env.production': "NEXT_PUBLIC_API_BASE_URL=https://api.starai.video\nNEXT_PUBLIC_APP_ENV=production\n",
    'star-ai-app/.gitignore': """node_modules
.next
out
dist
.DS_Store
*.log
.env*.local
ios/
android/
.capacitor/
""",
    'star-ai-app/src/app/layout.tsx': """import type { Metadata, Viewport } from 'next'
import './globals.css'

export const metadata: Metadata = {
  title: '榴莲TV - Star AI Video',
  description: 'Premium video streaming platform',
  icons: { icon: '/favicon.ico' }
}

export const viewport: Viewport = {
  themeColor: '#0a0a0f',
  width: 'device-width',
  initialScale: 1,
  maximumScale: 1
}

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="zh-CN">
      <body className="bg-bg text-white antialiased">
        {children}
      </body>
    </html>
  )
}
""",
    'star-ai-app/src/app/globals.css': """@tailwind base;
@tailwind components;
@tailwind utilities;

:root { color-scheme: dark; }

html, body { background: #0a0a0f; }

.app { padding-top: env(safe-area-inset-top); padding-bottom: env(safe-area-inset-bottom); }
""",
    'star-ai-app/src/app/page.tsx': """export default function HomePage() {
  return (
    <main className="min-h-screen">
      <section className="hero py-20 px-6 text-center">
        <h1 className="text-4xl md:text-6xl font-bold bg-gradient-to-r from-primary to-pink-400 bg-clip-text text-transparent">
          榴莲TV
        </h1>
        <p className="mt-4 text-gray-400 text-lg">Premium Video Streaming Platform</p>
        <div className="mt-8 flex gap-4 justify-center">
          <a href="/browse" className="px-6 py-3 bg-primary rounded-full font-semibold hover:opacity-90 transition">
            立即浏览
          </a>
          <a href="/subscription" className="px-6 py-3 border border-white/20 rounded-full hover:bg-white/10 transition">
            开通会员
          </a>
        </div>
      </section>
      <section className="px-6 pb-20">
        <h2 className="text-2xl font-bold mb-6">热门推荐</h2>
        <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
          {Array.from({ length: 6 }).map((_, i) => (
            <div key={i} className="aspect-video bg-white/5 rounded-lg flex items-center justify-center text-gray-500">
              视频占位 {i + 1}
            </div>
          ))}
        </div>
      </section>
    </main>
  )
}
""",
    'star-ai-app/src/app/browse/page.tsx': """export default function BrowsePage() {
  return (
    <main className="min-h-screen px-6 py-8">
      <h1 className="text-3xl font-bold mb-6">剧集浏览</h1>
      <div className="flex gap-2 mb-8 flex-wrap">
        {['全部', '动作', '剧情', '喜剧', '综艺', '纪录片'].map((c) => (
          <button key={c} className="px-4 py-2 rounded-full bg-white/5 hover:bg-white/10 transition">
            {c}
          </button>
        ))}
      </div>
      <div className="grid grid-cols-2 md:grid-cols-4 lg:grid-cols-6 gap-4">
        {Array.from({ length: 12 }).map((_, i) => (
          <a key={i} href={`/video/${i + 1}`} className="aspect-video bg-white/5 rounded-lg flex flex-col items-center justify-center text-gray-500 hover:bg-white/10 transition">
            <span>🎬</span>
            <span className="mt-2 text-sm">视频 #{i + 1}</span>
          </a>
        ))}
      </div>
    </main>
  )
}
""",
    'star-ai-app/src/app/video/[id]/page.tsx': """import { notFound } from 'next/navigation'

export default function VideoDetailPage({ params }: { params: { id: string } }) {
  if (!params.id) notFound()
  return (
    <main className="min-h-screen">
      <div className="aspect-video bg-black flex items-center justify-center">
        <div className="text-gray-500 text-center">
          <div className="text-6xl mb-4">▶️</div>
          <div>视频播放器区域 (hls.js)</div>
        </div>
      </div>
      <div className="px-6 py-8">
        <h1 className="text-2xl font-bold mb-2">视频标题 #{params.id}</h1>
        <p className="text-gray-400 mb-6">创作者: creator_01 · 播放量 12.5k</p>
        <div className="border border-white/10 rounded-lg p-4 mb-6">
          <h3 className="font-semibold mb-2">解锁选项</h3>
          <div className="flex gap-3 flex-wrap">
            <button className="px-4 py-2 bg-primary rounded">单集 $1.99</button>
            <button className="px-4 py-2 border border-white/20 rounded">月度会员 $9.99</button>
          </div>
        </div>
      </div>
    </main>
  )
}
""",
    'star-ai-app/src/app/subscription/page.tsx': """const plans = [
  { name: '月度会员', price: '$9.99', period: '/月', features: ['无限制观看', '高清画质', '离线下载'], popular: false },
  { name: '年度会员', price: '$99.99', period: '/年', features: ['无限制观看', '4K 画质', '离线下载', '创作者分润'], popular: true },
  { name: '终身会员', price: '$299', period: '一次性', features: ['所有功能永久解锁', '优先客服', '早期新功能体验'], popular: false }
]

export default function SubscriptionPage() {
  return (
    <main className="min-h-screen px-6 py-16">
      <div className="text-center mb-12">
        <h1 className="text-4xl font-bold mb-4">选择你的会员方案</h1>
        <p className="text-gray-400">随时随地，畅享精彩内容</p>
      </div>
      <div className="grid md:grid-cols-3 gap-6 max-w-5xl mx-auto">
        {plans.map((plan) => (
          <div key={plan.name} className={`rounded-2xl p-6 border relative ${plan.popular ? 'border-primary bg-primary/10' : 'border-white/10 bg-white/5'}`}>
            {plan.popular && <span className="absolute top-3 right-3 text-xs bg-primary px-2 py-1 rounded">推荐</span>}
            <h3 className="text-xl font-bold">{plan.name}</h3>
            <div className="mt-4">
              <span className="text-4xl font-bold">{plan.price}</span>
              <span className="text-gray-400 ml-1">{plan.period}</span>
            </div>
            <ul className="mt-6 space-y-2 text-gray-300">
              {plan.features.map((f) => (
                <li key={f}>✓ {f}</li>
              ))}
            </ul>
            <button className={`mt-6 w-full py-3 rounded-lg font-semibold ${plan.popular ? 'bg-primary' : 'border border-white/20 hover:bg-white/10'}`}>
              立即开通
            </button>
          </div>
        ))}
      </div>
    </main>
  )
}
""",
    'star-ai-app/src/app/creator/dashboard/page.tsx': """export default function CreatorDashboard() {
  return (
    <main className="min-h-screen px-6 py-8">
      <h1 className="text-3xl font-bold mb-6">创作者中心</h1>
      <div className="grid md:grid-cols-4 gap-4 mb-8">
        {[
          { label: '总播放量', value: '125.6k' },
          { label: '粉丝', value: '3.2k' },
          { label: '本月收入', value: '$1,234' },
          { label: '待审核', value: '3' }
        ].map((s) => (
          <div key={s.label} className="bg-white/5 rounded-lg p-4">
            <div className="text-gray-400 text-sm">{s.label}</div>
            <div className="text-2xl font-bold mt-1">{s.value}</div>
          </div>
        ))}
      </div>
      <div className="bg-white/5 rounded-lg p-6">
        <h2 className="text-xl font-bold mb-4">我的视频</h2>
        <div className="space-y-3">
          {['视频 A', '视频 B', '视频 C'].map((t, i) => (
            <div key={i} className="flex items-center gap-4 p-3 bg-black/30 rounded">
              <div className="w-32 aspect-video bg-white/10 rounded flex items-center justify-center">📹</div>
              <div className="flex-1">
                <div className="font-semibold">{t}</div>
                <div className="text-sm text-gray-400">播放 {1000 + i * 500} · 收益 ${10 + i * 5}</div>
              </div>
              <button className="px-3 py-1 border border-white/20 rounded text-sm">编辑</button>
            </div>
          ))}
        </div>
      </div>
    </main>
  )
}
""",
    'star-ai-app/src/lib/config/env.ts': """export const env = {
  IS_CAPACITOR: typeof document !== 'undefined' && document.querySelector('meta[name=\"capacitor\"]') !== null,
  IS_IOS: typeof navigator !== 'undefined' && /iPhone|iPad|iPod/.test(navigator.userAgent),
  IS_ANDROID: typeof navigator !== 'undefined' && /Android/.test(navigator.userAgent),
  API_BASE: process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080'
}

if (typeof document !== 'undefined' && env.IS_CAPACITOR) {
  document.documentElement.classList.add('app')
}
""",
    'star-ai-app/src/lib/api/client.ts': """import axios from 'axios'

const client = axios.create({
  baseURL: typeof window !== 'undefined'
    ? (process.env.NEXT_PUBLIC_API_BASE_URL || '/jeecg-boot')
    : (process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080/jeecg-boot'),
  timeout: 15000
})

client.interceptors.request.use((config) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem('X-Access-Token')
    if (token) {
      config.headers['X-Access-Token'] = token
      config.headers['Authorization'] = `Bearer ${token}`
    }
  }
  return config
})

client.interceptors.response.use(
  (res) => res.data,
  (error) => {
    if (error.response?.status === 401 && typeof window !== 'undefined') {
      localStorage.removeItem('X-Access-Token')
      window.location.href = '/login'
    }
    return Promise.reject(error)
  }
)

export default client
""",
    'star-ai-app/src/store/auth.ts': """import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface AuthState {
  token: string | null
  user: { id: string; username: string; region: string } | null
  setAuth: (token: string, user: AuthState['user']) => void
  logout: () => void
}

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      token: null,
      user: null,
      setAuth: (token, user) => set({ token, user }),
      logout: () => { localStorage.removeItem('X-Access-Token'); set({ token: null, user: null }) }
    }),
    { name: 'star-ai-auth' }
  )
)
"""
}

for path, content in {**FILES_ADMIN, **FILES_APP}.items():
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w') as f:
        f.write(content)
    print(f'✅ {path}')

print(f'\n🎉 共创建 {len(FILES_ADMIN) + len(FILES_APP)} 个文件')

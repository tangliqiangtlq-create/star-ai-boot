import axios, { AxiosInstance, AxiosError, InternalAxiosRequestConfig } from 'axios';
import { env } from '@/lib/config/env';

const api: AxiosInstance = axios.create({
  baseURL: env.API_BASE,
  timeout: 15000,
  headers: { 'Content-Type': 'application/json' }
});

api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  if (typeof window !== 'undefined') {
    const token = localStorage.getItem(env.TOKEN_KEY);
    if (token && config.headers) {
      config.headers.Authorization = `Bearer ${token}`;
      config.headers[env.TOKEN_KEY] = token;
    }
  }
  return config;
});

api.interceptors.response.use(
  (res) => {
    const data = res.data;
    if (data?.code === 401 || data?.success === false) {
      if (typeof window !== 'undefined') {
        localStorage.removeItem(env.TOKEN_KEY);
        window.location.href = '/login';
      }
      return Promise.reject(new Error(data?.message || '未授权'));
    }
    return data;
  },
  (err: AxiosError) => {
    if (err.response?.status === 401 && typeof window !== 'undefined') {
      localStorage.removeItem(env.TOKEN_KEY);
      window.location.href = '/login';
    }
    return Promise.reject(err);
  }
);

export default api;

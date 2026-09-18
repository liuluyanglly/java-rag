import axios, { AxiosRequestConfig } from 'axios';
import { message } from 'antd';

// 扩展 Axios 实例类型，使 get/post/put/delete 直接返回 Promise<T> (拦截器已解包 res.data)
declare module 'axios' {
  export interface AxiosInstance {
    <T = any>(config: AxiosRequestConfig): Promise<T>;
    request<T = any>(config: AxiosRequestConfig): Promise<T>;
    get<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
    delete<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
    head<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
    options<T = any>(url: string, config?: AxiosRequestConfig): Promise<T>;
    post<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>;
    put<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>;
    patch<T = any>(url: string, data?: any, config?: AxiosRequestConfig): Promise<T>;
  }
}

// Sa-Token 读取令牌的请求头名称，须与后端 SaTokenConfig 的 tokenName 保持一致；
// 该配置同时由登录接口的 tokenName 字段回传，改动后端时两侧需一起调整。
export const TOKEN_HEADER = 'satoken';

const request = axios.create({
  baseURL: '/api',
  timeout: 60000,
});

// 请求拦截器: 自动携带 Sa-Token
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token');
    if (token) {
      config.headers[TOKEN_HEADER] = token;
    }
    return config;
  },
  (error) => Promise.reject(error)
);

// 响应拦截器: 统一解包与权限失效处理
request.interceptors.response.use(
  (response) => {
    const res = response.data;
    if (res.code === 200) {
      return res.data;
    }
    if (res.code === 401) {
      message.error(res.msg || '登录凭证过期，请重新登录');
      localStorage.removeItem('token');
      window.location.href = '/login';
      return Promise.reject(new Error(res.msg || '未登录'));
    }
    message.error(res.msg || '网络请求错误');
    return Promise.reject(new Error(res.msg || 'Error'));
  },
  (error) => {
    const msg = error.response?.data?.msg || error.message || '服务器连接异常';
    message.error(msg);
    if (error.response?.status === 401) {
      localStorage.removeItem('token');
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default request;

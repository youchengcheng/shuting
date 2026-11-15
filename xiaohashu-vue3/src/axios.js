import axios from "axios";
import { message } from "@/utils/message";
import { useUserStore } from "@/stores/user";

// 创建 Axios 实例
const instance = axios.create({
    baseURL: '/api',
    timeout: 15000, // 请求超时时间
});



// 添加请求拦截器
instance.interceptors.request.use(function (config) {
    // 从 localStorage 获取 token
    const userStore = useUserStore();
    config.session = config.session || userStore.captureSession();
    if (!userStore.isCurrentSession(config.session)) {
        return Promise.reject(Object.assign(new Error('Session changed'), { code: 'ERR_SESSION_STALE' }));
    }
    const token = config.session.token;
    
    // 如果存在 token，则添加到请求头
    if (token) {
        // 添加请求头, key 为 Authorization，value 值的前缀为 'Bearer '
        config.headers['Authorization'] = `Bearer ${token}`;
    }

    return config;
}, function (error) {
    // 对请求错误做些什么
    return Promise.reject(error);
});

// 添加响应拦截器
instance.interceptors.response.use(function (response) {
    if (!useUserStore().isCurrentSession(response.config.session)) {
        return Promise.reject(Object.assign(new Error('Session changed'), { code: 'ERR_SESSION_STALE' }));
    }
    // 2xx 范围内的状态码都会触发该函数。
    // 对响应数据做点什么
    return response.data;
}, function (error) {
    // 超出 2xx 范围的状态码都会触发该函数。
    // 对响应错误做点什么
    const status = error.response?.status
    const userStore = useUserStore();
    if (error.config?.session && !userStore.isCurrentSession(error.config.session)) {
        return Promise.reject(Object.assign(new Error('Session changed'), { code: 'ERR_SESSION_STALE' }));
    }
    if (error.code === 'ERR_CANCELED' || error.code === 'ERR_SESSION_STALE') {
        return Promise.reject(error);
    }

    if (status === 401) {
        if (userStore.logout(error.config?.session)) {
            message.show('请先登录');
        }
    } else if (!error.config?.silent && (error.code === 'ECONNABORTED' || error.message?.includes('timeout'))) {
        // 请求超时（AI 接口较慢，已在 api/ai.js 里单独放宽超时时间）
        message.show('请求超时，请稍后重试');
    } else if (!error.config?.silent) {
        // 显示错误信息
        const msg = error.response?.data?.message || '请求失败';
        message.show(msg);
    }

    return Promise.reject(error);
});

// 暴露出去
export default instance;

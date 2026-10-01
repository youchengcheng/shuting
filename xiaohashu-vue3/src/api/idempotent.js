import axios from "@/axios";

// 接口前缀
const API_PREFIX = '/idempotent'

// 申请幂等 Token：发布笔记前调用，通过 Idempotent-Token 请求头携带给发布接口
export function getIdempotentToken() {
    return axios.get(`${API_PREFIX}/token`)
}

// 申请幂等 Token 的降级封装：申请失败时返回 null，调用方按"无 Token"继续（后端灰度放行，不阻塞业务）
export async function fetchIdempotentToken() {
    try {
        const res = await getIdempotentToken()
        if (res.success && res.data) {
            return res.data
        }
        console.warn('幂等令牌申请失败，降级为无 Token 请求:', res.message)
    } catch (error) {
        console.warn('幂等令牌申请异常，降级为无 Token 请求:', error)
    }
    return null
}

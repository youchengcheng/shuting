import axios from "@/axios";

// 接口前缀
const API_PREFIX = '/auth'

// 获取登录验证码
export function getVerificationCode(phone) {
    return axios.post(`${API_PREFIX}/verification/code/send`, {phone})
}

// 登录接口
export function login(loginReqVO) {
    return axios.post(`${API_PREFIX}/login`, loginReqVO)
}

// 密码登录接口
export function loginByPassword(phone, password) {
    return axios.post(`${API_PREFIX}/login`, {phone, password, type: 2})
}

// 修改密码接口
export function updatePassword(newPassword) {
    return axios.post(`${API_PREFIX}/password/update`, {newPassword})
}

// 退出登录接口
export function logout() {
    return axios.post(`${API_PREFIX}/logout`)
}

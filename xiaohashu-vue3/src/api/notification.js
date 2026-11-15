import axios from "@/axios";

// 接口前缀
const API_PREFIX = '/notification'

// 通知请求由页面显示错误提示
const config = (options) => ({
    silent: true,
    ...options
})

// 获取通知分页数据
export function listNotifications(data, options) {
    return axios.post(`${API_PREFIX}/list`, data, config(options))
}

// 获取通知未读数量
export function getNotificationSummary(options) {
    return axios.get(`${API_PREFIX}/summary`, config(options))
}

// 标记单条通知已读
export function readNotification(notificationId, options) {
    return axios.post(`${API_PREFIX}/read`, {notificationId}, config(options))
}

// 标记当前分类已加载的通知全部已读
export function readAllNotifications(category, snapshotSeq, options) {
    return axios.post(`${API_PREFIX}/read-all`, {category, snapshotSeq}, config(options))
}

// 获取通知已读状态
export function getNotificationReadState(notificationIds, options) {
    return axios.post(`${API_PREFIX}/read-state`, {notificationIds}, config(options))
}

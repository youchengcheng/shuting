import axios from '@/axios'

const config = (options) => ({ silent: true, ...options })
export const listNotifications = (data, options) => axios.post('/notification/list', data, config(options))
export const getNotificationSummary = (options) => axios.get('/notification/summary', config(options))
export const readNotification = (notificationId, options) => axios.post('/notification/read', { notificationId }, config(options))
export const readAllNotifications = (category, snapshotSeq, options) => axios.post('/notification/read-all', { category, snapshotSeq }, config(options))
export const getNotificationReadState = (notificationIds, options) => axios.post('/notification/read-state', { notificationIds }, config(options))

import axios from "@/axios";

// 接口前缀
const API_PREFIX = '/note'

// 发布笔记
export function publishNote(note) {
    return axios.post(`${API_PREFIX}/publish`, note)
}

// 获取发现页笔记数据
export function getDiscoverNotePageList(channelId, pageNo) {
    return axios.post(`${API_PREFIX}/discover/note/list`, {channelId, pageNo})
}

// 获取笔记详情
export function getNoteDetail(id) {
    return axios.post(`${API_PREFIX}/detail`, {id})
}

// 点赞笔记
export function likeNote(noteId) {
    return axios.post(`${API_PREFIX}/like`, {id: noteId})
}

// 取消点赞笔记
export function unlikeNote(noteId) {
    return axios.post(`${API_PREFIX}/unlike`, {id: noteId})
}

// 收藏笔记
export function collectNote(noteId) {
    return axios.post(`${API_PREFIX}/collect`, {id: noteId})
}

// 取消收藏笔记
export function uncollectNote(noteId) {
    return axios.post(`${API_PREFIX}/uncollect`, {id: noteId})
}

// 是否点赞、收藏
export function isLikedAndCollectedData(noteId) {
    return axios.post(`${API_PREFIX}/isLikedAndCollectedData`, {noteId})
}

// 获取用户主页已发布笔记列表（游标分页）
export function getPublishedNoteList(userId, cursor) {
    return axios.post(`${API_PREFIX}/published/list`, {userId, cursor})
}

// 获取个人主页赞过（2）、收藏（3）的笔记分页数据
export function getProfileNotePageList(type, userId, pageNo) {
    return axios.post(`${API_PREFIX}/profile/note/list`, {type, userId, pageNo})
}

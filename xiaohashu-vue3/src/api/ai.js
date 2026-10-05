import axios from "@/axios";

// 接口前缀（网关 /ai/** 固定路由到 AI 服务 8090）
const API_PREFIX = '/ai'

// 提问：检索全站已发布笔记 -> 判断是否对题 -> 润色回答，返回回答 + 引用笔记 + 会话 UUID
export function chatWithAi(query, chatUuid, topN) {
    return axios.post(`${API_PREFIX}/chat/send`, { query, chatUuid, topN })
}

// 检索并润色（不落会话，单次问答）
export function searchNoteByAi(query, topN) {
    return axios.post(`${API_PREFIX}/assistant/search`, { query, topN })
}

// 只返回相关笔记，不润色
export function searchRelatedNotes(query, topN) {
    return axios.post(`${API_PREFIX}/assistant/related`, { query, topN })
}

// 会话列表
export function getAiChatList() {
    return axios.get(`${API_PREFIX}/chat/list`)
}

// 会话消息
export function getAiChatMessages(chatUuid) {
    return axios.get(`${API_PREFIX}/chat/messages`, { params: { chatUuid } })
}

// 删除会话
export function deleteAiChat(chatUuid) {
    return axios.post(`${API_PREFIX}/chat/delete`, null, { params: { chatUuid } })
}

// 单篇笔记 AI 解读
export function analyzeNoteByAi(noteId, forceRefresh = false) {
    return axios.post(`${API_PREFIX}/note/analyze`, { noteId, forceRefresh })
}

// 索引统计（已索引笔记数 / 向量片段数）
export function getAiIndexStats() {
    return axios.get(`${API_PREFIX}/assistant/index/stats`)
}

// 全量重建索引（耗时较长，用于索引为空或消息丢失后的兜底）
export function rebuildAiIndex() {
    return axios.post(`${API_PREFIX}/assistant/index/rebuild`)
}
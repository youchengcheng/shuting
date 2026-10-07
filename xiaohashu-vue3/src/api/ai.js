import axios from "@/axios";

// 接口前缀（网关 /ai/** 固定路由到 AI 服务 8090）
const API_PREFIX = '/ai'

// AI 接口要跑「向量召回 + 多次大模型调用」，耗时常见十几秒到一分钟，远超全局默认的 15s，
// 这里单独放宽；重建索引要扫全站笔记，给更长时间
const AI_TIMEOUT = 180000
const AI_REBUILD_TIMEOUT = 600000

// 提问：检索全站已发布笔记 -> 判断是否对题 -> 润色回答，返回回答 + 引用笔记 + 会话 UUID
// 站内检索不到相关笔记时，后端会自动降级为 AI 直接回答（fromNotes=false）
export function chatWithAi(query, chatUuid) {
    return axios.post(`${API_PREFIX}/chat/send`, { query, chatUuid }, { timeout: AI_TIMEOUT })
}

// 检索并润色（不落会话，单次问答）
export function searchNoteByAi(query) {
    return axios.post(`${API_PREFIX}/assistant/search`, { query }, { timeout: AI_TIMEOUT })
}

// 只返回相关笔记，不润色
export function searchRelatedNotes(query) {
    return axios.post(`${API_PREFIX}/assistant/related`, { query }, { timeout: AI_TIMEOUT })
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
    return axios.post(`${API_PREFIX}/note/analyze`, { noteId, forceRefresh }, { timeout: AI_TIMEOUT })
}

// 索引统计（已索引笔记数 / 向量片段数）
export function getAiIndexStats() {
    return axios.get(`${API_PREFIX}/assistant/index/stats`)
}

// 全量重建索引（耗时较长，用于索引为空或消息丢失后的兜底）
export function rebuildAiIndex() {
    return axios.post(`${API_PREFIX}/assistant/index/rebuild`, null, { timeout: AI_REBUILD_TIMEOUT })
}

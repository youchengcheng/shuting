import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * AI 助手的全局状态
 * 用于在 AppHeader（操作按钮）和 AiAssistant（页面）之间共享状态和触发动作。
 * - conversationsOpen：历史会话侧栏是否展开
 * - rebuilding：是否正在重建索引（按钮文案切换）
 * - rebuildTrigger / newChatTrigger：计数器，AppHeader 点击按钮时自增，
 *   AiAssistant 通过 watch 监听变化并执行对应逻辑
 */
export const useAiStore = defineStore('ai', () => {
  const conversationsOpen = ref(false)
  const rebuilding = ref(false)

  const rebuildTrigger = ref(0)
  const newChatTrigger = ref(0)

  const triggerRebuild = () => {
    rebuildTrigger.value += 1
  }

  const triggerNewChat = () => {
    newChatTrigger.value += 1
  }

  const toggleConversations = () => {
    conversationsOpen.value = !conversationsOpen.value
  }

  return {
    conversationsOpen,
    rebuilding,
    rebuildTrigger,
    newChatTrigger,
    triggerRebuild,
    triggerNewChat,
    toggleConversations
  }
})

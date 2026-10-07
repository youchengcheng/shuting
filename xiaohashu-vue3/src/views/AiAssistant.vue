<template>
  <div class="ai-page">

    <!-- 未登录：AI 接口需要登录态 -->
    <div v-if="!isLoggedIn" class="st-surface">
      <EmptyState title="登录后使用 AI 助手" description="AI 助手会基于全站已发布笔记回答你的问题">
        <button type="button" class="st-btn st-btn-primary" @click="handleLogin">去登录</button>
      </EmptyState>
    </div>

    <div
      v-else
      class="ai-layout"
      :class="{
        'ai-layout--with-side': conversationsOpen,
        'ai-layout--with-sources': sourcesPanelOpen
      }"
    >
      <!-- 历史会话 -->
      <aside v-if="conversationsOpen" class="ai-conversations st-surface">
        <div class="ai-conversations__head">
          <span>历史对话</span>
          <button type="button" class="ai-conversations__refresh" :disabled="chatLoading" @click="loadChats">
            刷新
          </button>
        </div>

        <p v-if="chatLoading" class="ai-conversations__tip">加载中…</p>
        <p v-else-if="!chats.length" class="ai-conversations__tip">还没有历史对话</p>

        <ul v-else class="ai-conversations__list">
          <li v-for="chat in chats" :key="chat.chatUuid">
            <div class="ai-conversation" :class="{ 'ai-conversation--active': chat.chatUuid === chatUuid }">
              <button type="button" class="ai-conversation__main" @click="openChat(chat.chatUuid)">
                <span class="ai-conversation__title">{{ chat.title || '未命名对话' }}</span>
                <span class="ai-conversation__time">{{ formatChatTime(chat.updateTime) }}</span>
              </button>
              <button
                type="button"
                class="ai-conversation__delete"
                aria-label="删除对话"
                @click="askDeleteChat(chat)"
              >
                <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path
                    d="M6 7h12M10 7V5.5h4V7M9 7l.7 11.5h4.6L15 7"
                    stroke="currentColor"
                    stroke-width="1.6"
                    stroke-linecap="round"
                    stroke-linejoin="round"
                  />
                </svg>
              </button>
            </div>
          </li>
        </ul>
      </aside>

      <!-- 对话区 -->
      <section class="ai-chat st-surface">
        <div ref="scrollRef" class="ai-messages">
          <!-- 空态：给几个能直接点的提问示例 -->
          <div v-if="!messages.length" class="ai-welcome">
            <div class="ai-welcome__mark" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path
                  d="M20.5 11.4c0 3.9-3.8 7.1-8.5 7.1-.9 0-1.8-.1-2.6-.3l-4.4 2 1.2-3.6c-1.3-1.3-2.2-3.1-2.2-5.2C4 7.5 7.8 4.3 12.5 4.3s8 3.2 8 7.1Z"
                  stroke="currentColor"
                  stroke-width="1.6"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path d="M12.5 8.2l.9 1.9 2 .3-1.5 1.4.4 2-1.8-1-1.8 1 .4-2-1.5-1.4 2-.3.9-1.9Z" fill="currentColor" />
              </svg>
            </div>
            <p class="ai-welcome__title">想找什么？直接问我就好</p>
            <p class="ai-welcome__desc">我会先去全站已发布笔记里检索，判断哪些真的对题，再把内容整理成答案给你。</p>

            <div class="ai-welcome__examples">
              <button
                v-for="example in examples"
                :key="example"
                type="button"
                class="ai-example"
                @click="useExample(example)"
              >
                {{ example }}
              </button>
            </div>
          </div>

          <div
            v-for="(msg, index) in messages"
            :key="`${msg.role}-${index}`"
            class="ai-msg"
            :class="`ai-msg--${msg.role}`"
          >
            <div class="ai-msg__avatar" aria-hidden="true">
              <img v-if="msg.role === 'user'" :src="avatarUrl" alt="" />
              <svg v-else viewBox="0 0 24 24" fill="none">
                <path
                  d="M20.5 11.4c0 3.9-3.8 7.1-8.5 7.1-.9 0-1.8-.1-2.6-.3l-4.4 2 1.2-3.6c-1.3-1.3-2.2-3.1-2.2-5.2C4 7.5 7.8 4.3 12.5 4.3s8 3.2 8 7.1Z"
                  stroke="currentColor"
                  stroke-width="1.6"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
                <path d="M12.5 8.2l.9 1.9 2 .3-1.5 1.4.4 2-1.8-1-1.8 1 .4-2-1.5-1.4 2-.3.9-1.9Z" fill="currentColor" />
              </svg>
            </div>

            <div class="ai-msg__body">
              <p v-if="msg.role === 'assistant' && msg.fromNotes === false" class="ai-msg__notice">
                站内暂时没有检索到相关笔记，以下为 AI 直接回答
              </p>

              <!-- 助手回答是 Markdown，渲染前已在 utils/aiMarkdown.js 里整体转义 -->
              <div
                v-if="msg.role === 'assistant'"
                class="ai-msg__content ai-rich"
                :class="{ 'ai-msg__content--error': msg.failed }"
                v-html="renderAiMarkdown(msg.content)"
              ></div>
              <p v-else class="ai-msg__content">{{ msg.content }}</p>

              <!-- 当前会话新产生的回答：带标题、话题、匹配度与判优理由 -->
              <AiSourceList v-if="msg.notes?.length" :notes="msg.notes" @open="openNoteDetail" @show-sources="openSourcesPanel($event, 'full')" />

              <!-- 历史会话里只存了笔记 ID，退化成可点击的胶囊；超过 2 篇时显示摘要 + 抽屉 -->
              <div v-else-if="msg.noteRefs?.length" class="ai-msg__refs">
                <template v-if="msg.noteRefs.length <= 2">
                  <span class="ai-msg__refs-label">引用笔记</span>
                  <button
                    v-for="noteId in msg.noteRefs"
                    :key="noteId"
                    type="button"
                    class="ai-ref-chip"
                    @click="openNoteDetail({ noteId })"
                  >
                    笔记 {{ noteId }}
                  </button>
                </template>
                <button v-else type="button" class="ai-sources__summary" @click="openSourcesPanel(msg.noteRefs, 'ids')">
                  <svg class="ai-sources__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M6 4.5h9.5L19 8v11.5H6V4.5Z" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round" />
                    <path d="M9 12h7M9 15.5h5" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" />
                  </svg>
                  AI 总结 {{ msg.noteRefs.length }} 篇笔记生成
                  <svg class="ai-sources__arrow" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                    <path d="M9 6l6 6-6 6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                  </svg>
                </button>
              </div>

              <button
                v-if="msg.role === 'assistant' && msg.content && !msg.failed"
                type="button"
                class="ai-msg__copy"
                @click="copyAnswer(msg.content)"
              >
                复制回答
              </button>
            </div>
          </div>

          <!-- 生成中：展示当前阶段，避免长时间空白 -->
          <div v-if="pending" class="ai-msg ai-msg--assistant">
            <div class="ai-msg__avatar" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none">
                <path
                  d="M20.5 11.4c0 3.9-3.8 7.1-8.5 7.1-.9 0-1.8-.1-2.6-.3l-4.4 2 1.2-3.6c-1.3-1.3-2.2-3.1-2.2-5.2C4 7.5 7.8 4.3 12.5 4.3s8 3.2 8 7.1Z"
                  stroke="currentColor"
                  stroke-width="1.6"
                  stroke-linecap="round"
                  stroke-linejoin="round"
                />
              </svg>
            </div>
            <div class="ai-msg__body">
              <p class="ai-msg__stage">
                <span class="ai-msg__dots" aria-hidden="true"><i></i><i></i><i></i></span>
                {{ pendingStage }}
              </p>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="ai-composer">
          <textarea
            v-model="draft"
            class="ai-composer__input"
            rows="2"
            maxlength="500"
            placeholder="例如：帮我找几篇讲 Java 线程池的笔记，最好是带示例的"
            :disabled="pending"
            @keydown.enter="handleEnter"
          ></textarea>

          <div class="ai-composer__foot">
            <p class="ai-composer__hint">检索全站已发布笔记并引用原文润色；站内没有相关笔记时由 AI 直接回答</p>

            <button type="button" class="st-btn st-btn-primary" :disabled="!canSend" @click="handleSend">
              {{ pending ? '生成中…' : '发送' }}
            </button>
          </div>
        </div>
      </section>

      <!-- 右侧来源笔记面板：内联展示，非浮窗 -->
      <aside v-if="sourcesPanelOpen" class="ai-sources-panel st-surface">
        <header class="ai-sources-panel__head">
          <span>{{ sourcesPanelTitle }}</span>
          <button type="button" class="ai-sources-panel__close" aria-label="关闭" @click="sourcesPanelOpen = false">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
            </svg>
          </button>
        </header>

        <ul class="ai-sources-panel__list">
          <!-- 完整笔记信息（当前会话新产生的回答） -->
          <template v-if="sourcesPanelMode === 'full'">
            <li v-for="(note, index) in sourcesPanelNotes" :key="note.noteId || index">
              <button type="button" class="ai-source" @click="openNoteDetail(note)">
                <span class="ai-source__index st-num" aria-hidden="true">{{ index + 1 }}</span>
                <span class="ai-source__body">
                  <span class="ai-source__title">{{ note.title || `笔记 ${note.noteId}` }}</span>
                  <span class="ai-source__meta">
                    <span v-if="note.topicName" class="ai-source__topic"># {{ note.topicName }}</span>
                    <span v-if="note.judgeScore != null" class="ai-source__score">匹配度 {{ note.judgeScore }}%</span>
                    <span v-else-if="note.score != null" class="ai-source__score">相似度 {{ Math.round(note.score * 100) }}%</span>
                  </span>
                  <span v-if="note.reason" class="ai-source__reason">{{ note.reason }}</span>
                </span>
                <svg class="ai-source__arrow" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M9 6l6 6-6 6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </button>
            </li>
          </template>

          <!-- 仅笔记 ID（历史会话） -->
          <template v-else>
            <li v-for="(noteId, index) in sourcesPanelIds" :key="noteId">
              <button type="button" class="ai-source" @click="openNoteDetail({ noteId })">
                <span class="ai-source__index st-num" aria-hidden="true">{{ index + 1 }}</span>
                <span class="ai-source__body">
                  <span class="ai-source__title">笔记 {{ noteId }}</span>
                  <span class="ai-source__meta">
                    <span class="ai-source__topic">点击查看笔记详情</span>
                  </span>
                </span>
                <svg class="ai-source__arrow" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                  <path d="M9 6l6 6-6 6" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" />
                </svg>
              </button>
            </li>
          </template>
        </ul>
      </aside>
    </div>

    <!-- 笔记详情浮层：作为本页子路由渲染，关闭后仍停留在 AI 助手页 -->
    <router-view />

    <ConfirmDialog
      v-model:visible="confirmRebuild"
      title="重建笔记索引"
      message="将重新扫描全站已发布笔记并刷新向量索引，笔记较多时耗时较长，确认继续？"
      confirm-text="开始重建"
      :loading="rebuilding"
      @confirm="handleRebuild"
    />

    <ConfirmDialog
      v-model:visible="confirmDeleteChat"
      title="删除该对话"
      message="删除后该对话及其消息记录将无法恢复，确认删除？"
      confirm-text="删除"
      danger
      @confirm="handleDeleteChat"
    />
  </div>
</template>

<script setup>
defineOptions({ name: 'AiAssistant' })
import { computed, inject, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useAiStore } from '@/stores/ai'
import { storeToRefs } from 'pinia'
import { useNoteTransition } from '@/composables/noteTransition'
import { renderAiMarkdown } from '@/utils/aiMarkdown'
import { message } from '@/utils/message'
import { chatWithAi, deleteAiChat, getAiChatList, getAiChatMessages, getAiIndexStats, rebuildAiIndex } from '@/api/ai'
import AiSourceList from '@/components/ai/AiSourceList.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import defaultAvatar from '@/assets/avatar.png'

const STAGES = ['正在检索全站笔记…', '正在判断哪些笔记真正对题…', '正在整理回答…']
const STAGE_INTERVAL = 1300
const TYPE_INTERVAL = 16
const TYPE_FRAMES = 140

const route = useRoute()
const userStore = useUserStore()
const aiStore = useAiStore()
const { conversationsOpen, rebuilding } = storeToRefs(aiStore)
const { openNote } = useNoteTransition()
const showLoginModal = inject('showLoginModal')

const isLoggedIn = computed(() => !!userStore.token)
const profile = computed(() => userStore.profile || {})
const avatarUrl = computed(() => profile.value.avatar || defaultAvatar)

const examples = [
  '有没有讲 Java 线程池的笔记？',
  '推荐几篇适合新手的露营装备清单',
  '想了解 Redis 缓存穿透怎么解决',
  '找几篇讲时间管理的笔记，要有可执行的方法'
]

const chats = ref([])
const chatLoading = ref(false)
const chatUuid = ref('')

const messages = ref([])
const draft = ref('')
const pending = ref(false)
const pendingStage = ref(STAGES[0])

const indexStats = ref({ indexedNotes: 0, vectors: 0 })
const confirmRebuild = ref(false)
const confirmDeleteChat = ref(false)
const chatToDelete = ref(null)

// 右侧来源笔记面板（内联展示）
const sourcesPanelOpen = ref(false)
const sourcesPanelMode = ref('full') // 'full' = 完整笔记信息；'ids' = 仅笔记 ID
const sourcesPanelNotes = ref([])
const sourcesPanelIds = ref([])
const sourcesPanelCount = computed(() =>
  sourcesPanelMode.value === 'full' ? sourcesPanelNotes.value.length : sourcesPanelIds.value.length
)
const sourcesPanelTitle = computed(() => `来源笔记 · ${sourcesPanelCount.value} 篇`)

const scrollRef = ref(null)

let stageTimer = null
let revealTimer = null

const canSend = computed(() => !pending.value && !!draft.value.trim())

const scrollToBottom = () => {
  nextTick(() => {
    const el = scrollRef.value
    if (el) el.scrollTop = el.scrollHeight
  })
}

watch(() => messages.value.length, scrollToBottom)

watch(() => aiStore.rebuildTrigger, (n) => {
  if (n > 0) confirmRebuild.value = true
})

watch(() => aiStore.newChatTrigger, (n) => {
  if (n > 0) startNewChat()
})

watch(conversationsOpen, (open) => {
  if (open && isLoggedIn.value) loadChats()
})

/* ------------------------------- 索引状态 ------------------------------- */

const loadIndexStats = () => {
  getAiIndexStats()
    .then((res) => {
      if (res?.success) {
        indexStats.value = {
          indexedNotes: res.data?.indexedNotes ?? 0,
          vectors: res.data?.vectors ?? 0
        }
      }
    })
    .catch(() => {})
}

const handleRebuild = () => {
  if (rebuilding.value) return
  rebuilding.value = true
  rebuildAiIndex()
    .then((res) => {
      if (res?.success) {
        const data = res.data || {}
        message.show(`重建完成：重建 ${data.indexed ?? 0} 篇，跳过 ${data.skipped ?? 0} 篇`)
        loadIndexStats()
      } else {
        message.show(res?.message || '重建失败')
      }
    })
    .catch(() => {})
    .finally(() => {
      rebuilding.value = false
      confirmRebuild.value = false
    })
}

/* ------------------------------- 历史会话 ------------------------------- */

const loadChats = () => {
  chatLoading.value = true
  getAiChatList()
    .then((res) => {
      if (res?.success) chats.value = res.data || []
    })
    .catch(() => {})
    .finally(() => {
      chatLoading.value = false
    })
}

const openChat = (uuid) => {
  if (pending.value || uuid === chatUuid.value) return
  getAiChatMessages(uuid)
    .then((res) => {
      if (!res?.success) {
        message.show(res?.message || '加载对话失败')
        return
      }
      chatUuid.value = uuid
      messages.value = (res.data || []).map((item) => ({
        role: item.role,
        content: item.content,
        noteRefs: item.noteRefs || [],
        // 历史消息没存 fromNotes，用「助手回答且无引用笔记」推断为直答兜底
        fromNotes:
          item.role === 'assistant' && !(item.noteRefs || []).length && !String(item.content || '').startsWith('[出错了]')
            ? false
            : undefined
      }))
      scrollToBottom()
    })
    .catch(() => {})
}

const startNewChat = () => {
  chatUuid.value = ''
  messages.value = []
  draft.value = ''
}

const askDeleteChat = (chat) => {
  chatToDelete.value = chat
  confirmDeleteChat.value = true
}

const handleDeleteChat = () => {
  const target = chatToDelete.value
  if (!target) return
  deleteAiChat(target.chatUuid)
    .then((res) => {
      if (!res?.success) {
        message.show(res?.message || '删除失败')
        return
      }
      chats.value = chats.value.filter((item) => item.chatUuid !== target.chatUuid)
      if (chatUuid.value === target.chatUuid) startNewChat()
    })
    .catch(() => {})
    .finally(() => {
      confirmDeleteChat.value = false
      chatToDelete.value = null
    })
}

const formatChatTime = (value) => {
  if (!value) return ''
  let date
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value
    date = new Date(year, (month || 1) - 1, day || 1, hour, minute, second)
  } else {
    date = new Date(value)
  }
  if (Number.isNaN(date.getTime())) return ''
  const pad = (num) => String(num).padStart(2, '0')
  const sameDay = date.toDateString() === new Date().toDateString()
  return sameDay
    ? `${pad(date.getHours())}:${pad(date.getMinutes())}`
    : `${date.getMonth() + 1}月${date.getDate()}日`
}

/* -------------------------------- 提问 -------------------------------- */

const useExample = (text) => {
  draft.value = text
  handleSend()
}

const handleEnter = (event) => {
  if (event.isComposing || event.keyCode === 229) return
  if (event.shiftKey) return
  event.preventDefault()
  handleSend()
}

const startStages = () => {
  let index = 0
  pendingStage.value = STAGES[0]
  stageTimer = window.setInterval(() => {
    index = Math.min(index + 1, STAGES.length - 1)
    pendingStage.value = STAGES[index]
  }, STAGE_INTERVAL)
}

const stopStages = () => {
  if (stageTimer) {
    window.clearInterval(stageTimer)
    stageTimer = null
  }
}

// 逐字显示：长回答按长度分帧，整体耗时控制在 2s 出头，不做逐字符硬打字
const reveal = (target, full) =>
  new Promise((resolve) => {
    const text = full || ''
    const reduced =
      typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
    if (!text || reduced) {
      target.content = text
      resolve()
      return
    }
    const step = Math.max(1, Math.ceil(text.length / TYPE_FRAMES))
    let cursor = 0
    target.content = ''
    revealTimer = window.setInterval(() => {
      cursor = Math.min(text.length, cursor + step)
      target.content = text.slice(0, cursor)
      scrollToBottom()
      if (cursor >= text.length) {
        window.clearInterval(revealTimer)
        revealTimer = null
        resolve()
      }
    }, TYPE_INTERVAL)
  })

const handleSend = async () => {
  if (pending.value) return
  const query = draft.value.trim()
  if (!query) return

  draft.value = ''
  messages.value.push({ role: 'user', content: query })
  pending.value = true
  startStages()
  scrollToBottom()

  try {
    const res = await chatWithAi(query, chatUuid.value || undefined)
    if (!res?.success) {
      messages.value.push({ role: 'assistant', content: res?.message || 'AI 暂时不可用', failed: true })
      message.show(res?.message || 'AI 暂时不可用')
      return
    }

    const data = res.data || {}
    if (data.chatUuid) chatUuid.value = data.chatUuid

    const answer = String(data.answer || '')
    const failed = answer.startsWith('[出错了]')
    messages.value.push({
      role: 'assistant',
      content: '',
      notes: data.notes || [],
      fromNotes: data.fromNotes,
      failed
    })

    // 注意：必须拿 push 之后从数组里读出来的响应式代理，直接改原始对象不会触发重渲染，
    // 逐字显示会「静默」地跑完却看不到过程
    const target = messages.value[messages.value.length - 1]
    await reveal(target, answer)
    scrollToBottom()

    if (conversationsOpen.value) loadChats()
  } catch (error) {
    // 超时可能是「模型还在思考」而不是真的失败：后端会把这条回答落库，稍后可在历史对话里看到
    const timedOut = error?.code === 'ECONNABORTED' || error?.message?.includes('timeout')
    const msg = timedOut
      ? 'AI 思考时间过长已中断，稍后可在历史对话里查看结果，或把问题问得更具体些'
      : error?.response?.data?.message || '请求失败，请稍后重试'
    messages.value.push({ role: 'assistant', content: msg, failed: true })
  } finally {
    stopStages()
    pending.value = false
  }
}

/* -------------------------------- 其他 -------------------------------- */

const openNoteDetail = (note) => {
  const noteId = note?.noteId
  if (!noteId) return
  // 用统一的打开方式：笔记卡片没有封面时不会做飞行动画，直接淡入浮层
  openNote({ id: noteId }, { path: `${route.path}/note/${noteId}` })
}

// 打开右侧来源笔记面板
// data: 笔记数组（当前会话）或笔记 ID 数组（历史会话）
// mode: 'full' | 'ids'
const openSourcesPanel = (data, mode) => {
  sourcesPanelMode.value = mode
  if (mode === 'full') {
    sourcesPanelNotes.value = Array.isArray(data) ? data : []
    sourcesPanelIds.value = []
  } else {
    sourcesPanelIds.value = Array.isArray(data) ? data : []
    sourcesPanelNotes.value = []
  }
  sourcesPanelOpen.value = true
}

const copyAnswer = async (text) => {
  try {
    await navigator.clipboard.writeText(text)
    message.show('已复制')
  } catch (e) {
    message.show('复制失败，请手动选择文本')
  }
}

const handleLogin = () => {
  if (showLoginModal) showLoginModal.value = true
}

watch(isLoggedIn, (loggedIn) => {
  if (loggedIn) {
    loadIndexStats()
    if (conversationsOpen.value) loadChats()
  }
})

if (isLoggedIn.value) {
  loadIndexStats()
}

onBeforeUnmount(() => {
  stopStages()
  if (revealTimer) {
    window.clearInterval(revealTimer)
    revealTimer = null
  }
})
</script>
<style scoped>
.ai-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
  height: calc(100vh - var(--header-h) - 20px);
}

/* ------------------------------- 布局 ------------------------------- */

.ai-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  align-items: stretch;
  flex: 1;
  min-height: 0;
}

.ai-layout--with-side {
  grid-template-columns: 260px minmax(0, 1fr);
}

/* 右侧来源笔记面板（无历史会话侧栏） */
.ai-layout--with-sources {
  grid-template-columns: minmax(0, 1fr) 320px;
}

/* 同时有历史会话侧栏 + 来源笔记面板 */
.ai-layout--with-side.ai-layout--with-sources {
  grid-template-columns: 260px minmax(0, 1fr) 320px;
}

/* ----------------------------- 历史会话 ----------------------------- */

.ai-conversations {
  display: flex;
  flex-direction: column;
  padding: 14px 12px;
  height: 100%;
  overflow: hidden;
}

.ai-conversations__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 4px 10px;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-ink);
}

.ai-conversations__refresh {
  border: none;
  background: transparent;
  color: var(--color-ink-faint);
  font-size: 12px;
  cursor: pointer;
}

.ai-conversations__refresh:hover:not(:disabled) {
  color: var(--color-ink);
}

.ai-conversations__tip {
  margin: 12px 4px;
  font-size: 12px;
  color: var(--color-ink-faint);
}

.ai-conversations__list {
  display: flex;
  flex-direction: column;
  gap: 2px;
  margin: 0;
  padding: 0;
  list-style: none;
  overflow-y: auto;
}

.ai-conversation {
  display: flex;
  align-items: center;
  gap: 2px;
  border-radius: var(--radius-control);
  transition: background-color var(--motion-fast) var(--ease-standard);
}

.ai-conversation:hover {
  background: var(--color-canvas-sunken);
}

.ai-conversation--active {
  background: var(--color-canvas-deep);
}

.ai-conversation__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 8px 10px;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.ai-conversation__title {
  font-size: 13px;
  color: var(--color-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-conversation__time {
  font-size: 11px;
  color: var(--color-ink-faint);
}

.ai-conversation__delete {
  flex-shrink: 0;
  display: none;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  margin-right: 6px;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-ink-faint);
  cursor: pointer;
}

.ai-conversation:hover .ai-conversation__delete {
  display: inline-flex;
}

.ai-conversation__delete:hover {
  color: var(--color-brand);
}

.ai-conversation__delete svg {
  width: 15px;
  height: 15px;
}

/* ------------------------------ 对话区 ------------------------------ */

.ai-chat {
  display: flex;
  flex-direction: column;
  padding: 14px 16px 16px;
  min-width: 0;
  width: 100%;
  max-width: 820px;
  height: 100%;
  margin: 0 auto;
  overflow: hidden;
  border: none;
}

.ai-messages {
  display: flex;
  flex-direction: column;
  gap: 18px;
  flex: 1;
  min-height: 120px;
  padding: 16px 5px 8px;
  overflow-y: auto;
}

/* ------------------------------- 空态 ------------------------------- */

.ai-welcome {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  height: 100%;
  padding: 12px;
  text-align: center;
}

.ai-welcome__mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  margin-bottom: 4px;
  border-radius: var(--radius-pill);
  background: var(--color-brand-tint);
  color: var(--color-brand);
}

.ai-welcome__mark svg {
  width: 26px;
  height: 26px;
}

.ai-welcome__title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-ink);
}

.ai-welcome__desc {
  max-width: 420px;
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-ink-faint);
}

.ai-welcome__examples {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-top: 12px;
  max-width: 560px;
}

.ai-example {
  padding: 8px 14px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-pill);
  background: var(--color-paper);
  color: var(--color-ink-soft);
  font-size: 13px;
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    border-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}

.ai-example:hover {
  border-color: var(--color-line-strong);
  background: var(--color-canvas-sunken);
  color: var(--color-ink);
}

/* ------------------------------ 消息 ------------------------------ */

.ai-msg {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.ai-msg--user {
  flex-direction: row-reverse;
}

.ai-msg__avatar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  overflow: hidden;
  border-radius: var(--radius-pill);
  background: var(--color-canvas-sunken);
  color: var(--color-ink-soft);
}

.ai-msg__avatar svg {
  width: 18px;
  height: 18px;
}

.ai-msg__avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.ai-msg--assistant .ai-msg__avatar {
  background: var(--color-brand-tint);
  color: var(--color-brand);
}

.ai-msg__body {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  min-width: 0;
  max-width: min(720px, 100%);
}

.ai-msg--user .ai-msg__body {
  align-items: flex-end;
  max-width: min(560px, 78%);
}

.ai-msg--assistant .ai-msg__body {
  max-width: min(720px, 100%);
}

.ai-msg__content {
  margin: 0;
  padding: 11px 15px;
  border-radius: var(--radius-card);
  font-size: 14px;
  line-height: 1.75;
  word-break: break-word;
}

.ai-msg--assistant .ai-msg__content {
  background: transparent;
  color: var(--color-ink);
  padding: 11px 4px;
}

.ai-msg--user .ai-msg__content {
  background: var(--color-canvas-sunken);
  color: var(--color-ink);
  white-space: pre-wrap;
}

.ai-msg__content--error {
  background: var(--color-brand-tint);
  color: var(--color-brand);
}

.ai-msg__stage {
  display: inline-flex;
  align-items: center;
  margin: 0;
  padding: 11px 15px;
  border-radius: var(--radius-card);
  background: var(--color-canvas-sunken);
  color: var(--color-ink-soft);
  font-size: 13px;
}

.ai-msg__dots {
  display: inline-flex;
  gap: 3px;
  margin-right: 8px;
}

.ai-msg__dots i {
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: var(--color-brand);
  animation: ai-dot 1.1s ease-in-out infinite;
}

.ai-msg__dots i:nth-child(2) {
  animation-delay: 0.15s;
}

.ai-msg__dots i:nth-child(3) {
  animation-delay: 0.3s;
}

@keyframes ai-dot {
  0%,
  80%,
  100% {
    opacity: 0.25;
    transform: translateY(0);
  }
  40% {
    opacity: 1;
    transform: translateY(-3px);
  }
}

.ai-msg__refs {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed var(--color-line-strong);
}

.ai-msg__refs-label {
  font-size: 12px;
  color: var(--color-ink-faint);
}

.ai-ref-chip {
  padding: 5px 12px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-pill);
  background: var(--color-canvas-sunken);
  color: var(--color-ink-soft);
  font-size: 12px;
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}

.ai-ref-chip:hover {
  background: var(--color-canvas-deep);
  color: var(--color-ink);
}

.ai-msg__copy {
  margin-top: 6px;
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-ink-faint);
  font-size: 12px;
  cursor: pointer;
}

.ai-msg__copy:hover {
  color: var(--color-ink);
}

/* --------------------------- 回答的富文本 --------------------------- */

.ai-rich :deep(p) {
  margin: 0 0 10px;
}

.ai-rich :deep(p:last-child) {
  margin-bottom: 0;
}

.ai-rich :deep(h3),
.ai-rich :deep(h4),
.ai-rich :deep(h5),
.ai-rich :deep(h6) {
  margin: 14px 0 8px;
  font-size: 14px;
  font-weight: 600;
  line-height: 1.5;
}

.ai-rich :deep(h3:first-child),
.ai-rich :deep(h4:first-child),
.ai-rich :deep(h5:first-child),
.ai-rich :deep(h6:first-child) {
  margin-top: 0;
}

.ai-rich :deep(ul),
.ai-rich :deep(ol) {
  margin: 0 0 10px;
  padding-left: 20px;
}

.ai-rich :deep(li) {
  margin: 3px 0;
}

.ai-rich :deep(strong) {
  font-weight: 600;
}

.ai-rich :deep(a) {
  color: var(--color-brand);
  text-decoration: underline;
  text-underline-offset: 2px;
}

.ai-rich :deep(code) {
  padding: 1px 5px;
  border-radius: 5px;
  background: rgb(0 0 0 / 0.06);
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12.5px;
}

.ai-rich :deep(pre) {
  margin: 0 0 10px;
  padding: 12px 14px;
  border-radius: var(--radius-control);
  background: #2b2b2b;
  overflow-x: auto;
}

.ai-rich :deep(pre code) {
  padding: 0;
  background: transparent;
  color: #f5f5f5;
  font-size: 12.5px;
  line-height: 1.65;
}

.ai-rich :deep(blockquote) {
  margin: 0 0 10px;
  padding: 2px 0 2px 12px;
  border-left: 3px solid var(--color-line-strong);
  color: var(--color-ink-soft);
}

.ai-rich :deep(hr) {
  margin: 14px 0;
  border: none;
  border-top: 1px solid var(--color-line);
}

.ai-rich :deep(> *:last-child) {
  margin-bottom: 0;
}

/* ------------------------------ 输入区 ------------------------------ */

.ai-composer {
  margin-top: 12px;
  padding: 12px 14px 10px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-panel);
  background: var(--color-paper);
  transition: border-color var(--motion-fast) var(--ease-standard);
}

.ai-composer:focus-within {
  border-color: var(--color-line-strong);
}

.ai-composer__input {
  width: 100%;
  border: none;
  outline: none;
  resize: none;
  background: transparent;
  color: var(--color-ink);
  font-size: 14px;
  line-height: 1.7;
}

.ai-composer__input::placeholder {
  color: var(--color-ink-faint);
}

.ai-composer__input:disabled {
  color: var(--color-ink-faint);
}

.ai-composer__foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 8px;
}

.ai-composer__hint {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-ink-faint);
}

.ai-msg__notice {
  margin: 0 0 8px;
  padding: 6px 10px;
  border-radius: var(--radius-control);
  background: var(--color-canvas-sunken);
  color: var(--color-ink-soft);
  font-size: 12px;
  line-height: 1.6;
}

/* ------------------------------ 响应式 ------------------------------ */

@media (max-width: 1023px) {
  .ai-page {
    height: calc(100vh - var(--header-h) - 16px);
  }
}

@media (max-width: 1100px) {
  .ai-layout--with-side,
  .ai-layout--with-sources,
  .ai-layout--with-side.ai-layout--with-sources {
    grid-template-columns: minmax(0, 1fr);
  }

  .ai-conversations {
    height: 220px;
  }

  .ai-sources-panel {
    height: 320px;
  }

  .ai-msg__body,
  .ai-msg--assistant .ai-msg__body {
    max-width: 88%;
    width: auto;
  }
}

@media (max-width: 767px) {
  .ai-page {
    height: calc(100vh - var(--header-h) - 12px);
  }

  .ai-msg__body,
  .ai-msg--assistant .ai-msg__body {
    max-width: 92%;
  }
}

/* ----------------------- 摘要按钮 & 抽屉（复用 AiSourceList 样式） ----------------------- */

.ai-sources__summary {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 14px 6px 10px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-pill);
  background: var(--color-canvas-sunken);
  color: var(--color-ink-soft);
  font-size: 13px;
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    border-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}

.ai-sources__summary:hover {
  border-color: var(--color-line-strong);
  background: var(--color-canvas-deep);
  color: var(--color-ink);
}

.ai-sources__summary .ai-sources__icon {
  width: 15px;
  height: 15px;
  color: var(--color-brand);
}

.ai-sources__summary .ai-sources__arrow {
  width: 14px;
  height: 14px;
  color: var(--color-ink-faint);
}

/* ------------------------------ 右侧来源笔记面板 ------------------------------ */

.ai-sources-panel {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-width: 0;
  overflow: hidden;
}

.ai-sources-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-shrink: 0;
  padding: 14px 16px;
  border-bottom: 1px solid var(--color-line);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
}

.ai-sources-panel__close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-ink-faint);
  cursor: pointer;
  transition: background-color var(--motion-fast) var(--ease-standard);
}

.ai-sources-panel__close:hover {
  background: var(--color-canvas-sunken);
  color: var(--color-ink);
}

.ai-sources-panel__close svg {
  width: 18px;
  height: 18px;
}

.ai-sources-panel__list {
  display: flex;
  flex-direction: column;
  gap: 8px;
  flex: 1;
  margin: 0;
  padding: 14px 16px 20px;
  list-style: none;
  overflow-y: auto;
}

/* 右侧面板内 .ai-source 系列样式（AiSourceList.vue 的 scoped 样式不会泄漏到本面板） */
.ai-source {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-control);
  background: var(--color-canvas-sunken);
  text-align: left;
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    border-color var(--motion-fast) var(--ease-standard);
}

.ai-source:hover {
  border-color: var(--color-line-strong);
  background: var(--color-canvas-deep);
}

.ai-source__index {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  margin-top: 1px;
  border-radius: var(--radius-pill);
  background: var(--color-paper);
  color: var(--color-ink-soft);
  font-size: 12px;
  line-height: 20px;
  text-align: center;
}

.ai-source__body {
  display: flex;
  flex-direction: column;
  gap: 3px;
  min-width: 0;
  flex: 1;
}

.ai-source__title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.ai-source__meta {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 12px;
  color: var(--color-ink-faint);
}

.ai-source__topic {
  color: var(--color-ink-soft);
}

.ai-source__score {
  color: var(--color-brand);
}

.ai-source__reason {
  font-size: 12px;
  line-height: 1.6;
  color: var(--color-ink-faint);
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

/* 箭头尺寸受控 */
.ai-source__arrow {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
  margin-top: 2px;
  color: var(--color-ink-faint);
}</style>

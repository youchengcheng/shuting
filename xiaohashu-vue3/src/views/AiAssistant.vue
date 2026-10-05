<template>
  <div class="ai-page">
    <header class="ai-head">
      <div class="ai-head__text">
        <h2 class="st-page-title">AI 助手</h2>
        <p class="page-header__desc">
          用一句话描述你想找什么，助手会检索全站已发布笔记、判断哪些真正对题，再把笔记内容整理后回答你。
        </p>
      </div>

      <div class="ai-head__actions">
        <button type="button" class="ai-chip-btn" @click="startNewChat">新建对话</button>
        <button
          type="button"
          class="ai-chip-btn"
          :class="{ 'ai-chip-btn--active': conversationsOpen }"
          @click="conversationsOpen = !conversationsOpen"
        >
          历史对话
        </button>
      </div>
    </header>

    <!-- 未登录：AI 接口需要登录态 -->
    <div v-if="!isLoggedIn" class="st-surface">
      <EmptyState title="登录后使用 AI 助手" description="AI 助手会基于全站已发布笔记回答你的问题">
        <button type="button" class="st-btn st-btn-primary" @click="handleLogin">去登录</button>
      </EmptyState>
    </div>

    <div v-else class="ai-layout" :class="{ 'ai-layout--with-side': conversationsOpen }">
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
        <!-- 索引状态：索引为空时直接给出重建入口，避免用户以为「AI 坏了」 -->
        <div class="ai-status">
          <span class="ai-status__dot" :class="{ 'ai-status__dot--warn': indexStats.indexedNotes === 0 }"></span>
          <span class="ai-status__text">
            已索引 <b class="st-num">{{ indexStats.indexedNotes }}</b> 篇笔记 ·
            <b class="st-num">{{ indexStats.vectors }}</b> 个片段
          </span>
          <span v-if="indexStats.indexedNotes === 0" class="ai-status__warn">索引为空，先重建一次才能检索</span>
          <button type="button" class="ai-status__action" :disabled="rebuilding" @click="confirmRebuild = true">
            {{ rebuilding ? '重建中…' : '重建索引' }}
          </button>
        </div>

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
              <!-- 助手回答是 Markdown，渲染前已在 utils/aiMarkdown.js 里整体转义 -->
              <div
                v-if="msg.role === 'assistant'"
                class="ai-msg__content ai-rich"
                :class="{ 'ai-msg__content--error': msg.failed }"
                v-html="renderAiMarkdown(msg.content)"
              ></div>
              <p v-else class="ai-msg__content">{{ msg.content }}</p>

              <!-- 当前会话新产生的回答：带标题、话题、匹配度与判优理由 -->
              <AiSourceList v-if="msg.notes?.length" :notes="msg.notes" @open="openNoteDetail" />

              <!-- 历史会话里只存了笔记 ID，退化成可点击的胶囊 -->
              <div v-else-if="msg.noteRefs?.length" class="ai-msg__refs">
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
            <label class="ai-composer__topn">
              最多引用
              <select v-model.number="topN" :disabled="pending">
                <option v-for="value in [1, 2, 3, 5]" :key="value" :value="value">{{ value }}</option>
              </select>
              篇笔记
            </label>

            <button type="button" class="st-btn st-btn-primary" :disabled="!canSend" @click="handleSend">
              {{ pending ? '生成中…' : '发送' }}
            </button>
          </div>
        </div>
      </section>
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
import { computed, inject, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useNoteTransition } from '@/composables/noteTransition'
import { renderAiMarkdown } from '@/utils/aiMarkdown'
import { message } from '@/utils/message'
import { chatWithAi, deleteAiChat, getAiChatList, getAiChatMessages, getAiIndexStats, rebuildAiIndex } from '@/api/ai'
import AiSourceList from '@/components/ai/AiSourceList.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import defaultAvatar from '@/assets/avatar.png'

const STAGES = ['正在检索全站笔记…', '正在判断哪些笔记真正对题…', '正在整理润色回答…']
const STAGE_INTERVAL = 1300
const TYPE_INTERVAL = 16
const TYPE_FRAMES = 140

const route = useRoute()
const userStore = useUserStore()
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

const conversationsOpen = ref(false)
const chats = ref([])
const chatLoading = ref(false)
const chatUuid = ref('')

const messages = ref([])
const draft = ref('')
const topN = ref(3)
const pending = ref(false)
const pendingStage = ref(STAGES[0])

const indexStats = ref({ indexedNotes: 0, vectors: 0 })
const rebuilding = ref(false)
const confirmRebuild = ref(false)
const confirmDeleteChat = ref(false)
const chatToDelete = ref(null)

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
        noteRefs: item.noteRefs || []
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
    const res = await chatWithAi(query, chatUuid.value || undefined, topN.value)
    if (!res?.success) {
      messages.value.push({ role: 'assistant', content: res?.message || 'AI 暂时不可用', failed: true })
      message.show(res?.message || 'AI 暂时不可用')
      return
    }

    const data = res.data || {}
    if (data.chatUuid) chatUuid.value = data.chatUuid

    const answer = String(data.answer || '')
    const failed = answer.startsWith('[出错了]')
    messages.value.push({ role: 'assistant', content: '', notes: data.notes || [], failed })

    // 注意：必须拿 push 之后从数组里读出来的响应式代理，直接改原始对象不会触发重渲染，
    // 逐字显示会「静默」地跑完却看不到过程
    const target = messages.value[messages.value.length - 1]
    await reveal(target, answer)
    scrollToBottom()

    if (conversationsOpen.value) loadChats()
  } catch (error) {
    const msg = error?.response?.data?.message || '请求失败，请稍后重试'
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
}

.ai-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.ai-head__text {
  min-width: 0;
}

.ai-head__actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}

.ai-chip-btn {
  height: 32px;
  padding: 0 14px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-canvas-sunken);
  color: var(--color-ink-soft);
  font-size: 13px;
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}

.ai-chip-btn:hover {
  background: var(--color-canvas-deep);
  color: var(--color-ink);
}

.ai-chip-btn--active {
  background: var(--color-canvas-deep);
  color: var(--color-ink);
  font-weight: 500;
}

/* ------------------------------- 布局 ------------------------------- */

.ai-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  align-items: start;
}

.ai-layout--with-side {
  grid-template-columns: 260px minmax(0, 1fr);
}

/* ----------------------------- 历史会话 ----------------------------- */

.ai-conversations {
  display: flex;
  flex-direction: column;
  padding: 14px 12px;
  max-height: min(560px, calc(100vh - 340px));
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
  padding: 14px 18px 16px;
  min-width: 0;
}

.ai-status {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 2px 12px;
  border-bottom: 1px solid var(--color-line);
  font-size: 12px;
  color: var(--color-ink-faint);
}

.ai-status__dot {
  width: 6px;
  height: 6px;
  border-radius: var(--radius-pill);
  background: #00b96b;
  flex-shrink: 0;
}

.ai-status__dot--warn {
  background: var(--color-brand);
}

.ai-status__text b {
  color: var(--color-ink-soft);
  font-weight: 600;
}

.ai-status__warn {
  color: var(--color-brand);
}

.ai-status__action {
  margin-left: auto;
  border: none;
  background: transparent;
  color: var(--color-ink-faint);
  font-size: 12px;
  cursor: pointer;
  text-decoration: underline;
  text-underline-offset: 2px;
}

.ai-status__action:hover:not(:disabled) {
  color: var(--color-ink);
}

.ai-status__action:disabled {
  cursor: not-allowed;
  opacity: 0.6;
}

.ai-messages {
  display: flex;
  flex-direction: column;
  gap: 18px;
  height: min(560px, calc(100vh - 380px));
  min-height: 300px;
  padding: 16px 2px 8px;
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
  max-width: min(720px, 82%);
}

.ai-msg--user .ai-msg__body {
  align-items: flex-end;
  max-width: min(560px, 78%);
}

.ai-msg--assistant .ai-msg__body {
  width: min(720px, 82%);
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
  background: var(--color-canvas-sunken);
  color: var(--color-ink);
}

.ai-msg--user .ai-msg__content {
  background: var(--color-brand);
  color: #fff;
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

.ai-composer__topn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: var(--color-ink-faint);
}

.ai-composer__topn select {
  padding: 4px 8px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-canvas-sunken);
  color: var(--color-ink-soft);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
}

/* ------------------------------ 响应式 ------------------------------ */

@media (max-width: 1100px) {
  .ai-layout--with-side {
    grid-template-columns: minmax(0, 1fr);
  }

  .ai-conversations {
    max-height: 220px;
  }

  .ai-msg__body,
  .ai-msg--assistant .ai-msg__body {
    max-width: 88%;
    width: auto;
  }
}

@media (max-width: 767px) {
  .ai-head {
    flex-direction: column;
  }

  .ai-messages {
    height: min(520px, calc(100vh - 400px));
  }

  .ai-msg__body,
  .ai-msg--assistant .ai-msg__body {
    max-width: 92%;
  }
}
</style>
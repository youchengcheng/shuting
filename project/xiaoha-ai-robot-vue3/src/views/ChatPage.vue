<template>
  <Layout>
    <template #main-content>
      <div class="chat-page-root">
        <!-- 聊天记录区域（独立滚动容器） -->
        <div
          class="chat-messages-area"
          ref="chatContainer"
          :class="{ 'is-scrolling': chatIsScrolling }"
        >
          <div class="messages-content" ref="messagesContentRef">
            <template v-for="(chat, index) in chatList" :key="index">
              <!-- 用户提问消息（靠右） -->
              <div v-if="chat.role === 'user'" class="flex justify-end mb-8">
                <div class="quesiton-container">
                  <p>{{ chat.content }}</p>
                </div>
              </div>

              <!-- 大模型回复消息（靠左） -->
              <div v-else class="flex mb-10">
                <!-- 回复的内容 -->
                <div class="reply-content">
                  <LoadingDots v-if="chat.loading" />

                  <!-- 推理过程展示（带展开/收起功能） -->
                  <div v-if="chat.reasoning" class="reasoning-container">
                    <!-- 推理区标题行（可点击展开/收起）：标题动态文字；透明背景；文字箭头紧凑 3px -->
                    <div
                      class="reasoning-header"
                      @click="toggleReasoning(index)"
                    >
                      <span class="reasoning-title">{{ chat.reasoningPhase === 'active' ? '正在思考中' : '已完成思考' }}</span>
                      <!-- 展开/收起箭头（收起态朝右 >，展开态朝下） -->
                      <svg
                        class="reasoning-chevron"
                        :class="{ 'is-collapsed': collapsedReasoning[index] !== false }"
                        width="16" height="16" viewBox="0 0 24 24"
                        fill="none" stroke="currentColor" stroke-width="1.8"
                        stroke-linecap="round" stroke-linejoin="round"
                      >
                        <polyline points="6 9 12 15 18 9"></polyline>
                      </svg>
                    </div>
                    <!-- 推理内容（带过渡动画） -->
                    <div
                      class="reasoning-content-wrapper"
                      :class="{ 'is-collapsed': collapsedReasoning[index] !== false }"
                    >
                      <div class="reasoning-content">{{ chat.reasoning }}</div>
                    </div>
                  </div>

                  <!-- 分割线：推理和正文之间 -->
                  <div v-if="chat.reasoning && chat.content" class="reasoning-divider"></div>

                  <!-- 正常回复内容 -->
                  <StreamMarkdownRender :content="chat.content" />
                </div>
              </div>
            </template>
          </div>
        </div>

        <!-- 底部输入框（与消息区同级，固定在页面底部，不随滚动移动） -->
        <div class="chat-bottom-area">
          <div class="bottom-content">
            <ChatInputBox
              v-model="message"
              @sendMessage="sendMessage"
            />
          </div>
        </div>
      </div>
    </template>
  </Layout>
</template>

<script setup>
import { ref, reactive, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import SvgIcon from '@/components/SvgIcon.vue'
import StreamMarkdownRender from '@/components/StreamMarkdownRender.vue'
import LoadingDots from '@/components/LoadingDots.vue'
import Layout from '@/layouts/Layout.vue'
import ChatInputBox from '@/components/ChatInputBox.vue'
import { useRoute } from 'vue-router'
import { useChatStore } from '@/stores/chatStore'
import { fetchEventSource } from '@microsoft/fetch-event-source'
import { findChatMessagePageList } from '@/api/chat'

const chatStore = useChatStore()

console.log('首页传递过来的消息: ', history.state?.firstMessage)

const route = useRoute()

const message = ref(history.state?.firstMessage || '')

// 真正的滚动容器 = 聊天消息区域（chat-messages-area）
const chatContainer = ref(null)
// 外层 messages-content，用于观察新增的 AI 思考框节点
const messagesContentRef = ref(null)

const chatList = ref([])

// 流式回复期间仅在用户停留在底部时自动跟随；用户上滑后暂停，回到底部再恢复。
const shouldAutoScroll = ref(true)
const BOTTOM_THRESHOLD = 64

// 聊天容器滚动条显隐状态
const chatIsScrolling = ref(false)
let chatScrollTimer = null

/**
 * 给一个带 overflow-y:auto 的容器挂滚动条「滚动时显、停滚延迟隐藏」逻辑。
 * - 滚动发生：立刻加上 is-scrolling class
 * - 停滚 delay 毫秒后：移除 is-scrolling class
 * 返回解绑函数（组件卸载用）。
 */
const bindAutohideScrollbar = (el, delay = 800) => {
  if (!el || el.__autohideScrollbarBound) return () => {}
  let timer = null
  const onScroll = () => {
    // 立即显示
    el.classList.add('is-scrolling')
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      el.classList.remove('is-scrolling')
    }, delay)
  }
  el.addEventListener('scroll', onScroll, { passive: true })
  el.__autohideScrollbarBound = true
  el.__autohideScrollbarCleanup = () => {
    el.removeEventListener('scroll', onScroll)
    if (timer) clearTimeout(timer)
    delete el.__autohideScrollbarBound
    delete el.__autohideScrollbarCleanup
  }
  return el.__autohideScrollbarCleanup
}

// 已绑定的 AI 思考框清理函数集合
const reasoningCleanups = new Set()

/**
 * 扫描当前页面所有 AI 思考框容器（.reasoning-content-wrapper），
 * 对未绑定的容器挂载滚动条自动显隐逻辑。
 */
const bindReasoningScrollbars = () => {
  if (!messagesContentRef.value) return
  const wrappers = messagesContentRef.value.querySelectorAll('.reasoning-content-wrapper')
  wrappers.forEach((w) => {
    if (w.__autohideScrollbarBound) return
    const cleanup = bindAutohideScrollbar(w, 800)
    reasoningCleanups.add(cleanup)
  })
}

// 观察消息列表 DOM 变化（新出现的 reasoning-content-wrapper 需要绑定）
let reasoningObserver = null


// 推理区折叠状态：默认 true = 收起
const collapsedReasoning = reactive({})

const toggleReasoning = (index) => {
  collapsedReasoning[index] = collapsedReasoning[index] === false
}

watch(() => route.params.chatId, (newChatId) => {
  if (newChatId) {
    chatId.value = newChatId
    chatList.value = []
    current.value = 1
    // 重置推理折叠状态
    Object.keys(collapsedReasoning).forEach(k => delete collapsedReasoning[k])
    loadHistoryMessages()
  }
})

onMounted(() => {
  loadHistoryMessages()

  if (chatContainer.value) {
    chatContainer.value.addEventListener('scroll', handleScroll)
    // 聊天内容容器：滚动时显条、停滚 800ms 后隐藏
    chatContainer.value.addEventListener('scroll', () => {
      chatIsScrolling.value = true
      if (chatScrollTimer) clearTimeout(chatScrollTimer)
      chatScrollTimer = setTimeout(() => {
        chatIsScrolling.value = false
      }, 800)
    }, { passive: true })
  }

  // 为已渲染的 AI 思考框绑定滚动条自动显隐
  nextTick(bindReasoningScrollbars)

  // 监听消息列表 DOM：新出现的思考框也需要自动绑定
  if (messagesContentRef.value && typeof MutationObserver !== 'undefined') {
    reasoningObserver = new MutationObserver(() => {
      nextTick(bindReasoningScrollbars)
    })
    reasoningObserver.observe(messagesContentRef.value, { childList: true, subtree: true })
  }

  const firstMessage = history.state?.firstMessage
  if (firstMessage) {
    message.value = firstMessage
    sendMessage({
      selectedModel: chatStore.selectedModel,
      isNetworkSearch: chatStore.isNetworkSearchSelected,
      isDeepThinking: chatStore.isDeepThinkingSelected
    })
    if (history.replaceState) {
      const newState = { ...history.state }
      delete newState.firstMessage
      history.replaceState(newState, document.title)
    }
  }

  scrollToBottom()
})

const current = ref(1)
const size = ref(3)
const hasMore = ref(true)
const isLoadingMore = ref(false)

const loadHistoryMessages = async () => {
  findChatMessagePageList(current.value, size.value, chatId.value).then((res) => {
      isLoadingMore.value = false
      if (res.data.success) {
        const historyMessages = res.data.data
        hasMore.value = res.data.pages > current.value
        if (historyMessages && historyMessages.length > 0) {
          // 记录旧滚动高度，用于维持滚动位置
          const oldScrollHeight = chatContainer.value ? chatContainer.value.scrollHeight : 0
          const oldScrollTop = chatContainer.value ? chatContainer.value.scrollTop : 0

          chatList.value = [...historyMessages, ...chatList.value]

          nextTick(() => {
            if (chatContainer.value && current.value !== 1) {
              const newScrollHeight = chatContainer.value.scrollHeight
              chatContainer.value.scrollTop = oldScrollTop + (newScrollHeight - oldScrollHeight)
            }
          })
        }
        if (current.value === 1) {
          scrollToBottom()
        }
      }
  }).catch((error) => {
      console.error('加载历史消息失败:', error)
      isLoadingMore.value = false
  })
}

let eventSource = null

const chatId = ref(route.params.chatId || null)

const sendMessage = async (payload) => {
  if (!message.value.trim()) return

  console.log('选中的模型:', payload.selectedModel)
  console.log('是否联网:', payload.isNetworkSearch)

  const userMessage = message.value.trim()
  chatList.value.push({ role: 'user', content: userMessage })
  message.value = ''

  const aiIndex = chatList.value.length
  // 思考进行阶段推理区默认展开；思考结束后自动折叠
  collapsedReasoning[aiIndex] = false

  chatList.value.push({ role: 'assistant', content: '', reasoning: '', loading: true, reasoningPhase: 'active' })

  // BUG1 修复：本地回显两条消息后，立刻等 DOM 渲染完成平滑滚到底，避免新气泡被底部输入框遮住
  shouldAutoScroll.value = true
  scrollToBottom({ smooth: true })

  try {
    const requestBody = {
      message: userMessage,
      chatId: chatId.value,
      modelName: payload.selectedModel?.name,
      networkSearch: payload.isNetworkSearch === true,
      think: payload.isDeepThinking === true
    }

    let reasoningText = ''
    const lastMessage = chatList.value[chatList.value.length - 1]

    // 流式阶段状态机：thinking（思考进行中）→ collapsing（折叠动画中）→ answering（正文流式中）
    let streamPhase = 'thinking'
    let pendingAnswer = ''
    let startAnswerTimer = null

    // 任何离开 "thinking" 阶段的分支都调用：标题切换为「已完成思考」
    const finishReasoningPhase = () => {
      if (lastMessage.reasoningPhase === 'active') {
        lastMessage.reasoningPhase = 'done'
      }
    }

    const controller = new AbortController()
    const signal = controller.signal

    fetchEventSource('http://localhost:8080/chat/completion', {
      method: 'POST',
      signal: signal,
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify(requestBody),
      openWhenHidden: true,
      onmessage(msg) {
        if (msg.event === '') {
          if (lastMessage.loading) {
              lastMessage.loading = false;
          }
          let parseJson = JSON.parse(msg.data)
          const reasoningChunk = parseJson.reasoning || ''
          const answerChunk = parseJson.v || ''

          // 1) 思考进行阶段：仅在 thinking 阶段接收推理增量，保持展开，标题显示「正在思考中」
          if (reasoningChunk && streamPhase === 'thinking') {
            reasoningText += reasoningChunk
            lastMessage.reasoning = reasoningText
            collapsedReasoning[aiIndex] = false
          }

          // 2) 正文增量：出现首个正文 chunk → 标题改为「已完成思考」+ 自动折叠思考 → 折叠动画完成后输出正文
          if (answerChunk) {
            pendingAnswer += answerChunk

            if (streamPhase === 'thinking') {
              finishReasoningPhase()
              if (!reasoningText) {
                // 模型未返回思考内容，跳过折叠等待，直接开始正文流式输出
                streamPhase = 'answering'
                lastMessage.content = pendingAnswer
                pendingAnswer = ''
              } else {
                // 思考阶段结束 → 触发思考区块自动折叠（利用已有 CSS transition）
                streamPhase = 'collapsing'
                collapsedReasoning[aiIndex] = true

                // 折叠动画（max-height 0.3s）完成后，以累计正文为起点开始流式输出
                startAnswerTimer = setTimeout(() => {
                  streamPhase = 'answering'
                  lastMessage.content = pendingAnswer
                  pendingAnswer = ''
                  scrollToBottom()
                }, 350)
              }
            } else if (streamPhase === 'answering') {
              lastMessage.content = lastMessage.content + answerChunk
            }
            // streamPhase === 'collapsing'：折叠动画进行中，仅累积正文，不写入 DOM，避免闪烁抖动
          }

          scrollToBottom()
        }
        else if (msg.event === 'close') {
          finishReasoningPhase()
          controller.abort();
        }
      },
      onerror(err) {
        finishReasoningPhase()
        if (startAnswerTimer) clearTimeout(startAnswerTimer)
        throw err;
      }
    })
  } catch (error) {
    console.error('发送消息错误: ', error)
    const lastMessage = chatList.value[chatList.value.length - 1]
    lastMessage.reasoningPhase = 'done'
    lastMessage.content = '抱歉，请求出错了，请稍后重试。'
    lastMessage.loading = false
    scrollToBottom()
  }
}

/**
 * 滚到聊天容器底部。
 * - 默认 behavior:auto（瞬时），适合流式逐帧追底、折叠动画完成后对齐；
 * - 发送消息本地回显 / 首次加载历史等需要平滑体验的场景调用时传 { smooth: true }，
 *   避免新气泡、思考 Loading 看起来"被底部输入框压住"。
 */
const scrollToBottom = async ({ smooth = false } = {}) => {
  await nextTick()
  if (chatContainer.value && (shouldAutoScroll.value || smooth)) {
    const container = chatContainer.value;
    // 兜底：即使 smooth=true 浏览器不支持 behavior，也直接赋 scrollHeight 保证位置到位
    if (smooth && typeof container.scrollTo === 'function') {
      try {
        container.scrollTo({ top: container.scrollHeight, left: 0, behavior: 'smooth' })
        return
      } catch (_) {
        // 某些老浏览器 behavior 参数会抛错，降级成直接赋值
      }
    }
    container.scrollTop = container.scrollHeight;
  }
}

const closeSSE = () => {
  if (eventSource) {
    eventSource.close()
    eventSource = null
  }
}

onBeforeUnmount(() => {
  closeSSE()
  if (chatContainer.value) {
    chatContainer.value.removeEventListener('scroll', handleScroll);
  }
  if (chatScrollTimer) clearTimeout(chatScrollTimer)
  // 解绑所有 AI 思考框滚动条监听
  reasoningCleanups.forEach((fn) => fn())
  reasoningCleanups.clear()
  // 断开 DOM 观察
  if (reasoningObserver) {
    reasoningObserver.disconnect()
    reasoningObserver = null
  }
})

const handleScroll = () => {
  if (chatContainer.value) {
    const scrollTop = chatContainer.value.scrollTop
    const scrollHeight = chatContainer.value.scrollHeight
    const distanceFromBottom = scrollHeight - (scrollTop + chatContainer.value.clientHeight)
    shouldAutoScroll.value = distanceFromBottom <= BOTTOM_THRESHOLD
    console.log('=== 滚动事件日志 ===')
    console.log('scrollTop:', scrollTop)
    console.log('scrollHeight:', scrollHeight)
    console.log('isLoadingMore:', isLoadingMore.value)
    console.log('hasMore:', hasMore.value)

    if (scrollTop < 50 && hasMore.value && !isLoadingMore.value) {
      console.log('=== 触发加载更多历史消息 ===');
      loadMoreHistoryMessages();
    }
  }
}

const loadMoreHistoryMessages = () => {
  console.log('=== 开始加载更多历史消息 ===')
  console.log('当前页码:', current.value)

  if (!hasMore.value) {
    console.log('=== 没有更多历史消息，不再请求 ===')
    return
  }

  if (isLoadingMore.value) {
    console.log('=== 已有加载请求正在进行中，不再发送新请求 ===')
    return
  }

  isLoadingMore.value = true

  const nextPageNo = current.value + 1
  console.log('=== 计算下一页页码 ===', nextPageNo)

  const currentTemp = current.value
  current.value = nextPageNo

  try {
    loadHistoryMessages()
  } catch (error) {
    current.value = currentTemp
  }
}
</script>

<style scoped>
/* ========== 页面主容器（纵向 flex，外层禁止滚动，输入框永远在底部） ========== */
.chat-page-root {
  position: relative;
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  overflow: hidden;
}

/* ========== 聊天消息滚动容器（铺满主内容区宽度，滚动条紧贴页面右边缘） ========== */
.chat-messages-area {
  flex: 1;
  min-height: 0;
  width: 100%;
  overflow-y: auto;
  overflow-x: hidden;
  /* 自身不做 max-width，让其宽度贴到页面右边缘，从而滚动条紧贴边缘 */
}

/* 消息内容内层：统一限宽、auto 居中；整体右移 5px + 内容总宽度收缩 20px */
.messages-content {
  max-width: 1200px;
  width: 100%;
  margin-left: auto;
  margin-right: auto;
  /* top right bottom left → 左 50+5=55，右 50+15=65；总 padding L+R 多了 20px ⇒ 内容宽度收缩 20px
     且左侧比上次多 5px 空白 ⇒ 整体相对容器视觉向右偏移 5px
     底部 padding 由 32px → 12px：缩小最后一条消息与输入框顶部的留白（BUG2 修复） */
  padding: 80px 55px 0px 65px;
  box-sizing: border-box;
}

/* ========== 消息气泡 ========== */
.quesiton-container {
  font-size: 16px;
  line-height: 28px;
  color: #262626;
  padding: 10px 20px;
  box-sizing: border-box;
  white-space: pre-wrap;
  word-break: break-word;
  background-color: #eff6ff;
  border-radius: 18px;
  max-width: 72%;
  position: relative;
}

/* AI 回复内容：抵达容器最右边缘才触发换行 */
.reply-content {
  width: 100%;
  max-width: 100%;
  font-size: 15px;
  line-height: 28px;
  color: #171717;
  padding: 4px 0;
  word-break: break-word;
  white-space: normal;
  overflow-wrap: anywhere;
}

/* ========== 推理过程容器（透明无背景，简约风格） ========== */
.reasoning-container {
  background: transparent;
  border: none;
  border-radius: 0;
  margin-bottom: -5px;
  overflow: visible;
}

.reasoning-header {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 6px;
  padding: 0 0 8px 0;
  background-color: transparent;
  cursor: pointer;
  user-select: none;
  transition: color 0.2s ease;
}

.reasoning-header:hover {
  background-color: transparent;
}

.reasoning-title {
  font-size: 14px;
  font-weight: 400;
  color: #9ca3af;
  letter-spacing: 0;
}

/* 箭头：展开态朝下（∨），收起态朝右（>） */
.reasoning-chevron {
  color: #9ca3af;
  transition: transform 0.25s ease, color 0.2s ease;
  flex-shrink: 0;
  width: 14px;
  height: 14px;
  /* 默认展开态：箭头朝下 */
  transform: rotate(0deg);
}

.reasoning-chevron.is-collapsed {
  /* 收起态：箭头朝右 */
  transform: rotate(-90deg);
  color: #9ca3af;
}

.reasoning-chevron:not(.is-collapsed) {
  transform: rotate(0deg);
  color: #9ca3af;
}

.reasoning-content-wrapper {
  overflow: hidden;
  max-height: 210px;
  opacity: 1;
  transition: max-height 0.3s cubic-bezier(.4,0,.2,1), opacity 0.25s ease;
}

/* 展开时，超出500px出现滚动 */
.reasoning-content-wrapper:not(.is-collapsed) {
  overflow-y: auto;
}

.reasoning-content-wrapper.is-collapsed {
  max-height: 0;
  opacity: 0;
  overflow-y: hidden;
}

.reasoning-content {
  padding: 0;
  font-size: 15px;
  line-height: 1.8;
  color: #9ca3af;
  white-space: pre-wrap;
  word-break: break-word;
  font-style: normal;
}

/* 隐藏推理与正文之间的分割线，保持简约 */
.reasoning-divider {
  display: none;
}


/* ========== 底部输入框区域（同级固定底部，宽度占满，内层限宽） ========== */
.chat-bottom-area {
  flex-shrink: 0;
  width: 100%;
  /* 顶部留白收窄：原来仅靠 gradient 70% 过渡视觉上会在聊天滚到底与输入框之间留一层淡白；
     显式设 8px padding-top，既保证视觉上不顶边，又让整体紧凑，BUG2 不再有太多底部空白。 */
  padding-top: 8px;
  background: linear-gradient(to top, #ffffff 70%, rgba(255,255,255,0));
}

/* 输入框内层：与 messages-content 同 max-width，auto 居中保证左右外边缘严格对齐；内容宽度再收缩 10px（45→50） */
.bottom-content {
  max-width: 1200px;
  width: 100%;
  margin-left: auto;
  margin-right: auto;
  /* 对称收缩 10px：左右各 +5px ⇒ 45+5=50；
     底部 padding-bottom 由 24px → 12px：输入框与页面底部留白紧凑（BUG2 修复） */
  padding: 0 50px 12px 50px;
  box-sizing: border-box;
}

/* ========== 滚动条样式：静止隐藏 / 滚动时立即显示 / 停滚延迟自动隐藏；滑块圆角、轨道透明；过渡平滑 ========== */
/* ① 默认（静止）状态：滚动条宽度为 0，完全隐藏不可见（Firefox scrollbar-width:none） */
.chat-messages-area::-webkit-scrollbar,
.reasoning-content-wrapper::-webkit-scrollbar {
  width: 0;
  height: 0;
  transition: width 0.25s ease;
}
.chat-messages-area {
  scrollbar-width: none;
}
.reasoning-content-wrapper {
  scrollbar-width: none;
}

/* ② 滚动发生状态（is-scrolling class）：立刻显示滚动条（宽度 6px） */
.chat-messages-area.is-scrolling::-webkit-scrollbar,
.reasoning-content-wrapper.is-scrolling::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}
.chat-messages-area.is-scrolling {
  scrollbar-width: thin;
  scrollbar-color: rgba(0, 0, 0, 0.20) transparent;
}
.reasoning-content-wrapper.is-scrolling {
  scrollbar-width: thin;
  scrollbar-color: rgba(156, 163, 175, 0.55) transparent;
}

/* 轨道：始终透明 */
.chat-messages-area::-webkit-scrollbar-track,
.reasoning-content-wrapper::-webkit-scrollbar-track {
  background: transparent;
}

/* 滑块：圆角、默认透明（隐藏）、带过渡，is-scrolling 时平滑淡入 */
.chat-messages-area::-webkit-scrollbar-thumb,
.reasoning-content-wrapper::-webkit-scrollbar-thumb {
  background: transparent;
  border-radius: 999px;
  border: none;
  transition: background 0.25s ease;
}

.chat-messages-area.is-scrolling::-webkit-scrollbar-thumb {
  background: rgba(0, 0, 0, 0.20);
  border-radius: 999px;
}
.chat-messages-area.is-scrolling::-webkit-scrollbar-thumb:hover {
  background: rgba(0, 0, 0, 0.30);
}

.reasoning-content-wrapper.is-scrolling::-webkit-scrollbar-thumb {
  background: rgba(156, 163, 175, 0.55);
  border-radius: 999px;
}
.reasoning-content-wrapper.is-scrolling::-webkit-scrollbar-thumb:hover {
  background: rgba(107, 114, 128, 0.7);
}

/* 用 padding-right 的小幅变化缓解「滚动条显示/隐藏导致宽度抖动」：仅对 chat-messages-area 应用 */
.chat-messages-area {
  padding-right: 0;
  transition: padding-right 0.25s ease;
}
.chat-messages-area.is-scrolling {
  /* 6px 滚动条宽度 + 2px 留白，避免文字和滑块贴近 */
  padding-right: 0; /* 滚动条本身在 border 区域，不挤占内容；保持 0 防布局位移 */
}

/* ========== 响应式：整体右移 5px + 右偏收缩；auto 居中保持对齐 ========== */
@media (max-width: 768px) {
  .messages-content {
    max-width: 100%;
    width: 100%;
    margin-left: auto;
    margin-right: auto;
    /* 右移 5px（左 +5），总收缩 20px（左 +10，右 +10 → 合计 20；左=34+5=39，右=34+15=49）
       底部 padding-bottom: 20px → 10px，保持和桌面端同样紧凑（BUG2 修复） */
    padding: 70px 49px 10px 39px;
  }
  .quesiton-container {
    max-width: 85%;
  }
  .reply-content {
    width: 100%;
    max-width: 100%;
  }
  .bottom-content {
    max-width: 100%;
    width: 100%;
    margin-left: auto;
    margin-right: auto;
    /* 对称收缩 10px：29 + 5 = 34；底部 padding 16px → 10px 紧凑 */
    padding: 0 34px 10px 34px;
  }
}
</style>

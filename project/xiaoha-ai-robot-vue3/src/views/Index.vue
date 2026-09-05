<template>
  <Layout>
    <!-- 主内容区域：三段式 Flex 布局（欢迎区flex-1 + 输入框固定底部） -->
    <template #main-content>
      <div class="index-page-root">
        <!-- 顶部/中部：欢迎内容区域（内部垂直居中，超出可滚动） -->
        <div class="index-welcome-area">
          <div class="welcome-content">
            <div class="welcome-inner">
              <div class="flex items-center justify-center mb-3">
                <SvgIcon name="potato" customCss="w-10 h-10 text-gray-700 mr-3" />
                <h2 class="text-2xl text-gray-800">我是Potato AI，很高兴见到你！</h2>
              </div>
              <p class="text-gray-500 text-center">
                从写代码、查bug到方案设计，编程相关的难题都可以交给我！你只管说出需求，剩下的代码实现、问题排查，交给我就好~
              </p>
            </div>
          </div>
        </div>

        <!-- 底部输入框（固定在屏幕最下方，与 ChatPage 布局完全一致） -->
        <div class="index-bottom-area">
          <div class="bottom-content">
            <ChatInputBox
              v-model="userMessage"
              @sendMessage="sendMessage"
            />
          </div>
        </div>
      </div>
    </template>
  </Layout>
</template>

<script setup>
import { ref, watch } from 'vue'
import Layout from '@/layouts/Layout.vue'
import SvgIcon from '@/components/SvgIcon.vue'
import ChatInputBox from '@/components/ChatInputBox.vue'
import { newChat } from '@/api/chat'
import { useRouter } from 'vue-router'
import { useChatStore } from '@/stores/chatStore'

const router = useRouter()
const chatStore = useChatStore()

// 用户输入的消息
const userMessage = ref('')

watch(userMessage, (newText) => {
  console.log(`子组件传递的新值: ${newText}`)
})

// 发送消息 - 跳转到对话聊天页并发送消息
// 注意：深度思考/联网搜索开关状态已通过 chatStore 的 persist:true 持久化，
// 进入 ChatPage 后会自动从 store 读取，无需在 state 里再传一次。
const sendMessage = (payload) => {
  if (!userMessage.value.trim()) return;

  console.log('选中的模型:', payload.selectedModel)
  console.log('是否联网:', payload.isNetworkSearch)
  console.log('是否深度思考:', payload.isDeepThinking)

  // 临时保存消息的值，因为子组件中的 userMessage 会被清空
  const userMessageTemp = userMessage.value.trim();
  console.log('用户发送的消息: ' + userMessageTemp)

  // 请求对话新建接口
  newChat(userMessageTemp).then(res => {
    if (res.data.success) {
        // 跳转到聊天对话页面
        router.push({
          name: 'ChatPage', // 必须使用命名路由来跳转
          params: {
            chatId: res.data.data.uuid // Url 中的 UUID
          },
          state: {
            firstMessage: userMessageTemp, // 将用户在首页填入的消息，传递给 "聊天对话页"
          }
        })
    }
  })
}
</script>

<style scoped>
/* ========== Index 页面主容器（纵向 flex，外层禁止滚动） ========== */
.index-page-root {
  flex: 1;
  min-height: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #ffffff;
  overflow: hidden;
}

/* ========== 欢迎内容区（flex-1 占满输入框上方的全部空间） ========== */
.index-welcome-area {
  flex: 1;
  min-height: 0;
  width: 100%;
  overflow-y: auto;
  overflow-x: hidden;
}

/* 欢迎内容内层：与 .bottom-content 使用相同的 max-width + 左右 padding，确保与输入框左右对齐 */
.welcome-content {
  min-height: 100%;
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 40px 32px;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.welcome-inner {
  text-align: center;
  max-width: 768px;
  width: 100%;
}

/* ========== 底部输入框区域（与 ChatPage.chat-bottom-area 完全一致） ========== */
.index-bottom-area {
  flex-shrink: 0;
  width: 100%;
  padding-top: 8px;
  background: linear-gradient(to top, #ffffff 70%, rgba(255,255,255,0));
}

/* 输入框内层：与 ChatPage.bottom-content 参数完全一致，保证宽度、位置与 ChatPage 输入框完全统一 */
.bottom-content {
  max-width: 1200px;
  width: 100%;
  margin: 0 auto;
  padding: 0 32px 24px 32px;
  box-sizing: border-box;
}

/* ========== 响应式：与 ChatPage 响应式同步 ========== */
@media (max-width: 768px) {
  .welcome-content {
    max-width: 100%;
    padding: 24px 16px;
  }
  .bottom-content {
    max-width: 100%;
    padding: 0 16px 16px 16px;
  }
}
</style>

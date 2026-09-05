<template>
  <div :class="containerClass">
    <div class="chat-input-box">
      <!-- 文本输入区 -->
      <textarea
        :placeholder="props.placeholder"
        class="chat-textarea"
        v-model="userMessage"
        @input="autoResize"
        @keydown.enter="handleEnter"
        ref="textareaRef"
      ></textarea>

      <!-- 下方工具栏（在输入框内部下方） -->
      <div class="chat-toolbar">
        <!-- 左侧功能按钮组 -->
        <div class="toolbar-left">
          <!-- + 号按钮 -->
          <button class="toolbar-btn" title="更多功能">
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="12" y1="5" x2="12" y2="19"></line>
              <line x1="5" y1="12" x2="19" y2="12"></line>
            </svg>
          </button>

          <!-- 大模型下拉框 -->
          <div v-show="props.showModelDropdown" class="relative">
            <div
              class="model-selector"
              ref="selectRef"
              @click="toggleModelDropdown"
            >
              <SvgIcon :name="currSelectedModel.icon" customCss="w-4 h-4 mr-1" />
              <span class="model-label">{{ currSelectedModel.label }}</span>
              <SvgIcon name="down-arrow" customCss="w-3 h-3 ml-0.5 text-gray-800 transform transition-transform duration-300"
                :class="isModelDropdownOpen ? 'rotate-180' : ''" />
            </div>

            <!-- 下拉框菜单：与按钮同容器，left-0 左对齐不变，仅按空间决定上下方向 -->
            <div
              v-if="isModelDropdownOpen"
              ref="dropdownRef"
              :class="['absolute', 'left-0', 'w-48', 'bg-white', 'rounded-lg', 'shadow-lg', 'border', 'border-gray-200', 'z-50', 'overflow-hidden', dropdownPosition]"
            >
              <div
                v-for="model in models"
                :key="model.id"
                class="px-3 py-2 hover:bg-gray-100 cursor-pointer flex items-center justify-between"
                @click="selectModel(model)"
              >
                <div class="flex items-center">
                  <SvgIcon :name="model.icon" customCss="w-5 h-5 mr-2" />
                  <div class="flex flex-col text-xs">
                    <div class="text-gray-800">{{ model.label }}</div>
                    <div class="text-gray-500">{{ model.description }}</div>
                  </div>
                </div>
                <SvgIcon v-if="model.selected" name="check" customCss="w-3 h-3 text-gray-600" />
              </div>
            </div>
          </div>

          <!-- 联网搜索 -->
          <div
            v-show="props.showNetworkSearch"
            class="network-search-btn"
            :class="isNetworkSearchSelected ? 'selected' : ''"
            @click="toggleNetworkSearch"
          >
            <SvgIcon name="network" customCss="w-4 h-4 mr-1" :class="isNetworkSearchSelected ? 'text-[#4D6BFE]' : 'text-gray-500'" />
            <span class="text-sm" :class="isNetworkSearchSelected ? 'text-[#4D6BFE]' : 'text-gray-800'">联网搜索</span>
          </div>

          <!-- 深度思考（新增，UI 样式与「联网搜索」完全对齐） -->
          <div
            v-show="props.showDeepThinking !== false"
            class="deep-thinking-btn"
            :class="isDeepThinkingSelected ? 'selected' : ''"
            @click="toggleDeepThinking"
            title="开启后，模型会先输出思考过程再给出最终回答"
          >
            <!-- 大脑图标，代表"思考" -->
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"
                 stroke-linecap="round" stroke-linejoin="round"
                 class="w-4 h-4 mr-1"
                 :class="isDeepThinkingSelected ? 'text-[#4D6BFE]' : 'text-gray-500'">
              <path d="M9.5 2A2.5 2.5 0 0 1 12 4.5v15a2.5 2.5 0 0 1-4.96.44 2.5 2.5 0 0 1-2.96-3.08 3 3 0 0 1-.34-5.58 2.5 2.5 0 0 1 1.32-4.24 2.5 2.5 0 0 1 4.94-2.04Z"></path>
              <path d="M14.5 2A2.5 2.5 0 0 0 12 4.5v15a2.5 2.5 0 0 0 4.96.44 2.5 2.5 0 0 0 2.96-3.08 3 3 0 0 0 .34-5.58 2.5 2.5 0 0 0-1.32-4.24 2.5 2.5 0 0 0-4.94-2.04Z"></path>
            </svg>
            <span class="text-sm" :class="isDeepThinkingSelected ? 'text-[#4D6BFE]' : 'text-gray-800'">深度思考</span>
          </div>
        </div>

        <!-- 右侧：发送按钮 -->
        <a-tooltip placement="top">
          <template #title>
            <span>请输入你的问题</span>
          </template>
          <button
            class="send-btn"
            :disabled="!userMessage.trim()"
            @click="handleSendMessage"
          >
            <SvgIcon name="up-arrow" customCss="w-4 h-4 text-white"></SvgIcon>
          </button>
        </a-tooltip>
      </div>
    </div>

  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import SvgIcon from '@/components/SvgIcon.vue'
import { message } from 'ant-design-vue'
import { useChatStore } from '@/stores/chatStore'

const chatStore = useChatStore()

const props = defineProps({
  modelValue: {
    type: String,
    required: true
  },
  containerClass: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: '可以让我帮你写代码、排查Bug、设计开发方案~'
  },
  showModelDropdown: {
    type: Boolean,
    default: true
  },
  showNetworkSearch: {
    type: Boolean,
    default: true
  },
  /** 是否显示深度思考开关：默认 true；CustomerServiceChatPage 之类的非聊天页面传 false 隐藏即可 */
  showDeepThinking: {
    type: Boolean,
    default: true
  },
})

const emit = defineEmits(['update:modelValue', 'sendMessage'])

const userMessage = computed({
  get() {
    return props.modelValue;
  },
  set(value) {
    emit('update:modelValue', value);
  }
})

const models = computed(() => chatStore.models)

const isModelDropdownOpen = ref(false)
const selectRef = ref(null)
const leftContainerRef = ref(null)
const dropdownRef = ref(null)
const dropdownPosition = ref('bottom-10')

const toggleModelDropdown = (event) => {
  isModelDropdownOpen.value = !isModelDropdownOpen.value
  if (isModelDropdownOpen.value) {
    nextTick(() => {
      if (selectRef.value && dropdownRef.value) {
        const buttonRect = selectRef.value.getBoundingClientRect();
        const dropdownHeight = dropdownRef.value.offsetHeight;
        const gap = 8; // 弹窗与按钮之间的小间距
        // 优先向上展开：按钮顶部到视口顶部有足够空间（弹窗高度 + 间距）
        if (buttonRect.top >= dropdownHeight + gap) {
          // 弹窗底部贴到 relative 容器顶部（即按钮顶部）再额外留出 gap 间距
          dropdownPosition.value = 'bottom-[calc(100%+8px)]';
        } else {
          // 上方空间不足，回退向下弹出：弹窗顶部贴到 relative 容器底部（即按钮底部）再额外留出 gap 间距
          dropdownPosition.value = 'top-[calc(100%+8px)]';
        }
      }
    })
  }
}

const handleClickOutside = (event) => {
  const clickedInSelector = selectRef.value && selectRef.value.contains(event.target);
  const clickedInDropdown = dropdownRef.value && dropdownRef.value.contains(event.target);
  if (!clickedInSelector && !clickedInDropdown) {
    isModelDropdownOpen.value = false
  }
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
})

const currSelectedModel = computed(() => chatStore.selectedModel)

const selectModel = (model) => {
  chatStore.updateSelectedModel(model);
  isModelDropdownOpen.value = false;
}

const isNetworkSearchSelected = computed(() => chatStore.isNetworkSearchSelected)

const toggleNetworkSearch = () => {
  chatStore.updateNetworkSearchStatus(!chatStore.isNetworkSearchSelected)
}

const isDeepThinkingSelected = computed(() => !!chatStore.isDeepThinkingSelected) // 强制布尔，避免字符串/undefined 污染

const toggleDeepThinking = () => {
  chatStore.updateDeepThinkingStatus(!chatStore.isDeepThinkingSelected)
}

const handleSendMessage = () => {
  if (!userMessage.value.trim()) {
    message.warning('消息不能为空');
    return
  }
  emit('sendMessage', {
    selectedModel: chatStore.selectedModel,
    isNetworkSearch: !!chatStore.isNetworkSearchSelected, // 强制原生布尔 true/false
    isDeepThinking:  !!chatStore.isDeepThinkingSelected   // 新增：强制原生布尔，不允许字符串
  });
  userMessage.value = '';
}

const textareaRef = ref(null)

const autoResize = () => {
  const textarea = textareaRef.value;
  if (textarea) {
    textarea.style.height = 'auto'
    const newHeight = Math.min(textarea.scrollHeight, 200);
    textarea.style.height = newHeight + 'px';
    textarea.style.overflowY = textarea.scrollHeight > 200 ? 'auto' : 'hidden';
  }
}

const handleEnter = (event) => {
  if (event.shiftKey) {
    return
  }
  event.preventDefault()
  handleSendMessage()
}
</script>

<style scoped>
.chat-input-box {
  background: #ffffff;
  border: 1px solid #e8e8ef;
  border-radius: 20px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.06);
  padding: 12px 16px 10px;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.chat-textarea {
  width: 100%;
  border: none;
  outline: none;
  background: transparent;
  font-size: 16px;
  line-height: 28px;
  resize: none;
  min-height: 36px;
  max-height: 200px;
  overflow-y: auto;
  color: #171717;
  placeholder-color: #a3a3a3;
  box-sizing: border-box;
}

.chat-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 10px;
}

.toolbar-btn {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  color: #666;
  cursor: pointer;
  transition: background-color 0.2s;
}
.toolbar-btn:hover {
  background: #f0f0f0;
}

.model-selector {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid #dedede;
  cursor: pointer;
  font-size: 13px;
  background: #fff;
  transition: background-color 0.2s;
}
.model-selector:hover {
  background: #f1f1f1;
}
.model-label {
  font-size: 13px;
  color: #333;
}

.network-search-btn {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid #d0d0d0;
  cursor: pointer;
  font-size: 13px;
  background: #fff;
  transition: background-color 0.2s;
}
.network-search-btn:hover {
  background: #f0f0f0;
}
.network-search-btn.selected {
  border-color: #ceddee;
  background: #DBEAFE;
}
.network-search-btn.selected:hover {
  background: #C3DAF8;
}

/* 深度思考开关：与联网搜索视觉完全对齐 */
.deep-thinking-btn {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  border: 1px solid #d0d0d0;
  cursor: pointer;
  font-size: 13px;
  background: #fff;
  transition: background-color 0.2s;
}
.deep-thinking-btn:hover {
  background: #f0f0f0;
}
.deep-thinking-btn.selected {
  border-color: #ceddee;
  background: #DBEAFE;
}
.deep-thinking-btn.selected:hover {
  background: #C3DAF8;
}

.send-btn {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: #4D6BFE;
  border: 1px solid #4D6BFE;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: background-color 0.2s;
}
.send-btn:hover:not(:disabled) {
  background: #3a56d4;
  border-color: #3a56d4;
}
.send-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
</style>

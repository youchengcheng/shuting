<template>
  <div class="topic-selector">
    <!-- 话题输入框 -->
    <div class="topic-input-row">
      <span class="topic-hash">#</span>
      <input
        ref="inputRef"
        v-model="keyword"
        type="text"
        placeholder="搜索添加话题"
        class="topic-input"
        @input="onInput"
        @keydown.enter="onEnter"
        @keydown.backspace="onBackspace"
      />
    </div>

    <!-- 已选话题展示 -->
    <div v-if="selectedTopics.length" class="topic-chips">
      <div v-for="topic in selectedTopics" :key="topic.id" class="topic-chip">
        <span class="topic-chip__hash">#</span>
        {{ topic.name }}
        <button type="button" class="topic-chip__remove" aria-label="移除话题" @click="removeTopic(topic)">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
            <path d="M18 6L6 18M6 6l12 12" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>
      </div>
    </div>

    <!-- 搜索结果下拉框 -->
    <div v-if="showDropdown && suggestions.length" class="topic-dropdown">
      <button
        v-for="topic in suggestions"
        :key="topic.id"
        type="button"
        class="topic-option"
        @click="selectTopic(topic)"
      >
        <span class="topic-option__hash">#</span>
        <span class="topic-option__main">
          <span class="topic-option__name">{{ topic.name }}</span>
          <span class="topic-option__count st-num">{{ topic.noteCount }} 篇笔记</span>
        </span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'

const props = defineProps({
  modelValue: {
    type: Array,
    default: () => []
  }
})

const emit = defineEmits(['update:modelValue'])

const keyword = ref('')
const suggestions = ref([])
const showDropdown = ref(false)
const inputRef = ref(null)

const selectedTopics = ref(props.modelValue)

// 监听外部传入的值变化
watch(() => props.modelValue, (newVal) => {
  selectedTopics.value = newVal
})

// 搜索话题 - 模拟接口
const searchTopics = async (kw) => {
  // TODO: 替换为真实接口
  return [
    { id: 1, name: kw + '推荐', noteCount: 1234 },
    { id: 2, name: kw + '热门', noteCount: 5678 },
    { id: 3, name: kw + '相关', noteCount: 9012 }
  ]
}

// 自定义防抖函数
const debounce = (fn, delay) => {
  let timer = null
  return (...args) => {
    if (timer) clearTimeout(timer)
    timer = setTimeout(() => {
      fn.apply(this, args)
    }, delay)
  }
}

// 使用自定义防抖函数替代 useDebounceFn
const debouncedSearch = debounce(async (kw) => {
  if (!kw) {
    suggestions.value = []
    showDropdown.value = false
    return
  }
  suggestions.value = await searchTopics(kw)
  showDropdown.value = true
}, 300)

// 输入处理
const onInput = () => {
  debouncedSearch(keyword.value)
}

// 选择话题
const selectTopic = (topic) => {
  if (selectedTopics.value.some(t => t.id === topic.id)) {
    return
  }
  selectedTopics.value = [...selectedTopics.value, topic]
  emit('update:modelValue', selectedTopics.value)
  keyword.value = ''
  showDropdown.value = false
  inputRef.value?.focus()
}

// 移除话题
const removeTopic = (topic) => {
  selectedTopics.value = selectedTopics.value.filter(t => t.id !== topic.id)
  emit('update:modelValue', selectedTopics.value)
}

// 回车处理
const onEnter = () => {
  if (suggestions.value.length) {
    selectTopic(suggestions.value[0])
  }
}

// 退格键处理
const onBackspace = (e) => {
  if (!keyword.value && selectedTopics.value.length) {
    e.preventDefault()
    selectedTopics.value = selectedTopics.value.slice(0, -1)
    emit('update:modelValue', selectedTopics.value)
  }
}
</script>

<style scoped>
.topic-selector {
  position: relative;
}

.topic-input-row {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 0;
  border-bottom: 1px solid var(--color-line);
}

.topic-hash {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-ink-faint);
}

.topic-input {
  flex: 1;
  min-width: 0;
  border: none;
  outline: none;
  background: transparent;
  font-size: 14px;
  color: var(--color-ink);
}

.topic-input::placeholder {
  color: var(--color-ink-faint);
}

.topic-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.topic-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 30px;
  padding: 0 10px 0 12px;
  border-radius: var(--radius-pill);
  background: var(--color-canvas-sunken);
  font-size: 13px;
  color: var(--color-ink);
}

.topic-chip__hash {
  color: var(--color-ink-faint);
}

.topic-chip__remove {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  padding: 0;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-ink-faint);
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}

.topic-chip__remove:hover {
  background: var(--color-line);
  color: var(--color-ink);
}

.topic-chip__remove svg {
  width: 12px;
  height: 12px;
}

.topic-dropdown {
  position: absolute;
  left: 0;
  right: 0;
  top: 100%;
  z-index: 10;
  margin-top: 4px;
  padding: 6px;
  max-height: 280px;
  overflow-y: auto;
  background: var(--color-paper);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-panel);
}

.topic-option {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  width: 100%;
  padding: 8px 10px;
  border: none;
  border-radius: var(--radius-control);
  background: transparent;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--motion-fast) var(--ease-standard);
}

.topic-option:hover {
  background: var(--color-canvas-sunken);
}

.topic-option__hash {
  color: var(--color-ink-faint);
}

.topic-option__main {
  display: flex;
  flex-direction: column;
}

.topic-option__name {
  font-size: 14px;
  color: var(--color-ink);
}

.topic-option__count {
  font-size: 12px;
  color: var(--color-ink-faint);
}
</style>

<template>
  <div v-if="visible" class="loading-overlay">
    <div class="spinner-container">
      <div class="spinner" role="progressbar" aria-label="加载中"></div>
      <div v-if="text" class="spinner-text">{{ text }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'

const props = defineProps({
  // 加载时显示的文本
  text: {
    type: String,
    default: ''
  },
  // 最小显示时间（毫秒）
  minDuration: {
    type: Number,
    default: 500
  }
})

// 控制加载器的可见性
const visible = ref(false)
// 记录显示开始时间
let showStartTime = 0
// 记录隐藏请求时间
let hideRequestTime = 0
// 隐藏定时器
let hideTimer = null

// 显示加载器
const show = () => {
  // 清除可能存在的隐藏定时器
  if (hideTimer) {
    clearTimeout(hideTimer)
    hideTimer = null
  }

  // 记录显示开始时间
  showStartTime = Date.now()
  visible.value = true
}

// 隐藏加载器
const hide = () => {
  // 记录隐藏请求时间
  hideRequestTime = Date.now()

  // 计算已显示时间
  const elapsedTime = hideRequestTime - showStartTime

  // 如果已显示时间小于最小持续时间，则延迟隐藏
  if (elapsedTime < props.minDuration) {
    const remainingTime = props.minDuration - elapsedTime
    hideTimer = setTimeout(() => {
      visible.value = false
      hideTimer = null
    }, remainingTime)
  } else {
    // 已经显示足够长时间，直接隐藏
    visible.value = false
  }
}

// 暴露方法给父组件
defineExpose({
  show,
  hide
})
</script>

<style scoped>
.loading-overlay {
  width: 100%;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 0;
}

.spinner-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
}

.spinner {
  width: 22px;
  height: 22px;
  border: 2px solid rgb(20 17 14 / 0.12);
  border-top-color: var(--color-ink);
  border-radius: 50%;
  animation: spin 0.9s linear infinite;
}

.spinner-text {
  font-size: 13px;
  color: var(--color-ink-faint);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}
</style>

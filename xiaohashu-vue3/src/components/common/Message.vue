<template>
  <Transition name="fade">
    <div v-if="visible" class="message-toast" role="status">
      {{ message }}
    </div>
  </Transition>
</template>

<script setup>
import { ref } from 'vue'

const visible = ref(false)
const message = ref('')

// 显示消息
const show = (msg, duration = 2000) => {
  message.value = msg
  visible.value = true

  setTimeout(() => {
    visible.value = false
  }, duration)
}

// 暴露方法给外部使用
defineExpose({
  show
})
</script>

<style scoped>
.message-toast {
  position: fixed;
  left: 50%;
  bottom: 48px;
  transform: translateX(-50%);
  z-index: 10000;
  max-width: 80vw;
  padding: 10px 20px;
  border-radius: var(--radius-control);
  background: var(--color-ink);
  color: var(--color-paper);
  font-size: 13px;
  line-height: 1.5;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  box-shadow: var(--shadow-panel);
}

.fade-enter-active,
.fade-leave-active {
  transition:
    opacity var(--motion-base) var(--ease-standard),
    transform var(--motion-base) var(--ease-standard);
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
  transform: translateX(-50%) translateY(8px);
}
</style>

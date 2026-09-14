<template>
  <Teleport to="body">
    <Transition name="dialog-mask">
      <div v-if="visible" class="confirm-mask" @click="onMaskClick"></div>
    </Transition>

    <Transition name="confirm">
      <div v-if="visible" class="confirm-layer">
        <div class="confirm-panel" role="alertdialog" aria-modal="true" :aria-label="title">
          <h2 class="confirm-title">{{ title }}</h2>
          <p v-if="message" class="confirm-message">{{ message }}</p>

          <div class="confirm-footer">
            <button type="button" class="st-btn st-btn-ghost" :disabled="loading" @click="onCancel">
              {{ cancelText }}
            </button>
            <button
              type="button"
              class="st-btn"
              :class="danger ? 'confirm-btn--danger' : 'st-btn-primary'"
              :disabled="loading"
              @click="onConfirm"
            >
              <span v-if="loading" class="confirm-spinner" aria-hidden="true"></span>
              {{ confirmText }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { onMounted, onUnmounted } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  },
  title: {
    type: String,
    default: '确认操作'
  },
  message: {
    type: String,
    default: ''
  },
  confirmText: {
    type: String,
    default: '确定'
  },
  cancelText: {
    type: String,
    default: '取消'
  },
  danger: {
    type: Boolean,
    default: false
  },
  loading: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible', 'confirm'])

const onClose = () => {
  if (props.loading) return
  emit('update:visible', false)
}

const onCancel = () => {
  onClose()
}

const onConfirm = () => {
  if (props.loading) return
  emit('confirm')
}

const onMaskClick = () => {
  onClose()
}

const handleEscKey = (e) => {
  if (e.key !== 'Escape' || !props.visible) return

  // 弹窗常驻挂载在页面里，Esc 只关闭确认框本身，
  // 不再冒泡给下层的笔记详情浮层等监听者
  e.stopImmediatePropagation()
  onClose()
}

onMounted(() => {
  document.addEventListener('keydown', handleEscKey)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscKey)
})
</script>

<style scoped>
.confirm-mask {
  position: fixed;
  inset: 0;
  z-index: 200;
  background: rgb(20 17 14 / 0.32);
}

.confirm-layer {
  position: fixed;
  inset: 0;
  z-index: 201;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  pointer-events: none;
}

.confirm-panel {
  width: 400px;
  max-width: 100%;
  padding: 28px 24px 20px;
  pointer-events: auto;
  background: var(--color-paper);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
}

.confirm-title {
  font-size: 17px;
  font-weight: 600;
  color: var(--color-ink);
  text-align: center;
}

.confirm-message {
  margin-top: 12px;
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-ink-soft);
  text-align: center;
}

.confirm-footer {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
}

.confirm-btn--danger {
  background: #e02020;
  color: #fff;
}

.confirm-btn--danger:hover:not(:disabled) {
  background: #c81c1c;
}

.confirm-spinner {
  width: 14px;
  height: 14px;
  border: 2px solid rgb(255 255 255 / 0.5);
  border-top-color: #fff;
  border-radius: 50%;
  animation: confirm-spin 0.7s linear infinite;
}

@keyframes confirm-spin {
  to {
    transform: rotate(360deg);
  }
}

.confirm-enter-active,
.confirm-leave-active {
  transition:
    opacity 240ms var(--ease-standard),
    transform 240ms var(--ease-standard);
}

.confirm-enter-from,
.confirm-leave-to {
  opacity: 0;
  transform: translateY(10px) scale(0.98);
}
</style>

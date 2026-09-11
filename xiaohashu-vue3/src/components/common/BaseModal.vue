<template>
  <Teleport to="body">
    <div v-if="visible" class="modal-mask" @click="onClickMask"></div>

    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0 translate-y-2"
      enter-to-class="opacity-100 translate-y-0"
      leave-active-class="transition duration-150 ease-in"
      leave-from-class="opacity-100 translate-y-0"
      leave-to-class="opacity-0 translate-y-2"
    >
      <div v-if="visible" class="modal-layer">
        <div class="modal-panel" :style="{ width: width }" role="dialog" aria-modal="true" @click.stop>
          <div class="modal-header">
            <h2 class="st-section-title">{{ title }}</h2>
            <button type="button" class="modal-close" aria-label="关闭" @click="onClose">
              <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M18 6L6 18M6 6l12 12" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" />
              </svg>
            </button>
          </div>

          <div class="modal-body">
            <slot></slot>
          </div>

          <div v-if="showFooter" class="modal-footer">
            <button type="button" class="st-btn st-btn-ghost" @click="onClose">取消</button>
            <button type="button" class="st-btn st-btn-primary" @click="$emit('confirm')">确定</button>
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
    required: true
  },
  width: {
    type: String,
    default: '480px'
  },
  showFooter: {
    type: Boolean,
    default: true
  },
  closeOnClickMask: {
    type: Boolean,
    default: true
  }
})

const emit = defineEmits(['update:visible', 'confirm'])

const onClose = () => {
  emit('update:visible', false)
}

const onClickMask = () => {
  if (props.closeOnClickMask) {
    onClose()
  }
}

const handleEscKey = (e) => {
  if (e.key === 'Escape' && props.visible) {
    onClose()
  }
}

onMounted(() => {
  document.addEventListener('keydown', handleEscKey)
})

onUnmounted(() => {
  document.removeEventListener('keydown', handleEscKey)
})
</script>

<style scoped>
.modal-mask {
  position: fixed;
  inset: 0;
  z-index: 100;
  background: rgb(20 17 14 / 0.32);
}

.modal-layer {
  position: fixed;
  inset: 0;
  z-index: 101;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  pointer-events: none;
}

.modal-panel {
  display: flex;
  flex-direction: column;
  max-width: 100%;
  max-height: 90vh;
  pointer-events: auto;
  background: var(--color-paper);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
  overflow: hidden;
}

.modal-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-line);
}

.modal-close {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border: none;
  border-radius: var(--radius-control);
  background: transparent;
  color: var(--color-ink-faint);
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}

.modal-close:hover {
  background: var(--color-canvas-sunken);
  color: var(--color-ink);
}

.modal-close svg {
  width: 18px;
  height: 18px;
}

.modal-body {
  flex: 1;
  overflow-y: auto;
  padding: 24px;
}

.modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  padding: 16px 24px;
  border-top: 1px solid var(--color-line);
}
</style>

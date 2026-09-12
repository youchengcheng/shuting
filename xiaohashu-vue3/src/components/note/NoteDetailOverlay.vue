<template>
  <div
    class="note-overlay"
    role="dialog"
    aria-modal="true"
    aria-label="笔记详情"
    @click.self="close"
  >
    <button type="button" class="note-overlay__close" aria-label="关闭" @click="close">
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M18 6 6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
      </svg>
    </button>

    <div class="note-overlay__card">
      <NoteDetailContent :note-id="noteId" />
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NoteDetailContent from '@/components/note/NoteDetailContent.vue'

defineProps({
  noteId: {
    type: [String, Number],
    default: ''
  }
})

const route = useRoute()
const router = useRouter()

// 关闭浮层：回到打开前所在的页面（信息流 / 个人页 / 搜索页），
// 底层页面全程保持挂载，所以返回后不会重新请求数据、也不会丢失滚动位置。
// 直接从链接进入时没有可回退的历史记录，此时兜底回到浮层所属的父页面。
const close = () => {
  const state = window.history.state
  if (state?.back && state.back !== route.fullPath) {
    router.back()
    return
  }
  router.replace(route.path.replace(/\/note\/[^/]+$/, '') || '/discover')
}

const handleKeydown = (event) => {
  if (event.key === 'Escape') close()
}

// 浮层打开期间锁定背景滚动
onMounted(() => {
  document.addEventListener('keydown', handleKeydown)
  document.body.style.overflow = 'hidden'
  document.documentElement.style.overflow = 'hidden'
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = ''
  document.documentElement.style.overflow = ''
})
</script>

<style scoped>
.note-overlay {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 56px 88px;
  background: rgb(0 0 0 / 0.62);
}

.note-overlay__card {
  width: min(1080px, 100%);
  height: min(760px, 100%);
  border-radius: var(--radius-card);
  overflow: hidden;
  background: var(--color-paper);
  box-shadow: 0 20px 60px rgb(0 0 0 / 0.32);
}

.note-overlay__close {
  position: fixed;
  top: 24px;
  left: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-brand);
  color: #fff;
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    transform var(--motion-fast) var(--ease-standard);
}

.note-overlay__close:hover {
  background: var(--color-brand-hover);
  transform: scale(1.05);
}

.note-overlay__close svg {
  width: 20px;
  height: 20px;
}

/* 进出场过渡：遮罩淡入淡出 + 卡片轻微上浮 */
.note-overlay-enter-active,
.note-overlay-leave-active {
  transition: opacity 220ms var(--ease-standard);
}

.note-overlay-enter-active .note-overlay__card,
.note-overlay-leave-active .note-overlay__card {
  transition:
    opacity 220ms var(--ease-standard),
    transform 260ms var(--ease-standard);
}

.note-overlay-enter-from,
.note-overlay-leave-to {
  opacity: 0;
}

.note-overlay-enter-from .note-overlay__card,
.note-overlay-leave-to .note-overlay__card {
  opacity: 0;
  transform: translateY(18px) scale(0.985);
}

@media (prefers-reduced-motion: reduce) {
  .note-overlay-enter-active,
  .note-overlay-leave-active,
  .note-overlay-enter-active .note-overlay__card,
  .note-overlay-leave-active .note-overlay__card {
    transition-duration: 1ms;
  }
}

@media (max-width: 1279px) {
  .note-overlay {
    padding: 48px 40px;
  }
}

@media (max-width: 1023px) {
  .note-overlay {
    padding: 0;
  }

  .note-overlay__card {
    width: 100%;
    height: 100%;
    border-radius: 0;
    overflow-y: auto;
  }

  .note-overlay__close {
    top: 12px;
    left: 12px;
    width: 36px;
    height: 36px;
  }
}
</style>

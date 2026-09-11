<template>
  <div class="note-detail-page">
    <!-- 背景信息流：与小红书一致，笔记详情以浮层形式盖在首页之上 -->
    <div class="note-detail-page__bg" aria-hidden="true">
      <Discover />
    </div>

    <Teleport to="body">
      <div
        class="note-modal"
        role="dialog"
        aria-modal="true"
        aria-label="笔记详情"
        @click.self="close"
      >
        <button type="button" class="note-modal__close" aria-label="关闭" @click="close">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path d="M18 6 6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
          </svg>
        </button>

        <div class="note-modal__card">
          <NoteDetailContent :note-id="noteId" />
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import Discover from '@/views/Discover.vue'
import NoteDetailContent from '@/components/note/NoteDetailContent.vue'

const route = useRoute()
const router = useRouter()

const noteId = computed(() => route.params.noteId)

// 关闭浮层：回到上一页（信息流），直接从链接进入时兜底回首页
const close = () => {
  if (window.history.state?.back) {
    router.back()
  } else {
    router.replace('/discover')
  }
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
.note-detail-page__bg {
  pointer-events: none;
  user-select: none;
}

.note-modal {
  position: fixed;
  inset: 0;
  z-index: 200;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 56px 88px;
  background: rgb(0 0 0 / 0.62);
  animation: note-scrim-in 200ms var(--ease-standard);
}

.note-modal__card {
  width: min(1080px, 100%);
  height: min(760px, 100%);
  border-radius: var(--radius-card);
  overflow: hidden;
  background: var(--color-paper);
  box-shadow: 0 20px 60px rgb(0 0 0 / 0.32);
  animation: note-card-in 240ms var(--ease-standard);
}

.note-modal__close {
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

.note-modal__close:hover {
  background: var(--color-brand-hover);
  transform: scale(1.05);
}

.note-modal__close svg {
  width: 20px;
  height: 20px;
}

@keyframes note-scrim-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

@keyframes note-card-in {
  from {
    opacity: 0;
    transform: translateY(14px) scale(0.99);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 1279px) {
  .note-modal {
    padding: 48px 40px;
  }
}

@media (max-width: 1023px) {
  .note-modal {
    padding: 0;
  }

  .note-modal__card {
    width: 100%;
    height: 100%;
    border-radius: 0;
    overflow-y: auto;
  }

  .note-modal__close {
    top: 12px;
    left: 12px;
    width: 36px;
    height: 36px;
  }
}
</style>

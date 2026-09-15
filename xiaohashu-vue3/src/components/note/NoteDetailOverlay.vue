<template>
  <div
    ref="overlayRef"
    class="note-overlay"
    role="dialog"
    aria-modal="true"
    aria-label="笔记详情"
  >
    <!-- 遮罩单独一层：展开时它只负责「变暗」，封面与卡片各自演各自的 -->
    <div class="note-overlay__scrim" data-overlay-scrim @click="handleClose"></div>

    <button
      type="button"
      class="note-overlay__close"
      data-overlay-close
      aria-label="关闭"
      @click="handleClose"
    >
      <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="M18 6 6 18M6 6l12 12" stroke="currentColor" stroke-width="2" stroke-linecap="round" />
      </svg>
    </button>

    <div class="note-overlay__card" data-overlay-card>
      <NoteDetailContent :note-id="noteId" />
    </div>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NoteDetailContent from '@/components/note/NoteDetailContent.vue'
import { playOpenMorph } from '@/composables/noteMorph'
import { useNoteTransition } from '@/composables/noteTransition'
import { useNoteStore } from '@/stores/note'

const props = defineProps({
  noteId: {
    type: [String, Number],
    default: ''
  }
})

const route = useRoute()
const router = useRouter()
const noteStore = useNoteStore()
const { closeNote } = useNoteTransition()

const overlayRef = ref(null)
const closing = ref(false)

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

// 关闭时封面沿原路飞回卡片原位；收回动画播完才切换路由，
// 过渡期间浮层立刻从新画面里拿掉，避免残留
const handleClose = () => {
  if (closing.value) return
  closing.value = true
  closeNote(props.noteId, () => {
    close()
    if (overlayRef.value) overlayRef.value.style.display = 'none'
  })
}

const handleKeydown = (event) => {
  if (event.key === 'Escape') handleClose()
}

// 浮层打开期间锁定背景滚动
onMounted(async () => {
  document.addEventListener('keydown', handleKeydown)
  document.body.style.overflow = 'hidden'
  document.documentElement.style.overflow = 'hidden'

  // 展开动画：封面从卡片原位飞到媒体区（起点由 openNote 记录在 store 里）
  await nextTick()
  playOpenMorph({ overlayEl: overlayRef.value, hero: noteStore.pendingHero })
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', handleKeydown)
  document.body.style.overflow = ''
  document.documentElement.style.overflow = ''
  // 乐观封面只服务于本次打开，关掉浮层就清掉，避免影响下一篇
  noteStore.setPendingHero(null)
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
}

.note-overlay__scrim {
  position: absolute;
  inset: 0;
  background: rgb(0 0 0 / 0.62);
}

.note-overlay__card {
  position: relative;
  z-index: 1;
  width: min(1080px, 100%);
  height: min(760px, 100%);
  border-radius: var(--radius-card);
  overflow: hidden;
  background: var(--color-paper);
  box-shadow: 0 20px 60px rgb(0 0 0 / 0.32);
}

.note-overlay__close {
  position: fixed;
  z-index: 3;
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

<template>
  <div class="popover-anchor">
    <!-- 触发元素插槽 -->
    <div ref="triggerRef" @mouseenter="showPopover" @mouseleave="hidePopover">
      <slot></slot>
    </div>

    <!-- 弹出卡片 -->
    <Transition name="pop">
      <div
        v-if="visible"
        class="popover-panel"
        :style="popoverStyle"
        @mouseenter="showPopover"
        @mouseleave="hidePopover"
      >
        <div class="popover-head">
          <img :src="user.avatar" class="popover-avatar" :alt="user.name" />

          <div class="popover-identity">
            <span class="popover-name">{{ user.name }}</span>
          </div>

          <button type="button" class="st-btn st-btn-primary popover-follow">关注</button>
        </div>

        <p class="popover-desc">{{ user.description }}</p>

        <div class="popover-stats">
          <div class="popover-stat">
            <span class="popover-stat__num st-num">{{ user.notes }}</span>
            <span class="popover-stat__label">关注</span>
          </div>
          <div class="popover-stat">
            <span class="popover-stat__num st-num">{{ user.followers }}</span>
            <span class="popover-stat__label">粉丝</span>
          </div>
          <div class="popover-stat">
            <span class="popover-stat__num st-num">{{ user.likes }}</span>
            <span class="popover-stat__label">获赞与收藏</span>
          </div>
        </div>

        <div class="popover-notes">
          <div v-for="note in user.recentNotes" :key="note.id" class="popover-note">
            <img :src="note.cover" alt="note cover" />
          </div>
        </div>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'

const props = defineProps({
  user: {
    type: Object,
    required: true
  },
  placement: {
    type: String,
    default: 'bottom-start'
  }
})

const visible = ref(false)
const triggerRef = ref(null)
const triggerBounds = ref({
  height: 0,
  width: 0,
  top: 0,
  left: 0
})

// 更新元素位置信息
const updateBounds = () => {
  if (triggerRef.value) {
    const rect = triggerRef.value.getBoundingClientRect()
    triggerBounds.value = {
      height: rect.height,
      width: rect.width,
      top: rect.top,
      left: rect.left
    }
  }
}

// 计算弹出框位置
const popoverStyle = computed(() => {
  if (props.placement === 'bottom-start') {
    return {
      top: `${triggerBounds.value.height + 8}px`,
      left: '0'
    }
  }
  // 可以添加其他位置的计算...
})

let hideTimer = null

const showPopover = () => {
  if (hideTimer) {
    clearTimeout(hideTimer)
    hideTimer = null
  }
  visible.value = true
}

const hidePopover = () => {
  hideTimer = setTimeout(() => {
    visible.value = false
  }, 200)
}

onMounted(() => {
  updateBounds()
  window.addEventListener('resize', updateBounds)
  window.addEventListener('scroll', updateBounds)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', updateBounds)
  window.removeEventListener('scroll', updateBounds)
})
</script>

<style scoped>
.popover-anchor {
  position: relative;
  display: inline-block;
}

.popover-panel {
  position: absolute;
  z-index: 50;
  display: flex;
  flex-direction: column;
  gap: 14px;
  width: 340px;
  padding: 20px;
  background: var(--color-paper);
  border: 1px solid var(--color-line);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
}

.popover-head {
  display: flex;
  align-items: center;
  gap: 12px;
}

.popover-avatar {
  flex-shrink: 0;
  width: 44px;
  height: 44px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-line);
  object-fit: cover;
}

.popover-identity {
  flex: 1;
  min-width: 0;
}

.popover-name {
  display: block;
  font-size: 15px;
  font-weight: 600;
  color: var(--color-ink);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.popover-follow {
  flex-shrink: 0;
  height: 32px;
  padding: 0 16px;
  font-size: 13px;
  border-radius: var(--radius-pill);
}

.popover-desc {
  font-size: 13px;
  line-height: 1.6;
  color: var(--color-ink-soft);
}

.popover-stats {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.popover-stat {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.popover-stat__num {
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}

.popover-stat__label {
  font-size: 12px;
  color: var(--color-ink-faint);
}

.popover-notes {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}

.popover-note {
  aspect-ratio: 1 / 1;
  overflow: hidden;
  border-radius: var(--radius-control);
  background: var(--color-canvas-sunken);
}

.popover-note img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  cursor: pointer;
  transition: opacity var(--motion-fast) var(--ease-standard);
}

.popover-note img:hover {
  opacity: 0.88;
}
</style>

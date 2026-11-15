<template>
  <div class="tab-nav">
    <div
      ref="tabList"
      class="tab-list"
      :class="{ 'tab-list--animated': animate && indicator.width }"
      role="tablist"
    >
      <span
        v-if="animate && indicator.width"
        class="tab-indicator"
        :style="indicatorStyle"
        aria-hidden="true"
      ></span>
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        role="tab"
        class="tab-item"
        :class="{ active: modelValue === tab.key }"
        :aria-selected="modelValue === tab.key"
        :aria-label="tab.count ? `${tab.label}，${tab.count}条未读` : tab.label"
        @click="$emit('update:modelValue', tab.key)"
      >
        <span v-if="tab.icon" class="tab-item__icon" aria-hidden="true">
          <svg v-if="tab.icon === 'note'" viewBox="0 0 24 24" fill="none">
            <rect x="4" y="3.5" width="16" height="17" rx="3" stroke="currentColor" stroke-width="1.7" />
            <path d="M8 8.5h8M8 12h8M8 15.5h5" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
          </svg>
          <svg v-else-if="tab.icon === 'collect'" viewBox="0 0 24 24" fill="none">
            <path
              d="M12 3.6l2.5 5.1 5.6.8-4.1 4 1 5.6-5-2.7-5 2.7 1-5.6-4.1-4 5.6-.8L12 3.6Z"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linejoin="round"
            />
          </svg>
          <svg v-else-if="tab.icon === 'like'" viewBox="0 0 24 24" fill="none">
            <path
              d="M20.8 5.6a4.9 4.9 0 0 0-7 0L12 7.4l-1.8-1.8a4.9 4.9 0 1 0-7 7L12 21.4l8.8-8.8a4.9 4.9 0 0 0 0-7Z"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linejoin="round"
            />
          </svg>
        </span>
        <span class="tab-text">{{ tab.label }}</span>
        <span v-if="tab.count > 0" class="tab-count st-num" aria-hidden="true">{{ tab.count > 99 ? '99+' : tab.count }}</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'

const props = defineProps({
  tabs: {
    type: Array,
    required: true
  },
  modelValue: {
    type: String,
    required: true
  },
  animate: {
    type: Boolean,
    default: false
  }
})

defineEmits(['update:modelValue'])
const tabList = ref(null)
const indicator = ref({ left: 0, top: 0, width: 0, height: 0 })
const indicatorStyle = computed(() => ({
  transform: `translate(${indicator.value.left}px, ${indicator.value.top}px)`,
  width: `${indicator.value.width}px`,
  height: `${indicator.value.height}px`
}))
let resizeObserver
const updateIndicator = () => {
  if (!props.animate) return
  const selected = tabList.value?.querySelector('.tab-item.active')
  indicator.value = selected
    ? {
      left: selected.offsetLeft,
      top: selected.offsetTop,
      width: selected.offsetWidth,
      height: selected.offsetHeight
    }
    : { left: 0, top: 0, width: 0, height: 0 }
}
watch(() => [props.modelValue, props.tabs, props.animate], updateIndicator, {
  deep: true,
  flush: 'post'
})
onMounted(() => {
  updateIndicator()
  if (!props.animate) return
  resizeObserver = new ResizeObserver(updateIndicator)
  resizeObserver.observe(tabList.value)
  tabList.value.querySelectorAll('.tab-item').forEach(button => resizeObserver.observe(button))
})
onBeforeUnmount(() => resizeObserver?.disconnect())
</script>

<style scoped>
.tab-nav {
  display: flex;
  justify-content: center;
}

.tab-list {
  position: relative;
  display: flex;
  align-items: center;
  gap: 8px;
}

.tab-indicator {
  position: absolute;
  top: 0;
  left: 0;
  border-radius: var(--radius-pill);
  background: var(--color-canvas-sunken);
  pointer-events: none;
  transition:
    transform var(--motion-base) var(--ease-standard),
    width var(--motion-base) var(--ease-standard);
}

.tab-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 40px;
  padding: 0 20px;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-ink-faint);
  font-size: 15px;
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}

.tab-item:hover {
  background: var(--color-canvas-sunken);
  color: var(--color-ink);
}

.tab-item.active {
  background: var(--color-canvas-sunken);
  color: var(--color-ink);
  font-weight: 500;
}

.tab-list--animated .tab-item.active,
.tab-list--animated .tab-item:hover {
  background: transparent;
}

.tab-list--animated .tab-item:active {
  color: var(--color-brand-hover);
}

.tab-item__icon svg {
  display: block;
  width: 18px;
  height: 18px;
}
.tab-count {
  min-width: 18px;
  padding: 1px 5px;
  border-radius: var(--radius-pill);
  color: var(--color-brand-hover);
  background: var(--color-paper);
  font-size: 12px;
  line-height: 1.4;
}

@media (prefers-reduced-motion: reduce) {
  .tab-indicator {
    transition: none;
  }

}
</style>

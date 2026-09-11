<template>
  <div class="tab-nav">
    <div class="tab-list" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        role="tab"
        class="tab-item"
        :class="{ active: modelValue === tab.key }"
        :aria-selected="modelValue === tab.key"
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
      </button>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  tabs: {
    type: Array,
    required: true
  },
  modelValue: {
    type: String,
    required: true
  }
})

defineEmits(['update:modelValue'])
</script>

<style scoped>
.tab-nav {
  display: flex;
  justify-content: center;
}

.tab-list {
  display: flex;
  align-items: center;
  gap: 8px;
}

.tab-item {
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

.tab-item__icon svg {
  display: block;
  width: 18px;
  height: 18px;
}
</style>

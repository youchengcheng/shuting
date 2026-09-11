<template>
  <div class="empty-state" :class="{ 'empty-state--compact': compact }">
    <BrandLogo v-if="!compact" class="empty-state__mark" :size="48" tone="ink" :show-word="false" />
    <p class="empty-state__title">{{ title }}</p>
    <p v-if="description" class="empty-state__description">{{ description }}</p>
    <div v-if="$slots.default" class="empty-state__actions">
      <slot></slot>
    </div>
  </div>
</template>

<script setup>
import BrandLogo from '@/components/common/BrandLogo.vue'

defineProps({
  title: {
    type: String,
    required: true
  },
  description: {
    type: String,
    default: ''
  },
  compact: {
    type: Boolean,
    default: false
  }
})
</script>

<style scoped>
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 80px 24px;
  text-align: center;
}

.empty-state--compact {
  padding: 40px 24px;
  gap: 8px;
}

.empty-state__mark {
  margin-bottom: 4px;
  opacity: 0.85;
}

.empty-state__title {
  font-size: 18px;
  line-height: 1.4;
  color: var(--color-ink);
}

.empty-state__description {
  max-width: 320px;
  font-size: 13px;
  line-height: 1.7;
  color: var(--color-ink-faint);
}

.empty-state__actions {
  margin-top: 8px;
}
</style>

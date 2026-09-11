<template>
  <span class="brand-logo" :class="`brand-logo--${tone}`">
    <svg
      class="brand-logo__mark"
      :width="size"
      :height="size"
      viewBox="0 0 24 24"
      fill="none"
      aria-hidden="true"
    >
      <!-- 亭：飞檐 / 书：翻开的两页与书脊 -->
      <rect width="24" height="24" rx="7" fill="currentColor" />
      <g stroke="#fff" stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round">
        <path d="M5.6 10.4 12 5.9l6.4 4.5" />
        <path d="M5.6 14.25c2.3-.62 4.43-.02 6.4 1.5 1.97-1.52 4.1-2.12 6.4-1.5" />
        <path d="M12 15.75v3.85" />
      </g>
    </svg>
    <span v-if="showWord" class="brand-logo__word">书亭</span>
  </span>
</template>

<script setup>
defineProps({
  size: {
    type: Number,
    default: 32
  },
  // brand：品牌红底（顶栏、登录、关于）；ink：单色墨（空状态等弱化场景）
  tone: {
    type: String,
    default: 'brand'
  },
  showWord: {
    type: Boolean,
    default: true
  }
})
</script>

<style scoped>
.brand-logo {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.brand-logo__mark {
  display: block;
  flex-shrink: 0;
}

.brand-logo--brand .brand-logo__mark {
  color: var(--color-brand);
}

.brand-logo--ink .brand-logo__mark {
  color: var(--color-ink);
}

.brand-logo__word {
  font-size: 17px;
  font-weight: 600;
  line-height: 1;
  letter-spacing: 0.01em;
  color: var(--color-ink);
  white-space: nowrap;
}

@media (max-width: 767px) {
  .brand-logo__word {
    display: none;
  }
}
</style>

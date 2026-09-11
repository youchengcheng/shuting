<template>
  <BaseModal :visible="visible" title="关于书亭" width="420px" :show-footer="false" @update:visible="$emit('update:visible', $event)">
    <div class="about">
      <BrandLogo class="about__seal" :size="52" :show-word="false" />
      <p class="about__brand">书亭</p>
      <p class="about__desc">
        一方安静的记录处。写下你的笔记、收藏喜欢的内容，也看看别人正在读什么。
      </p>
      <div class="about__divider"></div>
      <a
        class="about__link"
        href="https://www.quanxiaoha.com/column/"
        target="_blank"
        rel="noopener noreferrer"
      >
        <span>由《犬小哈实战专栏》开发</span>
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="M7 17L17 7M17 7H9M17 7v8"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </a>
      <p class="about__version st-num">v1.0</p>
    </div>
  </BaseModal>
</template>

<script setup>
import BaseModal from '@/components/common/BaseModal.vue'
import BrandLogo from '@/components/common/BrandLogo.vue'

defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

defineEmits(['update:visible'])
</script>

<style scoped>
.about {
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  gap: 10px;
}

.about__seal {
  display: block;
}

.about__brand {
  margin-top: 6px;
  font-size: 22px;
  color: var(--color-ink);
}

.about__desc {
  margin: 0;
  max-width: 280px;
  font-size: 13px;
  line-height: 1.75;
  color: var(--color-ink-soft);
}

.about__divider {
  width: 100%;
  height: 1px;
  margin: 12px 0 4px;
  background: var(--color-line);
}

.about__link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 13px;
  color: var(--color-ink-soft);
  transition: color var(--motion-fast) var(--ease-standard);
}

.about__link:hover {
  color: var(--color-ink);
}

.about__link svg {
  width: 14px;
  height: 14px;
}

.about__version {
  margin: 0;
  font-size: 12px;
  color: var(--color-ink-faint);
}
</style>

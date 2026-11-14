<template>
  <BaseModal :visible="visible" title="关于书亭" width="420px" :show-footer="false" @update:visible="$emit('update:visible', $event)">
    <div class="about">
      <BrandLogo class="about__seal" :size="52" :show-word="false" />
      <p class="about__brand">书亭</p>
      <p class="about__desc">
        一方安静的记录处。写下你的笔记、收藏喜欢的内容，也看看别人正在读什么。
      </p>
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

.about__version {
  margin: 0;
  font-size: 12px;
  color: var(--color-ink-faint);
}
</style>

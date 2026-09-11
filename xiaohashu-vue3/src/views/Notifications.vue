<template>
  <div class="notifications-page">
    <header class="page-header">
      <h1 class="st-page-title">通知</h1>
      <p class="page-header__desc">关注、评论与点赞的提醒会在这里汇总</p>
    </header>

    <div class="page-panel">
      <EmptyState title="敬请期待" description="通知中心正在建设中，先去发现页看看新笔记吧">
        <router-link to="/discover" class="st-btn st-btn-ghost">返回发现</router-link>
      </EmptyState>
    </div>
  </div>
</template>

<script setup>
import EmptyState from '@/components/common/EmptyState.vue'
</script>

<style scoped>
.notifications-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header__desc {
  margin-top: 8px;
  font-size: 14px;
  color: var(--color-ink-faint);
}

.page-panel {
  border: 1px solid var(--color-line);
  border-radius: var(--radius-panel);
  background: var(--color-paper);
}
</style>

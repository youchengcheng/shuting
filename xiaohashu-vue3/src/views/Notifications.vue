<template>
  <div class="notifications-page">
    <header class="page-header">
      <h1 class="st-page-title">通知</h1>
      <p class="page-header__desc">赞和收藏、评论和回复，以及新的关注</p>
    </header>

    <div class="page-panel">
      <EmptyState v-if="!user.token" title="登录后查看通知" description="与你有关的互动会出现在这里">
        <button type="button" class="st-btn st-btn-primary" @click="showLoginModal = true">登录</button>
      </EmptyState>
      <template v-else>
        <div class="notification-toolbar">
          <TabNav v-model="category" :tabs="tabs" animate />
        </div>
        <p v-if="store.connection === 'offline' || store.connection === 'reconnecting'" class="notification-status" role="status">{{ store.connection === 'offline' ? '网络已断开，联网后会自动更新通知' : page.items.length ? '正在重新连接，已加载的通知仍可查看' : '正在重新连接通知' }}</p>
        <p v-if="store.summaryError && store.summaryError !== page.error" class="notification-status" role="status">{{ store.summaryError }} <button type="button" @click="store.refreshSummary()">重试</button></p>
        <p v-if="store.readStateError" class="notification-status" role="status">通知提醒暂时无法同步 <button type="button" @click="store.refreshReadState()">重试</button></p>
        <p v-if="store.mutationError" class="notification-status" role="alert">通知提醒暂未清除，请稍后重试</p>
        <p v-if="navigationError" class="notification-status" role="alert">{{ navigationError }}</p>
        <div v-if="page.loading && !page.items.length" class="notification-loading" role="status">正在加载通知…</div>
        <EmptyState v-else-if="page.error && !page.items.length" title="通知加载失败" :description="page.error">
          <button type="button" class="st-btn st-btn-ghost" @click="refresh">重新加载</button>
        </EmptyState>
        <EmptyState v-else-if="!page.items.length" :title="emptyText" description="新的互动通知到来后会显示在这里" />
        <ul v-if="page.items.length" class="notification-list" aria-label="通知列表">
          <NotificationItem v-for="item in page.items" :key="item.notificationId" :item="item" @open="openNotification" />
        </ul>
        <div v-if="page.items.length" class="notification-pagination">
          <p v-if="page.error" role="alert">{{ page.error }}</p>
          <button v-if="page.hasMore || page.error" type="button" class="st-btn st-btn-ghost" :disabled="page.loading" @click="store.loadPage(category, page.errorMode === 'refresh')">{{ page.loading ? '正在加载…' : page.error ? page.errorMode === 'refresh' ? '重新加载通知' : '重新加载更多' : '加载更多通知' }}</button>
          <span v-else>已显示全部通知</span>
        </div>
      </template>
    </div>
    <router-view />
  </div>
</template>

<script setup>
import EmptyState from '@/components/common/EmptyState.vue'
import TabNav from '@/components/common/TabNav.vue'
import NotificationItem from '@/components/notification/NotificationItem.vue'
import { computed, inject, onBeforeUnmount, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'

const user = useUserStore()
const store = useNotificationStore()
const router = useRouter()
const showLoginModal = inject('showLoginModal')
const category = ref('LIKE_COLLECTION')
const navigationError = ref('')
const labels = { LIKE_COLLECTION: '赞和收藏', COMMENT_REPLY: '评论和回复', FOLLOW: '新增关注' }
const tabs = computed(() => Object.entries(labels).map(([key, label]) => ({ key, label, count: store.summary.unreadCounts[key] || 0 })))
const page = computed(() => store.pages[category.value])
const emptyText = computed(() => ({ LIKE_COLLECTION: '还没有赞和收藏', COMMENT_REPLY: '还没有评论和回复', FOLLOW: '还没有新的关注' })[category.value])
const refresh = () => { void store.loadPage(category.value, true); void store.refreshSummary(); void store.refreshReadState() }
watch([category, () => user.generation], () => {
  navigationError.value = ''
  void store.activateCategory(user.token ? category.value : null)
}, { immediate: true })
onBeforeUnmount(() => { void store.activateCategory(null) })
const openNotification = (item) => {
  navigationError.value = ''
  void store.markRead(item)
  if (item.type === 'FOLLOW') {
    void router.push(`/user/profile/${item.actor.userId}`)
    return
  }
  if (item.contentStatus !== 'AVAILABLE') {
    navigationError.value = item.contentStatus === 'TEMPORARY_UNAVAILABLE' ? '内容暂时无法查看，请稍后重试' : '内容已不可查看'
    return
  }
  const commentId = ['COMMENT', 'REPLY', 'COMMENT_LIKE'].includes(item.type) ? item.commentId : null
  void router.push({ name: 'NotificationNoteDetail', params: { noteId: item.noteId }, query: commentId ? { commentId } : {} })
}
</script>

<style scoped>
.notifications-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header {
  width: 80%;
  align-self: center;
}

.page-header__desc {
  margin-top: 8px;
  font-size: 14px;
  color: var(--color-ink-soft);
}

.page-panel {
  width: 80%;
  align-self: center;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-panel);
  background: var(--color-paper);
}
.notification-toolbar { display: flex; align-items: center; justify-content: center; flex-wrap: wrap; gap: 12px; padding: 16px; border-bottom: 1px solid var(--color-line); }
.notification-list { list-style: none; margin: 0; padding: 0; }
.notification-status { margin: 12px 24px; font-size: 14px; line-height: 1.6; color: var(--color-ink-soft); }
.notification-status button { min-height: 44px; padding: 0 12px; text-decoration: underline; text-underline-offset: 3px; cursor: pointer; }
.notification-loading, .notification-pagination { padding: 32px 16px; text-align: center; font-size: 14px; color: var(--color-ink-soft); }
.notification-pagination p { margin-bottom: 12px; }
.notification-toolbar :deep(.tab-item) { min-height: 44px; color: var(--color-ink-soft); }
@media (max-width: 767px) {
  .page-header, .page-panel { width: 100%; }
  .notification-toolbar { padding: 12px; }
  .notification-toolbar :deep(.tab-nav) { width: 100%; min-width: 0; justify-content: flex-start; }
  .notification-toolbar :deep(.tab-list) { max-width: 100%; gap: 0; overflow-x: auto; }
  .notification-toolbar :deep(.tab-item) { flex-shrink: 0; white-space: nowrap; padding: 0 8px; font-size: 14px; }
}
</style>

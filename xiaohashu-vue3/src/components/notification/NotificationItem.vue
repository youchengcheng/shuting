<template>
  <li class="notification-item">
    <!-- 用户头像 -->
    <router-link
      :to="`/user/profile/${item.actor.userId}`"
      class="notification-item__avatar-link"
      :aria-label="`查看${item.actor.nickname || '用户'}的主页`"
    >
      <img :src="item.actor.avatar || defaultAvatar" alt="" class="notification-item__avatar" loading="lazy" />
    </router-link>
    <!-- 通知正文 -->
    <button type="button" class="notification-item__main" @click="$emit('open', item)">
      <strong class="notification-item__nickname">{{ item.actor.nickname || '书亭用户' }}</strong>
      <span class="notification-item__meta">
        <span class="notification-item__action">{{ actionText }}</span>
        <time :datetime="item.occurredAt" class="notification-item__time">{{ displayTime }}</time>
      </span>
      <img
        v-if="preview.imageUrl"
        :src="preview.imageUrl"
        alt="评论图片"
        class="notification-item__comment-image"
        loading="lazy"
      />
      <span v-else-if="preview.text" class="notification-item__preview">{{ preview.text }}</span>
    </button>
    <!-- 目标笔记封面 -->
    <button
      v-if="preview.cover"
      type="button"
      class="notification-item__cover-link"
      aria-label="查看相关笔记"
      @click="$emit('open', item)"
    >
      <img :src="preview.cover" alt="" class="notification-item__cover" loading="lazy" />
    </button>
  </li>
</template>

<script setup>
import { computed } from 'vue'
import defaultAvatar from '@/assets/avatar.png'
import { notificationPreview } from '@/utils/notificationPresentation'

const props = defineProps({
  item: {
    type: Object,
    required: true
  }
})

defineEmits(['open'])

// 根据通知类型显示互动文案
const actionText = computed(() => ({
  NOTE_LIKE: '赞了你的笔记',
  COMMENT_LIKE: '赞了你的评论',
  NOTE_COLLECT: '收藏了你的笔记',
  COMMENT: '评论了你的笔记',
  REPLY: props.item.recipientRole === 'OWNER' ? '回复了你笔记中的评论' : '回复了你的评论',
  FOLLOW: '关注了你'
})[props.item.type] || '与你互动')

// 评论文字、评论图片和笔记封面
const preview = computed(() => notificationPreview(props.item))

// 格式化通知时间
const displayTime = computed(() => {
  const date = new Date(props.item.occurredAt)
  return Number.isNaN(date.getTime()) ? props.item.occurredAt : date.toLocaleString('zh-CN', {
    month: 'numeric',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit'
  })
})
</script>

<style scoped>
.notification-item {
  display: flex;
  align-items: flex-start;
  gap: 14px;
  padding: 20px 24px;
  border-bottom: 1px solid var(--color-line);
}

.notification-item:last-child {
  border-bottom: 0;
}

.notification-item__avatar-link {
  flex: none;
  border-radius: var(--radius-pill);
}

.notification-item__avatar {
  width: 44px;
  height: 44px;
  object-fit: cover;
  border-radius: var(--radius-pill);
  background: var(--color-canvas-sunken);
}

.notification-item__main {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  padding: 0;
  border: 0;
  background: transparent;
  text-align: left;
  color: var(--color-ink-soft);
  cursor: pointer;
}

.notification-item__nickname {
  max-width: 100%;
  font-size: 15px;
  line-height: 1.5;
  font-weight: 500;
  color: var(--color-ink);
  overflow-wrap: anywhere;
}

.notification-item__meta {
  display: flex;
  align-items: baseline;
  gap: 10px;
  max-width: 100%;
}

.notification-item__action {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-ink-soft);
}

.notification-item__time {
  flex: none;
  white-space: nowrap;
  font-size: 12px;
  color: var(--color-ink-soft);
  font-variant-numeric: tabular-nums;
}

.notification-item__preview {
  max-width: 100%;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 14px;
  line-height: 1.6;
}

.notification-item__comment-image {
  width: 96px;
  height: 72px;
  object-fit: contain;
  border-radius: var(--radius-control);
  background: var(--color-canvas-sunken);
}

.notification-item__cover-link {
  flex: none;
  padding: 0;
  border: 0;
  border-radius: var(--radius-control);
  cursor: pointer;
  background: var(--color-canvas-sunken);
}

.notification-item__cover {
  width: 64px;
  height: 72px;
  border-radius: var(--radius-control);
  object-fit: cover;
}

@media (max-width: 767px) {
  .notification-item {
    gap: 10px;
    padding: 18px 12px;
  }

  .notification-item__cover {
    width: 48px;
    height: 58px;
  }
}
</style>

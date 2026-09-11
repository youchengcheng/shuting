<template>
  <div class="comment-item" :class="{ 'comment-item--reply': isReply, 'new-comment-animation': comment.isNewComment }">
    <img :src="comment.avatar" class="comment-item__avatar" :alt="comment.nickname" />

    <div class="comment-item__body">
      <!-- 评论者信息 -->
      <span class="comment-item__name">{{ comment.nickname }}</span>

      <!-- 评论内容 -->
      <p class="comment-item__content">
        <span v-if="comment.replyUserName" class="comment-item__reply">回复 {{ comment.replyUserName }}：</span>
        {{ comment.content }}
      </p>

      <!-- 评论图片 -->
      <div v-if="comment.imageUrl" class="comment-item__image">
        <img :src="comment.imageUrl" alt="评论图片" @click="showPreview = true" />
      </div>

      <!-- 图片预览 -->
      <ImagePreview v-model:visible="showPreview" :images="[comment.imageUrl]" />

      <div class="comment-item__time st-num">{{ comment.createTime }}</div>

      <!-- 评论底部操作区 -->
      <div class="comment-item__actions">
        <!-- 点赞 -->
        <button type="button" class="comment-action" :class="{ 'comment-action--liked': isLiked }" @click="toggleLike">
          <svg
            class="comment-action__icon"
            :class="isLiked ? 'animate-like' : 'animate-unlike'"
            viewBox="0 0 24 24"
            :fill="isLiked ? 'currentColor' : 'none'"
            stroke="currentColor"
            aria-hidden="true"
          >
            <path
              d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"
              stroke-width="1.8"
            />
          </svg>
          <span class="st-num">{{ comment.likeTotal }}</span>
        </button>

        <!-- 回复 -->
        <button type="button" class="comment-action" @click="onReplyClick">
          <svg class="comment-action__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
            <path
              d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"
              stroke-width="1.8"
            />
          </svg>
          <span>回复</span>
        </button>
      </div>

      <!-- 子评论区域 -->
      <template v-if="comment.childCommentTotal > 0">
        <div v-if="comment.childComments && comment.childComments.length > 0" class="comment-item__children">
          <CommentItem
            v-for="(childComment, index) in comment.childComments"
            :key="index"
            :comment="childComment"
            :is-reply="true"
            @reply="$emit('reply', $event)"
            @like="$emit('like', $event)"
          />
        </div>

        <button
          v-if="
            comment.childCommentTotal > 1 &&
            (!comment.childComments || comment.childComments.length < comment.childCommentTotal) &&
            comment.hasMoreChildComments !== false
          "
          type="button"
          class="comment-item__more"
          @click="handleExpandReplies(comment)"
        >
          {{ comment.childComments?.length > 1 ? '展开更多回复' : `展开 ${comment.childCommentTotal - 1} 条回复` }}
        </button>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import ImagePreview from '@/components/common/ImagePreview.vue'
import UserPopover from '@/components/common/UserPopover.vue'

const props = defineProps({
  comment: {
    type: Object,
    required: true
  },
  isReply: {
    type: Boolean,
    default: false
  }
})

const showPreview = ref(false)
const isLiked = ref(false)
const localLikes = ref(Number(props.comment.likeTotal) || 0)

// 计算显示的点赞数
const displayLikes = computed(() => {
  return isLiked.value ? localLikes.value + 1 : localLikes.value
})

// 修改 emit 定义，添加 like 事件
const emit = defineEmits(['reply', 'expand-replies', 'like'])

// 修改点赞切换函数
const toggleLike = () => {
  isLiked.value = !isLiked.value
  // 触发点赞事件，传递整个 comment 对象和点赞状态
  emit('like', { comment: props.comment, liked: isLiked.value })
}

// 点击回复
const onReplyClick = () => {
  emit('reply', props.comment)
}

// 添加展开回复的点击处理
const handleExpandReplies = (comment) => {
  emit('expand-replies', comment)
}
</script>

<style scoped>
.comment-item {
  display: flex;
  gap: 12px;
  padding: 10px 0;
}

.comment-item--reply {
  padding: 6px 0;
}

.comment-item:not(.comment-item--reply) {
  margin-bottom: 8px;
}

.comment-item__avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-line);
  background: var(--color-canvas-sunken);
  object-fit: cover;
  cursor: pointer;
}

.comment-item--reply .comment-item__avatar {
  width: 28px;
  height: 28px;
}

.comment-item__body {
  flex: 1;
  min-width: 0;
}

.comment-item__name {
  font-size: 13px;
  line-height: 18px;
  color: var(--color-ink-soft);
  cursor: pointer;
  transition: color var(--motion-fast) var(--ease-standard);
}

.comment-item__name:hover {
  color: var(--color-ink);
}

.comment-item__content {
  margin: 4px 0 0;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-ink);
  word-break: break-word;
}

.comment-item__reply {
  color: var(--color-ink-soft);
}

.comment-item__image img {
  margin-top: 8px;
  width: 120px;
  border-radius: var(--radius-control);
  object-fit: cover;
  cursor: zoom-in;
  transition: opacity var(--motion-fast) var(--ease-standard);
}

.comment-item__image img:hover {
  opacity: 0.88;
}

.comment-item__time {
  margin: 8px 0;
  font-size: 12px;
  line-height: 16px;
  color: var(--color-ink-faint);
}

.comment-item__actions {
  display: flex;
  align-items: center;
  gap: 20px;
  font-size: 12px;
  color: var(--color-ink-faint);
}

.comment-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 0;
  border: none;
  background: none;
  color: inherit;
  font-size: 12px;
  line-height: 16px;
  cursor: pointer;
  transition: color var(--motion-fast) var(--ease-standard);
}

.comment-action:hover {
  color: var(--color-ink);
}

.comment-action--liked,
.comment-action--liked:hover {
  color: var(--color-brand);
}

.comment-action__icon {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
}

.comment-action span {
  min-width: 1.5em;
  display: inline-block;
  text-align: left;
}

.comment-item__children {
  margin-top: 8px;
}

.comment-item__more {
  margin-top: 8px;
  padding: 0;
  border: none;
  background: none;
  font-size: 13px;
  font-weight: 500;
  color: var(--color-ink-soft);
  cursor: pointer;
  transition: color var(--motion-fast) var(--ease-standard);
}

.comment-item__more:hover {
  color: var(--color-ink);
}

@keyframes like {
  0% {
    transform: scale(1);
  }
  25% {
    transform: scale(0.8);
  }
  50% {
    transform: scale(1.2);
  }
  75% {
    transform: scale(0.95);
  }
  100% {
    transform: scale(1);
  }
}

@keyframes unlike {
  0% {
    transform: scale(1);
  }
  25% {
    transform: scale(0.9);
  }
  50% {
    transform: scale(1.1);
  }
  75% {
    transform: scale(0.95);
  }
  100% {
    transform: scale(1);
  }
}

.animate-like {
  animation: like 0.4s var(--ease-standard);
  transform-origin: center;
  animation-fill-mode: forwards;
}

.animate-unlike {
  animation: unlike 0.4s var(--ease-standard);
  transform-origin: center;
  animation-fill-mode: forwards;
}

/* 新评论动画效果 */
.new-comment-animation {
  animation: highlightNewComment 2s ease-out forwards;
}

@keyframes highlightNewComment {
  0% {
    background-color: var(--color-canvas-sunken);
  }
  100% {
    background-color: transparent;
  }
}

@media (prefers-reduced-motion: reduce) {
  .animate-like,
  .animate-unlike,
  .new-comment-animation {
    animation: none;
  }
}
</style>

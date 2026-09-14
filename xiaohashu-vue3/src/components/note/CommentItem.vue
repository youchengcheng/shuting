<template>
  <div ref="rootRef" class="comment-item" :class="{ 'comment-item--reply': isReply, 'new-comment-animation': comment.isNewComment }">
    <img :src="comment.avatar" class="comment-item__avatar" :alt="comment.nickname" />

    <div class="comment-item__body" :class="{ 'comment-item__body--has-more': canDelete }">
      <!-- 评论者信息 -->
      <span class="comment-item__name">{{ comment.nickname }}</span>

      <!-- 更多操作：仅本人的评论展示，右上角「…」 -->
      <div v-if="canDelete" class="comment-item__more-wrap">
        <button
          type="button"
          class="comment-item__more-btn"
          :class="{ 'comment-item__more-btn--open': menuOpen }"
          :aria-expanded="menuOpen ? 'true' : 'false'"
          aria-label="更多操作"
          @click.stop="toggleMenu"
        >
          <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <circle cx="5.5" cy="12" r="1.6" />
            <circle cx="12" cy="12" r="1.6" />
            <circle cx="18.5" cy="12" r="1.6" />
          </svg>
        </button>

        <Transition name="comment-menu">
          <div v-if="menuOpen" class="comment-item__menu" role="menu" @click.stop>
            <button
              type="button"
              class="comment-item__menu-item comment-item__menu-item--danger"
              role="menuitem"
              @click="onDeleteClick"
            >
              删除评论
            </button>
          </div>
        </Transition>
      </div>

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
          <span class="comment-action__label st-num">{{ displayLikes > 0 ? displayLikes : '赞' }}</span>
        </button>

        <!-- 回复 -->
        <button type="button" class="comment-action" @click="onReplyClick">
          <svg class="comment-action__icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" aria-hidden="true">
            <path
              d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z"
              stroke-width="1.8"
            />
          </svg>
          <span class="comment-action__label">回复</span>
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
            :current-user-id="currentUserId"
            @reply="$emit('reply', $event)"
            @like="$emit('like', $event)"
            @delete="$emit('delete', $event)"
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
import { ref, computed, watch, onBeforeUnmount } from 'vue'
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
  },
  // 当前登录用户 ID：用于判断是否展示删除按钮
  currentUserId: {
    type: [String, Number],
    default: null
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
const emit = defineEmits(['reply', 'expand-replies', 'like', 'delete'])

// 仅本人可删除自己的评论（一级、二级评论通用）
const canDelete = computed(() => {
  const currentId = props.currentUserId
  const ownerId = props.comment?.userId
  if (currentId === null || currentId === undefined || currentId === '') return false
  if (ownerId === null || ownerId === undefined || ownerId === '') return false
  return String(ownerId) === String(currentId)
})

// 「…」操作菜单
const rootRef = ref(null)
const menuOpen = ref(false)

const closeMenu = () => {
  menuOpen.value = false
}

const toggleMenu = () => {
  menuOpen.value = !menuOpen.value
}

// 点击「删除评论」：先收起菜单，再由父组件弹出二次确认
const onDeleteClick = () => {
  closeMenu()
  emit('delete', props.comment)
}

const onDocumentClick = (event) => {
  if (rootRef.value && !rootRef.value.contains(event.target)) closeMenu()
}

const onPageScroll = () => closeMenu()

const onKeydown = (event) => {
  if (event.key === 'Escape') closeMenu()
}

// 菜单展开时才挂全局监听：点击空白、页面滚动、Esc 都收起菜单
watch(menuOpen, (open) => {
  if (open) {
    document.addEventListener('click', onDocumentClick)
    window.addEventListener('scroll', onPageScroll, true)
    window.addEventListener('keydown', onKeydown)
  } else {
    document.removeEventListener('click', onDocumentClick)
    window.removeEventListener('scroll', onPageScroll, true)
    window.removeEventListener('keydown', onKeydown)
  }
})

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  window.removeEventListener('scroll', onPageScroll, true)
  window.removeEventListener('keydown', onKeydown)
})

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
  position: relative;
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

/* 右上角「…」更多操作：仅本人的评论展示 */
.comment-item__more-wrap {
  position: absolute;
  top: 0;
  right: 0;
  z-index: 3;
}

.comment-item__body--has-more .comment-item__name {
  padding-right: 28px;
}

.comment-item__more-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-canvas-sunken);
  color: var(--color-ink-soft);
  cursor: pointer;
  transition: background var(--motion-fast) var(--ease-standard), color var(--motion-fast) var(--ease-standard);
}

.comment-item__more-btn:hover,
.comment-item__more-btn--open {
  background: var(--color-line-strong);
  color: var(--color-ink);
}

.comment-item__more-btn svg {
  width: 16px;
  height: 16px;
}

.comment-item__menu {
  position: absolute;
  right: 0;
  top: 30px;
  min-width: 128px;
  padding: 6px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: var(--color-paper);
  box-shadow: var(--shadow-panel);
}

.comment-item__menu-item {
  display: block;
  width: 100%;
  padding: 8px 12px;
  border: none;
  border-radius: var(--radius-control);
  background: none;
  color: var(--color-ink);
  font-size: 14px;
  line-height: 20px;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  transition: background var(--motion-fast) var(--ease-standard), color var(--motion-fast) var(--ease-standard);
}

.comment-item__menu-item--danger {
  color: var(--color-brand);
}

.comment-item__menu-item--danger:hover {
  background: rgb(255 36 66 / 0.08);
}

.comment-menu-enter-active {
  transition:
    opacity 180ms var(--ease-standard),
    transform 180ms var(--ease-standard);
}

.comment-menu-leave-active {
  transition:
    opacity 120ms var(--ease-standard),
    transform 120ms var(--ease-standard);
}

.comment-menu-enter-from,
.comment-menu-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.97);
}

.comment-action__icon {
  flex-shrink: 0;
  width: 16px;
  height: 16px;
}

.comment-action__label {
  display: inline-block;
  min-width: 1.5em;
  text-align: left;
  white-space: nowrap;
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

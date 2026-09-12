<template>
  <div class="comment-list" @scroll="handleScroll" ref="commentListRef">
    <!-- 只在有评论时显示评论数量 -->
    <div v-if="comments && comments.length > 0" class="comment-list__count st-num">
      共 {{ total }} 条评论
    </div>

    <!-- 评论列表 -->
    <div>
      <CommentItem
        v-for="comment in comments"
        :key="comment.commentId"
        :comment="comment"
        @reply="$emit('reply', $event)"
        @expand-replies="$emit('expand-replies', $event)"
        @like="$emit('like', $event)"
      />
    </div>

    <!-- 空评论状态 -->
    <EmptyState
      v-if="comments === null || comments.length === 0"
      compact
      title="这是一片荒地"
      description="还没有人评论，来说说你的想法"
    >
      <button type="button" class="st-btn st-btn-ghost" @click="onClickComment">写下第一条评论</button>
    </EmptyState>

    <!-- 到底了提示 -->
    <div v-if="comments && comments.length > 0 && !hasMore" class="comment-list__end">到底了</div>
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import CommentItem from './CommentItem.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const props = defineProps({
  comments: {
    type: Array,
    default: () => []
  },
  total: {
    type: [Number, String],
    default: '0'
  },
  hasMore: {
    type: Boolean,
    default: false
  },
  moreCount: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['load-more', 'click-comment', 'reply', 'expand-replies', 'like'])
const commentListRef = ref(null)

// 处理滚动事件
const handleScroll = (e) => {
  if (!props.comments.length || !props.hasMore) return

  const container = e.target
  const scrollTop = container.scrollTop
  const scrollHeight = container.scrollHeight
  const clientHeight = container.clientHeight

  // 当滚动到距离底部 50px 时触发加载
  if (scrollHeight - scrollTop - clientHeight < 50) {
    emit('load-more')
  }
}

// 组件挂载时添加滚动监听
onMounted(() => {
  if (commentListRef.value) {
    commentListRef.value.addEventListener('scroll', handleScroll)
  }
})

// 组件卸载时移除滚动监听
onBeforeUnmount(() => {
  if (commentListRef.value) {
    commentListRef.value.removeEventListener('scroll', handleScroll)
  }
})

// 点击评论处理
const onClickComment = () => {
  emit('click-comment')
}
</script>

<style scoped>
.comment-list {
  /* 与笔记正文左右对齐：评论列表整块缩进 24px */
  padding: 16px 24px;
}

.comment-list__count {
  margin-bottom: 12px;
  font-size: 13px;
  color: var(--color-ink-faint);
}

.comment-list__end {
  position: relative;
  margin: 28px 0 16px;
  text-align: center;
  font-size: 12px;
  color: var(--color-ink-faint);
}

.comment-list__end::before,
.comment-list__end::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 64px;
  height: 1px;
  background: var(--color-line);
}

.comment-list__end::before {
  left: 50%;
  transform: translateX(-80px);
}

.comment-list__end::after {
  left: 50%;
  transform: translateX(16px);
}
</style>

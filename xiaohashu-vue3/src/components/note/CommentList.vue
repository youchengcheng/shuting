<template>
  <div class="comment-list" @scroll="handleScroll" ref="commentListRef">
    <p v-if="loading" class="comment-list__status" role="status">正在加载评论…</p>
    <p v-else-if="error" class="comment-list__status" role="alert">{{ error }} <button type="button" class="st-btn st-btn-ghost" @click="$emit('retry')">重试</button></p>
    <p v-if="locating" class="comment-list__status" role="status">正在定位相关评论…</p>
    <p v-else-if="locationError" class="comment-list__status" role="status">{{ locationError }} <button type="button" class="st-btn st-btn-ghost" @click="$emit('retry-target')">重试</button></p>
    <p v-else-if="unavailableReason" class="comment-list__status" role="status">相关评论已删除或不可查看</p>
    <!-- 只在有评论时显示评论数量 -->
    <div v-if="comments && comments.length > 0" class="comment-list__count st-num">
      共 {{ total }} 条评论
    </div>

    <!-- 评论列表 -->
    <div>
      <template v-for="section in sections" :key="section.pageNo">
        <div v-if="section.firstMissingPage" class="comment-list__gap">
          <button
            type="button"
            class="st-btn st-btn-ghost"
            :disabled="loadingMore"
            @click="$emit('load-page', section.firstMissingPage)"
          >{{ loadingMore ? '正在加载…' : '查看更多评论' }}</button>
        </div>
        <CommentItem
          v-for="comment in section.comments"
          :key="comment.commentId"
          :comment="comment"
          :current-user-id="currentUserId"
          :target-comment-id="targetCommentId"
          @reply="$emit('reply', $event)"
          @expand-replies="$emit('expand-replies', $event)"
          @like="$emit('like', $event)"
          @delete="$emit('delete', $event)"
        />
      </template>
    </div>

    <!-- 空评论状态 -->
    <EmptyState
      v-if="!loading && !error && (!comments || comments.length === 0)"
      compact
      title="这是一片荒地"
      description="还没有人评论，来说说你的想法"
    >
      <button type="button" class="st-btn st-btn-ghost" @click="onClickComment">写下第一条评论</button>
    </EmptyState>

    <!-- 到底了提示 -->
    <div v-if="comments && comments.length > 0" class="comment-list__end">
      <button
        v-if="hasMore"
        type="button"
        class="st-btn st-btn-ghost"
        :disabled="loadingMore"
        @click="$emit('load-more')"
      >{{ loadingMore ? '正在加载…' : '加载更多评论' }}</button>
      <span v-else>到底了</span>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { commentPageSections } from '@/utils/commentContext'
import CommentItem from './CommentItem.vue'
import EmptyState from '@/components/common/EmptyState.vue'

const props = defineProps({
  paging: {
    type: Object,
    default: null
  },
  targetCommentId: {
    type: String,
    default: ''
  },
  loading: Boolean,
  loadingMore: Boolean,
  locating: Boolean,
  locationError: {
    type: String,
    default: ''
  },
  error: {
    type: String,
    default: ''
  },
  unavailableReason: {
    type: String,
    default: ''
  },
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
  },
  // 当前登录用户 ID：透传给评论项，用于展示删除按钮
  currentUserId: {
    type: [String, Number],
    default: null
  }
})

const emit = defineEmits(['load-more', 'load-page', 'click-comment', 'reply', 'expand-replies', 'like', 'delete', 'retry', 'retry-target'])
const commentListRef = ref(null)
const sections = computed(() => commentPageSections(props.paging, props.comments))

// 处理滚动事件
const handleScroll = (e) => {
  if (!props.comments?.length || !props.hasMore || props.loadingMore) return

  const container = e.target
  const scrollTop = container.scrollTop
  const scrollHeight = container.scrollHeight
  const clientHeight = container.clientHeight

  // 当滚动到距离底部 50px 时触发加载
  if (scrollHeight - scrollTop - clientHeight < 50) {
    emit('load-more')
  }
}

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
.comment-list__status {
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-ink-soft);
}

.comment-list__gap {
  display: flex;
  justify-content: center;
  margin: 12px 0;
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

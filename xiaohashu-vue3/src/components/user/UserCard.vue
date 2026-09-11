<template>
  <div class="user-card">
    <!-- 左侧用户信息 -->
    <router-link :to="`/user/profile/${user.userId}`" class="user-card__main">
      <img :src="user.avatar" class="user-card__avatar" :alt="user.nickname" />

      <div class="user-card__info">
        <h3 class="user-card__name">{{ user.nickname }}</h3>

        <p v-if="type === 'user'" class="user-card__meta st-num">书亭号：{{ user.xiaohashuId }}</p>

        <p v-if="type === 'user' || type === 'fans'" class="user-card__meta st-num">
          粉丝 {{ user.fansTotal || 0 }}<span class="user-card__sep">·</span>笔记 {{ user.noteTotal || 0 }}
        </p>

        <p v-if="type === 'following'" class="user-card__meta user-card__meta--clamp">
          {{ user.introduction || '此用户还未填写简介' }}
        </p>
      </div>
    </router-link>

    <!-- 右侧关注按钮 -->
    <button
      type="button"
      class="st-btn user-card__follow"
      :class="user.isLiked ? 'st-btn-ghost' : 'st-btn-primary'"
      @click="handleFollow"
    >
      {{ user.isLiked ? '已关注' : '关注' }}
    </button>
  </div>
</template>

<script setup>
import { defineProps, defineEmits } from 'vue'
import { useUserStore } from '@/stores/user'

const props = defineProps({
  type: {
    type: String,
    required: true,
    default: 'user'
  },
  user: {
    type: Object,
    required: true,
    default: () => ({
      userId: '',
      nickname: '',
      avatar: '',
      xiaohashuId: '',
      fansTotal: 0,
      noteTotal: 0,
      isLiked: true,
      introduction: ''
    })
  }
})

const emit = defineEmits(['follow'])
const userStore = useUserStore()

// 处理关注按钮点击
const handleFollow = () => {
  // 检查用户是否已登录
  if (!userStore.token) {
    // 可以触发登录弹窗
    emit('login-required')
    return
  }
  
  // 触发关注事件
  emit('follow', props.user.userId)
}

</script>

<style scoped>
.user-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 16px 20px;
  border-radius: var(--radius-card);
  transition: background-color var(--motion-fast) var(--ease-standard);
}

.user-card:hover {
  background: var(--color-canvas-sunken);
}

.user-card__main {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.user-card__avatar {
  flex-shrink: 0;
  width: 64px;
  height: 64px;
  border-radius: var(--radius-pill);
  border: 1px solid var(--color-line);
  background: var(--color-canvas-sunken);
  object-fit: cover;
}

.user-card__info {
  min-width: 0;
  margin-left: 16px;
}

.user-card__name {
  font-size: 16px;
  font-weight: 600;
  line-height: 1.4;
  color: var(--color-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.user-card__meta {
  margin-top: 4px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--color-ink-faint);
}

.user-card__meta--clamp {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.user-card__sep {
  margin: 0 8px;
  color: var(--color-line);
}

.user-card__follow {
  flex-shrink: 0;
  min-width: 88px;
}
</style>

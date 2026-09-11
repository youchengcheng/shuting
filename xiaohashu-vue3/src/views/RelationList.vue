<template>
  <div class="relation-page">
    <!-- 页头 -->
    <header class="page-header">
      <h1 class="st-page-title">{{ nickname || '关注与粉丝' }}</h1>
      <p class="page-header__desc">书亭号 {{ userId }}</p>
    </header>

    <!-- 关注 / 粉丝 切换 -->
    <div class="relation-tabs" role="tablist">
      <button
        type="button"
        role="tab"
        class="relation-tab"
        :class="{ 'relation-tab--active': activeTab === 'following' }"
        :aria-selected="activeTab === 'following'"
        @click="activeTab = 'following'"
      >
        关注
      </button>
      <button
        type="button"
        role="tab"
        class="relation-tab"
        :class="{ 'relation-tab--active': activeTab === 'followers' }"
        :aria-selected="activeTab === 'followers'"
        @click="activeTab = 'followers'"
      >
        粉丝
      </button>
    </div>

    <!-- 用户列表 -->
    <div class="relation-panel">
      <LoadingSpinner ref="loadingRef" />

      <EmptyState
        v-if="users.length === 0"
        :title="activeTab === 'following' ? '暂未关注其他用户' : '暂无粉丝'"
        description="遇到喜欢的创作者，关注一下就能在这里找到"
      />

      <template v-else>
        <div v-for="user in users" :key="user.userId" class="relation-item">
          <UserCard
            :user="user"
            :type="listType"
            @follow="handleFollowUser"
            @login-required="handleLoginRequired"
          />
        </div>

        <!-- 加载更多 -->
        <div v-if="hasMore" class="load-more-row">
          <button type="button" class="st-btn st-btn-ghost" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? '加载中…' : '加载更多' }}
          </button>
        </div>

        <!-- 底线提示 -->
        <div class="bottom-line">
          <span class="bottom-line__rule"></span>
          <span class="bottom-line__text">书亭是有底线的</span>
          <span class="bottom-line__rule"></span>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, watch, onUnmounted, inject } from 'vue'
import { useRoute } from 'vue-router'
import UserCard from '@/components/user/UserCard.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { getFollowingList, getFansList, followUser, unfollowUser } from '@/api/relation'
import { message } from '@/utils/message'
import LoadingSpinner from '@/components/common/LoadingSpinner.vue'

const route = useRoute()
const userId = ref(route.params.userId)
const nickname = ref('')
const activeTab = ref(route.query.tab || 'following')
const users = ref([])
const loading = ref(true)
const loadingMore = ref(false)
const hasMore = ref(true)
const pageNo = ref(1)
const pageSize = 20
const listType = ref('following')

const loadingRef = ref(null)
const showLoginModal = inject('showLoginModal', null)

// 未登录时唤起登录弹窗
const handleLoginRequired = () => {
  if (typeof showLoginModal === 'function') {
    showLoginModal()
  }
}

// 获取关注列表
const fetchFollowingList = async (isLoadMore = false) => {
  if (isLoading.value) return
  
  if (isLoadMore) {
    loadingMore.value = true
  } else {
    loading.value = true
    // loadingRef.value?.show()
    users.value = []
    pageNo.value = 1
  }
  
  try {
    const res = await getFollowingList(userId.value, pageNo.value, pageSize)
    if (res.success) {
      const newUsers = res.data || []
      
      // 处理数据，添加 isLiked 字段
      const processedUsers = newUsers.map(user => ({
        ...user,
        isLiked: true // 关注列表中的用户默认都是已关注的
      }))
      
      if (isLoadMore) {
        users.value = [...users.value, ...processedUsers]
      } else {
        users.value = processedUsers
      }
      
      hasMore.value = newUsers.length === pageSize
      pageNo.value++
    }
  } catch (error) {
    console.error('获取关注列表失败', error)
  } finally {
    loading.value = false
    loadingMore.value = false
    // loadingRef.value?.hide()
  }
}

// 获取粉丝列表
const fetchFollowersList = async (isLoadMore = false) => {
  if (isLoading.value) return
  
  if (isLoadMore) {
    loadingMore.value = true
  } else {
    loading.value = true
    // loadingRef.value?.show()
    users.value = []
    pageNo.value = 1
  }
  
  try {
    const res = await getFansList(userId.value, pageNo.value, pageSize)
    if (res.success) {
      const newUsers = res.data || []
      
      // 处理数据，添加 isLiked 字段（粉丝可能已关注也可能未关注）
      const processedUsers = newUsers.map(user => ({
        ...user,
        isLiked: user.isLiked || false
      }))
      
      if (isLoadMore) {
        users.value = [...users.value, ...processedUsers]
      } else {
        users.value = processedUsers
      }
      
      hasMore.value = newUsers.length === pageSize
      pageNo.value++
    }
  } catch (error) {
    console.error('获取粉丝列表失败', error)
  } finally {
    loading.value = false
    loadingMore.value = false
    // loadingRef.value?.hide()
  }
}

// 加载更多
const loadMore = () => {
  if (activeTab.value === 'following') {
    fetchFollowingList(true)
  } else {
    fetchFollowersList(true)
  }
}

// 处理关注/取消关注
const handleFollowUser = (followUserId) => {
  const userIndex = users.value.findIndex(user => user.userId === followUserId)
  if (userIndex === -1) return

  const user = users.value[userIndex]
  const request = user.isLiked ? unfollowUser(followUserId) : followUser(followUserId)
  request.then(res => {
    if (!res.success) {
      message.show(res.message)
      return
    }
    
    // 更新用户关注状态
    user.isLiked = !user.isLiked
    message.show(user.isLiked ? '关注成功' : '取消关注成功')
  })
}

// 添加滚动加载功能
const isLoading = ref(false)

// 处理滚动事件
const handleScroll = () => {
  if (loading.value || loadingMore.value || !hasMore.value) return
  
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  const windowHeight = window.innerHeight
  const documentHeight = document.documentElement.scrollHeight
  
  // 当滚动到距离底部一定距离时加载更多
  if (documentHeight - scrollTop - windowHeight < 200) {
    loadMore()
  }
}

// 监听标签变化
watch(activeTab, (newTab) => {
  if (newTab === 'following') {
    listType.value = 'following'
    fetchFollowingList()
  } else {
    listType.value = 'fans'
    fetchFollowersList()
  }
})

onMounted(() => {
  // 添加滚动事件监听
  window.addEventListener('scroll', handleScroll)
  
  if (activeTab.value === 'following') {
    fetchFollowingList()
  } else {
    fetchFollowersList()
  }
})

onUnmounted(() => {
  // 移除滚动事件监听
  window.removeEventListener('scroll', handleScroll)
})
</script>

<style scoped>
.relation-page {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.page-header__desc {
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-ink-faint);
}

.relation-tabs {
  display: inline-flex;
  align-self: flex-start;
  gap: 4px;
  padding: 4px;
  border-radius: var(--radius-pill);
  background: var(--color-canvas-sunken);
}

.relation-tab {
  height: 36px;
  padding: 0 24px;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  font-size: 14px;
  color: var(--color-ink-soft);
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}

.relation-tab:hover {
  color: var(--color-ink);
}

.relation-tab--active {
  background: var(--color-paper);
  color: var(--color-ink);
  font-weight: 600;
  box-shadow: var(--shadow-card);
}

.relation-panel {
  padding: 8px 8px 24px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-panel);
  background: var(--color-paper);
}

.relation-item + .relation-item {
  border-top: 1px solid var(--color-line);
}

.load-more-row {
  display: flex;
  justify-content: center;
  padding: 16px 0 8px;
}

.bottom-line {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 24px 0 8px;
  color: var(--color-ink-faint);
}

.bottom-line__rule {
  width: 64px;
  height: 1px;
  background: var(--color-line);
}

.bottom-line__text {
  font-size: 13px;
}
</style>

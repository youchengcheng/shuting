<template>
  <div class="search-page">
    <header class="page-header">
      <h2 class="st-page-title">搜索「{{ keyword }}」</h2>
    </header>

    <!-- 结果分类 -->
    <div class="search-tabs">
      <div class="search-tabs__inner">
        <div class="search-tabs__row">
          <nav class="search-tabs__nav">
            <button
              v-for="category in categories"
              :key="category.name"
              type="button"
              class="search-tab"
              :class="{ 'search-tab--active': selectedType === category.type }"
              @click="handleTabChange(category.type)"
            >
              {{ category.name }}
            </button>
          </nav>

          <div v-if="selectedType !== 2" class="filter-entry">
            <button
              type="button"
              class="filter-entry__trigger"
              :aria-expanded="showFilter ? 'true' : 'false'"
              @click="showFilter = !showFilter"
            >
              <svg class="filter-entry__icon" viewBox="0 0 24 24" fill="none" aria-hidden="true">
                <path d="M4 6h16M7 12h10M10 18h4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
              </svg>
              <span>筛选</span>
            </button>
          </div>

          <!-- 筛选弹出框 -->
          <div v-if="showFilter" ref="filterRef" class="filter-panel">
            <div class="space-y-5">
              <!-- 排序依据 -->
              <div>
                <div class="text-[13px] font-medium mb-3 text-ink-faint">排序依据</div>
                <div class="grid grid-cols-3 gap-2">
                  <button 
                    v-for="sort in sortOptions" 
                    :key="sort.value"
                    class="h-9 rounded-control text-sm inline-flex items-center justify-center transition-colors"
                    :class="selectedSort === sort.value ? 'bg-canvas-deep text-ink font-medium' : 'bg-canvas-sunken text-ink-soft hover:bg-canvas-deep'"
                    @click="selectedSort = sort.value"
                  >
                    <div class="flex items-center justify-center">
                      <span>{{ sort.label }}</span>
                      <svg 
                        v-if="selectedSort === sort.value"
                        class="w-3 h-3 ml-1" 
                        viewBox="0 0 24 24" 
                        fill="none" 
                        stroke="currentColor"
                      >
                        <path d="M20 6L9 17l-5-5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                      </svg>
                    </div>
                  </button>
                </div>
              </div>

              <!-- 笔记类型 -->
              <div>
                <div class="text-[13px] font-medium mb-3 text-ink-faint">笔记类型</div>
                <div class="grid grid-cols-3 gap-2">
                  <button 
                    v-for="type in noteTypes" 
                    :key="type.value"
                    class="h-9 rounded-control text-sm inline-flex items-center justify-center transition-colors"
                    :class="selectedType === type.value ? 'bg-canvas-deep text-ink font-medium' : 'bg-canvas-sunken text-ink-soft hover:bg-canvas-deep'"
                    @click="selectedType = type.value"
                  >
                    <div class="flex items-center justify-center">
                      <span>{{ type.label }}</span>
                      <svg 
                        v-if="selectedType === type.value"
                        class="w-3 h-3 ml-1" 
                        viewBox="0 0 24 24" 
                        fill="none" 
                        stroke="currentColor"
                      >
                        <path d="M20 6L9 17l-5-5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                      </svg>
                    </div>
                  </button>
                </div>
              </div>

              <!-- 发布时间 -->
              <div>
                <div class="text-[13px] font-medium mb-3 text-ink-faint">发布时间</div>
                <div class="grid grid-cols-3 gap-2">
                  <button 
                    v-for="time in timeFilters" 
                    :key="time.value"
                    class="h-9 rounded-control text-sm inline-flex items-center justify-center transition-colors"
                    :class="selectedTime === time.value ? 'border border-ink text-ink bg-paper font-medium' : 'bg-canvas-sunken text-ink-soft hover:bg-line'"
                    @click="selectedTime = time.value"
                  >
                    <div class="flex items-center justify-center">
                      <span>{{ time.label }}</span>
                      <svg 
                        v-if="selectedTime === time.value"
                        class="w-3 h-3 ml-1" 
                        viewBox="0 0 24 24" 
                        fill="none" 
                        stroke="currentColor"
                      >
                        <path d="M20 6L9 17l-5-5" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                      </svg>
                    </div>
                  </button>
                </div>
              </div>

              <!-- 底部按钮 -->
              <div class="flex gap-2 pt-2">
                <button 
                  class="st-btn st-btn-ghost flex-1 h-11"
                  @click="resetFilters"
                >
                  重置
                </button>
                <button 
                  class="st-btn st-btn-primary flex-1 h-11"
                  @click="applyFilters"
                >
                  确定
                </button>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>


    <!-- 搜索结果内容 -->
    <div class="search-results">
      <NoteWaterfall
        v-if="activeTab !== 'users'"
        :notes="searchResults"
        :loading="loading"
        :loading-more="loadingMore"
        :has-more="hasMore"
        @note-click="onNoteClick"
        @load-more="loadMore"
      />

      <!-- 用户列表 -->
      <div v-else class="user-list">
        <UserCard
          v-for="user in searchResults"
          :key="user.id || user.userId"
          :user="user"
          @follow="handleFollowUser"
          @login-required="handleLoginRequired"
        />
      </div>

      <EmptyState
        v-if="!loading && searchResults.length === 0"
        title="未搜索到相关结果"
        description="换个关键词，或减少筛选条件"
      />
    </div>

    <!-- 笔记详情浮层：以子路由渲染，关闭时只卸载浮层，搜索结果不会重新加载 -->
    <router-view v-slot="{ Component }">
      <Transition name="note-overlay">
        <component :is="Component" />
      </Transition>
    </router-view>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch, computed, inject } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import NoteWaterfall from '@/components/note/NoteWaterfall.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { onClickOutside } from '@vueuse/core'
import { searchNote, searchUser } from '@/api/search'
import { followUser, unfollowUser } from '@/api/relation'
import UserCard from '@/components/user/UserCard.vue'
import { message } from '@/utils/message'
import { useNoteTransition } from '@/composables/noteTransition'

const route = useRoute()
const router = useRouter()
const { openNote } = useNoteTransition()
const showLoginModal = inject('showLoginModal', null)
const activeTab = ref('notes') // 默认选中笔记标签



// 筛选相关状态
const showFilter = ref(false)
const filterRef = ref(null)
const selectedTime = ref(null)
const selectedSort = ref(null)
const selectedType = ref(null)

const handleTabChange = (type) => {
  selectedType.value = type
  if (type === 2) {
    activeTab.value = 'users'
  } else {
    activeTab.value = 'others'
  }
  performSearch(true)
}

const categories = [
  { name: '全部', type: null },
  { name: '图文', type: 0 },
  { name: '视频', type: 1 },
  { name: '用户', type: 2 },
]

// 排序方式选项
const sortOptions = [
  { label: '综合', value: null },
  { label: '最新', value: 0 },
  { label: '最多点赞', value: 1 },
  { label: '最多收藏', value: 2 },
  { label: '最多评论', value: 3 }
]

// 笔记类型选项
const noteTypes = [
  { label: '不限', value: null },
  { label: '图文', value: 0 },
  { label: '视频', value: 1 },
]

// 时间筛选选项
const timeFilters = [
  { label: '不限', value: null },
  { label: '一天内', value: 0 },
  { label: '一周内', value: 1 },
  { label: '半年内', value: 2 }
]

// 使用 vueuse 的 onClickOutside
onClickOutside(filterRef, () => {
  showFilter.value = false
})

// 重置筛选
const resetFilters = () => {
  selectedTime.value = null
  selectedSort.value = null
  selectedType.value = null
}

// 应用筛选
const applyFilters = () => {
  // 应用排序 / 类型 / 时间三类筛选条件后重新搜索
  showFilter.value = false
  performSearch(true)
}

// 从路由中获取搜索关键词
const keyword = computed(() => route.query.keyword || '')

const searchResults = ref([])
const isLoading = ref(false) // 是否有请求在途
const hasMore = ref(true)
const currPageNo = ref(1)
const loading = ref(false) // 首屏加载：骨架屏
const loadingMore = ref(false) // 追加加载：底部加载行

// 执行搜索
const performSearch = (isFirstPage = true) => {
  if (isLoading.value) return

  isLoading.value = true

  // 如果是第一页，重置数据
  if (isFirstPage) {
    loading.value = true
    searchResults.value = []
    currPageNo.value = 1
    hasMore.value = true
  } else {
    loadingMore.value = true
  }
  
  // 根据当前选中的标签决定调用哪个搜索 API
  if (activeTab.value === 'users') {
    // 用户搜索
    searchUser(
      keyword.value, 
      currPageNo.value
    ).then(res => {
      if (res.success) {
        const newResults = (res.data || []).map(item => ({
          ...item,
          id: item.id ?? item.userId,
          isLiked: Boolean(item.isLiked)
        }))
        
        // 检查是否有重复数据
        const existingIds = searchResults.value.map(item => item.id ?? item.userId)
        const uniqueNewResults = newResults.filter(item => !existingIds.includes(item.id))
        
        // 添加不重复的数据
        if (uniqueNewResults.length > 0) {
          searchResults.value = [...searchResults.value, ...uniqueNewResults]
          currPageNo.value++
        }
        
        // 判断是否还有更多数据
        hasMore.value = newResults.length === 10
      } else {
        hasMore.value = false
      }
    }).finally(() => {
      isLoading.value = false
      loading.value = false
      loadingMore.value = false
    })
  } else {
    // // 笔记搜索 - 根据不同的标签设置不同的类型
    // let noteType = null
    // if (activeTab.value === 'images') {
    //   noteType = 0 // 图文类型
    // } else if (activeTab.value === 'videos') {
    //   noteType = 1 // 视频类型
    // }
    // 'all' 或其他情况保持 null，表示搜索所有类型
    searchNote(
      keyword.value, 
      selectedType.value, 
      selectedSort.value, 
      selectedTime.value, 
      currPageNo.value
    ).then(res => {
      if (res.success) {
        const newResults = (res.data || []).map(item => ({
          ...item,
          id: item.id ?? item.noteId
        }))
        
        // 检查是否有重复数据
        const existingIds = searchResults.value.map(item => item.id ?? item.noteId)
        const uniqueNewResults = newResults.filter(item => !existingIds.includes(item.id))
        
        // 添加不重复的数据
        if (uniqueNewResults.length > 0) {
          searchResults.value = [...searchResults.value, ...uniqueNewResults]
          currPageNo.value++
        }
        
        // 判断是否还有更多数据
        hasMore.value = newResults.length === 10
      } else {
        hasMore.value = false
      }
    }).finally(() => {
      isLoading.value = false
      loading.value = false
      loadingMore.value = false
    })
  }
}

// 监听路由参数变化，当搜索关键词变化时重新搜索
watch(() => route.query.keyword, (newKeyword, oldKeyword) => {
  if (newKeyword !== oldKeyword) {
    performSearch(true) // 重置并执行新搜索
  }
}, { immediate: true })

// 监听 activeTab 变化
watch(activeTab, () => {
  // 切换标签时重置并重新搜索
  performSearch(true)
})

// 加载更多结果
const loadMore = () => {
  if (!hasMore.value || isLoading.value) return
  performSearch(false)
}

// 处理滚动加载
const handleScroll = () => {
  if (isLoading.value || !hasMore.value) return
  
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  const windowHeight = window.innerHeight
  const documentHeight = document.documentElement.scrollHeight
  
  // 当滚动到距离底部一定距离时加载更多
  if (documentHeight - scrollTop - windowHeight < 200) {
    loadMore()
  }
}

// 组件挂载和卸载时添加/移除滚动事件监听
onMounted(() => {
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  window.removeEventListener('scroll', handleScroll)
})


// 点击笔记卡片：打开详情浮层（子路由），当前搜索结果保持挂载。
// 展开动画由 openNote 负责：封面从卡片原位扩张到详情媒体区
const onNoteClick = (note) => {
  const noteId = note.id ?? note.noteId
  if (!noteId) return
  openNote(note, {
    path: `${route.path}/note/${noteId}`,
    query: route.query
  })
}

// 处理用户关注/取消关注事件
const handleFollowUser = (userId) => {
  const user = searchResults.value.find(item => (item.id ?? item.userId) === userId)
  if (!user) return
  const request = user.isLiked ? unfollowUser(userId) : followUser(userId)
  request.then(res => {
    if (!res.success) {
      message.show(res.message)
      return
    }
    user.isLiked = !user.isLiked
    message.show(user.isLiked ? '关注成功' : '取消关注成功')
  })
}

// 处理登录需求
const handleLoginRequired = () => {
  if (showLoginModal) {
    showLoginModal.value = true
  }
}
</script>

<style scoped>
.search-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* 结果分类：跟频道条保持同一套胶囊语言 */
.search-tabs {
  position: sticky;
  top: var(--header-h);
  z-index: 20;
  height: 48px;
  display: flex;
  align-items: center;
  background: var(--color-canvas);
}

.search-tabs__inner {
  width: 100%;
}

.search-tabs__row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 12px;
}

.search-tabs__nav {
  display: flex;
  align-items: center;
  gap: 4px;
  flex: 1;
  min-width: 0;
  overflow-x: auto;
  padding: 6px 0;
  white-space: nowrap;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.search-tabs__nav::-webkit-scrollbar {
  display: none;
}

.search-tab {
  height: 32px;
  padding: 0 14px;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-ink-faint);
  font-size: 14px;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}

.search-tab:hover {
  background: rgb(0 0 0 / 0.045);
  color: var(--color-ink);
}

.search-tab--active {
  background: var(--color-canvas-deep);
  color: var(--color-ink);
  font-weight: 600;
}

.filter-entry {
  position: relative;
  flex-shrink: 0;
}

.filter-entry__trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 32px;
  padding: 0 12px;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-ink-soft);
  font-size: 14px;
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}

.filter-entry__trigger:hover {
  background: rgb(0 0 0 / 0.045);
  color: var(--color-ink);
}

.filter-entry__icon {
  width: 18px;
  height: 18px;
}

.filter-panel {
  position: absolute;
  right: 0;
  top: 44px;
  z-index: 50;
  width: 460px;
  max-width: calc(100vw - 40px);
  padding: 20px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-panel);
  background: var(--color-paper);
  box-shadow: var(--shadow-panel);
}

.search-results {
  min-height: 200px;
}

.user-list {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-panel);
  background: var(--color-paper);
}
</style>

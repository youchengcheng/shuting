<template>
  <div class="discover-page">
    <!-- 分类导航 -->
    <CategoryNav
      :active-channel-id="activeChannelId"
      @channel-change="handleChannelChange"
    />

    <!-- 瀑布流 -->
    <NoteWaterfall
      :notes="notes"
      :loading="loading"
      :loading-more="loadingMore"
      :has-more="hasMore"
      @note-click="onNoteClick"
      @load-more="loadMoreNotes"
    />

    <EmptyState
      v-if="!loading && notes.length === 0 && !error"
      title="该频道下暂无笔记"
      description="换个频道，或稍后再来看看"
    />

    <!-- 加载失败：错误态 + 重试 -->
    <EmptyState
      v-if="!loading && notes.length === 0 && error"
      title="数据加载失败"
      description="请检查网络后重试"
    >
      <button class="retry-btn" @click="retry">重新加载</button>
    </EmptyState>

    <!-- 笔记详情浮层：以子路由渲染，关闭时只卸载浮层，当前信息流不会重新加载。
         展开 / 收起动画由浮层自己驱动（见 composables/noteMorph.js），
         这里不再套 Transition，避免两套动画互相打断 -->
    <router-view />
  </div>
</template>

<script setup>
import CategoryNav from '@/components/layout/CategoryNav.vue'
import NoteWaterfall from '@/components/note/NoteWaterfall.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { ref, onMounted, watch, onBeforeUnmount } from 'vue'
import { getDiscoverNotePageList } from '@/api/note'
import { useNoteStore } from '@/stores/note'
import { useRoute, useRouter } from 'vue-router'
import { useNoteTransition } from '@/composables/noteTransition'


const route = useRoute()
const router = useRouter()
const noteStore = useNoteStore()
const { openNote } = useNoteTransition()

// 笔记数据
const notes = ref([])
const currPageNo = ref(1)
const hasMore = ref(true) // 是否有更多数据
const loading = ref(false) // 首屏加载：骨架屏
const loadingMore = ref(false) // 追加加载：底部加载行
const error = ref(false) // 加载是否失败

// 当前激活的频道ID
const activeChannelId = ref(0) // 默认为0，表示全部


// 从URL参数中获取频道ID
const getChannelIdFromRoute = () => {
  const channelId = route.query.channelId
  return channelId ? parseInt(channelId) : 0
}

// 加载笔记数据
const loadNotes = (channelId = 0, isFirstPage = true) => {
  if (loading.value || loadingMore.value) return

  if (isFirstPage) {
    loading.value = true
    currPageNo.value = 1
    notes.value = []
    hasMore.value = true
    error.value = false
  } else {
    loadingMore.value = true
  }

  // 调用API获取笔记列表
  getDiscoverNotePageList(channelId, currPageNo.value).then(res => {
    if (res.success) {
      const newNotes = res.data || []

      if (isFirstPage) {
        notes.value = newNotes
      } else {
        // 追加数据到现有列表末尾
        notes.value = [...notes.value, ...newNotes]
      }

      // 判断是否还有更多数据
      hasMore.value = newNotes.length > 0

      // 如果有数据返回，增加页码
      if (newNotes.length > 0) {
        currPageNo.value++
      }
    } else {
      // 业务失败：终止无限加载，避免哨兵反复触发死循环
      hasMore.value = false
      error.value = true
    }
  }).catch(() => {
    // 请求失败（网络/超时/5xx等）：终止无限加载
    hasMore.value = false
    error.value = true
  }).finally(() => {
    loading.value = false
    loadingMore.value = false
  })
}

// 重试加载
const retry = () => {
  error.value = false
  hasMore.value = true
  loadNotes(activeChannelId.value, true)
}

// 加载更多数据
const loadMoreNotes = () => {
  if (!hasMore.value || loading.value || loadingMore.value) return
  loadNotes(activeChannelId.value, false)
}

// 处理频道变更
const handleChannelChange = (channelId) => {
  activeChannelId.value = channelId
  
  // 更新URL参数
  updateRouteQuery(channelId)
  
  // 加载对应频道的笔记（第一页）
  loadNotes(channelId, true)
}

// 更新URL查询参数
const updateRouteQuery = (channelId) => {
  // 如果是默认频道(0)，则移除查询参数
  const query = channelId === 0 ? {} : { channelId }
  
  // 使用replace方法更新URL，不添加新的历史记录
  router.replace({ 
    path: route.path, 
    query 
  })
}

// 点击笔记卡片：打开详情浮层（子路由），当前信息流保持挂载。
// 展开动画由 openNote 负责：封面从卡片原位扩张到详情媒体区
const onNoteClick = (note) => {
  const noteId = note.id ?? note.noteId
  if (!noteId) return
  openNote(note, {
    path: `${route.path}/note/${noteId}`,
    query: route.query
  })
}

// 监听滚动事件，检测是否滚动到底部
const handleScroll = () => {
  const scrollTop = document.documentElement.scrollTop || document.body.scrollTop
  const scrollHeight = document.documentElement.scrollHeight || document.body.scrollHeight
  const clientHeight = document.documentElement.clientHeight || window.innerHeight
  
  // 当距离底部小于100px时，认为已经滚动到底部
  if (scrollHeight - scrollTop - clientHeight < 100) {
    loadMoreNotes()
  }
}

// 组件挂载时添加滚动监听
onMounted(() => {
  // 从URL获取频道ID
  const channelId = getChannelIdFromRoute()
  activeChannelId.value = channelId
  
  // 加载笔记数据
  loadNotes(channelId, true)
  
  // 添加滚动与窗口尺寸监听
  window.addEventListener('scroll', handleScroll)
})

// 组件卸载前移除滚动监听
onBeforeUnmount(() => {
  window.removeEventListener('scroll', handleScroll)
})

// 监听路由变化
watch(() => route.query.channelId, (newChannelId) => {
  const channelId = newChannelId ? parseInt(newChannelId) : 0
  if (activeChannelId.value !== channelId) {
    activeChannelId.value = channelId
    loadNotes(channelId, true)
  }
}, { immediate: true })

// 发布 / 删除笔记后刷新信息流，替代整页刷新
watch(() => noteStore.discoverRefreshToken, () => {
  loadNotes(activeChannelId.value, true)
  window.scrollTo({ top: 0, behavior: 'smooth' })
})
</script>

<style scoped>
.discover-page {
  min-height: 100%;
}

.retry-btn {
  margin-top: 8px;
  padding: 8px 20px;
  border: none;
  border-radius: var(--radius-pill);
  background: var(--color-canvas-deep);
  color: var(--color-ink);
  font-size: 14px;
  cursor: pointer;
  transition: background-color var(--motion-fast) var(--ease-standard);
}

.retry-btn:hover {
  background: rgb(0 0 0 / 0.08);
}
</style>

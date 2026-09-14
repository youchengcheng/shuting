<template>
  <div class="profile-page">
    <!-- 背景图：仅当用户设置过背景图时渲染，自上而下逐渐透明 -->
    <div v-if="profile.backgroundImg" class="profile-bg" aria-hidden="true">
      <div class="profile-bg__layer" :style="{ backgroundImage: `url(${profile.backgroundImg})` }"></div>
      <div class="profile-bg__fade"></div>
    </div>

    <!-- 个人资料头部：对照小红书资料页，无卡片边框、整体居中 -->
    <header class="profile-header">
      <div class="profile-avatar">
        <img v-if="profile.avatar" :src="profile.avatar" class="profile-avatar__img" alt="" />
        <img v-else src="@/assets/avatar.png" class="profile-avatar__img" alt="" />
      </div>

      <!-- 用户信息 -->
      <div class="profile-info">
        <div class="profile-name-row">
          <h1 class="profile-name">{{ profile.nickname }}</h1>
          <!-- 右侧按钮组 -->
          <div class="profile-actions">
            <button 
              v-if="!userStore.token || userStore.profile.userId !== profile.userId"
              @click="handleFollow"
              :class="isFollowing ? 'st-btn-ghost' : 'st-btn-primary'"
              class="st-btn w-[96px]">
              {{ isFollowing ? '已关注' : '关注' }}
            </button>
            <!-- 编辑按钮和下拉菜单 -->
            <div class="relative">
              <button 
                class="w-9 h-9 cursor-pointer border border-line hover:bg-canvas-sunken rounded-full flex items-center justify-center"
                @click="toggleDropdown"
                ref="dropdownTrigger"
              >
                <svg class="w-5 h-5 text-ink-soft" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                  <path
                    d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z" />
                </svg>
                
              </button>

              <!-- 下拉菜单 -->
              <Transition name="pop">
                <div 
                  v-if="showDropdown"
                  class="profile-dropdown absolute right-0 mt-2 w-[160px] bg-paper rounded-card shadow-panel p-2 z-50 border border-line"
                >
                  <button 
                    v-if="!userStore.token || userStore.profile.userId === profile.userId"
                    class="w-full px-4 py-2 text-left text-ink-soft hover:text-ink 
                    hover:bg-canvas-sunken flex items-center rounded-lg cursor-pointer"
                    @click="editProfile"
                  >
                    <svg t="1740136979710" class="icon w-5 h-5 mr-2" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="12707" width="200" height="200"><path d="M510.528 337.792c-98.048 0-177.792 78.848-177.792 175.744 0 96.96 79.744 175.808 177.792 175.808 9.536 0 19.584-1.024 31.744-3.136a33.152 33.152 0 0 0 27.008-38.4 33.28 33.28 0 0 0-38.912-26.624 114.048 114.048 0 0 1-19.84 2.048 110.4 110.4 0 0 1-110.848-109.696c0-60.416 49.664-109.632 110.72-109.632 61.12 0 110.848 49.216 110.848 109.632 0 5.952-0.64 12.16-2.112 19.584a33.152 33.152 0 0 0 33.024 39.04c16.256 0 30.08-11.52 32.832-27.2 2.176-11.392 3.2-21.376 3.2-31.36 0-46.912-18.496-91.008-52.096-124.224a177.536 177.536 0 0 0-125.568-51.584z" p-id="12708" fill="#57514A"></path><path d="M938.56 432.768a29.504 29.504 0 0 0-22.528-23.04 151.104 151.104 0 0 1-98.24-71.616 148.352 148.352 0 0 1-13.632-119.68 28.928 28.928 0 0 0-8.96-30.848 435.84 435.84 0 0 0-141.696-80.896 29.888 29.888 0 0 0-31.552 7.744 151.68 151.68 0 0 1-111.488 48.128 151.872 151.872 0 0 1-111.616-48.192 30.464 30.464 0 0 0-31.552-7.744c-52.48 18.176-100.16 45.44-141.632 80.96a29.184 29.184 0 0 0-8.96 30.72c12.608 40.64 7.744 83.2-13.568 119.744A150.848 150.848 0 0 1 104.96 409.6a29.696 29.696 0 0 0-22.592 23.168 428.8 428.8 0 0 0-7.872 80.768c0 26.432 2.624 53.632 7.872 80.768 2.176 11.456 11.008 20.48 22.528 23.104 41.92 9.6 76.8 34.944 98.24 71.552 21.312 36.608 26.176 79.168 13.568 119.808a29.44 29.44 0 0 0 8.96 30.72 436.48 436.48 0 0 0 141.696 80.96c11.456 3.84 23.68 0.512 31.424-7.68a151.872 151.872 0 0 1 111.616-48.256c42.624 0 82.304 17.152 111.552 48.192a29.888 29.888 0 0 0 31.616 7.808 436.48 436.48 0 0 0 141.632-80.896 29.44 29.44 0 0 0 8.96-30.72 147.84 147.84 0 0 1 13.632-119.808c21.376-36.672 56.192-62.08 98.24-71.552a29.76 29.76 0 0 0 22.528-23.104 429.44 429.44 0 0 0 7.872-80.768 430.08 430.08 0 0 0-7.872-80.896z m-209.92 357.12a352.896 352.896 0 0 1-85.44 48.832 201.728 201.728 0 0 0-265.6 0 353.28 353.28 0 0 1-85.44-48.832 194.816 194.816 0 0 0-22.784-138.624 199.424 199.424 0 0 0-110.208-88.832 339.84 339.84 0 0 1 0-97.728A199.488 199.488 0 0 0 269.44 375.872a195.84 195.84 0 0 0 22.72-138.624A353.28 353.28 0 0 1 377.6 188.416c36.8 32.192 83.776 49.92 132.8 49.92 49.152 0 96.128-17.664 132.8-49.856 30.72 12.288 59.456 28.672 85.44 48.832a195.648 195.648 0 0 0 22.656 138.624c24.448 41.984 63.424 73.344 110.208 88.832a341.12 341.12 0 0 1 0.064 97.664c-46.72 15.488-85.76 46.848-110.144 88.832a196.224 196.224 0 0 0-22.784 138.624z" p-id="12709" fill="#57514A"></path></svg>
                    编辑资料
                  </button>
                  <button 
                    class=" cursor-not-allowed w-full px-4 py-2 text-left text-ink-soft hover:text-ink 
                    hover:bg-canvas-sunken flex items-center rounded-lg"
                  >
                  <svg t="1741424760279" class="  icon w-4 h-4 mr-2.5 ml-[1px]" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="1684" width="200" height="200"><path d="M512 93.098667a418.901333 418.901333 0 1 0 0 837.802666c231.367111 0 418.901333-187.534222 418.901333-418.901333S743.367111 93.098667 512 93.098667zM0 512C0 229.262222 229.262222 0 512 0s512 229.262222 512 512-229.262222 512-512 512S0 794.737778 0 512z" fill="#57514A" p-id="1685"></path><path d="M564.622222 373.020444c11.235556 10.126222 16.867556 25.258667 15.018667 40.419556l-37.319111 303.729778 26.567111-6.769778a44.942222 44.942222 0 0 1 44.003555 12.572444c11.377778 11.946667 15.644444 29.155556 11.093334 45.141334a45.880889 45.880889 0 0 1-32.995556 32.199111l-91.022222 23.182222a44.913778 44.913778 0 0 1-41.187556-10.24 46.819556 46.819556 0 0 1-14.990222-40.476444l37.262222-303.701334-26.567111 6.769778a45.454222 45.454222 0 0 1-54.897777-33.792c-6.058667-24.689778 8.561778-49.777778 32.768-56.149333l91.022222-23.153778a44.913778 44.913778 0 0 1 41.244444 10.268444zM443.448889 245.475556c0-25.6 20.366222-46.364444 45.511111-46.364445h45.482667c25.144889 0 45.511111 20.764444 45.511111 46.364445 0 25.6-20.366222 46.364444-45.511111 46.364444h-45.511111c-12.060444 0-23.637333-4.892444-32.170667-13.596444-8.533333-8.704-13.340444-20.48-13.340445-32.768z" fill="#57514A" p-id="1686"></path></svg>
                    举报
                  </button>
                </div>
              </Transition>
            </div>
          </div>
        </div>

        <div class="profile-id">书亭号：{{ profile.xiaohashuId }}</div>

        <!-- 个人简介 -->
        <div class="profile-bio">
          {{ profile.introduction || '此用户还未填写简介' }}
        </div>

        <!-- 性别年龄地区 -->
        <div class="profile-tags">
          <span class="user-tag">
            <svg v-if="userStore.profile.sex === 0" t="1740127656798" class="icon w-3 h-2.5 text-ink-faint" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="9302" width="200" height="200"><path d="M512 93.090909c130.327273 0 232.727273 102.4 232.727273 232.727273s-102.4 232.727273-232.727273 232.727273-232.727273-102.4-232.727273-232.727273 102.4-232.727273 232.727273-232.727273m-46.545455 553.890909v97.745455h-186.181818c-27.927273 0-46.545455 18.618182-46.545454 46.545454s18.618182 46.545455 46.545454 46.545455h186.181818v139.636363c0 23.272727 23.272727 46.545455 46.545455 46.545455 27.927273 0 46.545455-23.272727 46.545455-46.545455v-139.636363h186.181818c27.927273 0 46.545455-18.618182 46.545454-46.545455s-18.618182-46.545455-46.545454-46.545454h-186.181818v-97.745455c176.872727-27.927273 302.545455-190.836364 274.618181-367.709091-27.927273-176.872727-190.836364-302.545455-367.709091-274.618182-176.872727 27.927273-302.545455 190.836364-274.618181 367.709091 18.618182 144.290909 130.327273 256 274.618181 274.618182" fill="currentColor" p-id="9303"></path></svg>
            <svg v-else t="1740547397483" class="icon w-3 h-2.5 text-ink-faint" viewBox="0 0 1024 1024" version="1.1" xmlns="http://www.w3.org/2000/svg" p-id="13790" width="200" height="200"><path d="M914.285714 0 658.285714 0l0 109.714286 178.428343 0-160.393143 160.393143C605.429029 215.606857 516.928 182.857143 420.571429 182.857143 188.286171 182.857143 0 371.143314 0 603.428571s188.286171 420.571429 420.571429 420.571429 420.571429-188.286171 420.571429-420.571429c0-96.356571-32.749714-184.8576-87.250286-255.749486L914.285714 187.285943 914.285714 365.714286l109.714286 0L1024 109.714286 1024 0 914.285714 0zM420.571429 914.285714c-171.392 0-310.857143-139.465143-310.857143-310.857143s139.465143-310.857143 310.857143-310.857143 310.857143 139.465143 310.857143 310.857143S591.963429 914.285714 420.571429 914.285714z" fill="currentColor" p-id="13791"></path></svg>
            {{ profile.age || 0 }}岁
          </span>
          <span class="user-tag">中国</span>
        </div>

        <!-- 数据统计 -->
        <div class="profile-stats">
          <router-link :to="`/user/${profile.userId}/relation?tab=following`" class="profile-stat">
            <span class="profile-stat__num">{{ profile.followingTotal || 0 }}</span>
            <span class="profile-stat__label">关注</span>
          </router-link>
          <router-link :to="`/user/${profile.userId}/relation?tab=followers`" class="profile-stat">
            <span class="profile-stat__num">{{ profile.fansTotal || 0 }}</span>
            <span class="profile-stat__label">粉丝</span>
          </router-link>
          <div class="profile-stat">
            <span class="profile-stat__num">{{ profile.likeAndCollectTotal || 0 }}</span>
            <span class="profile-stat__label">获赞与收藏</span>
          </div>
        </div>
      </div>
    </header>

    <!-- Tab 导航 -->
    <div class="profile-tabs">
      <TabNav
        v-model="activeTab"
        :tabs="tabList"
      />
    </div>

    <!-- 笔记列表：笔记 / 收藏 / 点赞 共用一个瀑布流 -->
    <div class="profile-notes">
      <NoteWaterfall
        :notes="displayNotes"
        :loading="loading"
        :loading-more="loadingMore"
        :has-more="hasMore"
        :owner-actions="activeTab === 'notes' && isSelf"
        @note-click="onNoteClick"
        @load-more="loadMoreNotes"
        @edit="handleNoteEdit"
        @top="handleNoteTop"
        @visible="handleNoteVisible"
        @delete="handleNoteDelete"
      />
      <EmptyState
        v-if="!loading && notes.length === 0"
        :title="emptyState.title"
        :description="emptyState.description"
      />
    </div>

    <!-- 编辑资料模态框 -->
    <EditProfileModal 
      v-model:visible="showEditModal" 
      :avatar="profile.avatar"
      @update-success="handleProfileUpdated"
    />

    <!-- 编辑笔记：复用发布弹窗的编辑模式 -->
    <PublishModal
      v-model:visible="showEditNoteModal"
      :edit-note="editingNote"
      @success="onEditNoteSuccess"
    />

    <!-- 删除笔记二次确认 -->
    <ConfirmDialog
      v-model:visible="showDeleteDialog"
      title="删除笔记"
      message="删除后该笔记将无法恢复，确定删除吗？"
      confirm-text="删除"
      danger
      :loading="deleteLoading"
      @confirm="confirmDeleteNote"
    />

    <!-- 设为仅自己可见二次确认 -->
    <ConfirmDialog
      v-model:visible="showPrivateDialog"
      title="设为仅自己可见"
      message="设为仅自己可见后，该笔记仅你本人可见，确定设置吗？"
      :loading="privateLoading"
      @confirm="confirmPrivateNote"
    />

    <!-- 笔记详情浮层：以子路由渲染，关闭时只卸载浮层，当前主页不会重新加载 -->
    <router-view v-slot="{ Component }">
      <Transition name="note-overlay">
        <component :is="Component" />
      </Transition>
    </router-view>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed, watch, inject } from 'vue'
import TabNav from '@/components/common/TabNav.vue'
import NoteWaterfall from '@/components/note/NoteWaterfall.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { useUserStore } from '@/stores/user'
import { getUserProfile } from '@/api/user'
import EditProfileModal from '@/components/profile/EditProfileModal.vue'
import {
  getPublishedNoteList,
  getProfileNotePageList,
  getNoteDetail,
  deleteNote,
  topNote,
  updateNoteVisible
} from '@/api/note'
import PublishModal from '@/components/note/PublishModal.vue'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import { useRoute, useRouter } from 'vue-router'
import { followUser, unfollowUser, isFollowedUser } from '@/api/relation'
import { message } from '@/utils/message'
import { useNoteTransition } from '@/composables/noteTransition'

const userStore = useUserStore()
const route = useRoute()
const router = useRouter()
const { openNote } = useNoteTransition()

// 当前激活的 tab
const activeTab = ref('notes')

// 笔记数据
const notes = ref([])
const currPageNo = ref(1)

// 已发布笔记列表的下一页游标（即当前页最早一篇笔记的 ID）
const cursor = ref(null)

// 已发布笔记列表接口单页大小（与后端保持一致）
const PUBLISHED_PAGE_SIZE = 20

// 请求序号，用于丢弃过期响应（快速切换 tab / 用户时）
let loadSeq = 0



// 点击笔记卡片：打开详情浮层（子路由），当前主页保持挂载。
// 展开动画由 openNote 负责：封面从卡片原位扩张到详情媒体区
const onNoteClick = (note) => {
  const noteId = note.id ?? note.noteId
  if (!noteId) return
  openNote(note, {
    path: `${route.path}/note/${noteId}`,
    query: route.query
  })
}

// 下拉菜单状态
const showDropdown = ref(false)
const dropdownTrigger = ref(null)

// 切换下拉菜单
const toggleDropdown = () => {
  showDropdown.value = !showDropdown.value
}

// 编辑资料模态框状态
const showEditModal = ref(false)

// 使用计算属性获取头像
const userAvatar = computed(() => userStore.profile.avatar || '')

// 更新整个 profile 的方法
const updateProfile = (newProfile) => {
  userStore.setProfile(newProfile)
}

// 登录状态控制
const isLoggedIn = computed(() => !!userStore.token)
const showLoginModal = inject('showLoginModal')

// 编辑资料
const editProfile = () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }

  showDropdown.value = false
  showEditModal.value = true
}

// 点击外部关闭下拉菜单
const handleClickOutside = (event) => {
  if (dropdownTrigger.value && !dropdownTrigger.value.contains(event.target)) {
    showDropdown.value = false
  }
}

const profile = ref({})
const isFollowing = ref(false)

// Tab 导航配置：与小红书一致（笔记 / 点赞 / 收藏）；“笔记”数量使用后端返回的真实数据
// 图标语义：like 为心形（点赞）、collect 为星形（收藏）
const tabList = computed(() => [
  { key: 'notes', label: '笔记', icon: 'note', count: profile.value.noteTotal },
  { key: 'like', label: '点赞', icon: 'like' },
  { key: 'collect', label: '收藏', icon: 'collect' }
])

// 当前 tab 对应的空态文案
const emptyState = computed(() => {
  const map = {
    notes: { title: '还没有发布笔记', description: '发布第一篇笔记，记录你的生活' },
    like:{ title: '还没有点赞的笔记', description: '点赞过的笔记会出现在这里' },
    collect:  { title: '还没有收藏的笔记', description: '收藏一些喜欢的内容，会出现在这里' }
  }
  return map[activeTab.value] || map.notes
})

// 添加和移除点击事件监听
onMounted(() => {
  // 用户资料与笔记数据由下方 route 参数 watcher（immediate）负责首次加载
  document.addEventListener('click', handleClickOutside)

  // 添加滚动事件监听
  window.addEventListener('scroll', handleScroll)
})

onUnmounted(() => {
  document.removeEventListener('click', handleClickOutside)
  window.removeEventListener('scroll', handleScroll)
})

const hasMore = ref(true) // 是否有更多数据
const isLoading = ref(false) // 是否有请求在途
const loading = ref(false) // 首屏加载：骨架屏
const loadingMore = ref(false) // 追加加载：底部加载行


// 加载笔记数据
// - “笔记”tab：已登录时调用已发布笔记接口，使用游标分页（后端每页最多返回 20 条）
// - 未登录的“笔记”tab、“赞过 / 收藏”tab：沿用页码分页接口（每页 10 条）
const loadNotes = (isFirstPage = true) => {
  if (isLoading.value) return

  const userId = Number(route.params.userId) || profile.value.userId
  if (!userId) return

  if (isFirstPage) {
    // 重置分页状态
    loading.value = true
    cursor.value = null
    currPageNo.value = 1
    notes.value = []
    hasMore.value = true
  } else {
    loadingMore.value = true
  }

  // 记录本次请求序号，响应返回时若已发起新请求则直接丢弃，避免快速切换 tab / 用户时数据错乱
  const requestSeq = ++loadSeq
  isLoading.value = true

  const appendNotes = (newNotes) => {
    if (requestSeq !== loadSeq) return
    if (isFirstPage) {
      notes.value = newNotes
      return
    }
    // 追加时按 id 去重
    const existingIds = new Set(notes.value.map(note => note.id))
    notes.value = [...notes.value, ...newNotes.filter(note => !existingIds.has(note.id))]
  }

  // “笔记”tab 在已登录时调用已发布笔记接口（含 isLiked、格式化点赞数）；
  // 未登录时该接口会被网关拦截，回退到已放行的浏览接口（type=1）
  if (activeTab.value === 'notes' && isLoggedIn.value) {
    // 后端返回的笔记 ID 字段为 noteId，统一补一个 id 供卡片组件使用
    getPublishedNoteList(userId, cursor.value).then(res => {
      if (requestSeq !== loadSeq) return
      if (res.success) {
        const newNotes = (res.data?.notes || []).map(note => ({ ...note, id: note.noteId }))
        appendNotes(newNotes)
        cursor.value = res.data?.nextCursor ?? null
        // 不足一页，或没有下一页游标，说明没有更多数据
        hasMore.value = newNotes.length >= PUBLISHED_PAGE_SIZE && cursor.value !== null
      } else {
        hasMore.value = false
      }
    }).finally(() => {
      if (requestSeq === loadSeq) {
        isLoading.value = false
        loading.value = false
        loadingMore.value = false
      }
    })
    return
  }

  // 赞过（type=2）、收藏（type=3）使用页码分页接口；未登录时“笔记”tab 使用 type=1
  const type = activeTab.value === 'notes' ? 1 :
               activeTab.value === 'like' ? 2 : 3
  getProfileNotePageList(type, userId, currPageNo.value).then(res => {
    if (requestSeq !== loadSeq) return
    if (res.success) {
      const newNotes = res.data || []
      appendNotes(newNotes)
      currPageNo.value = res.pageNo + 1
      hasMore.value = res.pageNo < res.totalPage
    } else {
      hasMore.value = false
    }
  }).finally(() => {
    if (requestSeq === loadSeq) {
      isLoading.value = false
      loading.value = false
      loadingMore.value = false
    }
  })
}

// 加载更多数据
const loadMoreNotes = () => {
  // 如果正在加载或没有更多数据，则不处理
  if (isLoading.value || !hasMore.value) return
  loadNotes(false)
}

// 监听滚动事件，检测是否滚动到底部
const handleScroll = () => {
  // 如果正在加载或者没有更多数据，则不处理
  if (isLoading.value || !hasMore.value) return
  
  const scrollTop = window.pageYOffset || document.documentElement.scrollTop
  const windowHeight = window.innerHeight
  const documentHeight = document.documentElement.scrollHeight
  
  // 当滚动到距离底部一定距离时加载更多
  if (documentHeight - scrollTop - windowHeight < 200) {
    // 设置一个标记，防止多次触发
    if (!isLoading.value) {
      loadMoreNotes()
    }
  }
}

// 监听 activeTab 变化
watch(activeTab, () => {
  // 重置加载状态后，加载对应 tab 的第一页数据
  isLoading.value = false
  loadNotes(true)
})


// ── 笔记操作菜单（仅本人主页的「笔记」tab）─────────────────────────
const showEditNoteModal = ref(false)
const editingNote = ref(null)
const showDeleteDialog = ref(false)
const pendingDeleteNote = ref(null)
const deleteLoading = ref(false)
const showPrivateDialog = ref(false)
const pendingPrivateNote = ref(null)
const privateLoading = ref(false)

// 当前主页是否属于登录用户本人
const isSelf = computed(() => {
  if (!userStore.token) return false
  const selfId = userStore.profile?.userId
  const pageId = profile.value?.userId
  if (selfId === undefined || selfId === null || pageId === undefined || pageId === null) return false
  return String(selfId) === String(pageId)
})

// 渲染顺序：置顶笔记排最前，其余保持后端顺序
// 不改动 notes 原始数组，避免影响游标分页
const displayNotes = computed(() => {
  const list = notes.value
  if (!list.some((note) => note?.isTop === true)) return list
  const top = []
  const rest = []
  list.forEach((note) => {
    if (note?.isTop === true) top.push(note)
    else rest.push(note)
  })
  return [...top, ...rest]
})

const noteIdOf = (note) => note?.id ?? note?.noteId

// 用新字段替换列表中对应笔记，触发视图刷新（角标 / 顺序）
const patchNote = (noteId, patch) => {
  notes.value = notes.value.map((note) =>
    noteIdOf(note) === noteId ? { ...note, ...patch } : note
  )
}

// 编辑笔记：先拉全量详情，再打开弹窗的编辑模式
const handleNoteEdit = (note) => {
  const noteId = noteIdOf(note)
  if (!noteId) return

  getNoteDetail(noteId).then((res) => {
    if (!res.success) {
      message.show(res.message || '获取笔记详情失败')
      return
    }
    editingNote.value = { ...res.data, id: res.data?.id ?? noteId }
    showEditNoteModal.value = true
  }).catch(() => {
    message.show('获取笔记详情失败')
  })
}

// 编辑成功：刷新列表（弹窗已给出「修改成功」提示）
const onEditNoteSuccess = () => {
  loadNotes(true)
}

// 删除笔记
const handleNoteDelete = (note) => {
  pendingDeleteNote.value = note
  showDeleteDialog.value = true
}

const confirmDeleteNote = () => {
  const noteId = noteIdOf(pendingDeleteNote.value)
  if (!noteId || deleteLoading.value) return

  deleteLoading.value = true
  deleteNote(noteId).then((res) => {
    if (res.success) {
      notes.value = notes.value.filter((note) => noteIdOf(note) !== noteId)
      profile.value = {
        ...profile.value,
        noteTotal: Math.max(0, Number(profile.value.noteTotal || 0) - 1)
      }
      message.show('笔记已删除')
      showDeleteDialog.value = false
      pendingDeleteNote.value = null
    } else {
      message.show(res.message || '删除失败')
    }
  }).catch(() => {
    message.show('删除失败')
  }).finally(() => {
    deleteLoading.value = false
  })
}

// 置顶 / 取消置顶：无二次确认
const handleNoteTop = (note) => {
  const noteId = noteIdOf(note)
  if (!noteId) return

  const nextTop = note.isTop !== true
  topNote(noteId, nextTop).then((res) => {
    if (res.success) {
      patchNote(noteId, { isTop: nextTop })
      message.show(nextTop ? '已置顶' : '已取消置顶')
    } else {
      message.show(res.message || '操作失败')
    }
  }).catch(() => {
    message.show('操作失败')
  })
}

// 可见性：设为仅自己可见需二次确认，切回公开直接执行
const handleNoteVisible = (note) => {
  const noteId = noteIdOf(note)
  if (!noteId) return

  if (Number(note.visible) === 1) {
    updateNoteVisible(noteId, 0).then((res) => {
      if (res.success) {
        patchNote(noteId, { visible: 0 })
        message.show('已设为公开')
      } else {
        message.show(res.message || '操作失败')
      }
    }).catch(() => {
      message.show('操作失败')
    })
    return
  }

  pendingPrivateNote.value = note
  showPrivateDialog.value = true
}

const confirmPrivateNote = () => {
  const noteId = noteIdOf(pendingPrivateNote.value)
  if (!noteId || privateLoading.value) return

  privateLoading.value = true
  updateNoteVisible(noteId, 1).then((res) => {
    if (res.success) {
      patchNote(noteId, { visible: 1 })
      message.show('已设为仅自己可见')
      showPrivateDialog.value = false
      pendingPrivateNote.value = null
    } else {
      message.show(res.message || '操作失败')
    }
  }).catch(() => {
    message.show('操作失败')
  }).finally(() => {
    privateLoading.value = false
  })
}

// 编辑弹窗关闭后清掉编辑对象，避免下次打开残留旧数据
watch(showEditNoteModal, (visible) => {
  if (!visible) editingNote.value = null
})

// 刷新是否已关注：接口按登录用户与目标用户实时查询，未登录或看自己主页时保持未关注
const refreshFollowStatus = (userId) => {
  const targetId = userId ?? profile.value.userId
  const selfId = userStore.profile?.userId

  if (!targetId || !userStore.token || (selfId !== undefined && String(selfId) === String(targetId))) {
    isFollowing.value = false
    return
  }

  isFollowedUser(targetId).then(res => {
    if (res.success) {
      isFollowing.value = Boolean(res.data)
    }
  }).catch(() => {
    // 关注状态获取失败时静默处理，不打断页面展示
  })
}

// 处理个人资料更新成功的回调
const handleProfileUpdated = (updatedProfile) => {
  // 更新本地的 profile 数据
  // 上传中的文件对象不是可渲染的 URL，跳过并交给下面的资料刷新补齐；
  // 移除背景图时为 null，需要保留以便背景层立即消失
  const { avatar, backgroundImg, removeBackgroundImg, ...rest } = updatedProfile || {}
  const localPatch = { ...rest }
  if (typeof avatar === 'string') localPatch.avatar = avatar
  if (typeof backgroundImg === 'string' || backgroundImg === null) localPatch.backgroundImg = backgroundImg
  profile.value = { ...profile.value, ...localPatch }

  // 可能需要重新获取用户资料以确保数据同步
  getUserProfile(route.params.userId).then(res => {
    if (res.success) {
      profile.value = res.data
      refreshFollowStatus(res.data?.userId)
    }
  })
}

const handleFollow = () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  const request = isFollowing.value
    ? unfollowUser(profile.value.userId)
    : followUser(profile.value.userId)
  request.then(res => {
    if (!res.success) {
      message.show(res.message)
      return
    }
    isFollowing.value = !isFollowing.value
    const currentFans = Number(profile.value.fansTotal || 0)
    profile.value.fansTotal = Math.max(0, currentFans + (isFollowing.value ? 1 : -1))
    message.show(isFollowing.value ? '关注成功' : '取消关注成功')
  })
}

// 监听路由参数变化，当用户ID变化时重新加载数据
watch(() => route.params.userId, (newUserId, oldUserId) => {
  if (newUserId === oldUserId) return

  // 丢弃在途请求，并重置分页状态
  loadSeq++
  cursor.value = null
  currPageNo.value = 1
  notes.value = []
  hasMore.value = true
  isLoading.value = false

  // 重置 tab 为默认的“笔记”选项卡；若 tab 确实变化，由 activeTab watcher 触发加载
  const tabChanged = activeTab.value !== 'notes'
  if (tabChanged) {
    activeTab.value = 'notes'
  }

  // 获取新用户的资料
  getUserProfile(newUserId).then(res => {
    if (res.success) {
      profile.value = res.data
      refreshFollowStatus(res.data?.userId)
    }
  }).catch(() => {
  }).finally(() => {
    // 加载新用户的笔记；tab 变化时由 activeTab watcher 加载，避免重复请求
    if (!tabChanged) {
      loadNotes(true)
    }
  })
}, { immediate: true })
</script>

<style scoped>
.profile-page {
  position: relative;
  display: flex;
  flex-direction: column;
}

/* 背景图层：圆角卡片铺满内容宽度（与下方笔记瀑布流左右对齐），
   绝对定位在资料头部区域，不参与交互 */
.profile-bg {
  position: absolute;
  top: 16px;
  left: 0;
  right: 0;
  height: 240px;
  border-radius: var(--radius-card);
  overflow: hidden;
  pointer-events: none;
  z-index: 0;
}

.profile-bg__layer {
  position: absolute;
  inset: 0;
  background-size: cover;
  background-position: center;
  -webkit-mask-image: linear-gradient(to bottom, rgb(0 0 0 / 0.9) 0%, rgb(0 0 0 / 0) 100%);
  mask-image: linear-gradient(to bottom, rgb(0 0 0 / 0.9) 0%, rgb(0 0 0 / 0) 100%);
}

.profile-bg__fade {
  position: absolute;
  inset: 0;
  background: linear-gradient(to bottom, rgb(255 255 255 / 0) 55%, var(--color-canvas) 100%);
}

/* 资料头部：头像在左、信息在右，整体居中且无卡片边框（贴近小红书资料页） */
.profile-header {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 40px;
  max-width: 760px;
  margin: 0 auto;
  padding: 40px 0 8px;
}

.profile-avatar {
  flex-shrink: 0;
  width: 120px;
  height: 120px;
}

.profile-avatar__img {
  width: 100%;
  height: 100%;
  border-radius: var(--radius-pill);
  object-fit: cover;
  background: var(--color-canvas-sunken);
}

.profile-info {
  flex: 1;
  min-width: 0;
}

.profile-name-row {
  display: flex;
  align-items: center;
  gap: 16px;
}

.profile-name {
  flex: 1;
  min-width: 0;
  font-weight: 600;
  font-size: 24px;
  line-height: 1.3;
  color: var(--color-ink);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.profile-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-shrink: 0;
}

.profile-id {
  margin-top: 8px;
  font-size: 13px;
  color: var(--color-ink-faint);
}

.profile-bio {
  margin-top: 14px;
  font-size: 15px;
  line-height: 1.7;
  color: var(--color-ink);
  white-space: pre-line;
}

.profile-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 14px;
}

.profile-stats {
  display: flex;
  align-items: center;
  gap: 28px;
  margin-top: 18px;
}

.profile-stat {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
}

.profile-stat__num {
  font-size: 16px;
  font-weight: 500;
  color: var(--color-ink);
}

.profile-stat__label {
  font-size: 13px;
  color: var(--color-ink-faint);
}

.user-tag {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 10px;
  border-radius: var(--radius-pill);
  background: var(--color-canvas-sunken);
  color: var(--color-ink-faint);
  font-size: 12px;
}

/* Tab 导航：居中胶囊，与小红书资料页一致 */
.profile-tabs {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

/* 笔记 / 收藏 / 点赞 三个 tab 共用同一个瀑布流容器 */
.profile-notes {
  width: 100%;
  margin-top: 20px;
}

/* 下拉菜单里的矢量图标跟随文字颜色 */
.profile-dropdown svg path {
  fill: currentColor;
}

@media (max-width: 767px) {
  .profile-header {
    gap: 20px;
    padding: 24px 0 8px;
  }

  .profile-avatar {
    width: 80px;
    height: 80px;
  }

  .profile-name {
    font-size: 20px;
  }
}
</style>

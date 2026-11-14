<template>
  <div class="note-detail">
    <EmptyState v-if="noteError" class="note-detail__error" title="笔记暂时无法查看" :description="noteError"><button type="button" class="st-btn st-btn-ghost" @click="initializeDetail">重试</button></EmptyState>
    <template v-else>
    <!-- 左侧媒体区域（data-note-media：展开动画的落点，见 composables/noteMorph.js） -->
    <div class="note-detail__media" data-note-media>
      <div class="note-detail__media-inner">
              <ImageCarousel v-if="mediaKind === 'image'" :images="mediaImages" class="h-full w-full" />
              <VideoPlayer v-else-if="mediaKind === 'video'"
                      :video-url="currNote.videoUri"
                      :poster="currNote.cover"
                      :autoplay="true"
                      :loop="true"
                    ></VideoPlayer>
              <!-- 详情数据未到位：先用卡片上已加载的封面顶上，媒体区不空、展开动画也有连续图像 -->
              <img v-else-if="mediaKind === 'cover'" class="note-detail__hero" :src="optimisticCover" alt="" />
              <!-- 笔记类型未确定时不渲染媒体组件，避免 VideoPlayer 黑底闪现 -->
            </div>
          </div>

          <!-- 右侧内容区域（data-note-panel：展开动画里错峰淡入） -->
          <div class="note-detail__panel" data-note-panel>

            <!-- 作者信息 -->
            <div 
              class="note-detail__author p-[24px] flex items-center bg-paper"
              :class="{'border-b border-line': isScrolled}"
              ref="authorInfoRef"
            >
              <router-link :to="`/user/profile/${currNote.creatorId}`">
                      <img 
                      :src="currNote.avatar" 
                      class="w-[40px]! h-[40px]! rounded-full"
                    />
              </router-link>

              <router-link :to="`/user/profile/${currNote.creatorId}`" class="ml-[12px] flex-1">
                      <div class="font-medium text-[16px] text-ink-soft hover:text-ink">{{ currNote.creatorName }}</div>
              </router-link>
              

              <button 
              v-if="!userStore.token || userStore.profile.userId !== currNote.creatorId"
              @click="handleFollow"
              :class="followStatus > 0 ? 'st-btn-ghost' : 'st-btn-primary'"
              class="st-btn w-[96px]">
                {{ followStatus === 2 ? '互相关注' : followStatus === 1 ? '已关注' : '关注' }}
              </button>
            </div>

            <!-- 评论区域容器 -->
            <div 
              class="note-detail__scroll" 
              @scroll="handleScroll"
              ref="scrollContainerRef"
            >
              <!-- 笔记正文 -->
              <div 
                class="text-ink px-[24px] pb-[24px] flex-1"
                ref="contentRef"
              >
                <h1 class="title">{{ currNote.title }}</h1>
                <div class="note-content note-detail__body whitespace-pre-wrap">{{ currNote.content }}</div>
                <ul v-if="currNote.topicName" class="text-ink-soft flex flex-wrap gap-2">
                  <li class="cursor-pointer">#{{currNote.topicName}}</li>
                </ul>
                <div class="text-ink-faint text-[14px] mt-[12px]">
                  编辑于 {{ currNote.updateTime }}
                </div>
              </div>

              <!-- 分割线 -->
              <div class="h-[1px] border-b border-line mx-[24px]"></div>

              <!-- 评论区 -->
              <CommentList
                :comments="comments"
                :paging="rootCommentPaging"
                :target-comment-id="targetCommentId"
                :loading="commentLoading"
                :loading-more="isLoadingMoreComments"
                :locating="isLocatingComment"
                :location-error="commentLocationError"
                :error="commentError"
                :unavailable-reason="contextUnavailableReason"
                :total="commentTotal"
                :has-more="hasMoreComments"
                :more-count="moreCommentsCount"
                :current-user-id="currentUserId"
                @load-more="loadMoreComments"
                @load-page="loadMoreComments"
                @reply="onReplyClick"
                @click-comment="focusComment"
                @expand-replies="handleExpandReplies"
                @like="handleCommentLike"
                @delete="onCommentDelete"
                @retry="reloadComments"
                @retry-target="locateTargetComment"
              />
            </div>
            

            <!-- 底部互动区 -->
            <div class="note-detail__footer border-t border-line p-[16px]">
              <div class="flex flex-col text-ink-faint text-[15px]">
                <!-- 评论输入区域 -->
                <div class="flex items-center gap-2">
                  <!-- 登录提示/评论输入框 -->
                  <div 
                    v-if="!isLoggedIn"
                    class="content-input grow cursor-pointer"
                    @click="focusComment"
                  >
                    <svg class="w-4 h-4 text-ink-faint" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                      <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2M12 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8z" stroke-width="2"/>
                    </svg>
                    <span class="text-ink-faint text-sm ml-2">登录后评论</span>
                  </div>
                  
                  <!-- 已登录状态显示评论输入框 -->
                  <div 
                    v-else
                    class="flex flex-col min-w-0"
                    :class="{ 'w-full': isInputFocused, 'flex-1': !isInputFocused }"
                  >
                    <!-- 回复提示 - 仅在回复时显示 -->
                    <div 
                      v-if="replyTo"
                      class="flex flex-col px-3 py-2 text-[14px] w-full"
                    >
                      <div class="flex items-center reply">
                        回复
                        <span class="text-ink mx-1">{{ replyTo.nickname }}</span>
                      </div>
                      <div class="reply-content line-clamp-1">
                        {{ replyTo.content }} <span v-if="replyTo.imageUrl">[图片]</span>
                      </div>
                    </div>

                    <!-- 评论输入框 -->
                    <div 
                      class="gap-2 rounded-full flex items-center content-input"
                      :class="{
                        'w-full px-[16px]!': isInputFocused,
                        'flex-1': !isInputFocused
                      }"
                      @click="focusComment"
                    >
                      <!-- 头像只在未聚焦时显示 -->
                      <img 
                        v-if="!isInputFocused"
                        :src="userStore.profile.avatar" 
                        class="w-[24px] h-[24px] rounded-full shrink-0"
                      />
                      
                      <!-- 未聚焦时显示默认文本 -->
                      <div 
                        v-if="!isInputFocused && !commentContent" 
                        class="text-ink-faint text-sm ml-2 whitespace-nowrap overflow-hidden text-ellipsis"
                      >
                        说点什么...
                      </div>
                      
                      <!-- 输入框 -->
                      <input 
                        type="text" 
                        placeholder="说点什么..." 
                        v-model="commentContent"
                        class="flex-1 bg-transparent focus:outline-none min-w-0 text-ink"
                        :class="{
                          'ml-2 text-sm': !isInputFocused,
                          'text-[16px]': isInputFocused
                        }"
                        @blur="onInputBlur"
                        ref="commentInput"
                      />
                      
                    </div>
                    
                    <div class="mt-[8px]" v-if="commentImage">
                      <img :src="commentImage" 
                      class="w-[60px]! rounded-lg object-cover cursor-zoom-in hover:brightness-80 ml-2">
                    </div>
                  </div>

                  <!-- 互动数据 - 仅在输入框未聚焦时显示 -->
                  <div v-if="!isInputFocused" class="flex items-center gap-[18px] ml-auto text-ink">
                    <div class="flex items-center gap-1 cursor-pointer hover:text-ink">
                      <svg 
                        class="action-icon w-[22px] h-[22px] transition-all duration-200"
                        :class="[isNoteLiked ? 'animate-like is-on' : 'animate-unlike']"
                        viewBox="0 0 24 24" 
                        :fill="isNoteLiked ? 'currentColor' : 'none'" 
                        stroke="currentColor"
                        @click="handleNoteLike"
                      >
                        <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z" stroke-width="2"/>
                      </svg>
                      <span class="text-[15px] leading-none">{{ currNote.likeTotal }}</span>
                    </div>
                    <div class="flex items-center gap-1 cursor-pointer hover:text-ink">
                      <svg 
                        class="action-icon w-[22px] h-[22px] transition-all duration-200"
                        :class="[isNoteCollected ? 'animate-like is-on' : 'animate-unlike']"
                        viewBox="0 0 24 24" 
                        :fill="isNoteCollected ? 'currentColor' : 'none'" 
                        stroke="currentColor"
                        @click="handleNoteCollect"
                      >
                        <path d="M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                      </svg>
                      <span class="text-[15px] leading-none">{{ currNote.collectTotal }}</span>
                    </div>
                    <div 
                      class="flex items-center gap-1 cursor-pointer hover:text-ink"
                      @click="focusComment"
                    >
                      <svg class="w-[22px] h-[22px]" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                        <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" stroke-width="2"/>
                      </svg>
                      <span class="text-[15px] leading-none">{{ currNote.commentTotal }}</span>
                    </div>
                    <div 
                      class="flex items-center gap-1 cursor-pointer hover:text-ink"
                      @click="handleShare"
                    >
                      <svg class="w-[22px] h-[22px]" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                        <path d="M22 2 11 13" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                        <path d="M22 2 15 22l-4-9-9-4 20-7z" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                      </svg>
                    </div>
                  </div>
                </div>

                <!-- 输入框聚焦时显示的底部工具栏 -->
                <div v-if="isInputFocused" class="flex items-center justify-between mt-3">
                  <div class="flex items-center gap-1">
                    <div class="relative">
                      <button 
                        class="p-[10px] hover:text-ink hover:bg-canvas-sunken rounded-full"
                        @click="toggleEmojiPicker"
                        ref="emojiButtonRef"
                      >
                        <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none">
                          <path d="M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 17.5228 6.47715 22 12 22Z" stroke="currentColor" stroke-width="2"/>
                          <path d="M8.5 15C8.5 15 9.8125 17 12 17C14.1875 17 15.5 15 15.5 15" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                          <path d="M9 10H9.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                          <path d="M15 10H15.01" stroke="currentColor" stroke-width="2" stroke-linecap="round"/>
                        </svg>
                      </button>
                      
                      <!-- 表情选择弹出框 -->
                      <div 
                        v-if="showEmojiPicker" 
                        class="emoji-picker border border-line"
                        ref="emojiPickerRef"
                      >
                        <div class="emoji-grid">
                          <button 
                            v-for="emoji in emojiList" 
                            :key="emoji"
                            class="emoji-item"
                            @click="insertEmoji(emoji)"
                          >
                            {{ emoji }}
                          </button>
                        </div>
                      </div>
                    </div>
                    
                    <!-- 上传图片按钮 -->
                    <div class="relative">
                      <button 
                        class="p-[10px] hover:text-ink hover:bg-canvas-sunken rounded-full"
                        @click="triggerFileUpload"
                      >
                        <svg class="w-5 h-5" viewBox="0 0 24 24" fill="none" stroke="currentColor">
                          <path d="M19 3H5C3.89543 3 3 3.89543 3 5V19C3 20.1046 3.89543 21 5 21H19C20.1046 21 21 20.1046 21 19V5C21 3.89543 20.1046 3 19 3Z" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                          <path d="M8.5 10C9.32843 10 10 9.32843 10 8.5C10 7.67157 9.32843 7 8.5 7C7.67157 7 7 7.67157 7 8.5C7 9.32843 7.67157 10 8.5 10Z" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                          <path d="M21 15L16 10L5 21" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                        </svg>
                      </button>
                      <input 
                        type="file" 
                        ref="fileInputRef" 
                        class="hidden" 
                        accept="image/*"
                        @change="handleFileChange"
                      />
                    </div>
                  </div>
                  <div class="flex items-center gap-2">
                    <!-- 发送按钮 -->
                    <button 
                      class="st-btn st-btn-primary w-[64px] h-10 text-[15px]"
                      :class="{'opacity-50': (!commentContent.trim() && !commentImage) || isPublishingComment}"
                      :disabled="isPublishingComment"
                      @click="handlePublishComment"
                    >
                      发送
                    </button>
                    <button class="border border-line w-[64px] h-[40px] text-[15px] 
                    font-bold text-ink-soft hover:text-ink hover:bg-canvas-sunken 
                    rounded-full cursor-pointer" @click="onCancel">
                      取消
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </div>

    </template>
    <!-- 删除评论二次确认 -->
    <ConfirmDialog
      v-model:visible="showDeleteCommentDialog"
      title="删除评论"
      message="删除后该评论及其回复将一并删除，确定删除吗？"
      confirm-text="删除"
      danger
      :loading="deleteCommentLoading"
      @confirm="confirmDeleteComment"
    />
  </div>
</template>

<script setup>
import { ref, computed, nextTick, watch, onBeforeUnmount, inject, onMounted } from 'vue'
import gsap from 'gsap'
import CommentList from './CommentList.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import { captureCommentSubmission, createCommentPageState, normalizeComment, mergeCommentPage, flattenCommentPages, nextMissingCommentPage, createCommentTargetNavigator, createCommentScrollAnchor, scrollCommentIntoContainer } from '@/utils/commentContext'
import ImageCarousel from '@/components/common/ImageCarousel.vue'
import VideoPlayer from '@/components/common/VideoPlayer.vue'
import { getNoteDetail, likeNote, unlikeNote, collectNote, uncollectNote, isLikedAndCollectedData } from '@/api/note' // 获取笔记详情的API
import { getCommentList, getCommentContext, publishComment, getChildCommentList, likeComment, unlikeComment, deleteComment } from '@/api/comment'
import { fetchIdempotentToken } from '@/api/idempotent'
import ConfirmDialog from '@/components/common/ConfirmDialog.vue'
import { followUser, unfollowUser, isFollowedUser } from '@/api/relation'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { useNoteStore } from '@/stores/note'
import { message } from '@/utils/message'
import { uploadFile } from '@/api/file' // 导入文件上传API

const router = useRouter()
const userStore = useUserStore()
const noteStore = useNoteStore()

const props = defineProps({
  targetCommentId: { type: String, default: '' },
  noteId: {
    type: [String, Number],
    default: ''
  },
  note: {
    type: Object,
    default: () => ({})
  }
})

const currNote = ref({})
const currNoteId = ref('')
const followStatus = ref(0)
const noteError = ref('')
let detailGeneration = 0
let commentGeneration = 0
let publishOperation = null
const contextUnavailableReason = ref('')
const commentLoading = ref(false)
const commentError = ref('')
const isLocatingComment = ref(false)
const commentLocationError = ref('')
const captureOwner = () => ({ session: userStore.captureSession(), generation: detailGeneration, commentGeneration, noteId: String(currNoteId.value), mode: 'ALL' })
const ownsDetail = (owner) => userStore.isCurrentSession(owner.session) && owner.generation === detailGeneration && owner.noteId === String(currNoteId.value)
const ownsComments = (owner) => ownsDetail(owner) && owner.commentGeneration === commentGeneration

// 打开详情时卡片上已经加载好的封面：数据到位前先顶上媒体区
const optimisticCover = computed(() => {
  const hero = noteStore.pendingHero
  if (!hero || String(hero.noteId) !== String(props.noteId)) return ''
  return hero.cover || ''
})

// 媒体区形态：图片 / 视频 / 占位封面（数据未到位）
const mediaKind = computed(() => {
  if (currNote.value.type === 0) return 'image'
  if (currNote.value.type === 1) return 'video'
  return optimisticCover.value ? 'cover' : 'none'
})

const mediaImages = computed(() => {
  const images = currNote.value.imgUris || []
  if (images.length) return images
  return optimisticCover.value ? [optimisticCover.value] : []
})

const emit = defineEmits(['update:visible'])

// DOM 引用
const modalRef = ref(null)
const authorInfoRef = ref(null)
const contentRef = ref(null)
const scrollContainerRef = ref(null)
const overlayScrollContainer = inject('noteOverlayScrollContainer', ref(null))
const actualScrollContainer = () => window.matchMedia('(max-width: 1023px)').matches ? overlayScrollContainer.value || scrollContainerRef.value : scrollContainerRef.value
const commentScrollAnchor = createCommentScrollAnchor()
const scrollToComment = async (commentId, stabilize = false, isCurrent = () => true) => {
  const owner = captureOwner()
  await nextTick()
  if (!ownsComments(owner) || !isCurrent()) return
  const container = actualScrollContainer()
  const target = [...(container?.querySelectorAll('[data-comment-id]') || [])].find(element => element.dataset.commentId === String(commentId))
  if (!target || !container) return
  const reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)').matches
  if (stabilize) commentScrollAnchor.start(container, target, { reducedMotion, isCurrent: () => ownsComments(owner) && isCurrent() })
  else { commentScrollAnchor.stop(); scrollCommentIntoContainer(container, target, reducedMotion ? 'auto' : 'smooth') }
}

const isScrolled = ref(false)

const handleScroll = (e) => {
  // 设置 isScrolled 状态
  isScrolled.value = e.target.scrollTop > 0
  
  const container = e.target
  const scrollTop = container.scrollTop
  const scrollHeight = container.scrollHeight
  const clientHeight = container.clientHeight

  // 当滚动到距离底部 50px 时触发加载
  if (scrollHeight - scrollTop - clientHeight < 50) {
    loadMoreComments()
  }
}

const comments = ref([])
const rootCommentPaging = ref(createCommentPageState())
const rootPageFlights = new Map()
const childPageFlights = new Map()
const commentRequestControllers = new Set()


const commentTotal = ref(0)
const hasMoreComments = computed(() => nextMissingCommentPage(rootCommentPaging.value) !== null)
const moreCommentsCount = ref(7)

// 后端一级评论返回 firstReplyComment（最早的一条回复），这里转成前端 childComments 数组展示
const mapCommentItem = (comment, previous) => {
  const normalized = normalizeComment(comment)
  const firstReplyComment = comment.firstReplyComment ? normalizeComment(comment.firstReplyComment) : null
  const childPaging = previous?.childPaging || createCommentPageState()
  if (!previous?.childPaging) {
    if (firstReplyComment) childPaging.pages[0] = [firstReplyComment]
  }
  childPaging.totalCount = Number(comment.childCommentTotal || 0)
  childPaging.totalPages = Math.max(childPaging.totalPages, Math.ceil(childPaging.totalCount / 6))
  return { ...normalized, firstReplyComment, childPaging, childComments: flattenCommentPages(childPaging, deletedCommentIds) }
}

const commentRequest = (owner, request) => {
  const controller = request?.options?.signal ? null : new AbortController()
  if (controller) commentRequestControllers.add(controller)
  return { controller, options: { ...request?.options, signal: request?.options?.signal || controller.signal, session: owner.session } }
}
const requestOwnsComments = (owner, request) => ownsComments(owner) && (!request?.isCurrent || request.isCurrent())
const loadRootCommentPage = (pageNo, request, force = false) => {
  if (!force && Object.hasOwn(rootCommentPaging.value.pages, pageNo)) return Promise.resolve()
  if (rootPageFlights.has(pageNo)) return rootPageFlights.get(pageNo)
  const owner = captureOwner()
  if (!requestOwnsComments(owner, request)) return Promise.resolve()
  const ctx = commentRequest(owner, request)
  const flight = (async () => {
    const response = await getCommentList(owner.noteId, pageNo, ctx.options)
    if (!requestOwnsComments(owner, request)) return
    mergeCommentPage(rootCommentPaging.value, response, mapCommentItem)
    comments.value = flattenCommentPages(rootCommentPaging.value, deletedCommentIds)
    commentTotal.value = rootCommentPaging.value.totalCount
  })().finally(() => {
    if (ctx.controller) commentRequestControllers.delete(ctx.controller)
    if (rootPageFlights.get(pageNo) === flight) rootPageFlights.delete(pageNo)
  })
  rootPageFlights.set(pageNo, flight)
  return flight
}

// 加载更多评论
const isLoadingMoreComments = ref(false)

const loadMoreComments = async (requestedPage) => {
  const nextPage = Number.isInteger(requestedPage) ? requestedPage : nextMissingCommentPage(rootCommentPaging.value)
  if (nextPage === null || commentLoading.value || isLoadingMoreComments.value) return
  const owner = captureOwner()
  isLoadingMoreComments.value = true
  try {
    await loadRootCommentPage(nextPage)
    if (ownsComments(owner)) commentError.value = ''
  } catch {
    if (ownsComments(owner)) commentError.value = '更多评论加载失败，请重试'
  } finally {
    if (ownsComments(owner)) isLoadingMoreComments.value = false
  }
}

// 登录状态控制
const isLoggedIn = computed(() => !!userStore.token)

// 评论输入框状态
const isInputFocused = ref(false)
const commentInput = ref(null)
const commentContent = ref('')
// 评论发布中标记：防止连点产生重复评论
const isPublishingComment = ref(false)

// 添加回复对象状态
const replyTo = ref(null)

const showLoginModal = inject('showLoginModal')

// 聚焦评论输入框
const focusComment = () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  isInputFocused.value = true
  nextTick(() => {
    if (commentInput.value) {
      commentInput.value.focus()
    }
  })
}

// 回复一级或二级评论。CommentItem 已经把实际评论对象向上透传，
// 不需要依赖不存在的 isReply/replies 字段来判断层级。
const onReplyClick = (comment) => {
  if (!comment?.commentId) return

  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }

  replyTo.value = comment
  isInputFocused.value = true
  nextTick(() => {
    if (commentInput.value) {
      commentInput.value.focus()
    }
  })
}

// 清除回复
const clearReply = () => {
  replyTo.value = null
}

// 输入框失焦处理
const onInputBlur = (e) => {
  // 只有点击取消按钮时才会关闭输入框
  if (e.relatedTarget?.textContent === '取消') {
    isInputFocused.value = false
  }
}

// 监听评论内容
const isCommentEmpty = computed(() => !commentContent.value.trim())

// 评论图片相关
const commentImage = ref('')
const fileInputRef = ref(null)

// 修改取消评论处理
const onCancel = () => {
  isInputFocused.value = false
  commentContent.value = ''
  commentImage.value = '' // 清除已上传的图片
  replyTo.value = null  // 清除回复对象
  if (commentInput.value) {
    commentInput.value.blur()
  }
}

// 表情选择相关
const showEmojiPicker = ref(false)
const emojiButtonRef = ref(null)
const emojiPickerRef = ref(null)

// 常用表情列表
const emojiList = [
  '😀', '😃', '😄', '😁', '😆', '😅', '😂', '🤣', '😊', '😇',
  '🙂', '🙃', '😉', '😌', '😍', '🥰', '😘', '😗', '😙', '😚',
  '😋', '😛', '😝', '😜', '🤪', '🤨', '🧐', '🤓', '😎', '🤩',
  '🥳', '😏', '😒', '😞', '😔', '😟', '😕', '🙁', '☹️', '😣',
  '😖', '😫', '😩', '🥺', '😢', '😭', '😤', '😠', '😡', '🤬',
  '🤯', '😳', '🥵', '🥶', '😱', '😨', '😰', '😥', '😓', '🤗',
  '🤔', '🤭', '🤫', '🤥', '😶', '😐', '😑', '😬', '🙄', '😯',
  '😦', '😧', '😮', '😲', '🥱', '😴', '🤤', '😪', '😵', '🤐',
  '🥴', '🤢', '🤮', '🤧', '😷', '🤒', '🤕', '🤑', '🤠', '😈',
  '👿', '👹', '👺', '🤡', '💩', '👻', '💀', '☠️', '👽', '👾'
]

// 切换表情选择器显示状态
const toggleEmojiPicker = () => {
  showEmojiPicker.value = !showEmojiPicker.value
}

// 插入表情到评论内容
const insertEmoji = (emoji) => {
  commentContent.value += emoji
  // 选中表情后隐藏表情选择器
  showEmojiPicker.value = false
}

// 点击外部关闭表情选择器
const handleClickOutside = (event) => {
  if (
    showEmojiPicker.value && 
    emojiPickerRef.value && 
    !emojiPickerRef.value.contains(event.target) &&
    !emojiButtonRef.value.contains(event.target)
  ) {
    showEmojiPicker.value = false
  }
}

// 组件挂载时添加点击外部关闭事件
onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  nextTick(() => overlayScrollContainer.value?.addEventListener('scroll', handleScroll))
})

// 组件卸载时移除事件监听
onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
  overlayScrollContainer.value?.removeEventListener('scroll', handleScroll)
  detailGeneration += 1
  commentGeneration += 1
  cancelCommentLoading()
})



// 触发文件选择
const triggerFileUpload = () => {
  fileInputRef.value.click()
}

// 处理文件选择
const handleFileChange = (event) => {
  const owner = captureOwner()
  const file = event.target.files[0]
  if (!file) return
  
  // 检查文件类型
  if (!file.type.startsWith('image/')) {
    message.show('请选择图片文件')
    return
  }
  
  // 检查文件大小（限制为5MB）
  if (file.size > 5 * 1024 * 1024) {
    message.show('图片大小不能超过5MB')
    return
  }
  
  // 上传文件
  const formData = new FormData()
  formData.append('file', file)
  
  message.show('图片上传中...')
  
  uploadFile(formData).then(res => {
    if (!ownsComments(owner)) return
    if (res.success) {
      commentImage.value = res.data
    } else {
      message.show('图片上传失败')
    }
  }).catch(err => {
    if (!ownsComments(owner)) return
    console.error('图片上传错误:', err)
    message.show('图片上传失败')
  })
  
  // 重置文件输入，以便可以再次选择同一文件
  event.target.value = ''
}

// 移除已上传的图片
const removeImage = () => {
  commentImage.value = ''
}

// 在评论列表中查找指定评论ID的评论的父评论
const findParentComment = (commentId, commentsList) => {
  for (const comment of commentsList) {
    // 检查一级评论的子评论
    if (comment.childComments) {
      const childIndex = comment.childComments.findIndex(child => child.commentId === commentId)
      if (childIndex !== -1) {
        return { parentComment: comment, isChild: true, childIndex }
      }
    }
    // 检查一级评论
    if (comment.commentId === commentId) {
      return { parentComment: comment, isChild: false }
    }
  }
  return null
}

// Snapshot all mutable fields before the first await; results belong to this note and mode.
const handlePublishComment = async () => {
  if (isPublishingComment.value) return
  if (!isLoggedIn.value) { showLoginModal.value = true; return }
  if (!commentContent.value.trim() && !commentImage.value) {
    message.show('请输入评论内容或上传图片')
    return
  }
  const owner = captureOwner()
  const submission = captureCommentSubmission({ noteId: currNoteId.value, content: commentContent.value, imageUrl: commentImage.value, reply: replyTo.value, profile: userStore.profile, session: owner.session, generation: owner.generation, mode: owner.mode })
  const operation = Symbol('comment-publish')
  publishOperation = operation
  isPublishingComment.value = true
  try {
    const idempotentToken = await fetchIdempotentToken()
    if (!ownsComments(owner)) return
    const res = await publishComment({ noteId: submission.noteId, content: submission.content, imageUrl: submission.imageUrl, replyCommentId: submission.reply?.commentId }, idempotentToken)
    if (!ownsComments(owner)) return
    if (!res.success) { message.show(res.message || '评论失败，请稍后重试'); return }
    const newComment = {
      commentId: String(res.data), noteId: submission.noteId, userId: submission.profile.userId,
      content: submission.content, imageUrl: submission.imageUrl, createTime: '刚刚',
      nickname: submission.profile.nickname, avatar: submission.profile.avatar, likeTotal: 0,
      liked: false, childCommentTotal: 0, replyUserName: submission.reply?.nickname || '', isNewComment: true
    }
    const destination = comments.value
    if (submission.reply?.commentId) {
      const result = findParentComment(submission.reply.commentId, destination)
      if (result) {
        const parent = result.parentComment
        parent.childComments = parent.childComments || []
        parent.childComments.splice(result.isChild ? result.childIndex + 1 : 0, 0, newComment)
        parent.childPaging = parent.childPaging || createCommentPageState()
        const replyPage = result.isChild ? Object.keys(parent.childPaging.pages).find(pageNo => parent.childPaging.pages[pageNo].some(child => String(child.commentId) === String(submission.reply.commentId))) : '0'
        const pageItems = parent.childPaging.pages[replyPage || '0'] ||= []
        const replyIndex = pageItems.findIndex(child => String(child.commentId) === String(submission.reply.commentId))
        pageItems.splice(result.isChild && replyIndex >= 0 ? replyIndex + 1 : 0, 0, newComment)
        parent.childCommentTotal = Number(parent.childCommentTotal || 0) + 1
      }
    } else {
      const localComments = rootCommentPaging.value.pages[0] ||= []
      localComments.unshift(newComment)
      comments.value = [newComment, ...comments.value]
    }
    commentTotal.value = Number(commentTotal.value || 0) + 1
    currNote.value.commentTotal = Number(currNote.value.commentTotal || 0) + 1
    // Preserve a draft typed while the request was in flight.
    if (commentContent.value === submission.content && commentImage.value === submission.imageUrl && replyTo.value?.commentId === submission.reply?.commentId) {
      commentContent.value = ''
      commentImage.value = ''
      replyTo.value = null
      isInputFocused.value = false
      commentInput.value?.blur()
    }
    message.show('评论成功')
    void scrollToComment(newComment.commentId)
  } catch {
    if (ownsComments(owner)) message.show('评论失败，请稍后重试')
  } finally {
    if (publishOperation === operation) {
      isPublishingComment.value = false
      publishOperation = null
    }
  }
}

// 加载二级评论
const loadChildComments = (parentComment, requestedPage, request) => {
  const owner = captureOwner()
  if (!parentComment?.commentId || !requestOwnsComments(owner, request)) return Promise.resolve()
  parentComment.childPaging = parentComment.childPaging || createCommentPageState()
  const nextPage = Number.isInteger(requestedPage) ? requestedPage : nextMissingCommentPage(parentComment.childPaging)
  if (nextPage === null || Object.hasOwn(parentComment.childPaging.pages, nextPage)) return Promise.resolve()
  const key = `${parentComment.commentId}:${nextPage}`
  if (childPageFlights.has(key)) return childPageFlights.get(key)
  const ctx = commentRequest(owner, request)
  parentComment.isLoadingChildComments = true
  parentComment.childCommentError = ''
  const flight = (async () => {
    const response = await getChildCommentList(parentComment.commentId, nextPage, ctx.options)
    if (!requestOwnsComments(owner, request)) return
    mergeCommentPage(parentComment.childPaging, response)
    parentComment.childComments = flattenCommentPages(parentComment.childPaging, deletedCommentIds)
    parentComment.childCommentTotal = parentComment.childPaging.totalCount
    parentComment.hasMoreChildComments = nextMissingCommentPage(parentComment.childPaging) !== null
  })().finally(() => {
    if (ctx.controller) commentRequestControllers.delete(ctx.controller)
    if (childPageFlights.get(key) === flight) childPageFlights.delete(key)
    if (ownsComments(owner)) parentComment.isLoadingChildComments = [...childPageFlights.keys()].some(pageKey => pageKey.startsWith(`${parentComment.commentId}:`))
  })
  childPageFlights.set(key, flight)
  return flight
}

// 处理展开回复事件
const handleExpandReplies = async (event) => {
  const comment = event.comment || event
  const owner = captureOwner()
  try { await loadChildComments(comment, event.pageNo) }
  catch { if (ownsComments(owner)) comment.childCommentError = '回复加载失败，请重试' }
}

const targetNavigator = createCommentTargetNavigator({
  api: { getCommentContext }, sessionOwner: userStore,
  getRoots: () => comments.value, getRootPaging: () => rootCommentPaging.value,
  loadRootPage: loadRootCommentPage, loadChildPage: loadChildComments,
  onState: ({ loading, error, unavailableReason }) => {
    isLocatingComment.value = loading
    commentLocationError.value = error
    contextUnavailableReason.value = unavailableReason
  },
  onFound: (commentId, current) => scrollToComment(commentId, true, current)
})
const locateTargetComment = () => {
  const owner = captureOwner()
  return targetNavigator.locate(owner.noteId, props.targetCommentId, () => ownsComments(owner))
}
const cancelCommentLoading = () => {
  targetNavigator.cancel()
  commentScrollAnchor.stop()
  commentRequestControllers.forEach(controller => controller.abort())
  commentRequestControllers.clear()
  rootPageFlights.clear()
  childPageFlights.clear()
  isLocatingComment.value = false
}

// 添加点赞状态
const isNoteLiked = ref(false)
// 处理笔记点赞、取消点赞
const handleNoteLike = () => {
  const owner = captureOwner()
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }

  if (!isNoteLiked.value) {
    likeNote(currNoteId.value).then(res => {
    if (!ownsDetail(owner)) return
    if (res.success) {
      isNoteLiked.value = !isNoteLiked.value
      currNote.value.likeTotal++
      } else {
        message.show(res.message)
      }
    }).catch(() => {})
    return
  }
  
  unlikeNote(currNoteId.value).then(res => {
    if (!ownsDetail(owner)) return
    if (res.success) {
      isNoteLiked.value = !isNoteLiked.value
      currNote.value.likeTotal--
    }
  }).catch(() => {})
}


// 当前登录用户 ID：评论项据此判断是否展示删除按钮
const currentUserId = computed(() => userStore.profile?.userId ?? null)

// ── 删除评论 ─────────────────────────────────────────────
const showDeleteCommentDialog = ref(false)
const pendingDeleteComment = ref(null)
const deleteCommentLoading = ref(false)
// 刚删除成功的评论 ID：后端级联删除是异步的，校准刷新时需要把它们过滤掉，避免「删了又回来」
const deletedCommentIds = new Set()

// 点击评论里的「删除」：先弹二次确认
const onCommentDelete = (comment) => {
  if (!comment?.commentId) return
  pendingDeleteComment.value = comment
  showDeleteCommentDialog.value = true
}

// 从内存列表中移除被删除的评论，并按界面语义扣减计数
const removeCommentFromList = (targetComment) => {
  const targetId = targetComment.commentId
  let removedCount = 0

  const topIndex = comments.value.findIndex((item) => item.commentId === targetId)
  if (topIndex !== -1) {
    // 一级评论：连同已加载的子评论一起移除，后端会级联删除其下全部回复
    const removed = comments.value[topIndex]
    // 已加载的子评论数与服务端返回的子评论总数取较大值，避免计数扣少了
    const childCount = Math.max(
      Number(removed.childCommentTotal || 0),
      removed.childComments?.length || 0
    )
    comments.value.splice(topIndex, 1)
    removedCount = 1 + (Number.isNaN(childCount) ? 0 : childCount)
  } else {
    // 二级评论：从父评论的回复列表里移除
    for (const parent of comments.value) {
      const children = parent.childComments || []
      const childIndex = children.findIndex((child) => child.commentId === targetId)
      if (childIndex === -1) continue

      children.splice(childIndex, 1)
      parent.childCommentTotal = Math.max(0, Number(parent.childCommentTotal || 0) - 1)
      if (parent.firstReplyComment?.commentId === targetId) {
        parent.firstReplyComment = null
      }
      if (parent.childCommentTotal === 0) {
        parent.childComments = []
      }
      removedCount = 1
      break
    }
  }

  if (removedCount > 0) {
    commentTotal.value = Math.max(0, Number(commentTotal.value || 0) - removedCount)
    if (currNote.value) {
      currNote.value.commentTotal = Math.max(0, Number(currNote.value.commentTotal || 0) - removedCount)
    }
  }

  return removedCount
}

// 静默重拉第一页：用服务端数据校准列表与总数
// 后端级联删除与计数扣减是异步的，这里只降不升，避免计数被旧值顶回去
const refreshCommentList = () => {
  const owner = captureOwner()
  if (!currNoteId.value) return
  const localTotal = Number(commentTotal.value || 0)
  loadRootCommentPage(1, undefined, true).then(() => {
    if (!ownsComments(owner)) return
    const serverTotal = rootCommentPaging.value.totalCount
    const nextTotal = Math.min(serverTotal, localTotal)
    commentTotal.value = nextTotal
    if (currNote.value) {
      currNote.value.commentTotal = nextTotal
    }
  }).catch(() => {
    // 校准失败不影响已完成的乐观更新
  })
}

const confirmDeleteComment = () => {
  const owner = captureOwner()
  const comment = pendingDeleteComment.value
  if (!comment?.commentId || deleteCommentLoading.value) return

  deleteCommentLoading.value = true
  deleteComment(comment.commentId).then((res) => {
    if (!ownsComments(owner)) return
    if (!res.success) {
      message.show(res.message || '删除失败')
      return
    }

    deletedCommentIds.add(comment.commentId)
    if (String(comment.commentId) === props.targetCommentId || comment.childComments?.some(child => String(child.commentId) === props.targetCommentId)) {
      targetNavigator.cancel()
      commentScrollAnchor.stop()
      isLocatingComment.value = false
      contextUnavailableReason.value = 'COMMENT_UNAVAILABLE'
    }
    removeCommentFromList(comment)
    showDeleteCommentDialog.value = false
    pendingDeleteComment.value = null
    deleteCommentLoading.value = false
    refreshCommentList()
  }).catch(() => {
    if (ownsComments(owner)) message.show('删除失败')
  }).finally(() => {
    if (ownsComments(owner)) deleteCommentLoading.value = false
  })
}

// 添加评论点赞处理函数
const handleCommentLike = ({ comment, liked }) => {
  if (!isLoggedIn.value) { showLoginModal.value = true; return }
  if (comment.likeLoading) return
  const owner = captureOwner()
  comment.likeLoading = true


  if (liked) {
    likeComment(comment.commentId).then(res => {
    if (!ownsComments(owner)) return
    if (res.success) {
      // 直接更新传入的 comment 对象的点赞数
      comment.likeTotal++
      comment.liked = true
      } else {
        message.show(res.message)
      }
    }).catch(() => {}).finally(() => { if (ownsComments(owner)) comment.likeLoading = false })
    return
  }
  
  unlikeComment(comment.commentId).then(res => {
    if (!ownsComments(owner)) return
    if (res.success) {
      comment.likeTotal = Math.max(0, Number(comment.likeTotal || 0) - 1)
      comment.liked = false
    }
  }).catch(() => {}).finally(() => { if (ownsComments(owner)) comment.likeLoading = false })
}

// 添加收藏状态
const isNoteCollected = ref(false)

// 处理笔记收藏
const handleNoteCollect = () => {
  const owner = captureOwner()
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }
  if (!isNoteCollected.value) {
    collectNote(currNoteId.value).then(res => {
      if (!ownsDetail(owner)) return
      if (res.success) {
        isNoteCollected.value = !isNoteCollected.value
        currNote.value.collectTotal++
      } else {
        message.show(res.message)
      }
    }).catch(() => {})
    return
  }

  uncollectNote(currNoteId.value).then(res => {
    if (!ownsDetail(owner)) return
    if (res.success) {
      isNoteCollected.value = !isNoteCollected.value
      currNote.value.collectTotal--
    }
  }).catch(() => {})
}

// 分享：暂未接入分享能力，先给出占位提示
const handleShare = () => {
  message.show('分享功能敬请期待')
}

const handleFollow = () => {
  const owner = captureOwner()
  const creatorId = currNote.value.creatorId
  if (!userStore.token) {
    showLoginModal.value = true
    return
  }
  const wasFollowing = followStatus.value > 0
  const request = wasFollowing
    ? unfollowUser(currNote.value.creatorId)
    : followUser(currNote.value.creatorId)
  request.then(res => {
    if (!ownsDetail(owner)) return
    if (!res.success) {
      message.show(res.message)
      return
    }
    // 重新查询关注关系状态（关注后是否互关取决于对方，前端无法预判）
    isFollowedUser(creatorId).then(r => {
      if (ownsDetail(owner) && r.success) followStatus.value = Number(r.data) || 0
    }).catch(() => {})
    message.show(wasFollowing ? '取消关注成功' : '关注成功')
  }).catch(() => {})
}

const reloadComments = async () => {
  commentGeneration += 1
  cancelCommentLoading()
  const owner = captureOwner()
  rootCommentPaging.value = createCommentPageState()
  comments.value = []
  commentLoading.value = true
  commentError.value = ''
  commentLocationError.value = ''
  contextUnavailableReason.value = ''
  isLoadingMoreComments.value = false
  try {
    await loadRootCommentPage(1)
    if (!ownsComments(owner)) return
    currNote.value.commentTotal = commentTotal.value
  } catch (error) {
    if (ownsComments(owner)) commentError.value = error.message || '评论暂时无法加载，请重试'
  } finally {
    if (ownsComments(owner)) commentLoading.value = false
  }
  if (ownsComments(owner) && !commentError.value && props.targetCommentId) void locateTargetComment()
}

const initializeDetail = () => {
  detailGeneration += 1
  commentGeneration += 1
  cancelCommentLoading()
  publishOperation = null
  isPublishingComment.value = false
  deleteCommentLoading.value = false
  deletedCommentIds.clear()
  onCancel()
  currNoteId.value = props.noteId || ''
  currNote.value = { ...props.note }
  comments.value = []
  rootCommentPaging.value = createCommentPageState()
  commentTotal.value = 0
  isLoadingMoreComments.value = false
  commentLoading.value = !!props.noteId
  commentError.value = ''
  commentLocationError.value = ''
  contextUnavailableReason.value = ''
  noteError.value = ''
  followStatus.value = 0
  isNoteLiked.value = false
  isNoteCollected.value = false
  showDeleteCommentDialog.value = false
  pendingDeleteComment.value = null
  if (!props.noteId) return
  const owner = captureOwner()
  getNoteDetail(owner.noteId).then(response => {
    if (!ownsDetail(owner)) return
    if (!response.success) { noteError.value = response.message || '内容已被删除或不可查看'; return }
    currNote.value = {
      ...response.data,
      likeTotal: Number(response.data.likeTotal ?? props.note?.likeTotal ?? 0),
      collectTotal: Number(response.data.collectTotal ?? props.note?.collectTotal ?? 0),
      commentTotal: Number(response.data.commentTotal ?? props.note?.commentTotal ?? 0)
    }
    followStatus.value = Number(response.data.followStatus || 0)
    commentTotal.value = currNote.value.commentTotal
    void reloadComments()
  }).catch(() => {
    if (ownsDetail(owner)) noteError.value = '笔记加载失败，请稍后重试'
  })
  if (userStore.token) {
    isLikedAndCollectedData(owner.noteId).then(response => {
      if (!ownsDetail(owner) || !response.success) return
      isNoteLiked.value = !!response.data.isLiked
      isNoteCollected.value = !!response.data.isCollected
    }).catch(() => {})
  }
}

watch([() => props.noteId, () => props.targetCommentId, () => userStore.generation], initializeDetail, { immediate: true })
</script>

<style scoped>
.note-detail__error { grid-column: 1 / -1; align-self: center; }
.zoom-move {
  transition: transform 0.3s ease-out;
}

/* 防止图片溢出容器 */
img {
  max-height: 100%;
  width: auto;
}

.title {
    margin-bottom: 8px;
    font-weight: 600;
    font-size: 18px;
    line-height: 140%;
}

/* 正文：15px / 1.75 行高，贴近小红书的阅读密度 */
.note-content {
  font-size: 15px;
  line-height: 1.75;
  color: var(--color-ink);
  word-break: break-word;
}

.content-input {
  caret-color: var(--color-brand);
  margin: 0px;
  height: 40px;  /* 固定高度 */
  background-color: var(--color-canvas-sunken);
  border: none;
  padding: 0 10px;  /* 调整内边距 */
  border-radius: var(--radius-control);
  outline: none;
  display: flex;
  align-items: center;
  white-space: nowrap;  /* 防止换行 */
}

/* 未聚焦状态下隐藏滚动条 */
.content-input:not(:focus-within) {
  overflow: hidden;
}

/* 限制回复内容显示一行 */
.line-clamp-1 {
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 1;
  -webkit-box-orient: vertical;
}

.reply {
    color: var(--color-ink-faint);
    font-size: 14px;
}

.reply-content {
    color: var(--color-ink-soft);
    font-size: 14px;
    width: 100%;
    margin-top: 4px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
}

/* 隐藏输入框的默认 placeholder */
input::placeholder {
    color: transparent;
}

/* 聚焦时显示 placeholder */
input:focus::placeholder {
    color: var(--color-ink-faint);
}

/* 修改图片样式，确保图片适应容器 */
:deep(.carousel-container) {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 详情页媒体区：白底 + 图片完整居中展示 */
:deep(.carousel-image) {
  width: 100%;
  height: 100%;
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
  object-position: center;
}

:deep(.note-detail__media .bg-canvas-sunken) {
  background: var(--color-paper) !important;
  border-right: none !important;
}

/* 表情选择器样式 */
.emoji-picker {
  position: absolute;
  bottom: 45px;
  left: 0;
  max-height: 280px;
  background-color: var(--color-paper);
  border-radius: var(--radius-panel);
  box-shadow: var(--shadow-panel);
  padding: 10px;
  z-index: 100;
  overflow-y: auto;
}

.emoji-grid {
  display: grid;
  grid-template-columns: repeat(10, 1fr);
  gap: 5px;
}

.emoji-item {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  font-size: 28px;
  cursor: pointer;
  border-radius: 4px;
  transition: background-color 0.2s;
}

.emoji-item:hover {
  background-color: var(--color-canvas-sunken);
}

/* 点赞动画效果 */
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
  animation: like 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  transform-origin: center;
}

.animate-unlike {
  animation: unlike 0.4s cubic-bezier(0.175, 0.885, 0.32, 1.275);
  transform-origin: center;
}

/* 点赞 / 收藏激活态：填充与描边统一跟随品牌令牌 */
.is-on {
  color: var(--color-brand);
}

/* 防止动画重复播放 */
.animate-like, .animate-unlike {
  animation-fill-mode: forwards;
}

/* 防止动画过程中文字抖动 */
.interactions span {
  min-width: 1.5em;
  display: inline-block;
  text-align: left;
}

/* ===== 详情页外壳（依附于弹层卡片，保留原有内部滚动逻辑） ===== */
.note-detail {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 460px;
  height: 100%;
  overflow: hidden;
  background: var(--color-paper);
}

.note-detail__media {
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  background: var(--color-paper);
}

.note-detail__media-inner {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
}

/* 数据未到位时的占位封面：与轮播里的图片保持同样的等比撑满方式 */
.note-detail__hero {
  height: 100%;
  width: auto;
  max-width: 100%;
  object-fit: contain;
}

.note-detail__panel {
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--color-paper);
  border-left: 1px solid var(--color-line);
}

/* 作者行与底部互动栏不参与收缩，滚动全部交给正文 + 评论区 */
.note-detail__author,
.note-detail__footer {
  flex: none;
}

/* 标题 / 正文 / 评论区共用同一条滚动流 */
.note-detail__scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
}

@media (min-width: 1024px) {
  /* 面板高度受限于弹层卡片：长正文由内部滚动承接，
     而不是把评论区与底部互动栏顶出卡片 */
  .note-detail {
    grid-template-rows: minmax(0, 1fr);
  }

  .note-detail__panel {
    min-height: 0;
    overflow: hidden;
  }

  /* 令 cqh 以右栏滚动可视高度为基准（仅桌面端，避免移动端自动高度被尺寸包含压成 0） */
  .note-detail__scroll {
    container-type: size;
  }

  /* 正文限制在可视区约 1/3 内，超出部分在正文区域内滚动 */
  .note-detail__body {
    max-height: 33%;
    max-height: 33cqh;
    overflow-y: auto;
  }
}

@media (max-width: 1023px) {
  .note-detail {
    grid-template-columns: minmax(0, 1fr);
    height: auto;
    min-height: 0;
  }

  .note-detail__media {
    height: 56vh;
  }

  .note-detail__panel {
    border-left: none;
    border-top: 1px solid var(--color-line);
  }

  .note-detail__panel .note-detail__scroll {
    overflow: visible;
  }
}
</style>

<template>
  <div class="note-card">
    <div 
      class="relative rounded-lg overflow-hidden"
      @click="$emit('click', note)"
    >
      <img v-if="note.type === 0"
        :src="note.cover" 
        class="w-full object-cover border border-gray-200 rounded-2xl hover:brightness-80 cursor-pointer"
      />
      
      <video v-if="note.type === 1"
          ref="videoRef"
          :src="note.videoUri" 
          class="w-full object-cover border border-gray-200 rounded-2xl hover:brightness-80 cursor-pointer"
          preload="metadata"
          muted
        ></video>

      <div v-if="note.type === 1" class="absolute right-2 top-2 play-icon">
        <svg class="w-3 h-3" viewBox="0 0 24 24" fill="currentColor">
          <path d="M8 5v14l11-7z"/>
        </svg>
      </div>
    </div>

    <div class="p-[12px]">
      <h3 class="note-title">{{ note.title }}</h3>
      <div class="flex items-center">
        <router-link :to="`/user/profile/${note.creatorId}`">
          <img
          v-if="note.avatar"
          :src="note.avatar" 
          class="w-[20px] h-[20px] mr-[6px] rounded-full border border-gray-200"
        />
          <div 
            v-else 
            class="w-[20px] h-[20px] mr-[6px] rounded-full border border-gray-200
             flex items-center justify-center bg-gray-100 text-gray-400"
          >
            <svg class="w-3 h-3" viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <path d="M20 21v-2a4 4 0 00-4-4H8a4 4 0 00-4 4v2" stroke-width="2" stroke-linecap="round"/>
              <circle cx="12" cy="7" r="4" stroke-width="2" stroke-linecap="round"/>
            </svg>
          </div>
        </router-link>
        <span class="text-[12px] text-gray-600 hover:text-gray-800 flex-1 truncate">
          <router-link :to="`/user/profile/${note.creatorId}`">
            {{ note.nickname }}
          </router-link>
        </span>
        <button 
          class="flex items-center transition-colors group"
          @click.stop="toggleLike"
        >
          <svg 
            class="w-[16px] h-[16px] transition-all duration-300" 
            :class="{'scale-animation': isLiked}"
            viewBox="0 0 24 24" 
            :style="{
              fill: isLiked ? '#fe2c55' : 'none',
              stroke: isLiked ? '#fe2c55' : 'currentColor'
            }"
            stroke-width="2"
          >
            <path d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"/>
          </svg>
          <span 
            class="ml-1 text-[12px] text-gray-600 transition-all duration-300"
            :class="{'scale-animation': isLiked}"
          >
            {{ likeText }}
          </span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, inject, watch } from 'vue'
import { likeNote, unlikeNote } from '@/api/note'
import { message } from '@/utils/message'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const props = defineProps({
  note: {
    type: Object,
    required: true
  }
})

defineEmits(['click'])

// 点赞状态与数量
// 后端接口返回的 likeTotal 可能是数字（发现页），也可能是 “1.2万” 这类格式化字符串（已发布笔记列表）
const isLiked = ref(false)
const likeNum = ref(0)

// 将后端返回的点赞数解析为数值
const parseLikeTotal = (value) => {
  if (value === null || value === undefined || value === '') return 0
  if (typeof value === 'number') return value
  const text = String(value).trim()
  const wanMatch = text.match(/^(\d+(?:\.\d+)?)万$/)
  if (wanMatch) return Math.floor(parseFloat(wanMatch[1]) * 10000)
  const num = parseInt(text, 10)
  return Number.isNaN(num) ? 0 : num
}

// 与后端 NumberUtils.formatNumberString 保持一致：小于 1 万原样展示，大于等于 1 万转为 “x.x万”
const formatLikeTotal = (num) => {
  if (num < 10000) return String(num)
  if (num >= 100000000) return '9999万'
  return `${Math.floor(num / 1000) / 10}万`
}

const likeText = computed(() => formatLikeTotal(likeNum.value))

// 笔记数据变化（如重新进入页面、刷新列表）时同步点赞状态
watch(() => props.note, (note) => {
  if (!note) return
  isLiked.value = Boolean(note.isLiked)
  likeNum.value = parseLikeTotal(note.likeTotal)
}, { immediate: true })


// 登录状态控制
const isLoggedIn = computed(() => !!userStore.token)
const showLoginModal = inject('showLoginModal')

// 切换点赞状态
const toggleLike = () => {
  if (!isLoggedIn.value) {
    showLoginModal.value = true
    return
  }

  const noteId = props.note.id ?? props.note.noteId
  const nextLiked = !isLiked.value

  // 先本地更新点赞状态与数量，接口失败时回滚
  isLiked.value = nextLiked
  likeNum.value = Math.max(0, likeNum.value + (nextLiked ? 1 : -1))

  const rollback = () => {
    isLiked.value = !nextLiked
    likeNum.value = Math.max(0, likeNum.value + (nextLiked ? -1 : 1))
  }

  const request = nextLiked ? likeNote(noteId) : unlikeNote(noteId)
  request.then(res => {
    if (!res.success) {
      rollback()
      message.show(res.message)
    }
  }).catch(() => {
    rollback()
  })
}
</script>

<style scoped>
.note-title {
    margin-bottom: 8px;
    word-break: break-all;
    display: -webkit-box;
    -webkit-box-orient: vertical;
    -webkit-line-clamp: 2;
    overflow: hidden;
    font-weight: 500;
    font-size: 14px;
    line-height: 140%;
    color: #333;
}

/* 点赞动画 */
@keyframes scale {
  0% {
    transform: scale(1);
  }
  50% {
    transform: scale(1.2);
  }
  100% {
    transform: scale(1);
  }
}

.scale-animation {
  animation: scale 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 悬浮效果 */
button:hover svg {
  transform: scale(1.1);
}

.play-icon {
  display: flex
;
    align-items: center;
    justify-content: center;
    position: absolute;
    right: 14px;
    top: 14px;
    width: 20px;
    height: 20px;
    color: #fff;
    background: rgba(64,64,64,0.25);
    backdrop-filter: saturate(150%) blur(10px);
    border-radius: 20px;
}
</style>

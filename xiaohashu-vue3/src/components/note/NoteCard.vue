<template>
  <article
    class="note-card"
    :class="{ 'note-card--liked': isLiked }"
    role="button"
    tabindex="0"
    :aria-label="note.title || '查看笔记'"
    @click="$emit('click', note)"
    @keydown.enter.prevent="$emit('click', note)"
    @keydown.space.prevent="$emit('click', note)"
  >
    <div class="note-card__media" :class="{ 'note-card__media--empty': mediaKind === 'empty' }">
      <img
        v-if="mediaKind === 'image'"
        class="note-card__image"
        :src="note.cover"
        alt=""
        loading="lazy"
        @error="onMediaError"
      />

      <video
        v-else-if="mediaKind === 'video'"
        ref="videoRef"
        class="note-card__image"
        :src="note.videoUri"
        preload="metadata"
        muted
        @error="onMediaError"
      ></video>

      <!-- 后端可能返回没有封面的笔记，用书页占位撑住卡片比例 -->
      <span v-else class="note-card__placeholder" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="none">
          <path
            d="M5.5 15.5c2.2-.7 4.4-.1 6.5 1.6 2.1-1.7 4.3-2.3 6.5-1.6"
            stroke="currentColor"
            stroke-width="1.4"
            stroke-linecap="round"
          />
          <path d="M12 17.1v3.4" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
        </svg>
      </span>

      <span v-if="mediaKind === 'video'" class="note-card__play" aria-hidden="true">
        <svg viewBox="0 0 24 24" fill="currentColor">
          <path d="M8 5v14l11-7z" />
        </svg>
      </span>
    </div>

    <div class="note-card__body">
      <h3 class="note-card__title">{{ note.title }}</h3>

      <div class="note-card__meta">
        <router-link class="note-card__author" :to="`/user/profile/${note.creatorId}`" @click.stop>
          <img
            v-if="note.avatar && !avatarFailed"
            class="note-card__avatar"
            :src="note.avatar"
            alt=""
            @error="avatarFailed = true"
          />
          <span v-else class="note-card__avatar note-card__avatar--empty" aria-hidden="true">
            <svg viewBox="0 0 24 24" fill="none" stroke="currentColor">
              <path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" stroke-width="2" stroke-linecap="round" />
              <circle cx="12" cy="7" r="4" stroke-width="2" />
            </svg>
          </span>
          <span class="note-card__name">{{ note.nickname }}</span>
        </router-link>

        <button
          type="button"
          class="note-card__like"
          :class="{ 'note-card__like--active': isLiked }"
          :aria-pressed="isLiked ? 'true' : 'false'"
          :aria-label="isLiked ? '取消点赞' : '点赞'"
          @click.stop="toggleLike"
        >
          <svg class="note-card__heart" viewBox="0 0 24 24" :fill="isLiked ? 'currentColor' : 'none'">
            <path
              d="M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z"
              stroke="currentColor"
              stroke-width="1.6"
            />
          </svg>
          <span class="note-card__count st-num">{{ likeText }}</span>
        </button>
      </div>
    </div>
  </article>
</template>

<script setup>
import { computed, inject, ref, watch } from 'vue'
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

// 封面 / 视频 / 占位三种形态
// 后端存在历史数据：封面地址失效、返回 404 或跨域被拦截。
// 这里统一兜底为占位图，避免卡片塌陷成一条白条。
const mediaFailed = ref(false)
const avatarFailed = ref(false)

const onMediaError = () => {
  mediaFailed.value = true
}

watch(
  () => [props.note.cover, props.note.videoUri, props.note.avatar],
  () => {
    mediaFailed.value = false
    avatarFailed.value = false
  }
)

const mediaKind = computed(() => {
  if (mediaFailed.value) return 'empty'
  if (props.note.type === 1 && props.note.videoUri) return 'video'
  if (props.note.cover) return 'image'
  return 'empty'
})

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
watch(
  () => props.note,
  (note) => {
    if (!note) return
    isLiked.value = Boolean(note.isLiked)
    likeNum.value = parseLikeTotal(note.likeTotal)
  },
  { immediate: true }
)

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
  request
    .then((res) => {
      if (!res.success) {
        rollback()
        message.show(res.message)
      }
    })
    .catch(() => {
      rollback()
    })
}
</script>

<style scoped>
.note-card {
  display: flex;
  flex-direction: column;
  cursor: pointer;
  background: transparent;
}

.note-card:focus-visible {
  outline: none;
}

.note-card:focus-visible .note-card__media {
  outline: 2px solid var(--color-brand);
  outline-offset: 2px;
}

.note-card__media {
  position: relative;
  display: block;
  width: 100%;
  border-radius: var(--radius-media);
  background: var(--color-canvas-sunken);
  overflow: hidden;
}

.note-card__media--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  aspect-ratio: 3 / 4;
}

.note-card__placeholder {
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--color-line-strong);
}

.note-card__placeholder svg {
  width: 44px;
  height: 44px;
}

.note-card__image {
  display: block;
  width: 100%;
  height: auto;
  object-fit: cover;
  transition: transform 420ms var(--ease-standard);
}

/* 悬停：封面轻微放大，替代原先的卡片抬升 */
.note-card:hover .note-card__image,
.note-card:focus-visible .note-card__image {
  transform: scale(1.03);
}

.note-card__media--empty .note-card__placeholder {
  transition: transform 420ms var(--ease-standard);
}

.note-card:hover .note-card__media--empty .note-card__placeholder {
  transform: scale(1.06);
}

.note-card__play {
  position: absolute;
  right: 10px;
  top: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: var(--radius-pill);
  background: rgb(20 17 14 / 0.42);
  color: var(--color-paper);
}

.note-card__play svg {
  width: 12px;
  height: 12px;
}

.note-card__body {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 10px 2px 0;
}

.note-card__title {
  font-size: 16px;
  font-weight: 400;
  line-height: 1.45;
  color: var(--color-ink);
  word-break: break-all;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  overflow: hidden;
}

.note-card__meta {
  display: flex;
  align-items: center;
  gap: 8px;
}

.note-card__author {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
  flex: 1;
}

.note-card__avatar {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  border-radius: var(--radius-pill);
  object-fit: cover;
}

.note-card__avatar--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-canvas-sunken);
  color: var(--color-ink-faint);
}

.note-card__avatar--empty svg {
  width: 12px;
  height: 12px;
}

.note-card__name {
  min-width: 0;
  font-size: 12px;
  color: var(--color-ink-faint);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: color var(--motion-fast) var(--ease-standard);
}

.note-card__author:hover .note-card__name {
  color: var(--color-ink);
}

.note-card__like {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 2px 4px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--color-ink-faint);
  font-size: 12px;
  cursor: pointer;
  transition: color var(--motion-fast) var(--ease-standard);
}

.note-card__like:hover {
  color: var(--color-ink);
}

.note-card__like--active,
.note-card__like--active:hover {
  color: var(--color-brand);
}

.note-card__heart {
  width: 16px;
  height: 16px;
}

.note-card--liked .note-card__heart {
  animation: heart-beat 0.3s var(--ease-standard);
}

@keyframes heart-beat {
  50% {
    transform: scale(1.18);
  }
}
</style>

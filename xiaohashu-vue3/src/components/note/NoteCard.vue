<template>
  <article
    ref="rootRef"
    class="note-card"
    :class="{ 'note-card--liked': isLiked, 'note-card--menu-open': menuOpen }"
    role="button"
    tabindex="0"
    :aria-label="note.title || '查看笔记'"
    @click="onCardClick"
    @keydown.enter="onCardKeydown"
    @keydown.space="onCardKeydown"
  >
    <!-- data-note-cover 供详情展开动画定位「从哪张卡片长出来」（见 composables/noteTransition.js） -->
    <div
      class="note-card__media"
      :class="{ 'note-card__media--empty': mediaKind === 'empty' }"
      :data-note-cover="note.id ?? note.noteId"
    >
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

      <span
        v-if="mediaKind === 'video'"
        class="note-card__play"
        :class="{ 'note-card__play--shifted': ownerActions }"
        aria-hidden="true"
      >
        <svg viewBox="0 0 24 24" fill="currentColor">
          <path d="M8 5v14l11-7z" />
        </svg>
      </span>

      <!-- 置顶 / 仅自己可见角标 -->
      <div v-if="badges.length" class="note-card__badges">
        <span v-for="badge in badges" :key="badge" class="note-card__badge">{{ badge }}</span>
      </div>
    </div>

    <!-- 作者的「…」操作菜单：仅在自己的个人主页「笔记」tab 开启 -->
    <div v-if="ownerActions" class="note-card__owner" @click.stop>
      <button
        type="button"
        class="note-card__more"
        :aria-expanded="menuOpen ? 'true' : 'false'"
        aria-label="更多操作"
        @click="toggleMenu"
      >
        <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
          <circle cx="5" cy="12" r="1.6" />
          <circle cx="12" cy="12" r="1.6" />
          <circle cx="19" cy="12" r="1.6" />
        </svg>
      </button>

      <Transition name="note-menu">
        <div v-if="menuOpen" class="note-card__menu" role="menu">
          <button type="button" class="note-card__menu-item" role="menuitem" @click="onMenuAction('top')">
            {{ isTop ? '取消置顶' : '置顶' }}
          </button>
          <button type="button" class="note-card__menu-item" role="menuitem" @click="onMenuAction('visible')">
            {{ isPrivate ? '设为公开' : '设为仅自己可见' }}
          </button>
          <button type="button" class="note-card__menu-item" role="menuitem" @click="onMenuAction('edit')">
            编辑笔记
          </button>
          <button
            type="button"
            class="note-card__menu-item note-card__menu-item--danger"
            role="menuitem"
            @click="onMenuAction('delete')"
          >
            删除笔记
          </button>
        </div>
      </Transition>
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
import { computed, inject, onBeforeUnmount, ref, watch } from 'vue'
import { likeNote, unlikeNote } from '@/api/note'
import { message } from '@/utils/message'
import { useUserStore } from '@/stores/user'

const userStore = useUserStore()

const props = defineProps({
  note: {
    type: Object,
    required: true
  },
  // 仅在本人主页的「笔记」tab 开启「…」操作菜单
  ownerActions: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['click', 'edit', 'top', 'visible', 'delete'])

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

// 卡片状态：置顶与可见性
// 后端旧缓存反序列化后 isTop / visible 可能为 null，一律做严格判断
const isTop = computed(() => props.note?.isTop === true)
const isPrivate = computed(() => Number(props.note?.visible) === 1)

const badges = computed(() => {
  const list = []
  if (isTop.value) list.push('置顶')
  if (isPrivate.value) list.push('仅自己可见')
  return list
})

// 「…」操作菜单
const rootRef = ref(null)
const menuOpen = ref(false)

const closeMenu = () => {
  menuOpen.value = false
}

const toggleMenu = () => {
  menuOpen.value = !menuOpen.value
}

const onMenuAction = (action) => {
  closeMenu()
  emit(action, props.note)
}

const onCardClick = () => {
  closeMenu()
  emit('click', props.note)
}

// 键盘打开卡片：只在卡片本身获得焦点时响应
// 菜单内的按钮需要保留自己的回车/空格行为
const onCardKeydown = (event) => {
  if (event.target !== rootRef.value) return
  event.preventDefault()
  onCardClick()
}

const onDocumentClick = (event) => {
  if (rootRef.value && !rootRef.value.contains(event.target)) closeMenu()
}

const onPageScroll = () => closeMenu()

const onKeydown = (event) => {
  if (event.key === 'Escape') closeMenu()
}

// 菜单展开时才挂全局监听：点击空白、页面滚动、Esc 都收起菜单
watch(menuOpen, (open) => {
  if (open) {
    document.addEventListener('click', onDocumentClick)
    window.addEventListener('scroll', onPageScroll, true)
    window.addEventListener('keydown', onKeydown)
  } else {
    document.removeEventListener('click', onDocumentClick)
    window.removeEventListener('scroll', onPageScroll, true)
    window.removeEventListener('keydown', onKeydown)
  }
})

// 列表刷新 / 删除后卡片数据被替换时收起菜单
watch(() => props.note, closeMenu)

onBeforeUnmount(() => {
  document.removeEventListener('click', onDocumentClick)
  window.removeEventListener('scroll', onPageScroll, true)
  window.removeEventListener('keydown', onKeydown)
})
</script>

<style scoped>
.note-card {
  position: relative;
  display: flex;
  flex-direction: column;
  cursor: pointer;
  background: transparent;
}

/* 菜单展开时抬升整张卡片，避免下拉被下方卡片遮住 */
.note-card--menu-open {
  z-index: 5;
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

/* 开启操作菜单时播放角标左移，给「…」让位 */
.note-card__play--shifted {
  right: 42px;
}

.note-card__badges {
  position: absolute;
  left: 10px;
  top: 10px;
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  max-width: calc(100% - 56px);
}

.note-card__badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: var(--radius-pill);
  background: rgb(20 17 14 / 0.42);
  color: var(--color-paper);
  font-size: 12px;
  line-height: 1;
  white-space: nowrap;
}

.note-card__owner {
  position: absolute;
  right: 10px;
  top: 10px;
  z-index: 6;
}

.note-card__more {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  padding: 0;
  border: none;
  border-radius: var(--radius-pill);
  background: rgb(20 17 14 / 0.42);
  color: var(--color-paper);
  opacity: 0.72;
  cursor: pointer;
  transition: opacity var(--motion-fast) var(--ease-standard), background var(--motion-fast) var(--ease-standard);
}

.note-card__more:hover,
.note-card__more[aria-expanded='true'] {
  opacity: 1;
  background: rgb(20 17 14 / 0.6);
}

.note-card__more svg {
  width: 16px;
  height: 16px;
}

.note-card__menu {
  position: absolute;
  right: 0;
  top: 30px;
  min-width: 148px;
  padding: 6px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-card);
  background: var(--color-paper);
  box-shadow: var(--shadow-panel);
}

.note-card__menu-item {
  display: block;
  width: 100%;
  padding: 8px 12px;
  border: none;
  border-radius: var(--radius-control);
  background: none;
  color: var(--color-ink);
  font-size: 14px;
  line-height: 20px;
  text-align: left;
  white-space: nowrap;
  cursor: pointer;
  transition: background var(--motion-fast) var(--ease-standard), color var(--motion-fast) var(--ease-standard);
}

.note-card__menu-item:hover {
  background: var(--color-canvas-sunken);
}

.note-card__menu-item--danger {
  color: var(--color-brand);
}

.note-card__menu-item--danger:hover {
  color: var(--color-brand);
  background: rgb(255 36 66 / 0.08);
}

.note-menu-enter-active {
  transition:
    opacity 180ms var(--ease-standard),
    transform 180ms var(--ease-standard);
}

.note-menu-leave-active {
  transition:
    opacity 120ms var(--ease-standard),
    transform 120ms var(--ease-standard);
}

.note-menu-enter-from,
.note-menu-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.97);
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

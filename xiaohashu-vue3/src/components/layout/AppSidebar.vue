<template>
  <aside class="sidebar" aria-label="站点导航">
    <nav class="sidebar__nav">
      <!-- 首页 -->
      <router-link to="/discover" class="nav-item" active-class="nav-item--active">
        <span class="nav-item__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <path
              d="M4 10.6 12 4l8 6.6V19a1.4 1.4 0 0 1-1.4 1.4h-3.2v-5.6H10.6v5.6H5.4A1.4 1.4 0 0 1 4 19v-8.4Z"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </span>
        <span class="nav-item__label">首页</span>
      </router-link>

      <!-- AI 助手 -->
      <button type="button" class="nav-item" @click="handleAiEntry">
        <span class="nav-item__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <path
              d="M20.5 11.4c0 3.9-3.8 7.1-8.5 7.1-.9 0-1.8-.1-2.6-.3l-4.4 2 1.2-3.6c-1.3-1.3-2.2-3.1-2.2-5.2C4 7.5 7.8 4.3 12.5 4.3s8 3.2 8 7.1Z"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <path
              d="M12.5 8.2l.9 1.9 2 .3-1.5 1.4.4 2-1.8-1-1.8 1 .4-2-1.5-1.4 2-.3.9-1.9Z"
              fill="currentColor"
            />
          </svg>
        </span>
        <span class="nav-item__label">
          AI 助手
          <sup class="nav-item__badge">ai</sup>
        </span>
      </button>

      <!-- 发布 -->
      <button type="button" class="nav-item" @click="handlePublish">
        <span class="nav-item__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <rect
              x="3.6"
              y="3.6"
              width="16.8"
              height="16.8"
              rx="5"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <path d="M12 8.4v7.2M8.4 12h7.2" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
          </svg>
        </span>
        <span class="nav-item__label">发布</span>
      </button>

      <!-- 通知 -->
      <router-link to="/notifications" class="nav-item" active-class="nav-item--active">
        <span class="nav-item__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <path
              d="M18 8A6 6 0 1 0 6 8c0 7-3 9-3 9h18s-3-2-3-9Z"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <path d="M13.7 21a2 2 0 0 1-3.4 0" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
          </svg>
        </span>
        <span class="nav-item__label">通知</span>
      </router-link>

      <!-- 我 -->
      <router-link
        v-if="isLoggedIn"
        :to="`/user/profile/${profile.userId}`"
        class="nav-item"
        active-class="nav-item--active"
      >
        <img class="nav-item__avatar" :src="profile.avatar || defaultAvatar" alt="" />
        <span class="nav-item__label">我</span>
      </router-link>
      <button v-else type="button" class="nav-item" @click="handleShowLogin">
        <span class="nav-item__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <path
              d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"
              stroke="currentColor"
              stroke-width="1.7"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
            <circle cx="12" cy="7" r="4" stroke="currentColor" stroke-width="1.7" />
          </svg>
        </span>
        <span class="nav-item__label">我</span>
      </button>
    </nav>

    <div class="sidebar__foot">
      <button
        type="button"
        class="nav-item"
        aria-haspopup="menu"
        :aria-expanded="showMoreMenu ? 'true' : 'false'"
        @click="showMoreMenu = !showMoreMenu"
      >
        <span class="nav-item__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
          </svg>
        </span>
        <span class="nav-item__label">更多</span>
      </button>

      <button type="button" class="nav-item" @click="showAboutModal = true">
        <span class="nav-item__icon" aria-hidden="true">
          <svg viewBox="0 0 24 24" fill="none">
            <circle cx="12" cy="12" r="8.6" stroke="currentColor" stroke-width="1.7" />
            <path d="M12 10.8v5.4" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" />
            <circle cx="12" cy="7.9" r="0.9" fill="currentColor" />
          </svg>
        </span>
        <span class="nav-item__label">关于我们</span>
      </button>

      <MoreMenu v-model:visible="showMoreMenu" @about="showAboutModal = true" />
    </div>

    <AboutModal v-model:visible="showAboutModal" />
  </aside>
</template>

<script setup>
import { computed, inject, ref } from 'vue'
import { useUserStore } from '@/stores/user'
import MoreMenu from './MoreMenu.vue'
import AboutModal from './AboutModal.vue'
import { message } from '@/utils/message'
import defaultAvatar from '@/assets/avatar.png'

const userStore = useUserStore()

const showLoginModal = inject('showLoginModal')
const showPublishModal = inject('showPublishModal', null)

const showMoreMenu = ref(false)
const showAboutModal = ref(false)

const isLoggedIn = computed(() => !!userStore.token)
const profile = computed(() => userStore.profile || {})

const handleShowLogin = () => {
  if (showLoginModal) showLoginModal.value = true
}

const handleAiEntry = () => {
  message.show('AI 助手即将上线，敬请期待')
}

const handlePublish = () => {
  if (!isLoggedIn.value) {
    handleShowLogin()
    return
  }
  if (showPublishModal) {
    showPublishModal.value = true
  }
}
</script>

<style scoped>
.sidebar {
  position: fixed;
  top: var(--header-h);
  left: 0;
  bottom: 0;
  width: var(--sidebar-w);
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 12px 18px 24px;
  background: var(--color-canvas);
  overflow-y: auto;
  z-index: 30;
}

.sidebar__nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sidebar__foot {
  position: relative;
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-top: 16px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  height: 48px;
  padding: 0 16px;
  border: none;
  border-radius: 24px;
  background: transparent;
  color: var(--color-ink);
  font-size: 16px;
  font-weight: 400;
  line-height: 1;
  text-align: left;
  cursor: pointer;
  transition: background-color var(--motion-fast) var(--ease-standard);
}

.nav-item:hover {
  background: var(--color-canvas-sunken);
}

.nav-item--active {
  background: var(--color-canvas-sunken);
  font-weight: 500;
}

.nav-item__icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  flex-shrink: 0;
}

.nav-item__icon svg {
  width: 24px;
  height: 24px;
}

.nav-item__avatar {
  width: 24px;
  height: 24px;
  flex-shrink: 0;
  border-radius: var(--radius-pill);
  object-fit: cover;
}

.nav-item__label {
  white-space: nowrap;
}

.nav-item__badge {
  margin-left: 2px;
  color: #00b96b;
  font-size: 10px;
  font-weight: 600;
  vertical-align: super;
}

@media (max-width: 1023px) {
  .sidebar {
    display: none;
  }
}
</style>

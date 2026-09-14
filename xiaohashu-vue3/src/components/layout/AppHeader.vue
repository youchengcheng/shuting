<template>
  <header class="app-header">
    <div class="app-header__inner">
      <!-- 品牌 -->
      <router-link to="/discover" class="brand" aria-label="书亭 · 返回首页">
        <BrandLogo :size="30" />
      </router-link>

      <!-- 全局搜索 -->
      <form class="search" role="search" @submit.prevent="handleSearch">
        <input
          v-model="searchKeyword"
          class="search__input"
          type="text"
          placeholder="搜索书亭"
          aria-label="搜索书亭"
        />

        <button
          v-if="searchKeyword"
          type="button"
          class="search__clear"
          aria-label="清空搜索"
          @click="searchKeyword = ''"
        >
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <circle cx="12" cy="12" r="9" fill="currentColor" />
            <path d="M15 9l-6 6M9 9l6 6" stroke="#fff" stroke-width="1.6" stroke-linecap="round" />
          </svg>
        </button>

        <button type="submit" class="search__submit" aria-label="搜索">
          <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <path
              d="M21 21l-4.35-4.35M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
      </form>

      <!-- 右侧：账号 -->
      <div class="actions">
        <div v-if="isLoggedIn" class="account" ref="accountRef">
          <button
            type="button"
            class="account__trigger"
            aria-haspopup="menu"
            :aria-expanded="menuOpen ? 'true' : 'false'"
            aria-label="账号菜单"
            @click="menuOpen = !menuOpen"
          >
            <img class="account__avatar" :src="avatarUrl" alt="" />
          </button>

          <Transition name="menu">
            <div v-if="menuOpen" class="menu" role="menu">
              <div class="menu__head">
                <p class="menu__name">{{ profile.nickname || '书亭用户' }}</p>
                <p v-if="profile.xiaohashuId" class="menu__id st-num">书亭号：{{ profile.xiaohashuId }}</p>
              </div>
              <div class="menu__divider"></div>
              <button type="button" class="menu__item" role="menuitem" @click="goProfile">我的主页</button>
              <div class="menu__divider"></div>
              <button type="button" class="menu__item menu__item--danger" role="menuitem" @click="handleLogout">
                退出登录
              </button>
            </div>
          </Transition>
        </div>

        <button v-else type="button" class="login-entry" @click="showLogin">登录</button>
      </div>
    </div>
  </header>
</template>

<script setup>
import { computed, inject, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import BrandLogo from '@/components/common/BrandLogo.vue'
import { logout } from '@/api/auth'
import { message } from '@/utils/message'
import defaultAvatar from '@/assets/avatar.png'

const router = useRouter()
const route = useRoute()
const userStore = useUserStore()

const showLoginModal = inject('showLoginModal')

const searchKeyword = ref(route.query.keyword || '')
const menuOpen = ref(false)
const accountRef = ref(null)

const isLoggedIn = computed(() => !!userStore.token)
const profile = computed(() => userStore.profile || {})
const avatarUrl = computed(() => profile.value.avatar || defaultAvatar)

watch(
  () => route.query.keyword,
  (keyword) => {
    searchKeyword.value = keyword || ''
  }
)

const handleSearch = () => {
  const keyword = searchKeyword.value.trim()
  if (!keyword) return
  router.push({ name: 'Search', query: { keyword } })
}

const showLogin = () => {
  if (showLoginModal) showLoginModal.value = true
}

const goProfile = () => {
  menuOpen.value = false
  const userId = profile.value.userId
  if (!userId) return
  router.push(`/user/profile/${userId}`)
}

const handleLogout = () => {
  menuOpen.value = false
  logout().then((res) => {
    if (res.success) {
      userStore.logout()
      message.show('已退出登录')
      router.push('/discover')
    } else {
      message.show(res.message || '退出登录失败')
    }
  })
}

const handleClickOutside = (event) => {
  if (!menuOpen.value) return
  if (accountRef.value && !accountRef.value.contains(event.target)) {
    menuOpen.value = false
  }
}

const handleEsc = (event) => {
  if (event.key === 'Escape') menuOpen.value = false
}

onMounted(() => {
  document.addEventListener('click', handleClickOutside)
  document.addEventListener('keydown', handleEsc)
})

onBeforeUnmount(() => {
  document.removeEventListener('click', handleClickOutside)
  document.removeEventListener('keydown', handleEsc)
})
</script>

<style scoped>
.app-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 60;
  height: var(--header-h);
  background: rgb(255 255 255 / 0.92);
  backdrop-filter: saturate(180%) blur(12px);
  -webkit-backdrop-filter: saturate(180%) blur(12px);
}

.app-header__inner {
  position: relative;
  height: 100%;
  display: flex;
  align-items: center;
  gap: 24px;
  padding: 0 24px;
}

.brand {
  display: inline-flex;
  align-items: center;
  flex-shrink: 0;
}

.search {
  position: relative;
  display: flex;
  align-items: center;
  flex: 1 1 auto;
  width: 100%;
  max-width: 600px;
  height: 44px;
  margin: 0 auto;
  padding: 0 6px 0 20px;
  border: 1px solid transparent;
  border-radius: var(--radius-pill);
  background: var(--color-canvas-sunken);
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    border-color var(--motion-fast) var(--ease-standard);
}

.search:focus-within {
  background: var(--color-paper);
  border-color: var(--color-line-strong);
}

.search__input {
  flex: 1;
  min-width: 0;
  height: 100%;
  border: none;
  background: transparent;
  color: var(--color-ink);
  font-size: 15px;
  outline: none;
}

.search__input::placeholder {
  color: var(--color-ink-faint);
}

.search__clear {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  margin-right: 4px;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  color: rgb(0 0 0 / 0.22);
  cursor: pointer;
  transition: color var(--motion-fast) var(--ease-standard);
}

.search__clear:hover {
  color: rgb(0 0 0 / 0.38);
}

.search__clear svg {
  width: 18px;
  height: 18px;
}

.search__submit {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: var(--radius-pill);
  background: transparent;
  color: var(--color-ink-faint);
  cursor: pointer;
  transition: color var(--motion-fast) var(--ease-standard);
}

.search__submit:hover {
  color: var(--color-ink);
}

.search__submit svg {
  width: 20px;
  height: 20px;
}

.actions {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 120px;
  justify-content: flex-end;
  flex-shrink: 0;
}

.login-entry {
  padding: 0;
  border: none;
  background: transparent;
  color: var(--color-ink);
  font-size: 16px;
  cursor: pointer;
  transition: color var(--motion-fast) var(--ease-standard);
}

.login-entry:hover {
  color: var(--color-brand);
}

.account {
  position: relative;
}

.account__trigger {
  display: block;
  padding: 0;
  border: none;
  background: transparent;
  cursor: pointer;
}

.account__avatar {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-pill);
  object-fit: cover;
  border: 1px solid var(--color-line);
}

.menu {
  position: absolute;
  top: calc(100% + 10px);
  right: 0;
  width: 220px;
  padding: 8px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-panel);
  background: var(--color-paper);
  box-shadow: var(--shadow-panel);
}

.menu__head {
  padding: 8px 10px 10px;
}

.menu__name {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-ink);
}

.menu__id {
  margin: 2px 0 0;
  font-size: 12px;
  color: var(--color-ink-faint);
}

.menu__divider {
  height: 1px;
  margin: 4px 0;
  background: var(--color-line);
}

.menu__item {
  display: block;
  width: 100%;
  padding: 9px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  text-align: left;
  font-size: 14px;
  color: var(--color-ink-soft);
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}

.menu__item:hover {
  background: var(--color-canvas-sunken);
  color: var(--color-ink);
}

.menu__item--danger:hover {
  color: var(--color-ink);
}

.menu-enter-active {
  transition:
    opacity 180ms var(--ease-standard),
    transform 180ms var(--ease-standard);
}

.menu-leave-active {
  transition:
    opacity 120ms var(--ease-standard),
    transform 120ms var(--ease-standard);
}

.menu-enter-from,
.menu-leave-to {
  opacity: 0;
  transform: translateY(-4px) scale(0.97);
}

@media (max-width: 1023px) {
  .app-header__inner {
    gap: 16px;
    padding: 0 18px;
  }

  .actions {
    min-width: 0;
  }
}

@media (max-width: 767px) {
  .app-header__inner {
    padding: 0 16px;
  }

  .search {
    height: 40px;
    padding-left: 16px;
  }
}
</style>

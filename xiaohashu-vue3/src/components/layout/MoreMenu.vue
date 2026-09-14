<template>
  <Transition name="dropdown">
    <div v-if="visible" class="more-menu" role="menu">
      <button type="button" class="more-menu__item" role="menuitem" @click="openAbout">关于书亭</button>
      <a
        class="more-menu__item"
        href="https://www.quanxiaoha.com/column/"
        target="_blank"
        rel="noopener noreferrer"
        role="menuitem"
      >
        <span>犬小哈实战专栏</span>
        <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
          <path
            d="M7 17L17 7M17 7H9M17 7v8"
            stroke="currentColor"
            stroke-width="1.6"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </a>

      <template v-if="isLoggedIn">
        <div class="more-menu__divider"></div>
        <button type="button" class="more-menu__item" role="menuitem" @click="openChangePassword">
          修改密码
        </button>
        <button type="button" class="more-menu__item more-menu__item--danger" role="menuitem" @click="handleLogout">
          退出登录
        </button>
      </template>
    </div>
  </Transition>

  <div v-if="visible" class="more-menu__scrim" @click="onClose"></div>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { logout } from '@/api/auth'
import { message } from '@/utils/message'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['update:visible', 'about', 'change-password'])

const router = useRouter()
const userStore = useUserStore()

const isLoggedIn = computed(() => !!userStore.token)

const onClose = () => {
  emit('update:visible', false)
}

const openAbout = () => {
  onClose()
  emit('about')
}

const openChangePassword = () => {
  onClose()
  emit('change-password')
}

const handleLogout = () => {
  logout().then((res) => {
    if (res.success) {
      userStore.logout()
      message.show('已退出登录')
      onClose()
      router.push('/discover')
    } else {
      message.show(res.message || '退出登录失败')
    }
  })
}
</script>

<style scoped>
.more-menu {
  position: absolute;
  left: 0;
  bottom: calc(100% + 10px);
  width: 100%;
  padding: 8px;
  border: 1px solid var(--color-line);
  border-radius: var(--radius-panel);
  background: var(--color-paper);
  box-shadow: var(--shadow-panel);
  z-index: 100;
}

.more-menu__item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
  padding: 9px 10px;
  border: none;
  border-radius: 8px;
  background: transparent;
  font-size: 14px;
  color: var(--color-ink-soft);
  text-align: left;
  cursor: pointer;
  transition:
    background-color var(--motion-fast) var(--ease-standard),
    color var(--motion-fast) var(--ease-standard);
}

.more-menu__item:hover {
  background: var(--color-canvas-sunken);
  color: var(--color-ink);
}

.more-menu__item--danger:hover {
  color: var(--color-ink);
}

.more-menu__item svg {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.more-menu__divider {
  height: 1px;
  margin: 4px 0;
  background: var(--color-line);
}

.more-menu__scrim {
  position: fixed;
  inset: 0;
  z-index: 99;
}

.dropdown-enter-active {
  transition:
    opacity 180ms var(--ease-standard),
    transform 180ms var(--ease-standard);
}

.dropdown-leave-active {
  transition:
    opacity 120ms var(--ease-standard),
    transform 120ms var(--ease-standard);
}

.dropdown-enter-from,
.dropdown-leave-to {
  opacity: 0;
  transform: translateY(4px) scale(0.97);
}
</style>

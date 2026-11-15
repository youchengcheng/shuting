import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  // 主页用户信息
  const profile = ref({})

  // 登录会话版本，用于识别账号切换后的旧请求
  const generation = ref(0)

  // 保存当前登录会话
  const captureSession = () => ({
    token: token.value,
    generation: generation.value
  })

  // 判断请求是否仍属于当前账号
  const isCurrentSession = (session) => !!session && session.token === token.value && session.generation === generation.value

  // 更新当前账号的用户资料
  const setProfile = (newProfile, session = captureSession()) => {
    if (!token.value || !isCurrentSession(session)) {
      return false
    }
    profile.value = newProfile || {}
    return true
  }

  // 保存 Token 并清空旧账号资料
  const setToken = (newToken) => {
    token.value = newToken || ''
    profile.value = {}
    generation.value += 1
  }

  // 退出登录
  const logout = (session = captureSession()) => {
    if (!isCurrentSession(session)) {
      return false
    }
    token.value = ''
    profile.value = {}
    generation.value += 1
    return true
  }

  // 同步其他标签页的登录状态
  const syncStorage = (event) => {
    if (event.key !== 'user' && event.key !== null) {
      return
    }
    try {
      const saved = event.newValue ? JSON.parse(event.newValue) : {}
      const nextToken = saved.token || ''
      const changed = nextToken !== token.value
      const nextProfile = nextToken ? (saved.profile || {}) : {}
      if (!changed && JSON.stringify(nextProfile) === JSON.stringify(profile.value)) {
        return
      }
      token.value = nextToken
      profile.value = nextProfile
      if (changed) {
        generation.value += 1
      }
    } catch {
      // 忽略格式错误的本地存储事件
    }
  }


  return {
    token,
    profile,
    generation,
    captureSession,
    isCurrentSession,
    syncStorage,
    setProfile,
    setToken,
    logout,
  }
}, 
{
  // 开启持久化
  persist: { pick: ['token', 'profile'] },
})

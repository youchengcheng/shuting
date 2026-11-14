import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useUserStore = defineStore('user', () => {
  const token = ref('')
  // 主页用户信息
  const profile = ref({})

  const generation = ref(0)
  const captureSession = () => ({ token: token.value, generation: generation.value })
  const isCurrentSession = (session) => !!session && session.token === token.value && session.generation === generation.value
  const setProfile = (newProfile, session = captureSession()) => {
    if (!token.value || !isCurrentSession(session)) return false
    profile.value = newProfile || {}
    return true
  }
  const setToken = (newToken) => {
    token.value = newToken || ''
    profile.value = {}
    generation.value += 1
  }
  const logout = (session = captureSession()) => {
    if (!isCurrentSession(session)) return false
    token.value = ''
    profile.value = {}
    generation.value += 1
    return true
  }
  const syncStorage = (event) => {
    if (event.key !== 'user' && event.key !== null) return
    try {
      const saved = event.newValue ? JSON.parse(event.newValue) : {}
      const nextToken = saved.token || ''
      const changed = nextToken !== token.value
      const nextProfile = nextToken ? (saved.profile || {}) : {}
      if (!changed && JSON.stringify(nextProfile) === JSON.stringify(profile.value)) return
      token.value = nextToken
      profile.value = nextProfile
      if (changed) generation.value += 1
    } catch { /* Ignore a malformed storage event. */ }
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

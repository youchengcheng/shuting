// 用户主页路由
export const PROFILE_ROUTE_PATH = '/user/profile/:userId?'

// 统一路由中的用户 ID
export const profileUserId = (value) => {
  if (value === undefined || value === null || value === '' || value === 'undefined') {
    return undefined
  }
  return String(value)
}

// 获取当前用户的主页地址
export const ownProfilePath = (profile) => {
  const userId = profileUserId(profile?.userId)
  return userId ? `/user/profile/${userId}` : '/user/profile'
}

// 初始化个人资料状态
export const emptyProfileState = () => ({
  profile: {},
  loading: false,
  error: ''
})

// 登录状态有效但用户资料可能未缓存，路由未指定用户 ID 时查询当前用户
export function createProfileLoader(state, fetchProfile, user) {
  let sequence = 0
  let controller = null

  // 取消旧的资料请求
  const invalidate = () => {
    sequence += 1
    controller?.abort()
    controller = null
  }

  // 加载用户资料，仅处理当前会话的请求结果
  const load = async (requestedId) => {
    invalidate()
    const userId = profileUserId(requestedId)
    const ownPage = userId === undefined
    const session = user.captureSession()
    const requestSequence = sequence
    const current = () => requestSequence === sequence && user.isCurrentSession(session)
    state.profile = {}
    state.error = ''
    state.loading = false
    if (ownPage && !user.token) {
      return null
    }
    const ownController = new AbortController()
    controller = ownController
    state.loading = true
    try {
      const response = await fetchProfile(userId, {
        session,
        signal: ownController.signal,
        silent: true
      })
      if (!current()) {
        return null
      }
      if (!response?.success || !response.data?.userId) {
        throw new Error(response?.message || '个人资料暂时无法加载，请重试')
      }
      state.profile = response.data
      if (ownPage || String(user.profile?.userId) === String(response.data.userId)) {
        user.setProfile(response.data, session)
      }
      return response.data
    } catch (error) {
      if (current()) {
        if (error.response?.status === 404) {
          state.error = '个人资料暂时无法加载，请稍后重试'
        } else if (error.code === 'ECONNABORTED') {
          state.error = '个人资料请求超时，请重试'
        } else {
          state.error = error.response?.data?.message || (error.response || error.message === 'Network Error' ? '个人资料暂时无法加载，请重试' : error.message) || '个人资料暂时无法加载，请重试'
        }
      }
      return null
    } finally {
      if (current()) {
        state.loading = false
        controller = null
      }
    }
  }
  return { load, invalidate }
}

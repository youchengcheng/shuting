export const PROFILE_ROUTE_PATH = '/user/profile/:userId?'
export const profileUserId = value => value === undefined || value === null || value === '' || value === 'undefined' ? undefined : String(value)
export const ownProfilePath = profile => profileUserId(profile?.userId) ? `/user/profile/${profileUserId(profile.userId)}` : '/user/profile'
export const emptyProfileState = () => ({ profile: {}, loading: false, error: '' })

// A valid login can outlive its cached profile. Fetch the current user when the route has no ID.
export function createProfileLoader(state, fetchProfile, user) {
  let sequence = 0
  let controller = null
  const invalidate = () => { sequence += 1; controller?.abort(); controller = null }
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
    if (ownPage && !user.token) return null
    const ownController = new AbortController()
    controller = ownController
    state.loading = true
    try {
      const response = await fetchProfile(userId, { session, signal: ownController.signal, silent: true })
      if (!current()) return null
      if (!response?.success || !response.data?.userId) throw new Error(response?.message || '个人资料暂时无法加载，请重试')
      state.profile = response.data
      if (ownPage || String(user.profile?.userId) === String(response.data.userId)) user.setProfile(response.data, session)
      return response.data
    } catch (error) {
      if (current()) {
        if (error.response?.status === 404) state.error = '个人资料暂时无法加载，请稍后重试'
        else if (error.code === 'ECONNABORTED') state.error = '个人资料请求超时，请重试'
        else state.error = error.response?.data?.message || (error.response || error.message === 'Network Error' ? '个人资料暂时无法加载，请重试' : error.message) || '个人资料暂时无法加载，请重试'
      }
      return null
    } finally {
      if (current()) { state.loading = false; controller = null }
    }
  }
  return { load, invalidate }
}

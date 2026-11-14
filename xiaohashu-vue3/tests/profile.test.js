import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createMemoryHistory } from 'vue-router'
import { useUserStore } from '../src/stores/user.js'
import { PROFILE_ROUTE_PATH, ownProfilePath, createProfileLoader, emptyProfileState } from '../src/utils/profileSession.js'
import { createNotificationController, emptyNotificationState } from '../src/utils/notificationState.js'

const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const user = () => { setActivePinia(createPinia()); const store = useUserStore(); store.setToken('account-a'); return store }
const ok = profile => ({ success: true, data: profile })

test('a restored login without a cached profile has a reachable own profile route and detail route', async () => {
  const store = user()
  const router = createRouter({ history: createMemoryHistory(), routes: [{ path: PROFILE_ROUTE_PATH, name: 'Profile', component: {}, children: [{ path: 'note/:noteId', name: 'ProfileNoteDetail', component: {} }] }] })
  await router.push(ownProfilePath(store.profile))
  assert.equal(router.currentRoute.value.name, 'Profile')
  assert.equal(router.currentRoute.value.params.userId, '')
  const ownDetail = router.resolve({ name: 'ProfileNoteDetail', params: { noteId: '9007199254740993001' } }).href
  assert.equal(ownDetail, '/user/profile/note/9007199254740993001')
  await router.push(ownDetail)
  assert.deepEqual(router.currentRoute.value.matched.map(route => route.name), ['Profile', 'ProfileNoteDetail'])
  assert.equal(router.currentRoute.value.params.userId, '')
  store.setProfile({ userId: '9007199254740993001' })
  assert.equal(ownProfilePath(store.profile), '/user/profile/9007199254740993001')
  await router.push('/user/profile/9007199254740993001/note/51')
  assert.equal(router.currentRoute.value.name, 'ProfileNoteDetail')
  assert.equal(router.currentRoute.value.params.userId, '9007199254740993001')
})

test('entering own profile without cached user ID fetches the current account and repairs cached profile', async () => {
  const store = user()
  const state = emptyProfileState()
  const loader = createProfileLoader(state, async (id, options) => {
    assert.equal(id, undefined)
    assert.equal(options.session.token, 'account-a')
    return ok({ userId: '42', nickname: 'Current account' })
  }, store)
  await loader.load()
  assert.equal(state.profile.userId, '42')
  assert.equal(store.profile.userId, '42')
  assert.equal(state.loading, false)
})

test('a failed profile request exposes a retry and a successful retry recovers the same logged in account', async () => {
  const store = user()
  const state = emptyProfileState()
  let calls = 0
  const loader = createProfileLoader(state, async () => {
    if (!calls++) throw { response: { status: 404 }, message: 'Request failed with status code 404' }
    return ok({ userId: '42' })
  }, store)
  await loader.load()
  assert.equal(store.token, 'account-a')
  assert.equal(state.error, '个人资料暂时无法加载，请稍后重试')
  assert.equal(state.loading, false)
  assert.equal(ownProfilePath(store.profile), '/user/profile')
  await loader.load()
  assert.equal(state.error, '')
  assert.equal(state.profile.userId, '42')
})

test('string and numeric IDs still identify the same cached owner when profile data is refreshed', async () => {
  const store = user()
  store.setProfile({ userId: '42', nickname: 'Old profile' })
  const state = emptyProfileState()
  const loader = createProfileLoader(state, async () => ok({ userId: 42, nickname: 'Updated profile' }), store)
  await loader.load('42')
  assert.equal(store.profile.nickname, 'Updated profile')
  assert.equal(String(store.profile.userId), String(state.profile.userId))
})

test('account switching aborts the previous profile request and late responses cannot restore the old identity', async () => {
  const store = user()
  const state = emptyProfileState()
  const pending = deferred()
  let oldSignal
  const loader = createProfileLoader(state, (_id, options) => {
    if (options.session.token === 'account-a') { oldSignal = options.signal; return pending.promise }
    return Promise.resolve(ok({ userId: '84' }))
  }, store)
  const oldRequest = loader.load()
  store.setToken('account-b')
  await loader.load()
  assert.equal(oldSignal.aborted, true)
  pending.resolve(ok({ userId: '42' }))
  await oldRequest
  assert.equal(store.profile.userId, '84')
  assert.equal(state.profile.userId, '84')
})

test('viewing another user does not replace the logged in identity, and logged out own profile makes no request', async () => {
  const store = user()
  store.setProfile({ userId: '42' })
  const state = emptyProfileState()
  let calls = 0
  const loader = createProfileLoader(state, async id => { calls++; return ok({ userId: id }) }, store)
  await loader.load('84')
  assert.equal(store.profile.userId, '42')
  store.logout()
  await loader.load()
  assert.equal(calls, 1)
  assert.deepEqual(state.profile, {})
})

test('unregistered notification endpoints display a recoverable localized error instead of raw Axios 404', async () => {
  const store = user()
  const state = emptyNotificationState()
  const unavailable = async () => { throw { response: { status: 404 }, message: 'Request failed with status code 404' } }
  const controller = createNotificationController(state, { listNotifications: unavailable, getNotificationSummary: unavailable }, store)
  await controller.loadPage('LIKE_COLLECTION', true)
  await controller.refreshSummary()
  assert.equal(state.pages.LIKE_COLLECTION.error, '通知服务暂时不可用，请稍后重试')
  assert.equal(state.summaryError, '通知服务暂时不可用，请稍后重试')
  assert.equal(store.token, 'account-a')
})

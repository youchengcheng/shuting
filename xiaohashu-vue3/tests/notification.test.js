import test from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
import { useUserStore } from '../src/stores/user.js'
import { compareSeq, createNotificationController, emptyNotificationState, shouldRefreshNotificationSignal } from '../src/utils/notificationState.js'
import { createSseParser } from '../src/utils/sse.js'
import { mapRelatedComments, captureCommentSubmission } from '../src/utils/commentContext.js'

const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const ok = data => ({ success: true, data })
const summary = (revision = '1', unread = 1, lastSeq = '20') => ({ revision, lastSeq, totalUnread: unread, unreadCounts: { LIKE_COLLECTION: unread, COMMENT_REPLY: 0, FOLLOW: 0 }, readAllSeq: {} })
const row = (id, seq = id) => ({ notificationId: String(id), seq: String(seq), category: 'LIKE_COLLECTION', read: false })
const setup = (overrides = {}) => {
  setActivePinia(createPinia())
  const user = useUserStore()
  user.setToken('account-a')
  const state = emptyNotificationState()
  const api = {
    getNotificationSummary: async () => ok(summary()),
    getNotificationReadState: async ids => ok({ items: ids.map(notificationId => ({ notificationId, read: false })), revision: '1', readAllSeq: {} }),
    listNotifications: async () => ok({ items: [], snapshotSeq: '20', revision: '1', nextCursor: null, hasMore: false }),
    readNotification: async () => ok({ revision: '2', summary: summary('2', 0), read: true }),
    readAllNotifications: async () => ok({ revision: '2', summary: summary('2', 0) }),
    ...overrides
  }
  return { user, state, api, controller: createNotificationController(state, api, user) }
}

test('decimal sequence comparison preserves values above Number.MAX_SAFE_INTEGER', () => {
  assert.equal(compareSeq('9007199254740993001', '9007199254740993000'), 1)
  assert.equal(compareSeq('000002', '2'), 0)
  assert.equal(compareSeq('999', '1000'), -1)
})

test('session changes reject old profile writes and old 401 logout, including A → B → A', () => {
  const { user } = setup()
  const old = user.captureSession()
  user.setToken('account-b')
  assert.equal(user.logout(old), false)
  assert.equal(user.setProfile({ userId: 'a' }, old), false)
  user.setToken('account-a')
  assert.equal(user.isCurrentSession(old), false)
  assert.equal(user.token, 'account-a')
})

test('storage synchronization invalidates the old session and avoids persistence ping-pong', () => {
  const { user } = setup()
  const old = user.captureSession()
  const event = { key: 'user', newValue: JSON.stringify({ token: 'account-b', profile: { userId: 'b' } }) }
  user.syncStorage(event)
  assert.equal(user.isCurrentSession(old), false)
  const profile = user.profile
  const generation = user.generation
  user.syncStorage(event)
  assert.equal(user.profile, profile)
  assert.equal(user.generation, generation)
  user.syncStorage({ key: 'user', newValue: null })
  assert.equal(user.token, '')
  assert.deepEqual(user.profile, {})
})

test('logout aborts pending page and a late response cannot populate the next account', async () => {
  const pending = deferred()
  let signal
  const { user, state, controller } = setup({ listNotifications: (_data, options) => { signal = options.signal; return pending.promise } })
  const request = controller.loadPage('LIKE_COLLECTION', true)
  user.setToken('account-b')
  controller.reset()
  assert.equal(signal.aborted, true)
  pending.resolve(ok({ items: [row('1')], snapshotSeq: '20', revision: '1', hasMore: false }))
  await request
  assert.deepEqual(state.pages.LIKE_COLLECTION.items, [])
  assert.equal(state.pages.LIKE_COLLECTION.loading, false)
})

test('a stale page arriving after SSE still joins and newly introduced IDs are calibrated', async () => {
  const pending = deferred()
  const requests = []
  const { state, controller } = setup({
    listNotifications: () => pending.promise,
    getNotificationReadState: async ids => { requests.push(ids); return ok({ items: ids.map(notificationId => ({ notificationId, read: notificationId === '19' })), revision: '2', readAllSeq: {} }) },
    getNotificationSummary: async () => ok(summary('2', 1))
  })
  state.pages.LIKE_COLLECTION.items = [row('20')]
  state.pages.LIKE_COLLECTION.snapshotSeq = '20'
  state.pages.LIKE_COLLECTION.hasMore = true
  const request = controller.loadPage('LIKE_COLLECTION')
  controller.changed('2')
  await controller.refreshReadState()
  pending.resolve(ok({ items: [row('19')], snapshotSeq: '20', revision: '1', hasMore: false }))
  await request
  await controller.refreshReadState(['19'])
  assert.deepEqual(state.pages.LIKE_COLLECTION.items.map(item => item.notificationId), ['20', '19'])
  assert.equal(state.pages.LIKE_COLLECTION.items[1].read, true)
  assert.ok(requests.some(ids => ids.includes('19')))
})

test('read-state synchronizes every loaded row in batches of at most 100 and true never regresses', async () => {
  const batches = []
  let read = true
  const { state, controller } = setup({ getNotificationReadState: async ids => { batches.push(ids); return ok({ items: ids.map(notificationId => ({ notificationId, read })), revision: '2', readAllSeq: {} }) } })
  state.pages.LIKE_COLLECTION.items = Array.from({ length: 251 }, (_, index) => row(index + 1))
  await controller.refreshReadState()
  assert.deepEqual(batches.map(batch => batch.length), [100, 100, 51])
  assert.equal(new Set(batches.flat()).size, 251)
  read = false
  await controller.refreshReadState()
  assert.ok(state.pages.LIKE_COLLECTION.items.every(item => item.read))
})

test('all-read uses the captured snapshot and leaves concurrently inserted notifications unread', async () => {
  const pending = deferred()
  let boundary
  const { state, controller } = setup({ readAllNotifications: (_category, snapshotSeq) => { boundary = snapshotSeq; return pending.promise } })
  const page = state.pages.LIKE_COLLECTION
  page.snapshotSeq = '20'
  page.items = [row('20'), row('19')]
  const request = controller.markAllRead('LIKE_COLLECTION')
  page.items.unshift(row('21'))
  page.snapshotSeq = '21'
  pending.resolve(ok({ revision: '2', summary: summary('2', 1, '21') }))
  await request
  assert.equal(boundary, '20')
  assert.deepEqual(page.items.map(item => item.read), [false, true, true])
})

test('one failed read-state batch keeps retry feedback even when later batches succeed', async () => {
  let calls = 0
  const { state, controller } = setup({ getNotificationReadState: async ids => {
    calls += 1
    if (calls === 1) throw new Error('temporarily unavailable')
    return ok({ items: ids.map(notificationId => ({ notificationId, read: true })), revision: '2', readAllSeq: {} })
  } })
  state.pages.LIKE_COLLECTION.items = Array.from({ length: 101 }, (_, index) => row(index + 1))
  await controller.refreshReadState()
  assert.ok(state.readStateError)
  assert.equal(state.pages.LIKE_COLLECTION.items[0].read, false)
  assert.equal(state.pages.LIKE_COLLECTION.items[100].read, true)
  await controller.refreshReadState()
  assert.equal(state.readStateError, '')
  assert.ok(state.pages.LIKE_COLLECTION.items.every(item => item.read))
})

test('read failures preserve unread counts; an older summary cannot undo successful mutation counts', async () => {
  const oldSummary = deferred()
  const { state, api, controller } = setup({ getNotificationSummary: () => oldSummary.promise, readNotification: async () => ({ success: false, message: 'unavailable' }) })
  const item = row('20')
  state.pages.LIKE_COLLECTION.items = [item]
  state.summary = summary('1', 1)
  await controller.markRead(item)
  assert.equal(item.read, false)
  assert.equal(state.summary.totalUnread, 1)
  const request = controller.refreshSummary()
  api.readNotification = async () => ok({ revision: '2', summary: summary('2', 0) })
  await controller.markRead(item)
  oldSummary.resolve(ok(summary('1', 1)))
  await request
  assert.equal(item.read, true)
  assert.equal(state.summary.totalUnread, 0)
})

test('summary single-flight repeats once when changed while a request is in flight', async () => {
  const pending = deferred()
  let calls = 0
  const { state, controller } = setup({ getNotificationSummary: async () => { calls += 1; return calls === 1 ? pending.promise : ok(summary('2', 2)) } })
  const request = controller.refreshSummary()
  controller.refreshSummary()
  controller.refreshSummary()
  pending.resolve(ok(summary('1', 1)))
  await request
  assert.equal(calls, 2)
  assert.equal(state.summary.totalUnread, 2)
})

test('entering and switching back to a cached category always requests its latest snapshot', async () => {
  const requests = []
  const { state, controller } = setup({
    getNotificationSummary: async () => ok(summary('1', 0, '0')),
    listNotifications: async data => {
      requests.push(data)
      const latest = String(20 + requests.length)
      return ok({ items: [row(latest)], snapshotSeq: latest, revision: '1', nextCursor: `cursor-${latest}`, hasMore: true })
    }
  })
  state.pages.LIKE_COLLECTION.items = [row('19')]
  state.pages.LIKE_COLLECTION.snapshotSeq = '19'
  state.pages.LIKE_COLLECTION.cursor = 'old-like-cursor'
  state.pages.COMMENT_REPLY.snapshotSeq = '18'
  state.pages.COMMENT_REPLY.cursor = 'old-comment-cursor'
  await controller.activateCategory('LIKE_COLLECTION')
  await controller.activateCategory('COMMENT_REPLY')
  await controller.activateCategory('LIKE_COLLECTION')
  assert.deepEqual(requests.map(({ category }) => category), ['LIKE_COLLECTION', 'COMMENT_REPLY', 'LIKE_COLLECTION'])
  assert.ok(requests.every(data => !('cursor' in data) && !('snapshotSeq' in data)))
  assert.deepEqual(state.pages.LIKE_COLLECTION.items.map(item => item.notificationId), ['23'])
  assert.equal(state.pages.LIKE_COLLECTION.cursor, 'cursor-23')
})

test('SSE bursts during a fresh request merge into one follow-up and keep successful read state', async () => {
  const pending = deferred()
  let calls = 0
  const { state, controller } = setup({
    getNotificationSummary: async () => ok(summary('3', 1, '21')),
    listNotifications: () => {
      calls += 1
      return calls === 1 ? pending.promise : Promise.resolve(ok({ items: [row('21'), row('20')], snapshotSeq: '21', revision: '3', hasMore: false }))
    }
  })
  state.pages.LIKE_COLLECTION.items = [{ ...row('20'), read: true }]
  state.pages.LIKE_COLLECTION.snapshotSeq = '20'
  const request = controller.activateCategory('LIKE_COLLECTION')
  const update = controller.changed('2')
  controller.changed('3')
  controller.changed('3')
  pending.resolve(ok({ items: [row('20')], snapshotSeq: '20', revision: '1', hasMore: false }))
  await request
  await update
  assert.equal(calls, 2)
  assert.deepEqual(state.pages.LIKE_COLLECTION.items.map(item => [item.notificationId, item.read]), [['21', false], ['20', true]])
  assert.equal(state.pages.LIKE_COLLECTION.loading, false)
})

test('a new notification cancels old pagination and the next page uses the refreshed cursor', async () => {
  const oldPage = deferred()
  const requests = []
  let oldSignal
  const { state, controller } = setup({
    getNotificationSummary: async () => ok(summary('2', 1, '20')),
    listNotifications: (data, options) => {
      requests.push(data)
      if (requests.length === 1) return Promise.resolve(ok({ items: [row('20'), row('19')], snapshotSeq: '20', revision: '2', nextCursor: 'cursor-20', hasMore: true }))
      if (requests.length === 2) { oldSignal = options.signal; return oldPage.promise }
      if (requests.length === 3) return Promise.resolve(ok({ items: [row('21'), row('20')], snapshotSeq: '21', revision: '3', nextCursor: 'cursor-21', hasMore: true }))
      return Promise.resolve(ok({ items: [row('20'), row('19')], snapshotSeq: '21', revision: '3', nextCursor: null, hasMore: false }))
    }
  })
  await controller.activateCategory('LIKE_COLLECTION')
  const pagination = controller.loadPage('LIKE_COLLECTION')
  await controller.changed('3')
  assert.equal(oldSignal.aborted, true)
  oldPage.resolve(ok({ items: [row('18')], snapshotSeq: '20', revision: '2', nextCursor: 'old-tail', hasMore: false }))
  await pagination
  assert.deepEqual(state.pages.LIKE_COLLECTION.items.map(item => item.notificationId), ['21', '20'])
  assert.equal(state.pages.LIKE_COLLECTION.cursor, 'cursor-21')
  await controller.loadPage('LIKE_COLLECTION')
  assert.equal(requests[3].cursor, 'cursor-21')
  assert.equal(requests[3].snapshotSeq, '21')
  assert.deepEqual(state.pages.LIKE_COLLECTION.items.map(item => item.notificationId), ['21', '20', '19'])
})

test('a change between fresh response completion and flight cleanup is not dropped', async () => {
  let controller
  let calls = 0
  const context = setup({
    getNotificationSummary: async () => ok(summary('1', 0, '20')),
    listNotifications: async () => {
      calls += 1
      if (calls === 1) Promise.resolve().then(() => queueMicrotask(() => { controller.changed('2') }))
      const latest = calls === 1 ? '20' : '21'
      return ok({ items: [row(latest)], snapshotSeq: latest, revision: calls === 1 ? '1' : '2', hasMore: false })
    }
  })
  controller = context.controller
  await controller.activateCategory('LIKE_COLLECTION')
  assert.equal(calls, 2)
  assert.deepEqual(context.state.pages.LIKE_COLLECTION.items.map(item => item.notificationId), ['21'])
  assert.equal(context.state.pages.LIKE_COLLECTION.loading, false)
})

test('a newer summary received during the initial page automatically reconciles its boundary', async () => {
  const pending = deferred()
  let calls = 0
  const { state, controller } = setup({
    getNotificationSummary: async () => ok(summary('2', 1, '21')),
    listNotifications: () => {
      calls += 1
      return calls === 1 ? pending.promise : Promise.resolve(ok({ items: [row('21')], snapshotSeq: '21', revision: '2', hasMore: false }))
    }
  })
  const request = controller.activateCategory('LIKE_COLLECTION')
  await controller.refreshSummary()
  pending.resolve(ok({ items: [row('20')], snapshotSeq: '20', revision: '1', hasMore: false }))
  await request
  assert.equal(calls, 2)
  assert.deepEqual(state.pages.LIKE_COLLECTION.items.map(item => item.notificationId), ['21'])
})

test('reset cancels a queued fresh follow-up and an old account cannot overwrite the new page', async () => {
  const pending = deferred()
  let calls = 0
  let oldSignal
  const { user, state, controller } = setup({
    getNotificationSummary: async () => ok(summary('1', 0, '0')),
    listNotifications: (_data, options) => {
      calls += 1
      if (calls === 1) { oldSignal = options.signal; return pending.promise }
      return Promise.resolve(ok({ items: [row('50')], snapshotSeq: '50', revision: '1', hasMore: false }))
    }
  })
  const oldRequest = controller.activateCategory('LIKE_COLLECTION')
  controller.changed('2')
  user.setToken('account-b')
  controller.reset()
  await controller.activateCategory('LIKE_COLLECTION')
  pending.resolve(ok({ items: [row('20')], snapshotSeq: '20', revision: '1', hasMore: false }))
  await oldRequest
  assert.equal(oldSignal.aborted, true)
  assert.equal(calls, 2)
  assert.deepEqual(state.pages.LIKE_COLLECTION.items.map(item => item.notificationId), ['50'])
  assert.equal(state.pages.LIKE_COLLECTION.loading, false)
})

test('automatic updates preserve the all-read boundary and leave the new notification unread', async () => {
  const pending = deferred()
  let calls = 0
  let boundary
  const { state, controller } = setup({
    getNotificationSummary: async () => ok(summary('2', 1, '20')),
    listNotifications: async () => {
      calls += 1
      return ok({ items: calls === 1 ? [row('20'), row('19')] : [row('21'), row('20'), row('19')], snapshotSeq: calls === 1 ? '20' : '21', revision: '2', hasMore: false })
    },
    readAllNotifications: (_category, snapshotSeq) => { boundary = snapshotSeq; return pending.promise }
  })
  await controller.activateCategory('LIKE_COLLECTION')
  const mutation = controller.markAllRead('LIKE_COLLECTION')
  await controller.changed('2')
  pending.resolve(ok({ revision: '3', summary: summary('3', 1, '21') }))
  await mutation
  assert.equal(boundary, '20')
  assert.deepEqual(state.pages.LIKE_COLLECTION.items.map(item => [item.notificationId, item.read]), [['21', false], ['20', true], ['19', true]])
})

test('changed events refresh even after HTTP advanced revision while unchanged heartbeats stay quiet', () => {
  const state = emptyNotificationState()
  state.revision = '9'
  assert.equal(shouldRefreshNotificationSignal('changed', '8', state), true)
  assert.equal(shouldRefreshNotificationSignal('ready', '8', state), true)
  assert.equal(shouldRefreshNotificationSignal('heartbeat', '8', state), false)
  assert.equal(shouldRefreshNotificationSignal('heartbeat', '10', state), true)
  state.summaryError = 'temporarily unavailable'
  assert.equal(shouldRefreshNotificationSignal('heartbeat', '9', state), true)
})

test('SSE handles UTF-8 byte splits, CRLF split boundaries, comments and multiline data', () => {
  const events = []
  const parse = createSseParser(event => events.push(event))
  const text = '\uFEFF: keepalive\r\nevent: changed\r\ndata: {"revision":"9007199254740993",\r\ndata: "name":"书亭"}\r\n\r\nevent: auth-expired\ndata: {}\n\n'
  const bytes = new TextEncoder().encode(text)
  const decoder = new TextDecoder()
  for (const byte of bytes) parse(decoder.decode(Uint8Array.of(byte), { stream: true }))
  assert.deepEqual(events.map(event => event.event), ['changed', 'auth-expired'])
  assert.deepEqual(JSON.parse(events[0].data), { revision: '9007199254740993', name: '书亭' })
})

test('related context deduplicates root and target and includes the replied-to comment', () => {
  const root = { commentId: '1', userId: 'owner', nickname: '作者' }
  const target = { commentId: '3', userId: 'actor', replyUserId: 'reply' }
  const repliedTo = { commentId: '2', userId: 'reply', nickname: '被回复用户' }
  assert.deepEqual(mapRelatedComments({ root, target: root })[0].childComments, [])
  const context = mapRelatedComments({ root, target, repliedTo })
  assert.deepEqual(context[0].childComments.map(item => item.commentId), ['2', '3'])
  assert.equal(context[0].childComments[1].replyUserName, '被回复用户')
  assert.equal(context[0].hasMoreChildComments, false)
  assert.deepEqual(mapRelatedComments({ unavailableReason: 'COMMENT_UNAVAILABLE' }), [])
})

test('comment submission snapshots note, reply and draft before an asynchronous token request', () => {
  const reply = { commentId: '3', nickname: 'original' }
  const profile = { userId: 'a', nickname: 'original-user' }
  const submission = captureCommentSubmission({ noteId: 'note-a', content: 'original-draft', imageUrl: 'original-image', reply, profile, session: { generation: 1 }, generation: 1, mode: 'RELATED' })
  reply.commentId = 'other-comment'
  profile.userId = 'b'
  assert.equal(submission.noteId, 'note-a')
  assert.equal(submission.reply.commentId, '3')
  assert.equal(submission.profile.userId, 'a')
  assert.equal(submission.content, 'original-draft')
})

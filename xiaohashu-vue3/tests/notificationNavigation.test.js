import test from 'node:test'
import assert from 'node:assert/strict'
import { createCommentPageState, normalizeComment, mergeCommentPage, flattenCommentPages, nextMissingCommentPage, commentPageSections, createCommentTargetNavigator, createCommentScrollAnchor } from '../src/utils/commentContext.js'

const deferred = () => { let resolve; const promise = new Promise(r => { resolve = r }); return { promise, resolve } }
const response = (pageNo, data, totalPage = pageNo) => ({ success: true, pageNo, data, totalPage, totalCount: totalPage * 10 })
const comment = id => ({ commentId: String(id), content: `normal comment ${id}`, liked: false })
const root = (id, childCount = 0) => ({ ...comment(id), childCommentTotal: childCount, childComments: [], childPaging: { ...createCommentPageState(), totalPages: Math.ceil(childCount / 6) } })

function navigationSetup({ context, rootPages = {}, childPages = {} } = {}) {
  let session = 1
  let roots = []
  const paging = createCommentPageState()
  const rootRequests = []
  const rootSignals = []
  const childRequests = []
  const statuses = []
  const found = []
  const getContext = typeof context === 'function' ? context : async () => ({ success: true, data: context })
  const loadRootPage = async (pageNo, request) => {
    rootRequests.push(pageNo)
    if (request?.options?.signal) rootSignals.push(request.options.signal)
    const result = await (rootPages[pageNo] || response(pageNo, [], paging.totalPages))
    if (request && !request.isCurrent()) return
    mergeCommentPage(paging, result, item => item)
    roots = flattenCommentPages(paging)
  }
  const loadChildPage = async (parent, pageNo, request) => {
    childRequests.push([parent.commentId, pageNo])
    const result = await (childPages[pageNo] || response(pageNo, [], parent.childPaging.totalPages))
    if (request && !request.isCurrent()) return
    mergeCommentPage(parent.childPaging, result)
    parent.childComments = flattenCommentPages(parent.childPaging)
  }
  const navigator = createCommentTargetNavigator({
    api: { getCommentContext: getContext },
    sessionOwner: { captureSession: () => session, isCurrentSession: captured => captured === session },
    getRoots: () => roots, getRootPaging: () => paging, loadRootPage, loadChildPage,
    onState: state => statuses.push(state), onFound: (id, current) => { if (current()) found.push(id) }
  })
  return { navigator, paging, loadRootPage, loadChildPage, rootRequests, rootSignals, childRequests, statuses, found,
    roots: () => roots, switchSession: () => { session += 1 },
    replaceRoots: items => { paging.pages = { 1: items }; roots = items }
  }
}

test('a non-first-page reply opens normal root and child pages while preserving gaps and first screen', async () => {
  const targetId = '9007199254740993002'
  const targetRoot = root('9007199254740993001', 24)
  targetRoot.childPaging.pages[0] = [comment('1')]
  targetRoot.childComments = flattenCommentPages(targetRoot.childPaging)
  const state = navigationSetup({
    context: { rootCommentId: targetRoot.commentId, rootPageNo: 3, childPageNo: 4,
      root: { ...targetRoot, content: 'context fragment must not replace normal list' }, target: { ...comment(targetId), imageUrl: 'context-image' } },
    rootPages: { 1: response(1, [root('10')], 3), 2: response(2, [root('20')], 3), 3: response(3, [targetRoot], 3) },
    childPages: { 4: response(4, [{ ...comment(targetId), content: '', imageUrl: 'normal-image' }], 4) }
  })
  await state.loadRootPage(1)
  await state.navigator.locate('note-a', targetId)
  assert.deepEqual(state.rootRequests, [1, 3])
  assert.deepEqual(state.childRequests, [[targetRoot.commentId, 4]])
  assert.deepEqual(state.roots().map(item => item.commentId), ['10', targetRoot.commentId])
  assert.equal(state.roots()[1].content, `normal comment ${targetRoot.commentId}`)
  assert.equal(state.roots()[1].childComments[1].imageUrl, 'normal-image')
  assert.deepEqual(state.found, [targetId])
  assert.equal(nextMissingCommentPage(state.paging), 2)
  assert.equal(commentPageSections(state.paging, state.roots())[1].firstMissingPage, 2)
  assert.equal(nextMissingCommentPage(targetRoot.childPaging), 1)
  assert.equal(commentPageSections(targetRoot.childPaging, targetRoot.childComments)[1].firstMissingPage, 1)
  await state.loadRootPage(nextMissingCommentPage(state.paging))
  assert.deepEqual(state.roots().map(item => item.commentId), ['10', '20', targetRoot.commentId])
  assert.equal(nextMissingCommentPage(state.paging), null)
})

test('a moved root page hint searches normal missing pages without skipping or injecting context rows', async () => {
  const state = navigationSetup({
    context: { rootCommentId: '50', rootPageNo: 3, childPageNo: null, root: comment('50'), target: comment('50') },
    rootPages: Object.fromEntries([1, 2, 3, 4, 5].map(pageNo => [pageNo, response(pageNo, [root(String(pageNo * 10))], 5)]))
  })
  await state.loadRootPage(1)
  await state.navigator.locate('note-a', '50')
  assert.deepEqual(state.rootRequests, [1, 3, 2, 4, 5])
  assert.deepEqual(state.roots().map(item => item.commentId), ['10', '20', '30', '40', '50'])
  assert.deepEqual(state.found, ['50'])
  assert.deepEqual(state.childRequests, [])
})

test('a deleted target preserves the ordinary note comment list and returns brief unavailable state', async () => {
  const state = navigationSetup({ context: { unavailableReason: 'COMMENT_UNAVAILABLE' }, rootPages: { 1: response(1, [root('10')], 2) } })
  await state.loadRootPage(1)
  await state.navigator.locate('note-a', 'deleted')
  assert.deepEqual(state.roots().map(item => item.commentId), ['10'])
  assert.deepEqual(state.rootRequests, [1])
  assert.deepEqual(state.found, [])
  assert.equal(state.statuses.at(-1).unavailableReason, 'COMMENT_UNAVAILABLE')
  assert.equal(state.statuses.at(-1).error, '')
})

test('a reply deleted after location is rechecked without clearing the ordinary root list', async () => {
  let contextCalls = 0
  const targetRoot = root('10', 0)
  const state = navigationSetup({
    context: async () => ({ success: true, data: ++contextCalls === 1
      ? { rootCommentId: '10', rootPageNo: 1, childPageNo: 2, root: comment('10'), target: comment('20') }
      : { unavailableReason: 'COMMENT_UNAVAILABLE' } }),
    rootPages: { 1: response(1, [targetRoot], 1) }, childPages: { 2: response(2, [], 0) }
  })
  await state.loadRootPage(1)
  await state.navigator.locate('note-a', '20')
  assert.deepEqual(state.childRequests, [['10', 2]], 'context child hint must load even when cached child count is zero')
  assert.equal(contextCalls, 2)
  assert.deepEqual(state.roots().map(item => item.commentId), ['10'])
  assert.equal(state.statuses.at(-1).unavailableReason, 'COMMENT_UNAVAILABLE')
})

test('a late normal page from a previous account cannot populate or scroll the new account', async () => {
  const pending = deferred()
  let contextCalls = 0
  const state = navigationSetup({
    context: async () => ({ success: true, data: ++contextCalls === 1
      ? { rootCommentId: '20', rootPageNo: 2, target: comment('20') }
      : { rootCommentId: '50', rootPageNo: 1, target: comment('50') } }),
    rootPages: { 1: response(1, [root('10')], 2), 2: pending.promise }
  })
  await state.loadRootPage(1)
  const old = state.navigator.locate('note-a', '20')
  await Promise.resolve()
  state.switchSession()
  state.replaceRoots([root('50')])
  await state.navigator.locate('note-b', '50')
  assert.equal(state.rootSignals[0].aborted, true)
  pending.resolve(response(2, [root('20')], 2))
  await old
  assert.deepEqual(state.roots().map(item => item.commentId), ['50'])
  assert.deepEqual(state.found, ['50'])
  assert.equal(state.statuses.at(-1).loading, false)
  assert.equal(state.statuses.at(-1).error, '')
})

test('reply pages deduplicate the first-reply preview and retain string IDs and deleted rows', () => {
  const paging = createCommentPageState()
  paging.pages[0] = [{ ...comment('9007199254740993001'), imageUrl: 'preview-image' }]
  mergeCommentPage(paging, response(3, [comment('9007199254740993003')], 3))
  mergeCommentPage(paging, response(1, [comment('9007199254740993001'), comment('9007199254740993002')], 3))
  assert.deepEqual(flattenCommentPages(paging).map(item => item.commentId), ['9007199254740993001', '9007199254740993002', '9007199254740993003'])
  assert.deepEqual(flattenCommentPages(paging, new Set(['9007199254740993002'])).map(item => item.commentId), ['9007199254740993001', '9007199254740993003'])
  assert.equal(nextMissingCommentPage(paging), 2)
  assert.equal(normalizeComment({ commentId: '9007199254740993001', parentId: '9007199254740993000' }).parentId, '9007199254740993000')
})

function scrollFixture() {
  let targetOffset = 400
  const listeners = new Map()
  const calls = []
  const timers = new Map()
  let timerId = 0
  let observerCallback
  let disconnected = false
  const image = { complete: true }
  const container = {
    scrollTop: 0, clientTop: 0, clientHeight: 500, scrollHeight: 2000, children: [{}],
    getBoundingClientRect: () => ({ top: 100 }),
    scrollTo: options => { calls.push(options); container.scrollTop = options.top },
    querySelectorAll: () => [image],
    addEventListener: (event, listener) => listeners.set(event, listener),
    removeEventListener: event => listeners.delete(event)
  }
  const target = {
    getBoundingClientRect: () => ({ top: 100 + targetOffset - container.scrollTop, height: 1000 }),
    querySelector: selector => selector === ':scope > .comment-item__body > .comment-item__actions'
      ? { getBoundingClientRect: () => ({ bottom: 100 + targetOffset - container.scrollTop + 100 }) } : null,
    focus: options => { target.focusOptions = options }
  }
  const anchor = createCommentScrollAnchor({
    ResizeObserverClass: class { constructor(callback) { observerCallback = callback } observe() {} disconnect() { disconnected = true } },
    setTimer: callback => { timers.set(++timerId, callback); return timerId }, clearTimer: id => timers.delete(id)
  })
  return { anchor, container, target, image, calls, timers, listeners,
    moveTarget: offset => { targetOffset = offset }, resize: () => observerCallback(), disconnected: () => disconnected }
}

test('comment anchoring centers the comment itself without its replies and stops when the user starts scrolling', () => {
  const state = scrollFixture()
  state.anchor.start(state.container, state.target)
  assert.equal(state.calls[0].top, 200)
  assert.equal(state.calls[0].behavior, 'smooth')
  assert.deepEqual(state.target.focusOptions, { preventScroll: true })
  state.moveTarget(450)
  state.resize()
  assert.equal(state.calls.at(-1).top, 250)
  state.listeners.get('wheel')()
  const count = state.calls.length
  state.moveTarget(500)
  state.resize()
  assert.equal(state.calls.length, count)
  assert.equal(state.disconnected(), true)
  assert.equal(state.timers.size, 0)
})

test('slow image loads keep a bounded anchor beyond the initial settling period', () => {
  const state = scrollFixture()
  state.image.complete = false
  state.anchor.start(state.container, state.target, { reducedMotion: true })
  assert.equal(state.calls[0].behavior, 'auto')
  const initialTimer = state.timers.get(1)
  state.timers.delete(1)
  initialTimer()
  assert.equal(state.disconnected(), false)
  state.moveTarget(480)
  state.image.complete = true
  state.listeners.get('load')()
  assert.equal(state.calls.at(-1).top, 280)
  state.timers.get(2)()
  assert.equal(state.disconnected(), true)
})

test('a view or account change stops resize anchoring before it can scroll a later note', () => {
  const state = scrollFixture()
  let current = true
  state.anchor.start(state.container, state.target, { isCurrent: () => current })
  current = false
  const count = state.calls.length
  state.resize()
  assert.equal(state.calls.length, count)
  assert.equal(state.disconnected(), true)
})

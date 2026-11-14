export const NOTIFICATION_CATEGORIES = ['LIKE_COLLECTION', 'COMMENT_REPLY', 'FOLLOW']
export const compareSeq = (a = '0', b = '0') => {
  a = String(a ?? '0').replace(/^0+(?=\d)/, '')
  b = String(b ?? '0').replace(/^0+(?=\d)/, '')
  return a.length === b.length ? (a === b ? 0 : a > b ? 1 : -1) : a.length > b.length ? 1 : -1
}
const maxSeq = (a, b) => compareSeq(a, b) >= 0 ? String(a || '0') : String(b || '0')
export const shouldRefreshNotificationSignal = (event, revision, state) => event === 'ready' || event === 'changed' || compareSeq(revision, state.revision) > 0 || !!state.summaryError || !!state.readStateError
export const notificationRequestError = (error, fallback) => {
  if (error.response?.status === 404 || error.response?.status === 503) return '通知服务暂时不可用，请稍后重试'
  if (error.code === 'ECONNABORTED') return '通知请求超时，请重试'
  return error.response?.data?.message || (error.response || error.message === 'Network Error' ? fallback : error.message) || fallback
}
const emptyPage = () => ({ items: [], cursor: null, snapshotSeq: null, hasMore: false, loading: false, error: '', errorMode: '', generation: 0 })
export const emptyNotificationState = () => ({
  pages: Object.fromEntries(NOTIFICATION_CATEGORIES.map(key => [key, emptyPage()])),
  summary: { unreadCounts: Object.fromEntries(NOTIFICATION_CATEGORIES.map(key => [key, 0])), totalUnread: 0, lastSeq: '0', revision: '0', readAllSeq: {} },
  revision: '0', summaryError: '', readStateError: '', connection: 'idle', mutationError: '', reading: {}, readingAll: {}
})

// Injected requests make the account and pagination races testable without a live service.
export function createNotificationController(state, api, sessionOwner) {
  let epoch = 0
  let summaryFlight = null
  let summaryDirty = false
  let stateFlight = null
  let stateDirty = false
  let activeCategory = null
  const pageFlights = new Map()
  const stateQueue = new Set()
  const controllers = new Set()
  const active = (session, version) => version === epoch && sessionOwner.isCurrentSession(session)
  const requestContext = () => {
    const controller = new AbortController()
    const session = sessionOwner.captureSession()
    controllers.add(controller)
    return { session, version: epoch, options: { signal: controller.signal, session }, controller }
  }
  const payload = (response) => {
    if (!response.success) throw new Error(response.message || '通知加载失败，请重试')
    return response.data
  }
  const allItems = () => NOTIFICATION_CATEGORIES.flatMap(category => state.pages[category].items)
  const mergeWatermarks = (watermarks = {}) => {
    for (const category of NOTIFICATION_CATEGORIES) state.summary.readAllSeq[category] = maxSeq(state.summary.readAllSeq[category], watermarks[category])
    for (const item of allItems()) if (compareSeq(item.seq, state.summary.readAllSeq[item.category]) <= 0) item.read = true
  }
  const mergeSummary = (summary) => {
    if (!summary) return
    state.revision = maxSeq(state.revision, summary.revision)
    if (compareSeq(summary.revision, state.summary.revision) >= 0) {
      const oldWatermarks = state.summary.readAllSeq
      const previousLastSeq = state.summary.lastSeq
      state.summary = { ...summary, readAllSeq: { ...oldWatermarks } }
      mergeWatermarks(summary.readAllSeq)
      const page = state.pages[activeCategory]
      if (page && page.snapshotSeq !== null && compareSeq(summary.lastSeq, previousLastSeq) > 0 && compareSeq(summary.lastSeq, page.snapshotSeq) > 0) void loadPage(activeCategory, true)
    }
  }
  const refreshSummary = () => {
    if (!sessionOwner.token) return Promise.resolve()
    if (summaryFlight) { summaryDirty = true; return summaryFlight }
    const ctx = requestContext()
    const flight = (async () => {
      do {
        summaryDirty = false
        try {
          const data = payload(await api.getNotificationSummary(ctx.options))
          if (!active(ctx.session, ctx.version)) return
          mergeSummary(data)
          state.summaryError = ''
        } catch (error) {
          if (!active(ctx.session, ctx.version)) return
          state.summaryError = notificationRequestError(error, '未读数量暂时无法更新')
        }
      } while (summaryDirty && active(ctx.session, ctx.version))
    })().finally(() => {
      controllers.delete(ctx.controller)
      if (summaryFlight === flight) summaryFlight = null
    })
    summaryFlight = flight
    return flight
  }
  const refreshReadState = (ids = allItems().map(item => item.notificationId)) => {
    ids.forEach(id => stateQueue.add(String(id)))
    if (!sessionOwner.token || !stateQueue.size) return Promise.resolve()
    if (stateFlight) { stateDirty = true; return stateFlight }
    const ctx = requestContext()
    const flight = (async () => {
      do {
        stateDirty = false
        const queued = [...stateQueue]
        stateQueue.clear()
        let failed = false
        for (let index = 0; index < queued.length; index += 100) {
          try {
            const data = payload(await api.getNotificationReadState(queued.slice(index, index + 100), ctx.options))
            if (!active(ctx.session, ctx.version)) return
            state.revision = maxSeq(state.revision, data.revision)
            mergeWatermarks(data.readAllSeq)
            const reads = new Set((data.items || []).filter(item => item.read).map(item => String(item.notificationId)))
            for (const item of allItems()) if (reads.has(String(item.notificationId))) item.read = true
          } catch {
            if (!active(ctx.session, ctx.version)) return
            failed = true
          }
        }
        state.readStateError = failed ? '部分通知的已读状态暂时无法更新' : ''
        if (stateDirty) allItems().forEach(item => stateQueue.add(String(item.notificationId)))
      } while ((stateDirty || stateQueue.size) && active(ctx.session, ctx.version))
    })().finally(() => {
      controllers.delete(ctx.controller)
      if (stateFlight === flight) stateFlight = null
    })
    stateFlight = flight
    return flight
  }
  const loadPage = (category, refresh = false) => {
    if (!sessionOwner.token || !NOTIFICATION_CATEGORIES.includes(category)) return Promise.resolve()
    const page = state.pages[category]
    const pending = pageFlights.get(category)
    if (pending) {
      if (!refresh) return pending.promise
      if (pending.refresh) { pending.dirty = true; return pending.promise }
      // A fresh snapshot supersedes an older pagination request, even if its abort arrives late.
      pending.ctx.controller.abort()
      pageFlights.delete(category)
    }
    if (!refresh && page.snapshotSeq !== null && !page.hasMore) return Promise.resolve()
    const ctx = requestContext()
    const pageGeneration = ++page.generation
    const flight = { ctx, refresh, dirty: false, checkedLatestSeq: '0', promise: null }
    page.loading = true
    page.error = ''
    page.errorMode = ''
    flight.promise = (async () => {
      do {
        flight.dirty = false
        try {
          const data = payload(await api.listNotifications({ category, pageSize: 20, ...(refresh ? {} : { cursor: page.cursor, snapshotSeq: page.snapshotSeq }) }, ctx.options))
          if (!active(ctx.session, ctx.version) || page.generation !== pageGeneration) return
          const previous = new Map(page.items.map(item => [String(item.notificationId), item]))
          const added = []
          const incoming = (data.items || []).map(item => {
            const normalized = { ...item, notificationId: String(item.notificationId), seq: String(item.seq), category, read: !!item.read || !!previous.get(String(item.notificationId))?.read }
            if (compareSeq(normalized.seq, state.summary.readAllSeq[category]) <= 0) normalized.read = true
            if (!previous.has(normalized.notificationId)) added.push(normalized.notificationId)
            return normalized
          })
          page.items = refresh ? incoming : [...page.items, ...incoming.filter(item => !previous.has(item.notificationId))]
          page.cursor = data.nextCursor
          page.snapshotSeq = String(data.snapshotSeq)
          page.hasMore = !!data.hasMore
          page.error = page.errorMode = ''
          const stale = compareSeq(data.revision, state.revision) < 0
          state.revision = maxSeq(state.revision, data.revision)
          if (stale && added.length) void refreshReadState(added)
          if (compareSeq(data.revision, state.summary.revision) > 0) void refreshSummary()
          // A summary may arrive while the first page is pending. Reconcile that newer boundary once.
          if (refresh && activeCategory === category && compareSeq(state.summary.lastSeq, page.snapshotSeq) > 0 && compareSeq(state.summary.lastSeq, flight.checkedLatestSeq) > 0) {
            flight.checkedLatestSeq = String(state.summary.lastSeq)
            flight.dirty = true
          }
        } catch (error) {
          if (!active(ctx.session, ctx.version) || page.generation !== pageGeneration) return
          page.error = notificationRequestError(error, '通知加载失败，请重试')
          page.errorMode = refresh ? 'refresh' : 'more'
        }
      } while (flight.dirty && active(ctx.session, ctx.version) && page.generation === pageGeneration)
    })().finally(() => {
      controllers.delete(ctx.controller)
      if (pageFlights.get(category) === flight) {
        pageFlights.delete(category)
        if (active(ctx.session, ctx.version) && page.generation === pageGeneration) {
          // A hint can land after the async loop settles but before this cleanup microtask.
          if (flight.dirty) return loadPage(category, true)
          page.loading = false
        }
      }
    })
    pageFlights.set(category, flight)
    return flight.promise
  }
  const activateCategory = (category) => {
    activeCategory = NOTIFICATION_CATEGORIES.includes(category) ? category : null
    if (!activeCategory || !sessionOwner.token) return Promise.resolve()
    const request = loadPage(activeCategory, true)
    void refreshSummary()
    void refreshReadState()
    return request
  }
  const markRead = async (item) => {
    if (item.read || state.reading[item.notificationId]) return
    const ctx = requestContext()
    state.reading[item.notificationId] = true
    try {
      const data = payload(await api.readNotification(item.notificationId, ctx.options))
      if (!active(ctx.session, ctx.version)) return
      for (const row of allItems()) if (row.notificationId === item.notificationId) row.read = true
      mergeSummary(data.summary)
      state.revision = maxSeq(state.revision, data.revision)
      state.mutationError = ''
    } catch {
      if (active(ctx.session, ctx.version)) state.mutationError = '标记已读失败，未读状态已保留，请重试'
    } finally {
      controllers.delete(ctx.controller)
      if (active(ctx.session, ctx.version)) delete state.reading[item.notificationId]
    }
  }
  const markAllRead = async (category) => {
    const snapshotSeq = state.pages[category].snapshotSeq
    if (snapshotSeq === null || state.readingAll[category]) return
    const ctx = requestContext()
    state.readingAll[category] = true
    try {
      const data = payload(await api.readAllNotifications(category, snapshotSeq, ctx.options))
      if (!active(ctx.session, ctx.version)) return
      mergeWatermarks({ [category]: snapshotSeq })
      mergeSummary(data.summary)
      state.revision = maxSeq(state.revision, data.revision)
      state.mutationError = ''
    } catch {
      if (active(ctx.session, ctx.version)) state.mutationError = '全部已读失败，未读状态已保留，请重试'
    } finally {
      controllers.delete(ctx.controller)
      if (active(ctx.session, ctx.version)) delete state.readingAll[category]
    }
  }
  const changed = (revision) => {
    if (compareSeq(revision, state.revision) > 0) state.revision = String(revision)
    void refreshSummary()
    void refreshReadState()
    return activeCategory ? loadPage(activeCategory, true) : Promise.resolve()
  }
  const reset = () => {
    epoch += 1
    controllers.forEach(controller => controller.abort())
    controllers.clear()
    summaryFlight = stateFlight = null
    summaryDirty = stateDirty = false
    stateQueue.clear()
    pageFlights.clear()
    activeCategory = null
    Object.assign(state, emptyNotificationState())
  }
  return { loadPage, activateCategory, refreshSummary, refreshReadState, markRead, markAllRead, changed, reset }
}

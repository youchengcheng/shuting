export function mapRelatedComments(context) {
  if (!context?.target) return []
  const root = context.root || context.target
  const normalize = item => ({ ...item, commentId: String(item.commentId), replyUserName: item.replyUserName || '', likeTotal: Number(item.likeTotal || 0), childCommentTotal: 0, hasMoreChildComments: false })
  const children = [context.repliedTo, context.target].filter(Boolean)
    .filter((item, index, items) => String(item.commentId) !== String(root.commentId) && items.findIndex(other => String(other.commentId) === String(item.commentId)) === index)
    .map(item => ({ ...normalize(item), replyUserName: item.replyUserId && String(item.replyUserId) === String(root.userId) ? root.nickname : item === context.target ? context.repliedTo?.nickname || '' : '' }))
  return [{ ...normalize(root), childComments: children, childCommentTotal: children.length }]
}

// Capture mutable input and its owner before the idempotency-token await.
export function captureCommentSubmission({ noteId, content, imageUrl, reply, profile, session, generation, mode }) {
  return { noteId: String(noteId), content, imageUrl, reply: reply ? { ...reply } : null, profile: { ...profile }, session, generation, mode }
}

export const createCommentPageState = () => ({ pages: {}, totalPages: 0, totalCount: 0 })

export function normalizeComment(comment) {
  const normalized = { ...comment, commentId: String(comment.commentId), liked: !!comment.liked }
  for (const key of ['noteId', 'userId', 'parentId', 'replyCommentId', 'replyUserId']) {
    if (comment[key] !== null && comment[key] !== undefined) normalized[key] = String(comment[key])
  }
  return normalized
}

export function mergeCommentPage(paging, response, mapItem = normalizeComment) {
  if (!response.success) throw new Error(response.message || '评论加载失败，请重试')
  const pageNo = Number(response.pageNo)
  if (!Number.isInteger(pageNo) || pageNo < 1) throw new Error('评论加载失败，请重试')
  const existing = new Map(Object.values(paging.pages).flat().map(item => [String(item.commentId), item]))
  paging.pages[pageNo] = (response.data || []).map(item => {
    const previous = existing.get(String(item.commentId))
    const next = mapItem(item, previous)
    return previous ? Object.assign(previous, next) : next
  })
  paging.totalPages = Math.max(0, Number(response.totalPage) || 0)
  paging.totalCount = Math.max(0, Number(response.totalCount) || 0)
}

export function flattenCommentPages(paging, deletedIds = new Set()) {
  const seen = new Set()
  return Object.keys(paging.pages).map(Number).sort((a, b) => a - b).flatMap(pageNo => paging.pages[pageNo]).filter(item => {
    const id = String(item.commentId)
    if (seen.has(id) || deletedIds.has(id)) return false
    seen.add(id)
    return true
  })
}

export function nextMissingCommentPage(paging) {
  for (let pageNo = 1; pageNo <= paging.totalPages; pageNo += 1) {
    if (!Object.hasOwn(paging.pages, pageNo)) return pageNo
  }
  return null
}

// A direct jump never marks intervening pages as loaded; visible gaps keep their own entry point.
export function commentPageSections(paging, items) {
  const visible = new Map(items.map(item => [String(item.commentId), item]))
  const seen = new Set()
  let previousPage = 0
  return Object.keys(paging?.pages || { 0: items }).map(Number).sort((a, b) => a - b).map(pageNo => {
    const firstMissingPage = pageNo > previousPage + 1 ? previousPage + 1 : null
    previousPage = Math.max(previousPage, pageNo)
    const comments = (paging?.pages[pageNo] || items).filter(item => {
      const id = String(item.commentId)
      if (!visible.has(id) || seen.has(id)) return false
      seen.add(id)
      return true
    }).map(item => visible.get(String(item.commentId)))
    return { pageNo, firstMissingPage, comments }
  })
}

// Location page numbers are hints: a concurrent deletion or ranking change can move a row.
export function createCommentTargetNavigator({ api, sessionOwner, getRoots, getRootPaging, loadRootPage, loadChildPage, onState, onFound }) {
  let generation = 0
  let controller = null
  const cancel = () => { generation += 1; controller?.abort(); controller = null }
  const locate = async (noteId, commentId, ownsView = () => true) => {
    cancel()
    if (!commentId) { onState({ loading: false, error: '', unavailableReason: '' }); return }
    const version = generation
    const session = sessionOwner.captureSession()
    const ownController = new AbortController()
    controller = ownController
    const current = () => version === generation && sessionOwner.isCurrentSession(session) && ownsView()
    const request = { options: { signal: ownController.signal, session }, isCurrent: current }
    const targetId = String(commentId)
    let contextKnown = false
    onState({ loading: true, error: '', unavailableReason: '' })
    try {
      const response = await api.getCommentContext(String(noteId), targetId, request.options)
      if (!current()) return
      if (!response.success) throw new Error()
      const context = response.data
      if (!context?.target || context.unavailableReason) {
        onState({ loading: false, error: '', unavailableReason: context?.unavailableReason || 'COMMENT_UNAVAILABLE' })
        return
      }
      contextKnown = true
      const rootId = String(context.rootCommentId ?? context.root?.commentId ?? context.target.commentId)
      const findRoot = () => getRoots().find(root => String(root.commentId) === rootId)
      const rootHint = Number(context.rootPageNo)
      if (!findRoot() && Number.isInteger(rootHint) && rootHint > 0) {
        await loadRootPage(rootHint, request)
        if (!current()) return
      }
      while (!findRoot()) {
        const missingPage = nextMissingCommentPage(getRootPaging())
        if (missingPage === null) break
        await loadRootPage(missingPage, request)
        if (!current()) return
      }
      const root = findRoot()
      if (!root) throw new Error()
      const findTarget = () => targetId === rootId ? root : root.childComments?.find(child => String(child.commentId) === targetId)
      const childHint = Number(context.childPageNo)
      if (!findTarget() && Number.isInteger(childHint) && childHint > 0) {
        await loadChildPage(root, childHint, request)
        if (!current()) return
      }
      while (!findTarget()) {
        const missingPage = nextMissingCommentPage(root.childPaging || createCommentPageState())
        if (missingPage === null) break
        await loadChildPage(root, missingPage, request)
        if (!current()) return
      }
      if (!findTarget()) throw new Error()
      await onFound(targetId, current)
      if (current()) onState({ loading: false, error: '', unavailableReason: '' })
    } catch {
      if (!current()) return
      // The target can disappear between its location response and the normal page request.
      if (contextKnown) {
        try {
          const latest = await api.getCommentContext(String(noteId), targetId, request.options)
          if (!current()) return
          if (latest.success && (!latest.data?.target || latest.data.unavailableReason)) {
            onState({ loading: false, error: '', unavailableReason: latest.data?.unavailableReason || 'COMMENT_UNAVAILABLE' })
            return
          }
        } catch { if (!current()) return }
      }
      onState({ loading: false, error: '相关评论暂时无法定位，请重试', unavailableReason: '' })
    } finally {
      if (controller === ownController) controller = null
    }
  }
  return { locate, cancel }
}

export function scrollCommentIntoContainer(container, target, behavior = 'auto') {
  const targetRect = target.getBoundingClientRect()
  // A root comment contains its replies; center only the comment's own content.
  const actions = target.querySelector(':scope > .comment-item__body > .comment-item__actions')
  const targetHeight = actions ? actions.getBoundingClientRect().bottom - targetRect.top : targetRect.height
  const visibleHeight = container.clientHeight
  const offset = (visibleHeight - Math.min(targetHeight, visibleHeight)) / 2
  const top = container.scrollTop + targetRect.top - container.getBoundingClientRect().top - container.clientTop - offset
  const maxTop = Math.max(0, container.scrollHeight - visibleHeight)
  container.scrollTo({ top: Math.max(0, Math.min(top, maxTop)), behavior })
}

// Keep a newly located target stable while images settle; user input immediately owns scrolling.
export function createCommentScrollAnchor({ ResizeObserverClass = globalThis.ResizeObserver, setTimer = setTimeout, clearTimer = clearTimeout } = {}) {
  let cleanup = () => {}
  const stop = () => cleanup()
  const start = (container, target, { isCurrent = () => true, reducedMotion = false } = {}) => {
    stop()
    if (!container || !target || !isCurrent()) return false
    let observer
    let timer
    let stopped = false
    const events = ['wheel', 'touchstart', 'pointerdown', 'keydown']
    const adjust = () => {
      if (!isCurrent()) { finish(); return }
      if (!stopped) scrollCommentIntoContainer(container, target)
    }
    const finish = () => {
      if (stopped) return
      stopped = true
      observer?.disconnect()
      clearTimer(timer)
      events.forEach(event => container.removeEventListener(event, finish, true))
      container.removeEventListener('load', adjust, true)
      cleanup = () => {}
    }
    cleanup = finish
    events.forEach(event => container.addEventListener(event, finish, { capture: true, passive: true }))
    container.addEventListener('load', adjust, true)
    if (ResizeObserverClass) {
      observer = new ResizeObserverClass(adjust)
      observer.observe(container)
      observer.observe(target)
      for (const child of container.children) observer.observe(child)
    }
    timer = setTimer(() => {
      // Fixed image thumbnails reserve their space; slower images elsewhere retain a bounded anchor.
      if ([...container.querySelectorAll('img')].some(image => !image.complete)) timer = setTimer(finish, 11000)
      else finish()
    }, 4000)
    scrollCommentIntoContainer(container, target, reducedMotion ? 'auto' : 'smooth')
    target.focus?.({ preventScroll: true })
    return true
  }
  return { start, stop }
}

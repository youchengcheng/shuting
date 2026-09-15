/**
 * 笔记详情「展开 / 收回」动画的几何计算与时间线。
 *
 * 打开：封面从卡片原位一路放大飞到详情媒体区，深色遮罩、详情卡片、右侧面板
 * 依次错峰淡入；关闭：封面沿同一条路线收回卡片里。整套动作只动浮层自己的元素，
 * 信息流不参与，所以不会出现整页叠影。
 *
 * 之前用 View Transitions API 的问题：浏览器会对整页做快照交叉淡化，
 * 打开瞬间能同时看到信息流和详情（发白、重影），封面也只是一次性跳到终点，
 * 没有「从卡片里长出来」的连续过程；而且浮层锁滚动会改变根快照尺寸，
 * 连整页都会被拉伸。这里改为显式的封面飞行，时序完全可控。
 */

// 时间线（毫秒）。封面飞行是主角，其余元素依次跟上，整体 460ms 内落定
const OPEN = {
  flight: { duration: 440, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' },
  scrim: { duration: 220, easing: 'cubic-bezier(0.4, 0, 0.2, 1)' },
  card: { duration: 320, delay: 40, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' },
  panel: { duration: 260, delay: 120, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' },
  closeButton: { duration: 200, delay: 240, easing: 'cubic-bezier(0.22, 1, 0.36, 1)' }
}

// 关闭比打开略快一点，收回卡片时更利落
const CLOSE = {
  flight: { duration: 360, easing: 'cubic-bezier(0.4, 0, 0.2, 1)' },
  card: { duration: 200, easing: 'cubic-bezier(0.4, 0, 0.2, 1)' },
  scrim: { duration: 240, delay: 40, easing: 'cubic-bezier(0.4, 0, 0.2, 1)' }
}

// 详情卡片轻轻浮起，右侧面板从右往左跟上
const CARD_ENTER = { from: { opacity: 0, transform: 'translateY(14px) scale(0.985)' }, to: { opacity: 1, transform: 'none' } }
const PANEL_ENTER = { from: { opacity: 0, transform: 'translateX(24px)' }, to: { opacity: 1, transform: 'none' } }
const CARD_LEAVE = { from: { opacity: 1, transform: 'none' }, to: { opacity: 0, transform: 'translateY(10px) scale(0.985)' } }

const GHOST_CLASS = 'note-overlay__ghost'

export const prefersReducedMotion = () =>
  typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches

export const rectOf = (el) => {
  const rect = el.getBoundingClientRect()
  return { x: rect.left, y: rect.top, w: rect.width, h: rect.height }
}

// 按给定比例在容器里居中取最大矩形，与详情页 object-fit: contain 的渲染结果一致
export const containRect = (box, aspect) => {
  if (!aspect || !box.w || !box.h) return { ...box }
  let w = box.w
  let h = w / aspect
  if (h > box.h) {
    h = box.h
    w = h * aspect
  }
  return { x: box.x + (box.w - w) / 2, y: box.y + (box.h - h) / 2, w, h }
}

/**
 * 图片真实比例。
 * 详情媒体区的图片元素框会被撑到整个媒体区（h-full + max-width:100%），
 * 量元素尺寸得到的是容器的比例，所以这里优先用 naturalWidth。
 */
export const aspectOf = (imgEl, fallbackEl) => {
  if (imgEl && imgEl.naturalWidth && imgEl.naturalHeight) {
    return imgEl.naturalWidth / imgEl.naturalHeight
  }
  const el = imgEl || fallbackEl
  if (!el) return 0
  const rect = rectOf(el)
  return rect.h ? rect.w / rect.h : 0
}

const radiusOf = (el) => {
  if (!el) return 0
  const value = parseFloat(getComputedStyle(el).borderTopLeftRadius)
  return Number.isFinite(value) ? value : 0
}

// 卡片封面：媒体区本身就是图片比例，历史脏数据比例不一致时按图片比例居中裁一次
const coverRectOf = (el, aspect) => containRect(rectOf(el), aspect)

// 页面上任意一篇笔记的封面（信息流 / 个人页 / 搜索结果共用同一套卡片）
export const findNoteCover = (noteId) => {
  if (noteId === undefined || noteId === null || noteId === '') return null
  return document.querySelector(`[data-note-cover="${String(noteId)}"]`)
}

const play = (el, keyframes, timing) => {
  if (!el) return null
  return el.animate(keyframes, { fill: 'both', ...timing })
}

/**
 * 飞行封面。
 * 元素由脚本创建，带不上组件的 scoped 样式，所以外观全部写在内联样式里：
 * 固定在视口坐标系，压在详情卡片之上、关闭按钮之下。
 */
const createGhost = (host, src, radius) => {
  const img = document.createElement('img')
  img.className = GHOST_CLASS
  img.src = src
  img.alt = ''
  img.setAttribute('aria-hidden', 'true')
  // 卡片用的是同一张图，这里能从内存缓存里直接解码，避免飞行途中才亮起来
  img.decoding = 'sync'
  Object.assign(img.style, {
    position: 'fixed',
    zIndex: '2',
    margin: '0',
    display: 'block',
    pointerEvents: 'none',
    objectFit: 'cover',
    willChange: 'transform',
    // 位移 + 缩放都按左上角算，才能用「目标位置 + 反向变换」精确还原卡片原位
    transformOrigin: '0 0',
    borderRadius: `${radius}px`
  })
  host.appendChild(img)
  return img
}

/**
 * 封面飞行。
 * 幽灵元素固定在目标位置上，用「相对位移 + 等比缩放」还原到起点：
 * 起止两个矩形比例相同（同一张图），所以缩放是等比的，途中不会变形。
 */
const playFlight = (ghostEl, from, to, { duration, delay = 0, easing, fromRadius = 0, toRadius = 0 }) => {
  const scale = to.w ? from.w / to.w : 1
  ghostEl.style.left = `${to.x}px`
  ghostEl.style.top = `${to.y}px`
  ghostEl.style.width = `${to.w}px`
  ghostEl.style.height = `${to.h}px`
  return ghostEl.animate(
    [
      {
        transform: `translate(${from.x - to.x}px, ${from.y - to.y}px) scale(${scale})`,
        borderRadius: `${fromRadius / (scale || 1)}px`
      },
      { transform: 'none', borderRadius: `${toRadius}px` }
    ],
    { duration, delay, easing, fill: 'both' }
  )
}

// 飞行期间把真实媒体藏起来，落地后同一帧换回来，避免同一张图出现两次
const setMediaHidden = (mediaEl, hidden) => {
  if (mediaEl) mediaEl.style.visibility = hidden ? 'hidden' : ''
}

const waitAnimations = (animations) =>
  Promise.all(animations.filter(Boolean).map((animation) => animation.finished.catch(() => {})))

/**
 * 展开动画。
 * hero 由 useNoteTransition 在点击卡片时记录：{ rect, aspect, cover, radius }。
 * 没有起点（例如直接打开笔记链接）时只做浮层自身的淡入，不做飞行。
 */
export async function playOpenMorph({ overlayEl, hero }) {
  if (!overlayEl) return

  const scrimEl = overlayEl.querySelector('[data-overlay-scrim]')
  const cardEl = overlayEl.querySelector('[data-overlay-card]')
  const panelEl = overlayEl.querySelector('[data-note-panel]')
  const closeEl = overlayEl.querySelector('[data-overlay-close]')
  const mediaEl = overlayEl.querySelector('[data-note-media]')

  if (prefersReducedMotion()) return

  // 落点必须在启动卡片动画之前量：卡片一进场就带 translate / scale，
  // 那之后再量到的媒体区尺寸是形变后的，封面会落偏
  const target =
    mediaEl && hero?.cover && hero.rect && hero.aspect ? containRect(rectOf(mediaEl), hero.aspect) : null

  const anim = [
    play(scrimEl, [{ opacity: 0 }, { opacity: 1 }], OPEN.scrim),
    play(cardEl, [CARD_ENTER.from, CARD_ENTER.to], OPEN.card),
    play(panelEl, [PANEL_ENTER.from, PANEL_ENTER.to], OPEN.panel),
    play(closeEl, [{ opacity: 0 }, { opacity: 1 }], OPEN.closeButton)
  ]
  // 动画落定后清掉填充，避免长期挂着一层动画影响后续样式
  waitAnimations(anim).then(() => anim.forEach((item) => item && item.cancel()))

  if (!target) return

  const ghostEl = createGhost(overlayEl, hero.cover, hero.radius)
  setMediaHidden(mediaEl, true)

  const flight = playFlight(ghostEl, hero.rect, target, {
    ...OPEN.flight,
    fromRadius: hero.radius,
    toRadius: 0
  })

  await flight.finished.catch(() => {})
  setMediaHidden(mediaEl, false)
  ghostEl.remove()
}

/**
 * 收起动画：封面飞回卡片。
 * 找不到原卡片（例如直接打开链接进来）时只做淡出。
 */
export async function playCloseMorph({ overlayEl, noteId }) {
  if (!overlayEl) return

  const scrimEl = overlayEl.querySelector('[data-overlay-scrim]')
  const cardEl = overlayEl.querySelector('[data-overlay-card]')
  const mediaEl = overlayEl.querySelector('[data-note-media]')

  if (prefersReducedMotion()) return

  // 起点与终点都在启动淡出动画之前量，避免量到形变中的坐标
  const coverEl = findNoteCover(noteId)
  const imgEl = mediaEl?.querySelector('img')
  const flightReady = Boolean(mediaEl && coverEl && imgEl?.currentSrc)
  const aspect = flightReady ? aspectOf(imgEl, mediaEl) : 0
  const from = flightReady ? containRect(rectOf(mediaEl), aspect) : null
  const to = flightReady ? coverRectOf(coverEl, aspect) : null
  const radius = flightReady ? radiusOf(coverEl) : 0

  const fades = [
    play(cardEl, [CARD_LEAVE.from, CARD_LEAVE.to], CLOSE.card),
    play(scrimEl, [{ opacity: 1 }, { opacity: 0 }], CLOSE.scrim)
  ]

  if (!flightReady) {
    await waitAnimations(fades)
    return
  }

  const ghostEl = createGhost(overlayEl, imgEl.currentSrc, 0)
  setMediaHidden(mediaEl, true)

  const flight = playFlight(ghostEl, from, to, {
    ...CLOSE.flight,
    fromRadius: 0,
    toRadius: radius
  })

  await flight.finished.catch(() => {})
  setMediaHidden(mediaEl, false)
  ghostEl.remove()
}

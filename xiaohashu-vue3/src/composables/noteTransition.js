import { nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { useNoteStore } from '@/stores/note'

/**
 * 笔记详情的共享元素过渡。
 *
 * 点开笔记时封面从卡片原位向外扩张到详情面板的媒体区，关闭时反向收回，
 * 用浏览器原生的 View Transitions API 实现；不支持或用户偏好减少动效时，
 * 退化成原来的淡入淡出，不影响任何功能。
 */

// 全局同一时刻只允许一个 note-hero 命名元素，重名会被浏览器整段忽略
const HERO_NAME = 'note-hero'

let namedElement = null

const supportsViewTransition = () =>
  typeof document !== 'undefined' &&
  typeof document.startViewTransition === 'function' &&
  !window.matchMedia('(prefers-reduced-motion: reduce)').matches

const clearHeroName = () => {
  if (!namedElement) return
  namedElement.style.viewTransitionName = ''
  namedElement = null
}

const setHeroName = (el) => {
  clearHeroName()
  if (!el) return
  el.style.viewTransitionName = HERO_NAME
  namedElement = el
}

// 详情媒体区的命名由 html.note-vt 承载（见 main.css），
// 开关必须在「旧状态」快照前完成，所以调用方要保证在 startViewTransition 之前同步设置
const setTransitionFlag = (on) => {
  document.documentElement.classList.toggle('note-vt', on)
}

// 页面上某篇笔记的封面元素（信息流 / 个人页 / 搜索页共用同一套卡片）
const findNoteCover = (noteId) => {
  if (noteId === undefined || noteId === null || noteId === '') return null
  return document.querySelector(`[data-note-cover="${String(noteId)}"]`)
}

// router.back() 是异步的 history 遍历，没有 Promise 可等，这里等一次路由落地
const waitForRouteChange = (router) =>
  new Promise((resolve) => {
    const stop = router.afterEach(() => {
      stop()
      resolve()
    })
    window.setTimeout(() => {
      stop()
      resolve()
    }, 500)
  })

export function useNoteTransition() {
  const router = useRouter()
  const noteStore = useNoteStore()

  // 打开详情：卡片封面 → 详情媒体区
  const openNote = async (note, target) => {
    const noteId = note?.id ?? note?.noteId
    if (!noteId || !target) return

    // 卡片上已加载的封面先顶上媒体区，避免数据到位前媒体区空白
    noteStore.setPendingHero({ noteId, cover: note.cover || '', type: note.type })

    const coverEl = findNoteCover(noteId)
    if (!supportsViewTransition() || !coverEl) {
      await router.push(target)
      return
    }

    setTransitionFlag(true)
    setHeroName(coverEl)

    const transition = document.startViewTransition(async () => {
      await router.push(target)
      await nextTick()
      // 旧状态的命名必须在新快照前摘掉：详情媒体区此时已接管 note-hero
      clearHeroName()
    })

    transition.finished.finally(() => setTransitionFlag(false))
  }

  // 关闭详情：详情媒体区 → 卡片封面
  // performClose 是原有的关闭逻辑（返回上一页 / 兜底回到父页面）
  const closeNote = async (noteId, performClose) => {
    const coverEl = findNoteCover(noteId)
    if (!supportsViewTransition() || !coverEl) {
      performClose()
      return
    }

    setTransitionFlag(true)
    await nextTick()

    const transition = document.startViewTransition(async () => {
      performClose()
      await waitForRouteChange(router)
      await nextTick()
      setHeroName(coverEl)
    })

    transition.finished.finally(() => {
      clearHeroName()
      setTransitionFlag(false)
    })
  }

  return { openNote, closeNote }
}

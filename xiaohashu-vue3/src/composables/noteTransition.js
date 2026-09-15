import { useRouter } from 'vue-router'
import { useNoteStore } from '@/stores/note'
import { aspectOf, findNoteCover, playCloseMorph, rectOf } from '@/composables/noteMorph'

/**
 * 笔记详情的打开 / 关闭过渡。
 *
 * 打开：先把「从哪张卡片长出来」记进 store（位置、封面、图片比例、圆角），
 * 再切路由；浮层挂载后按这份起点做展开动画。
 * 关闭：封面沿同一条路线飞回卡片，动画播完再执行真正的关闭逻辑。
 *
 * 动画本身在 composables/noteMorph.js 里，这里只负责时机与数据。
 */
export function useNoteTransition() {
  const router = useRouter()
  const noteStore = useNoteStore()

  // 打开详情：记录起点后切路由
  const openNote = async (note, target) => {
    const noteId = note?.id ?? note?.noteId
    if (!noteId || !target) return

    const coverEl = findNoteCover(noteId)
    const imgEl = coverEl?.querySelector('img')
    // 视频笔记卡片上是 video 元素，飞行封面仍然是封面图
    const mediaEl = imgEl || coverEl?.querySelector('video') || coverEl

    noteStore.setPendingHero({
      noteId,
      cover: imgEl?.currentSrc || note.cover || '',
      type: note.type,
      rect: coverEl ? rectOf(coverEl) : null,
      radius: coverEl ? parseFloat(getComputedStyle(coverEl).borderTopLeftRadius) || 0 : 0,
      aspect: aspectOf(imgEl, mediaEl)
    })

    await router.push(target)
  }

  // 关闭详情：performClose 是原有的关闭逻辑（返回上一页 / 兜底回到父页面）
  const closeNote = async (noteId, performClose) => {
    await playCloseMorph({ overlayEl: document.querySelector('.note-overlay'), noteId })
    performClose()
  }

  return { openNote, closeNote }
}
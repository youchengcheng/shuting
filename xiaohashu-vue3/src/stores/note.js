import { defineStore } from 'pinia'
import { ref } from 'vue'

/**
 * 笔记相关的全局状态
 * 发布成功等场景下，通过 discoverRefreshToken 通知信息流重新拉取第一页数据，
 * 替代此前直接调用 location.reload() 的整页刷新。
 */
export const useNoteStore = defineStore('note', () => {
  const discoverRefreshToken = ref(0)

  const requestDiscoverRefresh = () => {
    discoverRefreshToken.value += 1
  }

  // 打开笔记详情时的「乐观封面」：卡片上已经加载好的封面先顶上媒体区。
  // 既避免详情数据到位前媒体区空白，也让展开动画有连续的图像可以形变。
  // { noteId, cover, type } | null
  const pendingHero = ref(null)

  const setPendingHero = (hero) => {
    pendingHero.value = hero
  }

  return {
    discoverRefreshToken,
    requestDiscoverRefresh,
    pendingHero,
    setPendingHero
  }
})

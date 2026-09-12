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

  return {
    discoverRefreshToken,
    requestDiscoverRefresh
  }
})

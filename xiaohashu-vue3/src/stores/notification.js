import { defineStore } from 'pinia'
import { reactive, toRefs } from 'vue'
import { useUserStore } from './user'
import * as api from '@/api/notification'
import { createNotificationController, emptyNotificationState } from '@/utils/notificationState'

export const useNotificationStore = defineStore('notification', () => {
  const state = reactive(emptyNotificationState())
  return { ...toRefs(state), ...createNotificationController(state, api, useUserStore()) }
})

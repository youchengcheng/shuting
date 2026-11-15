import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import { shouldRefreshNotificationSignal } from '@/utils/notificationState'
import { createSseParser } from '@/utils/sse'

// 建立通知实时连接，断开后按退避时间重连
export function useNotificationStream() {
  const user = useUserStore()
  const notifications = useNotificationStore()
  let controller = null
  let retryTimer = null
  let watchdog = null
  let attempt = 0
  let lifecycle = 0
  let mounted = false

  // 页面可见、已登录且联网时才建立连接
  const eligible = () => mounted && !!user.token && document.visibilityState === 'visible' && navigator.onLine

  // 关闭连接并清理重连及心跳定时器
  const stop = () => {
    lifecycle += 1
    controller?.abort()
    controller = null
    clearTimeout(retryTimer)
    clearInterval(watchdog)
    retryTimer = watchdog = null
  }

  // 建立 SSE 连接并处理通知事件
  const connect = async () => {
    if (!eligible() || controller) {
      return
    }
    const session = user.captureSession()
    const version = lifecycle
    const ownController = new AbortController()
    controller = ownController
    notifications.connection = 'connecting'
    let lastData = Date.now()
    watchdog = setInterval(() => {
      if (Date.now() - lastData > 45000) {
        ownController.abort()
      }
    }, 5000)
    const current = () => version === lifecycle && user.isCurrentSession(session)
    try {
      void notifications.refreshSummary()
      void notifications.refreshReadState()
      const response = await fetch('/api/notification/stream', {
        headers: {
          Authorization: `Bearer ${session.token}`,
          Accept: 'text/event-stream'
        },
        signal: ownController.signal,
        cache: 'no-store'
      })
      if (!current()) {
        return
      }
      if (response.status === 401) {
        user.logout(session)
        return
      }
      if (!response.ok || !response.headers.get('content-type')?.toLowerCase().includes('text/event-stream') || !response.body) {
        if (response.headers.get('content-type')?.includes('json')) {
          const body = await response.json()
          if (current() && /AUTH|TOKEN|UNAUTHORIZED/i.test(body.errorCode || '')) {
            user.logout(session)
            return
          }
        }
        throw new Error('Notification stream unavailable')
      }
      const parser = createSseParser(({ event, data }) => {
        if (!current()) {
          return
        }
        if (event === 'auth-expired') {
          user.logout(session)
          ownController.abort()
          return
        }
        if (event === 'service-unavailable') {
          ownController.abort()
          return
        }
        if (!['ready', 'changed', 'heartbeat'].includes(event)) {
          return
        }
        const signal = JSON.parse(data)
        if (event === 'ready') {
          attempt = 0
          notifications.connection = 'connected'
        }
        if (shouldRefreshNotificationSignal(event, signal.revision, notifications)) {
          notifications.changed(signal.revision)
        }
      })
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      try {
        while (current()) {
          const { value, done } = await reader.read()
          if (done) {
            break
          }
          lastData = Date.now()
          parser(decoder.decode(value, { stream: true }))
        }
      } finally {
        reader.releaseLock()
      }
    } catch {
      // 页面显示重连状态，避免每次重试都弹出提示
    } finally {
      if (!current()) {
        return
      }
      clearInterval(watchdog)
      watchdog = null
      controller = null
      if (eligible()) {
        notifications.connection = 'reconnecting'
        const delay = [1000, 2000, 4000, 8000, 16000, 30000][Math.min(attempt++, 5)]
        retryTimer = setTimeout(connect, delay)
      }
    }
  }

  // 页面可见性或网络状态变化时重新检查连接
  const reconcile = () => {
    stop()
    if (!user.token) {
      notifications.connection = 'idle'
    } else if (!navigator.onLine) {
      notifications.connection = 'offline'
    } else if (document.visibilityState !== 'visible') {
      notifications.connection = 'hidden'
    } else {
      void connect()
    }
  }

  // 切换账号时取消旧连接并重置通知状态
  watch(() => user.generation, () => {
    stop()
    attempt = 0
    notifications.reset()
    reconcile()
  }, { flush: 'sync' })

  // 组件挂载时监听网络和页面状态
  onMounted(() => {
    mounted = true
    window.addEventListener('storage', user.syncStorage)
    window.addEventListener('online', reconcile)
    window.addEventListener('offline', reconcile)
    document.addEventListener('visibilitychange', reconcile)
    reconcile()
  })

  // 组件卸载时移除事件监听
  onBeforeUnmount(() => {
    mounted = false
    stop()
    notifications.reset()
    window.removeEventListener('storage', user.syncStorage)
    window.removeEventListener('online', reconcile)
    window.removeEventListener('offline', reconcile)
    document.removeEventListener('visibilitychange', reconcile)
  })
}

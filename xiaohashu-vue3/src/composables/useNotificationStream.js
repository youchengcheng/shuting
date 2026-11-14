import { onBeforeUnmount, onMounted, watch } from 'vue'
import { useUserStore } from '@/stores/user'
import { useNotificationStore } from '@/stores/notification'
import { shouldRefreshNotificationSignal } from '@/utils/notificationState'
import { createSseParser } from '@/utils/sse'

export function useNotificationStream() {
  const user = useUserStore()
  const notifications = useNotificationStore()
  let controller = null
  let retryTimer = null
  let watchdog = null
  let attempt = 0
  let lifecycle = 0
  let mounted = false
  const eligible = () => mounted && !!user.token && document.visibilityState === 'visible' && navigator.onLine
  const stop = () => {
    lifecycle += 1
    controller?.abort()
    controller = null
    clearTimeout(retryTimer)
    clearInterval(watchdog)
    retryTimer = watchdog = null
  }
  const connect = async () => {
    if (!eligible() || controller) return
    const session = user.captureSession()
    const version = lifecycle
    const ownController = new AbortController()
    controller = ownController
    notifications.connection = 'connecting'
    let lastData = Date.now()
    watchdog = setInterval(() => { if (Date.now() - lastData > 45000) ownController.abort() }, 5000)
    const current = () => version === lifecycle && user.isCurrentSession(session)
    try {
      void notifications.refreshSummary()
      void notifications.refreshReadState()
      const response = await fetch('/api/notification/stream', {
        headers: { Authorization: `Bearer ${session.token}`, Accept: 'text/event-stream' },
        signal: ownController.signal, cache: 'no-store'
      })
      if (!current()) return
      if (response.status === 401) { user.logout(session); return }
      if (!response.ok || !response.headers.get('content-type')?.toLowerCase().includes('text/event-stream') || !response.body) {
        if (response.headers.get('content-type')?.includes('json')) {
          const body = await response.json()
          if (current() && /AUTH|TOKEN|UNAUTHORIZED/i.test(body.errorCode || '')) { user.logout(session); return }
        }
        throw new Error('Notification stream unavailable')
      }
      const parser = createSseParser(({ event, data }) => {
        if (!current()) return
        if (event === 'auth-expired') { user.logout(session); ownController.abort(); return }
        if (event === 'service-unavailable') { ownController.abort(); return }
        if (!['ready', 'changed', 'heartbeat'].includes(event)) return
        const signal = JSON.parse(data)
        if (event === 'ready') { attempt = 0; notifications.connection = 'connected' }
        if (shouldRefreshNotificationSignal(event, signal.revision, notifications)) notifications.changed(signal.revision)
      })
      const reader = response.body.getReader()
      const decoder = new TextDecoder()
      try {
        while (current()) {
          const { value, done } = await reader.read()
          if (done) break
          lastData = Date.now()
          parser(decoder.decode(value, { stream: true }))
        }
      } finally { reader.releaseLock() }
    } catch { /* The page shows reconnection; do not toast every retry. */ }
    finally {
      if (!current()) return
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
  const reconcile = () => {
    stop()
    if (!user.token) notifications.connection = 'idle'
    else if (!navigator.onLine) notifications.connection = 'offline'
    else if (document.visibilityState !== 'visible') notifications.connection = 'hidden'
    else void connect()
  }
  watch(() => user.generation, () => {
    stop()
    attempt = 0
    notifications.reset()
    reconcile()
  }, { flush: 'sync' })
  onMounted(() => {
    mounted = true
    window.addEventListener('storage', user.syncStorage)
    window.addEventListener('online', reconcile)
    window.addEventListener('offline', reconcile)
    document.addEventListener('visibilitychange', reconcile)
    reconcile()
  })
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

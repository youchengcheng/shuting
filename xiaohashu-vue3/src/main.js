import '@/assets/main.css'

import { createApp } from 'vue'
import App from '@/App.vue'

// 导入路由
import router from '@/router'

// 引入全局状态管理 Pinia
import pinia from '@/stores'

// 图片兜底：后端存在历史失效的图片地址（404 / 跨域被拦），
// 加载失败时打上标记，由样式隐藏浏览器默认的破图图标与 alt 文案。
// img 的 error 事件不冒泡，必须使用捕获阶段监听。
window.addEventListener(
  'error',
  (event) => {
    const target = event.target
    if (target instanceof HTMLImageElement && !target.classList.contains('st-img--failed')) {
      target.classList.add('st-img--failed')
      target.removeAttribute('alt')
    }
  },
  true
)

const app = createApp(App)

// 应用路由
app.use(router)
// 应用 Pinia
app.use(pinia)

// 整页刷新的过渡：index.html 里的首屏加载层先于 JS 呈现，应用挂载完成后再淡出，
// 避免刷新时白屏硬切。最短展示时长用于避免加载快时加载层一闪而过。
const BOOT_MIN_VISIBLE = 260

const dismissBootLayer = () => {
  const bootEl = document.getElementById('boot')
  if (!bootEl) return

  // performance.now() 即导航开始至今的时长，可近似视为加载层已展示的时间
  const elapsed = window.performance?.now?.() ?? BOOT_MIN_VISIBLE
  const delay = Math.max(0, BOOT_MIN_VISIBLE - elapsed)

  window.setTimeout(() => {
    bootEl.classList.add('boot--done')

    const removeBootLayer = () => bootEl.remove()
    // reduced-motion 下过渡被禁用，没有 transitionend，用定时器兜底
    bootEl.addEventListener('transitionend', removeBootLayer, { once: true })
    window.setTimeout(removeBootLayer, 600)
  }, delay)
}

try {
  app.mount('#app')
} finally {
  dismissBootLayer()
}

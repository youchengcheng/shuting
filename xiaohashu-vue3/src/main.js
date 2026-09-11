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

app.mount('#app')

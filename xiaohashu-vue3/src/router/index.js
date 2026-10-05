import { createRouter, createWebHistory } from 'vue-router'
import BasicLayout from '@/layouts/BasicLayout.vue'

// 笔记详情浮层：作为信息流 / 个人页 / 搜索页的子路由渲染
// 打开时底层页面保持挂载，关闭时只卸载浮层，因此返回后不会重新请求数据
const NoteDetailOverlay = () => import('@/components/note/NoteDetailOverlay.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: BasicLayout,
      redirect: '/discover',
      children: [
        {
          path: '/discover',
          name: 'Discover',
          component: () => import('@/views/Discover.vue'),
          children: [
            {
              path: 'note/:noteId',
              name: 'DiscoverNoteDetail',
              component: NoteDetailOverlay,
              props: true
            }
          ]
        },
        {
          path: '/notifications',
          name: 'Notifications',
          component: () => import('@/views/Notifications.vue')
        },
        {
          path: '/user/profile/:userId',
          name: 'Profile',
          component: () => import('@/views/Profile.vue'),
          children: [
            {
              path: 'note/:noteId',
              name: 'ProfileNoteDetail',
              component: NoteDetailOverlay,
              props: true
            }
          ]
        },
        {
          path: '/search',
          name: 'Search',
          component: () => import('@/views/SearchResult.vue'),
          children: [
            {
              path: 'note/:noteId',
              name: 'SearchNoteDetail',
              component: NoteDetailOverlay,
              props: true
            }
          ]
        },
        {
          // AI 助手：用自然语言检索全站已发布笔记，判断对题后润色返回
          path: '/ai',
          name: 'AiAssistant',
          component: () => import('@/views/AiAssistant.vue'),
          children: [
            {
              path: 'note/:noteId',
              name: 'AiNoteDetail',
              component: NoteDetailOverlay,
              props: true
            }
          ]
        },
        {
          // 直接访问笔记链接时，落到信息流之上的浮层
          path: '/note/:noteId',
          redirect: to => ({ path: `/discover/note/${to.params.noteId}` })
        },
        {
          path: '/user/:userId/relation',
          name: 'RelationList',
          component: () => import('@/views/RelationList.vue')
        }
      ]
    }
  ]
})

export default router

<template>
  <router-view></router-view>

  <!-- 全局模态框：登录 / 发布 -->
  <Teleport to="body">
    <LoginModal v-model:visible="showLoginModal" />
    <PublishModal v-model:visible="showPublishModal" />
  </Teleport>
</template>

<script setup>
import { onMounted, provide, ref } from 'vue'
import LoginModal from '@/components/auth/LoginModal.vue'
import PublishModal from '@/components/note/PublishModal.vue'
import { useChannelStore } from '@/stores/channel'

const showLoginModal = ref(false)
const showPublishModal = ref(false)

provide('showLoginModal', showLoginModal)
provide('showPublishModal', showPublishModal)

const channelStore = useChannelStore()

onMounted(() => {
  // 加载频道数据
  channelStore.loadChannels()
})
</script>

<template>
  <div class="category-nav">
    <div class="category-nav__track" role="tablist" aria-label="频道">
      <button
        type="button"
        class="channel"
        role="tab"
        :aria-selected="activeChannelId === 0 ? 'true' : 'false'"
        :class="{ 'channel--active': activeChannelId === 0 }"
        @click="handleChannelClick(0)"
      >
        <span class="channel__text">推荐</span>
      </button>

      <button
        v-for="channel in channels"
        :key="channel.id"
        type="button"
        class="channel"
        role="tab"
        :aria-selected="activeChannelId === channel.id ? 'true' : 'false'"
        :class="{ 'channel--active': activeChannelId === channel.id }"
        @click="handleChannelClick(channel.id)"
      >
        <span class="channel__text">{{ channel.name }}</span>
      </button>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useChannelStore } from '@/stores/channel'

defineProps({
  activeChannelId: {
    type: Number,
    default: 0
  }
})

const emit = defineEmits(['channel-change'])

const channelStore = useChannelStore()
const channels = ref([])

// 处理频道点击
const handleChannelClick = (channelId) => {
  emit('channel-change', channelId)
}

// 组件挂载时获取频道列表
onMounted(async () => {
  // 如果 store 中已有频道数据，直接使用
  if (channelStore.channels.length > 0) {
    channels.value = channelStore.channels
  } else {
    // 否则获取频道数据
    await channelStore.loadChannels()
    channels.value = channelStore.channels
  }
})
</script>

<style scoped>
.category-nav {
  position: sticky;
  top: var(--header-h);
  z-index: 20;
  height: 56px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-canvas);
}

.category-nav__track {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  max-width: 100%;
  overflow-x: auto;
  white-space: nowrap;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.category-nav__track::-webkit-scrollbar {
  display: none;
}

.channel {
  position: relative;
  height: 44px;
  padding: 0 12px;
  border: none;
  background: transparent;
  color: var(--color-ink-soft);
  font-size: 16px;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
  user-select: none;
  transition: color var(--motion-fast) var(--ease-standard);
}

.channel:hover {
  color: var(--color-ink);
}

.channel--active {
  color: var(--color-ink);
  font-weight: 600;
}

/* 激活频道：文字下方红色短下划线（宽度贴合文字） */
.channel--active .channel__text {
  position: relative;
}

.channel--active .channel__text::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -10px;
  height: 2px;
  border-radius: 2px;
  background: var(--color-brand);
}

.channel__text {
  display: inline-block;
}
</style>

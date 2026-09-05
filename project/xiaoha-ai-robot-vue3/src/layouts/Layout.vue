<template>
    <div class="h-screen flex overflow-hidden overflow-x-hidden bg-white text-[#171717]">
        <!-- 左边栏 -->
        <Sidebar
            :sidebarOpen="sidebarOpen"
            @toggle-sidebar="toggleSidebar"
            />
        
        <!-- 主内容区域 -->
        <div :class="sidebarOpen ? 'ml-[280px]' : 'ml-0'" class="flex flex-col flex-1 min-w-0 transition-all duration-300 bg-white">
            <!-- 插槽 -->
            <slot name="main-content"></slot>
        </div>

    </div>
</template>

<script setup>
import { ref } from 'vue'
import Sidebar from '@/components/Sidebar.vue'

// 左边栏状态
const sidebarOpen = ref(true)

// 切换侧边栏显示/隐藏
const toggleSidebar = () => {
  sidebarOpen.value = !sidebarOpen.value;
}

</script>

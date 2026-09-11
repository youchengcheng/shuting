<template>
  <div 
    class="relative h-full flex items-center justify-center group"
    @mouseenter="showControls = true"
    @mouseleave="showControls = false"
    @wheel="handleWheel"
  >
    <!-- 图片容器 -->
    <div class="h-full overflow-hidden">
      <div 
        class="h-full flex transition-transform duration-500 ease-in-out"
        :style="{ transform: `translateX(-${currentIndex * 100}%)` }"
      >
        <div 
          v-for="(image, index) in images" 
          :key="index"
          class="h-full w-full flex-shrink-0 flex items-center justify-center bg-canvas-sunken border-r border-line"
        >
          <img 
            v-if="!failedIndexes.includes(index)"
            :src="image" 
            class="h-full w-auto object-contain cursor-zoom-in" 
            alt=""
            @load="onImageLoad"
            @error="onImageError(index)"
            ref="imageRefs"
            @click="onImageClick"
          />

          <!-- 后端存在历史失效图片地址，兜底为占位，避免出现浏览器默认的破图图标 -->
          <span v-else class="carousel-fallback">
            <svg viewBox="0 0 24 24" fill="none" aria-hidden="true">
              <path
                d="M5.5 15.5c2.2-.7 4.4-.1 6.5 1.6 2.1-1.7 4.3-2.3 6.5-1.6"
                stroke="currentColor"
                stroke-width="1.4"
                stroke-linecap="round"
              />
              <path d="M12 17.1v3.4" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
            </svg>
            <span class="carousel-fallback__text">图片暂时无法显示</span>
          </span>
        </div>
      </div>
    </div>

    <!-- 图片计数器 -->
    <Transition name="fade">
      <div 
        v-if="images.length > 1 && showControls"
        class="absolute top-4 right-4 px-[12px] py-[6px] rounded-full bg-black/40 backdrop-blur-sm text-white text-xs"
      >
        {{ currentIndex + 1 }}/{{ images.length }}
      </div>
    </Transition>

    <!-- 左右切换按钮 -->
    <Transition name="fade">
      <button 
        v-if="images.length > 1 && showControls"
        class="carousel-btn left-4" 
        @click="prev"
      >
        <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor">
          <path d="M15 19l-7-7 7-7" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </Transition>
    
    <Transition name="fade">
      <button 
        v-if="images.length > 1 && showControls"
        class="carousel-btn right-4" 
        @click="next"
      >
        <svg class="w-4 h-4" viewBox="0 0 24 24" fill="none" stroke="currentColor">
          <path d="M9 5l7 7-7 7" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
        </svg>
      </button>
    </Transition>

    <!-- 底部指示器 -->
    <div 
      v-if="images.length > 1"
      class="absolute bottom-4 left-1/2 -translate-x-1/2 flex items-center gap-2"
    >
      <button 
        v-for="(_, index) in images" 
        :key="index"
        class="w-1.5 h-1.5 rounded-full transition-all duration-200"
        :class="index === currentIndex ? 'bg-paper scale-125' : 'bg-white/50 hover:bg-white/80'"
        @click="currentIndex = index"
      />
    </div>

    <!-- 图片预览 -->
    <ImagePreview
      v-model:visible="showPreview"
      :images="images"
      :initial-index="currentIndex"
    />
  </div>
</template>

<script setup>
import { ref, watch, onBeforeUnmount, onMounted } from 'vue'
import ImagePreview from './ImagePreview.vue'

const props = defineProps({
  images: {
    type: Array,
    required: true,
    default: () => []
  }
})

const currentIndex = ref(0)
const imageRefs = ref([])
const showControls = ref(false)
const showPreview = ref(false)
// 记录加载失败的图片下标
const failedIndexes = ref([])

const onImageError = (index) => {
  if (!failedIndexes.value.includes(index)) {
    failedIndexes.value = [...failedIndexes.value, index]
  }
}

// 添加滚动节流控制
let wheelTimeout = null
const WHEEL_DELAY = 200 // 200ms 的节流延迟

// 图片加载完成后的处理
const onImageLoad = (event) => {
  const img = event.target
  const container = img.parentElement.parentElement.parentElement
  const ratio = img.naturalWidth / img.naturalHeight
  const containerHeight = container.clientHeight
  const newWidth = containerHeight * ratio

  // 设置容器宽度为图片等比缩放后的宽度
  container.style.width = `${newWidth}px`
}

// 监听 images 变化，重置 currentIndex
watch(() => props.images, (newImages) => {
  failedIndexes.value = []
  if (newImages && newImages.length) {
    currentIndex.value = 0
  }
}, { immediate: true })

// 键盘事件处理
const handleKeydown = (e) => {
  if (!props.images.length || props.images.length <= 1) return
  
  switch (e.key) {
    case 'ArrowLeft':
    case 'PageUp':
      e.preventDefault()
      prev()
      break
    case 'ArrowRight':
    case 'PageDown':
      e.preventDefault()
      next()
      break
  }
}

// 组件挂载时添加键盘事件监听
onMounted(() => {
  window.addEventListener('keydown', handleKeydown)
})

// 组件卸载时移除键盘事件监听
onBeforeUnmount(() => {
  window.removeEventListener('keydown', handleKeydown)
  currentIndex.value = 0
  if (wheelTimeout) {
    clearTimeout(wheelTimeout)
  }
})

const next = () => {
  if (!props.images.length) return
  currentIndex.value = (currentIndex.value + 1) % props.images.length
}

const prev = () => {
  if (!props.images.length) return
  currentIndex.value = currentIndex.value === 0 
    ? props.images.length - 1 
    : currentIndex.value - 1
}

// 修改图片点击事件
const onImageClick = () => {
  showPreview.value = true
}

// 处理鼠标滚轮事件
const handleWheel = (e) => {
  e.preventDefault() // 阻止页面滚动
  
  // 如果已经有待执行的滚动，则忽略新的滚动
  if (wheelTimeout) return
  
  // 执行滚动并设置节流
  if (e.deltaY > 0) {
    next()
  } else {
    prev()
  }
  
  // 设置节流定时器
  wheelTimeout = setTimeout(() => {
    wheelTimeout = null
  }, WHEEL_DELAY)
}
</script>

<style scoped>
.carousel-fallback {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: var(--color-ink-faint);
}

.carousel-fallback svg {
  width: 56px;
  height: 56px;
  color: var(--color-line-strong);
}

.carousel-fallback__text {
  font-size: 13px;
}

.carousel-btn {
  @apply absolute top-1/2;
  transform: translateY(-50%);
  width: 30px;
  height: 30px;
  border-radius: 9999px;
  background-color: rgba(0, 0, 0, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  transition: all 0.2s;
}

.carousel-btn:hover {
  background-color: rgba(0, 0, 0, 0.4);
}

/* 添加计数器的动画效果 */
.carousel-counter-enter-active,
.carousel-counter-leave-active {
  transition: opacity 0.2s ease;
}

.carousel-counter-enter-from,
.carousel-counter-leave-to {
  opacity: 0;
}

/* 添加图片容器过渡效果 */
.carousel-container {
  transition: width 0.3s ease-in-out;
}

/* 添加淡入淡出动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style> 

<template>
  <div ref="rootRef" class="waterfall">
    <!-- 首屏用骨架屏，不用转圈 -->
    <div
      v-if="loading"
      class="waterfall__skeletons"
      :style="{ gridTemplateColumns: `repeat(${columnCount}, minmax(0, 1fr))` }"
    >
      <NoteCardSkeleton v-for="item in skeletonCount" :key="item" />
    </div>

    <template v-else>
      <div
        class="waterfall__grid"
        :style="{ height: `${layout.height}px` }"
      >
        <!-- 同一父节点下保持 ID key，重排时保留卡片状态和键盘阅读顺序 -->
        <div
          v-for="(note, index) in notes"
          :key="noteKey(note)"
          :ref="(element) => setItemRef(noteKey(note), element)"
          class="waterfall__item"
          :style="{
            width: `${columnWidth}px`,
            left: `${layout.positions[index].left}px`,
            top: `${layout.positions[index].top}px`
          }"
        >
          <NoteCard
            :note="note"
            :owner-actions="ownerActions"
            @click="$emit('note-click', note)"
            @edit="$emit('edit', $event)"
            @top="$emit('top', $event)"
            @visible="$emit('visible', $event)"
            @delete="$emit('delete', $event)"
          />
        </div>
      </div>

      <div v-if="loadingMore" class="waterfall__loading">
        <span class="st-spinner"></span>
        <span>正在加载…</span>
      </div>

      <!-- 触底哨兵：进入视口即拉取下一页（内容不足一屏时也能自动续接） -->
      <div v-if="hasMore" ref="sentinelRef" class="waterfall__sentinel" aria-hidden="true"></div>
    </template>

    <!-- 底线提示 -->
    <div v-if="reachedEnd" class="waterfall__end">
      <span class="waterfall__rule"></span>
      <span class="waterfall__text">书亭是有底线的</span>
      <span class="waterfall__rule"></span>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, shallowRef, watch } from 'vue'
import NoteCard from '@/components/note/NoteCard.vue'
import NoteCardSkeleton from '@/components/note/NoteCardSkeleton.vue'
import { createWaterfallLayout, useWaterfallColumns } from '@/composables/useWaterfallColumns'

const props = defineProps({
  notes: {
    type: Array,
    default: () => []
  },
  // 首屏加载：显示骨架屏
  loading: {
    type: Boolean,
    default: false
  },
  // 追加加载：显示底部加载行
  loadingMore: {
    type: Boolean,
    default: false
  },
  hasMore: {
    type: Boolean,
    default: false
  },
  skeletonCount: {
    type: Number,
    default: 10
  },
  showBottomLine: {
    type: Boolean,
    default: true
  },
  // 透传给卡片：是否渲染作者操作菜单
  ownerActions: {
    type: Boolean,
    default: false
  }
})

const emit = defineEmits(['note-click', 'load-more', 'edit', 'top', 'visible', 'delete'])

const rootRef = ref(null)
const sentinelRef = ref(null)
let observer = null

const { columnCount, containerWidth } = useWaterfallColumns(rootRef)
const columnGap = ref(16)
const rowGap = 24
const columnWidth = computed(() => Math.max(0,
  (containerWidth.value - columnGap.value * (columnCount.value - 1)) / columnCount.value
))
const noteKey = (note) => note.id ?? note.noteId
const itemRefs = new Map()
const cardSizes = shallowRef(new Map())
let resizeObserver = null
let measureFrame = null

// 图片尚未加载或列宽改变时先按 3:4 封面预估，真实尺寸到位后再校正。
const layout = computed(() => createWaterfallLayout(
  props.notes.map((note) => {
    const size = cardSizes.value.get(noteKey(note))
    return size && Math.abs(size.width - columnWidth.value) < 1
      ? size.height
      : columnWidth.value * 4 / 3 + 90
  }),
  columnCount.value,
  columnWidth.value,
  columnGap.value,
  rowGap
))

// 一帧内批量读卡片尺寸，避免每张封面加载时反复触发布局读写。
const scheduleMeasure = () => {
  if (measureFrame !== null) return
  measureFrame = requestAnimationFrame(() => {
    measureFrame = null
    const sizes = new Map()
    itemRefs.forEach((element, key) => {
      const { width, height } = element.getBoundingClientRect()
      sizes.set(key, { width, height })
    })
    cardSizes.value = sizes
  })
}

const setItemRef = (key, element) => {
  const previous = itemRefs.get(key)
  if (previous === element) return
  if (previous) resizeObserver?.unobserve(previous)
  if (element) {
    itemRefs.set(key, element)
    resizeObserver?.observe(element)
  } else {
    itemRefs.delete(key)
  }
}

watch(
  [() => props.notes, () => props.notes.length, columnWidth, () => props.loading],
  () => nextTick(scheduleMeasure),
  { flush: 'post' }
)

// 重新观察哨兵：IntersectionObserver 只在交叉状态变化时回调，
// 每次数据追加后重新 observe 一次，才能连续加载到填满视口。
const rearm = () => {
  if (!observer) return
  observer.disconnect()
  if (sentinelRef.value) observer.observe(sentinelRef.value)
}

onMounted(() => {
  columnGap.value = parseFloat(getComputedStyle(rootRef.value).getPropertyValue('--grid-gap')) || 16
  resizeObserver = new ResizeObserver(scheduleMeasure)
  itemRefs.forEach((element) => resizeObserver.observe(element))
  scheduleMeasure()
  observer = new IntersectionObserver(
    (entries) => {
      if (!entries.some((entry) => entry.isIntersecting)) return
      if (!props.hasMore || props.loading || props.loadingMore) return
      emit('load-more')
    },
    { rootMargin: '240px 0px' }
  )
  rearm()
})

watch(
  [sentinelRef, () => props.notes.length, () => props.hasMore, () => props.loading, () => props.loadingMore, () => layout.value.height],
  () => nextTick(rearm),
  { flush: 'post' }
)

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
  resizeObserver?.disconnect()
  if (measureFrame !== null) cancelAnimationFrame(measureFrame)
})

const reachedEnd = computed(
  () => props.showBottomLine && !props.loading && !props.hasMore && props.notes.length > 0
)

</script>

<style scoped>
.waterfall {
  width: 100%;
}

.waterfall__grid,
.waterfall__skeletons {
  width: 100%;
  position: relative;
  z-index: 1;
}

.waterfall__skeletons {
  display: grid;
  column-gap: var(--grid-gap);
  row-gap: 24px;
}

.waterfall__grid {
  animation: fadeIn var(--motion-base) var(--ease-standard);
}

.waterfall__item {
  position: absolute;
  min-width: 0;
}

.waterfall__item:has(.note-card--menu-open) {
  z-index: 5;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.waterfall__loading {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 28px 0 8px;
  font-size: 13px;
  color: var(--color-ink-faint);
}

.waterfall__end {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 28px 0 48px;
  color: var(--color-ink-faint);
}

.waterfall__sentinel {
  width: 100%;
  height: 1px;
}

.waterfall__rule {
  width: 64px;
  height: 1px;
  background: var(--color-line-strong);
}

.waterfall__text {
  font-size: 13px;
  letter-spacing: 0.02em;
}
</style>

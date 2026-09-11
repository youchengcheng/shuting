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
        :style="{ gridTemplateColumns: `repeat(${columnCount}, minmax(0, 1fr))` }"
      >
        <div v-for="(column, index) in columns" :key="index" class="waterfall__column">
          <NoteCard
            v-for="note in column"
            :key="noteKey(note)"
            :note="note"
            @click="$emit('note-click', note)"
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
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import NoteCard from '@/components/note/NoteCard.vue'
import NoteCardSkeleton from '@/components/note/NoteCardSkeleton.vue'
import { splitIntoColumns, useWaterfallColumns } from '@/composables/useWaterfallColumns'

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
  }
})

const emit = defineEmits(['note-click', 'load-more'])

const rootRef = ref(null)
const sentinelRef = ref(null)
let observer = null

const { columnCount } = useWaterfallColumns(rootRef)

// 重新观察哨兵：IntersectionObserver 只在交叉状态变化时回调，
// 每次数据追加后重新 observe 一次，才能连续加载到填满视口。
const rearm = () => {
  if (!observer) return
  observer.disconnect()
  if (sentinelRef.value) observer.observe(sentinelRef.value)
}

onMounted(() => {
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
  [sentinelRef, () => props.notes.length, () => props.hasMore, () => props.loading, () => props.loadingMore],
  () => nextTick(rearm),
  { flush: 'post' }
)

onBeforeUnmount(() => {
  if (observer) observer.disconnect()
})

const columns = computed(() => splitIntoColumns(props.notes, columnCount.value))
const reachedEnd = computed(
  () => props.showBottomLine && !props.loading && !props.hasMore && props.notes.length > 0
)

const noteKey = (note) => note.id ?? note.noteId
</script>

<style scoped>
.waterfall {
  width: 100%;
}

.waterfall__grid,
.waterfall__skeletons {
  display: grid;
  column-gap: var(--grid-gap);
  row-gap: 24px;
  width: 100%;
  position: relative;
  z-index: 1;
}

.waterfall__grid {
  animation: fadeIn var(--motion-base) var(--ease-standard);
}

.waterfall__column {
  display: flex;
  flex-direction: column;
  gap: 24px;
  min-width: 0;
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

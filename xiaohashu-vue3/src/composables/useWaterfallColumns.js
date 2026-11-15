import { onBeforeUnmount, onMounted, ref } from 'vue'

/**
 * 瀑布流列数按「内容区宽度」而不是视口宽度计算：
 * 左侧导航占位后，视口宽度会错判一列。
 * 阈值换算自视口宽度（左侧栏 200px + 内容区左右内边距 48px）：
 * 内容区 1180 → 5 列（约 1440px 视口以上）
 * 内容区 920  → 4 列（约 1200px 视口起）
 * 内容区 728  → 3 列（约 768px 视口起）
 * 其余 → 2 列（手机）
 */
const COLUMN_BREAKPOINTS = [
  { min: 1180, count: 5 },
  { min: 952, count: 4 },
  { min: 728, count: 3 },
  { min: 0, count: 2 }
]

export function resolveColumnCount(width, viewportWidth) {
  const viewport = viewportWidth ?? (typeof window !== 'undefined' ? window.innerWidth : width)
  // 手机端固定两列：<768px 时页面内边距同步收窄，内容区反而可能比 768px 更宽，
  // 仅用内容区宽度判断会把手机误判成三列。
  if (viewport < 768) return 2

  const matched = COLUMN_BREAKPOINTS.find((item) => width >= item.min)
  return matched ? matched.count : 2
}

/** 按列表顺序放入当前最短列；高度相同时优先左侧。 */
export function createWaterfallLayout(heights, columnCount, columnWidth, columnGap = 16, rowGap = 24) {
  const columnHeights = Array(columnCount).fill(0)
  const positions = heights.map((height) => {
    let column = 0
    for (let index = 1; index < columnCount; index++) {
      if (columnHeights[index] < columnHeights[column]) column = index
    }
    const top = columnHeights[column]
    columnHeights[column] += height + rowGap
    return { column, left: column * (columnWidth + columnGap), top }
  })
  return { positions, height: heights.length ? Math.max(...columnHeights) - rowGap : 0 }
}

export function useWaterfallColumns(targetRef) {
  const columnCount = ref(4)
  const containerWidth = ref(0)
  let observer = null

  const measure = () => {
    const element = targetRef.value
    const width = element ? element.clientWidth : window.innerWidth
    if (!width) return
    containerWidth.value = width
    columnCount.value = resolveColumnCount(width, window.innerWidth)
  }

  onMounted(() => {
    measure()
    if (typeof ResizeObserver !== 'undefined' && targetRef.value) {
      observer = new ResizeObserver(measure)
      observer.observe(targetRef.value)
    }
    window.addEventListener('resize', measure)
  })

  onBeforeUnmount(() => {
    if (observer) observer.disconnect()
    window.removeEventListener('resize', measure)
  })

  return { columnCount, containerWidth, measure }
}

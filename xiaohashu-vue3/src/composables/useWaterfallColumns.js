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

/** 按 DOM 顺序依次落到各列，保证阅读顺序从左到右 */
export function splitIntoColumns(items, columnCount) {
  const columns = Array.from({ length: columnCount }, () => [])
  items.forEach((item, index) => {
    columns[index % columnCount].push(item)
  })
  return columns
}

export function useWaterfallColumns(targetRef) {
  const columnCount = ref(4)
  let observer = null

  const measure = () => {
    const element = targetRef.value
    const width = element ? element.clientWidth : window.innerWidth
    if (!width) return
    columnCount.value = resolveColumnCount(width, window.innerWidth)
  }

  onMounted(() => {
    measure()
    if (typeof ResizeObserver !== 'undefined' && targetRef.value) {
      observer = new ResizeObserver(measure)
      observer.observe(targetRef.value)
    } else {
      window.addEventListener('resize', measure)
    }
  })

  onBeforeUnmount(() => {
    if (observer) observer.disconnect()
    window.removeEventListener('resize', measure)
  })

  return { columnCount, measure }
}

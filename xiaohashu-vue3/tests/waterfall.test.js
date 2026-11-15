import test from 'node:test'
import assert from 'node:assert/strict'
import { createWaterfallLayout, resolveColumnCount } from '../src/composables/useWaterfallColumns.js'

test('portrait cards are spread across columns instead of accumulating on one side', () => {
  const heights = [600, 100, 100, 100, 100, 600, 100, 100, 100, 100, 600, 100, 100, 100, 100]
  const layout = createWaterfallLayout(heights, 5, 220)
  const bottoms = Array(5).fill(0)
  layout.positions.forEach((position, index) => {
    bottoms[position.column] = position.top + heights[index]
  })

  // The previous round-robin layout had a 1,500px difference for this feed.
  assert.deepEqual([0, 5, 10].map(index => layout.positions[index].column), [0, 1, 3])
  assert.ok(Math.max(...bottoms) - Math.min(...bottoms) <= 600 + 24)
  assert.equal(layout.height, 848)
})

test('each next card fills the shortest column and equal heights start from the left', () => {
  const layout = createWaterfallLayout([500, 100, 200, 90, 180, 100], 3, 200)
  assert.deepEqual(layout.positions, [
    { column: 0, left: 0, top: 0 },
    { column: 1, left: 216, top: 0 },
    { column: 2, left: 432, top: 0 },
    { column: 1, left: 216, top: 124 },
    { column: 2, left: 432, top: 224 },
    { column: 1, left: 216, top: 238 }
  ])
  assert.equal(layout.height, 500)
})

test('appending a page preserves existing positions and fills the existing short columns', () => {
  const firstPage = [500, 100, 200]
  const before = createWaterfallLayout(firstPage, 3, 200)
  const after = createWaterfallLayout([...firstPage, 90, 180, 100], 3, 200)
  assert.deepEqual(after.positions.slice(0, firstPage.length), before.positions)
  assert.deepEqual(after.positions.slice(firstPage.length).map(position => position.column), [1, 2, 1])
  assert.equal(after.positions[firstPage.length].top, 124)
})

test('newly measured media heights rebalance cards that used placeholder heights', () => {
  const estimated = createWaterfallLayout([300, 300, 300, 300, 300, 300], 3, 200)
  const measured = createWaterfallLayout([600, 100, 100, 100, 100, 100], 3, 200)
  assert.deepEqual(estimated.positions.map(position => position.column), [0, 1, 2, 0, 1, 2])
  assert.deepEqual(measured.positions.map(position => position.column), [0, 1, 2, 1, 2, 1])
  assert.equal(measured.positions[3].top, 124)
  assert.equal(measured.height, 600)
})

test('cards never overlap and the requested gaps are respected across two to five columns', () => {
  const heights = [360, 120, 480, 210, 90, 340, 160, 600, 130, 280, 190, 420]
  for (const columnCount of [2, 3, 4, 5]) {
    const layout = createWaterfallLayout(heights, columnCount, 180, 20, 30)
    const previousBottoms = Array(columnCount).fill(null)
    layout.positions.forEach((position, index) => {
      assert.equal(position.left, position.column * 200)
      const previousBottom = previousBottoms[position.column]
      if (previousBottom !== null) assert.equal(position.top - previousBottom, 30)
      previousBottoms[position.column] = position.top + heights[index]
    })
    assert.equal(layout.height, Math.max(...previousBottoms))
  }
})

test('container height ends at the tallest card bottom without a trailing row gap', () => {
  assert.equal(createWaterfallLayout([100, 140, 200], 2, 200).height, 324)
  assert.equal(createWaterfallLayout([100], 5, 200).height, 100)
  assert.deepEqual(createWaterfallLayout([], 5, 200), { positions: [], height: 0 })
})

test('responsive column counts follow the content width while phones stay at two columns', () => {
  const sizes = [
    { width: 600, viewport: 900, count: 2 },
    { width: 728, viewport: 1024, count: 3 },
    { width: 952, viewport: 1280, count: 4 },
    { width: 1180, viewport: 1440, count: 5 },
    { width: 800, viewport: 767, count: 2 }
  ]
  for (const { width, viewport, count } of sizes) {
    assert.equal(resolveColumnCount(width, viewport), count)
    const columnWidth = (width - (count - 1) * 16) / count
    const layout = createWaterfallLayout(Array(10).fill(150), count, columnWidth)
    assert.equal(layout.positions[count - 1].left + columnWidth, width)
    assert.ok(layout.positions.every(position => position.column < count))
  }
})

const commentTypes = new Set(['COMMENT_LIKE', 'COMMENT', 'REPLY'])
const shorten = (value, fallback) => {
  const text = String(value || '').replace(/\s+/g, ' ').trim() || fallback
  const characters = Array.from(text)
  return characters.length > 32 ? `${characters.slice(0, 32).join('')}…` : characters.join('')
}

export function notificationPreview(item) {
  const empty = { text: '', imageUrl: '', cover: '' }
  if (item.type === 'FOLLOW') return empty
  if (item.contentStatus === 'UNAVAILABLE') return { ...empty, text: '内容已不可查看' }
  if (item.contentStatus === 'TEMPORARY_UNAVAILABLE') return { ...empty, text: '内容暂时无法加载' }
  const preview = item.preview || {}
  if (commentTypes.has(item.type)) {
    const imageUrl = preview.imageUrl || ''
    return { text: imageUrl ? '' : shorten(preview.summary, '查看评论'), imageUrl, cover: preview.cover || '' }
  }
  return { text: shorten(preview.title || preview.summary, '查看笔记'), imageUrl: '', cover: preview.cover || '' }
}

export function createSseParser(onEvent) {
  let buffer = ''
  let event = ''
  let data = []
  let first = true
  const line = (value) => {
    if (!value) {
      if (data.length) onEvent({ event: event || 'message', data: data.join('\n') })
      event = ''
      data = []
      return
    }
    if (value[0] === ':') return
    const separator = value.indexOf(':')
    const name = separator < 0 ? value : value.slice(0, separator)
    let content = separator < 0 ? '' : value.slice(separator + 1)
    if (content.startsWith(' ')) content = content.slice(1)
    if (name === 'event') event = content
    if (name === 'data') data.push(content)
  }
  return (chunk) => {
    if (first && chunk) { chunk = chunk.replace(/^\uFEFF/, ''); first = false }
    buffer += chunk
    let index = 0
    while (index < buffer.length) {
      const end = buffer.slice(index).search(/[\r\n]/)
      if (end < 0) break
      const at = index + end
      if (buffer[at] === '\r' && at + 1 === buffer.length) break
      line(buffer.slice(index, at))
      index = at + (buffer[at] === '\r' && buffer[at + 1] === '\n' ? 2 : 1)
    }
    buffer = buffer.slice(index)
    if (buffer.length > 1024 * 1024) throw new Error('SSE frame is too large')
  }
}

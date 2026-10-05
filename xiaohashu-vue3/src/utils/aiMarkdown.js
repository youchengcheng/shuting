/**
 * AI 回答的轻量 Markdown 渲染。
 *
 * 只覆盖模型最常用的语法（标题 / 列表 / 引用 / 代码块 / 加粗 / 行内代码 / 链接），
 * 做法是「先整体转义、再做标记替换」，渲染结果里不可能带出可执行脚本，
 * 所以不用为了一个回答气泡再引入 marked + DOMPurify 这类依赖。
 */

const escapeHtml = (text) =>
  String(text ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')

// 行内标记：行内代码 -> 加粗 -> 链接。先转义再替换，顺序不能反
const renderInline = (text) => {
  let out = escapeHtml(text)
  out = out.replace(/`([^`]+)`/g, '<code>$1</code>')
  out = out.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
  out = out.replace(
    /\[([^\]]+)\]\((https?:\/\/[^)\s]+)\)/g,
    '<a href="$2" target="_blank" rel="noopener noreferrer">$1</a>'
  )
  return out
}

export function renderAiMarkdown(markdown) {
  const lines = String(markdown ?? '').replace(/\r\n?/g, '\n').split('\n')
  const html = []
  let listTag = null
  let paragraph = []

  const flushParagraph = () => {
    if (!paragraph.length) return
    html.push(`<p>${paragraph.map(renderInline).join('<br />')}</p>`)
    paragraph = []
  }

  const closeList = () => {
    if (!listTag) return
    html.push(`</${listTag}>`)
    listTag = null
  }

  const flushAll = () => {
    flushParagraph()
    closeList()
  }

  for (let i = 0; i < lines.length; i += 1) {
    const line = lines[i]

    // 代码块：内部原样输出，不再做行内解析
    if (/^\s*```/.test(line)) {
      flushAll()
      const lang = line.replace(/^\s*```/, '').trim()
      const buffer = []
      i += 1
      while (i < lines.length && !/^\s*```/.test(lines[i])) {
        buffer.push(lines[i])
        i += 1
      }
      const cls = lang ? ` class="language-${escapeHtml(lang)}"` : ''
      html.push(`<pre><code${cls}>${escapeHtml(buffer.join('\n'))}</code></pre>`)
      continue
    }

    if (!line.trim()) {
      flushAll()
      continue
    }

    if (/^\s*(-{3,}|\*{3,}|_{3,})\s*$/.test(line)) {
      flushAll()
      html.push('<hr />')
      continue
    }

    const heading = /^(#{1,6})\s+(.*)$/.exec(line)
    if (heading) {
      flushAll()
      const level = Math.min(heading[1].length + 2, 6)
      html.push(`<h${level}>${renderInline(heading[2].trim())}</h${level}>`)
      continue
    }

    const quote = /^\s*>\s?(.*)$/.exec(line)
    if (quote) {
      flushAll()
      html.push(`<blockquote>${renderInline(quote[1])}</blockquote>`)
      continue
    }

    const unordered = /^\s*[-*+]\s+(.*)$/.exec(line)
    if (unordered) {
      flushParagraph()
      if (listTag !== 'ul') {
        closeList()
        html.push('<ul>')
        listTag = 'ul'
      }
      html.push(`<li>${renderInline(unordered[1])}</li>`)
      continue
    }

    const ordered = /^\s*\d+[.)]\s+(.*)$/.exec(line)
    if (ordered) {
      flushParagraph()
      if (listTag !== 'ol') {
        closeList()
        html.push('<ol>')
        listTag = 'ol'
      }
      html.push(`<li>${renderInline(ordered[1])}</li>`)
      continue
    }

    closeList()
    paragraph.push(line.trim())
  }

  flushAll()
  return html.join('\n')
}
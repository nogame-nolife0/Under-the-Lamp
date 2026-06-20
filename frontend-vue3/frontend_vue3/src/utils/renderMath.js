import katex from 'katex'
import { normalizeLatexDelimiters } from '@/utils/latexNormalize'

const IMAGE_MARKER_RE = /\[嵌入图片(\d+)\]/g
const MATH_SEGMENT_RE = /(\$\$[\s\S]+?\$\$|\$[^$\n]+?\$)/g
const LATEX_AFTER_MARKER_RE = /(\[嵌入图片\d+\])(?:\s*\$[^$\n]+?\$)+/g
const LATEX_BETWEEN_MARKERS_RE = /(\[嵌入图片\d+\])(?:\s*\$[^$\n]+?\$)+\s*(?=\[嵌入图片\d+\])/g

/** 去掉紧邻图片占位符的重复 LaTeX，保留问句尾部等独立公式 */
export function prepareRichContent(raw) {
  if (!raw || !IMAGE_MARKER_RE.test(raw)) {
    return raw || ''
  }
  IMAGE_MARKER_RE.lastIndex = 0
  let cleaned = raw.replace(LATEX_BETWEEN_MARKERS_RE, '$1')
  cleaned = cleaned.replace(LATEX_AFTER_MARKER_RE, '$1')
  return cleaned
}

export function parseRichBlocks(raw) {
  const content = prepareRichContent(raw)
  if (!content) return []

  const blocks = []
  let lastIndex = 0
  let match

  IMAGE_MARKER_RE.lastIndex = 0
  while ((match = IMAGE_MARKER_RE.exec(content)) !== null) {
    if (match.index > lastIndex) {
      blocks.push({ type: 'text', content: content.slice(lastIndex, match.index) })
    }
    blocks.push({ type: 'image', index: Number(match[1]) })
    lastIndex = IMAGE_MARKER_RE.lastIndex
  }

  if (lastIndex < content.length) {
    blocks.push({ type: 'text', content: content.slice(lastIndex) })
  }

  if (blocks.length === 0) {
    blocks.push({ type: 'text', content })
  }

  return blocks
}

function escapeHtml(text) {
  return text
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
}

function renderKatex(expr, displayMode) {
  try {
    return katex.renderToString(expr.trim(), {
      throwOnError: false,
      displayMode,
      strict: 'ignore',
    })
  } catch {
    return escapeHtml(expr)
  }
}

/**
 * 将含 $...$ / $$...$$ 的文本转为 HTML（含 KaTeX 渲染）。
 */
export function renderMathHtml(text) {
  if (!text) return ''

  const normalized = normalizeLatexDelimiters(text)
  const parts = normalized.split(/(\$\$[\s\S]+?\$\$|\$[^$\n]+?\$)/g)
  return parts
    .map((part) => {
      if (!part) return ''
      if (part.startsWith('$$') && part.endsWith('$$')) {
        return `<div class="math-block">${renderKatex(part.slice(2, -2), true)}</div>`
      }
      if (part.startsWith('$') && part.endsWith('$')) {
        return `<span class="math-inline">${renderKatex(part.slice(1, -1), false)}</span>`
      }
      return `<span class="text-part">${escapeHtml(part).replace(/\n/g, '<br/>')}</span>`
    })
    .join('')
}

export function isImageErrorText(text) {
  if (!text) return false
  return (
    text.includes('图片识别失败') ||
    text.includes('超出视觉识别数量上限') ||
    text.includes('未识别到任何可读取') ||
    text.includes('完全空白')
  )
}

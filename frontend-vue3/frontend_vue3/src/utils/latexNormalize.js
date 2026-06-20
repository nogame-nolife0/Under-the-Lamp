const MATH_SPLIT_RE = /(\$\$[\s\S]+?\$\$|\$[^$\n]+?\$)/g

const PAREN_INLINE_RE = /\\\((.+?)\\\)/g
const PAREN_BLOCK_RE = /\\\[(.+?)\\\]/g

const LATEX_CMD_RE =
  /\\(?:overline|underline|frac|sqrt|cdot|times|bar|vec|hat|tilde|text|mathrm|mathbf|left|right|quad|pm|mp|leq|geq|neq|approx|sum|prod|int|alpha|beta|gamma|delta|pi|theta|lambda|mu|sigma|omega)\b/

const VAR_EQ_RE = /[A-Za-z]\s*=\s*(?=\\)/

export function normalizeLatexDelimiters(text) {
  if (!text || (!text.includes('\\') && !text.includes('$'))) return text

  let normalized = text.replace(PAREN_INLINE_RE, (_, expr) => `$${expr}$`)
  normalized = normalized.replace(PAREN_BLOCK_RE, (_, expr) => `$$${expr}$$`)
  const parts = normalized.split(MATH_SPLIT_RE)
  return parts
    .map((part, idx) => {
      if (!part) return ''
      if (idx % 2 === 1) return part
      return wrapBareLatexInPlain(part)
    })
    .join('')
}

function wrapBareLatexInPlain(text) {
  if (!text.includes('\\')) return text

  const result = []
  let i = 0
  const n = text.length

  while (i < n) {
    const start = findLatexStart(text, i)
    if (start == null) {
      result.push(text[i])
      i += 1
      continue
    }
    result.push(text.slice(i, start))
    const end = scanLatexEnd(text, start)
    result.push('$', text.slice(start, end), '$')
    i = end
  }
  result.push(text.slice(i))
  return result.join('')
}

function findLatexStart(text, pos) {
  for (let i = pos; i < text.length; i += 1) {
    if (text[i] === '\\') {
      LATEX_CMD_RE.lastIndex = i
      if (LATEX_CMD_RE.test(text)) {
        const prefix = text.slice(pos, i).match(/[A-Za-z]\s*=\s*$/)
        return prefix ? pos + prefix.index : i
      }
    }
    const eq = text.slice(i).match(VAR_EQ_RE)
    if (eq) {
      LATEX_CMD_RE.lastIndex = i + eq[0].length
      if (LATEX_CMD_RE.test(text)) return i
    }
  }
  return null
}

function scanLatexEnd(text, start) {
  let i = start
  while (i < text.length) {
    if (text[i] === '\\') {
      LATEX_CMD_RE.lastIndex = i
      const cmd = LATEX_CMD_RE.exec(text)
      if (!cmd) break
      i = cmd.index + cmd[0].length
      while (i < text.length && text[i] === ' ') i += 1
      if (i < text.length && text[i] === '{') i = skipBraceGroup(text, i) + 1
      continue
    }

    if ('_{^'.includes(text[i])) {
      i += 1
      if (i < text.length && text[i] === '{') i = skipBraceGroup(text, i) + 1
      else if (i < text.length) i += 1
      continue
    }

    if ('+-=·()[]'.includes(text[i])) {
      i += 1
      continue
    }

    if (/\s/.test(text[i])) {
      let j = i + 1
      while (j < text.length && /\s/.test(text[j])) j += 1
      if (j < text.length && (text[j] === '\\' || '+-=·'.includes(text[j]))) {
        i = j
        continue
      }
      break
    }

    if (/[A-Za-z0-9,.]/.test(text[i])) {
      i += 1
      continue
    }

    if (text.charCodeAt(i) >= 0x4e00 && text.charCodeAt(i) <= 0x9fff) break
    break
  }
  return i
}

function skipBraceGroup(text, pos) {
  if (pos >= text.length || text[pos] !== '{') return pos
  let depth = 0
  let i = pos
  while (i < text.length) {
    if (text[i] === '{') depth += 1
    else if (text[i] === '}') {
      depth -= 1
      if (depth === 0) return i
    }
    i += 1
  }
  return pos
}

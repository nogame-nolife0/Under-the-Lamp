function scoreImportText(text) {
  const value = (text || '').trim()
  if (!value) return 0
  const markers = (value.match(/\[嵌入图片\d+\]/g) || []).length
  const han = (value.match(/[\u4e00-\u9fff]/g) || []).length
  return markers * 10000 + han * 100 + value.length
}

/** 在 stemRaw / stemHtml 等候选中选取更完整的导入题干 */
export function pickRicherImportText(...texts) {
  let best = ''
  let bestScore = 0
  for (const text of texts) {
    const value = (text || '').trim()
    if (!value) continue
    const score = scoreImportText(value)
    if (score > bestScore) {
      best = value
      bestScore = score
    }
  }
  return best
}

export function resolveImportPreviewText(html, raw, status) {
  const htmlText = (html || '').trim()
  const rawText = (raw || '').trim()
  if (status === 'EDITED' && htmlText) return htmlText
  return pickRicherImportText(rawText, htmlText)
}

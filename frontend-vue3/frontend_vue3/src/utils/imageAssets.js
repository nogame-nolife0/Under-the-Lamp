export function resolveQuestionImages(item) {
  if (!item) return []
  if (Array.isArray(item.images) && item.images.length) return item.images
  const raw = item.imagesJson
  if (!raw) return []
  if (Array.isArray(raw)) return raw
  if (typeof raw === 'string') {
    try {
      const parsed = JSON.parse(raw)
      return Array.isArray(parsed) ? parsed : []
    } catch {
      return []
    }
  }
  return []
}

export function buildImageMap(images, scope, options = {}) {
  const map = {}
  const normalizedScope = scope ? String(scope).toUpperCase() : null
  if (images?.length) {
    for (const img of images) {
      const imgScope = String(img.scope || 'STEM').toUpperCase()
      if (normalizedScope && imgScope !== normalizedScope) continue
      if (img.index != null && img.url) {
        map[img.index] = img.url
      }
    }
  }

  const batchUuid = options.batchUuid
  const questionId = options.questionId
  const content = options.content || ''
  const markerRe = /\[嵌入图片(\d+)\]/g
  if (content && (batchUuid || questionId)) {
    let match
    while ((match = markerRe.exec(content)) !== null) {
      const index = Number(match[1])
      if (!map[index]) {
        const scopeSegment = normalizedScope || 'STEM'
        if (questionId) {
          map[index] = `/api/assets/questions/${questionId}/images/${scopeSegment}/${index}`
        } else if (batchUuid) {
          map[index] = `/api/assets/batches/${batchUuid}/images/${scopeSegment}/${index}`
        }
      }
    }
  }
  return map
}

export function buildImageMapForQuestion(question, scope) {
  const images = resolveQuestionImages(question)
  const content = [
    question?.stem,
    question?.answer,
    question?.analysis,
    ...(question?.options || []),
  ]
    .filter(Boolean)
    .join('\n')
  return buildImageMap(images, scope, {
    questionId: question?.id || question?.questionId,
    content,
  })
}

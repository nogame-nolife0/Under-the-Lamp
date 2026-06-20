/** 分值展示与累加，避免 100.02000000000001 这类浮点误差 */
export function roundScore(value, digits = 2) {
  const n = Number(value)
  if (!Number.isFinite(n)) return 0
  const factor = 10 ** digits
  return Math.round(n * factor) / factor
}

export function formatScore(value) {
  const n = roundScore(value)
  if (!Number.isFinite(n)) return '-'
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

export function sumScores(items, getScore = (item) => item?.score) {
  const total = items.reduce((sum, item) => sum + Number(getScore(item) || 0), 0)
  return roundScore(total)
}

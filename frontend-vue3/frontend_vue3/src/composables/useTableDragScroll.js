import { nextTick, onMounted, onUnmounted, watch } from 'vue'

const BLOCK_SELECTOR =
  'a, button, input, textarea, select, label, .el-checkbox, .el-checkbox__inner, .el-button, .el-input, .el-select, .el-switch, .el-radio'

const PAGE_SCROLL_SELECTOR = '.page-scroll, .page-wrapper.page-scroll, .page-scroll.page-wrapper'

function resolveTableEl(tableRef) {
  const ref = tableRef.value
  return ref?.$el ?? ref ?? null
}

function getBodyWrap(tableEl) {
  return (
    tableEl.querySelector('.el-table__body-wrapper .el-scrollbar__wrap')
    || tableEl.querySelector('.el-table__body-wrapper')
  )
}

function getTextRects(container) {
  const range = document.createRange()
  const rects = []
  const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, {
    acceptNode(node) {
      return node.textContent?.trim() ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT
    },
  })

  let node = walker.nextNode()
  while (node) {
    range.selectNodeContents(node)
    rects.push(...range.getClientRects())
    node = walker.nextNode()
  }

  return rects
}

function isPointOnText(x, y, container) {
  return getTextRects(container).some(
    (rect) => x >= rect.left && x <= rect.right && y >= rect.top && y <= rect.bottom,
  )
}

function isClickOnBlankCell(event) {
  if (event.button !== 0) return false
  if (event.target.closest(BLOCK_SELECTOR)) return false

  const cell = event.target.closest('td.el-table__cell')
  if (!cell) return false
  if (event.target.closest('.el-checkbox')) return false

  const inner = cell.querySelector('.cell') ?? cell
  if (!inner.textContent?.trim()) return true

  return !isPointOnText(event.clientX, event.clientY, inner)
}

/**
 * 表格空白区域按住拖动即可上下左右滚动（无需滚轮或拖滚动条）。
 */
export function useTableDragScroll(tableRef, options = {}) {
  const enabled = options.enabled ?? true
  let cleanup = null

  function setup() {
    cleanup?.()
    if (!enabled) return

    const tableEl = resolveTableEl(tableRef)
    if (!tableEl) return

    const bodyWrap = getBodyWrap(tableEl)
    const scrollHost = bodyWrap || tableEl
    const pageScroll = tableEl.closest(PAGE_SCROLL_SELECTOR)

    let dragging = false
    let moved = false
    let startX = 0
    let startY = 0
    let startScrollLeft = 0
    let startScrollTop = 0
    let startPageScrollTop = 0

    function onMouseDown(event) {
      if (!isClickOnBlankCell(event)) return

      dragging = true
      moved = false
      startX = event.clientX
      startY = event.clientY
      startScrollLeft = bodyWrap?.scrollLeft ?? 0
      startScrollTop = bodyWrap?.scrollTop ?? 0
      startPageScrollTop = pageScroll?.scrollTop ?? 0

      tableEl.classList.add('is-drag-scrolling')
      document.body.style.userSelect = 'none'
      event.preventDefault()
    }

    function onMouseMove(event) {
      if (!dragging) return

      const dx = event.clientX - startX
      const dy = event.clientY - startY
      if (Math.abs(dx) > 2 || Math.abs(dy) > 2) moved = true

      if (bodyWrap) {
        bodyWrap.scrollLeft = startScrollLeft - dx
        if (bodyWrap.scrollHeight > bodyWrap.clientHeight + 1) {
          bodyWrap.scrollTop = startScrollTop - dy
        }
      }

      if (pageScroll && pageScroll.scrollHeight > pageScroll.clientHeight + 1) {
        const tableCanScrollY = bodyWrap && bodyWrap.scrollHeight > bodyWrap.clientHeight + 1
        if (!tableCanScrollY) {
          pageScroll.scrollTop = startPageScrollTop - dy
        }
      }
    }

    function onHoverMove(event) {
      if (dragging) return
      scrollHost.style.cursor = isClickOnBlankCell({
        ...event,
        button: 0,
        target: event.target,
      })
        ? 'grab'
        : ''
    }

    function onHoverLeave() {
      if (!dragging) scrollHost.style.cursor = ''
    }

    function stopDragging() {
      if (!dragging) return
      dragging = false
      tableEl.classList.remove('is-drag-scrolling')
      document.body.style.userSelect = ''
    }

    function onClickCapture(event) {
      if (!moved) return
      event.preventDefault()
      event.stopPropagation()
      moved = false
    }

    scrollHost.addEventListener('mousedown', onMouseDown)
    scrollHost.addEventListener('mousemove', onHoverMove)
    scrollHost.addEventListener('mouseleave', onHoverLeave)
    document.addEventListener('mousemove', onMouseMove)
    document.addEventListener('mouseup', stopDragging)
    tableEl.addEventListener('click', onClickCapture, true)

    cleanup = () => {
      scrollHost.removeEventListener('mousedown', onMouseDown)
      scrollHost.removeEventListener('mousemove', onHoverMove)
      scrollHost.removeEventListener('mouseleave', onHoverLeave)
      document.removeEventListener('mousemove', onMouseMove)
      document.removeEventListener('mouseup', stopDragging)
      tableEl.removeEventListener('click', onClickCapture, true)
      scrollHost.style.cursor = ''
      stopDragging()
    }
  }

  onMounted(() => nextTick(setup))
  watch(() => tableRef.value, () => nextTick(setup))
  onUnmounted(() => cleanup?.())

  return { refresh: () => nextTick(setup) }
}

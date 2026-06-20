import { onUnmounted, watch } from 'vue'

const SCROLL_CONTAINER_SELECTOR = '.content-stage .page-wrapper.page-scroll, .content-stage .page-scroll.page-wrapper'
const SCROLLABLE_OVERFLOW = new Set(['auto', 'scroll', 'overlay'])

function isScrollableElement(el) {
  if (!(el instanceof HTMLElement)) return false
  const style = window.getComputedStyle(el)
  const canScrollY =
    SCROLLABLE_OVERFLOW.has(style.overflowY) && el.scrollHeight > el.clientHeight + 1
  const canScrollX =
    SCROLLABLE_OVERFLOW.has(style.overflowX) && el.scrollWidth > el.clientWidth + 1
  return canScrollY || canScrollX
}

function findScrollableAncestor(target, root) {
  let node = target
  while (node && node !== root) {
    if (isScrollableElement(node)) return node
    node = node.parentElement
  }
  return null
}

function canScrollInDirection(el, event) {
  const { scrollTop, scrollHeight, clientHeight, scrollLeft, scrollWidth, clientWidth } = el
  const style = window.getComputedStyle(el)

  const canScrollY =
    SCROLLABLE_OVERFLOW.has(style.overflowY) && scrollHeight > clientHeight + 1
  const canScrollX =
    SCROLLABLE_OVERFLOW.has(style.overflowX) && scrollWidth > clientWidth + 1

  if (canScrollY && event.deltaY !== 0) {
    if (event.deltaY < 0 && scrollTop > 0) return true
    if (event.deltaY > 0 && scrollTop + clientHeight < scrollHeight - 1) return true
  }

  if (canScrollX && event.deltaX !== 0) {
    if (event.deltaX < 0 && scrollLeft > 0) return true
    if (event.deltaX > 0 && scrollLeft + clientWidth < scrollWidth - 1) return true
  }

  return false
}

export function useMainScrollLock(visibleRef, options = {}) {
  const dialogRootSelector = options.dialogRootSelector || '.question-detail-dialog'

  let savedScrollTop = 0
  let wheelHandler = null

  function getScrollContainer() {
    return document.querySelector(SCROLL_CONTAINER_SELECTOR)
  }

  function shouldAllowDialogScroll(event) {
    const roots = document.querySelectorAll(dialogRootSelector)

    for (const root of roots) {
      if (!root.contains(event.target)) continue

      const scrollable = findScrollableAncestor(event.target, root)
      if (scrollable && canScrollInDirection(scrollable, event)) {
        return true
      }

      // 在弹窗内但已滚到边界或无内层滚动区：拦截，避免带动背后页面
      return false
    }

    return false
  }

  function lock() {
    const container = getScrollContainer()
    if (container) {
      savedScrollTop = container.scrollTop
      container.classList.add('main-scroll-locked')
    }

    document.body.classList.add('dialog-scroll-locked')

    wheelHandler = (event) => {
      if (shouldAllowDialogScroll(event)) return
      event.preventDefault()
    }
    document.addEventListener('wheel', wheelHandler, { passive: false, capture: true })
  }

  function unlock() {
    const container = getScrollContainer()
    if (container) {
      container.classList.remove('main-scroll-locked')
      container.scrollTop = savedScrollTop
    }

    document.body.classList.remove('dialog-scroll-locked')

    if (wheelHandler) {
      document.removeEventListener('wheel', wheelHandler, { capture: true })
      wheelHandler = null
    }
  }

  watch(visibleRef, (visible) => {
    if (visible) {
      lock()
    } else {
      unlock()
    }
  })

  onUnmounted(unlock)
}

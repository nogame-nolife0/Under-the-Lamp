import { defineStore } from 'pinia'
import { ref } from 'vue'

const HUE_KEY = 'pg_shade_hue'
const DEFAULT_HUE = 32

function normalizeHue(hue) {
  const n = Math.floor(Number(hue))
  if (!Number.isFinite(n)) return DEFAULT_HUE
  return ((n % 360) + 360) % 360
}

function readStoredHue() {
  return normalizeHue(localStorage.getItem(HUE_KEY))
}

export const useThemeStore = defineStore('theme', () => {
  const shadeHue = ref(readStoredHue())

  function applyToDocument(hue = shadeHue.value) {
    document.documentElement.style.setProperty('--shade-hue', String(hue))
  }

  function setShadeHue(hue) {
    const normalized = normalizeHue(hue)
    shadeHue.value = normalized
    localStorage.setItem(HUE_KEY, String(normalized))
    applyToDocument(normalized)
  }

  function init() {
    shadeHue.value = readStoredHue()
    applyToDocument()
  }

  return {
    shadeHue,
    setShadeHue,
    init,
  }
})

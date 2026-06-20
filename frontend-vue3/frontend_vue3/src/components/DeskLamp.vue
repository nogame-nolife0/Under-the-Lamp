<script setup>
import { ref, computed } from 'vue'

defineOptions({ name: 'DeskLamp' })

const props = defineProps({
  hue: {
    type: Number,
    default: 30,
  },
  on: {
    type: Boolean,
    default: true,
  },
})

const emit = defineEmits(['update:on', 'update:hue'])

const ANCHOR_X = 130
const ANCHOR_Y = 198
const DEFAULT_HANDLE_Y = 300
const TRIGGER_DIST = 50
const MAX_DX = 120
const MAX_DY_UP = 80
const MAX_DY_DOWN = 120

const handleX = ref(ANCHOR_X)
const handleY = ref(DEFAULT_HANDLE_Y)
const dragging = ref(false)
const triggered = ref(false)

const dist = computed(() => {
  const dx = handleX.value - ANCHOR_X
  const dy = handleY.value - ANCHOR_Y
  return Math.hypot(dx, dy)
})

function clamp(val, min, max) {
  return Math.max(min, Math.min(max, val))
}

function onPointerDown(event) {
  event.preventDefault()
  event.target.setPointerCapture(event.pointerId)
  dragging.value = true
}

function onPointerMove(event) {
  if (!dragging.value) return
  const svgEl = event.currentTarget.closest('svg')
  if (!svgEl) return
  const pt = svgEl.createSVGPoint()
  pt.x = event.clientX
  pt.y = event.clientY
  const svgPt = pt.matrixTransform(svgEl.getScreenCTM().inverse())

  handleX.value = clamp(svgPt.x, ANCHOR_X - MAX_DX, ANCHOR_X + MAX_DX)
  handleY.value = clamp(svgPt.y, ANCHOR_Y - MAX_DY_UP, ANCHOR_Y + MAX_DY_DOWN)

  triggered.value = dist.value >= TRIGGER_DIST
}

function onPointerUp(event) {
  if (!dragging.value) return
  dragging.value = false

  if (event) {
    event.target.releasePointerCapture(event.pointerId)
  }

  if (dist.value >= TRIGGER_DIST) {
    const newOn = !props.on
    emit('update:on', newOn)
    if (newOn) {
      const newHue = Math.floor(Math.random() * 360)
      emit('update:hue', newHue)
    }
    playClickSound()
  }

  // Spring back to origin
  handleX.value = ANCHOR_X
  handleY.value = DEFAULT_HANDLE_Y
  triggered.value = false
}

let audioCtx = null

function playClickSound() {
  try {
    if (!audioCtx) {
      audioCtx = new window.AudioContext()
    }
    if (audioCtx.state === 'suspended') {
      audioCtx.resume()
    }
    const osc = audioCtx.createOscillator()
    const gain = audioCtx.createGain()

    osc.type = 'square'
    osc.frequency.setValueAtTime(800, audioCtx.currentTime)
    osc.frequency.exponentialRampToValueAtTime(200, audioCtx.currentTime + 0.08)

    gain.gain.setValueAtTime(0.15, audioCtx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.001, audioCtx.currentTime + 0.1)

    osc.connect(gain)
    gain.connect(audioCtx.destination)

    osc.onended = () => {
      osc.disconnect()
      gain.disconnect()
    }

    osc.start(audioCtx.currentTime)
    osc.stop(audioCtx.currentTime + 0.1)
  } catch {
    // Silently handle AudioContext unavailability
  }
}
</script>

<template>
  <div class="desk-lamp">
    <svg viewBox="0 0 200 400" xmlns="http://www.w3.org/2000/svg">
      <defs>
        <radialGradient id="bgGlow" cx="50%" cy="35%">
          <stop offset="0%" :stop-color="`hsla(${hue},85%,60%,0.15)`" />
          <stop offset="100%" stop-color="transparent" />
        </radialGradient>
        <linearGradient id="lightCone" x1="50%" y1="0%" x2="50%" y2="100%">
          <stop offset="0%" :stop-color="`hsla(${hue},85%,65%,0.35)`" />
          <stop offset="40%" :stop-color="`hsla(${hue},85%,60%,0.12)`" />
          <stop offset="100%" :stop-color="`hsla(${hue},85%,55%,0)`" />
        </linearGradient>
        <radialGradient id="bulbGrad" cx="50%" cy="35%">
          <stop offset="0%" stop-color="#fff8ed" />
          <stop offset="30%" :stop-color="`hsl(${hue},85%,70%)`" />
          <stop offset="70%" :stop-color="`hsl(${hue},85%,55%)`" />
          <stop offset="100%" :stop-color="`hsl(${hue},70%,40%)`" />
        </radialGradient>
        <linearGradient id="shadeGrad" x1="0%" y1="0%" x2="0%" y2="100%">
          <stop offset="0%" stop-color="#f0ece8" />
          <stop offset="40%" stop-color="#e8e3de" />
          <stop offset="100%" stop-color="#d5cfc8" />
        </linearGradient>
        <filter id="bulbGlow">
          <feGaussianBlur stdDeviation="6" />
        </filter>
        <filter id="softGlow">
          <feGaussianBlur stdDeviation="10" />
        </filter>
      </defs>

      <!-- Background glow -->
      <circle cx="100" cy="160" r="140" fill="url(#bgGlow)" :opacity="on ? 1 : 0" />

      <!-- Light cone -->
      <polygon points="100,195 25,370 175,370" fill="url(#lightCone)" :opacity="on ? 1 : 0" />

      <!-- Desktop light spot -->
      <ellipse
        cx="100" cy="350" rx="85" ry="16"
        :fill="`hsl(${hue},85%,60%)`"
        :opacity="on ? 0.08 : 0"
        filter="url(#softGlow)"
      />

      <!-- Base shadow -->
      <ellipse cx="100" cy="348" rx="44" ry="8" fill="#2d2a26" opacity="0.2" />
      <!-- Base -->
      <rect x="60" y="330" width="80" height="16" rx="8" fill="#d5cfc8" />
      <ellipse cx="100" cy="330" rx="40" ry="7" fill="#e8e3de" />
      <ellipse cx="100" cy="328" rx="36" ry="5" fill="#f5f0eb" opacity="0.5" />

      <!-- Pole -->
      <rect x="96" y="200" width="8" height="132" rx="4" fill="#8a8076" />
      <rect x="98" y="202" width="2" height="128" fill="#b8b0a8" opacity="0.3" />

      <!-- Bulb large glow -->
      <circle
        cx="100" cy="218" r="45"
        :fill="`hsl(${hue},85%,60%)`"
        :opacity="on ? 0.06 : 0"
        filter="url(#softGlow)"
      />
      <circle
        cx="100" cy="218" r="28"
        :fill="`hsl(${hue},85%,60%)`"
        :opacity="on ? 0.1 : 0"
        filter="url(#softGlow)"
      />

      <!-- Pull chain (rendered BEFORE shade so top is hidden inside shade) -->
      <line
        :x1="ANCHOR_X" :y1="ANCHOR_Y"
        :x2="handleX" :y2="handleY"
        stroke="#a0988e" stroke-width="2" stroke-linecap="round"
        class="pull-line"
        :style="{
          stroke: triggered ? `hsl(${hue},80%,60%)` : '#a0988e',
          transition: dragging ? 'none' : 'all 0.5s cubic-bezier(0.34, 1.56, 0.64, 1)',
        }"
      />
      <!-- Pull handle -->
      <circle
        :cx="handleX" :cy="handleY"
        r="11" fill="none"
        :stroke="triggered ? `hsl(${hue},80%,60%)` : '#b8b0a8'"
        stroke-width="2.5"
        class="pull-handle"
        :style="{ transition: dragging ? 'none' : 'all 0.5s cubic-bezier(0.34, 1.56, 0.64, 1)' }"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
      />
      <circle
        :cx="handleX" :cy="handleY"
        r="4.5"
        :fill="triggered ? `hsl(${hue},80%,60%)` : '#c8c0b8'"
        class="pull-handle"
        :style="{ transition: dragging ? 'none' : 'all 0.5s cubic-bezier(0.34, 1.56, 0.64, 1)' }"
        @pointerdown="onPointerDown"
        @pointermove="onPointerMove"
        @pointerup="onPointerUp"
      />

      <!-- Lamp shade -->
      <path d="M32 200 Q32 148 100 135 Q168 148 168 200 Z" fill="url(#shadeGrad)" stroke="#d5cfc8" stroke-width="0.5" />
      <ellipse cx="100" cy="200" rx="68" ry="9" fill="#e0dbd5" />
      <path d="M54 178 Q100 146 146 178" fill="none" stroke="#fff" stroke-width="2" opacity="0.35" />
      <ellipse cx="100" cy="200" rx="56" ry="6" :fill="`hsl(${hue},85%,65%)`" opacity="0.3" />

      <!-- Bulb -->
      <ellipse cx="100" cy="216" rx="15" ry="18" fill="url(#bulbGrad)" filter="url(#bulbGlow)" />

      <!-- Face group — rotate 180° when off -->
      <g :transform="`translate(100, 168) rotate(${on ? 0 : 180})`">
        <!-- Left eye -->
        <path d="M-17 -2 Q-14 -8 -11 -2" fill="none" :stroke="on ? '#5a4a3a' : '#2d2a26'" stroke-width="2.2" stroke-linecap="round" :opacity="on ? 1 : 0.5" />
        <!-- Right eye -->
        <path d="M11 -2 Q14 -8 17 -2" fill="none" :stroke="on ? '#5a4a3a' : '#2d2a26'" stroke-width="2.2" stroke-linecap="round" :opacity="on ? 1 : 0.5" />
        <!-- Blush -->
        <circle cx="-21" cy="2" r="5" :fill="`hsl(${hue},70%,60%)`" :opacity="on ? 0.2 : 0" />
        <circle cx="21" cy="2" r="5" :fill="`hsl(${hue},70%,60%)`" :opacity="on ? 0.2 : 0" />
        <!-- Mouth -->
        <ellipse cx="0" cy="6" rx="5.5" ry="4" :fill="`hsl(${hue},75%,55%)`" :opacity="on ? 1 : 0.3" />
        <!-- Tongue -->
        <ellipse cx="0" cy="7.5" rx="3.5" ry="2.2" fill="#f8a4b8" :opacity="on ? 1 : 0" />
      </g>
    </svg>
  </div>
</template>

<style scoped>
.desk-lamp {
  flex-shrink: 0;
  width: 200px;
  height: 400px;
}

.desk-lamp svg {
  width: 100%;
  height: 100%;
}

.pull-line,
.pull-handle {
  cursor: grab;
  user-select: none;
  touch-action: none;
}

.pull-line:active,
.pull-handle:active {
  cursor: grabbing;
}
</style>

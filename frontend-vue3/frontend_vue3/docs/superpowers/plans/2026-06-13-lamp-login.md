# 台灯登录页 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 Login.vue 改造为深色台灯交互登录页 — 左侧 Q 版 SVG 台灯（弹性拉绳开关 + 随机变色 + 音效），右侧毛玻璃表单

**Architecture:** 新增 DeskLamp.vue 封装台灯 SVG/拖拽/音效，通过 emits 向上传递开关状态和色相；Login.vue 接收后通过 CSS class `lamp-on` 和 CSS 变量 `--shade-hue` 驱动表单显隐和全页配色

**Tech Stack:** Vue 3 Composition API + 原生 pointer 事件 + Web Audio API + CSS 自定义属性

---

### Task 1: 创建 DeskLamp.vue — SVG 静态结构

**Files:**
- Create: `src/components/DeskLamp.vue`

- [ ] **Step 1: 创建组件文件，组装完整 SVG 台灯（开灯默认状态）**

写入 `src/components/DeskLamp.vue`：

```vue
<script setup>
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

      <!-- 背景光晕 -->
      <circle cx="100" cy="160" r="140" fill="url(#bgGlow)" :opacity="on ? 1 : 0" />

      <!-- 锥形灯光 -->
      <polygon points="100,195 25,370 175,370" fill="url(#lightCone)" :opacity="on ? 1 : 0" />

      <!-- 桌面光斑 -->
      <ellipse
        cx="100" cy="350" rx="85" ry="16"
        :fill="`hsl(${hue},85%,60%)`"
        :opacity="on ? 0.08 : 0"
        filter="url(#softGlow)"
      />

      <!-- 底座投影 -->
      <ellipse cx="100" cy="348" rx="44" ry="8" fill="#2d2a26" opacity="0.2" />
      <!-- 底座 -->
      <rect x="60" y="330" width="80" height="16" rx="8" fill="#d5cfc8" />
      <ellipse cx="100" cy="330" rx="40" ry="7" fill="#e8e3de" />
      <ellipse cx="100" cy="328" rx="36" ry="5" fill="#f5f0eb" opacity="0.5" />

      <!-- 灯杆 -->
      <rect x="96" y="200" width="8" height="132" rx="4" fill="#8a8076" />
      <rect x="98" y="202" width="2" height="128" fill="#b8b0a8" opacity="0.3" />

      <!-- 灯泡大光晕 -->
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

      <!-- ★ 拉绳（在灯罩之前渲染，绳头被灯罩遮挡） -->
      <line
        x1="130" y1="198"
        x2="130" y2="290"
        stroke="#a0988e" stroke-width="2" stroke-linecap="round"
        class="pull-line"
      />
      <!-- 拉环 -->
      <circle cx="130" cy="300" r="11" fill="none" stroke="#b8b0a8" stroke-width="2.5" class="pull-handle" />
      <circle cx="130" cy="300" r="4.5" fill="#c8c0b8" class="pull-handle" />

      <!-- 灯罩 -->
      <path d="M32 200 Q32 148 100 135 Q168 148 168 200 Z" fill="url(#shadeGrad)" stroke="#d5cfc8" stroke-width="0.5" />
      <ellipse cx="100" cy="200" rx="68" ry="9" fill="#e0dbd5" />
      <path d="M54 178 Q100 146 146 178" fill="none" stroke="#fff" stroke-width="2" opacity="0.35" />
      <ellipse cx="100" cy="200" rx="56" ry="6" :fill="`hsl(${hue},85%,65%)`" opacity="0.3" />

      <!-- 灯泡 -->
      <ellipse cx="100" cy="216" rx="15" ry="18" fill="url(#bulbGrad)" filter="url(#bulbGlow)" />

      <!-- 表情 -->
      <g :transform="`translate(100, 168) rotate(${on ? 0 : 180})`">
        <!-- 左眼 -->
        <path d="M-17 -2 Q-14 -8 -11 -2" fill="none" :stroke="on ? '#5a4a3a' : '#2d2a26'" stroke-width="2.2" stroke-linecap="round" :opacity="on ? 1 : 0.5" />
        <!-- 右眼 -->
        <path d="M11 -2 Q14 -8 17 -2" fill="none" :stroke="on ? '#5a4a3a' : '#2d2a26'" stroke-width="2.2" stroke-linecap="round" :opacity="on ? 1 : 0.5" />
        <!-- 腮红 -->
        <circle cx="-21" cy="2" r="5" :fill="`hsl(${hue},70%,60%)`" :opacity="on ? 0.2 : 0" />
        <circle cx="21" cy="2" r="5" :fill="`hsl(${hue},70%,60%)`" :opacity="on ? 0.2 : 0" />
        <!-- 嘴巴 -->
        <ellipse cx="0" cy="6" rx="5.5" ry="4" :fill="`hsl(${hue},75%,55%)`" :opacity="on ? 1 : 0.3" />
        <!-- 舌头 -->
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
  transition: stroke 0.3s;
}

.pull-line:active,
.pull-handle:active {
  cursor: grabbing;
}
</style>
```

- [ ] **Step 2: 验证组件渲染**

Run: `npm run dev`，在 App.vue 临时引入 `<DeskLamp :on="true" :hue="30" />`，确认台灯 SVG 完整显示

- [ ] **Step 3: Commit**

```bash
git add src/components/DeskLamp.vue
git commit -m "feat: add DeskLamp component with static SVG lamp"
```

---

### Task 2: DeskLamp — 弹性拉绳拖拽交互

**Files:**
- Modify: `src/components/DeskLamp.vue`

- [ ] **Step 1: 添加 pointer 事件拖拽逻辑**

在 `<script setup>` 中添加以下代码（替换原有的空 setup）：

```js
import { ref, computed } from 'vue'

// ... props/emits 保持不变 ...

const ANCHOR_X = 130
const ANCHOR_Y = 198
const TRIGGER_DIST = 50
const MAX_DX = 120
const MAX_DY_UP = 80
const MAX_DY_DOWN = 120

const handleX = ref(ANCHOR_X)
const handleY = ref(300)  // 拉环默认位置（锚点正下方）
const dragging = ref(false)
const triggered = ref(false)  // 是否超过触发阈值

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

function onPointerUp() {
  if (!dragging.value) return
  dragging.value = false

  if (dist.value >= TRIGGER_DIST) {
    const newOn = !props.on
    emit('update:on', newOn)
    if (newOn) {
      const newHue = Math.floor(Math.random() * 360)
      emit('update:hue', newHue)
    }
    playClickSound()
  }

  // 弹簧回弹
  handleX.value = ANCHOR_X
  handleY.value = 300
  triggered.value = false
}
```

- [ ] **Step 2: 添加 Web Audio 音效函数**

在 `<script setup>` 中添加：

```js
function playClickSound() {
  try {
    const ctx = new (window.AudioContext || window.webkitAudioContext)()
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()

    osc.type = 'square'
    osc.frequency.setValueAtTime(800, ctx.currentTime)
    osc.frequency.exponentialRampToValueAtTime(200, ctx.currentTime + 0.08)

    gain.gain.setValueAtTime(0.15, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + 0.1)

    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.start(ctx.currentTime)
    osc.stop(ctx.currentTime + 0.1)
  } catch {
    // 静默处理 AudioContext 不可用的情况
  }
}
```

- [ ] **Step 3: 更新模板中拉绳线/拉环绑定动态坐标 + 指针事件**

将拉绳相关的 SVG 元素替换为：

```html
<!-- 拉绳 -->
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
<!-- 拉环 -->
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
```

- [ ] **Step 4: 手动验证拖拽**

Run `npm run dev`，在浏览器中验证：
- 拉环可向任意方向拖拽
- 超过约 50px 时拉环/绳子变色
- 释放后弹簧回弹原位

- [ ] **Step 5: Commit**

```bash
git add src/components/DeskLamp.vue
git commit -m "feat: add elastic pull chain drag interaction with sound"
```

---

### Task 3: Login.vue — 样式改造为深色毛玻璃主题

**Files:**
- Modify: `src/views/auth/Login.vue`

- [ ] **Step 1: 移除现有浅色样式，写入深色主题 CSS**

删除 `<style scoped>` 中所有现有样式（约 290 行），替换为：

```css
<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 64px;
  padding: 56px 36px;
  background: linear-gradient(180deg, #1a1f2e 0%, #1e2435 40%, #1a2030 100%);
  position: relative;
  overflow: hidden;
  flex-wrap: wrap;
}

/* 卡片 — 毛玻璃 */
.login-card {
  width: 320px;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid hsla(var(--shade-hue, 30), 80%, 60%, 0.25);
  border-radius: 20px;
  padding: 40px 32px;
  box-shadow: 0 0 40px hsla(var(--shade-hue, 30), 80%, 55%, 0.08),
              0 0 80px hsla(var(--shade-hue, 30), 80%, 55%, 0.03),
              0 8px 40px rgba(0, 0, 0, 0.25);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);

  opacity: 0;
  transform: scale(0.92);
  pointer-events: none;
  transition: opacity 0.5s cubic-bezier(0.34, 1.56, 0.64, 1),
              transform 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.lamp-on .login-card {
  opacity: 1;
  transform: scale(1);
  pointer-events: auto;
}

/* 标题 */
.login-card h2 {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 700;
  color: #f5f0eb;
  letter-spacing: 1px;
}

.login-card .sub-text {
  margin: 0 0 32px;
  font-size: 13px;
  color: #8c8076;
}

/* 输入框覆盖 */
.login-card :deep(.el-form-item__label) {
  color: #8c8076;
  font-size: 11px;
  letter-spacing: 1px;
}

.login-card :deep(.el-input__wrapper) {
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  border-radius: 12px;
  height: 46px;
  box-shadow: none;
  transition: border-color 0.3s, box-shadow 0.3s;
}

.login-card :deep(.el-input.is-focus .el-input__wrapper) {
  border-color: hsla(var(--shade-hue, 30), 80%, 60%, 0.5);
  box-shadow: 0 0 14px hsla(var(--shade-hue, 30), 80%, 55%, 0.15),
              inset 0 0 8px hsla(var(--shade-hue, 30), 80%, 55%, 0.03);
}

.login-card :deep(.el-input__inner) {
  color: #f5f0eb;
}

.login-card :deep(.el-input__inner::placeholder) {
  color: #6b6058;
}

/* 按钮 */
.submit-btn {
  width: 100%;
  height: 48px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 10px;
  border-radius: 14px;
  border: none;
  background: linear-gradient(135deg,
    hsl(var(--shade-hue, 30), 70%, 55%),
    hsl(var(--shade-hue, 30), 65%, 45%));
  box-shadow: 0 4px 20px hsla(var(--shade-hue, 30), 70%, 50%, 0.2);
  color: #fff;
  margin-top: 12px;
  transition: background 0.3s, box-shadow 0.3s;
}

.submit-btn:hover {
  box-shadow: 0 6px 24px hsla(var(--shade-hue, 30), 70%, 50%, 0.3);
}

.submit-btn:active {
  transform: scale(0.98);
}

/* 底部链接行 */
.switch-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 22px;
  font-size: 14px;
  color: #8c8076;
}

.switch-row :deep(.el-button) {
  font-size: 14px;
}

.extra-row {
  display: flex;
  justify-content: flex-end;
  margin: -8px 0 4px;
}

/* 忘记密码弹窗适配深色 */
.login-page :deep(.el-dialog) {
  background: #1e2435;
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 16px;
}

.login-page :deep(.el-dialog__header) {
  color: #f5f0eb;
}

.login-page :deep(.el-dialog__body) {
  color: #c8c0b8;
}

.login-page :deep(.el-form-item__label) {
  color: #8c8076;
}

/* 响应式 */
@media (max-width: 768px) {
  .login-page {
    flex-direction: column;
    gap: 24px;
    padding: 32px 20px;
  }

  .login-card {
    width: 100%;
    max-width: 320px;
    padding: 32px 24px;
  }
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add src/views/auth/Login.vue
git commit -m "style: redesign Login.vue with dark frosted glass theme"
```

---

### Task 4: Login.vue — 集成 DeskLamp 组件

**Files:**
- Modify: `src/views/auth/Login.vue`

- [ ] **Step 1: 引入 DeskLamp，添加响应式状态**

在 `<script setup>` 顶部导入后面添加：

```js
import DeskLamp from '@/components/DeskLamp.vue'

const isLampOn = ref(false)
const lampHue = ref(30)

function onLampToggle(on) {
  isLampOn.value = on
}

function onLampHue(hue) {
  lampHue.value = hue
}
```

- [ ] **Step 2: 修改模板，加入台灯和 lamp-on 包装**

将 `<template>` 中的根元素改为：

```html
<template>
  <div class="login-page" :class="{ 'lamp-on': isLampOn }" :style="{ '--shade-hue': lampHue }">
    <DeskLamp
      :on="isLampOn"
      :hue="lampHue"
      @update:on="onLampToggle"
      @update:hue="onLampHue"
    />

    <div class="login-card">
      <h2>欢迎回来</h2>
      <p class="sub-text">登录您的账号</p>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="form.phone" maxlength="11" placeholder="请输入手机号" size="large" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            size="large"
            @keyup.enter="handleLogin"
          />
        </el-form-item>
        <div class="extra-row">
          <el-button link type="primary" @click="openForgot">忘记密码？</el-button>
        </div>
        <el-button type="primary" class="submit-btn" :loading="loading" size="large" @click="handleLogin">
          登 录
        </el-button>
        <div class="switch-row">
          <span>还没有账号？</span>
          <el-button link type="primary" native-type="button" @click="router.push('/register')">
            立即注册
          </el-button>
        </div>
      </el-form>
    </div>

    <!-- 忘记密码弹窗 -->
    <el-dialog v-model="forgotVisible" title="找回密码" width="420px" destroy-on-close>
      <el-form label-position="top">
        <el-form-item label="注册手机号">
          <el-input v-model="forgotForm.phone" maxlength="11" placeholder="请输入注册时的手机号" />
        </el-form-item>
        <el-alert
          v-if="recoveredPassword"
          type="success"
          :closable="false"
          show-icon
          class="password-alert"
        >
          <template #title>
            您的密码是：<strong>{{ recoveredPassword }}</strong>
          </template>
        </el-alert>
      </el-form>
      <template #footer>
        <el-button @click="forgotVisible = false">关闭</el-button>
        <el-button type="primary" :loading="forgotLoading" @click="handleForgot">查询密码</el-button>
      </template>
    </el-dialog>
  </div>
</template>
```

- [ ] **Step 3: 验证完整交互**

Run `npm run dev`：
1. 页面加载 → 台灯关闭、表单隐藏
2. 拖拽拉绳超过阈值松手 → 台灯点亮、表单弹簧弹出、音效播放
3. 再次拉绳 → 台灯关闭、表单缩小消失
4. 多次开灯 → 每次随机不同 hue，全页配色联动变化
5. 表单输入、登录功能正常
6. 忘记密码弹窗正常

- [ ] **Step 4: Commit**

```bash
git add src/views/auth/Login.vue
git commit -m "feat: integrate DeskLamp into Login page with hue-driven theming"
```

---

### Task 5: 清理与收尾

**Files:**
- Modify: `src/views/auth/Login.vue`

- [ ] **Step 1: 移除不再需要的旧样式类引用**

确认 Login.vue 中没有残留旧 CSS class（如 `.auth-page`、`.auth-decor-top`、`.brand`、`.feature-row` 等），若有则删除对应的 HTML 结构。

- [ ] **Step 2: 最终功能验证**

Run `npm run dev`，完整走通流程：
- 首页默认跳转 `/login` → 台灯关闭、表单隐藏
- 拉绳开灯 → 表单弹出
- 输入手机号密码 → 登录成功 → 跳转 `/import/upload`
- 路由守卫正常工作
- 注册页面（Register.vue）不受影响，保持原样

- [ ] **Step 3: Commit**

```bash
git add src/views/auth/Login.vue
git commit -m "chore: clean up old Login styles and verify"
```

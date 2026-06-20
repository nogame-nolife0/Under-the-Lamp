# 台灯登录页设计文档

## 概述

将现有 Login.vue 改造为深色背景的交互式登录页面：左侧放置一个可交互的 SVG 台灯（带弹性拉绳开关），右侧放置毛玻璃风格登录表单。拉绳开灯后表单以弹簧动画弹出，每次开灯随机切换灯光色相。

## 技术决策

- **GSAP 替代**：不使用 GSAP 付费插件。拉绳拖拽用原生 pointer 事件，形变动画用 CSS transition
- **音效**：Web Audio API 程序化生成咔哒声，零外部依赖
- **零新依赖**：不新增任何 npm 包

## 文件变更

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/components/DeskLamp.vue` | 新增 | 台灯 SVG + 拉绳交互 + 音效 |
| `src/views/auth/Login.vue` | 修改 | 引入 DeskLamp，改造样式 |

`Register.vue` 不受影响。

---

## 1. DeskLamp.vue（新增组件）

### 1.1 Props & Emits

```js
// 无 props — 组件自管理拖拽状态

// emits
emit('update:on', boolean)   // 灯光开关状态变化
emit('update:hue', number)   // 色相变化（0~360）
```

### 1.2 SVG 结构

```
<svg viewBox="0 0 200 400">
  <!-- 层级 1: 背景光晕（开灯时显示） -->
  <!-- 层级 2: 锥形灯光（polygon 从灯泡向下扩散） -->
  <!-- 层级 3: 底座 + 底座投影 -->
  <!-- 层级 4: 灯杆 -->
  <!-- ★ 层级 5: 拉绳线 + 拉环（在灯罩之前渲染！绳头被灯罩遮挡） -->
  <!-- 层级 6: 灯罩（遮挡绳头，形成"从内部延伸"效果） -->
  <!-- 层级 7: 灯泡 + 发光 -->
  <!-- 层级 8: 表情组 -->
</svg>
```

**关键**：拉绳在灯罩之前渲染，锚点藏在灯罩底部边缘内侧，灯罩画上去后自然遮挡绳头，视觉上绳子从灯罩内部垂下来。

### 1.3 表情旋转

- 开灯：眼睛 `rotate(0)`（弯弯朝上笑），嘴舌腮红正常
- 关灯：眼睛 `rotate(180)`（朝下），嘴舌腮红变暗 / opacity 降低

通过 CSS `transform: rotate(var(--eye-rotate))` 控制，`--eye-rotate` 由 `--on` 驱动：`--on: 0` 时为 `180deg`，`--on: 1` 时为 `0deg`。

### 1.4 拉绳拖拽

**实现方式**：原生 pointer 事件，不使用 GSAP Draggable。

```
pointerdown on 拉环:
  - setPointerCapture
  - 锚点固定坐标 (anchorX=130, anchorY=198)，在灯罩底部内侧，被灯罩遮挡
  - 记录指针初始偏移

pointermove:
  - 计算指针相对于锚点的偏移量
  - 限制拉环在锚点周围: dx ∈ [-120, 120], dy ∈ [-80, 120]
  - 实时更新拉环 cx/cy 和绳子 x2/y2
  - 距离 = hypot(dx, dy)
  - 距离 ≥ 50px: 拉环高亮（表示触发阈值已到）

pointerup:
  - 释放 pointer capture
  - 距离 ≥ 50px: 触发 toggle
    - 反转 --on（0↔1）
    - 若开灯: 随机生成 hue，emit('update:hue', hue)
    - 播放咔哒音效
  - 拉环 CSS transition 弹簧回弹到锚点正下方原位
    transition: cx 0.5s cubic-bezier(0.34, 1.56, 0.64, 1),
                cy 0.5s cubic-bezier(0.34, 1.56, 0.64, 1)
```

**约束范围**：

```
handleX = clamp(anchorX + dx, anchorX - 120, anchorX + 120)
handleY = clamp(anchorY + dy, anchorY - 80,  anchorY + 120)
```

### 1.5 Web Audio 咔哒音效

```js
function playClickSound() {
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
}
```

短促的方形波频率从 800Hz 降到 200Hz，持续约 80ms，模拟物理开关的咔哒声。每次调用创建新的 AudioContext 并在播放后自动回收。

**浏览器兼容**：AudioContext 需要在用户手势（pointerdown）中创建或恢复，以符合浏览器自动播放策略。在 pointerdown 时预先创建/resume AudioContext，确保后续播放不被阻止。

---

## 2. Login.vue（修改）

### 2.1 保留不变的内容

- `form` reactive 对象（phone, password）
- `rules` 验证规则
- `handleLogin`、`openForgot`、`handleForgot` 方法
- `forgotPassword` API 调用
- `useAuthStore`、`useRouter` 逻辑
- 忘记密码 el-dialog

### 2.2 模板结构变化

```
<div class="login-page">          <!-- 原 auth-page -->
  <DeskLamp                        <!-- 新增台灯组件 -->
    @update:on="..." @update:hue="..."
  />
  <div class="login-card">        <!-- 原 auth-card，样式改为毛玻璃 -->
    <h2>欢迎回来</h2>
    <!-- el-form 保留 -->
  </div>
</div>
```

### 2.3 CSS 变量与状态

页面通过两个 CSS 自定义属性控制配色：

```css
.login-page {
  --shade-hue: 30;  /* 初始暖金色，开灯时 JS 动态更新为随机值 */
}
```

台灯开关状态通过 CSS class `lamp-on` 控制（由 DeskLamp emit 的事件在 Login 中切换）：

```html
<div class="login-page" :class="{ 'lamp-on': isLampOn }">
```

```css
/* 表单卡片 — 关灯时隐藏 */
.login-card {
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid hsla(var(--shade-hue), 80%, 60%, 0.25);
  border-radius: 20px;
  backdrop-filter: blur(24px);
  box-shadow: 0 0 40px hsla(var(--shade-hue), 80%, 55%, 0.08),
              0 8px 40px rgba(0, 0, 0, 0.25);

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
```

**为什么用 class 而非 CSS 变量驱动**：`pointer-events` 不接受数值计算，`opacity: var(--on)` 在不同浏览器中 transition 行为不一致。class toggle 更可靠。

### 2.4 输入框

```css
/* 默认 */
.login-input :deep(.el-input__wrapper) {
  border: 1px solid rgba(255, 255, 255, 0.08);
  background: rgba(255, 255, 255, 0.03);
  border-radius: 12px;
  height: 46px;
}

/* 获焦 */
.login-input :deep(.el-input.is-focus .el-input__wrapper) {
  border-color: hsla(var(--shade-hue), 80%, 60%, 0.5);
  box-shadow: 0 0 14px hsla(var(--shade-hue), 80%, 55%, 0.15),
              inset 0 0 8px hsla(var(--shade-hue), 80%, 55%, 0.03);
}
```

### 2.5 登录按钮

```css
.login-btn {
  background: linear-gradient(135deg,
    hsl(var(--shade-hue), 70%, 55%),
    hsl(var(--shade-hue), 65%, 45%));
  border-radius: 14px;
  height: 48px;
  font-size: 15px;
  letter-spacing: 10px;
  box-shadow: 0 4px 20px hsla(var(--shade-hue), 70%, 50%, 0.2);
}
```

### 2.6 布局

```css
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 64px;
  padding: 56px 36px;
  min-height: 100vh;
  flex-wrap: wrap;  /* 窄屏上下堆叠 */
}
```

### 2.7 忘记密码弹窗

保留现有 el-dialog，样式适配深色主题（深色背景 + 浅色文字）。

---

## 3. Register.vue

不做任何修改。注册页面保持现有浅色调风格。

---

## 4. CSS 变量联动公式

所有颜色通过 `--shade-hue` 统一驱动：

| 元素 | HSL 表达式 |
|------|-----------|
| 灯泡发光 | `hsl(var(--shade-hue), 85%, 60%)` |
| 灯泡光晕 | `hsla(var(--shade-hue), 85%, 60%, 0.5)` |
| 锥形光 | `hsla(var(--shade-hue), 85%, 65%, 0.35)` |
| 卡片边框 | `hsla(var(--shade-hue), 80%, 60%, 0.25)` |
| 卡片发光 | `hsla(var(--shade-hue), 80%, 55%, 0.08)` |
| 按钮背景 | `hsl(var(--shade-hue), 70%, 50%)` |
| 按钮阴影 | `hsla(var(--shade-hue), 70%, 50%, 0.2)` |
| 输入获焦边框 | `hsla(var(--shade-hue), 80%, 60%, 0.5)` |
| 输入获焦发光 | `hsla(var(--shade-hue), 80%, 55%, 0.15)` |
| 台灯嘴巴 | `hsl(var(--shade-hue), 75%, 55%)` |

---

## 5. 响应式

`flex-wrap: wrap` 实现。窄屏（< 768px）时台灯和表单上下堆叠，台灯 SVG 缩小，表单宽度 `max-width: 320px` 自适应。

---

## 6. 不涉及的内容

- Register.vue 保持不变
- 不做路由层面的改动
- 不改变 Auth Store 逻辑
- 不需要额外 npm 依赖

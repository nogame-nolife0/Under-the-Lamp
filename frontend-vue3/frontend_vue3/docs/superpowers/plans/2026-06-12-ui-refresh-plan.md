# UI 重设计 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Redesign the paper generator system UI with enhanced card layouts, animations, and premium pink-themed styling across all 10 layers without changing any API, store, router, or business logic.

**Architecture:** Pure CSS/HTML/template restructuring. Three global CSS files set the foundation (variables, animations, element overrides), then each page/component gets scoped style and template layout changes. No new files — all modifications to existing 13 files.

**Tech Stack:** Vue 3 (Composition API + `<script setup>`), Element Plus 2.x, KaTeX, Vite

**Verification:** After each task, run `npm run dev` and visually confirm the page renders correctly. Final check: all 8 routes render without console errors.

---

## File Structure Map

| File | Responsibility | Change Type |
|------|---------------|-------------|
| `src/styles/variables.css` | Design tokens (colors, shadows, radii) | Append new vars |
| `src/styles/transitions.css` | Global animation keyframes & utility classes | Append new animations |
| `src/styles/element-overrides.css` | Element Plus CSS variable + class overrides | Replace card/button/input rules |
| `src/layout/MainLayout.vue` | Sidebar + header + global import alert | Restructure template + rewrite styles |
| `src/views/auth/Login.vue` | Login page | Rewrite template + styles |
| `src/views/auth/Register.vue` | Register page (mirrors Login) | Rewrite template + styles |
| `src/views/import/Upload.vue` | Word import upload | New two-column layout |
| `src/views/import/Confirm.vue` | Batch confirmation + item editing | Restructure summary + adjust split |
| `src/views/question/List.vue` | Question bank list | Add stat cards + restyle |
| `src/views/paper/Compose.vue` | Manual paper composition | Adjust split + bottom preview |
| `src/views/paper/SmartCompose.vue` | Smart/AI paper composition | Restructure left form + right stats |
| `src/views/paper/History.vue` | Paper history list | Add stat cards + restyle |
| `src/components/QuestionEditDialog.vue` | Question edit modal | Style refresh |
| `src/components/RichContent.vue` | Rich text + LaTeX + image renderer | Style refresh |

---

### Task 1: CSS Foundation — Variables & Design Tokens

**Files:**
- Modify: `src/styles/variables.css`

- [ ] **Step 1: Add new CSS variables**

Append after the existing `--font-weight-semibold` line in `:root`:

```css

  /* ===== 卡片圆角 ===== */
  --radius-card: 12px;
  --radius-lg: 14px;
  --radius-auth: 16px;

  /* ===== 卡片阴影 ===== */
  --shadow-card: 0 2px 12px rgba(45, 36, 39, 0.05);
  --shadow-card-hover: 0 6px 24px rgba(45, 36, 39, 0.10);
  --shadow-lg: 0 12px 40px rgba(45, 36, 39, 0.12);

  /* ===== 统计卡片渐变色 ===== */
  --color-stat-1: linear-gradient(135deg, #fdf2f4 0%, #fce1e7 100%);
  --color-stat-2: linear-gradient(135deg, #edf7f3 0%, #d9f0e6 100%);
  --color-stat-3: linear-gradient(135deg, #fef7f1 0%, #fdf2f4 100%);
  --color-stat-4: linear-gradient(135deg, #f5f0fa 0%, #ede0f5 100%);

  /* ===== 渐变按钮 ===== */
  --btn-gradient: linear-gradient(135deg, #e8738a 0%, #d7657c 100%);
  --btn-gradient-hover: linear-gradient(135deg, #d7657c 0%, #c8576e 100%);

  /* ===== 装饰色 ===== */
  --color-decor-pink: rgba(232, 115, 138, 0.08);
```

- [ ] **Step 2: Run dev server to verify no CSS parse errors**

```bash
npm run dev
```

Expected: dev server starts without CSS errors. Open any page — variables should be available but no visual change yet.

- [ ] **Step 3: Commit**

```bash
git add src/styles/variables.css
git commit -m "feat: add new CSS design tokens for UI refresh"
```

---

### Task 2: CSS Foundation — Animation Keyframes

**Files:**
- Modify: `src/styles/transitions.css`

- [ ] **Step 1: Append new animation keyframes and utility classes**

Append to end of `src/styles/transitions.css`:

```css
/* ===== 卡片入场（stagger 支持） ===== */
.card-enter {
  animation: card-in 0.45s ease-out both;
  animation-delay: calc(var(--i, 0) * 0.08s);
}

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* ===== 统计数字跳动 ===== */
@keyframes count-in {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.count-animate {
  animation: count-in 0.5s ease-out both;
}

/* ===== 面板滑入滑出（底部预览面板） ===== */
.panel-slide-enter-active {
  animation: panel-slide-in 0.3s ease-out;
}

.panel-slide-leave-active {
  animation: panel-slide-out 0.25s ease-in;
}

@keyframes panel-slide-in {
  from {
    transform: translateY(100%);
    opacity: 0;
  }
  to {
    transform: translateY(0);
    opacity: 1;
  }
}

@keyframes panel-slide-out {
  from {
    transform: translateY(0);
    opacity: 1;
  }
  to {
    transform: translateY(100%);
    opacity: 0;
  }
}

/* ===== 脉冲动画（上传区文件指示器） ===== */
@keyframes pulse-border {
  0%, 100% {
    border-color: #ece5e7;
    box-shadow: 0 0 0 0 rgba(232, 115, 138, 0.1);
  }
  50% {
    border-color: #e8738a;
    box-shadow: 0 0 0 6px rgba(232, 115, 138, 0);
  }
}

.pulse-active {
  animation: pulse-border 2s ease-in-out infinite;
}

/* ===== 行滑出（删除/接受后） ===== */
@keyframes row-slide-out {
  to {
    opacity: 0;
    transform: translateX(24px);
    max-height: 0;
    padding-top: 0;
    padding-bottom: 0;
  }
}

.row-leaving {
  animation: row-slide-out 0.3s ease forwards;
}

/* ===== 总分弹跳 ===== */
@keyframes score-bounce {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.15); color: #d7657c; }
}

.score-bounce {
  animation: score-bounce 0.3s ease;
}
```

- [ ] **Step 2: Verify dev server compiles**

```bash
npm run dev
```

Expected: no CSS errors. Animations defined but not yet applied anywhere.

- [ ] **Step 3: Commit**

```bash
git add src/styles/transitions.css
git commit -m "feat: add animation keyframes for UI refresh"
```

---

### Task 3: CSS Foundation — Element Plus Overrides

**Files:**
- Modify: `src/styles/element-overrides.css`

- [ ] **Step 1: Replace card, button, input override rules**

Replace the existing `/* ===== 卡片微调 ===== */` block through `/* ===== 输入框微调 ===== */` with:

```css
/* ===== 卡片微调 ===== */
.el-card {
  border-radius: var(--radius-card);
  border: none;
  box-shadow: var(--shadow-card);
  transition: box-shadow 0.3s ease, transform 0.2s ease;
}

.el-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-1px);
}

.el-card__header {
  border-bottom-color: var(--color-border);
  font-size: 15px;
  font-weight: 600;
  padding: 18px 24px;
}

.el-card__body {
  padding: 24px;
}

/* ===== 表格微调 ===== */
.el-table th.el-table__cell {
  background-color: #faf8f9;
  color: #6b5e63;
  font-size: 13px;
  font-weight: 500;
}

.el-table--striped .el-table__body tr.el-table__row--striped td.el-table__cell {
  background-color: #faf9fa;
}

.el-table__body tr {
  transition: background-color 0.2s ease;
}

.el-table__body tr:hover > td.el-table__cell {
  background-color: #fdf2f4;
}

.el-table__body tr.current-row > td.el-table__cell {
  background-color: #fdf2f4;
}

/* ===== 按钮微调 ===== */
.el-button {
  font-weight: 500;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.el-button:hover {
  transform: none;
}

.el-button:active {
  transform: none;
}

.el-button--primary {
  background: var(--btn-gradient);
  border: none;
}

.el-button--primary:hover {
  background: var(--btn-gradient-hover);
  box-shadow: 0 4px 14px rgba(232, 115, 138, 0.3);
}

.el-button--primary:active {
  background: linear-gradient(135deg, #c8576e 0%, #b84960 100%);
}

/* ===== 输入框微调 ===== */
.el-input__wrapper {
  border-radius: 8px;
  box-shadow: 0 0 0 1px var(--color-border) inset;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.el-input .el-input__wrapper:hover {
  box-shadow: 0 0 0 1px #bfb4b8 inset;
}

.el-input.is-focus .el-input__wrapper {
  box-shadow: 0 0 0 1px #e8738a inset, 0 0 0 3px rgba(232, 115, 138, 0.1);
}

.el-textarea__inner {
  border-radius: 8px;
  transition: box-shadow 0.2s ease, border-color 0.2s ease;
}

.el-textarea__inner:focus {
  box-shadow: 0 0 0 1px #e8738a inset, 0 0 0 3px rgba(232, 115, 138, 0.1);
}

/* ===== 弹窗微调 ===== */
.el-dialog {
  border-radius: var(--radius-lg);
}

.el-overlay {
  background-color: rgba(45, 36, 39, 0.45) !important;
}

/* ===== 下拉菜单微调 ===== */
.el-select .el-input__wrapper {
  border-radius: 8px;
}

/* ===== 分页微调 ===== */
.el-pagination .el-pager li.is-active {
  background-color: #e8738a;
  border-radius: 6px;
}

/* ===== Alert 微调 ===== */
.el-alert {
  border-radius: 8px;
}

/* ===== Tag 微调 ===== */
.el-tag {
  border-radius: 6px;
}

/* ===== 拖拽上传区 ===== */
.el-upload-dragger {
  border-radius: 10px;
  border: 2px dashed var(--color-border);
  background: #faf8f9;
  transition: all 0.3s ease;
}

.el-upload-dragger:hover {
  border-color: #e8738a;
  background-color: #fdf2f4;
}

/* ===== 分割线 ===== */
.el-divider {
  border-color: var(--color-border);
}

/* ===== 折叠面板 ===== */
.el-collapse {
  border: none;
}

.el-collapse-item__header {
  border: none;
  font-weight: 500;
}

.el-collapse-item__wrap {
  border: none;
}
```

Updated the `/* ===== 全局按钮微调 ===== */` block:

```css
.el-button {
  font-weight: 500;
  border-radius: 8px;
  transition: all 0.2s ease;
}

.el-button:hover {
  transform: none;
}

.el-button:active {
  transform: none;
}
```

And update `--el-input-focus-box-shadow` in the `:root` block:

```css
--el-input-focus-box-shadow: 0 0 0 1px #e8738a inset, 0 0 0 3px rgba(232, 115, 138, 0.1);
```

- [ ] **Step 2: Verify dev server — open any page, check card has rounded corners and subtle shadow**

```bash
npm run dev
```

Expected: all Element Plus components now have larger border-radius, cards have shadow, buttons have 8px radius, primary buttons have gradient background.

- [ ] **Step 3: Commit**

```bash
git add src/styles/element-overrides.css
git commit -m "feat: update Element Plus overrides — cards, buttons, inputs"
```

---

### Task 4: Main Layout — Sidebar & Header

**Files:**
- Modify: `src/layout/MainLayout.vue`

- [ ] **Step 1: Replace the entire `<template>` block**

```html
<template>
  <el-container class="layout">
    <el-aside
      :width="collapsed ? '68px' : '240px'"
      class="aside"
      @mouseenter="collapsed = false"
      @mouseleave="collapsed = true"
    >
      <div class="logo-row">
        <div class="logo-icon">试</div>
        <transition name="logo-text">
          <div v-show="!collapsed" class="logo-text">
            <span class="logo-title">试卷出题系统</span>
            <span class="logo-sub">智能组卷 · 题库管理</span>
          </div>
        </transition>
        <div class="collapse-toggle" @click.stop="toggleCollapsed">
          <el-icon :size="16">
            <ArrowRight v-if="collapsed" />
            <ArrowLeft v-else />
          </el-icon>
        </div>
      </div>

      <el-menu :default-active="activeMenu" router :collapse="collapsed" class="side-menu">
        <el-menu-item index="/import/upload">
          <el-icon><Upload /></el-icon>
          <span>题库导入</span>
        </el-menu-item>
        <el-menu-item
          v-if="confirmMenuPath"
          :index="confirmMenuPath"
        >
          <el-icon><Collection /></el-icon>
          <span>导入确认</span>
        </el-menu-item>
        <el-menu-item index="/questions">
          <el-icon><List /></el-icon>
          <span>题库列表</span>
        </el-menu-item>
        <el-menu-item index="/paper/compose">
          <el-icon><EditPen /></el-icon>
          <span>手动组卷</span>
        </el-menu-item>
        <el-menu-item index="/paper/smart">
          <el-icon><MagicStick /></el-icon>
          <span>智能组卷</span>
        </el-menu-item>
        <el-menu-item index="/paper/history">
          <el-icon><Document /></el-icon>
          <span>历史试卷</span>
        </el-menu-item>
      </el-menu>

      <div class="user-bar">
        <div class="user-avatar">{{ (authStore.username || authStore.phone || '?')[0] }}</div>
        <transition name="logo-text">
          <div v-show="!collapsed" class="user-info">
            <span class="user-name">{{ authStore.username || authStore.phone }}</span>
            <el-button link type="danger" class="logout-btn" @click="handleLogout">退出登录</el-button>
          </div>
        </transition>
      </div>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div class="header-left">
          <span class="header-title">{{ pageTitle }}</span>
        </div>
        <div class="header-right">
          <el-button link type="danger" @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-main class="main">
        <transition name="fade-slide">
          <el-alert
            v-if="importStore.uploading"
            type="warning"
            :closable="false"
            show-icon
            class="global-alert"
            title="Word 正在后台解析中"
            description="可自由切换页面，解析不会中断。完成后可在「题库导入」或「导入确认」继续操作。"
          />
        </transition>
        <transition name="fade-slide">
          <el-alert
            v-else-if="importStore.lastBatchUuid && !route.path.startsWith('/import/confirm')"
            type="success"
            :closable="false"
            show-icon
            class="global-alert"
          >
            <template #title>
              有待确认的导入批次：{{ importStore.lastFileName }}（{{ importStore.lastTotalCount }} 题）
            </template>
            <el-button type="primary" link @click="goToConfirm">进入导入确认</el-button>
          </el-alert>
        </transition>

        <RouterView v-slot="{ Component, route: currentRoute }">
          <keep-alive :include="['ImportUpload', 'ImportConfirm']">
            <div
              :key="currentRoute.name === 'import-confirm'
                ? `confirm-${currentRoute.params.batchUuid}`
                : currentRoute.name"
              class="page-wrapper"
              style="--i: 0"
            >
              <component :is="Component" />
            </div>
          </keep-alive>
        </RouterView>
      </el-main>
    </el-container>
  </el-container>
</template>
```

- [ ] **Step 2: Replace the entire `<style scoped>` block**

```css
<style scoped>
.layout {
  min-height: 100vh;
}

/* ===== 侧边栏 ===== */
.aside {
  position: relative;
  background: linear-gradient(180deg, #fcf9fa 0%, #faf8f9 40%, #f7f4f5 100%);
  color: #6b5e63;
  border-right: 1px solid #ece5e7;
  transition: width 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  box-shadow: 2px 0 12px rgba(45, 36, 39, 0.03);
}

/* Logo 行 */
.logo-row {
  height: 64px;
  display: flex;
  align-items: center;
  padding: 0 14px;
  border-bottom: 1px solid #ece5e7;
  gap: 12px;
  flex-shrink: 0;
  position: relative;
}

.logo-icon {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: var(--btn-gradient);
  color: #fff;
  font-size: 16px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 2px 8px rgba(232, 115, 138, 0.25);
}

.logo-text {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.logo-title {
  display: block;
  font-size: 15px;
  font-weight: 700;
  color: #3d2e33;
  white-space: nowrap;
}

.logo-sub {
  display: block;
  font-size: 11px;
  color: #8c7f84;
  white-space: nowrap;
  margin-top: 1px;
}

.logo-text-enter-active {
  transition: opacity 0.25s ease-out, transform 0.25s ease-out;
}
.logo-text-leave-active {
  transition: opacity 0.15s ease-in, transform 0.15s ease-in;
}
.logo-text-enter-from,
.logo-text-leave-to {
  opacity: 0;
  transform: translateX(6px);
}

/* 折叠按钮 */
.collapse-toggle {
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  cursor: pointer;
  color: #8c7f84;
  transition: all 0.2s ease;
  flex-shrink: 0;
}

.collapse-toggle:hover {
  color: #e8738a;
  background: #fdf2f4;
}

/* 菜单 */
.side-menu {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.side-menu :deep(.el-menu) {
  border-right: none;
  background: transparent;
}

.side-menu :deep(.el-menu-item) {
  color: #6b5e63;
  margin: 2px 8px;
  border-radius: 8px;
  height: 42px;
  line-height: 42px;
  transition: all 0.2s ease;
  position: relative;
  padding-left: 20px !important;
}

.side-menu :deep(.el-menu-item:hover) {
  background: linear-gradient(90deg, #fdf2f4, transparent);
  color: #3d2e33;
}

.side-menu :deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, #fdf2f4, rgba(253, 242, 244, 0.4)) !important;
  color: #3d2e33 !important;
  font-weight: 600;
}

.side-menu :deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 20px;
  background: #e8738a;
  border-radius: 0 3px 3px 0;
}

/* 用户栏 */
.user-bar {
  display: flex;
  align-items: center;
  padding: 12px 14px;
  gap: 10px;
  border-top: 1px solid #ece5e7;
  flex-shrink: 0;
}

.user-avatar {
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: linear-gradient(135deg, #fdf2f4, #fce1e7);
  color: #e8738a;
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-info {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-name {
  font-size: 13px;
  color: #3d2e33;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.logout-btn {
  font-size: 12px;
  padding: 0;
  flex-shrink: 0;
}

/* ===== 顶部 Header ===== */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border-bottom: none;
  box-shadow: 0 1px 0 rgba(45, 36, 39, 0.05) inset;
  padding: 0 28px;
  height: 56px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #2d2427;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ===== 内容区 ===== */
.main {
  background: #f5f2f3;
  padding: 24px;
  min-height: calc(100vh - 56px);
}

.global-alert {
  margin-bottom: 16px;
}

.page-wrapper {
  animation: card-in 0.4s ease-out both;
}

/* ===== 响应式（小屏自动收起） ===== */
@media (max-width: 768px) {
  .aside {
    width: 68px !important;
  }
}
</style>
```

- [ ] **Step 3: Trim `<script setup>` — remove unused imports**

Remove `ArrowLeft` and `ArrowRight` from the import — they're already used, so keep them. The script logic stays identical (keep all existing functions, computed, refs).

- [ ] **Step 4: Verify — open the app, check sidebar and header**

```bash
npm run dev
```

Expected: 
- Sidebar has pink gradient logo circle, hover expands/collapses smoothly
- Active menu item has pink left bar indicator
- User avatar and name at sidebar bottom
- Header is semi-transparent with blur
- Import alerts fade in/out with transition

- [ ] **Step 5: Commit**

```bash
git add src/layout/MainLayout.vue
git commit -m "feat: redesign sidebar and header layout"
```

---

### Task 5: Auth Pages — Login

**Files:**
- Modify: `src/views/auth/Login.vue`

- [ ] **Step 1: Replace `<template>` — keep `<script setup>` unchanged**

```html
<template>
  <div class="auth-page">
    <div class="auth-decor-top"></div>
    <div class="auth-decor-bottom"></div>

    <div class="auth-panel card-enter" style="--i: 0">
      <div class="brand">
        <div class="brand-icon">📝</div>
        <h1>试卷出题系统</h1>
        <p>智能组卷 · 题库管理 · 一键导出</p>
      </div>

      <div class="auth-card">
        <div class="card-header-row">
          <span class="card-icon">🔑</span>
          <h2>账号登录</h2>
        </div>

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

      <div class="feature-row">
        <div class="feature-item" style="--i: 1">
          <span class="feature-icon">📄</span>
          <span>Word 导入</span>
        </div>
        <div class="feature-item" style="--i: 2">
          <span class="feature-icon">🔒</span>
          <span>数据安全</span>
        </div>
        <div class="feature-item" style="--i: 3">
          <span class="feature-icon">⚡</span>
          <span>智能组卷</span>
        </div>
      </div>
    </div>

    <!-- 忘记密码弹窗 — 保持不变 -->
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

- [ ] **Step 2: Replace `<style scoped>`**

```css
<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fdf2f4 0%, #faf8f9 40%, #f5f2f3 100%);
  padding: 24px;
  position: relative;
  overflow: hidden;
}

/* 装饰元素 */
.auth-decor-top {
  position: absolute;
  top: -120px;
  right: -80px;
  width: 400px;
  height: 400px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(232, 115, 138, 0.12) 0%, transparent 70%);
  pointer-events: none;
}

.auth-decor-bottom {
  position: absolute;
  bottom: -60px;
  left: -40px;
  width: 300px;
  height: 200px;
  border-radius: 50%;
  background: radial-gradient(ellipse, rgba(232, 115, 138, 0.06) 0%, transparent 70%);
  pointer-events: none;
}

/* 面板 */
.auth-panel {
  width: 100%;
  max-width: 440px;
  position: relative;
  z-index: 1;
}

/* 品牌区 */
.brand {
  text-align: center;
  margin-bottom: 32px;
}

.brand-icon {
  font-size: 40px;
  margin-bottom: 10px;
}

.brand h1 {
  margin: 0;
  font-size: 28px;
  color: #2d2427;
  font-weight: 700;
}

.brand p {
  margin: 8px 0 0;
  color: #8c7f84;
  font-size: 14px;
}

/* 卡片 */
.auth-card {
  background: #fff;
  border-radius: 16px;
  padding: 32px 28px;
  box-shadow: 0 8px 40px rgba(45, 36, 39, 0.08);
}

.card-header-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 28px;
}

.card-icon {
  font-size: 22px;
}

.card-header-row h2 {
  margin: 0;
  font-size: 20px;
  color: #2d2427;
  font-weight: 600;
}

/* 表单 */
.extra-row {
  display: flex;
  justify-content: flex-end;
  margin: -8px 0 4px;
}

.submit-btn {
  width: 100%;
  margin-top: 12px;
  height: 46px;
  font-size: 16px;
  border-radius: 10px;
  letter-spacing: 4px;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 22px;
  font-size: 14px;
  color: #8c7f84;
}

/* 特性图标行 */
.feature-row {
  display: flex;
  justify-content: center;
  gap: 32px;
  margin-top: 28px;
}

.feature-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #8c7f84;
  animation: card-in 0.4s ease-out both;
  animation-delay: calc(var(--i) * 0.1s);
}

.feature-icon {
  font-size: 24px;
}

.password-alert {
  margin-top: 8px;
}
</style>
```

- [ ] **Step 3: Verify — navigate to /login**

```bash
npm run dev
```

Expected: Pink radial decoration, rounded white card with shadow, gradient primary button, three feature icons below, card entrance animation on load.

- [ ] **Step 4: Commit**

```bash
git add src/views/auth/Login.vue
git commit -m "feat: redesign login page with decorations and animations"
```

---

### Task 6: Auth Pages — Register

**Files:**
- Modify: `src/views/auth/Register.vue`

- [ ] **Step 1: Apply the same pattern as Login — replace `<template>`**

```html
<template>
  <div class="auth-page">
    <div class="auth-decor-top"></div>
    <div class="auth-decor-bottom"></div>

    <div class="auth-panel card-enter" style="--i: 0">
      <div class="brand">
        <div class="brand-icon">📝</div>
        <h1>试卷出题系统</h1>
        <p>注册新账号，登录后即可组卷与导出</p>
      </div>

      <div class="auth-card">
        <div class="card-header-row">
          <span class="card-icon">✨</span>
          <h2>注册账号</h2>
        </div>

        <el-form
          ref="formRef"
          :model="form"
          :rules="rules"
          label-position="top"
          @submit.prevent="handleRegister"
        >
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" maxlength="20" placeholder="用于显示的名称" size="large" />
          </el-form-item>
          <el-form-item label="手机号" prop="phone">
            <el-input v-model="form.phone" maxlength="11" placeholder="用于登录与找回密码" size="large" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input
              v-model="form.password"
              type="password"
              show-password
              placeholder="6~32 位密码"
              size="large"
            />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirmPassword">
            <el-input
              v-model="form.confirmPassword"
              type="password"
              show-password
              placeholder="再次输入密码"
              size="large"
              @keyup.enter="handleRegister"
            />
          </el-form-item>
          <el-button type="primary" class="submit-btn" :loading="loading" size="large" @click="handleRegister">
            注册并进入系统
          </el-button>
          <div class="switch-row">
            <span>已有账号？</span>
            <el-button link type="primary" native-type="button" @click="router.push('/login')">
              去登录
            </el-button>
          </div>
        </el-form>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 2: Replace `<style scoped>` with same styles as Login (minus feature-row)**

```css
<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #fdf2f4 0%, #faf8f9 40%, #f5f2f3 100%);
  padding: 24px;
  position: relative;
  overflow: hidden;
}

.auth-decor-top {
  position: absolute;
  top: -120px;
  right: -80px;
  width: 400px;
  height: 400px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(232, 115, 138, 0.12) 0%, transparent 70%);
  pointer-events: none;
}

.auth-decor-bottom {
  position: absolute;
  bottom: -60px;
  left: -40px;
  width: 300px;
  height: 200px;
  border-radius: 50%;
  background: radial-gradient(ellipse, rgba(232, 115, 138, 0.06) 0%, transparent 70%);
  pointer-events: none;
}

.auth-panel {
  width: 100%;
  max-width: 440px;
  position: relative;
  z-index: 1;
}

.brand {
  text-align: center;
  margin-bottom: 32px;
}

.brand-icon {
  font-size: 40px;
  margin-bottom: 10px;
}

.brand h1 {
  margin: 0;
  font-size: 28px;
  color: #2d2427;
  font-weight: 700;
}

.brand p {
  margin: 8px 0 0;
  color: #8c7f84;
  font-size: 14px;
}

.auth-card {
  background: #fff;
  border-radius: 16px;
  padding: 32px 28px;
  box-shadow: 0 8px 40px rgba(45, 36, 39, 0.08);
}

.card-header-row {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 28px;
}

.card-icon {
  font-size: 22px;
}

.card-header-row h2 {
  margin: 0;
  font-size: 20px;
  color: #2d2427;
  font-weight: 600;
}

.submit-btn {
  width: 100%;
  margin-top: 12px;
  height: 46px;
  font-size: 16px;
  border-radius: 10px;
  letter-spacing: 4px;
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 22px;
  font-size: 14px;
  color: #8c7f84;
}
</style>
```

- [ ] **Step 3: Verify — navigate to /register**

Expected: same visual design as login, with "注册账号" header and 4 form fields.

- [ ] **Step 4: Commit**

```bash
git add src/views/auth/Register.vue
git commit -m "feat: redesign register page to match login style"
```

---

### Task 7: Import Upload Page — Two-Column Layout

**Files:**
- Modify: `src/views/import/Upload.vue`

- [ ] **Step 1: Replace `<template>`**

```html
<template>
  <div class="upload-page">
    <el-row :gutter="20">
      <!-- 左栏：主表单 -->
      <el-col :span="14">
        <el-card shadow="never" class="main-card card-enter" style="--i: 0">
          <template #header>
            <div class="card-header">
              <span class="card-title">📤 Word 题库导入</span>
              <el-tag type="info" effect="plain">试卷与答案分文件上传</el-tag>
            </div>
          </template>

          <transition name="fade-slide">
            <el-alert
              v-if="uploading"
              type="warning"
              :closable="false"
              show-icon
              class="tip-block"
              title="正在后台解析"
              :description="importStore.uploadMessage"
            />
          </transition>

          <el-form label-width="100px" class="upload-form">
            <el-form-item label="课程名称" required>
              <el-input
                v-model="courseForm.subject"
                placeholder="如：数字电子技术、大学物理、医学影像技术"
                maxlength="64"
                show-word-limit
                :disabled="uploading"
                size="large"
              />
            </el-form-item>
            <el-form-item label="试卷文件" required>
              <el-upload
                drag
                :auto-upload="false"
                :show-file-list="true"
                :limit="1"
                accept=".docx"
                :disabled="uploading"
                :before-upload="beforeStemUpload"
                :on-change="handleStemChange"
                :on-exceed="() => ElMessage.warning('一次只能上传一个试卷文件')"
                class="upload-zone"
                :class="{ 'has-file': stemFile }"
              >
                <el-icon class="upload-icon"><UploadFilled /></el-icon>
                <div class="el-upload__text">
                  将试卷（题干卷）拖到此处，或 <em>点击选择</em>
                </div>
                <template #tip>
                  <div class="el-upload__tip">必填，仅含题目与选项</div>
                </template>
              </el-upload>
            </el-form-item>

            <el-form-item label="答案文件">
              <el-upload
                drag
                :auto-upload="false"
                :show-file-list="true"
                :limit="1"
                accept=".docx"
                :disabled="uploading"
                :before-upload="beforeAnswerUpload"
                :on-change="handleAnswerChange"
                :on-exceed="() => ElMessage.warning('一次只能上传一个答案文件')"
                class="upload-zone"
                :class="{ 'has-file': answerFile }"
              >
                <el-icon class="upload-icon"><UploadFilled /></el-icon>
                <div class="el-upload__text">
                  将答案卷拖到此处，或 <em>点击选择</em>
                </div>
                <template #tip>
                  <div class="el-upload__tip">选填，按题号与试卷自动配对；也可在确认页手动补答案</div>
                </template>
              </el-upload>
            </el-form-item>

            <el-form-item>
              <el-button type="primary" class="upload-submit-btn" :loading="uploading" size="large" @click="handleUpload">
                {{ uploading ? '解析中…' : '开始解析' }}
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <!-- 右栏：信息面板 -->
      <el-col :span="10">
        <div class="side-panels">
          <!-- 待确认批次 -->
          <transition name="fade-slide">
            <div v-if="hasPendingConfirm" class="info-card pending-card card-enter" style="--i: 1" @click="goToConfirm">
              <div class="info-card-icon">📋</div>
              <div class="info-card-body">
                <div class="info-card-label">待确认批次</div>
                <div class="info-card-value">{{ importStore.lastFileName }}</div>
                <div class="info-card-meta">{{ importStore.lastTotalCount }} 题待确认 →</div>
              </div>
            </div>
          </transition>

          <!-- 导入说明 -->
          <el-card shadow="never" class="info-card card-enter" style="--i: 1">
            <template #header>
              <span class="info-card-title">💡 导入说明</span>
            </template>
            <ul class="info-list">
              <li>支持 <strong>.docx</strong> 格式的 Word 文档</li>
              <li>自动识别题目结构（题干、选项、答案）</li>
              <li>嵌入图片自动提取并关联题目</li>
              <li>解析可在后台进行，不阻塞其他操作</li>
              <li>解析完成后前往「导入确认」逐题审核</li>
            </ul>
          </el-card>

          <!-- 统计概览 -->
          <el-card shadow="never" class="info-card card-enter" style="--i: 2">
            <template #header>
              <span class="info-card-title">📊 题库概览</span>
            </template>
            <div class="stat-row">
              <div class="stat-item">
                <span class="stat-num">{{ importStore.importedCount || 0 }}</span>
                <span class="stat-label">已入库题目</span>
              </div>
              <div class="stat-item">
                <span class="stat-num">{{ importStore.courseCount || 0 }}</span>
                <span class="stat-label">课程数</span>
              </div>
            </div>
          </el-card>
        </div>
      </el-col>
    </el-row>
  </div>
</template>
```

**Note:** The `importStore.importedCount` and `importStore.courseCount` may not exist in the current store. If not available, replace with:
```html
<span class="stat-num">-</span>
```
Or check `src/stores/import.js` — use `importStore.lastTotalCount` for one stat and hardcode the course count display as fallback. Do NOT add new store fields.

- [ ] **Step 2: Replace `<style scoped>`**

```css
<style scoped>
.upload-page {
  /* fills MainLayout content area */
}

.main-card {
  min-height: 520px;
}

.main-card :deep(.el-card__header) {
  padding: 18px 24px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
  color: #2d2427;
}

.tip-block {
  margin-bottom: 20px;
}

.upload-form {
  margin-top: 4px;
}

.upload-submit-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 10px;
  letter-spacing: 2px;
}

/* 上传区 */
.upload-zone :deep(.el-upload-dragger) {
  border-radius: 12px;
  border: 2px dashed #ece5e7;
  background: #faf8f9;
  transition: all 0.3s ease;
  padding: 28px 20px;
}

.upload-zone :deep(.el-upload-dragger:hover) {
  border-color: #e8738a;
  background: #fdf2f4;
}

.upload-zone.has-file :deep(.el-upload-dragger) {
  border-color: #e8738a;
  border-style: solid;
  background: #fdf2f4;
  animation: pulse-border 2s ease-in-out infinite;
}

.upload-icon {
  font-size: 44px;
  color: #e8738a;
  margin-bottom: 8px;
}

.upload-zone :deep(.el-upload__text em) {
  color: #e8738a;
}

/* 右栏面板 */
.side-panels {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-card {
  border: none;
}

.info-card :deep(.el-card__header) {
  padding: 14px 20px;
}

.info-card :deep(.el-card__body) {
  padding: 16px 20px;
}

.info-card-title {
  font-size: 14px;
  font-weight: 600;
}

/* 待确认批次 */
.pending-card {
  background: linear-gradient(135deg, #fdf2f4, #fff);
  border-left: 4px solid #e8738a;
  border-radius: 12px;
  padding: 18px 20px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 14px;
  box-shadow: var(--shadow-card);
  transition: all 0.2s ease;
}

.pending-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-1px);
}

.info-card-icon {
  font-size: 32px;
}

.info-card-body {
  flex: 1;
}

.info-card-label {
  font-size: 12px;
  color: #8c7f84;
}

.info-card-value {
  font-size: 15px;
  font-weight: 600;
  color: #2d2427;
  margin: 2px 0;
}

.info-card-meta {
  font-size: 12px;
  color: #e8738a;
  font-weight: 500;
}

/* 导入说明列表 */
.info-list {
  margin: 0;
  padding: 0 0 0 16px;
  color: #6b5e63;
  font-size: 13px;
  line-height: 2;
}

/* 统计 */
.stat-row {
  display: flex;
  gap: 24px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #e8738a;
}

.stat-label {
  font-size: 12px;
  color: #8c7f84;
}
</style>
```

- [ ] **Step 3: Check if importStore has `importedCount`/`courseCount`**

```bash
grep -n "importedCount\|courseCount" src/stores/import.js || echo "NOT FOUND"
```

If NOT FOUND, update the stat card template to use hardcoded fallback:
```html
<div class="stat-item">
  <span class="stat-num">-</span>
  <span class="stat-label">已入库题目</span>
</div>
<div class="stat-item">
  <span class="stat-num">-</span>
  <span class="stat-label">课程数</span>
</div>
```

- [ ] **Step 4: Verify — navigate to /import/upload**

Expected: Two-column layout, left form full-height card, right side with pending batch card + info card + stats card.

- [ ] **Step 5: Commit**

```bash
git add src/views/import/Upload.vue
git commit -m "feat: redesign import upload page with two-column layout"
```

---

### Task 8: Import Confirm Page — Summary Bar & Adjusted Split

**Files:**
- Modify: `src/views/import/Confirm.vue`

- [ ] **Step 1: Replace template — summary bar + 5:7 split**

```html
<template>
  <div v-loading="loading" class="confirm-page">
    <!-- 摘要条 -->
    <div class="summary-bar card-enter" style="--i: 0">
      <div class="summary-left">
        <div class="summary-icon">📋</div>
        <div class="summary-info">
          <div class="summary-title">{{ batchInfo?.fileName || '导入批次' }}</div>
          <div class="summary-meta">
            <span>共 <strong>{{ batchInfo?.totalCount || 0 }}</strong> 题</span>
            <span class="meta-sep">·</span>
            <span>待确认 <strong>{{ needsReviewCount }}</strong> 题</span>
            <span class="meta-sep">·</span>
            <span>已入库 <strong>{{ items.filter((i) => i.questionId).length }}</strong> 题</span>
          </div>
        </div>
      </div>
      <div class="summary-right">
        <el-form :inline="true" class="course-inline" @submit.prevent="handleSaveCourse">
          <el-form-item>
            <el-select
              v-model="batchCourse.subject"
              filterable
              allow-create
              default-first-option
              placeholder="课程名称"
              style="width: 180px"
            >
              <el-option v-for="name in []" :key="name" :label="name" :value="name" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" plain :loading="savingCourse" @click="handleSaveCourse">保存课程</el-button>
          </el-form-item>
        </el-form>
        <el-button @click="router.push('/questions')">查看题库</el-button>
        <el-button type="primary" :loading="confirming" @click="handleConfirmAll">全部入库</el-button>
      </div>
    </div>

    <!-- 内容区 -->
    <el-row :gutter="16" class="content-row">
      <!-- 左侧：候选题目 5/12 -->
      <el-col :span="10">
        <el-card shadow="never" class="list-card card-enter" style="--i: 1">
          <template #header>
            <div class="list-header">
              <span>候选题目 ({{ items.length }})</span>
              <el-input
                v-model="localFilter"
                placeholder="搜索题号或题干..."
                clearable
                size="small"
                style="width: 200px"
              />
            </div>
          </template>
          <el-table
            :data="filteredItems"
            highlight-current-row
            max-height="500"
            @row-click="selectItem"
          >
            <el-table-column prop="seqNo" label="#" width="45" />
            <el-table-column prop="chapter" label="题号" width="65" />
            <el-table-column label="题干" min-width="160">
              <template #default="{ row }">
                <div class="stem-cell">{{ stemBrief(row.stemRaw) }}</div>
              </template>
            </el-table-column>
            <el-table-column label="置信度" width="80">
              <template #default="{ row }">
                <span class="confidence-dot" :class="isLowConfidence(row) ? 'low' : 'high'"></span>
                {{ row.confidenceScore ?? '-' }}
              </template>
            </el-table-column>
            <el-table-column label="状态" width="80">
              <template #default="{ row }">
                <span class="status-dot" :class="row.questionId ? 'done' : row.status === 'ACCEPTED' ? 'accept' : row.status === 'REJECTED' ? 'reject' : 'pending'"></span>
                {{ statusLabel(row) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="100" fixed="right">
              <template #default="{ row }">
                <template v-if="!row.questionId">
                  <el-button link type="primary" size="small" @click.stop="handleAccept(row)">接受</el-button>
                  <el-button link type="danger" size="small" @click.stop="handleReject(row)">拒绝</el-button>
                </template>
                <span v-else class="done-check">✓</span>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <!-- 右侧：详情/编辑 7/12 -->
      <el-col :span="14">
        <el-card v-if="currentItem" shadow="never" class="detail-card card-enter" style="--i: 2">
          <template #header>
            <span>题目详情 / 编辑</span>
          </template>

          <el-collapse v-model="activeCollapse">
            <el-collapse-item title="题干预览" name="stem">
              <div class="preview-box">
                <RichContent :content="previewStem" :image-map="stemImageMap" />
              </div>
              <el-button link type="primary" @click="showStemEdit = !showStemEdit" class="edit-toggle">
                {{ showStemEdit ? '收起编辑' : '编辑文本' }}
              </el-button>
              <el-input
                v-if="showStemEdit"
                v-model="currentItem.stemHtml"
                type="textarea"
                :rows="5"
                class="edit-area"
                placeholder="LaTeX 公式可用 $...$ 包裹"
              />
            </el-collapse-item>

            <el-collapse-item title="答案预览" name="answer">
              <div class="preview-box answer-preview">
                <RichContent :content="previewAnswer" :image-map="answerImageMap" empty-text="暂无答案" />
              </div>
              <el-button link type="primary" @click="showAnswerEdit = !showAnswerEdit" class="edit-toggle">
                {{ showAnswerEdit ? '收起编辑' : '编辑文本' }}
              </el-button>
              <el-input
                v-if="showAnswerEdit"
                v-model="currentItem.answerHtml"
                type="textarea"
                :rows="4"
                class="edit-area"
              />
            </el-collapse-item>

            <el-collapse-item title="选项（每行一个）" name="options">
              <el-input v-model="currentItem.optionsText" type="textarea" :rows="3" />
            </el-collapse-item>
          </el-collapse>

          <el-row :gutter="12" class="meta-row">
            <el-col :span="8">
              <el-form-item label="题型" label-position="top">
                <el-select v-model="currentItem.questionType" style="width: 100%">
                  <el-option label="单选题" value="SINGLE_CHOICE" />
                  <el-option label="多选题" value="MULTI_CHOICE" />
                  <el-option label="判断题" value="TRUE_FALSE" />
                  <el-option label="填空题" value="FILL_BLANK" />
                  <el-option label="简答题" value="SHORT_ANSWER" />
                  <el-option label="计算题" value="CALCULATION" />
                  <el-option label="解答题" value="ESSAY" />
                  <el-option label="未知" value="UNKNOWN" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="难度" label-position="top">
                <el-select v-model="currentItem.difficulty" clearable style="width: 100%">
                  <el-option label="简单" value="EASY" />
                  <el-option label="中等" value="MEDIUM" />
                  <el-option label="困难" value="HARD" />
                </el-select>
              </el-form-item>
            </el-col>
            <el-col :span="8">
              <el-form-item label="题号" label-position="top">
                <el-input v-model="currentItem.chapter" placeholder="如 2-1" />
              </el-form-item>
            </el-col>
          </el-row>

          <el-alert
            v-if="currentItem.warnings?.length"
            :title="currentItem.warnings.join('；')"
            type="warning"
            show-icon
            :closable="false"
            class="warn-alert"
          />

          <div class="edit-actions">
            <el-button type="primary" @click="handleSaveEdit">保存修改</el-button>
            <el-button v-if="!currentItem.questionId" @click="handleAccept(currentItem)">接受并入库</el-button>
            <el-button v-if="!currentItem.questionId" type="danger" plain @click="handleReject(currentItem)">拒绝此题</el-button>
            <el-button v-else link type="success" @click="router.push('/questions')">已在题库，去查看</el-button>
          </div>
        </el-card>
        <el-empty v-else description="请选择左侧题目" :image-size="80" />
      </el-col>
    </el-row>
  </div>
</template>
```

- [ ] **Step 2: Add the `localFilter` and `filteredItems` computed + `activeCollapse` ref in `<script setup>`**

Add after `const showAnswerEdit = ref(false)`:
```js
const localFilter = ref('')
const activeCollapse = ref(['stem', 'answer'])

const filteredItems = computed(() => {
  if (!localFilter.value) return items.value
  const kw = localFilter.value.toLowerCase()
  return items.value.filter(
    (item) =>
      String(item.chapter || '').toLowerCase().includes(kw) ||
      String(item.stemRaw || '').toLowerCase().includes(kw)
  )
})
```

Import `computed` at top if not already there (check existing import — already has `computed`).

- [ ] **Step 3: Replace `<style scoped>`**

```css
<style scoped>
/* 摘要条 */
.summary-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 24px;
  background: linear-gradient(135deg, #fdf2f4, #faf8f9);
  border-radius: 12px;
  margin-bottom: 16px;
  box-shadow: var(--shadow-card);
}

.summary-left {
  display: flex;
  align-items: center;
  gap: 14px;
}

.summary-icon {
  font-size: 36px;
}

.summary-title {
  font-size: 17px;
  font-weight: 700;
  color: #2d2427;
}

.summary-meta {
  margin-top: 4px;
  font-size: 13px;
  color: #6b5e63;
}

.summary-meta strong {
  color: #2d2427;
}

.meta-sep {
  margin: 0 4px;
  color: #bfb4b8;
}

.summary-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.course-inline {
  margin: 0;
}

/* 内容区 */
.content-row {
  /* natural flex height */
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.stem-cell {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}

/* 状态圆点 */
.confidence-dot,
.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}

.confidence-dot.high { background: #5dab8b; }
.confidence-dot.low { background: #e09b5e; }
.status-dot.done { background: #5dab8b; }
.status-dot.accept { background: #5dab8b; }
.status-dot.reject { background: #bfb4b8; }
.status-dot.pending { background: #e09b5e; }

.done-check { color: #5dab8b; font-weight: 600; }

/* 详情卡片 */
.detail-card :deep(.el-card__body) {
  padding: 20px 24px;
}

.preview-box {
  padding: 14px 16px;
  background: linear-gradient(135deg, #faf8f9, #fdf2f4);
  border: 1px solid #ece5e7;
  border-radius: 8px;
  min-height: 60px;
  max-height: 280px;
  overflow-y: auto;
}

.preview-box.answer-preview {
  background: linear-gradient(135deg, #edf7f3, #e8f6ef);
  border-color: #d4ebe0;
}

.edit-toggle {
  margin: 8px 0 4px;
}

.edit-area {
  margin-bottom: 8px;
}

.meta-row {
  margin-top: 16px;
}

.warn-alert {
  margin-bottom: 16px;
}

.edit-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-top: 12px;
}
</style>
```

- [ ] **Step 4: Verify — navigate to /import/confirm/:uuid**

Expected: Gradient summary bar at top, 5:7 split below, collapsible preview panels, color dots for status/confidence.

- [ ] **Step 5: Commit**

```bash
git add src/views/import/Confirm.vue
git commit -m "feat: redesign import confirm page with summary bar and collapsible panels"
```

---

### Task 9: Question List Page — Stat Cards & Restyle

**Files:**
- Modify: `src/views/question/List.vue`

- [ ] **Step 1: Add stat card row before the main card in `<template>`**

Insert after `<div class="page-card">`:

```html
<div class="page-card">
  <!-- 统计卡片行 -->
  <el-row :gutter="16" class="stat-row">
    <el-col :span="6">
      <div class="stat-card card-enter" style="--i: 0; --c: var(--color-stat-1)">
        <div class="stat-icon">📚</div>
        <div class="stat-body">
          <span class="stat-num count-animate">{{ total }}</span>
          <span class="stat-label">总题目</span>
        </div>
      </div>
    </el-col>
    <el-col :span="6">
      <div class="stat-card card-enter" style="--i: 1; --c: var(--color-stat-2)">
        <div class="stat-icon">✅</div>
        <div class="stat-body">
          <span class="stat-num count-animate">{{ syncedCount }}</span>
          <span class="stat-label">已同步</span>
        </div>
      </div>
    </el-col>
    <el-col :span="6">
      <div class="stat-card card-enter" style="--i: 2; --c: var(--color-stat-3)">
        <div class="stat-icon">📝</div>
        <div class="stat-body">
          <span class="stat-num count-animate">{{ singleChoiceCount }}</span>
          <span class="stat-label">单选题</span>
        </div>
      </div>
    </el-col>
    <el-col :span="6">
      <div class="stat-card card-enter" style="--i: 3; --c: var(--color-stat-4)">
        <div class="stat-icon">🔴</div>
        <div class="stat-body">
          <span class="stat-num count-animate">{{ hardCount }}</span>
          <span class="stat-label">困难题</span>
        </div>
      </div>
    </el-col>
  </el-row>

  <!-- 主卡片 -->
  <el-card shadow="never" class="main-card card-enter" style="--i: 4">
    ...
  </el-card>
```

**Note:** Close the `</div>` for `.page-card` after the `el-card` + dialog sections. The original outermost `<div class="page-card">` and its closing `</div>` need to wrap everything.

- [ ] **Step 2: Add computed properties in `<script setup>` after `total`**

```js
const syncedCount = computed(() => tableData.value.filter((q) => q.embedStatus === 'SYNCED').length)
const singleChoiceCount = computed(() => tableData.value.filter((q) => q.questionType === 'SINGLE_CHOICE').length)
const hardCount = computed(() => tableData.value.filter((q) => q.difficulty === 'HARD').length)
```

These use existing `tableData` — no new API calls. They show data from the current page only (limitation of page-based data), which is acceptable.

- [ ] **Step 3: Update the vector status column template — replace `<el-tag>` with color dot**

In the table column labeled "向量库":
```html
<el-table-column label="向量库" width="90">
  <template #default="{ row }">
    <span class="embed-dot" :class="embedStatusClass(row.embedStatus)"></span>
    {{ embedStatusLabel(row.embedStatus) }}
  </template>
</el-table-column>
```

Add helper:
```js
function embedStatusClass(status) {
  if (status === 'SYNCED') return 'synced'
  if (status === 'FAILED') return 'failed'
  return 'pending'
}
```

- [ ] **Step 4: Update `<style scoped>` — remove max-width, add stat card styles**

```css
<style scoped>
.page-card {
  /* removed max-width */
}

/* 统计卡片行 */
.stat-row {
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  border-radius: 12px;
  background: var(--c, var(--color-stat-1));
  box-shadow: var(--shadow-card);
  transition: box-shadow 0.3s ease, transform 0.2s ease;
}

.stat-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.stat-icon {
  font-size: 32px;
}

.stat-body {
  display: flex;
  flex-direction: column;
}

.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #2d2427;
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: #8c7f84;
  margin-top: 2px;
}

/* 主卡片 */
.main-card :deep(.el-card__header) {
  padding: 18px 24px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-actions {
  display: flex;
  gap: 8px;
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 600;
}

.sub-title {
  font-size: 13px;
  font-weight: 400;
  color: #8c7f84;
}

/* 筛选区 */
.filter-form {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* 向量库状态圆点 */
.embed-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 5px;
  vertical-align: middle;
}

.embed-dot.synced { background: #5dab8b; }
.embed-dot.failed { background: #d95565; }
.embed-dot.pending { background: #e09b5e; }

/* 详情弹窗 */
.detail-block { margin-top: 16px; }
.detail-label {
  font-size: 13px;
  color: #8c7f84;
  margin-bottom: 6px;
  font-weight: 500;
}
.detail-panel {
  line-height: 1.7;
  padding: 12px 14px;
  background: #faf8f9;
  border: 1px solid #ece5e7;
  border-radius: 8px;
}
.detail-panel.answer {
  background: #edf7f3;
  border-color: #d4ebe0;
}
</style>
```

- [ ] **Step 5: Verify — navigate to /questions**

Expected: 4 gradient stat cards at top, filter bar inside card with separator line, color dots in vector column.

- [ ] **Step 6: Commit**

```bash
git add src/views/question/List.vue
git commit -m "feat: redesign question list with stat cards and status dots"
```

---

### Task 10: Manual Compose Page — Adjusted Split & Bottom Preview

**Files:**
- Modify: `src/views/paper/Compose.vue`

- [ ] **Step 1: Adjust `el-row` gutter and `el-col` spans — 7:5 ratio**

Change:
```html
<el-row :gutter="16">
  <el-col :span="14">  <!-- was 14, now effectively wider due to no max-width on page -->
```
to:
```html
<el-row :gutter="20">
  <el-col :span="14">
```

Change right column:
```html
<el-col :span="10">
```

No change to spans — they already map to roughly 58:42 which is close to 7:5. Keep `:span="14"` and `:span="10"`.

- [ ] **Step 2: Compact the left filter — inline row**

Replace the filter form to use a single inline row:
```html
<el-form :inline="true" class="filter-form" @submit.prevent="handleSearch">
  <el-form-item>
    <el-select
      v-model="query.subject"
      placeholder="选择课程"
      clearable
      filterable
      style="width: 160px"
      @change="handleCourseChange"
    >
      <el-option v-for="name in courseOptions" :key="name" :label="name" :value="name" />
    </el-select>
  </el-form-item>
  <el-form-item>
    <el-select v-model="query.questionType" clearable style="width: 110px">
      <el-option v-for="item in questionTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
    </el-select>
  </el-form-item>
  <el-form-item>
    <el-input v-model="query.keyword" placeholder="搜索题干" clearable style="width: 180px" @keyup.enter="handleSearch" />
  </el-form-item>
  <el-form-item>
    <el-button type="primary" @click="handleSearch">查询</el-button>
  </el-form-item>
</el-form>
```

- [ ] **Step 3: Add bottom preview panel (collapsible)**

Add after the right `el-card` closing tag but before the closing `</el-row>`:

```html
<!-- 底部预览面板 -->
<transition name="panel-slide">
  <div v-if="previewQuestion && previewVisible" class="bottom-preview">
    <div class="preview-handle" @click="previewVisible = false">
      <span>👁 题目预览</span>
      <el-button link @click="previewVisible = false">收起 ▲</el-button>
    </div>
    <div class="preview-body">
      <div class="preview-section">
        <div class="preview-label">题干</div>
        <div class="preview-content">
          <RichContent :content="previewQuestion.stem" :image-map="previewStemImageMap" />
        </div>
      </div>
      <div v-if="previewQuestion.options?.length" class="preview-section">
        <div class="preview-label">选项</div>
        <div v-for="(opt, idx) in previewQuestion.options" :key="idx" class="preview-content option-item">
          <RichContent :content="opt" :image-map="previewStemImageMap" />
        </div>
      </div>
      <div class="preview-section">
        <div class="preview-label">答案</div>
        <div class="preview-content answer-bg">
          <RichContent :content="previewQuestion.answer" :image-map="previewAnswerImageMap" empty-text="暂无答案" />
        </div>
      </div>
      <div v-if="previewQuestion.analysis" class="preview-section">
        <div class="preview-label">解析</div>
        <div class="preview-content">
          <RichContent :content="previewQuestion.analysis" :image-map="previewStemImageMap" />
        </div>
      </div>
    </div>
  </div>
</transition>
```

Add in `<script setup>`:
```js
const previewVisible = ref(false)

// Modify handleRowClick and handleSelectedRowClick to also open the panel:
function handleRowClick(row) {
  previewQuestion.value = row
  previewVisible.value = true
}

function handleSelectedRowClick(row) {
  previewQuestion.value = row
  previewVisible.value = true
}
```

- [ ] **Step 4: Replace `<style scoped>` completely**

```css
<style scoped>
.compose-page {
  /* no max-width */
}

.panel {
  min-height: 500px;
}

.panel :deep(.el-card__header) {
  font-weight: 600;
  color: #2d2427;
}

.filter-form {
  margin-bottom: 12px;
}

/* 卡片头部 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}

.score-total {
  font-size: 14px;
  color: #e8738a;
  font-weight: 600;
}

/* 试卷表单 — 紧凑 */
.paper-form {
  margin-bottom: 12px;
}

.paper-form :deep(.el-form-item) {
  margin-bottom: 10px;
}

/* 题型分组 */
.selected-groups {
  max-height: 240px;
  overflow-y: auto;
}

.selected-group + .selected-group {
  margin-top: 14px;
}

.group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  padding: 4px 10px;
  font-size: 13px;
  font-weight: 600;
  color: #2d2427;
  background: linear-gradient(90deg, #fdf2f4, transparent);
  border-left: 3px solid #e8738a;
  border-radius: 0 4px 4px 0;
}

.group-count {
  font-size: 12px;
  font-weight: 400;
  color: #8c7f84;
}

/* 分页 */
.pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

/* 题干预览 */
.stem-preview-cell {
  max-height: 56px;
  overflow: hidden;
}

.stem-preview-cell.is-compact {
  max-height: 40px;
}

.stem-preview-cell :deep(.rich-content) {
  font-size: 13px;
  line-height: 1.45;
}

/* 底部预览面板 */
.bottom-preview {
  position: fixed;
  bottom: 0;
  left: 68px;
  right: 0;
  height: 40vh;
  background: #fff;
  border-top: 1px solid #ece5e7;
  box-shadow: 0 -4px 24px rgba(45, 36, 39, 0.08);
  border-radius: 14px 14px 0 0;
  z-index: 50;
  display: flex;
  flex-direction: column;
}

.preview-handle {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 24px;
  border-bottom: 1px solid #ece5e7;
  font-weight: 600;
  font-size: 14px;
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;
}

.preview-handle:hover {
  background: #faf8f9;
}

.preview-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.preview-section {}

.preview-label {
  font-size: 12px;
  color: #8c7f84;
  margin-bottom: 4px;
  font-weight: 500;
}

.preview-content {
  padding: 10px 14px;
  background: #faf8f9;
  border: 1px solid #ece5e7;
  border-radius: 8px;
}

.preview-content.answer-bg {
  background: #edf7f3;
  border-color: #d4ebe0;
}

.preview-content.option-item {
  margin-bottom: 4px;
}

/* 操作栏 */
.action-bar {
  margin-top: 16px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
</style>
```

- [ ] **Step 5: Verify — navigate to /paper/compose, click a question row**

Expected: 7:5 layout, bottom preview panel slides up when clicking a row, group titles have pink left border, "收起" hides the panel.

- [ ] **Step 6: Commit**

```bash
git add src/views/paper/Compose.vue
git commit -m "feat: redesign manual compose with bottom preview panel"
```

---

### Task 11: Smart Compose Page — Restructured Layout

**Files:**
- Modify: `src/views/paper/SmartCompose.vue`

- [ ] **Step 1: Replace left column form area — remove label-width, add chips**

```html
<el-col :span="14">
  <el-card shadow="never" class="panel card-enter" style="--i: 0">
    <template #header>
      <div class="card-header">
        <span>✨ 智能组卷</span>
        <el-button :loading="syncing" size="small" @click="handleSyncEmbed">同步向量库</el-button>
      </div>
    </template>

    <el-alert type="info" :closable="false" show-icon class="tip-block">
      <template #title>支持按题型比例组卷（如 5单选+3计算），尽量分散章节避免重复。先预览再确认。</template>
    </el-alert>

    <el-form label-position="top" class="compose-form">
      <el-form-item label="试卷名称" required>
        <el-input v-model="form.title" placeholder="如：数字电子技术期末卷" size="large" />
      </el-form-item>
      <el-form-item label="组卷需求" required>
        <el-input
          v-model="form.query"
          type="textarea"
          :rows="5"
          placeholder="例如：数字电子技术 触发器 8道单选题，中等难度"
        />
      </el-form-item>
      <div class="chip-row">
        <span class="chip-label">💡 试试：</span>
        <span
          v-for="item in queryExamples"
          :key="item"
          class="query-chip"
          @click="applyExample(item)"
        >{{ item }}</span>
      </div>
      <el-row :gutter="12">
        <el-col :span="16">
          <el-form-item label="课程">
            <el-input v-model="form.subject" placeholder="如：数字电子技术" />
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label="总分">
            <el-input-number v-model="form.totalScore" :min="1" :max="300" style="width: 100%" />
          </el-form-item>
        </el-col>
        <el-col :span="4">
          <el-form-item label="时长(分)">
            <el-input-number v-model="form.durationMinutes" :min="1" :max="300" style="width: 100%" />
          </el-form-item>
        </el-col>
      </el-row>
      <el-button type="primary" class="search-btn" :loading="searching" size="large" @click="handleSearch">
        ⚡ 检索匹配题目
      </el-button>
    </el-form>

    <transition name="fade-slide">
      <div v-if="Object.keys(composeCondition).length" class="condition-card">
        <div class="condition-title">📋 解析出的组卷条件</div>
        <el-descriptions :column="2" border size="small">
          <el-descriptions-item label="课程">{{ composeCondition.subject || '-' }}</el-descriptions-item>
          <el-descriptions-item label="题量">{{ composeCondition.count || '-' }}</el-descriptions-item>
          <el-descriptions-item label="题型比例" :span="2">
            {{ formatTypeCounts(composeCondition.typeCounts) }}
          </el-descriptions-item>
          <el-descriptions-item label="难度">{{ composeCondition.difficulty || '-' }}</el-descriptions-item>
          <el-descriptions-item label="章节">{{ composeCondition.chapter || '-' }}</el-descriptions-item>
          <el-descriptions-item label="章节去重">每章最多 {{ composeCondition.maxPerChapter || 1 }} 题</el-descriptions-item>
        </el-descriptions>
      </div>
    </transition>
  </el-card>
</el-col>
```

- [ ] **Step 2: Replace right column — stat cards + table + actions**

```html
<el-col :span="10">
  <el-card shadow="never" class="panel card-enter" style="--i: 1">
    <template #header>
      <div class="card-header">
        <span>📋 预览确认</span>
        <span v-if="hasPreview" class="stat-summary">
          共 {{ previewQuestions.length }} 题 · 总分 {{ totalPreviewScore }}
        </span>
      </div>
    </template>

    <el-empty v-if="!hasPreview" description="检索后在此预览、换题并确认生成" :image-size="80" />

    <template v-else>
      <!-- 统计概览条 -->
      <el-row :gutter="12" class="preview-stats">
        <el-col :span="8">
          <div class="mini-stat" style="background: var(--color-stat-1)">
            <span class="mini-num">{{ previewQuestions.length }}</span>
            <span class="mini-label">题量</span>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="mini-stat" style="background: var(--color-stat-2)">
            <span class="mini-num">{{ totalPreviewScore }}</span>
            <span class="mini-label">总分</span>
          </div>
        </el-col>
        <el-col :span="8">
          <div class="mini-stat" style="background: var(--color-stat-3)">
            <span class="mini-num">{{ form.durationMinutes }}</span>
            <span class="mini-label">时长(分)</span>
          </div>
        </el-col>
      </el-row>

      <el-alert
        v-if="previewMessage"
        type="success"
        :closable="false"
        show-icon
        class="preview-tip"
        :title="previewMessage"
      />

      <el-table :data="previewQuestions" size="small" max-height="260" @row-click="handlePreviewRow">
        <el-table-column prop="sortOrder" label="#" width="35" />
        <el-table-column label="题型" width="70">
          <template #default="{ row }">{{ questionTypeLabel(row.questionType) }}</template>
        </el-table-column>
        <el-table-column label="分值" width="80">
          <template #default="{ row }">
            <el-input-number
              v-model="row.score" :min="0.5" :max="100" :step="0.5"
              size="small" controls-position="right" style="width: 70px"
            />
          </template>
        </el-table-column>
        <el-table-column label="题干" min-width="100">
          <template #default="{ row }">
            <div class="stem-preview-cell is-compact">
              <RichContent :content="row.stem" :image-map="buildImageMap(resolveQuestionImages(row), 'STEM')" />
            </div>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="110" fixed="right">
          <template #default="{ row }">
            <el-button link size="small" @click.stop="movePreviewUp(row.questionId)">↑</el-button>
            <el-button link size="small" @click.stop="movePreviewDown(row.questionId)">↓</el-button>
            <el-button link type="primary" size="small" @click.stop="openSwapDialog(row)">换题</el-button>
            <el-button link type="danger" size="small" @click.stop="removePreviewQuestion(row.questionId)">移除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="action-bar">
        <el-button type="primary" :loading="confirming" :disabled="!!paperId" @click="handleConfirmPaper">
          {{ paperId ? '已生成试卷' : '确认生成试卷' }}
        </el-button>
        <el-button type="primary" plain :loading="exporting" :disabled="!paperId" @click="handleExport('STUDENT')">学生版</el-button>
        <el-button type="success" plain :loading="exporting" :disabled="!paperId" @click="handleExport('TEACHER')">教师版</el-button>
      </div>
    </template>
  </el-card>
</el-col>
```

- [ ] **Step 3: Replace `<style scoped>`**

```css
<style scoped>
.smart-compose-page {
  /* no max-width */
}

.panel {
  min-height: 500px;
}

.panel :deep(.el-card__header) {
  font-weight: 600;
  color: #2d2427;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.stat-summary {
  font-size: 13px;
  font-weight: 400;
  color: #8c7f84;
}

.tip-block {
  margin-bottom: 20px;
}

.compose-form {
  margin-top: 4px;
}

/* Chips */
.chip-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: -8px 0 16px;
}

.chip-label {
  font-size: 13px;
  color: #8c7f84;
}

.query-chip {
  display: inline-block;
  padding: 4px 12px;
  background: #fdf2f4;
  color: #e8738a;
  border: 1px solid #f0cfd5;
  border-radius: 20px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s ease;
  white-space: nowrap;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.query-chip:hover {
  background: #e8738a;
  color: #fff;
  border-color: #e8738a;
}

.search-btn {
  width: 100%;
  height: 44px;
  font-size: 16px;
  border-radius: 10px;
}

/* 解析条件 */
.condition-card {
  margin-top: 16px;
  padding: 14px 16px;
  background: #faf8f9;
  border-radius: 10px;
  border: 1px solid #ece5e7;
}

.condition-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
  color: #2d2427;
}

/* 统计概览条 */
.preview-stats {
  margin-bottom: 14px;
}

.mini-stat {
  padding: 12px;
  border-radius: 10px;
  text-align: center;
  display: flex;
  flex-direction: column;
}

.mini-num {
  font-size: 22px;
  font-weight: 700;
  color: #2d2427;
}

.mini-label {
  font-size: 11px;
  color: #8c7f84;
  margin-top: 2px;
}

.preview-tip {
  margin-bottom: 12px;
}

.stem-preview-cell {
  max-height: 48px;
  overflow: hidden;
}

.stem-preview-cell.is-compact {
  max-height: 40px;
}

.action-bar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}
</style>
```

- [ ] **Step 4: Verify — navigate to /paper/smart**

Expected: Left form with chips, full-width search button, condition card. Right side has 3 mini stat cards, table, action buttons. No inline preview box.

- [ ] **Step 5: Commit**

```bash
git add src/views/paper/SmartCompose.vue
git commit -m "feat: redesign smart compose with chips and stat cards"
```

---

### Task 12: History Page — Stat Cards & Style Refresh

**Files:**
- Modify: `src/views/paper/History.vue`

- [ ] **Step 1: Add stat card row + update table status/export columns in template**

Insert after `<div class="page-card">`:

```html
<div class="page-card">
  <el-row :gutter="16" class="stat-row">
    <el-col :span="6">
      <div class="stat-card card-enter" style="--i: 0; --c: var(--color-stat-1)">
        <div class="stat-icon">📄</div>
        <div class="stat-body">
          <span class="stat-num count-animate">{{ total }}</span>
          <span class="stat-label">总试卷</span>
        </div>
      </div>
    </el-col>
    <el-col :span="6">
      <div class="stat-card card-enter" style="--i: 1; --c: var(--color-stat-2)">
        <div class="stat-icon">✅</div>
        <div class="stat-body">
          <span class="stat-num count-animate">{{ completedCount }}</span>
          <span class="stat-label">已完成</span>
        </div>
      </div>
    </el-col>
    <el-col :span="6">
      <div class="stat-card card-enter" style="--i: 2; --c: var(--color-stat-3)">
        <div class="stat-icon">📝</div>
        <div class="stat-body">
          <span class="stat-num count-animate">{{ draftCount }}</span>
          <span class="stat-label">草稿</span>
        </div>
      </div>
    </el-col>
    <el-col :span="6">
      <div class="stat-card card-enter" style="--i: 3; --c: var(--color-stat-4)">
        <div class="stat-icon">📥</div>
        <div class="stat-body">
          <span class="stat-num count-animate">{{ tableData.length }}</span>
          <span class="stat-label">当前页</span>
        </div>
      </div>
    </el-col>
  </el-row>

  <el-card shadow="never" class="main-card card-enter" style="--i: 4">
    ...
  </el-card>
</div>
```

- [ ] **Step 2: Add computed properties in `<script setup>`**

```js
const completedCount = computed(() => tableData.value.filter((r) => r.status === 'COMPLETED').length)
const draftCount = computed(() => tableData.value.filter((r) => r.status === 'DRAFT').length)
```

- [ ] **Step 3: Replace status tag with color dot**

```html
<el-table-column label="状态" width="90">
  <template #default="{ row }">
    <span class="status-dot" :class="row.status === 'COMPLETED' ? 'done' : 'draft'"></span>
    {{ statusMap[row.status] || row.status }}
  </template>
</el-table-column>
```

- [ ] **Step 4: Merge export buttons into dropdown**

```html
<el-table-column label="导出" width="100" fixed="right">
  <template #default="{ row }">
    <el-dropdown @command="(type) => handleExport(row, type)">
      <el-button link type="primary" :loading="exportingId === row.id">
        导出 ▾
      </el-button>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="STUDENT">学生版</el-dropdown-item>
          <el-dropdown-item command="TEACHER">教师版</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>
  </template>
</el-table-column>
```

- [ ] **Step 5: Replace `<style scoped>`**

```css
<style scoped>
.page-card {
  /* no max-width */
}

.stat-row {
  margin-bottom: 20px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 20px;
  border-radius: 12px;
  background: var(--c, var(--color-stat-1));
  box-shadow: var(--shadow-card);
  transition: box-shadow 0.3s ease, transform 0.2s ease;
}

.stat-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.stat-icon { font-size: 32px; }

.stat-body {
  display: flex;
  flex-direction: column;
}

.stat-num {
  font-size: 28px;
  font-weight: 700;
  color: #2d2427;
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: #8c7f84;
  margin-top: 2px;
}

.main-card :deep(.el-card__header) {
  padding: 18px 24px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 600;
}

.sub-title {
  font-size: 13px;
  font-weight: 400;
  color: #8c7f84;
}

.filter-form {
  padding-bottom: 16px;
  margin-bottom: 4px;
  border-bottom: 1px solid var(--color-border);
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

/* 状态圆点 */
.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 5px;
  vertical-align: middle;
}

.status-dot.done { background: #5dab8b; }
.status-dot.draft { background: #bfb4b8; }
</style>
```

- [ ] **Step 6: Verify — navigate to /paper/history**

Expected: 4 stat cards, status dots instead of tags, dropdown export button.

- [ ] **Step 7: Commit**

```bash
git add src/views/paper/History.vue
git commit -m "feat: redesign history page with stat cards and status dots"
```

---

### Task 13: Shared Components — QuestionEditDialog & RichContent

**Files:**
- Modify: `src/components/QuestionEditDialog.vue`
- Modify: `src/components/RichContent.vue`

- [ ] **Step 1: QuestionEditDialog — update dialog width and preview panels**

Change dialog width from `860px` to `900px`:
```html
<el-dialog
  v-model="visible"
  title="编辑题目"
  width="900px"
  destroy-on-close
  class="question-edit-dialog"
>
```

Update `<style scoped>`:
```css
<style scoped>
.tip-alert {
  margin-bottom: 16px;
}

.preview-panel {
  margin-bottom: 8px;
  padding: 12px 16px;
  background: linear-gradient(135deg, #faf8f9, #fdf2f4);
  border: 1px solid #ece5e7;
  border-radius: 8px;
}

.preview-panel.answer-panel {
  background: linear-gradient(135deg, #edf7f3, #e8f6ef);
  border-color: #d4ebe0;
}

.hidden-file-input {
  display: none;
}

.question-edit-dialog :deep(.el-dialog__header) {
  font-size: 17px;
  font-weight: 600;
}

.question-edit-dialog :deep(.el-dialog__footer) {
  text-align: right;
}
</style>
```

- [ ] **Step 2: RichContent — update styles**

Replace `<style scoped>`:
```css
<style scoped>
.rich-content {
  line-height: 1.9;
  font-size: 15px;
  color: #2d2427;
  word-break: break-word;
}

.image-block {
  display: block;
  margin: 10px 0;
}

.embedded-image {
  display: block;
  max-width: 100%;
  height: auto;
  border: 1px solid #ece5e7;
  border-radius: 10px;
  background: #fff;
}

.embedded-image.is-editable,
.image-marker.is-editable {
  cursor: pointer;
}

.embedded-image.is-editable:hover {
  border-color: #e8738a;
  box-shadow: 0 0 0 3px rgba(232, 115, 138, 0.12);
}

.image-marker.is-editable:hover {
  background: #fce1e7;
}

.image-marker {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 32px;
  height: 24px;
  margin: 0 4px;
  padding: 0 8px;
  border-radius: 6px;
  background: #fdf2f4;
  color: #e8738a;
  font-size: 13px;
  font-weight: 600;
  vertical-align: middle;
  animation: pulse-border 2.5s ease-in-out infinite;
}

.rich-text :deep(.math-inline) {
  margin: 0 2px;
}

.rich-text :deep(.math-block) {
  margin: 10px 0;
  padding: 8px 12px;
  border-left: 3px solid #f0cfd5;
  background: #fdf2f4;
  border-radius: 0 6px 6px 0;
  overflow-x: auto;
}

.rich-text :deep(.text-part) {
  white-space: pre-wrap;
}

.rich-text.is-error {
  color: #e09b5e;
  font-size: 13px;
}

.empty-text {
  color: #bfb4b8;
  font-size: 13px;
}
</style>
```

- [ ] **Step 3: Verify — open question detail/edit dialog and check RichContent rendering**

Expected: Dialog wider with rounded preview panels. Math blocks have left pink border. Image markers slightly larger with pulse animation.

- [ ] **Step 4: Commit**

```bash
git add src/components/QuestionEditDialog.vue src/components/RichContent.vue
git commit -m "feat: refresh shared component styles"
```

---

### Task 14: Final Verification — All Routes

**Files:** None (verification only)

- [ ] **Step 1: Start dev server**

```bash
npm run dev
```

- [ ] **Step 2: Visit every route, check for console errors and visual correctness**

| Route | Check |
|-------|-------|
| `/login` | Pink decorations, rounded card, gradient button, feature icons, entrance animation |
| `/register` | Same design as login, 4 form fields |
| `/import/upload` | Two-column layout, left form, right info panels |
| `/import/confirm/:uuid` | Gradient summary bar, 5:7 split, collapsible panels, status dots |
| `/questions` | 4 stat cards, filter with divider, color dots in table |
| `/paper/compose` | Bottom preview panel, group title bars |
| `/paper/smart` | Chips, full-width search button, mini stat cards |
| `/paper/history` | 4 stat cards, status dots, dropdown export |

- [ ] **Step 3: Check for any console errors (F12)**

Expected: no red errors. Vue warnings acceptable if pre-existing.

- [ ] **Step 4: Run build to ensure production compiles**

```bash
npm run build
```

Expected: build succeeds without errors.

- [ ] **Step 5: Final commit**

```bash
git add -A
git commit -m "chore: final verification — all pages render correctly"
```

---

## Summary

| Task | Files | Key Changes |
|------|-------|-------------|
| 1 | `variables.css` | New CSS tokens |
| 2 | `transitions.css` | 4 new animation keyframes |
| 3 | `element-overrides.css` | Card shadows, button gradients, input glow |
| 4 | `MainLayout.vue` | Sidebar logo+user, glass header |
| 5 | `Login.vue` | Decorative bg, custom card, features row |
| 6 | `Register.vue` | Mirror Login design |
| 7 | `Upload.vue` | Two-column 7:5, right info panels |
| 8 | `Confirm.vue` | Summary bar, 5:7 split, collapsible panels |
| 9 | `question/List.vue` | 4 stat cards, status dots |
| 10 | `paper/Compose.vue` | Bottom preview panel, group title bars |
| 11 | `paper/SmartCompose.vue` | Chips, mini stats, full-width button |
| 12 | `paper/History.vue` | 4 stat cards, dropdown export, status dots |
| 13 | `QuestionEditDialog.vue`, `RichContent.vue` | Wider dialog, math block borders, image pulse |
| 14 | All routes | Visual verification + build check |

**No files created, no API/store/router changes.**

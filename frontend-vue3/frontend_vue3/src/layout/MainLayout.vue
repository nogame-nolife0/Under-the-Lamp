<script setup>
import { RouterView, useRoute, useRouter } from 'vue-router'
import {
  Collection,
  Document,
  EditPen,
  List,
  MagicStick,
  Upload,
} from '@element-plus/icons-vue'
import { computed, ref } from 'vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useImportStore } from '@/stores/import'
import { useComposeStore } from '@/stores/compose'
import { APP_NAME, APP_TAGLINE } from '@/constants/app'

const route = useRoute()
const router = useRouter()
const navExpanded = ref(false)

const authStore = useAuthStore()
const importStore = useImportStore()
const composeStore = useComposeStore()
const { selectedQuestions, selectedPanelVisible } = storeToRefs(composeStore)

const isComposePage = computed(() => route.path.startsWith('/paper/compose'))

const navItems = computed(() => {
  const items = [
    { path: '/import/upload', icon: Upload, label: '题库导入', desc: 'Word 解析入库' },
    { path: '/questions', icon: List, label: '题库列表', desc: '浏览与管理' },
    { path: '/paper/compose', icon: EditPen, label: '手动组卷', desc: '精选题目出卷' },
    { path: '/paper/smart', icon: MagicStick, label: '智能组卷', desc: 'AI 辅助选题' },
    { path: '/paper/history', icon: Document, label: '历史试卷', desc: '导出与归档' },
  ]
  if (confirmMenuPath.value) {
    items.splice(1, 0, {
      path: confirmMenuPath.value,
      icon: Collection,
      label: '导入确认',
      desc: '逐题审核',
    })
  }
  return items
})

const activeMenu = computed(() => {
  if (route.path.startsWith('/import/confirm')) {
    return `/import/confirm/${importStore.lastBatchUuid || route.params.batchUuid || ''}`
  }
  return route.path
})

const pageTitle = computed(() => route.meta?.title || APP_NAME)
const pageDesc = computed(() => route.meta?.description || '')

const keepAlivePages = [
  'ImportUpload',
  'ImportConfirm',
  'PaperCompose',
  'PaperSmartCompose',
]

function pageCacheKey(currentRoute) {
  if (currentRoute.name === 'import-confirm') {
    return `confirm-${currentRoute.params.batchUuid}`
  }
  return currentRoute.name
}

const confirmMenuPath = computed(() => {
  const uuid = importStore.lastBatchUuid || route.params.batchUuid
  return uuid ? `/import/confirm/${uuid}` : ''
})

function goToConfirm() {
  if (confirmMenuPath.value) {
    router.push(confirmMenuPath.value)
  }
}

function isActive(path) {
  if (path.startsWith('/import/confirm')) {
    return route.path.startsWith('/import/confirm')
  }
  return activeMenu.value === path || activeMenu.value.startsWith(path + '/')
}

async function handleLogout() {
  await authStore.logout()
  router.replace('/login')
}

const userInitial = computed(() => (authStore.username || authStore.phone || '?')[0])
const userName = computed(() => authStore.username || authStore.phone || '用户')
</script>

<template>
  <div class="app-shell">
    <div class="ambient ambient--warm" aria-hidden="true" />
    <div class="ambient ambient--cool" aria-hidden="true" />

    <aside
      class="nav-rail"
      :class="{ expanded: navExpanded }"
      @mouseenter="navExpanded = true"
      @mouseleave="navExpanded = false"
    >
      <div class="nav-brand">
        <div class="brand-mark">灯</div>
        <transition name="nav-text">
          <div v-show="navExpanded" class="brand-copy">
            <span class="brand-title">{{ APP_NAME }}</span>
            <span class="brand-sub">{{ APP_TAGLINE }}</span>
          </div>
        </transition>
      </div>

      <nav class="nav-menu">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <span class="nav-icon-wrap">
            <el-icon :size="20"><component :is="item.icon" /></el-icon>
          </span>
          <transition name="nav-text">
            <span v-show="navExpanded" class="nav-label">
              <span class="nav-label-title">{{ item.label }}</span>
              <span class="nav-label-desc">{{ item.desc }}</span>
            </span>
          </transition>
        </router-link>
      </nav>

      <div class="nav-footer">
        <div class="user-chip">
          <div class="user-avatar">{{ userInitial }}</div>
          <transition name="nav-text">
            <div v-show="navExpanded" class="user-meta">
              <span class="user-name">{{ userName }}</span>
              <button type="button" class="logout-link" @click="handleLogout">退出登录</button>
            </div>
          </transition>
        </div>
      </div>
    </aside>

    <div class="workspace">
      <header class="top-bar">
        <div class="top-bar__lead">
          <h1 class="top-bar__title">{{ pageTitle }}</h1>
          <p v-if="pageDesc" class="top-bar__desc">{{ pageDesc }}</p>
        </div>
        <div class="top-bar__actions">
          <el-badge
            v-if="isComposePage"
            :value="selectedQuestions.length"
            :hidden="!selectedQuestions.length"
            :max="99"
          >
            <el-button type="primary" @click="selectedPanelVisible = true">已选题目</el-button>
          </el-badge>
          <el-button v-if="confirmMenuPath && !route.path.startsWith('/import/confirm')" plain @click="goToConfirm">
            待确认批次
          </el-button>
          <el-button link class="logout-top" @click="handleLogout">退出</el-button>
        </div>
      </header>

      <main class="content-stage">
        <transition name="fade-slide">
          <el-alert
            v-if="importStore.uploading"
            type="warning"
            :closable="false"
            show-icon
            class="global-alert"
            title="Word 正在后台解析中"
            :description="importStore.uploadMessage || '可自由切换页面，解析不会中断。'"
          />
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
          <div class="route-outlet is-fill">
            <keep-alive :include="keepAlivePages">
              <component
                :is="Component"
                v-if="Component"
                :key="pageCacheKey(currentRoute)"
                class="page-wrapper is-fill"
                style="--i: 0"
              />
            </keep-alive>
          </div>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.app-shell {
  position: relative;
  display: flex;
  height: 100vh;
  max-height: 100vh;
  background: linear-gradient(165deg, #141820 0%, #1a1f2e 38%, #171c28 100%);
  overflow: hidden;
}

.ambient {
  position: fixed;
  border-radius: 50%;
  pointer-events: none;
  filter: blur(80px);
  z-index: 0;
}

.ambient--warm {
  width: 520px;
  height: 520px;
  top: -120px;
  left: 8%;
  background: radial-gradient(circle, hsla(var(--shade-hue), 70%, 50%, 0.14) 0%, transparent 70%);
}

.ambient--cool {
  width: 400px;
  height: 400px;
  bottom: -80px;
  right: 12%;
  background: radial-gradient(circle, rgba(90, 110, 180, 0.08) 0%, transparent 70%);
}

/* ===== 侧边导航 ===== */
.nav-rail {
  position: relative;
  z-index: 10;
  flex-shrink: 0;
  align-self: stretch;
  width: var(--nav-width);
  margin: 16px 0 16px 16px;
  padding: 14px 10px;
  display: flex;
  flex-direction: column;
  border-radius: var(--radius-xl);
  background: var(--color-bg-sidebar);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-md), inset 0 1px 0 rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  transition: width var(--transition-slow);
  overflow: hidden;
}

.nav-rail.expanded {
  width: var(--nav-width-expanded);
}

.nav-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 6px 8px 18px;
  border-bottom: 1px solid var(--color-border);
  margin-bottom: 12px;
  position: relative;
}

.brand-mark {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: var(--btn-gradient);
  color: #fff;
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 4px 16px hsla(var(--shade-hue), 70%, 50%, 0.35);
}

.brand-copy {
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.brand-title {
  display: block;
  font-family: var(--font-display);
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
}

.brand-sub {
  display: block;
  font-size: 11px;
  color: var(--color-text-secondary);
  white-space: nowrap;
  margin-top: 2px;
}

.nav-menu {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  overflow-y: auto;
  padding: 4px 0;
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.12) transparent;
}

.nav-menu::-webkit-scrollbar {
  width: 4px;
}

.nav-menu::-webkit-scrollbar-track {
  background: transparent;
}

.nav-menu::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 4px;
}

.nav-menu::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.2);
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 10px 10px;
  border-radius: 12px;
  text-decoration: none;
  color: var(--color-text-sidebar);
  transition: all var(--transition);
  position: relative;
  min-height: 48px;
}

.nav-item:hover {
  background: var(--color-bg-glass-hover);
  color: var(--color-text-primary);
}

.nav-item.active {
  background: var(--color-primary-light);
  color: var(--color-text-primary);
}

.nav-item.active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 22px;
  border-radius: 0 4px 4px 0;
  background: var(--color-primary);
  box-shadow: 0 0 12px var(--color-primary-glow);
}

.nav-icon-wrap {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: var(--color-bg-glass);
  transition: background var(--transition);
}

.nav-item.active .nav-icon-wrap {
  background: hsla(var(--shade-hue), 60%, 50%, 0.2);
  color: hsl(var(--shade-hue), 70%, 62%);
}

.nav-label {
  display: flex;
  flex-direction: column;
  min-width: 0;
  overflow: hidden;
}

.nav-label-title {
  font-size: 13px;
  font-weight: 600;
  white-space: nowrap;
}

.nav-label-desc {
  font-size: 11px;
  color: var(--color-text-placeholder);
  white-space: nowrap;
  margin-top: 1px;
}

.nav-item.active .nav-label-desc {
  color: var(--color-text-secondary);
}

.nav-footer {
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
  margin-top: 8px;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 12px;
}

.user-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  background: linear-gradient(135deg, hsla(var(--shade-hue), 50%, 45%, 0.3), rgba(255, 255, 255, 0.06));
  border: 1px solid var(--color-primary-border);
  color: hsl(var(--shade-hue), 65%, 65%);
  font-size: 14px;
  font-weight: 600;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.logout-link {
  border: none;
  background: none;
  padding: 0;
  font-size: 11px;
  color: var(--color-text-secondary);
  cursor: pointer;
  text-align: left;
  transition: color var(--transition);
}

.logout-link:hover {
  color: var(--color-danger);
}

.nav-text-enter-active {
  transition: opacity 0.22s ease, transform 0.22s ease;
}
.nav-text-leave-active {
  transition: opacity 0.12s ease, transform 0.12s ease;
}
.nav-text-enter-from,
.nav-text-leave-to {
  opacity: 0;
  transform: translateX(6px);
}

/* ===== 工作区 ===== */
.workspace {
  position: relative;
  z-index: 5;
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  margin: 16px 16px 16px 12px;
  border-radius: var(--radius-xl);
  border: 1px solid var(--color-border);
  background: rgba(26, 31, 46, 0.45);
  box-shadow: var(--shadow-md);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  overflow: hidden;
}

.top-bar {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  padding: 20px 28px 18px;
  border-bottom: 1px solid var(--color-border);
  background: rgba(255, 255, 255, 0.02);
}

.top-bar__title {
  margin: 0;
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 600;
  color: var(--color-text-primary);
  letter-spacing: 0.02em;
}

.top-bar__desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.top-bar__actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.logout-top {
  color: var(--color-text-secondary) !important;
}

.logout-top:hover {
  color: var(--color-danger) !important;
}

.content-stage {
  flex: 1;
  min-height: 0;
  padding: 24px 28px 28px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.global-alert {
  margin-bottom: 18px;
  flex-shrink: 0;
}

.route-outlet {
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.route-outlet :deep(.page-wrapper.page-scroll),
.route-outlet :deep(.page-scroll.page-wrapper) {
  flex: 1;
  min-height: 0;
  height: 100%;
  overflow-x: hidden;
  overflow-y: auto;
  -webkit-overflow-scrolling: touch;
}

.page-wrapper {
  animation: card-in 0.45s ease-out both;
}

@media (max-width: 900px) {
  .app-shell {
    flex-direction: column;
    height: 100vh;
    max-height: 100vh;
  }

  .nav-rail {
    width: auto !important;
    margin: 12px 12px 0;
    flex-direction: row;
    align-items: center;
    padding: 10px 12px;
  }

  .nav-rail.expanded {
    width: auto !important;
  }

  .nav-brand {
    border-bottom: none;
    margin-bottom: 0;
    padding-bottom: 0;
  }

  .nav-menu {
    flex-direction: row;
    flex: 1;
    overflow-x: auto;
  }

  .nav-label,
  .brand-copy,
  .user-meta {
    display: none !important;
  }

  .nav-footer {
    border-top: none;
    margin-top: 0;
    padding-top: 0;
  }

  .workspace {
    margin: 12px;
    min-height: 0;
    flex: 1;
  }

  .top-bar {
    flex-direction: column;
    align-items: flex-start;
    padding: 16px 20px;
  }

  .content-stage {
    padding: 16px 20px 20px;
    min-height: 0;
  }
}
</style>

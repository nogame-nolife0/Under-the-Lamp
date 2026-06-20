import { createRouter, createWebHistory } from 'vue-router'
import MainLayout from '@/layout/MainLayout.vue'
import { useAuthStore } from '@/stores/auth'
import { APP_NAME } from '@/constants/app'

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/auth/Login.vue'),
      meta: { public: true, title: '登录' },
    },
    {
      path: '/register',
      name: 'register',
      component: () => import('@/views/auth/Register.vue'),
      meta: { public: true, title: '注册' },
    },
    {
      path: '/',
      component: MainLayout,
      redirect: '/import/upload',
      meta: { requiresAuth: true },
      children: [
        {
          path: 'import/upload',
          name: 'import-upload',
          component: () => import('@/views/import/Upload.vue'),
          meta: { title: '题库导入', description: '上传 Word 试卷，解析并写入题库', requiresAuth: true, fillViewport: true },
        },
        {
          path: 'import/confirm/:batchUuid',
          name: 'import-confirm',
          component: () => import('@/views/import/Confirm.vue'),
          meta: { title: '导入确认', description: '逐题审核题干、答案与选项', requiresAuth: true, fillViewport: true },
        },
        {
          path: 'questions',
          name: 'question-list',
          component: () => import('@/views/question/List.vue'),
          meta: { title: '题库列表', description: '检索、编辑与管理已入库题目', requiresAuth: true, fillViewport: true },
        },
        {
          path: 'paper/compose',
          name: 'paper-compose',
          component: () => import('@/views/paper/Compose.vue'),
          meta: { title: '手动组卷', description: '从题库精选题目，自定义分值与顺序', requiresAuth: true, fillViewport: true },
        },
        {
          path: 'paper/smart',
          name: 'paper-smart',
          component: () => import('@/views/paper/SmartCompose.vue'),
          meta: { title: '智能组卷', description: '按章节与题型比例 AI 辅助选题', requiresAuth: true, fillViewport: true },
        },
        {
          path: 'paper/history',
          name: 'paper-history',
          component: () => import('@/views/paper/History.vue'),
          meta: { title: '历史试卷', description: '查看、导出与管理已生成试卷', requiresAuth: true, fillViewport: true },
        },
      ],
    },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  if (!authStore.initialized) {
    await authStore.init()
  }

  if (to.meta.public) {
    if (authStore.token && (to.name === 'login' || to.name === 'register')) {
      return '/import/upload'
    }
    return true
  }

  if (!authStore.registered) {
    return '/login'
  }
  if (!authStore.token) {
    return '/login'
  }
  return true
})

router.afterEach((to) => {
  const pageTitle = to.meta?.title
  document.title = pageTitle ? `${pageTitle} · ${APP_NAME}` : APP_NAME
})

export default router

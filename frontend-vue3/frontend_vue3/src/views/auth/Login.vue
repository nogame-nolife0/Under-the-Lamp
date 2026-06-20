<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { forgotPassword } from '@/api/auth'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import DeskLamp from '@/components/DeskLamp.vue'

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()

const isRegister = ref(false)
const loading = ref(false)
const registerLoading = ref(false)
const forgotVisible = ref(false)
const forgotLoading = ref(false)
const recoveredPassword = ref('')

const lampDialogTransition = {
  name: 'lamp-dialog',
  appear: true,
}

const isLampOn = ref(false)
const lampHue = ref(themeStore.shadeHue)

function onLampToggle(on) {
  isLampOn.value = on
}

function onLampHue(hue) {
  lampHue.value = hue
  themeStore.setShadeHue(hue)
}

// ---- login ----
const loginFormRef = ref(null)
const loginForm = reactive({ phone: '', password: '' })

const loginRules = {
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '请输入 11 位手机号', trigger: 'blur' },
  ],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
}

async function handleLogin() {
  try { await loginFormRef.value?.validate() } catch { return }
  loading.value = true
  try {
    await authStore.login({ ...loginForm })
    ElMessage.success('登录成功')
    router.replace('/import/upload')
  } finally {
    loading.value = false
  }
}

// ---- register ----
const registerFormRef = ref(null)
const registerForm = reactive({
  username: '',
  phone: '',
  password: '',
  confirmPassword: '',
})

function validateConfirmPassword(_rule, value, callback) {
  if (!value) { callback(new Error('请确认密码')); return }
  if (value !== registerForm.password) { callback(new Error('两次输入的密码不一致')); return }
  callback()
}

const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '用户名长度为 2~20 个字符', trigger: 'blur' },
  ],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '请输入 11 位手机号', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度为 6~32 位', trigger: 'blur' },
  ],
  confirmPassword: [{ validator: validateConfirmPassword, trigger: 'blur' }],
}

function switchToRegister() {
  registerForm.username = ''
  registerForm.phone = ''
  registerForm.password = ''
  registerForm.confirmPassword = ''
  isRegister.value = true
}

async function handleRegister() {
  try { await registerFormRef.value?.validate() } catch { return }
  registerLoading.value = true
  try {
    await authStore.register({ ...registerForm })
    ElMessage.success('注册成功，已自动登录')
    router.replace('/import/upload')
  } finally {
    registerLoading.value = false
  }
}

// ---- forgot ----
const forgotForm = reactive({ phone: '' })

function openForgot() {
  forgotForm.phone = loginForm.phone
  recoveredPassword.value = ''
  forgotVisible.value = true
}

async function handleForgot() {
  if (!/^1\d{10}$/.test(forgotForm.phone)) {
    ElMessage.warning('请输入正确的手机号')
    return
  }
  forgotLoading.value = true
  try {
    const result = await forgotPassword(forgotForm.phone)
    recoveredPassword.value = result.password
    ElMessage.success('已找到该账号密码')
  } finally {
    forgotLoading.value = false
  }
}
</script>

<template>
  <div
    class="login-page"
    :class="{ 'lamp-on': isLampOn }"
    :style="{ '--shade-hue': lampHue, '--card-height': '480px' }"
  >
    <div class="lamp-wrapper">
      <DeskLamp
        :on="isLampOn"
        :hue="lampHue"
        @update:on="onLampToggle"
        @update:hue="onLampHue"
      />
    </div>

    <div
      class="login-card"
      :class="{ 'is-register': isRegister }"
      :style="isLampOn ? { '--shade-hue': lampHue } : undefined"
    >
      <transition name="form-swap" mode="out-in">
        <!-- ====== 登录表单 ====== -->
        <div v-if="!isRegister" key="login" class="login-panel">
          <h2>欢迎回来</h2>
          <p class="sub-text">登录您的账号</p>

          <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" label-position="top" @submit.prevent="handleLogin">
            <el-form-item label="手机号" prop="phone">
              <el-input v-model="loginForm.phone" maxlength="11" placeholder="请输入手机号" size="large" />
            </el-form-item>
            <el-form-item label="密码" prop="password">
              <el-input v-model="loginForm.password" type="password" show-password placeholder="请输入密码" size="large" />
            </el-form-item>
            <div class="extra-row">
              <el-button link type="primary" @click="openForgot">忘记密码？</el-button>
            </div>
            <el-button type="primary" class="submit-btn" :loading="loading" size="large" @click="handleLogin">
              登 录
            </el-button>
            <div class="switch-row">
              <span>还没有账号？</span>
              <el-button link type="primary" native-type="button" @click="switchToRegister">
                立即注册
              </el-button>
            </div>
          </el-form>
        </div>

        <!-- ====== 注册表单 ====== -->
        <div v-else key="register" class="register-panel">
          <h2>注册账号</h2>
          <p class="sub-text">创建新账号，开始智能组卷</p>

          <el-form ref="registerFormRef" class="register-form" :model="registerForm" :rules="registerRules" label-position="top" @submit.prevent="handleRegister">
            <div class="register-fields-scroll">
              <el-form-item label="用户名" prop="username">
                <el-input v-model="registerForm.username" maxlength="20" placeholder="用于显示的名称" size="large" />
              </el-form-item>
              <el-form-item label="手机号" prop="phone">
                <el-input v-model="registerForm.phone" maxlength="11" placeholder="用于登录与找回密码" size="large" />
              </el-form-item>
              <el-form-item label="密码" prop="password">
                <el-input v-model="registerForm.password" type="password" show-password placeholder="6~32 位密码" size="large" />
              </el-form-item>
              <el-form-item label="确认密码" prop="confirmPassword">
                <el-input v-model="registerForm.confirmPassword" type="password" show-password placeholder="再次输入密码" size="large" />
              </el-form-item>
              <el-button type="primary" class="submit-btn register-submit-btn" :loading="registerLoading" size="large" @click="handleRegister">
                注册并登录
              </el-button>
              <div class="switch-row">
                <span>已有账号？</span>
                <el-button link type="primary" native-type="button" @click="isRegister = false">
                  去登录
                </el-button>
              </div>
            </div>
          </el-form>
        </div>
      </transition>
    </div>

    <!-- 忘记密码弹窗 -->
    <el-dialog
      v-model="forgotVisible"
      title="找回密码"
      width="420px"
      append-to-body="false"
      destroy-on-close
      class="forgot-dialog"
      :transition="lampDialogTransition"
    >
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

<style scoped>
.login-page {
  height: 100%;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: 64px;
  padding-top: calc((100vh - var(--card-height, 480px)) / 2);
  background: linear-gradient(180deg, #1a1f2e 0%, #1e2435 40%, #1a2030 100%);
  overflow: hidden;
  flex-wrap: wrap;
}

.lamp-wrapper {
  flex-shrink: 0;
  transition: transform 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}

.login-card {
  width: 0;
  min-width: 0;
  height: 0;
  padding: 0;
  border: none;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.05);
  border-radius: 20px;
  box-shadow: 0 0 40px hsla(var(--shade-hue), 80%, 55%, 0.08),
              0 0 80px hsla(var(--shade-hue), 80%, 55%, 0.03),
              0 8px 40px rgba(0, 0, 0, 0.25);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);

  opacity: 0;
  transform: scale(0.92);
  pointer-events: none;
  transition: width 0.5s cubic-bezier(0.34, 1.56, 0.64, 1),
              padding 0.5s cubic-bezier(0.34, 1.56, 0.64, 1),
              border 0.5s,
              border-color 0.6s ease,
              box-shadow 0.6s ease,
              opacity 0.5s cubic-bezier(0.34, 1.56, 0.64, 1),
              transform 0.5s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.lamp-on .login-card {
  width: 340px;
  height: 480px;
  max-height: 480px;
  display: flex;
  flex-direction: column;
  padding: 40px 32px;
  border: 1px solid hsla(var(--shade-hue), 80%, 60%, 0.25);
  opacity: 1;
  transform: scale(1);
  pointer-events: auto;
  overflow: hidden;
  box-sizing: border-box;
  transition: width 0.55s cubic-bezier(0.34, 1.56, 0.64, 1),
              padding 0.55s cubic-bezier(0.34, 1.56, 0.64, 1),
              border 0.5s,
              border-color 0.6s ease,
              box-shadow 0.6s ease,
              opacity 0.5s cubic-bezier(0.34, 1.56, 0.64, 1),
              transform 0.55s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.lamp-on .login-card > .login-panel,
.lamp-on .login-card > .register-panel {
  animation: form-in 0.38s cubic-bezier(0.22, 1, 0.36, 1) 0.16s both;
}

.login-panel {
  display: flex;
  flex-direction: column;
}

.register-panel {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.login-card h2 {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 700;
  color: #f5f0eb;
  letter-spacing: 1px;
  flex-shrink: 0;
}

.login-card .sub-text {
  margin: 0 0 28px;
  font-size: 13px;
  color: #8c8076;
  flex-shrink: 0;
}

.register-panel .sub-text {
  margin-bottom: 28px;
}

.register-form {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.register-fields-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding-right: 6px;
  margin-right: -6px;
  padding-bottom: 2px;
}

.register-fields-scroll::-webkit-scrollbar {
  width: 4px;
}

.register-fields-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.register-fields-scroll::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 4px;
}

.register-fields-scroll::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.2);
}

.register-submit-btn {
  margin-top: 16px;
}

.login-card :deep(.el-form-item__label) {
  color: #8c8076;
  font-size: 11px;
  letter-spacing: 1px;
}

.login-card :deep(.el-input__wrapper) {
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: transparent;
  border-radius: 12px;
  height: 46px;
  box-shadow: none !important;
  overflow: hidden;
  transition: border-color 0.6s ease, box-shadow 0.6s ease;
}

.login-card :deep(.el-input__wrapper:hover) {
  background: transparent;
  border-color: rgba(255, 255, 255, 0.14);
}

.login-card :deep(.el-input.is-focus .el-input__wrapper) {
  background: transparent;
  border-color: hsla(var(--shade-hue), 55%, 58%, 0.45);
  box-shadow: 0 0 0 1px hsla(var(--shade-hue), 55%, 58%, 0.2);
}

.login-card :deep(.el-input__inner),
.login-card :deep(.el-input__wrapper input) {
  color: #f5f0eb;
  background: transparent !important;
  box-shadow: none !important;
}

.login-card :deep(.el-input__inner::placeholder) {
  color: var(--color-text-secondary);
}

.submit-btn {
  width: 100%;
  height: 48px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 6px;
  border-radius: 14px;
  border: none;
  background: var(--btn-gradient);
  box-shadow: 0 4px 20px hsla(var(--shade-hue), 70%, 50%, 0.22);
  color: #fff;
  margin-top: 4px;
  transition: background 0.6s ease, box-shadow 0.6s ease, transform 0.3s;
}

.submit-btn:hover {
  background: var(--btn-gradient-hover);
  box-shadow: 0 6px 24px hsla(var(--shade-hue), 70%, 50%, 0.32);
}

.submit-btn:active {
  transform: scale(0.98);
}

.switch-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  margin-top: 20px;
  font-size: 14px;
  color: #8c8076;
}

.extra-row {
  display: flex;
  justify-content: flex-end;
  margin: -8px 0 4px;
}

.password-alert {
  margin-top: 8px;
}

/* 弹窗深色主题 */
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

.login-page :deep(.el-dialog .el-form-item__label) {
  color: #8c8076;
}

.login-page :deep(.el-dialog .el-input__wrapper) {
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: transparent;
  border-radius: 10px;
  box-shadow: none !important;
  overflow: hidden;
}

.login-page :deep(.el-dialog .el-input__inner),
.login-page :deep(.el-dialog .el-input__wrapper input) {
  color: #f5f0eb;
  background: transparent !important;
  box-shadow: none !important;
}

/* 台灯页弹窗 — 弹簧弹出，与登录卡片一致 */
.login-page :deep(.lamp-dialog-enter-active) {
  animation: lamp-overlay-in 0.32s ease;
}

.login-page :deep(.lamp-dialog-enter-active .el-overlay-dialog) {
  animation: lamp-dialog-spring-in 0.48s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.login-page :deep(.lamp-dialog-leave-active) {
  animation: lamp-overlay-out 0.22s ease forwards;
}

.login-page :deep(.lamp-dialog-leave-active .el-overlay-dialog) {
  animation: lamp-dialog-spring-out 0.22s ease-in forwards;
}

@keyframes lamp-overlay-in {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes lamp-overlay-out {
  from { opacity: 1; }
  to { opacity: 0; }
}

@keyframes lamp-dialog-spring-in {
  from {
    opacity: 0;
    transform: scale(0.9) translateY(14px);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

@keyframes lamp-dialog-spring-out {
  from {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
  to {
    opacity: 0;
    transform: scale(0.94) translateY(-10px);
  }
}

/* 登录/注册表单切换动画 */
.form-swap-enter-active {
  animation: form-in 0.35s cubic-bezier(0.22, 1, 0.36, 1);
}

.form-swap-leave-active {
  animation: form-out 0.2s ease-in;
}

@keyframes form-in {
  from {
    opacity: 0;
    transform: translateY(12px) scale(0.97);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@keyframes form-out {
  from {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
  to {
    opacity: 0;
    transform: translateY(-8px) scale(0.97);
  }
}

@media (max-width: 768px) {
  .login-page {
    flex-direction: column;
    gap: 24px;
    padding: calc((100vh - var(--card-height, 480px)) / 2) 20px 32px;
  }

  .lamp-on .login-card {
    width: 100%;
    max-width: 340px;
    height: min(480px, calc(100vh - 120px));
    max-height: min(480px, calc(100vh - 120px));
    padding: 32px 24px;
  }

  .login-page :deep(.el-dialog) {
    width: calc(100vw - 32px) !important;
    max-width: 420px;
  }
}
</style>

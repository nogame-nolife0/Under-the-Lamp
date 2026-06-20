<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { useThemeStore } from '@/stores/theme'
import DeskLamp from '@/components/DeskLamp.vue'

const router = useRouter()
const authStore = useAuthStore()
const themeStore = useThemeStore()
const loading = ref(false)

const isLampOn = ref(true)
const lampHue = ref(themeStore.shadeHue)

function onLampToggle(on) {
  isLampOn.value = on
}

function onLampHue(hue) {
  lampHue.value = hue
  themeStore.setShadeHue(hue)
}

const form = reactive({
  username: '',
  phone: '',
  password: '',
  confirmPassword: '',
})

function validateConfirmPassword(rule, value, callback) {
  if (!value) {
    callback(new Error('请确认密码'))
    return
  }
  if (value !== form.password) {
    callback(new Error('两次输入的密码不一致'))
    return
  }
  callback()
}

const rules = {
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

const formRef = ref(null)

async function handleRegister() {
  try { await formRef.value.validate() } catch { return }
  loading.value = true
  try {
    await authStore.register({ ...form })
    ElMessage.success('注册成功，已自动登录')
    router.replace('/import/upload')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div
    class="register-page"
    :class="{ 'lamp-on': isLampOn }"
    :style="{ '--shade-hue': lampHue }"
  >
    <div class="lamp-wrapper">
      <DeskLamp
        :on="isLampOn"
        :hue="lampHue"
        @update:on="onLampToggle"
        @update:hue="onLampHue"
      />
    </div>

    <div class="register-card" :style="{ '--shade-hue': lampHue }">
      <h2>注册账号</h2>
      <p class="sub-text">创建新账号，开始智能组卷</p>

      <el-form
        ref="formRef"
        class="register-form"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="handleRegister"
      >
        <div class="register-fields-scroll">
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
        </div>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.register-page {
  height: 100%;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  gap: 64px;
  padding-top: calc((100vh - 480px) / 2);
  background: linear-gradient(180deg, #1a1f2e 0%, #1e2435 40%, #1a2030 100%);
  overflow: hidden;
  flex-wrap: wrap;
}

.lamp-wrapper {
  flex-shrink: 0;
}

.register-card {
  width: 340px;
  height: 480px;
  max-height: 480px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid hsla(var(--shade-hue), 80%, 60%, 0.25);
  border-radius: 20px;
  padding: 40px 32px;
  box-shadow: 0 0 40px hsla(var(--shade-hue), 80%, 55%, 0.08),
              0 0 80px hsla(var(--shade-hue), 80%, 55%, 0.03),
              0 8px 40px rgba(0, 0, 0, 0.25);
  backdrop-filter: blur(24px);
  -webkit-backdrop-filter: blur(24px);
  flex-shrink: 0;
  transition: border-color 0.6s ease, box-shadow 0.6s ease;
}

.register-card h2 {
  margin: 0 0 6px;
  font-size: 24px;
  font-weight: 700;
  color: #f5f0eb;
  letter-spacing: 1px;
  flex-shrink: 0;
}

.register-card .sub-text {
  margin: 0 0 28px;
  font-size: 13px;
  color: #8c8076;
  flex-shrink: 0;
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

.register-card :deep(.el-form-item__label) {
  color: #8c8076;
  font-size: 11px;
  letter-spacing: 1px;
}

.register-card :deep(.el-input__wrapper) {
  border: 1px solid rgba(255, 255, 255, 0.1);
  background: transparent;
  border-radius: 12px;
  height: 46px;
  box-shadow: none !important;
  overflow: hidden;
  transition: border-color 0.6s ease, box-shadow 0.6s ease;
}

.register-card :deep(.el-input__wrapper:hover) {
  background: transparent;
  border-color: rgba(255, 255, 255, 0.14);
}

.register-card :deep(.el-input.is-focus .el-input__wrapper) {
  background: transparent;
  border-color: hsla(var(--shade-hue), 55%, 58%, 0.45);
  box-shadow: 0 0 0 1px hsla(var(--shade-hue), 55%, 58%, 0.2);
}

.register-card :deep(.el-input__inner),
.register-card :deep(.el-input__wrapper input) {
  color: #f5f0eb;
  background: transparent !important;
  box-shadow: none !important;
}

.register-card :deep(.el-input__inner::placeholder) {
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
  margin-top: 16px;
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

@media (max-width: 768px) {
  .register-page {
    flex-direction: column;
    gap: 24px;
    padding: calc((100vh - 480px) / 2) 20px 32px;
  }

  .register-card {
    width: 100%;
    max-width: 340px;
    height: min(480px, calc(100vh - 120px));
    max-height: min(480px, calc(100vh - 120px));
    padding: 32px 24px;
  }
}
</style>

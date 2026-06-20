<script setup>

import { computed, onActivated, onMounted, ref } from 'vue'

import { useRouter } from 'vue-router'

import { ElMessage } from 'element-plus'

import { UploadFilled } from '@element-plus/icons-vue'

import { listCourseNames, pageQuestions } from '@/api/question'

import { useImportStore } from '@/stores/import'



defineOptions({ name: 'ImportUpload' })



const MAX_STEM_FILES = 5

const MAX_ANSWER_FILES = 5



const router = useRouter()

const importStore = useImportStore()



const stemFiles = ref([])

const answerFiles = ref([])

const courseForm = ref({ subject: '' })

const bankStats = ref({ total: 0, courseCount: 0 })

const statsLoading = ref(false)



const uploading = computed(() => importStore.uploading)

const hasPendingConfirm = computed(() => !!importStore.lastBatchUuid)



async function loadBankStats() {

  statsLoading.value = true

  try {

    const [pageRes, subjects] = await Promise.all([

      pageQuestions({ pageNum: 1, pageSize: 1 }),

      listCourseNames(),

    ])

    bankStats.value = {

      total: pageRes.total || 0,

      courseCount: (subjects || []).length,

    }

  } finally {

    statsLoading.value = false

  }

}



function validateDocx(file, label) {

  const isDocx = file.name.toLowerCase().endsWith('.docx')

  if (!isDocx) {

    ElMessage.error(`${label}仅支持 .docx 格式，PDF 功能二期开发`)

    return false

  }

  const isLt50M = file.size / 1024 / 1024 < 50

  if (!isLt50M) {

    ElMessage.error(`${label}大小不能超过 50MB`)

    return false

  }

  return true

}



function beforeStemUpload(file) {

  if (!validateDocx(file, '试卷文件')) return false

  return false

}



function beforeAnswerUpload(file) {

  if (!validateDocx(file, '答案文件')) return false

  return false

}



function syncStemFiles(uploadFiles) {

  stemFiles.value = (uploadFiles || []).map((item) => item?.raw).filter(Boolean)

}



function syncAnswerFiles(uploadFiles) {

  answerFiles.value = (uploadFiles || []).map((item) => item?.raw).filter(Boolean)

}



function handleStemChange(_uploadFile, uploadFiles) {

  syncStemFiles(uploadFiles)

}



function handleStemRemove(_uploadFile, uploadFiles) {

  syncStemFiles(uploadFiles)

}



function handleAnswerChange(_uploadFile, uploadFiles) {

  syncAnswerFiles(uploadFiles)

}



function handleAnswerRemove(_uploadFile, uploadFiles) {

  syncAnswerFiles(uploadFiles)

}



async function handleUpload() {

  if (!courseForm.value.subject?.trim()) {

    ElMessage.warning('请填写课程名称')

    return

  }

  if (stemFiles.value.length === 0) {

    ElMessage.warning('请先选择至少一份试卷 Word 文件（题干卷）')

    return

  }

  try {

    const data = await importStore.startUpload(

      stemFiles.value,

      answerFiles.value,

      courseForm.value.subject.trim()

    )

    ElMessage.success(importStore.uploadMessage)

    router.push({

      name: 'import-confirm',

      params: { batchUuid: data.batchUuid },

    })

  } catch {

    // 错误提示由 request 拦截器或 store 处理

  }

}



function goToConfirm() {

  if (!importStore.lastBatchUuid) return

  router.push({

    name: 'import-confirm',

    params: { batchUuid: importStore.lastBatchUuid },

  })

}



onMounted(loadBankStats)

onActivated(loadBankStats)

</script>



<template>

  <div class="upload-page page-scroll">

    <el-row :gutter="20">

      <el-col :span="14">

        <el-card shadow="never" class="main-card card-enter" style="--i: 0">

          <template #header>

            <div class="card-header">

              <span class="card-title">📤 Word 题库导入</span>

              <el-tag type="info" effect="plain">一套题，可多文件同时上传</el-tag>

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

                placeholder="如：数字电子技术、大学物理"

                maxlength="64"

                show-word-limit

                :disabled="uploading"

                size="large"

              />

            </el-form-item>



            <el-form-item label="试卷文件" required>

              <el-upload

                drag

                multiple

                :auto-upload="false"

                :show-file-list="true"

                :limit="MAX_STEM_FILES"

                accept=".docx"

                :disabled="uploading"

                :before-upload="beforeStemUpload"

                :on-change="handleStemChange"

                :on-remove="handleStemRemove"

                :on-exceed="() => ElMessage.warning(`最多上传 ${MAX_STEM_FILES} 份试卷文件`)"

                class="upload-zone"

                :class="{ 'has-file': stemFiles.length > 0 }"

              >

                <el-icon class="upload-icon"><UploadFilled /></el-icon>

                <div class="el-upload__text">

                  将试卷（题干卷）拖到此处，或 <em>点击选择</em>

                </div>

                <template #tip>

                  <div class="el-upload__tip">

                    必填，可上传多份（如按章节拆分的题目文档），合并为一个导入批次

                  </div>

                </template>

              </el-upload>

            </el-form-item>



            <el-form-item label="答案文件">

              <el-upload

                drag

                multiple

                :auto-upload="false"

                :show-file-list="true"

                :limit="MAX_ANSWER_FILES"

                accept=".docx"

                :disabled="uploading"

                :before-upload="beforeAnswerUpload"

                :on-change="handleAnswerChange"

                :on-remove="handleAnswerRemove"

                :on-exceed="() => ElMessage.warning(`最多上传 ${MAX_ANSWER_FILES} 份答案文件`)"

                class="upload-zone"

                :class="{ 'has-file': answerFiles.length > 0 }"

              >

                <el-icon class="upload-icon"><UploadFilled /></el-icon>

                <div class="el-upload__text">

                  将答案卷拖到此处，或 <em>点击选择</em>

                </div>

                <template #tip>

                  <div class="el-upload__tip">

                    选填，可上传多份（如答案拆成多个文档）；系统按题号自动合并匹配

                  </div>

                </template>

              </el-upload>

            </el-form-item>



            <el-form-item>

              <el-button

                type="primary"

                class="upload-submit-btn"

                :loading="uploading"

                size="large"

                @click="handleUpload"

              >

                {{ uploading ? '解析中…' : '开始解析' }}

              </el-button>

            </el-form-item>

          </el-form>

        </el-card>

      </el-col>



      <el-col :span="10">

        <div class="side-panels">

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



          <el-card shadow="never" class="info-card card-enter" style="--i: 1">

            <template #header>

              <span class="info-card-title">💡 导入说明</span>

            </template>

            <ul class="info-list">

              <li>支持 <strong>.docx</strong> 格式的 Word 文档</li>

              <li>一次导入 <strong>一套题</strong>，试卷和答案均可选多份文件</li>

              <li>多份试卷按选择顺序合并题号，多份答案按题号自动合并</li>

              <li>嵌入图片自动提取并关联题目</li>

              <li>解析可在后台进行，不阻塞其他操作</li>

              <li>解析完成后前往「导入确认」逐题审核</li>

            </ul>

          </el-card>



          <el-card shadow="never" class="info-card card-enter" style="--i: 2" v-loading="statsLoading">

            <template #header>

              <span class="info-card-title">📊 题库概览</span>

            </template>

            <div class="stat-row">

              <div class="stat-item">

                <span class="stat-num">{{ bankStats.total }}</span>

                <span class="stat-label">已入库题目</span>

              </div>

              <div class="stat-item">

                <span class="stat-num">{{ bankStats.courseCount }}</span>

                <span class="stat-label">课程数</span>

              </div>

            </div>

          </el-card>

        </div>

      </el-col>

    </el-row>

  </div>

</template>



<style scoped>
.main-card {
  min-height: 520px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.card-title {
  font-family: var(--font-display);
  font-size: 17px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.tip-block {
  margin-bottom: 20px;
}

.upload-submit-btn {
  width: 100%;
  height: 46px;
  font-size: 15px;
  border-radius: 12px;
  letter-spacing: 3px;
}

.upload-zone :deep(.el-upload-dragger) {
  padding: 32px 24px;
  border-radius: var(--radius-lg);
}

.upload-zone.has-file :deep(.el-upload-dragger) {
  border-color: hsl(var(--shade-hue), 60%, 55%);
  border-style: solid;
  background: var(--color-primary-light);
  animation: pulse-border 2s ease-in-out infinite;
}

.upload-icon {
  font-size: 46px;
  color: hsl(var(--shade-hue), 65%, 58%);
  margin-bottom: 10px;
}

.upload-zone :deep(.el-upload__text em) {
  color: hsl(var(--shade-hue), 65%, 62%);
}

.side-panels {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.info-card-title {
  font-family: var(--font-display);
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.pending-card {
  background: var(--color-primary-light);
  border: 1px solid var(--color-primary-border);
  border-left: 4px solid var(--color-primary);
  border-radius: var(--radius-card);
  padding: 20px 22px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 14px;
  box-shadow: var(--shadow-card);
  transition: all var(--transition);
}

.pending-card:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-2px);
}

.info-card-icon {
  font-size: 32px;
}

.info-card-body {
  flex: 1;
  min-width: 0;
}

.info-card-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.info-card-value {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 2px 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.info-card-meta {
  font-size: 12px;
  color: hsl(var(--shade-hue), 65%, 62%);
  font-weight: 500;
}

.info-list {
  margin: 0;
  padding: 0 0 0 16px;
  color: var(--color-text-secondary);
  font-size: var(--font-size-sm);
  line-height: 2;
}

.stat-row {
  display: flex;
  gap: 28px;
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-num {
  font-family: var(--font-display);
  font-size: 30px;
  font-weight: 700;
  color: hsl(var(--shade-hue), 65%, 60%);
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}
</style>



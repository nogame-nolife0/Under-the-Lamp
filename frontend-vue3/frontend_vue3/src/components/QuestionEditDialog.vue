<script setup>
import { computed, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import RichContent from '@/components/RichContent.vue'
import { buildImageMap } from '@/utils/imageAssets'
import { getQuestion, updateQuestion, uploadQuestionImage } from '@/api/question'
import { useCourseOptions } from '@/composables/useCourseOptions'

const props = defineProps({
  modelValue: {
    type: Boolean,
    default: false,
  },
  questionId: {
    type: Number,
    default: null,
  },
})

const emit = defineEmits(['update:modelValue', 'saved'])

const { courseOptions } = useCourseOptions()

const visible = computed({
  get: () => props.modelValue,
  set: (value) => emit('update:modelValue', value),
})

const loading = ref(false)
const saving = ref(false)
const uploading = ref(false)
const fileInputRef = ref(null)
const pendingImage = ref(null)

const form = reactive({
  stem: '',
  answer: '',
  analysis: '',
  optionsText: '',
  questionType: '',
  difficulty: '',
  subject: '',
  chapter: '',
  images: [],
})

const questionTypeOptions = [
  { label: '单选题', value: 'SINGLE_CHOICE' },
  { label: '多选题', value: 'MULTI_CHOICE' },
  { label: '判断题', value: 'TRUE_FALSE' },
  { label: '填空题', value: 'FILL_BLANK' },
  { label: '简答题', value: 'SHORT_ANSWER' },
  { label: '计算题', value: 'CALCULATION' },
  { label: '论述题', value: 'ESSAY' },
]

const difficultyOptions = [
  { label: '简单', value: 'EASY' },
  { label: '中等', value: 'MEDIUM' },
  { label: '困难', value: 'HARD' },
]

const stemImageMap = computed(() => buildImageMap(form.images, 'STEM'))
const answerImageMap = computed(() => buildImageMap(form.images, 'ANSWER'))

watch(
  () => [props.modelValue, props.questionId],
  async ([open, id]) => {
    if (open && id) {
      await loadQuestion(id)
    }
  }
)

async function loadQuestion(id) {
  loading.value = true
  try {
    const data = await getQuestion(id)
    form.stem = data.stem || ''
    form.answer = data.answer || ''
    form.analysis = data.analysis || ''
    form.optionsText = (data.options || []).join('\n')
    form.questionType = data.questionType || ''
    form.difficulty = data.difficulty || ''
    form.subject = data.subject || ''
    form.chapter = data.chapter || ''
    form.images = [...(data.images || [])]
  } finally {
    loading.value = false
  }
}

function handleImageClick(payload) {
  pendingImage.value = payload
  fileInputRef.value?.click()
}

async function handleFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file || !pendingImage.value || !props.questionId) return

  const allowed = ['image/png', 'image/jpeg', 'image/jpg', 'image/webp', 'image/gif']
  if (!allowed.includes(file.type)) {
    ElMessage.warning('仅支持 png/jpg/jpeg/webp/gif 图片')
    return
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('图片大小不能超过 5MB')
    return
  }

  uploading.value = true
  try {
    const { index, scope } = pendingImage.value
    const result = await uploadQuestionImage(props.questionId, scope, index, file)
    const baseUrl = (result.url || '').split('?')[0]
    const cacheUrl = `${baseUrl}?t=${Date.now()}`
    const existing = form.images.find(
      (img) => img.index === index && String(img.scope || 'STEM').toUpperCase() === scope
    )
    if (existing) {
      existing.url = cacheUrl
    } else {
      form.images.push({
        index,
        scope,
        url: cacheUrl,
        placeholder: result.placeholder,
      })
    }
    ElMessage.success('图片已替换')
  } finally {
    uploading.value = false
    pendingImage.value = null
  }
}

async function handleSave() {
  if (!props.questionId) return
  if (!form.stem.trim()) {
    ElMessage.warning('题干不能为空')
    return
  }

  saving.value = true
  try {
    const options = form.optionsText
      ? form.optionsText.split('\n').map((line) => line.trim()).filter(Boolean)
      : []
    await updateQuestion(props.questionId, {
      stem: form.stem.trim(),
      answer: form.answer?.trim() || '',
      analysis: form.analysis?.trim() || undefined,
      options,
      questionType: form.questionType || undefined,
      difficulty: form.difficulty || undefined,
      subject: form.subject?.trim() || undefined,
      chapter: form.chapter?.trim() || undefined,
    })
    ElMessage.success('题目已保存，向量库将后台重新同步')
    emit('saved')
    visible.value = false
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog
    v-model="visible"
    title="编辑题目"
    width="900px"
    top="56px"
    append-to-body
    lock-scroll
    destroy-on-close
    class="question-edit-dialog"
    modal-class="question-edit-overlay"
  >
    <div v-loading="loading || uploading">
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="tip-alert"
        title="题干/答案中的图片可点击，选择本地文件即可替换。保存后向量库会自动重新同步。"
      />

      <el-form label-width="80px">
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="课程">
              <el-select
                v-model="form.subject"
                filterable
                allow-create
                default-first-option
                placeholder="选择或输入课程"
                style="width: 100%"
              >
                <el-option v-for="name in courseOptions" :key="name" :label="name" :value="name" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="章节">
              <el-input v-model="form.chapter" placeholder="章节或知识点" />
            </el-form-item>
          </el-col>
        </el-row>
        <el-row :gutter="12">
          <el-col :span="12">
            <el-form-item label="题型">
              <el-select v-model="form.questionType" clearable style="width: 100%">
                <el-option
                  v-for="item in questionTypeOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="难度">
              <el-select v-model="form.difficulty" clearable style="width: 100%">
                <el-option
                  v-for="item in difficultyOptions"
                  :key="item.value"
                  :label="item.label"
                  :value="item.value"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="题干" required>
          <div class="preview-panel">
            <RichContent
              :content="form.stem"
              :image-map="stemImageMap"
              editable
              image-scope="STEM"
              @image-click="handleImageClick"
            />
          </div>
          <el-input
            v-model="form.stem"
            type="textarea"
            :rows="5"
            placeholder="支持文本、公式 $...$ 与图片占位符 [嵌入图片1]"
          />
        </el-form-item>

        <el-form-item label="选项">
          <el-input
            v-model="form.optionsText"
            type="textarea"
            :rows="4"
            placeholder="每行一个选项，如 A. xxx"
          />
        </el-form-item>

        <el-form-item label="答案">
          <div class="preview-panel answer-panel">
            <RichContent
              :content="form.answer"
              :image-map="answerImageMap"
              editable
              image-scope="ANSWER"
              empty-text="暂无答案"
              @image-click="handleImageClick"
            />
          </div>
          <el-input v-model="form.answer" type="textarea" :rows="4" />
        </el-form-item>

        <el-form-item label="解析">
          <el-input v-model="form.analysis" type="textarea" :rows="3" />
        </el-form-item>
      </el-form>
    </div>

    <input
      ref="fileInputRef"
      type="file"
      accept="image/png,image/jpeg,image/jpg,image/webp,image/gif"
      class="hidden-file-input"
      @change="handleFileChange"
    />

    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.tip-alert {
  margin-bottom: 16px;
}

.preview-panel {
  margin-bottom: 8px;
  padding: 14px 16px;
  background: var(--color-bg-glass);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.preview-panel.answer-panel {
  background: var(--color-success-light);
  border-color: rgba(110, 201, 160, 0.22);
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

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import RichContent from '@/components/RichContent.vue'
import { buildImageMap } from '@/utils/imageAssets'
import { pickRicherImportText, resolveImportPreviewText } from '@/utils/importPreview'
import { useMainScrollLock } from '@/composables/useMainScrollLock'
import { useImportStore } from '@/stores/import'
import {
  acceptImportItem,
  confirmBatch,
  getBatch,
  getBatchItems,
  rejectImportItem,
  updateImportBatch,
  updateImportItem,
} from '@/api/import'

defineOptions({ name: 'ImportConfirm' })

const route = useRoute()
const router = useRouter()
const importStore = useImportStore()

const batchUuid = computed(() => route.params.batchUuid)
const loading = ref(false)
const confirming = ref(false)
const batchInfo = ref(null)
const items = ref([])
const currentItem = ref(null)
const showStemEdit = ref(false)
const showAnswerEdit = ref(false)
const showOptionsEdit = ref(false)
const localFilter = ref('')
const activeCollapse = ref(['stem', 'options', 'answer'])

const statusFilter = ref('')
const batchCourse = ref({ subject: '' })
const savingCourse = ref(false)
const detailVisible = ref(false)

useMainScrollLock(detailVisible, {
  dialogRootSelector: '.import-detail-overlay',
})

const filteredItems = computed(() => {
  if (!localFilter.value) return items.value
  const kw = localFilter.value.toLowerCase()
  return items.value.filter(
    (item) =>
      String(item.chapter || '').toLowerCase().includes(kw) ||
      String(item.stemRaw || '').toLowerCase().includes(kw)
  )
})

const previewStem = computed(() => {
  const item = currentItem.value
  if (!item) return ''
  return resolveImportPreviewText(item.stemHtml, item.stemRaw, item.status)
})
const previewAnswer = computed(() => {
  const item = currentItem.value
  if (!item) return ''
  return resolveImportPreviewText(item.answerHtml, item.answerRaw, item.status)
})
const previewOptions = computed(() => {
  if (!currentItem.value) return []
  if (currentItem.value.optionsText) {
    return currentItem.value.optionsText
      .split('\n')
      .map((line) => line.trim())
      .filter(Boolean)
  }
  return currentItem.value.options || []
})
const markerContent = (...parts) => parts.filter(Boolean).join('\n')
const stemImageMap = computed(() => {
  const item = currentItem.value
  if (!item) return {}
  return buildImageMap(item.images, 'STEM', {
    batchUuid: batchUuid.value,
    content: markerContent(
      item.stemHtml,
      item.stemRaw,
      previewStem.value,
      item.optionsText,
      ...(item.options || [])
    ),
  })
})
const answerImageMap = computed(() =>
  buildImageMap(currentItem.value?.images, 'ANSWER', {
    batchUuid: batchUuid.value,
    content: previewAnswer.value,
  })
)

const pendingCount = computed(
  () => items.value.filter((item) => isPendingItem(item)).length
)

onMounted(() => loadData())
watch(batchUuid, () => loadData())

function isPendingItem(item) {
  if (item.questionId) return false
  if (item.status === 'REJECTED' || item.status === 'ACCEPTED') return false
  return true
}

function isLowConfidence(item) {
  if (item.questionId || item.status === 'REJECTED') return false
  const score = item.confidenceScore
  if (score != null && Number(score) < 0.7) {
    return true
  }
  if (batchInfo.value?.importMode === 'SEPARATE') {
    const answer = item.answerRaw || item.answerHtml
    return !answer || !String(answer).trim()
  }
  return false
}

function rowClassName({ row }) {
  return row.status === 'REJECTED' ? 'row-rejected' : ''
}

function stemBrief(text) {
  if (!text) return '-'
  return text.replace(/\[嵌入图片\d+\]/g, '[图]').slice(0, 60)
}

async function loadData() {
  if (!batchUuid.value) return
  loading.value = true
  try {
    const [batch, list] = await Promise.all([
      getBatch(batchUuid.value),
      getBatchItems(batchUuid.value, statusFilter.value || undefined),
    ])
    batchInfo.value = batch
    batchCourse.value = {
      subject: batch.subject || '',
    }
    items.value = list || []
    const allProcessed =
      items.value.length > 0 && items.value.every((item) => !isPendingItem(item))
    if (batch.status === 'CONFIRMED' || allProcessed) {
      importStore.clearLastBatch()
    } else {
      importStore.lastBatchUuid = batchUuid.value
      importStore.lastFileName = batch.fileName
      importStore.lastTotalCount = batch.totalCount || 0
    }
    if (currentItem.value) {
      const refreshed = items.value.find((i) => i.id === currentItem.value.id)
      if (refreshed) {
        selectItem(refreshed)
      } else {
        currentItem.value = null
        detailVisible.value = false
      }
    }
  } finally {
    loading.value = false
  }
}

function selectItem(item) {
  showStemEdit.value = false
  showAnswerEdit.value = false
  showOptionsEdit.value = false
  currentItem.value = {
    ...item,
    stemHtml:
      item.status === 'EDITED'
        ? item.stemHtml || item.stemRaw || ''
        : pickRicherImportText(item.stemRaw, item.stemHtml),
    answerHtml:
      item.status === 'EDITED'
        ? item.answerHtml || item.answerRaw || ''
        : pickRicherImportText(item.answerRaw, item.answerHtml),
    optionsText: (item.options || []).join('\n'),
  }
}

function openDetail(row) {
  selectItem(row)
  detailVisible.value = true
}

async function handleAccept(item) {
  await acceptImportItem(item.id)
  ElMessage.success('已接受并写入题库')
  await loadData()
}

async function handleReject(item) {
  await rejectImportItem(item.id)
  ElMessage.success('已拒绝')
  await loadData()
}

async function handleSaveEdit() {
  if (!currentItem.value) return
  const options = currentItem.value.optionsText
    ? currentItem.value.optionsText.split('\n').map((s) => s.trim()).filter(Boolean)
    : []
  await updateImportItem(currentItem.value.id, {
    stemHtml: currentItem.value.stemHtml || currentItem.value.stemRaw,
    answerHtml: currentItem.value.answerHtml || currentItem.value.answerRaw,
    analysisHtml: currentItem.value.analysisHtml,
    options,
    questionType: currentItem.value.questionType,
    difficulty: currentItem.value.difficulty,
    chapter: currentItem.value.chapter,
  })
  ElMessage.success('保存成功')
  await loadData()
}

async function handleSaveCourse() {
  if (!batchCourse.value.subject?.trim()) {
    ElMessage.warning('请填写课程名称')
    return
  }
  savingCourse.value = true
  try {
    await updateImportBatch(batchUuid.value, {
      subject: batchCourse.value.subject.trim(),
    })
    ElMessage.success('课程信息已更新，本批次题目将归入该课程')
    await loadData()
  } finally {
    savingCourse.value = false
  }
}

async function handleConfirmAll() {
  await ElMessageBox.confirm(
    '将把已接受/已编辑及高置信度题目写入正式题库，是否继续？',
    '确认入库',
    { type: 'warning' }
  )
  confirming.value = true
  try {
    const result = await confirmBatch(batchUuid.value)
    if (result.importedCount > 0) {
      ElMessage.success(`入库完成：${result.importedCount} 题，跳过 ${result.skippedCount} 题`)
      importStore.clearLastBatch()
      router.push('/questions')
    } else {
      ElMessage.warning(
        result.skippedCount > 0
          ? `没有题目入库，${result.skippedCount} 题被跳过（需先逐题点「接受」或补充答案）`
          : '没有题目需要入库'
      )
    }
  } finally {
    confirming.value = false
  }
}
</script>

<template>
  <div v-loading="loading" class="confirm-page page-scroll">
    <div class="summary-bar card-enter" style="--i: 0">
      <div class="summary-left">
        <div class="summary-icon">📋</div>
        <div class="summary-info">
          <div class="summary-title">{{ batchInfo?.fileName || '导入批次' }}</div>
          <div class="summary-meta">
            <span>共 <strong>{{ batchInfo?.totalCount || 0 }}</strong> 题</span>
            <span class="meta-sep">·</span>
            <span>待确认 <strong>{{ pendingCount }}</strong> 题</span>
            <span class="meta-sep">·</span>
            <span>已入库 <strong>{{ items.filter((i) => i.questionId).length }}</strong> 题</span>
          </div>
        </div>
      </div>
      <div class="summary-right">
        <el-input
          v-model="batchCourse.subject"
          placeholder="课程名称"
          class="course-input"
          @keyup.enter="handleSaveCourse"
        />
        <el-button type="primary" plain :loading="savingCourse" @click="handleSaveCourse">
          保存课程
        </el-button>
        <el-button @click="router.push('/questions')">查看题库</el-button>
        <el-button type="primary" :loading="confirming" @click="handleConfirmAll">全部入库</el-button>
      </div>
    </div>

    <el-card shadow="never" class="list-card card-enter" style="--i: 1">
      <template #header>
        <div class="list-header">
          <span>候选题目 ({{ items.length }})</span>
          <el-input
            v-model="localFilter"
            placeholder="搜索题号或题干..."
            clearable
            size="small"
            style="width: 240px"
          />
        </div>
      </template>
      <el-table
        :data="filteredItems"
        highlight-current-row
        :row-class-name="rowClassName"
        @row-click="openDetail"
      >
        <el-table-column prop="seqNo" label="#" width="50" />
        <el-table-column prop="chapter" label="题号" width="80" />
        <el-table-column label="题干" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="stem-cell">{{ stemBrief(pickRicherImportText(row.stemRaw, row.stemHtml)) }}</div>
          </template>
        </el-table-column>
        <el-table-column label="置信度" width="90">
          <template #default="{ row }">
            <span class="confidence-dot" :class="isLowConfidence(row) ? 'low' : 'high'"></span>
            {{ row.confidenceScore ?? '-' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" align="center">
          <template #default="{ row }">
            <div class="action-cell">
              <el-button link type="primary" size="small" @click.stop="openDetail(row)">详情</el-button>
              <template v-if="row.status === 'REJECTED'">
                <span class="done-reject" title="已拒绝">✗</span>
              </template>
              <template v-else-if="!row.questionId">
                <el-button link type="primary" size="small" @click.stop="handleAccept(row)">接受</el-button>
                <el-button link type="danger" size="small" @click.stop="handleReject(row)">拒绝</el-button>
              </template>
              <span v-else class="done-check" title="已入库">✓</span>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog
      v-model="detailVisible"
      title="题目详情 / 编辑"
      width="900px"
      top="56px"
      append-to-body
      lock-scroll
      destroy-on-close
      class="question-detail-dialog"
      modal-class="import-detail-overlay question-detail-overlay"
    >
      <template v-if="currentItem">
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

          <el-collapse-item title="选项" name="options">
            <div v-if="previewOptions.length" class="options-preview-list">
              <div
                v-for="(opt, idx) in previewOptions"
                :key="idx"
                class="preview-box option-preview"
              >
                <RichContent :content="opt" :image-map="stemImageMap" />
              </div>
            </div>
            <div v-else class="preview-empty">暂无选项</div>
            <el-button link type="primary" @click="showOptionsEdit = !showOptionsEdit" class="edit-toggle">
              {{ showOptionsEdit ? '收起编辑' : '编辑文本' }}
            </el-button>
            <el-input
              v-if="showOptionsEdit"
              v-model="currentItem.optionsText"
              type="textarea"
              :rows="4"
              class="edit-area"
              placeholder="每行一个选项，如 A. xxx"
            />
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
      </template>

      <template v-if="currentItem" #footer>
        <div class="edit-actions">
          <el-button @click="detailVisible = false">关闭</el-button>
          <el-button type="primary" @click="handleSaveEdit">保存修改</el-button>
          <template v-if="currentItem.status === 'REJECTED'">
            <el-tag type="info" effect="plain">已拒绝此题</el-tag>
          </template>
          <template v-else-if="!currentItem.questionId">
            <el-button @click="handleAccept(currentItem)">接受并入库</el-button>
            <el-button type="danger" plain @click="handleReject(currentItem)">拒绝此题</el-button>
          </template>
          <el-button v-else link type="success" @click="router.push('/questions')">已在题库，去查看</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.summary-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  padding: 20px 28px;
  margin-bottom: 20px;
  border-radius: var(--radius-card);
  background: var(--color-bg-card);
  border: 1px solid var(--color-border);
  box-shadow: var(--shadow-card);
  backdrop-filter: blur(16px);
}

.summary-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.summary-icon {
  font-size: 34px;
  filter: drop-shadow(0 0 12px var(--color-primary-glow));
}

.summary-title {
  font-family: var(--font-display);
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.summary-meta {
  margin-top: 6px;
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
}

.summary-meta strong {
  color: var(--color-primary);
}

.meta-sep {
  margin: 0 6px;
  color: var(--color-text-placeholder);
}

.summary-right {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 8px;
}

.course-input {
  width: 180px;
}

.action-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 2px;
  min-height: 24px;
  width: 100%;
}

.list-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  color: var(--color-text-primary);
}

.stem-cell {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
  color: var(--color-text-primary);
}

.done-check { color: var(--color-success); font-weight: 600; font-size: 16px; }
.done-reject { color: var(--color-text-placeholder); font-weight: 600; font-size: 16px; }

:deep(.row-rejected) {
  opacity: 0.45;
}

:deep(.row-rejected .stem-cell) {
  text-decoration: line-through;
  color: var(--color-text-secondary);
}

.list-card :deep(.el-card__header) {
  padding: 18px 24px;
}

.list-card :deep(.el-card__body) {
  padding: 0 24px 24px;
}

.preview-box {
  padding: 16px 18px;
  background: var(--color-bg-glass);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  min-height: 60px;
}

.preview-box.answer-preview {
  background: var(--color-success-light);
  border-color: rgba(110, 201, 160, 0.22);
}

.options-preview-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.preview-box.option-preview {
  min-height: 40px;
  max-height: 160px;
  overflow-y: auto;
}

.preview-empty {
  padding: 12px 0;
  color: var(--color-text-placeholder);
  font-size: var(--font-size-sm);
}

.edit-toggle { margin: 10px 0 6px; }
.edit-area { margin-bottom: 10px; }
.meta-row { margin-top: 18px; }
.warn-alert { margin-bottom: 16px; }

.edit-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
  width: 100%;
}
</style>

<script setup>
import { computed, onActivated, onDeactivated, ref } from 'vue'
import { storeToRefs } from 'pinia'

defineOptions({ name: 'PaperSmartCompose' })
import { ElMessage } from 'element-plus'
import RichContent from '@/components/RichContent.vue'
import { buildImageMapForQuestion } from '@/utils/imageAssets'
import { sumScores, formatScore } from '@/utils/score'
import { useMainScrollLock } from '@/composables/useMainScrollLock'
import { useTableDragScroll } from '@/composables/useTableDragScroll'
import { createPaper, exportPaper, smartAlternatives, smartPreviewPaper } from '@/api/paper'
import { syncQuestionEmbed } from '@/api/question'
import { useSmartComposeStore } from '@/stores/smartCompose'

const smartComposeStore = useSmartComposeStore()
const {
  form,
  previewQuestions,
  composeCondition,
  previewMessage,
  paperId,
  activePreviewQuestionId,
} = storeToRefs(smartComposeStore)

const searching = ref(false)
const confirming = ref(false)
const syncing = ref(false)
const exporting = ref(false)

const previewQuestion = computed(() => {
  if (!activePreviewQuestionId.value) return null
  return previewQuestions.value.find((item) => item.questionId === activePreviewQuestionId.value) || null
})

const previewVisible = ref(false)
const previewDetailVisible = ref(false)
const previewTableRef = ref(null)
const swapVisible = ref(false)
const savedOverlayState = ref({ preview: false, detail: false, swap: false })
const swapLoading = ref(false)
const swapTarget = ref(null)
const swapAlternatives = ref([])

const anyOverlayOpen = computed(() => previewVisible.value || previewDetailVisible.value || swapVisible.value)
useMainScrollLock(anyOverlayOpen, {
  dialogRootSelector: '.smart-preview-overlay, .smart-question-detail-overlay, .smart-swap-overlay',
})

useTableDragScroll(previewTableRef)

const queryExamples = [
  '数字电子技术 5道单选题 3道计算题 2道简答题',
  '微机原理 存储器与总线 简答题5道，偏难',
  'PLC编程 梯形图基础 10道判断题',
]

const questionTypeOptions = [
  { label: '单选题', value: 'SINGLE_CHOICE' },
  { label: '多选题', value: 'MULTI_CHOICE' },
  { label: '判断题', value: 'TRUE_FALSE' },
  { label: '填空题', value: 'FILL_BLANK' },
  { label: '简答题', value: 'SHORT_ANSWER' },
  { label: '计算题', value: 'CALCULATION' },
  { label: '论述题', value: 'ESSAY' },
]

const hasPreview = computed(() => previewQuestions.value.length > 0)
const totalPreviewScore = computed(() => sumScores(previewQuestions.value))
const previewStemImageMap = computed(() =>
  buildImageMapForQuestion(previewQuestion.value, 'STEM')
)
const previewAnswerImageMap = computed(() =>
  buildImageMapForQuestion(previewQuestion.value, 'ANSWER')
)

function stemImageMapFor(question) {
  return buildImageMapForQuestion(question, 'STEM')
}

function questionTypeLabel(type) {
  return questionTypeOptions.find((o) => o.value === type)?.label || type || '-'
}

function formatTypeCounts(typeCounts) {
  if (!typeCounts || typeof typeCounts !== 'object') return '-'
  const entries = Object.entries(typeCounts)
  if (entries.length === 0) return '-'
  return entries.map(([type, count]) => `${questionTypeLabel(type)} ${count} 题`).join(' + ')
}

function applyExample(text) { form.value.query = text }

function renumberPreview() {
  previewQuestions.value.forEach((item, index) => { item.sortOrder = index + 1 })
}

function handlePreviewRow(row) {
  smartComposeStore.setActivePreviewQuestionId(row?.questionId ?? null)
  previewDetailVisible.value = true
}

async function handleSyncEmbed() {
  syncing.value = true
  try { const res = await syncQuestionEmbed(50); ElMessage.success(res.message || '向量同步完成') }
  finally { syncing.value = false }
}

async function handleSearch() {
  if (!form.value.query.trim()) { ElMessage.warning('请描述组卷需求'); return }
  searching.value = true
  try {
    const res = await smartPreviewPaper({
      query: form.value.query.trim(),
      subject: form.value.subject || undefined,
      totalScore: form.value.totalScore || undefined,
    })
    smartComposeStore.setPreviewResult({
      questions: res.questions,
      composeCondition: res.composeCondition,
      message: res.message,
    })
    if (!form.value.subject && composeCondition.value.subject) form.value.subject = composeCondition.value.subject
    if (previewQuestions.value.length) previewVisible.value = true
    ElMessage.success(previewMessage.value || `已匹配 ${previewQuestions.value.length} 题，请预览确认`)
  } finally { searching.value = false }
}

function removePreviewQuestion(questionId) {
  previewQuestions.value = previewQuestions.value.filter((item) => item.questionId !== questionId)
  renumberPreview()
  if (activePreviewQuestionId.value === questionId) {
    smartComposeStore.setActivePreviewQuestionId(previewQuestions.value[0]?.questionId ?? null)
  }
}

function movePreviewUp(questionId) {
  const list = [...previewQuestions.value]
  const index = list.findIndex((item) => item.questionId === questionId)
  if (index <= 0) return
  ;[list[index - 1], list[index]] = [list[index], list[index - 1]]
  previewQuestions.value = list; renumberPreview()
}

function movePreviewDown(questionId) {
  const list = [...previewQuestions.value]
  const index = list.findIndex((item) => item.questionId === questionId)
  if (index < 0 || index >= list.length - 1) return
  ;[list[index], list[index + 1]] = [list[index + 1], list[index]]
  previewQuestions.value = list; renumberPreview()
}

async function openSwapDialog(row) {
  swapTarget.value = row; swapVisible.value = true; swapLoading.value = true; swapAlternatives.value = []
  try {
    const excludeIds = previewQuestions.value.map((item) => item.questionId)
    swapAlternatives.value = await smartAlternatives({
      subject: form.value.subject || composeCondition.value.subject || undefined,
      questionType: row.questionType || undefined,
      chapter: composeCondition.value.chapter || undefined,
      keywords: composeCondition.value.keywords || undefined,
      excludeIds, limit: 12,
    })
  } finally { swapLoading.value = false }
}

function applySwap(alternative) {
  if (!swapTarget.value) return
  const index = previewQuestions.value.findIndex((item) => item.questionId === swapTarget.value.questionId)
  if (index < 0) return
  const current = previewQuestions.value[index]
  previewQuestions.value[index] = {
    questionId: alternative.id, sortOrder: current.sortOrder, score: current.score,
    stem: alternative.stem, answer: alternative.answer, analysis: alternative.analysis,
    questionType: alternative.questionType, options: alternative.options,
    images: alternative.images || [], imagesJson: null,
  }
  if (activePreviewQuestionId.value === swapTarget.value.questionId) {
    smartComposeStore.setActivePreviewQuestionId(previewQuestions.value[index].questionId)
  }
  swapVisible.value = false; ElMessage.success('已替换题目')
}

async function handleConfirmPaper() {
  if (!form.value.title.trim()) { ElMessage.warning('请填写试卷名称'); return }
  if (previewQuestions.value.length === 0) { ElMessage.warning('请先检索并确认题目'); return }
  confirming.value = true
  try {
    const result = await createPaper({
      title: form.value.title.trim(), paperType: form.value.paperType,
      subject: form.value.subject || undefined,
      durationMinutes: form.value.durationMinutes || undefined,
      composeMode: 'SMART', composeCondition: composeCondition.value,
      questions: previewQuestions.value.map((item) => ({
        questionId: item.questionId, sortOrder: item.sortOrder, score: Number(item.score),
      })),
    })
    smartComposeStore.setPaperId(result.id)
    ElMessage.success('试卷已生成，可以导出 Word')
  } finally { confirming.value = false }
}

async function handleExport(exportType) {
  if (!paperId.value) { ElMessage.warning('请先确认生成试卷'); return }
  exporting.value = true
  try {
    await exportPaper(paperId.value, exportType)
    ElMessage.success(exportType === 'TEACHER' ? '教师版已生成' : '学生版已生成')
  } finally { exporting.value = false }
}

onDeactivated(() => {
  savedOverlayState.value = {
    preview: previewVisible.value,
    detail: previewDetailVisible.value,
    swap: swapVisible.value,
  }
  previewVisible.value = false
  previewDetailVisible.value = false
  swapVisible.value = false
})

onActivated(() => {
  previewVisible.value = savedOverlayState.value.preview
  previewDetailVisible.value = savedOverlayState.value.detail
  swapVisible.value = savedOverlayState.value.swap
})
</script>

<template>
  <div class="smart-compose-page page-scroll">
    <el-card shadow="never" class="panel card-enter" style="--i: 0">
      <template #header>
        <div class="card-header">
          <span>智能组卷</span>
          <el-button :loading="syncing" size="small" @click="handleSyncEmbed">同步向量库</el-button>
        </div>
      </template>

      <el-alert type="info" :closable="false" show-icon class="tip-block">
        <template #title>支持按题型比例组卷（如 5单选+3计算），尽量分散章节避免重复。检索后点击「预览确认」调整题目。</template>
      </el-alert>

      <el-form label-position="top" class="compose-form">
        <el-row :gutter="20">
          <el-col :span="10">
            <el-form-item label="试卷名称" required>
              <el-input v-model="form.title" placeholder="如：数字电子技术期末卷" />
            </el-form-item>
          </el-col>
          <el-col :span="6">
            <el-form-item label="课程">
              <el-input v-model="form.subject" placeholder="如：数字电子技术" />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="总分">
              <el-input-number
                v-model="form.totalScore"
                :min="1"
                :max="300"
                controls-position="right"
                class="compact-number-input"
              />
            </el-form-item>
          </el-col>
          <el-col :span="4">
            <el-form-item label="时长(分)">
              <el-input-number
                v-model="form.durationMinutes"
                :min="1"
                :max="300"
                controls-position="right"
                class="compact-number-input"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="组卷需求" required>
          <el-input
            v-model="form.query"
            type="textarea"
            :rows="4"
            placeholder="例如：数字电子技术 触发器 8道单选题，中等难度"
          />
        </el-form-item>
        <div class="chip-row">
          <span class="chip-label">示例：</span>
          <span v-for="item in queryExamples" :key="item" class="query-chip" @click="applyExample(item)">
            {{ item }}
          </span>
        </div>
        <div class="action-row">
          <el-button type="primary" class="action-btn" :loading="searching" @click="handleSearch">
            检索匹配题目
          </el-button>
          <div class="action-btn-wrap">
            <el-badge
              :value="previewQuestions.length"
              :hidden="!hasPreview"
              :max="99"
              class="action-badge"
            >
              <el-button type="primary" plain class="action-btn" :disabled="!hasPreview" @click="previewVisible = true">
                预览确认
              </el-button>
            </el-badge>
          </div>
        </div>
      </el-form>

      <transition name="fade-slide">
        <div v-if="Object.keys(composeCondition).length" class="condition-card">
          <div class="condition-title">解析出的组卷条件</div>
          <el-descriptions :column="3" border size="small">
            <el-descriptions-item label="课程">{{ composeCondition.subject || '-' }}</el-descriptions-item>
            <el-descriptions-item label="题量">{{ composeCondition.count || '-' }}</el-descriptions-item>
            <el-descriptions-item label="难度">{{ composeCondition.difficulty || '-' }}</el-descriptions-item>
            <el-descriptions-item label="题型比例" :span="2">
              {{ formatTypeCounts(composeCondition.typeCounts) }}
            </el-descriptions-item>
            <el-descriptions-item label="章节去重">
              每章最多 {{ composeCondition.maxPerChapter || 1 }} 题
            </el-descriptions-item>
            <el-descriptions-item label="章节" :span="3">
              {{ composeCondition.chapter || '-' }}
            </el-descriptions-item>
          </el-descriptions>
        </div>
      </transition>
    </el-card>

    <el-dialog
      v-model="previewVisible"
      width="900px"
      align-center
      append-to-body
      lock-scroll
      destroy-on-close
      class="smart-preview-dialog"
      modal-class="smart-preview-overlay"
    >
      <template #header>
        <div class="preview-dialog-header">
          <span>预览确认</span>
          <span v-if="hasPreview" class="stat-summary">
            共 {{ previewQuestions.length }} 题 · 总分 {{ formatScore(totalPreviewScore) }}
          </span>
        </div>
      </template>

      <el-empty v-if="!hasPreview" description="请先检索匹配题目" />

      <template v-else>
        <el-row :gutter="12" class="preview-stats">
          <el-col :span="8">
            <div class="mini-stat" style="background: var(--color-stat-1)">
              <span class="mini-num">{{ previewQuestions.length }}</span>
              <span class="mini-label">题量</span>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="mini-stat" style="background: var(--color-stat-2)">
              <span class="mini-num">{{ formatScore(totalPreviewScore) }}</span>
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

        <el-table
          ref="previewTableRef"
          class="preview-question-table"
          :data="previewQuestions"
          size="small"
          row-key="questionId"
          highlight-current-row
          :current-row-key="activePreviewQuestionId"
          max-height="360"
          native-scrollbar
          @row-click="handlePreviewRow"
        >
          <el-table-column prop="sortOrder" label="#" width="40" />
          <el-table-column label="题型" width="80">
            <template #default="{ row }">{{ questionTypeLabel(row.questionType) }}</template>
          </el-table-column>
          <el-table-column label="分值" width="90">
            <template #default="{ row }">
              <el-input-number
                v-model="row.score"
                :min="0.5"
                :max="100"
                :step="0.5"
                size="small"
                controls-position="right"
                style="width: 72px"
              />
            </template>
          </el-table-column>
          <el-table-column label="题干" min-width="0">
            <template #default="{ row }">
              <div class="stem-preview-cell is-compact is-clickable" @click.stop="handlePreviewRow(row)">
                <RichContent :content="row.stem" :image-map="stemImageMapFor(row)" compact />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="150">
            <template #default="{ row }">
              <el-button link size="small" @click.stop="movePreviewUp(row.questionId)">↑</el-button>
              <el-button link size="small" @click.stop="movePreviewDown(row.questionId)">↓</el-button>
              <el-button link type="primary" size="small" @click.stop="openSwapDialog(row)">换题</el-button>
              <el-button link type="danger" size="small" @click.stop="removePreviewQuestion(row.questionId)">
                移除
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </template>

      <template v-if="hasPreview" #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="confirming" :disabled="!!paperId" @click="handleConfirmPaper">
            {{ paperId ? '已生成试卷' : '确认生成试卷' }}
          </el-button>
          <el-button type="primary" plain :loading="exporting" :disabled="!paperId" @click="handleExport('STUDENT')">
            学生版
          </el-button>
          <el-button type="success" plain :loading="exporting" :disabled="!paperId" @click="handleExport('TEACHER')">
            教师版
          </el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="previewDetailVisible"
      title="题目详情"
      width="720px"
      top="56px"
      append-to-body
      lock-scroll
      destroy-on-close
      class="smart-question-detail-dialog"
      modal-class="smart-question-detail-overlay"
    >
      <div v-if="previewQuestion" class="preview-detail-scroll subtle-scroll">
        <el-descriptions :column="2" border size="small" class="preview-detail-meta">
          <el-descriptions-item label="题型">
            {{ questionTypeLabel(previewQuestion.questionType) }}
          </el-descriptions-item>
          <el-descriptions-item label="分值">
            {{ previewQuestion.score ?? '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="detail-block">
          <div class="detail-label">题干</div>
          <div class="detail-panel">
            <RichContent :content="previewQuestion.stem" :image-map="previewStemImageMap" />
          </div>
        </div>

        <div v-if="previewQuestion.options?.length" class="detail-block">
          <div class="detail-label">选项</div>
          <div v-for="(opt, idx) in previewQuestion.options" :key="idx" class="detail-panel">
            <RichContent :content="opt" :image-map="previewStemImageMap" />
          </div>
        </div>

        <div class="detail-block">
          <div class="detail-label">答案</div>
          <div class="detail-panel answer">
            <RichContent
              :content="previewQuestion.answer"
              :image-map="previewAnswerImageMap"
              empty-text="暂无答案"
            />
          </div>
        </div>

        <div v-if="previewQuestion.analysis" class="detail-block">
          <div class="detail-label">解析</div>
          <div class="detail-panel">
            <RichContent :content="previewQuestion.analysis" :image-map="previewStemImageMap" />
          </div>
        </div>
      </div>
    </el-dialog>

    <el-dialog
      v-model="swapVisible"
      title="换一道题"
      width="720px"
      append-to-body
      lock-scroll
      destroy-on-close
      modal-class="smart-swap-overlay"
    >
      <div v-loading="swapLoading">
        <el-empty v-if="!swapLoading && swapAlternatives.length === 0" description="暂无其他候选题目" />
        <el-table v-else :data="swapAlternatives" max-height="360" @row-click="applySwap">
          <el-table-column prop="id" label="ID" width="70" />
          <el-table-column label="题型" width="90">
            <template #default="{ row }">{{ questionTypeLabel(row.questionType) }}</template>
          </el-table-column>
          <el-table-column label="题干" min-width="280">
            <template #default="{ row }">
              <div class="stem-preview-cell">
                <RichContent :content="row.stem" :image-map="stemImageMapFor(row)" />
              </div>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="80">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="applySwap(row)">选用</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-dialog>
  </div>
</template>

<style scoped>
.panel :deep(.el-card__header) {
  font-weight: 600;
  color: var(--color-text-primary);
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
}

.action-row {
  display: flex;
  align-items: stretch;
  gap: 12px;
  width: 100%;
  margin-top: 4px;
}

.action-btn-wrap { flex: 1; min-width: 0; }
.action-row > .action-btn,
.action-btn-wrap { flex: 1; min-width: 0; }
.action-btn-wrap :deep(.el-badge) { display: block; width: 100%; }
.action-btn { width: 100%; height: 40px; border-radius: 10px; }

.preview-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-right: 24px;
  font-size: 16px;
  font-weight: 600;
}

.tip-block { margin-bottom: 20px; }
.compose-form { margin-top: 4px; }
.compose-form :deep(.compact-number-input) {
  width: 100%;
  min-width: 120px;
}
.compose-form :deep(.compact-number-input .el-input__wrapper) {
  padding-left: 10px;
  padding-right: 42px;
}
.chip-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin: -8px 0 16px;
}

.preview-stats { margin-bottom: 14px; }
.mini-stat {
  padding: 12px;
  border-radius: 10px;
  text-align: center;
  display: flex;
  flex-direction: column;
}
.preview-tip { margin-bottom: 12px; }
.stem-preview-cell { max-height: 48px; overflow: hidden; }
.stem-preview-cell.is-compact { max-height: 40px; }
.stem-preview-cell.is-clickable {
  cursor: pointer;
  border-radius: 6px;
  transition: background 0.2s ease;
}
.stem-preview-cell.is-clickable:hover {
  background: var(--color-primary-light);
}

.preview-question-table {
  width: 100%;
}

.preview-question-table :deep(.el-table__body-wrapper) {
  cursor: default;
  overflow-x: hidden !important;
}

.preview-question-table :deep(.el-scrollbar__wrap) {
  overflow-x: hidden !important;
}

.preview-question-table :deep(.el-scrollbar__bar.is-horizontal) {
  display: none !important;
}

.preview-question-table :deep(.el-table__body-wrapper),
.preview-question-table :deep(.el-scrollbar__wrap) {
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.08) transparent;
}

.preview-question-table :deep(.el-table__body-wrapper::-webkit-scrollbar),
.preview-question-table :deep(.el-scrollbar__wrap::-webkit-scrollbar) {
  width: 3px;
  height: 0;
}

.preview-question-table :deep(.el-table__body-wrapper::-webkit-scrollbar-thumb),
.preview-question-table :deep(.el-scrollbar__wrap::-webkit-scrollbar-thumb) {
  background: rgba(255, 255, 255, 0.08);
  border-radius: 3px;
}

.preview-question-table :deep(.el-table__body-wrapper::-webkit-scrollbar-thumb:hover),
.preview-question-table :deep(.el-scrollbar__wrap::-webkit-scrollbar-thumb:hover) {
  background: rgba(255, 255, 255, 0.14);
}

.preview-question-table :deep(.el-table__body-wrapper::-webkit-scrollbar-track),
.preview-question-table :deep(.el-scrollbar__wrap::-webkit-scrollbar-track) {
  background: transparent;
}

.preview-question-table :deep(.el-table__body .cell) {
  overflow: hidden;
}

.preview-question-table.is-drag-scrolling :deep(.el-table__body-wrapper) {
  cursor: grabbing !important;
}

.preview-question-table.is-drag-scrolling :deep(.el-table__body td.el-table__cell) {
  cursor: grabbing !important;
}

.preview-detail-scroll {
  max-height: min(62vh, 520px);
  overflow: auto;
  padding-right: 4px;
}

.detail-block { margin-top: 16px; }
.detail-block:first-of-type { margin-top: 0; }

.detail-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 6px;
  font-weight: 500;
}

.detail-panel {
  line-height: 1.7;
  padding: 14px 16px;
  background: var(--color-bg-glass);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}

.detail-panel.answer {
  background: var(--color-success-light);
  border-color: rgba(110, 201, 160, 0.22);
}

.preview-detail-meta { margin-bottom: 4px; }

.stat-summary {
  font-size: 14px;
  font-weight: 600;
  color: hsl(var(--shade-hue), 65%, 62%);
}

.chip-label { font-size: 13px; color: var(--color-text-secondary); }

.query-chip {
  display: inline-block;
  padding: 5px 14px;
  background: var(--color-primary-light);
  color: hsl(var(--shade-hue), 65%, 62%);
  border: 1px solid var(--color-primary-border);
  border-radius: var(--radius-pill);
  font-size: 12px;
  cursor: pointer;
  transition: all var(--transition);
  white-space: nowrap;
  max-width: 260px;
  overflow: hidden;
  text-overflow: ellipsis;
}

.query-chip:hover {
  background: var(--btn-gradient);
  color: #fff;
  border-color: transparent;
}

.condition-card {
  margin-top: 16px;
  padding: 16px 18px;
  background: var(--color-bg-glass);
  border-radius: var(--radius-lg);
  border: 1px solid var(--color-border);
}

.condition-title {
  margin-bottom: 8px;
  font-size: 14px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.mini-num {
  font-family: var(--font-display);
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-primary);
}
.mini-label { font-size: 11px; color: var(--color-text-secondary); margin-top: 2px; }

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
</style>

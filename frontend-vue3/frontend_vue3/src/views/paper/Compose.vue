<script setup>
import { computed, nextTick, onActivated, onDeactivated, onMounted, onUnmounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'

defineOptions({ name: 'PaperCompose' })
import { ElMessage } from 'element-plus'
import RichContent from '@/components/RichContent.vue'
import { buildImageMapForQuestion } from '@/utils/imageAssets'
import { sumScores, formatScore } from '@/utils/score'
import { pageQuestions } from '@/api/question'
import { useCourseOptions } from '@/composables/useCourseOptions'

const { courseOptions } = useCourseOptions()
import { createPaper, exportPaper } from '@/api/paper'
import { useComposeStore } from '@/stores/compose'
import { useMainScrollLock } from '@/composables/useMainScrollLock'

const composeStore = useComposeStore()
const { selectedQuestions, paperForm, query, selectedPanelVisible } = storeToRefs(composeStore)

const loading = ref(false)
const exporting = ref(false)
const tableData = ref([])
const total = ref(0)
const tableRef = ref(null)
const panelRef = ref(null)
const previewLayout = ref({ left: '0px', width: '100%' })
const previewQuestion = ref(null)
const previewVisible = ref(false)
const savedOverlayState = ref({ preview: false, selected: false })
const isSyncingSelection = ref(false)

const anyOverlayOpen = computed(() => previewVisible.value || selectedPanelVisible.value)
useMainScrollLock(anyOverlayOpen, {
  dialogRootSelector: '.compose-preview-panel, .compose-selected-overlay',
})

function syncPreviewLayout() {
  const panelEl = panelRef.value?.$el ?? panelRef.value
  if (!panelEl) return
  const rect = panelEl.getBoundingClientRect()
  previewLayout.value = {
    left: `${rect.left}px`,
    width: `${rect.width}px`,
  }
}

watch(previewVisible, async (visible) => {
  if (visible) {
    await nextTick()
    syncPreviewLayout()
  }
})

const previewStemImageMap = computed(() =>
  buildImageMapForQuestion(previewQuestion.value, 'STEM')
)
const previewAnswerImageMap = computed(() =>
  buildImageMapForQuestion(previewQuestion.value, 'ANSWER')
)

function stemImageMapFor(question) {
  return buildImageMapForQuestion(question, 'STEM')
}
const totalScore = computed(() => sumScores(selectedQuestions.value))

const questionTypeOrder = [
  'SINGLE_CHOICE', 'MULTI_CHOICE', 'TRUE_FALSE', 'FILL_BLANK',
  'SHORT_ANSWER', 'CALCULATION', 'ESSAY', 'UNKNOWN',
]

const groupedSelectedQuestions = computed(() => {
  const groups = new Map()
  selectedQuestions.value.forEach((question) => {
    const type = question.questionType || 'UNKNOWN'
    if (!groups.has(type)) {
      groups.set(type, { type, label: questionTypeLabel(type), items: [] })
    }
    groups.get(type).items.push(question)
  })
  const ordered = questionTypeOrder.filter((type) => groups.has(type)).map((type) => groups.get(type))
  groups.forEach((group, type) => {
    if (!questionTypeOrder.includes(type)) ordered.push(group)
  })
  return ordered
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

function questionTypeLabel(type) {
  return questionTypeOptions.find((o) => o.value === type)?.label || type || '-'
}

function isSelected(id) {
  return selectedQuestions.value.some((q) => q.id === id)
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageQuestions({
      pageNum: query.value.pageNum, pageSize: query.value.pageSize,
      subject: query.value.subject || undefined,
      questionType: query.value.questionType || undefined,
      keyword: query.value.keyword || undefined,
    })
    tableData.value = res.list || []
    total.value = res.total || 0
    syncTableSelection()
  } finally { loading.value = false }
}

function syncTableSelection() {
  nextTick(() => {
    if (!tableRef.value) return
    isSyncingSelection.value = true
    tableRef.value.clearSelection()
    tableData.value.forEach((row) => {
      if (isSelected(row.id)) {
        tableRef.value.toggleRowSelection(row, true)
      }
    })
    nextTick(() => {
      isSyncingSelection.value = false
    })
  })
}

function handleSearch() {
  query.value.pageNum = 1
  if (query.value.subject) paperForm.value.subject = query.value.subject
  loadData()
}

function handleCourseChange(value) {
  if (value) paperForm.value.subject = value
  handleSearch()
}

function handlePageChange(page) { query.value.pageNum = page; loadData() }

function handleSelectionChange(rows) {
  if (isSyncingSelection.value) return
  const selectedIds = new Set(rows.map((row) => row.id))
  tableData.value.forEach((row) => {
    if (selectedIds.has(row.id)) {
      if (!isSelected(row.id)) composeStore.addQuestion(row)
    } else if (isSelected(row.id)) {
      composeStore.removeQuestion(row.id)
    }
  })
}

function handleRowClick(row) {
  previewQuestion.value = row
  previewVisible.value = true
}

function handleSelectedRowClick(row) {
  previewQuestion.value = row
  previewVisible.value = true
}

function removeSelected(id) {
  composeStore.removeQuestion(id)
  const row = tableData.value.find((item) => item.id === id)
  if (row) tableRef.value?.toggleRowSelection(row, false)
  if (previewQuestion.value?.id === id) {
    previewQuestion.value = selectedQuestions.value[0] || null
  }
}

function handleClear() {
  composeStore.clearSelection()
  tableRef.value?.clearSelection()
  previewQuestion.value = null
  previewVisible.value = false
}

async function handleExport(exportType) {
  if (!paperForm.value.title.trim()) { ElMessage.warning('请填写试卷名称'); return }
  if (selectedQuestions.value.length === 0) { ElMessage.warning('请至少选择一道题目'); return }
  exporting.value = true
  try {
    const result = await createPaper({
      title: paperForm.value.title, paperType: 'EXAM',
      subject: paperForm.value.subject || undefined,
      durationMinutes: paperForm.value.durationMinutes || undefined,
      composeMode: 'MANUAL',
      questions: selectedQuestions.value.map((q) => ({
        questionId: q.id, sortOrder: q.sortOrder, score: Number(q.score),
      })),
    })
    await exportPaper(result.id, exportType)
    ElMessage.success(exportType === 'TEACHER' ? '教师版已生成' : '学生版已生成')
  } finally { exporting.value = false }
}

onMounted(() => {
  loadData()
  window.addEventListener('resize', syncPreviewLayout)
})

onUnmounted(() => {
  window.removeEventListener('resize', syncPreviewLayout)
})

onDeactivated(() => {
  savedOverlayState.value = {
    preview: previewVisible.value,
    selected: selectedPanelVisible.value,
  }
  previewVisible.value = false
  selectedPanelVisible.value = false
})

onActivated(() => {
  previewVisible.value = savedOverlayState.value.preview
  selectedPanelVisible.value = savedOverlayState.value.selected
  syncTableSelection()
  if (previewVisible.value) nextTick(syncPreviewLayout)
})
</script>

<template>
  <div class="compose-page page-scroll">
    <el-card ref="panelRef" shadow="never" class="panel">
      <template #header>
        <span>题库选题</span>
      </template>

      <el-form :inline="true" class="filter-form" @submit.prevent="handleSearch">
        <el-form-item>
          <el-select v-model="query.subject" placeholder="选择课程" clearable filterable style="width: 160px" @change="handleCourseChange">
            <el-option v-for="name in courseOptions" :key="name" :label="name" :value="name" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-select v-model="query.questionType" placeholder="题型" clearable style="width: 110px">
            <el-option v-for="item in questionTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-input v-model="query.keyword" placeholder="搜索题干" clearable style="width: 200px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>

      <el-table
        ref="tableRef"
        v-loading="loading"
        :data="tableData"
        row-key="id"
        @selection-change="handleSelectionChange"
        @row-click="handleRowClick"
      >
        <el-table-column type="selection" width="45" />
        <el-table-column label="题干" min-width="360">
          <template #default="{ row }">
            <div class="stem-preview-cell">
              <RichContent :content="row.stem" :image-map="stemImageMapFor(row)" compact />
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="subject" label="课程" width="130" show-overflow-tooltip />
        <el-table-column label="题型" width="100">
          <template #default="{ row }">{{ questionTypeLabel(row.questionType) }}</template>
        </el-table-column>
        <el-table-column prop="scoreDefault" label="分值" width="80" />
      </el-table>

      <div class="pagination">
        <el-pagination
          small
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="query.pageSize"
          :current-page="query.pageNum"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-dialog
      v-model="selectedPanelVisible"
      width="820px"
      align-center
      append-to-body
      lock-scroll
      destroy-on-close
      class="compose-selected-dialog"
      modal-class="compose-selected-overlay"
    >
      <template #header>
        <div class="selected-dialog-header">
          <span>已选题目</span>
          <span v-if="selectedQuestions.length" class="score-total">共 {{ selectedQuestions.length }} 题 · 总分 {{ formatScore(totalScore) }}</span>
        </div>
      </template>

      <div class="selected-dialog-layout">
        <el-form label-width="80px" class="paper-form">
          <el-row :gutter="16">
            <el-col :span="12">
              <el-form-item label="试卷名称" required>
                <el-input v-model="paperForm.title" placeholder="如：期中复习卷" />
              </el-form-item>
            </el-col>
            <el-col :span="12">
              <el-form-item label="课程">
                <el-input v-model="paperForm.subject" placeholder="导出试卷封面课程名" />
              </el-form-item>
            </el-col>
          </el-row>
          <el-form-item label="时长(分)">
            <el-input-number v-model="paperForm.durationMinutes" :min="1" :max="300" />
          </el-form-item>
        </el-form>

        <div class="selected-groups-scroll subtle-scroll">
          <el-empty v-if="selectedQuestions.length === 0" description="请从题库勾选题目" />

          <div v-else class="selected-groups">
            <div v-for="group in groupedSelectedQuestions" :key="group.type" class="selected-group">
              <div class="group-title">
                {{ group.label }}<span class="group-count">{{ group.items.length }} 题</span>
              </div>
              <el-table :data="group.items" size="small" @row-click="handleSelectedRowClick">
                <el-table-column prop="sortOrder" label="#" width="40" />
                <el-table-column label="题干" min-width="200">
                  <template #default="{ row }">
                    <div class="stem-preview-cell is-compact">
                      <RichContent :content="row.stem" :image-map="stemImageMapFor(row)" compact />
                    </div>
                  </template>
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
                      @change="(val) => composeStore.updateScore(row.id, val)"
                    />
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="110">
                  <template #default="{ row }">
                    <el-button link size="small" @click.stop="composeStore.moveUp(row.id)">↑</el-button>
                    <el-button link size="small" @click.stop="composeStore.moveDown(row.id)">↓</el-button>
                    <el-button link type="danger" size="small" @click.stop="removeSelected(row.id)">移除</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="handleClear">清空选题</el-button>
          <el-button type="primary" :loading="exporting" @click="handleExport('STUDENT')">生成学生版</el-button>
          <el-button type="success" :loading="exporting" @click="handleExport('TEACHER')">生成教师版</el-button>
        </div>
      </template>
    </el-dialog>

    <Teleport to="body">
      <transition name="compose-preview-slide">
        <div
          v-if="previewQuestion && previewVisible"
          class="compose-preview-panel"
          :style="previewLayout"
        >
          <div class="preview-handle" @click="previewVisible = false">
            <span class="preview-drag-hint" />
            <span>👁 题目预览</span>
            <el-button link @click.stop="previewVisible = false">收起</el-button>
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
    </Teleport>
  </div>
</template>

<style scoped>
.panel :deep(.el-card__header) {
  font-weight: 600;
  color: var(--color-text-primary);
}

.filter-form { margin-bottom: 12px; }
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-weight: 600;
}
.selected-dialog-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding-right: 24px;
  font-size: 16px;
  font-weight: 600;
}
.paper-form { margin-bottom: 12px; flex-shrink: 0; }
.paper-form :deep(.el-form-item) { margin-bottom: 10px; }
.selected-dialog-layout {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  height: 100%;
}
.selected-groups-scroll {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  overscroll-behavior: contain;
  padding-right: 2px;
}
.selected-groups { padding-bottom: 4px; }
.selected-group + .selected-group { margin-top: 14px; }
.pagination {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
}
.stem-preview-cell { max-height: 56px; overflow: hidden; }
.stem-preview-cell.is-compact { max-height: 40px; }
.stem-preview-cell :deep(.rich-content) { font-size: 13px; line-height: 1.45; }

.score-total {
  font-size: 14px;
  color: hsl(var(--shade-hue), 65%, 62%);
  font-weight: 600;
}

.group-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  padding: 6px 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  background: var(--color-primary-light);
  border-left: 3px solid var(--color-primary);
  border-radius: 0 6px 6px 0;
}

.group-count { font-size: 12px; font-weight: 400; color: var(--color-text-secondary); }

.compose-preview-panel {
  position: fixed;
  bottom: 0;
  right: auto;
  max-height: min(72vh, 620px);
  background: rgba(26, 31, 46, 0.96);
  border: 1px solid var(--color-border-strong);
  border-bottom: none;
  box-shadow: 0 -12px 48px rgba(0, 0, 0, 0.4);
  border-radius: var(--radius-xl) var(--radius-xl) 0 0;
  z-index: 2000;
  display: flex;
  flex-direction: column;
  backdrop-filter: blur(20px);
}

.compose-preview-slide-enter-active {
  animation: compose-preview-in 0.32s cubic-bezier(0.22, 1, 0.36, 1);
}

.compose-preview-slide-leave-active {
  animation: compose-preview-out 0.24s ease-in;
}

@keyframes compose-preview-in {
  from {
    transform: translateY(100%);
  }
  to {
    transform: translateY(0);
  }
}

@keyframes compose-preview-out {
  from {
    transform: translateY(0);
  }
  to {
    transform: translateY(100%);
  }
}

.preview-handle {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 24px 10px;
  border-bottom: 1px solid var(--color-border);
  font-weight: 600;
  font-size: 14px;
  color: var(--color-text-primary);
  cursor: pointer;
  user-select: none;
  flex-shrink: 0;
}

.preview-drag-hint {
  position: absolute;
  top: 6px;
  left: 50%;
  transform: translateX(-50%);
  width: 36px;
  height: 4px;
  border-radius: 2px;
  background: var(--color-border-strong);
}

.preview-handle:hover { background: var(--color-bg-glass-hover); }

.preview-body {
  flex: 1;
  overflow-y: auto;
  padding: 16px 24px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.preview-label { font-size: 12px; color: var(--color-text-secondary); margin-bottom: 4px; font-weight: 500; }

.preview-content {
  padding: 12px 14px;
  background: var(--color-bg-glass);
  border: 1px solid var(--color-border);
  border-radius: var(--radius);
}

.preview-content.answer-bg {
  background: var(--color-success-light);
  border-color: rgba(110, 201, 160, 0.22);
}
.preview-content.option-item { margin-bottom: 4px; }

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  flex-wrap: wrap;
}
</style>

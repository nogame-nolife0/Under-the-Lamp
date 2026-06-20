<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import RichContent from '@/components/RichContent.vue'
import QuestionEditDialog from '@/components/QuestionEditDialog.vue'
import { buildImageMap, buildImageMapForQuestion } from '@/utils/imageAssets'
import {
  pageQuestions,
  getQuestion,
  batchDeleteQuestions,
  batchUpdateSubject,
  syncQuestionEmbed,
} from '@/api/question'
import { useCourseOptions } from '@/composables/useCourseOptions'
import { useMainScrollLock } from '@/composables/useMainScrollLock'
import { useTableDragScroll } from '@/composables/useTableDragScroll'

const { courseOptions, loadCourseOptions } = useCourseOptions()

const loading = ref(false)
const deleting = ref(false)
const syncing = ref(false)
const updatingSubject = ref(false)
const subjectDialogVisible = ref(false)
const batchSubject = ref('')
const tableData = ref([])
const total = ref(0)
const selectedRows = ref([])
const tableRef = ref(null)

useTableDragScroll(tableRef)

const syncedCount = computed(() => tableData.value.filter((q) => q.embedStatus === 'SYNCED').length)
const singleChoiceCount = computed(() => tableData.value.filter((q) => q.questionType === 'SINGLE_CHOICE').length)
const hardCount = computed(() => tableData.value.filter((q) => q.difficulty === 'HARD').length)

const query = reactive({
  pageNum: 1,
  pageSize: 10,
  subject: '',
  questionType: '',
  keyword: '',
})

const detailVisible = ref(false)
const detailLoading = ref(false)
const currentQuestion = ref(null)
const editVisible = ref(false)
const editQuestionId = ref(null)

const anyDetailDialogOpen = computed(() => detailVisible.value || editVisible.value)
useMainScrollLock(anyDetailDialogOpen, {
  dialogRootSelector: '.question-detail-overlay, .question-edit-overlay',
})

const detailStemImageMap = computed(() =>
  buildImageMapForQuestion(currentQuestion.value, 'STEM')
)
const detailAnswerImageMap = computed(() =>
  buildImageMapForQuestion(currentQuestion.value, 'ANSWER')
)

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

function truncate(text, len = 80) {
  if (!text) return '-'
  return text.length > len ? text.slice(0, len) + '…' : text
}

function embedStatusLabel(status) {
  if (status === 'SYNCED') return '已同步'
  if (status === 'FAILED') return '同步失败'
  return '待同步'
}

function embedStatusClass(status) {
  if (status === 'SYNCED') return 'synced'
  if (status === 'FAILED') return 'failed'
  return 'pending'
}

function handleSelectionChange(rows) {
  selectedRows.value = rows
}

async function loadData() {
  loading.value = true
  try {
    const res = await pageQuestions({
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      subject: query.subject || undefined,
      questionType: query.questionType || undefined,
      keyword: query.keyword || undefined,
    })
    tableData.value = res.list || []
    total.value = res.total || 0
    selectedRows.value = []
    tableRef.value?.clearSelection()
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  query.pageNum = 1
  loadData()
}

function handleReset() {
  query.subject = ''
  query.questionType = ''
  query.keyword = ''
  query.pageNum = 1
  loadData()
}

function handlePageChange(page) {
  query.pageNum = page
  loadData()
}

async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  try {
    currentQuestion.value = await getQuestion(row.id)
  } finally {
    detailLoading.value = false
  }
}

function openEdit(row) {
  editQuestionId.value = row.id
  editVisible.value = true
}

function handleEditSaved() {
  loadData()
  loadCourseOptions()
}

function openBatchSubjectDialog() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先勾选要修改的题目')
    return
  }
  batchSubject.value = ''
  subjectDialogVisible.value = true
}

async function handleBatchUpdateSubject() {
  if (!batchSubject.value.trim()) {
    ElMessage.warning('请填写课程名称')
    return
  }
  updatingSubject.value = true
  try {
    const ids = selectedRows.value.map((row) => row.id)
    const result = await batchUpdateSubject(ids, batchSubject.value.trim())
    ElMessage.success(result.message || `已更新 ${result.updatedCount} 题`)
    subjectDialogVisible.value = false
    await loadData()
    await loadCourseOptions()
  } finally {
    updatingSubject.value = false
  }
}

async function handleSyncEmbed() {
  syncing.value = true
  try {
    const res = await syncQuestionEmbed(50)
    ElMessage.success(res.message || '向量同步完成')
    await loadData()
  } finally {
    syncing.value = false
  }
}

async function handleBatchDelete() {
  if (selectedRows.value.length === 0) {
    ElMessage.warning('请先勾选要删除的题目')
    return
  }

  await ElMessageBox.confirm(
    `确定删除选中的 ${selectedRows.value.length} 道题目吗？已被历史试卷引用的题目将自动跳过。`,
    '批量删除',
    { type: 'warning' }
  )

  deleting.value = true
  try {
    const ids = selectedRows.value.map((row) => row.id)
    const result = await batchDeleteQuestions(ids)
    if (result.deletedCount > 0) {
      ElMessage.success(result.message || `已删除 ${result.deletedCount} 题`)
    } else {
      ElMessage.warning(result.message || '没有题目被删除')
    }
    await loadData()
    await loadCourseOptions()
  } finally {
    deleting.value = false
  }
}

onMounted(() => loadData())
</script>

<template>
  <div class="page-card page-scroll">
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

    <el-card shadow="never" class="main-card card-enter" style="--i: 4">
      <template #header>
        <div class="card-header">
          <div class="title-wrap">
            <span>题库列表</span>
            <span class="sub-title">共 {{ total }} 道题目</span>
          </div>
          <div class="header-actions">
            <el-button :loading="syncing" @click="handleSyncEmbed">同步向量库</el-button>
            <el-button
              :disabled="selectedRows.length === 0"
              @click="openBatchSubjectDialog"
            >
              批量改课程{{ selectedRows.length ? ` (${selectedRows.length})` : '' }}
            </el-button>
            <el-button
              type="danger"
              plain
              :disabled="selectedRows.length === 0"
              :loading="deleting"
              @click="handleBatchDelete"
            >
              批量删除{{ selectedRows.length ? ` (${selectedRows.length})` : '' }}
            </el-button>
          </div>
        </div>
      </template>

      <el-form :inline="true" class="filter-form" @submit.prevent="handleSearch">
        <el-form-item label="课程">
          <el-select
            v-model="query.subject"
            placeholder="全部课程"
            clearable
            filterable
            style="width: 180px"
          >
            <el-option v-for="name in courseOptions" :key="name" :label="name" :value="name" />
          </el-select>
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="query.questionType" placeholder="全部" clearable style="width: 130px">
            <el-option
              v-for="item in questionTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="搜索题干"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>

      <el-table
        ref="tableRef"
        class="question-table"
        v-loading="loading"
        :data="tableData"
        stripe
        row-key="id"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="48" />
        <el-table-column label="序号" width="70" align="center">
          <template #default="{ $index }">
            {{ (query.pageNum - 1) * query.pageSize + $index + 1 }}
          </template>
        </el-table-column>
        <el-table-column label="题干" min-width="320">
          <template #default="{ row }">
            {{ truncate(row.stem) }}
          </template>
        </el-table-column>
        <el-table-column label="题型" width="100">
          <template #default="{ row }">
            {{ questionTypeLabel(row.questionType) }}
          </template>
        </el-table-column>
        <el-table-column prop="subject" label="课程" width="130" show-overflow-tooltip />
        <el-table-column label="向量库" width="90">
          <template #default="{ row }">
            <span class="embed-dot" :class="embedStatusClass(row.embedStatus)"></span>
            {{ embedStatusLabel(row.embedStatus) }}
          </template>
        </el-table-column>
        <el-table-column prop="scoreDefault" label="默认分值" width="90" />
        <el-table-column
          label="操作"
          width="130"
          fixed="right"
          class-name="action-col"
          header-cell-class-name="action-col-header"
        >
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row)">详情</el-button>
            <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :total="total"
          :page-size="query.pageSize"
          :current-page="query.pageNum"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="subjectDialogVisible" title="批量设置课程" width="420px" destroy-on-close>
      <el-form label-width="80px">
        <el-form-item label="课程名称" required>
          <el-select
            v-model="batchSubject"
            filterable
            allow-create
            default-first-option
            placeholder="选择或输入课程名"
            style="width: 100%"
          >
            <el-option v-for="name in courseOptions" :key="name" :label="name" :value="name" />
          </el-select>
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          show-icon
          title="修改课程后，向量库会在后台自动重新同步。"
        />
      </el-form>
      <template #footer>
        <el-button @click="subjectDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="updatingSubject" @click="handleBatchUpdateSubject">
          确定
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="detailVisible"
      title="题目详情"
      width="720px"
      top="56px"
      append-to-body
      lock-scroll
      destroy-on-close
      class="question-detail-dialog"
      modal-class="question-detail-overlay"
    >
      <div v-loading="detailLoading">
        <template v-if="currentQuestion">
          <el-descriptions :column="2" border>
            <el-descriptions-item label="题型">
              {{ questionTypeLabel(currentQuestion.questionType) }}
            </el-descriptions-item>
            <el-descriptions-item label="默认分值">
              {{ currentQuestion.scoreDefault ?? '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="课程">
              {{ currentQuestion.subject || '-' }}
            </el-descriptions-item>
            <el-descriptions-item label="向量库">
              {{ embedStatusLabel(currentQuestion.embedStatus) }}
            </el-descriptions-item>
            <el-descriptions-item label="章节" :span="2">
              {{ currentQuestion.chapter || '-' }}
            </el-descriptions-item>
          </el-descriptions>

          <div class="detail-block">
            <div class="detail-label">题干</div>
            <div class="detail-panel">
              <RichContent
                :content="currentQuestion.stem"
                :image-map="detailStemImageMap"
                :compact="false"
              />
            </div>
          </div>

          <div v-if="currentQuestion.options?.length" class="detail-block">
            <div class="detail-label">选项</div>
            <div v-for="(opt, idx) in currentQuestion.options" :key="idx" class="detail-panel">
              <RichContent :content="opt" :image-map="buildImageMapForQuestion(currentQuestion, 'STEM')" :compact="false" />
            </div>
          </div>

          <div class="detail-block">
            <div class="detail-label">答案</div>
            <div class="detail-panel answer">
              <RichContent
                :content="currentQuestion.answer"
                :image-map="detailAnswerImageMap"
                empty-text="-"
              />
            </div>
          </div>

          <div v-if="currentQuestion.analysis" class="detail-block">
            <div class="detail-label">解析</div>
            <div class="detail-panel">
              <RichContent
                :content="currentQuestion.analysis"
                :image-map="detailStemImageMap"
              />
            </div>
          </div>
        </template>
      </div>
      <template v-if="currentQuestion" #footer>
        <el-button type="primary" @click="openEdit(currentQuestion)">编辑题目</el-button>
      </template>
    </el-dialog>

    <QuestionEditDialog
      v-model="editVisible"
      :question-id="editQuestionId"
      @saved="handleEditSaved"
    />
  </div>
</template>

<style scoped>
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

.stat-body { display: flex; flex-direction: column; }

.stat-num {
  font-family: var(--font-display);
  font-size: 28px;
  font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
  margin-top: 2px;
}

.header-actions { display: flex; gap: 8px; }

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
  font-family: var(--font-display);
  color: var(--color-text-primary);
}

.sub-title {
  font-size: 13px;
  font-weight: 400;
  color: var(--color-text-secondary);
}

.filter-form {
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--color-border);
}

.question-table {
  width: 100%;
}

.question-table :deep(.el-table__body-wrapper) {
  cursor: default;
}

.question-table.is-drag-scrolling :deep(.el-table__body-wrapper) {
  cursor: grabbing !important;
}

.question-table.is-drag-scrolling :deep(.el-table__body td.el-table__cell) {
  cursor: grabbing !important;
}

/* 右侧固定「操作」列 — 实心背景，避免与滚动列重叠 */
.question-table :deep(.el-table__fixed-right),
.question-table :deep(.el-table__fixed-right-patch) {
  background-color: #1e2435;
}

.question-table :deep(.el-table__fixed-right::before) {
  background-color: var(--color-border);
}

.question-table :deep(th.el-table-fixed-column--right),
.question-table :deep(th.action-col-header) {
  background-color: #252d42 !important;
}

.question-table :deep(td.el-table-fixed-column--right),
.question-table :deep(td.action-col) {
  background-color: #1e2435 !important;
}

.question-table :deep(.el-table__body tr.el-table__row--striped td.el-table-fixed-column--right),
.question-table :deep(.el-table__body tr.el-table__row--striped td.action-col) {
  background-color: #222836 !important;
}

.question-table :deep(.el-table__body tr:hover > td.el-table-fixed-column--right),
.question-table :deep(.el-table__body tr:hover > td.action-col) {
  background-color: #283044 !important;
}

.pagination {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

.embed-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 5px;
  vertical-align: middle;
}

.embed-dot.synced { background: var(--color-success); }
.embed-dot.failed { background: var(--color-danger); }
.embed-dot.pending { background: var(--color-warning); }

.detail-block { margin-top: 16px; }
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
</style>

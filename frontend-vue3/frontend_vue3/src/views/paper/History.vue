<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { pagePapers, exportPaper, batchDeletePapers } from '@/api/paper'
import { useTableDragScroll } from '@/composables/useTableDragScroll'

const loading = ref(false)
const deleting = ref(false)
const exportingId = ref(null)
const tableData = ref([])
const total = ref(0)
const selectedRows = ref([])
const tableRef = ref(null)

useTableDragScroll(tableRef)

const query = reactive({
  pageNum: 1, pageSize: 10, keyword: '',
})

const statusMap = { DRAFT: '草稿', COMPLETED: '已完成' }

function handleSelectionChange(rows) { selectedRows.value = rows }

async function loadData() {
  loading.value = true
  try {
    const res = await pagePapers({
      pageNum: query.pageNum, pageSize: query.pageSize,
      keyword: query.keyword || undefined,
    })
    tableData.value = res.list || []
    total.value = res.total || 0
    selectedRows.value = []
    tableRef.value?.clearSelection()
  } finally { loading.value = false }
}

function handleSearch() { query.pageNum = 1; loadData() }
function handlePageChange(page) { query.pageNum = page; loadData() }

async function handleExport(row, exportType) {
  exportingId.value = row.id
  try { await exportPaper(row.id, exportType); ElMessage.success('导出成功'); await loadData() }
  finally { exportingId.value = null }
}

async function handleBatchDelete() {
  if (selectedRows.value.length === 0) { ElMessage.warning('请先勾选要删除的试卷'); return }
  await ElMessageBox.confirm(
    `确定删除选中的 ${selectedRows.value.length} 份试卷吗？删除后不可恢复，题库中的题目不会被删除。`,
    '批量删除', { type: 'warning' }
  )
  deleting.value = true
  try {
    const ids = selectedRows.value.map((row) => row.id)
    const result = await batchDeletePapers(ids)
    if (result.deletedCount > 0) ElMessage.success(result.message || `已删除 ${result.deletedCount} 份试卷`)
    else ElMessage.warning(result.message || '没有试卷被删除')
    await loadData()
  } finally { deleting.value = false }
}

onMounted(() => loadData())
</script>

<template>
  <div class="page-card page-scroll">
    <el-card shadow="never" class="main-card card-enter" style="--i: 0">
      <template #header>
        <div class="card-header">
          <div class="title-wrap">
            <span>历史试卷</span>
            <span class="sub-title">共 {{ total }} 份</span>
          </div>
          <el-button type="danger" plain :disabled="selectedRows.length === 0" :loading="deleting" @click="handleBatchDelete">
            批量删除{{ selectedRows.length ? ` (${selectedRows.length})` : '' }}
          </el-button>
        </div>
      </template>

      <el-form :inline="true" class="filter-form" @submit.prevent="handleSearch">
        <el-form-item label="试卷名称">
          <el-input v-model="query.keyword" placeholder="搜索试卷" clearable style="width: 220px" @keyup.enter="handleSearch" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
        </el-form-item>
      </el-form>

      <div class="history-table-wrap">
        <el-table
          ref="tableRef"
          class="history-table"
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
          <el-table-column prop="title" label="试卷名称" min-width="200" />
          <el-table-column prop="subject" label="课程" width="120" show-overflow-tooltip />
          <el-table-column prop="totalScore" label="总分" width="80" />
          <el-table-column prop="createdBy" label="出题人" width="100" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <span class="status-dot" :class="row.status === 'COMPLETED' ? 'done' : 'draft'"></span>
              {{ statusMap[row.status] || row.status }}
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="创建时间" min-width="180" />
          <el-table-column
            label="导出"
            width="108"
            fixed="right"
            class-name="export-col"
            header-cell-class-name="export-col-header"
          >
            <template #default="{ row }">
              <el-dropdown @command="(type) => handleExport(row, type)">
                <el-button link type="primary" :loading="exportingId === row.id">导出 ▾</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="STUDENT">学生版</el-dropdown-item>
                    <el-dropdown-item command="TEACHER">教师版</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <div class="pagination">
        <el-pagination
          background layout="total, prev, pager, next"
          :total="total" :page-size="query.pageSize"
          :current-page="query.pageNum" @current-change="handlePageChange"
        />
      </div>
    </el-card>
  </div>
</template>

<style scoped>
.main-card :deep(.el-card__header) { padding: 18px 24px; }

.card-header { display: flex; align-items: center; justify-content: space-between; }

.filter-form { padding-bottom: 16px; margin-bottom: 4px; border-bottom: 1px solid var(--color-border); }
.pagination { margin-top: 20px; display: flex; justify-content: flex-end; }

.history-table-wrap {
  width: 100%;
}

.history-table {
  width: 100%;
}

.history-table :deep(.el-table__body-wrapper) {
  cursor: default;
}

.history-table.is-drag-scrolling :deep(.el-table__body-wrapper) {
  cursor: grabbing !important;
}

.history-table.is-drag-scrolling :deep(.el-table__body td.el-table__cell) {
  cursor: grabbing !important;
}

/* 右侧固定「导出」列 — 实心背景，避免与滚动列重叠 */
.history-table :deep(.el-table__fixed-right),
.history-table :deep(.el-table__fixed-right-patch) {
  background-color: #1e2435;
}

.history-table :deep(.el-table__fixed-right::before) {
  background-color: var(--color-border);
}

.history-table :deep(th.el-table-fixed-column--right),
.history-table :deep(th.export-col-header) {
  background-color: #252d42 !important;
}

.history-table :deep(td.el-table-fixed-column--right),
.history-table :deep(td.export-col) {
  background-color: #1e2435 !important;
}

.history-table :deep(.el-table__body tr.el-table__row--striped td.el-table-fixed-column--right),
.history-table :deep(.el-table__body tr.el-table__row--striped td.export-col) {
  background-color: #222836 !important;
}

.history-table :deep(.el-table__body tr:hover > td.el-table-fixed-column--right),
.history-table :deep(.el-table__body tr:hover > td.export-col) {
  background-color: #283044 !important;
}

.status-dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 5px;
  vertical-align: middle;
}

.title-wrap {
  display: flex;
  align-items: center;
  gap: 12px;
  font-weight: 600;
  font-family: var(--font-display);
  color: var(--color-text-primary);
}
.sub-title { font-size: 13px; font-weight: 400; color: var(--color-text-secondary); }

.status-dot.done { background: var(--color-success); }
.status-dot.draft { background: var(--color-text-placeholder); }
</style>

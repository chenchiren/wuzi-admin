<template>
  <el-card>
    <el-tabs v-model="tab">
      <el-tab-pane label="导入" name="import">
        <div class="bar">
          <el-select v-model="dataType" style="width:180px">
            <el-option value="SUPPLIER" label="供应商" />
            <el-option value="SUPPLIER_PRODUCT" label="供应商产品" />
            <el-option value="MATERIAL" label="物资档案" />
            <el-option value="OPENING_STOCK" label="期初库存" />
          </el-select>
          <el-upload :show-file-list="false" :auto-upload="false" accept=".xlsx,.xls" @change="onFile">
            <n-button type="primary">选择Excel预览</n-button>
          </el-upload>
          <n-button @click="downloadTemplate">下载模板</n-button>
          <n-button type="success" v-perm="'sys:import:create'" :disabled="!batch" @click="confirmImport">确认导入</n-button>
          <n-button v-if="lastBatchId" v-perm="'sys:import:create'" type="danger" :disabled="batch" @click="rollbackBatch">回滚此批次</n-button>
        </div>
        <el-alert v-if="result" type="success" :closable="false" style="margin-bottom:12px"
          :title="`导入完成：成功 ${result.success} / 失败 ${result.failed} / 共 ${result.total}`" />
        <el-table v-if="batch" :data="batch.rows || []" border stripe>
          <el-table-column prop="rowIndex" label="行号" min-width="80" />
          <el-table-column label="是否有效" min-width="90">
            <template #default="{ row }">{{ row.valid ? '是' : '否' }}</template>
          </el-table-column>
          <el-table-column prop="message" label="说明" min-width="200" />
          <el-table-column label="数据" min-width="240">
            <template #default="{ row }">
              <span style="font-size:12px;color:#666">{{ JSON.stringify(row.data) }}</span>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="导入记录" name="records">
        <div class="bar">
          <n-button type="primary" @click="loadRecords">刷新</n-button>
          <el-input v-model="errBatchId" placeholder="批次ID" style="width:220px" clearable />
          <n-button @click="loadBatchErrors" :disabled="!errBatchId">查错误明细</n-button>
          <n-button v-if="errBatchId" v-perm="'sys:import:create'" type="danger" @click="rollbackById">回滚此批次</n-button>
        </div>
        <el-alert v-if="errSummary" :type="errSummary.rolledBack ? 'warning' : 'info'" :closable="false" style="margin-bottom:12px"
          :title="`批次 ${errSummary.batchId}：错误 ${errSummary.errorCount} 条${errSummary.rolledBack ? '（已回滚）' : ''}`" />
        <el-table v-if="errRows.length" :data="errRows" border stripe size="small" style="margin-bottom:16px;max-height:300px">
          <el-table-column prop="rowIndex" label="行号" width="80" />
          <el-table-column prop="message" label="错误说明" min-width="220" />
          <el-table-column label="数据" min-width="240">
            <template #default="{ row }">
              <span style="font-size:12px;color:#666">{{ JSON.stringify(row.data) }}</span>
            </template>
          </el-table-column>
        </el-table>
        <el-table :data="records" border stripe>
          <el-table-column prop="operatorName" label="操作人" min-width="110" />
          <el-table-column prop="module" label="模块" min-width="120" />
          <el-table-column prop="action" label="动作" min-width="140" />
          <el-table-column prop="params" label="参数" min-width="180" />
          <el-table-column label="结果" min-width="100">
            <template #default="{ row }">
              <StatusTag :value="row.result" :label="tImportStatus(row.result) || tLogResult(row.result)" />
            </template>
          </el-table-column>
          <el-table-column prop="createdAt" label="时间" min-width="160" />
        </el-table>
        <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="recTotal"
          v-model:current-page="recPage" :page-size="10" @current-change="loadRecords" />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import axios from 'axios'
import api from '../api/request'
import { tImportStatus, tLogResult, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const tab = ref('import')
const dataType = ref('SUPPLIER')
const batch = ref(null)
const result = ref(null)
const records = ref([])
const recTotal = ref(0)
const recPage = ref(1)

const typePathMap = {
  SUPPLIER: '/import/suppliers',
  SUPPLIER_PRODUCT: '/import/supplier-products',
  MATERIAL: '/import/materials',
  OPENING_STOCK: '/import/opening-stocks'
}
const templateNameMap = {
  SUPPLIER: 'supplier-import-template.xlsx',
  SUPPLIER_PRODUCT: 'supplier-product-import-template.xlsx',
  MATERIAL: 'material-import-template.xlsx',
  OPENING_STOCK: 'opening-stock-import-template.xlsx'
}

async function onFile(file) {
  if (!file?.raw) return
  const fd = new FormData()
  fd.append('file', file.raw)
  const path = typePathMap[dataType.value] || '/import/suppliers'
  try {
    const res = await api.post(path, fd, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
    batch.value = res.data
    result.value = null
    ElMessage.success(`预览完成：有效 ${res.data.validCount}，错误 ${res.data.errorCount}`)
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
  }
}

async function confirmImport() {
  if (!batch.value?.batchId) return
  const base = typePathMap[dataType.value] || '/import/suppliers'
  const path = `${base}/confirm`
  try {
    const res = await api.post(path, { batchId: batch.value.batchId })
    result.value = res.data
    lastBatchId.value = batch.value.batchId
    batch.value = null
    ElMessage.success('已导入')
    loadRecords()
  } catch (e) {
    ElMessage.error(e.message || '导入失败')
  }
}

const lastBatchId = ref('')

async function rollbackBatch() {
  if (!lastBatchId.value) return
  await doRollback(lastBatchId.value)
}

const errBatchId = ref('')
const errRows = ref([])
const errSummary = ref(null)

async function loadBatchErrors() {
  if (!errBatchId.value) return
  try {
    const res = await api.get(`/import/errors/${errBatchId.value}/summary`)
    errSummary.value = res.data
    errRows.value = res.data.errors || []
  } catch (e) {
    errSummary.value = null
    errRows.value = []
    ElMessage.error(e.message || '查询失败')
  }
}

async function rollbackById() {
  await doRollback(errBatchId.value)
  loadBatchErrors()
}

async function doRollback(batchId) {
  try {
    await ElMessageBox.confirm(`确认回滚批次 ${batchId}？已导入的业务数据将被删除/作废。`, '危险操作', { type: 'warning' })
  } catch { return }
  try {
    const res = await api.post(`/import/rollback/${batchId}`)
    ElMessage.warning(res.data?.message || '已回滚')
    lastBatchId.value = ''
    loadRecords()
  } catch (e) { ElMessage.error(e.message || '回滚失败') }
}

async function downloadTemplate() {
  try {
    const res = await axios.get('/api/import/templates/download', {
      params: { dataType: dataType.value },
      responseType: 'blob',
      headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
    })
    const url = URL.createObjectURL(res.data)
    const a = document.createElement('a')
    a.href = url
    a.download = templateNameMap[dataType.value] || 'supplier-import-template.xlsx'
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error(e.message || '下载失败')
  }
}

async function loadRecords() {
  const res = await api.get('/import/records', { params: { page: recPage.value, size: 10 } })
  records.value = res.data.records
  recTotal.value = res.data.total
}

onMounted(async () => {
  await loadDicts()
  loadRecords()
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; align-items:center; }
</style>

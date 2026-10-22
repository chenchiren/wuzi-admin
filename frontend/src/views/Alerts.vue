<template>
  <div v-loading="loading" class="page">
    <el-tabs v-model="tab">
      <el-tab-pane label="库存预警" name="stock">
    <el-row :gutter="16" style="margin-bottom:16px">
      <el-col :span="12">
        <el-card shadow="hover">
          <div class="kpi">低库存（需补货）</div>
          <div class="num warn">{{ data.lowCount || data.lowStockCount || 0 }}</div>
          <div class="hint">现有量已低于安全库存下限，建议尽快采购补充</div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card shadow="hover">
          <div class="kpi">超库存（积压）</div>
          <div class="num danger">{{ data.overCount || data.overStockCount || 0 }}</div>
          <div class="hint">现有量已超过最大库存上限，存在积压或占用资金</div>
        </el-card>
      </el-col>
    </el-row>
    <el-card>
      <template #header>
        <div class="card-title">低库存明细</div>
        <div class="card-desc">库存 ≤ 安全库存（safetyStock）的物资，表示该物料库存过低，需要补货</div>
      </template>
      <el-table :data="data.lowStock || []" border stripe empty-text="暂无低库存物资">
        <el-table-column prop="materialCode" label="物资编码" min-width="120" />
        <el-table-column prop="materialName" label="物资名称" min-width="140" />
        <el-table-column prop="warehouseId" label="仓库" min-width="100">
          <template #default="{ row }">{{ warehouseLabel(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" min-width="120" />
        <el-table-column prop="qty" label="当前库存" min-width="100" />
        <el-table-column prop="safetyStock" label="安全库存下限" min-width="110" />
        <el-table-column label="缺口" min-width="90">
          <template #default="{ row }">
            <span class="gap-down">-{{ shortOf(row.qty, row.safetyStock) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    <el-card style="margin-top:16px">
      <template #header>
        <div class="card-title">超库存明细</div>
        <div class="card-desc">库存 ≥ 最大库存（maxStock）的物资，表示该物料库存过高，已超出上限</div>
      </template>
      <el-table :data="data.overStock || []" border stripe empty-text="暂无超库存物资">
        <el-table-column prop="materialCode" label="物资编码" min-width="120" />
        <el-table-column prop="materialName" label="物资名称" min-width="140" />
        <el-table-column prop="warehouseId" label="仓库" min-width="100">
          <template #default="{ row }">{{ warehouseLabel(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="batchNo" label="批次" min-width="120" />
        <el-table-column prop="qty" label="当前库存" min-width="100" />
        <el-table-column prop="maxStock" label="最大库存上限" min-width="110" />
        <el-table-column label="超出" min-width="90">
          <template #default="{ row }">
            <span class="gap-up">+{{ overBy(row.qty, row.maxStock) }}</span>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
      </el-tab-pane>

      <el-tab-pane label="临期批次" name="expiry">
        <div class="bar">
          <el-input-number v-model="expiryDays" :min="1" :max="365" controls-position="right" style="width:120px" />
          <span class="muted" style="margin:0 8px">天内到期</span>
          <n-button type="primary" @click="loadExpiry">查询</n-button>
        </div>
        <el-table :data="expiryRows" border stripe>
          <el-table-column prop="batchNo" label="批次" min-width="120" />
          <el-table-column prop="materialId" label="物资ID" min-width="90" />
          <el-table-column prop="warehouseId" label="仓库" min-width="100">
            <template #default="{ row }">{{ warehouseLabel(row.warehouseId) }}</template>
          </el-table-column>
          <el-table-column prop="qty" label="数量" min-width="80" />
          <el-table-column prop="productionDate" label="生产日期" min-width="110" />
          <el-table-column prop="expireDate" label="到期日" min-width="110" />
          <el-table-column prop="remainDays" label="剩余天" min-width="80">
            <template #default="{ row }">
              <el-tag size="small" :type="row.expired ? 'danger' : 'warning'">{{ row.remainDays }}</el-tag>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="预警记录" name="records">
        <div class="bar">
          <el-select v-model="alertType" placeholder="类型" clearable style="width:140px" @change="loadRecords">
            <el-option v-for="o in alertTypeOptions" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
          <n-button type="primary" @click="loadRecords">查询</n-button>
        </div>
        <el-table :data="alertRecords" border stripe>
          <el-table-column prop="id" label="ID" min-width="70" />
          <el-table-column label="类型" min-width="110">
          <template #default="{ row }">
            <StatusTag :value="row.alertType" dict-type="ALERT_TYPE" />
          </template>
        </el-table-column>
          <el-table-column prop="materialId" label="物资" min-width="90" />
          <el-table-column prop="content" label="内容" min-width="180" />
          <el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <StatusTag :value="row.status" dict-type="ALERT_STATUS" />
          </template>
        </el-table-column>
          <el-table-column prop="createdAt" label="时间" min-width="150" />
          <el-table-column label="操作" min-width="280">
            <template #default="{ row }">
              <n-button v-if="row.status==='OPEN'" size="small" text @click="markRead(row)">已读</n-button>
              <n-button v-if="row.status!=='HANDLED'" size="small" text type="success" @click="markHandled(row)">处理</n-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/request'
import { tAlertStatus, tAlertType, loadDicts, dictOptionsOr } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const tab = ref('stock')
const alertTypeOptions = computed(() => dictOptionsOr('ALERT_TYPE', ['LOW_STOCK', 'OVER_STOCK', 'EXPIRY', 'BORROW_OVERDUE']))
const data = ref({ lowStock: [], overStock: [], lowCount: 0, overCount: 0, lowStockCount: 0, overStockCount: 0 })
const loading = ref(true)
const expiryDays = ref(30)
const expiryRows = ref([])
const alertType = ref('')
const alertRecords = ref([])
const warehouses = ref([])

function warehouseLabel(id) {
  if (id == null) return '—'
  const w = warehouses.value.find(x => x.id === id)
  return w ? w.warehouseName : String(id)
}

function shortOf(qty, limit) {
  const d = Number(limit || 0) - Number(qty || 0)
  return d > 0 ? d : 0
}

function overBy(qty, limit) {
  const d = Number(qty || 0) - Number(limit || 0)
  return d > 0 ? d : 0
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/stock/alerts')
    data.value = res.data
  } finally {
    loading.value = false
  }
}

async function loadExpiry() {
  try {
    const res = await api.get('/alerts/expiry', { params: { days: expiryDays.value } })
    expiryRows.value = res.data || []
  } catch (e) { ElMessage.error(e.message || '加载失败') }
}

async function loadRecords() {
  try {
    const res = await api.get('/alerts/record', {
      params: { page: 1, size: 20, alertType: alertType.value || undefined }
    })
    alertRecords.value = res.data.records || []
  } catch (e) { ElMessage.error(e.message || '加载失败') }
}

async function markRead(row) {
  try {
    await api.post(`/alerts/record/${row.id}/read`)
    loadRecords()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

async function markHandled(row) {
  try {
    await api.post(`/alerts/record/${row.id}/handle`)
    loadRecords()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

onMounted(async () => {
  await loadDicts()
  try {
    const w = await api.get('/warehouses/list')
    warehouses.value = w.data || []
  } catch { /* ignore */ }
  await load()
  loadExpiry()
  loadRecords()
})
</script>

<style scoped>
.kpi { color:#888; font-size:13px; }
.num { font-size:28px; font-weight:600; margin-top:8px; }
.num.warn { color:#e6a23c; }
.num.danger { color:#f56c6c; }
.hint { margin-top:6px; font-size:12px; color:#909399; line-height:1.5; }
.card-title { font-size:15px; font-weight:600; color:#303133; }
.card-desc { margin-top:4px; font-size:12px; color:#909399; font-weight:400; line-height:1.5; }
.gap-down { color:#e6a23c; font-weight:600; }
.gap-up { color:#f56c6c; font-weight:600; }
.page { min-height: 320px; }
</style>

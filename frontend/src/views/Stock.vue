<template>
  <el-card v-loading="loading">
    <el-tabs v-model="tab">
      <el-tab-pane label="库存" name="stock">
        <div class="bar">
          <el-select v-model="stockWarehouseId" clearable placeholder="仓库" style="width:150px" @change="onStockWhChange">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
          <el-select v-model="stockLocationId" clearable placeholder="库位" style="width:150px" filterable>
            <el-option v-for="l in stockLocations" :key="l.id" :value="l.id" :label="`${l.locationCode} ${l.locationName || ''}`" />
          </el-select>
          <el-input v-model="batchNo" placeholder="批次号" style="width:160px" clearable />
          <n-button type="primary" @click="loadStock">查询</n-button>
          <n-button @click="exportStock">导出CSV</n-button>
        </div>
        <el-table :data="stocks" border stripe>
          <el-table-column prop="warehouseId" label="仓库" min-width="100" :formatter="(_, __, v) => warehouseLabel(v)" />
          <el-table-column label="库位" min-width="110">
            <template #default="{ row }">{{ locationLabel(row.locationId) }}</template>
          </el-table-column>
          <el-table-column prop="materialId" label="物资" min-width="140" :formatter="(_, __, v) => materialLabel(v)" />
          <el-table-column prop="batchNo" label="批次" min-width="130" />
          <el-table-column prop="qty" label="数量" min-width="100" />
          <el-table-column prop="frozenQty" label="冻结" min-width="90" />
          <el-table-column prop="costTaxIncl" label="含税成本" min-width="110" />
          <el-table-column prop="lastTaxInclPrice" label="最近含税价" min-width="110" />
          <el-table-column prop="updatedAt" label="更新时间" min-width="160" />
        </el-table>
        <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="stockTotal"
          v-model:current-page="stockPage" :page-size="10" @current-change="loadStock" />
      </el-tab-pane>
      <el-tab-pane label="流水" name="records">
        <div class="bar">
          <el-select v-model="recWarehouseId" clearable placeholder="仓库" style="width:150px" @change="onRecWhChange">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
          <el-select v-model="recLocationId" clearable placeholder="库位" style="width:150px" filterable>
            <el-option v-for="l in recLocations" :key="l.id" :value="l.id" :label="`${l.locationCode} ${l.locationName || ''}`" />
          </el-select>
          <el-input v-model="bizType" placeholder="业务类型" style="width:140px" clearable />
          <el-input v-model="recBatchNo" placeholder="批次号" style="width:140px" clearable />
          <n-button type="primary" @click="loadRecords">查询</n-button>
          <n-button @click="exportRecords">导出CSV</n-button>
        </div>
        <el-table :data="records" border stripe>
          <el-table-column label="业务类型" min-width="110">
            <template #default="{ row }">
              <StatusTag :value="row.bizType" dict-type="BIZ_TYPE" />
            </template>
          </el-table-column>
          <el-table-column prop="bizNo" label="业务单号" min-width="150" />
          <el-table-column prop="warehouseId" label="仓库" min-width="100" :formatter="(_, __, v) => warehouseLabel(v)" />
          <el-table-column label="库位" min-width="110">
            <template #default="{ row }">{{ locationLabel(row.locationId) }}</template>
          </el-table-column>
          <el-table-column prop="materialId" label="物资" min-width="140" :formatter="(_, __, v) => materialLabel(v)" />
          <el-table-column prop="batchNo" label="批次" min-width="120" />
          <el-table-column prop="changeQty" label="变动" min-width="90" />
          <el-table-column prop="beforeQty" label="变动前" min-width="90" />
          <el-table-column prop="afterQty" label="变动后" min-width="90" />
          <el-table-column prop="createdAt" label="时间" min-width="160" />
        </el-table>
        <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="recTotal"
          v-model:current-page="recPage" :page-size="10" @current-change="loadRecords" />
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import api from '../api/request'
import { downloadExportCsv } from '../utils/export'
import { ElMessage } from 'element-plus'
import { tBiz, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const tab = ref('stock')
const loading = ref(false)
const stocks = ref([])
const stockTotal = ref(0)
const stockPage = ref(1)
const batchNo = ref('')
const stockWarehouseId = ref(null)
const stockLocationId = ref(null)
const stockLocations = ref([])

const records = ref([])
const recTotal = ref(0)
const recPage = ref(1)
const bizType = ref('')
const recBatchNo = ref('')
const recWarehouseId = ref(null)
const recLocationId = ref(null)
const recLocations = ref([])

const warehouses = ref([])
const materials = ref([])
const allLocations = ref([])

function warehouseLabel(id) {
  if (id == null) return '-'
  const w = warehouses.value.find(x => x.id === id)
  return w ? w.warehouseName : String(id)
}

function locationLabel(id) {
  if (id == null) return '—'
  const l = allLocations.value.find(x => x.id === id)
  return l ? (l.locationCode || l.locationName || `#${id}`) : `#${id}`
}

function materialLabel(id) {
  if (id == null) return '-'
  const m = materials.value.find(x => x.id === id)
  return m ? `${m.materialCode} ${m.materialName}` : String(id)
}

async function loadLocationsFor(whId, target) {
  if (!whId) {
    target.value = []
    return
  }
  try {
    const res = await api.get(`/warehouses/${whId}/locations`)
    target.value = res.data || []
    target.value.forEach(l => {
      if (!allLocations.value.find(x => x.id === l.id)) allLocations.value.push(l)
    })
  } catch { target.value = [] }
}

function onStockWhChange(whId) {
  stockLocationId.value = null
  loadLocationsFor(whId, stockLocations)
}

function onRecWhChange(whId) {
  recLocationId.value = null
  loadLocationsFor(whId, recLocations)
}

async function loadAllLocations() {
  try {
    const w = await api.get('/warehouses/list')
    warehouses.value = w.data || []
    const lists = await Promise.all(warehouses.value.map(x =>
      api.get(`/warehouses/${x.id}/locations`).catch(() => ({ data: [] }))
    ))
    allLocations.value = lists.flatMap(r => r.data || [])
    stockLocations.value = allLocations.value
    recLocations.value = allLocations.value
  } catch { /* ignore */ }
}

async function exportStock() {
  try {
    await downloadExportCsv('/exports/stock', { batchNo: batchNo.value }, '库存')
    ElMessage.success('已导出')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

async function exportRecords() {
  try {
    await downloadExportCsv('/exports/stock-records', { bizType: bizType.value, batchNo: recBatchNo.value }, '库存流水')
    ElMessage.success('已导出')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

async function loadStock() {
  loading.value = true
  try {
    const res = await api.get('/stock', {
      params: {
        page: stockPage.value, size: 10,
        warehouseId: stockWarehouseId.value || undefined,
        locationId: stockLocationId.value || undefined,
        batchNo: batchNo.value || undefined
      }
    })
    stocks.value = res.data.records
    stockTotal.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function loadRecords() {
  loading.value = true
  try {
    const res = await api.get('/stock/records', {
      params: {
        page: recPage.value, size: 10,
        warehouseId: recWarehouseId.value || undefined,
        locationId: recLocationId.value || undefined,
        bizType: bizType.value || undefined,
        batchNo: recBatchNo.value || undefined
      }
    })
    records.value = res.data.records
    recTotal.value = res.data.total
  } finally {
    loading.value = false
  }
}

watch(tab, (t) => {
  if (t === 'stock') loadStock()
  else loadRecords()
})

onMounted(async () => {
  await loadDicts()
  loadStock()
  loadRecords()
  loadAllLocations()
  try {
    const m = await api.get('/materials', { params: { page: 1, size: 200 } })
    materials.value = m.data.records || []
  } catch { /* ignore */ }
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; }
</style>

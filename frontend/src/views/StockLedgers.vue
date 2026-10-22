<template>
  <el-card v-loading="loading">
    <el-tabs v-model="tab">
      <el-tab-pane label="序列号台账" name="serials">
        <div class="bar">
          <el-input v-model="serialQuery.serialNo" placeholder="序列号" style="width:170px" clearable @change="loadSerials" />
          <el-select v-model="serialQuery.materialId" placeholder="物资" filterable clearable style="width:200px" @change="loadSerials">
            <el-option v-for="m in materials" :key="m.id" :label="`${m.materialCode} ${m.materialName}`" :value="m.id" />
          </el-select>
          <el-select v-model="serialQuery.status" placeholder="状态" style="width:130px" clearable @change="loadSerials">
            <el-option v-for="c in serialStatusOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
          <n-button type="primary" @click="loadSerials">查询</n-button>
        </div>
        <el-table :data="serials" border stripe>
          <el-table-column prop="serialNo" label="序列号" min-width="140" />
          <el-table-column prop="materialName" label="物资" min-width="160" />
          <el-table-column prop="warehouseName" label="仓库" min-width="110" />
          <el-table-column prop="locationName" label="库位" min-width="110" />
          <el-table-column prop="batchNo" label="批次" min-width="120" />
          <el-table-column label="状态" min-width="100">
            <template #default="{ row }">
              <StatusTag :value="row.status" dict-type="SERIAL_STATUS" />
            </template>
          </el-table-column>
          <el-table-column prop="boundAt" label="绑定时间" min-width="160" />
          <el-table-column label="操作" min-width="120">
            <template #default="{ row }">
              <n-button size="small" text type="primary" v-perm="'wh:serial:trail'" @click="openSerialTrail(row)">流转记录</n-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="serialTotal"
          v-model:current-page="serialPage" :page-size="10" @current-change="loadSerials" />
      </el-tab-pane>

      <el-tab-pane label="批次台账" name="batches">
        <div class="bar">
          <el-input v-model="batchQuery.batchNo" placeholder="批次号" style="width:170px" clearable @change="loadBatches" />
          <el-select v-model="batchQuery.materialId" placeholder="物资" filterable clearable style="width:200px" @change="loadBatches">
            <el-option v-for="m in materials" :key="m.id" :label="`${m.materialCode} ${m.materialName}`" :value="m.id" />
          </el-select>
          <el-select v-model="batchQuery.warehouseId" placeholder="仓库" clearable style="width:150px" @change="loadBatches">
            <el-option v-for="w in warehouses" :key="w.id" :label="w.warehouseName" :value="w.id" />
          </el-select>
          <n-button type="primary" @click="loadBatches">查询</n-button>
        </div>
        <el-table :data="batches" border stripe>
          <el-table-column prop="batchNo" label="批次号" min-width="140" />
          <el-table-column prop="materialName" label="物资" min-width="160" />
          <el-table-column prop="qty" label="数量" min-width="90" />
          <el-table-column prop="productionDate" label="生产日期" min-width="110" />
          <el-table-column prop="expireDate" label="有效期" min-width="110" />
          <el-table-column prop="warehouseName" label="仓库" min-width="110" />
          <el-table-column label="操作" min-width="120">
            <template #default="{ row }">
              <n-button size="small" text type="primary" v-perm="'wh:batch:trail'" @click="openBatchTrail(row)">批次流水</n-button>
            </template>
          </el-table-column>
        </el-table>
        <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="batchTotal"
          v-model:current-page="batchPage" :page-size="10" @current-change="loadBatches" />
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="trailDlg" :title="trailTitle" size="52%">
      <el-table :data="trails" border size="small">
        <el-table-column label="业务类型" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.bizType" dict-type="BIZ_TYPE" effect="plain" />
          </template>
        </el-table-column>
        <el-table-column prop="bizNo" label="业务单号" min-width="140" />
        <el-table-column prop="materialName" label="物资" min-width="130" />
        <el-table-column prop="warehouseName" label="仓库" min-width="100" />
        <el-table-column prop="changeQty" label="变动" width="90" />
        <el-table-column prop="beforeQty" label="变动前" width="90" />
        <el-table-column prop="afterQty" label="变动后" width="90" />
        <el-table-column prop="createdAt" label="时间" min-width="150" />
        <el-table-column prop="remark" label="备注" min-width="120" />
      </el-table>
      <el-empty v-if="!trails.length" description="暂无流转记录" />
    </el-drawer>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/request'
import { loadDicts, dictOptionsOr, tBiz } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const tab = ref('serials')
const loading = ref(false)

const serials = ref([])
const serialTotal = ref(0)
const serialPage = ref(1)
const serialQuery = reactive({ serialNo: '', materialId: null, status: '' })

const batches = ref([])
const batchTotal = ref(0)
const batchPage = ref(1)
const batchQuery = reactive({ batchNo: '', materialId: null, warehouseId: null })

const trailDlg = ref(false)
const trailTitle = ref('流转记录')
const trails = ref([])

const materials = ref([])
const warehouses = ref([])

const serialStatusOptions = computed(() =>
  dictOptionsOr('SERIAL_STATUS', ['IN_STOCK', 'OUT_STOCK', 'IN_TRANSIT', 'LENT', 'REPAIR', 'SCRAPPED', 'LOST']))

async function loadSerials() {
  loading.value = true
  try {
    const res = await api.get('/stock/serials', {
      params: {
        page: serialPage.value, size: 10,
        serialNo: serialQuery.serialNo || undefined,
        materialId: serialQuery.materialId || undefined,
        status: serialQuery.status || undefined
      }
    })
    serials.value = res.data.records
    serialTotal.value = res.data.total
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function loadBatches() {
  loading.value = true
  try {
    const res = await api.get('/stock/batches', {
      params: {
        page: batchPage.value, size: 10,
        batchNo: batchQuery.batchNo || undefined,
        materialId: batchQuery.materialId || undefined,
        warehouseId: batchQuery.warehouseId || undefined
      }
    })
    batches.value = res.data.records
    batchTotal.value = res.data.total
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function openSerialTrail(row) {
  trailTitle.value = `序列号 ${row.serialNo} 流转记录`
  trailDlg.value = true
  trails.value = []
  try {
    const res = await api.get(`/stock/serials/${encodeURIComponent(row.serialNo)}/trail`)
    trails.value = res.data || []
  } catch (e) {
    ElMessage.error(e.message || '加载流转记录失败')
  }
}

async function openBatchTrail(row) {
  trailTitle.value = `批次 ${row.batchNo} 流水`
  trailDlg.value = true
  trails.value = []
  try {
    const res = await api.get(`/stock/batches/${row.id}/trail`)
    trails.value = res.data || []
  } catch (e) {
    ElMessage.error(e.message || '加载批次流水失败')
  }
}

watch(tab, (t) => {
  if (t === 'serials') loadSerials()
  else loadBatches()
})

onMounted(async () => {
  await loadDicts()
  loadSerials()
  loadBatches()
  try {
    const [w, m] = await Promise.all([
      api.get('/warehouses/list'),
      api.get('/materials', { params: { page: 1, size: 200 } })
    ])
    warehouses.value = w.data || []
    materials.value = m.data.records || []
  } catch { /* ignore */ }
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; flex-wrap:wrap; }
</style>

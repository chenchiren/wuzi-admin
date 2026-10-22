<template>
  <el-card>
    <div class="bar">
      <el-select v-model="status" placeholder="状态" clearable style="width:140px" @change="load">
        <el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'wh:outbound:create'" @click="openCreate">新建出库</n-button>
      <n-button @click="doExport">导出CSV</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="outboundNo" label="出库单号" min-width="180" />
      <el-table-column label="类型" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.outboundType" dict-type="OUTBOUND_TYPE" />
        </template>
      </el-table-column>
      <el-table-column prop="warehouseId" label="仓库" min-width="100" :formatter="(_, __, v) => warehouseLabel(v)" />
      <el-table-column prop="outboundTime" label="出库时间" min-width="160" />
      <el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="OUTBOUND_STATUS" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text @click="openFlow(row)">流程</n-button>
            <n-button size="small" v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'wh:outbound:submit'" text type="primary" @click="submit(row)">提交审批</n-button>
            <n-button size="small" v-if="['DRAFT','REJECTED'].includes(row.status)" text type="warning" @click="cancel(row)">作废</n-button>
            <n-button size="small" v-if="row.status==='APPROVED'" v-perm="'wh:outbound:confirm'" text type="primary" @click="confirm(row)">确认出库</n-button>
            <n-button size="small" v-if="row.status==='CONFIRMED'" v-perm="'wh:outbound:red-flush'" text type="error" @click="redFlush(row)">红冲</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="dialog" title="新建出库单" width="720px">
      <el-form label-width="90px">
        <el-form-item label="仓库">
          <el-select v-model="form.warehouseId" class="w-full" @change="onWhChange">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
        </el-form-item>
        <el-form-item label="出库类型">
          <el-select v-model="form.outboundType" class="w-full">
            <el-option v-for="o in outboundTypeOptions" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="明细">
          <el-table :data="form.items" size="small" border>
            <el-table-column label="物资" min-width="160">
              <template #default="{ row }">
                <el-select v-model="row.materialId" class="w-full">
                  <el-option v-for="m in materials" :key="m.id" :value="m.id" :label="m.materialName" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" min-width="100">
              <template #default="{ row }"><el-input-number v-model="row.qty" :min="1" size="small" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="库位" min-width="130">
              <template #default="{ row }">
                <el-select v-model="row.locationId" size="small" clearable filterable class="w-full" placeholder="可选">
                  <el-option v-for="l in locations" :key="l.id" :value="l.id" :label="`${l.locationCode} ${l.locationName || ''}`" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="批次" min-width="110">
              <template #default="{ row }"><el-input v-model="row.batchNo" size="small" /></template>
            </el-table-column>
            <el-table-column label="操作" min-width="70">
              <template #default="{ $index }"><n-button size="small" text type="error" @click="form.items.splice($index,1)">删</n-button></template>
            </el-table-column>
          </el-table>
          <n-button class="mt-2" @click="form.items.push({ materialId: null, qty: 1, batchNo: '', locationId: null })">加行</n-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="dialog=false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
        </div>
      </template>
    </el-dialog>
  
    <ApprovalFlowDialog
      v-model="flowDialog"
      :biz-type="flowBizType"
      :biz-id="flowBizId"
      :title="flowTitle"
    />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { downloadExportCsv } from '../utils/export'
import ApprovalFlowDialog from '../components/ApprovalFlowDialog.vue'
import { tOutboundStatus, tOutboundType, loadDicts, dictOptionsOr } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const status = ref('')
const statusOptions = computed(() =>
  dictOptionsOr('OUTBOUND_STATUS', ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'CONFIRMED', 'RED_FLUSHED', 'REJECTED'])
)
const outboundTypeOptions = computed(() =>
  dictOptionsOr('OUTBOUND_TYPE', ['USE', 'OTHER', 'TRANSFER', 'SCRAP'])
)
const dialog = ref(false)
const warehouses = ref([])
const materials = ref([])
const locations = ref([])

function warehouseLabel(id) {
  if (id == null) return '-'
  const w = warehouses.value.find(x => x.id === id)
  return w ? w.warehouseName : String(id)
}
const form = reactive({
  warehouseId: null, outboundType: 'USE',
  items: [{ materialId: null, qty: 1, batchNo: '', locationId: null }]
})

async function onWhChange(whId) {
  form.items.forEach(r => { r.locationId = null })
  if (!whId) { locations.value = []; return }
  try {
    const res = await api.get(`/warehouses/${whId}/locations`)
    locations.value = res.data || []
  } catch { locations.value = [] }
}

async function doExport() {
  try {
    await downloadExportCsv('/exports/outbounds', { status: status.value }, '出库单')
    ElMessage.success('已导出')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

async function load() {
  const res = await api.get('/outbound/orders', { params: { page: page.value, size: 10, status: status.value } })
  rows.value = res.data.records
  total.value = res.data.total
}

function openCreate() {
  form.warehouseId = null
  form.outboundType = 'USE'
  form.items = [{ materialId: null, qty: 1, batchNo: '', locationId: null }]
  locations.value = []
  dialog.value = true
}

async function save() {
  try {
    await api.post('/outbound/orders', { ...form })
    ElMessage.success('已创建')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '创建失败') }
}

async function submit(row) {
  try {
    await api.post(`/outbound/orders/${row.id}/submit`)
    ElMessage.success('已提交审批')
    load()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
}

async function cancel(row) {
  await ElMessageBox.confirm(`作废出库单 ${row.outboundNo}？`, '提示')
  try {
    await api.post(`/outbound/orders/${row.id}/cancel`)
    ElMessage.success('已作废')
    load()
  } catch (e) { ElMessage.error(e.message || '作废失败') }
}

async function confirm(row) {
  await ElMessageBox.confirm('确认出库？将扣减库存', '提示')
  try {
    await api.post(`/outbound/orders/${row.id}/confirm`)
    ElMessage.success('已确认')
    load()
  } catch (e) { ElMessage.error(e.message || '确认失败') }
}

async function redFlush(row) {
  await ElMessageBox.confirm('确认红冲该出库单？', '提示')
  try {
    await api.post(`/outbound/orders/${row.id}/red-flush`)
    ElMessage.success('已红冲')
    load()
  } catch (e) { ElMessage.error(e.message || '红冲失败') }
}

const flowDialog = ref(false)
const flowBizType = ref('')
const flowBizId = ref(null)
const flowTitle = ref('')

function openFlow(row) {
  flowBizType.value = 'OUTBOUND'
  flowBizId.value = row.id
  flowTitle.value = `审批流程 · ${row.outboundNo || row.id}`
  flowDialog.value = true
}

onMounted(async () => {
  await loadDicts()
  load()
  const [w, m] = await Promise.all([
    api.get('/warehouses/list'),
    api.get('/materials', { params: { page: 1, size: 100 } })
  ])
  warehouses.value = w.data
  materials.value = m.data.records
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; }
</style>

<template>
  <el-card>
    <div class="bar">
      <el-select v-model="status" placeholder="状态" clearable style="width:140px" @change="load">
        <el-option v-for="s in statuses" :key="s" :value="s" :label="tInboundStatus(s)" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'wh:inbound:create'" @click="openFromOrder">按订单入库</n-button>
      <n-button type="success" v-perm="'wh:inbound:create'" @click="openCreate">新建入库</n-button>
      <n-button @click="doExport">导出CSV</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="inboundNo" label="入库单号" min-width="180" />
      <el-table-column label="类型" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.inboundType" dict-type="INBOUND_TYPE" />
        </template>
      </el-table-column>
      <el-table-column prop="warehouseId" label="仓库" min-width="100" :formatter="(_, __, v) => warehouseLabel(v)" />
      <el-table-column prop="sourceNo" label="来源订单" min-width="150" />
      <el-table-column prop="inboundTime" label="入库时间" min-width="160" />
      <el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="INBOUND_STATUS" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="300">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button v-if="row.status==='DRAFT'" v-perm="'wh:inbound:confirm'" size="small" text type="primary" @click="confirm(row)">确认入库</n-button>
            <n-button v-if="row.status==='DRAFT'" v-perm="'wh:inbound:create'" size="small" text type="warning" @click="cancel(row)">作废</n-button>
            <n-button v-if="row.status==='CONFIRMED'" v-perm="'wh:inbound:red-flush'" size="small" text type="error" @click="redFlush(row)">红冲</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="fromOrderDialog" title="按采购订单生成入库单" width="520px">
      <el-form label-width="90px">
        <el-form-item label="采购订单" required>
          <el-select v-model="fromOrderForm.purchaseOrderId" filterable class="w-full" placeholder="选择订单（自动带出未入库明细）">
            <el-option v-for="o in orders" :key="o.id" :value="o.id" :label="`${o.orderNo} · ${tPurchaseOrderStatus(o.status)}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="入库仓库" required>
          <el-select v-model="fromOrderForm.warehouseId" class="w-full" placeholder="选择仓库" @change="onFromOrderWh">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
        </el-form-item>
        <el-form-item label="默认库位">
          <el-select v-model="fromOrderForm.locationId" clearable filterable class="w-full" placeholder="可选，写入各明细行">
            <el-option v-for="l in fromOrderLocations" :key="l.id" :value="l.id" :label="`${l.locationCode} ${l.locationName || ''}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="fromOrderForm.remark" /></el-form-item>
        <div class="text-xs leading-5 text-slate-500">系统会按订单自动带出尚未入库的数量、税价；确认入库后写入库存并回写订单收货。</div>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="fromOrderDialog=false">取消</n-button>
          <n-button type="primary" :disabled="!fromOrderForm.purchaseOrderId || !fromOrderForm.warehouseId" :loading="saving" @click="saveFromOrder">生成入库单</n-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="dialog" title="新建入库单" width="720px">
      <el-form label-width="90px">
        <el-form-item label="仓库">
          <el-select v-model="form.warehouseId" class="w-full" @change="onManualWh">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商">
          <el-select v-model="form.supplierId" filterable clearable class="w-full" placeholder="可选">
            <el-option v-for="s in suppliers" :key="s.id" :value="s.id" :label="s.supplierName" />
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
              <template #default="{ row }"><el-input-number v-model="row.inQty" :min="1" size="small" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="库位" min-width="130">
              <template #default="{ row }">
                <el-select v-model="row.locationId" size="small" clearable filterable class="w-full" placeholder="可选">
                  <el-option v-for="l in manualLocations" :key="l.id" :value="l.id" :label="`${l.locationCode} ${l.locationName || ''}`" />
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
          <n-button class="mt-2" @click="form.items.push({ materialId: null, inQty: 1, batchNo: '', locationId: null })">加行</n-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="dialog=false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
        </div>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { downloadExportCsv } from '../utils/export'
import { tInboundStatus, tInboundType, tPurchaseOrderStatus, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const status = ref('')
const statuses = ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'CONFIRMED', 'REJECTED', 'CANCELLED', 'RED_FLUSHED']
const dialog = ref(false)
const fromOrderDialog = ref(false)
const saving = ref(false)
const warehouses = ref([])
const materials = ref([])
const suppliers = ref([])
const orders = ref([])
const manualLocations = ref([])
const fromOrderLocations = ref([])
const fromOrderForm = reactive({ purchaseOrderId: null, warehouseId: null, locationId: null, remark: '' })

function warehouseLabel(id) {
  if (id == null) return '-'
  const w = warehouses.value.find(x => x.id === id)
  return w ? w.warehouseName : String(id)
}

function inboundTypeLabel(v) {
  return tInboundType(v)
}
const form = reactive({
  warehouseId: null, supplierId: null,
  items: [{ materialId: null, inQty: 1, batchNo: '', locationId: null }]
})

async function loadLocs(whId, target) {
  if (!whId) { target.value = []; return }
  try {
    const res = await api.get(`/warehouses/${whId}/locations`)
    target.value = res.data || []
  } catch { target.value = [] }
}

function onManualWh(whId) {
  form.items.forEach(r => { r.locationId = null })
  loadLocs(whId, manualLocations)
}

function onFromOrderWh(whId) {
  fromOrderForm.locationId = null
  loadLocs(whId, fromOrderLocations)
}

async function doExport() {
  try {
    await downloadExportCsv('/exports/inbounds', { status: status.value }, '入库单')
    ElMessage.success('已导出')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

async function load() {
  const res = await api.get('/inbound/orders', { params: { page: page.value, size: 10, status: status.value } })
  rows.value = res.data.records
  total.value = res.data.total
}

function openCreate() {
  form.warehouseId = null
  form.supplierId = null
  form.items = [{ materialId: null, inQty: 1, batchNo: '', locationId: null }]
  manualLocations.value = []
  dialog.value = true
}

async function openFromOrder() {
  fromOrderForm.purchaseOrderId = null
  fromOrderForm.warehouseId = form.warehouseId || warehouses.value[0]?.id || null
  fromOrderForm.locationId = null
  fromOrderForm.remark = ''
  fromOrderLocations.value = []
  if (fromOrderForm.warehouseId) loadLocs(fromOrderForm.warehouseId, fromOrderLocations)
  fromOrderDialog.value = true
  if (!orders.value.length) {
    try {
      const res = await api.get('/purchase/orders', { params: { page: 1, size: 50 } })
      orders.value = (res.data.records || []).filter(o =>
        ['APPROVED', 'CONFIRMED', 'ARRIVED', 'PART_RECEIVED', 'RECEIVED', 'DRAFT'].includes(o.status)
      )
    } catch { /* ignore */ }
  }
}

async function saveFromOrder() {
  saving.value = true
  try {
    await api.post('/inbound/orders', {
      purchaseOrderId: fromOrderForm.purchaseOrderId,
      warehouseId: fromOrderForm.warehouseId,
      locationId: fromOrderForm.locationId || undefined,
      remark: fromOrderForm.remark || undefined,
      items: []
    })
    ElMessage.success('已生成入库单草稿，请核对后确认入库')
    fromOrderDialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '生成入库失败')
  } finally {
    saving.value = false
  }
}

async function save() {
  try {
    await api.post('/inbound/orders', { ...form })
    ElMessage.success('已创建')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '创建失败') }
}

async function confirm(row) {
  await ElMessageBox.confirm('确认入库？将写入库存', '提示')
  try {
    await api.post(`/inbound/orders/${row.id}/confirm`)
    ElMessage.success('已确认')
    load()
  } catch (e) { ElMessage.error(e.message || '确认失败') }
}

async function cancel(row) {
  await ElMessageBox.confirm(`作废入库单 ${row.inboundNo}？`, '提示')
  try {
    await api.post(`/inbound/orders/${row.id}/cancel`)
    ElMessage.success('已作废')
    load()
  } catch (e) { ElMessage.error(e.message || '作废失败') }
}

async function redFlush(row) {
  await ElMessageBox.confirm('确认红冲该入库单？将冲回库存并写负向流水。', '红冲确认', { type: 'warning' })
  try {
    await api.post(`/red-flush/inbound/${row.id}`)
    ElMessage.success('已红冲')
    load()
  } catch (e) { ElMessage.error(e.message || '红冲失败') }
}

onMounted(async () => {
  await loadDicts()
  load()
  const [w, m, s] = await Promise.all([
    api.get('/warehouses/list'),
    api.get('/materials', { params: { page: 1, size: 100 } }),
    api.get('/suppliers', { params: { page: 1, size: 100 } }).catch(() => ({ data: { records: [] } }))
  ])
  warehouses.value = w.data
  materials.value = m.data.records
  suppliers.value = s.data?.records || []
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; }
</style>

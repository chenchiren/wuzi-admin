<template>
  <el-card>
    <div class="bar">
      <el-select v-model="status" placeholder="状态" clearable style="width:140px" @change="load">
        <el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'po:arrival:create'" @click="openCreate">按订单到货</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="arrivalNo" label="到货单号" min-width="180" />
      <el-table-column label="来源订单" min-width="140">
        <template #default="{ row }">
          <span v-if="row.orderNo || row.orderId" class="text-primary">{{ row.orderNo || `#${row.orderId}` }}</span>
          <span v-else class="text-slate-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="供应商" min-width="120">
        <template #default="{ row }">{{ row.supplierName || row.supplierId || '—' }}</template>
      </el-table-column>
      <el-table-column prop="arrivalTime" label="到货时间" min-width="160" />
      <el-table-column prop="transportInfo" label="运输信息" min-width="140" />
      <el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="PURCHASE_ARRIVAL_STATUS" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="320">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text @click="openFlow(row)">流程</n-button>
            <n-button size="small" v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'po:arrival:submit'" text type="primary" @click="submit(row)">提交审批</n-button>
            <n-button size="small" v-if="row.status==='APPROVED'" v-perm="'po:arrival:complete'" text type="success" @click="complete(row)">确认到货</n-button>
            <n-button
              size="small"
              v-if="['APPROVED','COMPLETED'].includes(row.status)"
              v-perm="'wh:inbound:create'"
              text
              type="primary"
              @click="genInbound(row)"
            >生成入库</n-button>
            <n-button size="small" v-if="row.status==='DRAFT'" v-perm="'po:arrival:cancel'" text type="warning" @click="cancel(row)">作废</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="inboundDialog" title="按订单生成入库单" width="420px">
      <el-form label-width="90px">
        <el-form-item label="订单">
          <el-input :model-value="inboundForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="入库仓库" required>
          <el-select v-model="inboundForm.warehouseId" class="w-full" placeholder="选择仓库">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
        </el-form-item>
        <div class="text-xs leading-5 text-slate-500">按订单带出未入库明细与税价，生成草稿后到「入库」确认。</div>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="inboundDialog=false">取消</n-button>
          <n-button type="primary" @click="saveInbound">生成</n-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="dialog" title="按采购订单生成到货单" width="480px">
      <el-form label-width="100px">
        <el-form-item label="采购订单">
          <el-select v-model="form.orderId" class="w-full">
            <el-option v-for="o in orders" :key="o.id" :value="o.id" :label="`${o.orderNo} · ${tPurchaseOrderStatus(o.status)}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="运输信息"><el-input v-model="form.transportInfo" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="dialog=false">取消</n-button>
          <n-button type="primary" @click="save">生成</n-button>
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
import { tArrivalStatus, tPurchaseOrderStatus, loadDicts, dictOptionsOr } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'
import ApprovalFlowDialog from '../components/ApprovalFlowDialog.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const status = ref('')
const statusOptions = computed(() =>
  dictOptionsOr('PURCHASE_ARRIVAL_STATUS', ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'COMPLETED', 'CANCELLED', 'REJECTED'])
)
const statuses = computed(() => statusOptions.value.map((s) => s.value))
const dialog = ref(false)
const orders = ref([])
const warehouses = ref([])
const inboundDialog = ref(false)
const inboundForm = reactive({ orderId: null, orderNo: '', warehouseId: null })
const form = reactive({ orderId: null, transportInfo: '', remark: '' })

async function load() {
  const res = await api.get('/purchase/arrivals', { params: { page: page.value, size: 10, status: status.value } })
  rows.value = res.data.records
  total.value = res.data.total
}

function openCreate() {
  form.orderId = null
  form.transportInfo = ''
  form.remark = ''
  dialog.value = true
}

async function save() {
  if (!form.orderId) { ElMessage.warning('请选择采购订单'); return }
  try {
    await api.post('/purchase/arrivals', { ...form })
    ElMessage.success('已生成到货单')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '生成失败') }
}

async function submit(row) {
  try {
    await api.post(`/purchase/arrivals/${row.id}/submit`)
    ElMessage.success('已提交审批')
    load()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
}

async function complete(row) {
  await ElMessageBox.confirm('确认到货完成？将累计订单收货数量', '提示')
  try {
    await api.post(`/purchase/arrivals/${row.id}/complete`)
    ElMessage.success('已确认到货，可继续「生成入库」写入库存')
    load()
  } catch (e) { ElMessage.error(e.message || '确认失败') }
}

async function genInbound(row) {
  if (!warehouses.value.length) {
    try {
      const w = await api.get('/warehouses/list')
      warehouses.value = w.data || []
    } catch { /* ignore */ }
  }
  inboundForm.orderId = row.orderId
  inboundForm.orderNo = row.orderNo || `#${row.orderId}`
  inboundForm.warehouseId = warehouses.value[0]?.id || null
  inboundDialog.value = true
}

async function saveInbound() {
  if (!inboundForm.warehouseId) {
    ElMessage.warning('请选择入库仓库')
    return
  }
  try {
    await api.post('/inbound/orders', {
      purchaseOrderId: inboundForm.orderId,
      warehouseId: inboundForm.warehouseId,
      items: []
    })
    ElMessage.success('已生成入库单草稿，请到「入库」确认入库')
    inboundDialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '生成入库失败')
  }
}

async function cancel(row) {
  await ElMessageBox.confirm('确认作废该到货单？', '提示')
  try {
    await api.post(`/purchase/arrivals/${row.id}/cancel`)
    ElMessage.success('已作废')
    load()
  } catch (e) { ElMessage.error(e.message || '作废失败') }
}

const flowDialog = ref(false)
const flowBizType = ref('')
const flowBizId = ref(null)
const flowTitle = ref('')

function openFlow(row) {
  flowBizType.value = 'PURCHASE_ARRIVAL'
  flowBizId.value = row.id
  flowTitle.value = `审批流程 · ${row.arrivalNo || row.id}`
  flowDialog.value = true
}

onMounted(async () => {
  await loadDicts()
  load()
  const o = await api.get('/purchase/orders', { params: { page: 1, size: 50 } })
  orders.value = o.data.records
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; }
</style>

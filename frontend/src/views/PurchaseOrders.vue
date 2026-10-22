<template>
  <el-card>
    <div class="bar">
      <n-button type="primary" v-perm="'po:order:create'" @click="openCreate">新建采购订单</n-button>
      <n-button @click="doExport">导出CSV</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="orderNo" label="订单号" min-width="180" />
      <el-table-column label="来源" min-width="150">
        <template #default="{ row }">
          <span v-if="row.inquiryNo || row.applyId" class="text-primary">
            {{ row.inquiryNo || (row.applyId ? `申请#${row.applyId}` : '') }}
          </span>
          <span v-else class="text-slate-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="PURCHASE_ORDER_STATUS" />
        </template>
      </el-table-column>
      <el-table-column prop="taxInclusiveAmount" label="含税金额" min-width="110" />
      <el-table-column prop="taxExclusiveAmount" label="不含税" min-width="110" />
      <el-table-column prop="taxAmount" label="税额" min-width="100" />
      <el-table-column prop="taxRateSummary" label="税率分布" min-width="140" />
      <el-table-column prop="orderDate" label="下单日" min-width="110" />
      <el-table-column label="操作" min-width="320">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text @click="openFlow(row)">流程</n-button>
            <n-button size="small" text @click="openDetail(row)">明细</n-button>
            <n-button size="small" v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'po:order:submit'" text type="primary" @click="submit(row)">提交审批</n-button>
            <n-button
              size="small"
              v-if="canArrive(row.status)"
              v-perm="'po:arrival:create'"
              text
              type="success"
              @click="genArrival(row)"
            >生成到货</n-button>
            <n-button
              size="small"
              v-if="canInbound(row.status)"
              v-perm="'wh:inbound:create'"
              text
              type="primary"
              @click="genInbound(row)"
            >入库</n-button>
            <n-button size="small" v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'po:order:cancel'" text type="warning" @click="cancel(row)">作废</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="dialog" title="新建采购订单（含税价）" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="form.supplierId" class="w-full" placeholder="请选择供应商" filterable>
            <el-option v-for="s in suppliers" :key="s.id" :value="s.id" :label="`${s.supplierCode} ${s.supplierName} (${s.defaultTaxRate}%)`" />
          </el-select>
        </el-form-item>
        <el-form-item label="明细" prop="items">
          <el-table :data="form.items" size="small" border>
            <el-table-column label="物资" min-width="180">
              <template #default="{ row }">
                <el-select v-model="row.materialId" class="w-full" placeholder="请选择物资" filterable>
                  <el-option v-for="m in materials" :key="m.id" :value="m.id" :label="m.materialName" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" min-width="100">
              <template #default="{ row }"><el-input-number v-model="row.qty" :min="1" size="small" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="含税单价" min-width="120">
              <template #default="{ row }"><el-input-number v-model="row.taxInclusivePrice" :min="0" :precision="2" size="small" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="税率" min-width="140">
              <template #default="{ row }">
                <TaxRateSelect v-model:tax-code="row.taxCode" v-model:tax-rate="row.taxRate" />
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="70">
              <template #default="{ $index }"><n-button size="small" text type="error" @click="form.items.splice($index,1)">删</n-button></template>
            </el-table-column>
          </el-table>
          <n-button class="mt-2" @click="form.items.push({ materialId:null, qty:1, taxInclusivePrice:null, taxCode:null, taxRate:13 })">加行</n-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="dialog=false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
        </div>
      </template>
    </el-dialog>
  
    <el-dialog v-model="inboundDlg" title="按订单生成入库单" width="420px">
      <el-form label-width="90px">
        <el-form-item label="订单">
          <el-input :model-value="inboundForm.orderNo" disabled />
        </el-form-item>
        <el-form-item label="入库仓库" required>
          <el-select v-model="inboundForm.warehouseId" class="w-full" placeholder="选择仓库">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
        </el-form-item>
        <div class="text-xs leading-5 text-slate-500">自动带出订单未入库明细与税价，生成草稿后到「入库」确认。</div>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="inboundDlg=false">取消</n-button>
          <n-button type="primary" @click="saveInbound">生成</n-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDlg" title="订单明细" size="55%">
      <div v-if="detail" class="mb-3 text-sm text-slate-600">
        {{ detail.order?.orderNo }} · {{ detail.order?.status }} · 含税合计 {{ money(detail.order?.taxInclusiveAmount) }}
      </div>
      <el-table :data="detail?.items || []" border size="small">
        <el-table-column prop="materialId" label="物资ID" width="80" />
        <el-table-column prop="qty" label="数量" width="80" />
        <el-table-column prop="taxInclusivePrice" label="含税价" width="100" />
        <el-table-column label="含税金额" width="110">
          <template #default="{ row }">{{ money(row.taxInclusiveAmount) }}</template>
        </el-table-column>
        <el-table-column label="税额" width="100">
          <template #default="{ row }">{{ money(row.taxAmount) }}</template>
        </el-table-column>
        <el-table-column prop="receivedQty" label="已收" width="80" />
        <el-table-column prop="unit" label="单位" width="70" />
      </el-table>
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
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/request'
import { downloadExportCsv } from '../utils/export'
import { tPurchaseOrderStatus, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'
import ApprovalFlowDialog from '../components/ApprovalFlowDialog.vue'
import TaxRateSelect from '../components/TaxRateSelect.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const dialog = ref(false)
const detailDlg = ref(false)
const detail = ref(null)
const suppliers = ref([])
const materials = ref([])
const form = reactive({ supplierId: null, items: [{ materialId: null, qty: 1, taxInclusivePrice: null, taxCode: null, taxRate: 13 }] })
const formRef = ref()
const rules = {
  supplierId: [{ required: true, message: '请选择供应商', trigger: 'change' }],
  items: [{
    validator: (rule, value, callback) => {
      if (!value || !value.length) {
        callback(new Error('请添加订单明细'))
        return
      }
      for (let i = 0; i < value.length; i++) {
        const it = value[i]
        const row = i + 1
        if (it.materialId == null || it.materialId === '') {
          callback(new Error(`第${row}行请选择物资`))
          return
        }
        if (it.qty == null || Number(it.qty) <= 0) {
          callback(new Error(`第${row}行数量须大于0`))
          return
        }
        if (it.taxInclusivePrice == null) {
          callback(new Error(`第${row}行请填写含税单价`))
          return
        }
      }
      callback()
    },
    trigger: 'change'
  }]
}
const money = v => v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

async function openDetail(row) {
  try {
    const res = await api.get(`/purchase/orders/${row.id}`)
    detail.value = res.data
    detailDlg.value = true
  } catch (e) { ElMessage.error(e.message || '加载明细失败') }
}

async function doExport() {
  try {
    await downloadExportCsv('/exports/purchase-orders', {}, '采购订单')
    ElMessage.success('已导出')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

async function load() {
  const res = await api.get('/purchase/orders', { params: { page: page.value, size: 10 } })
  rows.value = res.data.records
  total.value = res.data.total
}

function openCreate() {
  Object.assign(form, { supplierId: null, items: [{ materialId: null, qty: 1, taxInclusivePrice: null, taxCode: null, taxRate: 13 }] })
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function save() {
  try {
    await formRef.value.validate()
  } catch {
    ElMessage.warning('请完善必填项后再保存')
    return
  }
  try {
    await api.post('/purchase/orders', form)
    ElMessage.success('已创建')
    dialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '创建失败')
  }
}

async function submit(row) {
  try {
    await api.post(`/purchase/orders/${row.id}/submit`)
    ElMessage.success('已提交审批')
    load()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
}

function canArrive(status) {
  return ['APPROVED', 'CONFIRMED', 'ARRIVED', 'PART_RECEIVED'].includes(status)
}

function canInbound(status) {
  return ['APPROVED', 'CONFIRMED', 'ARRIVED', 'PART_RECEIVED', 'RECEIVED'].includes(status)
}

async function genArrival(row) {
  await ElMessageBox.confirm(
    `按订单「${row.orderNo}」生成到货单？系统会带出未收货明细，可到「到货验收」登记合格数并提交审批。`,
    '生成到货'
  )
  try {
    await api.post('/purchase/arrivals', { orderId: row.id })
    ElMessage.success('已生成到货单，请到「到货验收」继续')
    load()
  } catch (e) {
    ElMessage.error(e.message || '生成到货失败')
  }
}

const inboundDlg = ref(false)
const inboundForm = reactive({ orderId: null, orderNo: '', warehouseId: null })
const warehouses = ref([])

async function genInbound(row) {
  if (!warehouses.value.length) {
    try {
      const w = await api.get('/warehouses/list')
      warehouses.value = w.data || []
    } catch { /* ignore */ }
  }
  inboundForm.orderId = row.id
  inboundForm.orderNo = row.orderNo
  inboundForm.warehouseId = warehouses.value[0]?.id || null
  inboundDlg.value = true
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
    inboundDlg.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '生成入库失败')
  }
}

async function cancel(row) {
  try {
    await api.post(`/purchase/orders/${row.id}/cancel`)
    ElMessage.success('已作废')
    load()
  } catch (e) { ElMessage.error(e.message || '作废失败') }
}

const flowDialog = ref(false)
const flowBizType = ref('')
const flowBizId = ref(null)
const flowTitle = ref('')

function openFlow(row) {
  flowBizType.value = 'PURCHASE_ORDER'
  flowBizId.value = row.id
  flowTitle.value = `审批流程 · ${row.orderNo || row.id}`
  flowDialog.value = true
}

onMounted(async () => {
  await loadDicts()
  load()
  const [s, m] = await Promise.all([
    api.get('/suppliers', { params: { page: 1, size: 50 } }),
    api.get('/materials', { params: { page: 1, size: 50 } })
  ])
  suppliers.value = s.data.records
  materials.value = m.data.records
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; }
</style>

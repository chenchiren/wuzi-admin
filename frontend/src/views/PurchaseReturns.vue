<template>
  <el-card v-loading="loading">
    <div class="bar">
      <el-select v-model="status" placeholder="状态" clearable style="width:140px" @change="load">
        <el-option v-for="s in statuses" :key="s" :value="s" :label="tPurchaseReturnStatus(s)" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'po:return:create'" @click="openCreate">新建采购退货</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="returnNo" label="退货单号" min-width="180" />
      <el-table-column label="原订单" min-width="140">
        <template #default="{ row }">{{ orderLabel(row.orderId) }}</template>
      </el-table-column>
      <el-table-column prop="supplierId" label="供应商" min-width="100">
        <template #default="{ row }">{{ supplierLabel(row.supplierId) }}</template>
      </el-table-column>
      <el-table-column prop="reason" label="原因" min-width="140" />
      <el-table-column prop="status" label="状态" min-width="120">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="PURCHASE_RETURN_STATUS" />
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="160" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text @click="openFlow(row)">流程</n-button>
            <n-button v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'po:return:submit'" size="small" text type="primary" @click="submit(row)">提交</n-button>
            <n-button v-if="row.status==='PENDING_APPROVAL' || row.status==='APPROVED'" v-perm="'po:return:confirm'" size="small" text type="success" @click="confirmReturn(row)">确认退货</n-button>
            <n-button v-if="['DRAFT','PENDING_APPROVAL'].includes(row.status)" v-perm="'po:return:cancel'" size="small" text type="warning" @click="cancel(row)">取消</n-button>
            <n-button v-if="row.status==='DRAFT'" v-perm="'po:return:delete'" size="small" text type="error" @click="remove(row)">删除</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="dialog" title="新建采购退货" width="720px">
      <el-form label-width="100px">
        <el-form-item label="采购订单">
          <el-select v-model="form.orderId" class="w-full" @change="onOrderChange">
            <el-option v-for="o in orders" :key="o.id" :value="o.id" :label="`${o.orderNo || o.id}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="原因"><el-input v-model="form.reason" /></el-form-item>
        <el-form-item label="退货明细">
          <el-table :data="form.items" size="small" border>
            <el-table-column label="物资" min-width="160">
              <template #default="{ row }">
                <el-select v-model="row.materialId" class="w-full">
                  <el-option v-for="m in materials" :key="m.id" :value="m.id" :label="`${m.materialCode} ${m.materialName}`" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="退货数量" min-width="110">
              <template #default="{ row }"><el-input-number v-model="row.returnQty" :min="0.01" size="small" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="含税单价" min-width="110">
              <template #default="{ row }"><el-input-number v-model="row.taxInclusivePrice" :min="0" :precision="2" size="small" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="税率" min-width="140">
              <template #default="{ row }">
                <TaxRateSelect v-model:tax-code="row.taxCode" v-model:tax-rate="row.taxRate" />
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="60">
              <template #default="{ $index }"><n-button size="small" text type="error" @click="form.items.splice($index,1)">删</n-button></template>
            </el-table-column>
          </el-table>
          <n-button class="mt-2" @click="form.items.push({ materialId: null, returnQty: 1, taxInclusivePrice: 0, taxCode: null, taxRate: 13 })">加行</n-button>
        </el-form-item>
      </el-form>
      <template #footer>
        
        <div class="flex items-center justify-end gap-2">
        <n-button @click="dialog=false">取消</n-button>
        <n-button type="primary" :disabled="!form.orderId" @click="save">保存</n-button>
        </div>
      
      </template>
    </el-dialog>

    <ApprovalFlowDialog v-model="flowDialog" :biz-type="flowBizType" :biz-id="flowBizId" :title="flowTitle" />
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { tPurchaseReturnStatus, tBiz, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'
import ApprovalFlowDialog from '../components/ApprovalFlowDialog.vue'
import TaxRateSelect from '../components/TaxRateSelect.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const status = ref('')
const statuses = ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED', 'RETURNED']
const loading = ref(false)
const orders = ref([])
const materials = ref([])
const suppliers = ref([])
const dialog = ref(false)

const flowDialog = ref(false)
const flowBizType = ref('PURCHASE_RETURN')
const flowBizId = ref(null)
const flowTitle = ref('')

const form = reactive({
  orderId: null, reason: '',
  items: [{ materialId: null, returnQty: 1, taxInclusivePrice: 0, taxCode: null, taxRate: 13 }]
})

function supplierLabel(id) {
  const s = suppliers.value.find(x => x.id === id)
  return s ? s.supplierName : (id || '-')
}

function orderLabel(id) {
  const o = orders.value.find(x => x.id === id)
  return o ? o.orderNo : (id == null ? '—' : `#${id}`)
}


async function load() {
  loading.value = true
  try {
    const res = await api.get('/purchase/returns', { params: { page: page.value, size: 10, status: status.value } })
    rows.value = res.data.records
    total.value = res.data.total
  } catch (e) { ElMessage.error(e.message || '加载失败') } finally { loading.value = false }
}

function openCreate() {
  form.orderId = null
  form.reason = ''
  form.items = [{ materialId: null, returnQty: 1, taxInclusivePrice: 0, taxCode: null, taxRate: 13 }]
  dialog.value = true
}

function onOrderChange(id) {
  const o = orders.value.find(x => x.id === id)
  if (o && o.supplierId) form.supplierId = o.supplierId
}

async function save() {
  const o = orders.value.find(x => x.id === form.orderId)
  try {
    await api.post('/purchase/returns', {
      orderId: form.orderId,
      supplierId: o ? o.supplierId : null,
      reason: form.reason,
      lines: form.items
    })
    ElMessage.success('退货单已创建')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '创建失败') }
}

async function submit(row) {
  try {
    await api.post(`/purchase/returns/${row.id}/submit`)
    ElMessage.success('已提交')
    load()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
}

async function confirmReturn(row) {
  await ElMessageBox.confirm('确认退货？将红冲入库金额。', '提示')
  try {
    await api.post(`/purchase/returns/${row.id}/confirm`)
    ElMessage.success('已确认退货')
    load()
  } catch (e) { ElMessage.error(e.message || '确认失败') }
}

async function cancel(row) {
  await ElMessageBox.confirm('取消该退货单？', '提示')
  try {
    await api.post(`/purchase/returns/${row.id}/cancel`)
    ElMessage.success('已取消')
    load()
  } catch (e) { ElMessage.error(e.message || '取消失败') }
}

async function remove(row) {
  await ElMessageBox.confirm(`删除退货单 ${row.returnNo}？`, '提示')
  try {
    await api.delete(`/purchase/returns/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

function openFlow(row) {
  flowBizType.value = 'PURCHASE_RETURN'
  flowBizId.value = row.id
  flowTitle.value = `采购退货-${row.returnNo}`
  flowDialog.value = true
}

onMounted(async () => {
  await loadDicts()
  load()
  try {
    const [o, m, s] = await Promise.all([
      api.get('/purchase/orders', { params: { page: 1, size: 100 } }),
      api.get('/materials', { params: { page: 1, size: 200 } }),
      api.get('/suppliers', { params: { page: 1, size: 200 } })
    ])
    orders.value = o.data.records || []
    materials.value = m.data.records || []
    suppliers.value = s.data.records || []
  } catch { /* ignore */ }
})
</script>

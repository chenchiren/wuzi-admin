<template>
  <el-card>
    <div class="bar">
      <el-select v-model="status" placeholder="状态" clearable style="width:140px" @change="load">
        <el-option v-for="s in statuses" :key="s" :value="s" :label="tTransferStatus(s)" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'wh:transfer:create'" @click="openCreate">新建调拨</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="transferNo" label="调拨单号" min-width="180" />
      <el-table-column prop="fromWarehouseId" label="调出仓" min-width="100">
        <template #default="{ row }">{{ warehouseLabel(row.fromWarehouseId) }}</template>
      </el-table-column>
      <el-table-column prop="toWarehouseId" label="调入仓" min-width="100">
        <template #default="{ row }">{{ warehouseLabel(row.toWarehouseId) }}</template>
      </el-table-column>
      <el-table-column prop="transferTime" label="调拨时间" min-width="160" />
      <el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="TRANSFER_STATUS" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text @click="openFlow(row)">流程</n-button>
            <n-button size="small" v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'wh:transfer:submit'" text type="primary" @click="submit(row)">提交审批</n-button>
            <n-button size="small" v-if="row.status==='APPROVED'" v-perm="'wh:transfer:out'" text type="primary" @click="doOut(row)">调出</n-button>
            <n-button size="small" v-if="row.status==='OUT_DONE'" v-perm="'wh:transfer:in'" text type="success" @click="doIn(row)">调入</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="dialog" title="新建调拨单" width="720px">
      <el-form label-width="90px">
        <el-form-item label="调出仓">
          <el-select v-model="form.fromWarehouseId" class="w-full">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
        </el-form-item>
        <el-form-item label="调入仓">
          <el-select v-model="form.toWarehouseId" class="w-full">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
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
            <el-table-column label="批次" min-width="110">
              <template #default="{ row }"><el-input v-model="row.batchNo" size="small" /></template>
            </el-table-column>
            <el-table-column label="操作" min-width="70">
              <template #default="{ $index }"><n-button size="small" text type="error" @click="form.items.splice($index,1)">删</n-button></template>
            </el-table-column>
          </el-table>
          <n-button class="mt-2" @click="form.items.push({ materialId: null, qty: 1, batchNo: '' })">加行</n-button>
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
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { tTransferStatus, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'
import ApprovalFlowDialog from '../components/ApprovalFlowDialog.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const status = ref('')
const statuses = ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'OUT_DONE', 'CONFIRMED', 'REJECTED']
const dialog = ref(false)
const warehouses = ref([])
const materials = ref([])
const form = reactive({
  fromWarehouseId: null, toWarehouseId: null,
  items: [{ materialId: null, qty: 1, batchNo: '' }]
})

function warehouseLabel(id) {
  if (id == null) return '—'
  const w = warehouses.value.find(x => x.id === id)
  return w ? w.warehouseName : String(id)
}

async function load() {
  const res = await api.get('/transfer/orders', { params: { page: page.value, size: 10, status: status.value } })
  rows.value = res.data.records
  total.value = res.data.total
}

function openCreate() {
  form.fromWarehouseId = null
  form.toWarehouseId = null
  form.items = [{ materialId: null, qty: 1, batchNo: '' }]
  dialog.value = true
}

async function save() {
  try {
    await api.post('/transfer/orders', { ...form })
    ElMessage.success('已创建')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '创建失败') }
}

async function submit(row) {
  try {
    await api.post(`/transfer/orders/${row.id}/submit`)
    ElMessage.success('已提交审批')
    load()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
}

async function doOut(row) {
  await ElMessageBox.confirm('确认从调出仓扣减库存？', '提示')
  try {
    await api.post(`/transfer/orders/${row.id}/out`)
    ElMessage.success('已调出')
    load()
  } catch (e) { ElMessage.error(e.message || '调出失败') }
}

async function doIn(row) {
  await ElMessageBox.confirm('确认调入到目标仓？', '提示')
  try {
    await api.post(`/transfer/orders/${row.id}/in`)
    ElMessage.success('已调入')
    load()
  } catch (e) { ElMessage.error(e.message || '调入失败') }
}

const flowDialog = ref(false)
const flowBizType = ref('')
const flowBizId = ref(null)
const flowTitle = ref('')

function openFlow(row) {
  flowBizType.value = 'TRANSFER'
  flowBizId.value = row.id
  flowTitle.value = `审批流程 · ${row.transferNo || row.id}`
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

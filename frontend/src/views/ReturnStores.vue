<template>
  <el-card v-loading="loading">
    <div class="bar">
      <el-select v-model="status" placeholder="状态" clearable style="width:140px" @change="load">
        <el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'asset:return:create'" @click="openCreate">新建退库</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="returnNo" label="退库单号" min-width="180" />
      <el-table-column prop="warehouseId" label="仓库" min-width="110">
        <template #default="{ row }">{{ warehouseLabel(row.warehouseId) }}</template>
      </el-table-column>
      <el-table-column label="关联领用" min-width="130">
        <template #default="{ row }">{{ useApplyLabel(row.applyId) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" min-width="120">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="RETURN_STORE_STATUS" />
        </template>
      </el-table-column>
      <el-table-column prop="applyRemark" label="备注" min-width="140" />
      <el-table-column prop="applyTime" label="申请时间" min-width="160" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text @click="openFlow(row)">流程</n-button>
            <n-button size="small" text type="primary" @click="openDetail(row)">明细</n-button>
            <n-button v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'asset:return:submit'" size="small" text type="primary" @click="submit(row)">提交</n-button>
            <n-button v-if="['APPROVED','APPLIED'].includes(row.status)" v-perm="'asset:return:accept'" size="small" text type="success" @click="accept(row)">验收</n-button>
            <n-button v-if="row.status==='ACCEPTED'" v-perm="'asset:return:confirm'" size="small" text type="warning" @click="confirmIn(row)">确认入库</n-button>
            <n-button v-if="row.status==='APPLIED'" v-perm="'asset:return:accept'" size="small" text type="error" @click="rejectOrder(row)">驳回</n-button>
            <n-button v-if="['DRAFT','APPLIED','REJECTED'].includes(row.status)" v-perm="'asset:return:create'" size="small" text type="info" @click="cancelOrder(row)">作废</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="dialog" title="新建退库单" width="720px">
      <el-form label-width="100px">
        <el-form-item label="入库仓库">
          <el-select v-model="form.warehouseId" class="w-full">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
        <el-form-item label="退库明细">
          <el-table :data="form.items" size="small" border>
            <el-table-column label="物资" min-width="160">
              <template #default="{ row }">
                <el-select v-model="row.materialId" class="w-full">
                  <el-option v-for="m in materials" :key="m.id" :value="m.id" :label="`${m.materialCode} ${m.materialName}`" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" min-width="100">
              <template #default="{ row }"><el-input-number v-model="row.qty" :min="0.01" size="small" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="批次" min-width="110">
              <template #default="{ row }"><el-input v-model="row.batchNo" size="small" /></template>
            </el-table-column>
            <el-table-column label="状态" min-width="100">
              <template #default="{ row }">
                <el-select v-model="row.conditionStatus" size="small">
                  <el-option v-for="o in conditionOptions" :key="o.value" :value="o.value" :label="o.label" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="60">
              <template #default="{ $index }"><n-button size="small" text type="error" @click="form.items.splice($index,1)">删</n-button></template>
            </el-table-column>
          </el-table>
          <n-button class="mt-2" @click="form.items.push({ materialId: null, qty: 1, batchNo: '', conditionStatus: 'GOOD' })">加行</n-button>
        </el-form-item>
      </el-form>
      <template #footer>
        
        <div class="flex items-center justify-end gap-2">
        <n-button @click="dialog=false">取消</n-button>
        <n-button type="primary" :disabled="!form.warehouseId" @click="save">保存</n-button>
        </div>
      
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialog" title="退库明细" width="720px">
      <el-table :data="detailItems" border size="small">
        <el-table-column prop="materialId" label="物资" min-width="140">
          <template #default="{ row }">{{ materialLabel(row.materialId) }}</template>
        </el-table-column>
        <el-table-column prop="applyQty" label="申请数" min-width="90" />
        <el-table-column prop="acceptQty" label="验收数" min-width="90" />
        <el-table-column prop="batchNo" label="批次" min-width="110" />
        <el-table-column label="成色" min-width="90">
          <template #default="{ row }">
            <StatusTag :value="row.conditionStatus" dict-type="CONDITION_STATUS" />
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <ApprovalFlowDialog v-model="flowDialog" :biz-type="flowBizType" :biz-id="flowBizId" :title="flowTitle" />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { tReturnStoreStatus, tConditionStatus, tBiz, loadDicts, dictOptionsOr } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'
import ApprovalFlowDialog from '../components/ApprovalFlowDialog.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const status = ref('')
const statusOptions = computed(() =>
  dictOptionsOr('RETURN_STORE_STATUS', ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'APPLIED', 'ACCEPTED', 'CONFIRMED', 'REJECTED', 'CANCELLED'])
)
const conditionOptions = computed(() => dictOptionsOr('CONDITION_STATUS', ['GOOD', 'DAMAGED', 'REPAIRABLE', 'SCRAPPED']))
const loading = ref(false)
const warehouses = ref([])
const materials = ref([])
const dialog = ref(false)
const detailDialog = ref(false)
const detailItems = ref([])

const flowDialog = ref(false)
const flowBizType = ref('RETURN_STORE')
const flowBizId = ref(null)
const flowTitle = ref('')

const form = reactive({
  warehouseId: null, remark: '',
  items: [{ materialId: null, qty: 1, batchNo: '', conditionStatus: 'GOOD' }]
})

function warehouseLabel(id) {
  const w = warehouses.value.find(x => x.id === id)
  return w ? w.warehouseName : (id || '-')
}

const useApplies = ref([])

function useApplyLabel(id) {
  const a = useApplies.value.find(x => x.id === id)
  return a ? a.applyNo : (id == null ? '—' : `#${id}`)
}
function materialLabel(id) {
  const m = materials.value.find(x => x.id === id)
  return m ? `${m.materialCode} ${m.materialName}` : (id || '-')
}


async function load() {
  loading.value = true
  try {
    const res = await api.get('/return-stores', { params: { page: page.value, size: 10, status: status.value } })
    rows.value = res.data.records
    total.value = res.data.total
  } catch (e) { ElMessage.error(e.message || '加载失败') } finally { loading.value = false }
}

function openCreate() {
  form.warehouseId = null
  form.remark = ''
  form.items = [{ materialId: null, qty: 1, batchNo: '', conditionStatus: 'GOOD' }]
  dialog.value = true
}

async function save() {
  try {
    await api.post('/return-stores', {
      warehouseId: form.warehouseId,
      remark: form.remark,
      items: form.items
    })
    ElMessage.success('退库单已创建')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '创建失败') }
}

async function openDetail(row) {
  try {
    const res = await api.get(`/return-stores/${row.id}`)
    detailItems.value = res.data.items || []
    detailDialog.value = true
  } catch (e) { ElMessage.error(e.message || '加载失败') }
}

async function submit(row) {
  try {
    await api.post(`/return-stores/${row.id}/submit`)
    ElMessage.success('已提交')
    load()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
}

async function rejectOrder(row) {
  let reason = ''
  try {
    const { value } = await ElMessageBox.prompt('请输入驳回原因', '驳回退库单', { inputPlaceholder: '选填' })
    reason = value || ''
  } catch { return }
  try {
    await api.post(`/return-stores/${row.id}/reject`, null, { params: { reason } })
    ElMessage.success('已驳回')
    load()
  } catch (e) { ElMessage.error(e.message || '驳回失败') }
}

async function cancelOrder(row) {
  try {
    await ElMessageBox.confirm(`确认作废退库单 ${row.returnNo || row.id}？`, '提示')
    await api.post(`/return-stores/${row.id}/cancel`)
    ElMessage.success('已作废')
    load()
  } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '作废失败') }
}

async function accept(row) {
  await ElMessageBox.confirm('确认验收通过？', '提示')
  try {
    await api.post(`/return-stores/${row.id}/accept`, { remark: '' })
    ElMessage.success('已验收')
    load()
  } catch (e) { ElMessage.error(e.message || '验收失败') }
}

async function confirmIn(row) {
  await ElMessageBox.confirm('确认入库并增加库存？', '提示')
  try {
    await api.post(`/return-stores/${row.id}/confirm`, { remark: '' })
    ElMessage.success('已入库')
    load()
  } catch (e) { ElMessage.error(e.message || '入库失败') }
}

function openFlow(row) {
  flowBizType.value = 'RETURN_STORE'
  flowBizId.value = row.id
  flowTitle.value = `退库-${row.returnNo}`
  flowDialog.value = true
}

onMounted(async () => {
  await loadDicts()
  load()
  try {
    const [w, m, ua] = await Promise.all([
      api.get('/warehouses/list'),
      api.get('/materials', { params: { page: 1, size: 200 } }),
      api.get('/material-use', { params: { page: 1, size: 100 } }).catch(() => ({ data: { records: [] } }))
    ])
    warehouses.value = w.data || []
    materials.value = m.data.records || []
    useApplies.value = ua.data?.records || []
  } catch { /* ignore */ }
})
</script>

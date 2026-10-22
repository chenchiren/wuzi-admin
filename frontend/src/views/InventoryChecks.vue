<template>
  <el-card>
    <div class="bar">
      <el-select v-model="status" placeholder="状态" clearable style="width:140px" @change="load">
        <el-option v-for="s in statuses" :key="s" :value="s" :label="tInventoryStatus(s)" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'wh:inventory:create'" @click="openCreate">新建盘点</n-button>
      <n-button v-perm="'wh:inventory:create'" @click="openSpot">抽盘</n-button>
      <n-button @click="openWizard">按库位盘点</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="checkNo" label="盘点单号" min-width="180" />
      <el-table-column prop="warehouseId" label="仓库" min-width="110">
        <template #default="{ row }">{{ warehouseLabel(row.warehouseId) }}</template>
      </el-table-column>
      <el-table-column prop="checkTime" label="盘点时间" min-width="160" />
      <el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="INVENTORY_STATUS" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text @click="openFlow(row)">流程</n-button>
            <n-button size="small" text type="info" @click="exportCsv(row)">导出</n-button>
            <n-button size="small" v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'wh:inventory:update'" text type="warning" @click="fillBook(row)">回填账面</n-button>
            <n-button size="small" v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'wh:inventory:submit'" text type="primary" @click="submit(row)">提交审批</n-button>
            <n-button size="small" v-if="row.status==='APPROVED'" v-perm="'wh:inventory:confirm'" text type="primary" @click="confirm(row)">确认调账</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="spotDialog" title="抽样盘点（自动生成盘点单）" width="480px">
      <el-form label-width="100px">
        <el-form-item label="仓库" required>
          <el-select v-model="spotForm.warehouseId" class="w-full" placeholder="选择仓库">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
        </el-form-item>
        <el-form-item label="物资分类">
          <el-select v-model="spotForm.categoryId" class="w-full" clearable placeholder="全部分类">
            <el-option v-for="c in categories" :key="c.id" :value="c.id" :label="c.categoryName" />
          </el-select>
        </el-form-item>
        <el-form-item label="抽样方式">
          <el-radio-group v-model="spotForm.mode">
            <el-radio value="ratio">按比例</el-radio>
            <el-radio value="count">按条数</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="spotForm.mode === 'ratio'" label="抽盘比例%">
          <el-input-number v-model="spotForm.sampleRatio" :min="1" :max="100" class="w-full" />
        </el-form-item>
        <el-form-item v-else label="抽样条数">
          <el-input-number v-model="spotForm.sampleCount" :min="1" class="w-full" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="spotForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="spotDialog=false">取消</n-button>
          <n-button type="primary" :loading="spotBusy" @click="doSpot">生成盘点单</n-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="wizardDialog" title="按库位盘点向导" width="760px" top="6vh">
      <div class="bar" style="margin-bottom:10px">
        <el-select v-model="wizardWarehouseId" placeholder="选择仓库" style="width:220px" @change="loadWizard">
          <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
        </el-select>
        <span v-if="wizardSteps.length" class="muted">共 {{ wizardSteps.length }} 个库位，按库位逐区清点</span>
      </div>
      <el-table :data="wizardSteps" v-loading="wizardLoading" border size="small" max-height="440" empty-text="选择仓库后展示库位清单">
        <el-table-column prop="locationCode" label="库位编码" min-width="120" />
        <el-table-column prop="locationName" label="库位名称" min-width="120" />
        <el-table-column prop="stockCount" label="库存记录数" width="100" />
        <el-table-column prop="totalQty" label="在库总数" width="100" />
        <el-table-column label="涉及物资" min-width="220">
          <template #default="{ row }">
            <span style="font-size:12px;color:#666">{{ (row.materialNames || []).join('、') || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="wizardDialog=false">关闭</n-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="dialog" title="新建盘点单" width="760px">
      <el-form label-width="90px">
        <el-form-item label="仓库">
          <el-select v-model="form.warehouseId" class="w-full">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
        </el-form-item>
        <el-form-item label="明细">
          <el-table :data="form.items" size="small" border>
            <el-table-column label="物资" min-width="140">
              <template #default="{ row }">
                <el-select v-model="row.materialId" class="w-full">
                  <el-option v-for="m in materials" :key="m.id" :value="m.id" :label="m.materialName" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="账面" min-width="100">
              <template #default="{ row }"><el-input-number v-model="row.bookQty" :min="0" size="small" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="实盘" min-width="100">
              <template #default="{ row }"><el-input-number v-model="row.actualQty" :min="0" size="small" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="批次" min-width="110">
              <template #default="{ row }"><el-input v-model="row.batchNo" size="small" /></template>
            </el-table-column>
            <el-table-column label="操作" min-width="70">
              <template #default="{ $index }"><n-button size="small" text type="error" @click="form.items.splice($index,1)">删</n-button></template>
            </el-table-column>
          </el-table>
          <n-button class="mt-2" @click="form.items.push({ materialId: null, bookQty: 0, actualQty: 0, batchNo: '' })">加行</n-button>
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
import { tInventoryStatus, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'
import ApprovalFlowDialog from '../components/ApprovalFlowDialog.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const status = ref('')
const statuses = ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'CONFIRMED', 'REJECTED']
const dialog = ref(false)
const warehouses = ref([])
const materials = ref([])
const form = reactive({
  warehouseId: null,
  items: [{ materialId: null, bookQty: 0, actualQty: 0, batchNo: '' }]
})

function warehouseLabel(id) {
  if (id == null) return '—'
  const w = warehouses.value.find(x => x.id === id)
  return w ? w.warehouseName : String(id)
}

async function load() {
  const res = await api.get('/inventory/checks', { params: { page: page.value, size: 10, status: status.value } })
  rows.value = res.data.records
  total.value = res.data.total
}

function openCreate() {
  form.warehouseId = null
  form.items = [{ materialId: null, bookQty: 0, actualQty: 0, batchNo: '' }]
  dialog.value = true
}

async function save() {
  try {
    await api.post('/inventory/checks', { ...form })
    ElMessage.success('已创建')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '创建失败') }
}

async function submit(row) {
  try {
    await api.post(`/inventory/checks/${row.id}/submit`)
    ElMessage.success('已提交审批')
    load()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
}

async function confirm(row) {
  await ElMessageBox.confirm('确认盘点结果？将调整库存', '提示')
  try {
    await api.post(`/inventory/checks/${row.id}/confirm`)
    ElMessage.success('已确认')
    load()
  } catch (e) { ElMessage.error(e.message || '确认失败') }
}

const flowDialog = ref(false)
const flowBizType = ref('')
const flowBizId = ref(null)
const flowTitle = ref('')

// ===== 抽盘 =====
const spotDialog = ref(false)
const spotBusy = ref(false)
const categories = ref([])
const spotForm = reactive({ warehouseId: null, categoryId: null, mode: 'ratio', sampleRatio: 20, sampleCount: 10, remark: '' })

function openSpot() {
  Object.assign(spotForm, { warehouseId: null, categoryId: null, mode: 'ratio', sampleRatio: 20, sampleCount: 10, remark: '' })
  if (!categories.value.length) {
    api.get('/categories', { params: { page: 1, size: 100 } }).then((r) => { categories.value = r.data.records || [] }).catch(() => {})
  }
  spotDialog.value = true
}

async function doSpot() {
  if (!spotForm.warehouseId) { ElMessage.warning('请选择仓库'); return }
  spotBusy.value = true
  try {
    const body = {
      warehouseId: spotForm.warehouseId,
      categoryId: spotForm.categoryId || null,
      remark: spotForm.remark || null
    }
    if (spotForm.mode === 'ratio') body.sampleRatio = spotForm.sampleRatio
    else body.sampleCount = spotForm.sampleCount
    const res = await api.post('/inventory/checks/spot', body)
    ElMessage.success(`抽盘单已生成：${res.data?.check?.checkNo || res.data?.checkNo || ''}（${res.data?.itemCount ?? '?'} 条明细）`)
    spotDialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '抽盘失败') } finally { spotBusy.value = false }
}

// ===== 库位向导 =====
const wizardDialog = ref(false)
const wizardLoading = ref(false)
const wizardWarehouseId = ref(null)
const wizardSteps = ref([])

function openWizard() {
  wizardWarehouseId.value = null
  wizardSteps.value = []
  wizardDialog.value = true
}

async function loadWizard() {
  if (!wizardWarehouseId.value) return
  wizardLoading.value = true
  try {
    const res = await api.get('/inventory/checks/location-wizard', { params: { warehouseId: wizardWarehouseId.value } })
    wizardSteps.value = res.data?.steps || []
  } catch (e) { ElMessage.error(e.message || '加载失败') } finally { wizardLoading.value = false }
}

// ===== 回填账面 / 导出 =====
async function fillBook(row) {
  try {
    await ElMessageBox.confirm(`按账面库存回填盘点单 ${row.checkNo || row.id} 的实际数量？`, '回填账面')
  } catch { return }
  try {
    const res = await api.post(`/inventory/checks/${row.id}/fill-book`, {})
    ElMessage.success(`已回填 ${res.data?.updated ?? '?'} 条`)
    load()
  } catch (e) { ElMessage.error(e.message || '回填失败') }
}

async function exportCsv(row) {
  try {
    const res = await api.get(`/inventory/checks/${row.id}/export`)
    const csv = res.data || ''
    const blob = new Blob(['\ufeff' + csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `${row.checkNo || 'inventory-check'}.csv`
    a.click()
    URL.revokeObjectURL(url)
  } catch (e) { ElMessage.error(e.message || '导出失败') }
}

function openFlow(row) {
  flowBizType.value = 'INVENTORY_CHECK'
  flowBizId.value = row.id
  flowTitle.value = `审批流程 · ${row.checkNo || row.id}`
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

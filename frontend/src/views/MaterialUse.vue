<template>
  <el-card>
    <div class="bar">
      <el-input v-model="keyword" placeholder="单号/项目/用途" style="width:200px" clearable @change="load" />
      <el-select v-model="status" placeholder="状态" clearable style="width:140px" @change="load">
        <el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'asset:use:create'" @click="openCreate">新建领用</n-button>
      <n-button @click="doExport">导出CSV</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="applyNo" label="领用单号" min-width="180" />
      <el-table-column label="类型" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.useType" dict-type="USE_TYPE" />
        </template>
      </el-table-column>
      <el-table-column prop="projectName" label="项目" min-width="130" />
      <el-table-column prop="needDate" label="需求日" min-width="110" />
      <el-table-column prop="purpose" label="用途" min-width="120" />
      <el-table-column label="发料仓库" min-width="110">
        <template #default="{ row }">
          <span v-if="row.issueWarehouseId">{{ warehouseLabel(row.issueWarehouseId) }}</span>
          <span v-else class="text-slate-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="MATERIAL_USE_STATUS" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="360">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text @click="openFlow(row)">流程</n-button>
            <n-button size="small" text type="info" @click="openDetail(row)">明细/库存</n-button>
            <n-button size="small" text type="primary" @click="openIssueRecords(row)">发放记录</n-button>
            <n-button size="small" v-if="['DRAFT','REJECTED'].includes(row.status)" text type="warning" @click="openEdit(row)">修改</n-button>
            <n-button size="small" v-if="row.status==='DRAFT'" v-perm="'asset:use:submit'" text type="primary" @click="submit(row)">提交审批</n-button>
            <n-button size="small" v-if="row.status==='REJECTED'" v-perm="'asset:use:submit'" text type="primary" @click="submit(row)">重新发起</n-button>
            <n-button size="small" v-if="['SUBMITTED','PENDING_APPROVAL'].includes(row.status)" v-perm="'asset:use:approve'" text type="success" @click="approve(row, true)">通过</n-button>
            <n-button size="small" v-if="['SUBMITTED','PENDING_APPROVAL'].includes(row.status)" v-perm="'asset:use:approve'" text type="error" @click="approve(row, false)">驳回</n-button>
            <n-button size="small" v-if="row.status==='APPROVED'" v-perm="'asset:use:issue'" text type="warning" @click="issue(row)">发料出库</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="detailDialog" title="领用明细 / 库存变动" width="820px">      <el-descriptions v-if="detail.apply" :column="2" border size="small" class="mb-3">
        <el-descriptions-item label="领用单号">{{ detail.apply.applyNo }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <StatusTag :value="detail.apply.status" dict-type="MATERIAL_USE_STATUS" />
        </el-descriptions-item>
        <el-descriptions-item label="发料仓库">
          {{ detail.apply.issueWarehouseId ? warehouseLabel(detail.apply.issueWarehouseId) : '未发料' }}
        </el-descriptions-item>
        <el-descriptions-item label="出库单">
          {{ detail.outbound?.outboundNo || '—' }}
        </el-descriptions-item>
      </el-descriptions>
      <div class="mb-1 font-semibold">申请明细</div>
      <el-table :data="detail.items || []" border size="small" class="mb-3">
        <el-table-column prop="materialId" label="物资ID" width="90" />
        <el-table-column prop="qty" label="数量" width="100" />
        <el-table-column prop="unit" label="单位" width="80" />
        <el-table-column prop="remark" label="备注" min-width="120" />
      </el-table>
      <div class="mb-1 font-semibold">库存流水（发料扣减 / 退库回加）</div>
      <el-table :data="detail.stockRecords || []" border size="small">
        <el-table-column label="业务类型" width="110">
          <template #default="{ row }">
            <StatusTag :value="row.bizType" dict-type="BIZ_TYPE" effect="plain" />
          </template>
        </el-table-column>
        <el-table-column prop="warehouseId" label="仓库" width="90">
          <template #default="{ row }">{{ warehouseLabel(row.warehouseId) }}</template>
        </el-table-column>
        <el-table-column prop="materialId" label="物资ID" width="90" />
        <el-table-column prop="changeQty" label="变动" width="90" />
        <el-table-column prop="beforeQty" label="变动前" width="90" />
        <el-table-column prop="afterQty" label="变动后" width="90" />
        <el-table-column prop="bizNo" label="单号" min-width="120" />
        <el-table-column prop="createdAt" label="时间" min-width="150" />
      </el-table>
      <el-empty v-if="!(detail.stockRecords || []).length" description="暂无库存变动（审批通过不扣库存，请点「发料出库」）" :image-size="60" />
    </el-dialog>

    <el-dialog v-model="issueDialog" title="领用发料" width="420px">
      <el-form label-width="90px">
        <el-form-item label="领用单">
          <el-input :model-value="String(issueForm.applyNo)" disabled />
        </el-form-item>
        <el-form-item label="发料行数">
          <el-input :model-value="String(issueForm.itemCount)" disabled />
        </el-form-item>
        <el-form-item label="发料仓库" required>
          <el-select v-model="issueForm.warehouseId" class="w-full" placeholder="选择仓库" @change="onIssueWh">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
        </el-form-item>
        <el-form-item label="库位">
          <el-select v-model="issueForm.locationId" clearable filterable class="w-full" placeholder="可选，从该库位扣减">
            <el-option v-for="l in issueLocations" :key="l.id" :value="l.id" :label="`${l.locationCode} ${l.locationName || ''}`" />
          </el-select>
        </el-form-item>
        <div class="text-xs leading-5 text-slate-500">
          发料才会扣减仓库库存（审批通过本身不动库存）。确认后写库存流水、生成出库单。
        </div>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="issueDialog=false">取消</n-button>
          <n-button type="warning" @click="confirmIssue">确认发料</n-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="dialog" :title="form.id ? (form.status === 'REJECTED' ? '修改领用申请（驳回后）' : '修改领用申请') : '新建领用申请'" width="720px">
      <el-form label-width="90px">
        <el-form-item label="类型">
          <el-select v-model="form.useType" class="w-full">
            <el-option v-for="o in useTypeOptions" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目"><el-input v-model="form.projectName" /></el-form-item>
        <el-form-item label="需求日"><el-date-picker v-model="form.needDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
        <el-form-item label="用途"><el-input v-model="form.purpose" /></el-form-item>
        <el-form-item label="明细">
          <el-table :data="form.items" size="small" border>
            <el-table-column label="物资" min-width="180">
              <template #default="{ row }">
                <el-select v-model="row.materialId" class="w-full">
                  <el-option v-for="m in materials" :key="m.id" :value="m.id" :label="`${m.materialCode} ${m.materialName}`" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" min-width="110">
              <template #default="{ row }"><el-input-number v-model="row.qty" :min="1" size="small" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="操作" min-width="70">
              <template #default="{ $index }"><n-button size="small" text type="error" @click="form.items.splice($index,1)">删</n-button></template>
            </el-table-column>
          </el-table>
          <n-button class="mt-2" @click="form.items.push({ materialId: null, qty: 1 })">加行</n-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="dialog=false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
          <n-button v-if="form.id" type="success" @click="saveAndResubmit">保存并重新发起</n-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="irDialog" :title="`发放记录 · ${irApplyNo}`" width="720px">
      <el-table :data="irRows" v-loading="irLoading" border size="small" max-height="420">
        <el-table-column prop="createdAt" label="时间" min-width="150" />
        <el-table-column prop="bizType" label="类型" width="110" />
        <el-table-column label="物资" min-width="140">
          <template #default="{ row }">
            {{ (materials.find(m => m.id === row.materialId) || {}).materialName || ('#' + row.materialId) }}
          </template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" width="90" />
        <el-table-column prop="warehouseId" label="仓库" width="80" />
        <el-table-column prop="remark" label="备注" min-width="100" />
      </el-table>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="irDialog=false">关闭</n-button>
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
import { tMaterialUseStatus, tUseType, tBiz, loadDicts, dictOptionsOr } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'
import ApprovalFlowDialog from '../components/ApprovalFlowDialog.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const status = ref('')
const statusOptions = computed(() =>
  dictOptionsOr('MATERIAL_USE_STATUS', ['DRAFT', 'SUBMITTED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'ISSUED', 'CLOSED'])
)
const useTypeOptions = computed(() => dictOptionsOr('USE_TYPE', ['DAILY', 'PROJECT', 'OTHER']))
const dialog = ref(false)
const materials = ref([])
const warehouses = ref([])
const form = reactive({
  id: null, status: '', useType: 'DAILY', projectName: '', needDate: '', purpose: '',
  items: [{ materialId: null, qty: 1 }]
})

async function doExport() {
  try {
    await downloadExportCsv('/exports/material-use', {}, '领用申请')
    ElMessage.success('已导出')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

async function load() {
  const res = await api.get('/material-use', {
    params: { page: page.value, size: 10, keyword: keyword.value, status: status.value }
  })
  rows.value = res.data.records
  total.value = res.data.total
}

function openCreate() {
  form.id = null
  form.status = ''
  form.useType = 'DAILY'
  form.projectName = ''
  form.needDate = ''
  form.purpose = ''
  form.items = [{ materialId: null, qty: 1 }]
  dialog.value = true
}

async function openEdit(row) {
  try {
    const res = await api.get(`/material-use/${row.id}`)
    const apply = res.data.apply || row
    const items = res.data.items || []
    form.id = apply.id
    form.status = apply.status
    form.useType = apply.useType || 'DAILY'
    form.projectName = apply.projectName || ''
    form.needDate = apply.needDate || ''
    form.purpose = apply.purpose || ''
    form.items = items.length
      ? items.map((it) => ({ materialId: it.materialId, qty: it.qty, unit: it.unit }))
      : [{ materialId: null, qty: 1 }]
    dialog.value = true
  } catch (e) { ElMessage.error(e.message || '加载失败') }
}

function payload() {
  return {
    useType: form.useType,
    projectName: form.projectName,
    needDate: form.needDate,
    purpose: form.purpose,
    items: form.items
  }
}

async function save() {
  try {
    if (form.id) {
      await api.put(`/material-use/${form.id}`, payload())
      ElMessage.success('已保存修改')
    } else {
      await api.post('/material-use', payload())
      ElMessage.success('已创建')
    }
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

async function saveAndResubmit() {
  try {
    if (!form.id) {
      await save()
      return
    }
    await api.put(`/material-use/${form.id}`, payload())
    await api.post(`/material-use/${form.id}/submit`)
    ElMessage.success('已保存并重新发起（从头审批）')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '重新发起失败') }
}

async function submit(row) {
  try {
    await api.post(`/material-use/${row.id}/submit`)
    ElMessage.success(row.status === 'REJECTED' ? '已重新发起（从头审批）' : '已提交')
    load()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
}

async function approve(row, approved) {
  const { value } = await ElMessageBox.prompt('审批意见', approved ? '通过' : '驳回', { inputValue: '' })
  try {
    await api.post(`/material-use/${row.id}/approve`, { approved, comment: value || '' })
    ElMessage.success('已处理')
    load()
  } catch (e) { ElMessage.error(e.message || '审批失败') }
}

async function issue(row) {
  const detail = await api.get(`/material-use/${row.id}`)
  const items = (detail.data.items || []).map((it) => ({
    itemId: it.id, materialId: it.materialId, qty: it.qty
  }))
  if (!warehouses.value.length) {
    try {
      const w = await api.get('/warehouses/list')
      warehouses.value = w.data || []
    } catch { /* ignore */ }
  }
  issueForm.applyId = row.id
  issueForm.applyNo = row.applyNo || row.id
  issueForm.itemCount = items.length
  issueForm.items = items
  issueForm.warehouseId = warehouses.value[0]?.id || null
  issueForm.locationId = null
  if (issueForm.warehouseId) onIssueWh(issueForm.warehouseId)
  issueDialog.value = true
}

const issueDialog = ref(false)
const issueLocations = ref([])
const issueForm = reactive({
  applyId: null, applyNo: '', itemCount: 0, warehouseId: null, locationId: null, items: []
})

async function onIssueWh(whId) {
  issueForm.locationId = null
  if (!whId) { issueLocations.value = []; return }
  try {
    const res = await api.get(`/warehouses/${whId}/locations`)
    issueLocations.value = res.data || []
  } catch { issueLocations.value = [] }
}

const detailDialog = ref(false)
const detail = ref({ apply: null, items: [], stockRecords: [], outbound: null })

function warehouseLabel(id) {
  const w = warehouses.value.find(x => x.id === id)
  return w ? w.warehouseName : (id == null ? '—' : `#${id}`)
}

async function openDetail(row) {
  try {
    const res = await api.get(`/material-use/${row.id}`)
    detail.value = {
      apply: res.data.apply || row,
      items: res.data.items || [],
      stockRecords: res.data.stockRecords || [],
      outbound: res.data.outbound || null
    }
    detailDialog.value = true
  } catch (e) {
    ElMessage.error(e.message || '加载明细失败')
  }
}

async function confirmIssue() {
  if (!issueForm.warehouseId) {
    ElMessage.warning('请选择发料仓库')
    return
  }
  try {
    await api.post(`/material-use/${issueForm.applyId}/issue`, {
      warehouseId: Number(issueForm.warehouseId),
      locationId: issueForm.locationId || undefined,
      items: issueForm.items
    })
    ElMessage.success('已发料')
    issueDialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '发料失败')
  }
}

const flowDialog = ref(false)
const flowBizType = ref('')
const flowBizId = ref(null)
const flowTitle = ref('')

function openFlow(row) {
  flowBizType.value = 'MATERIAL_USE'
  flowBizId.value = row.id
  flowTitle.value = `审批流程 · ${row.applyNo || row.id}`
  flowDialog.value = true
}

onMounted(async () => {
  await loadDicts()
  load()
  const [m, w] = await Promise.all([
    api.get('/materials', { params: { page: 1, size: 100 } }),
    api.get('/warehouses/list')
  ])
  materials.value = m.data.records
  warehouses.value = w.data
})

const irDialog = ref(false)
const irLoading = ref(false)
const irRows = ref([])
const irApplyNo = ref('')

async function openIssueRecords(row) {
  irApplyNo.value = row.applyNo || `#${row.id}`
  irDialog.value = true
  irLoading.value = true
  try {
    const res = await api.get(`/material-use/${row.id}/issue-records`)
    irRows.value = res.data || []
  } catch (e) { ElMessage.error(e.message || '加载失败') } finally { irLoading.value = false }
}
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; }
</style>

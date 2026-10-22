<template>
  <el-card>
    <div class="bar">
      <el-input v-model="keyword" placeholder="单号/项目/用途" style="width:200px" clearable @change="load" />
      <el-select v-model="status" placeholder="状态" clearable style="width:140px" @change="load">
        <el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="warning" @click="loadOverdue">逾期列表</n-button>
      <n-button type="success" v-perm="'asset:borrow:create'" @click="openCreate">新建借用</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="borrowNo" label="借用单号" min-width="180" />
      <el-table-column label="类型" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.borrowType" dict-type="BORROW_TYPE" />
        </template>
      </el-table-column>
      <el-table-column prop="projectName" label="项目" min-width="120" />
      <el-table-column prop="borrowDate" label="借用日" min-width="110" />
      <el-table-column prop="expectReturnDate" label="预计归还" min-width="110" />
      <el-table-column prop="purpose" label="用途" min-width="120" />
      <el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="BORROW_STATUS" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text @click="openFlow(row)">流程</n-button>
            <n-button size="small" v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'asset:borrow:submit'" text type="primary" @click="submit(row)">提交审批</n-button>
            <n-button size="small" v-if="['SUBMITTED','PENDING_APPROVAL'].includes(row.status)" v-perm="'asset:borrow:approve'" text type="success" @click="approve(row, true)">通过</n-button>
            <n-button size="small" v-if="['SUBMITTED','PENDING_APPROVAL'].includes(row.status)" v-perm="'asset:borrow:approve'" text type="error" @click="approve(row, false)">驳回</n-button>
            <n-button size="small" v-if="row.status==='APPROVED'" v-perm="'asset:borrow:lend'" text type="primary" @click="lend(row)">借出</n-button>
            <n-button size="small" v-if="['LENT','PART_RETURNED','OVERDUE'].includes(row.status)" v-perm="'asset:borrow:return'" text type="warning" @click="doReturn(row)">归还</n-button>
            <n-button size="small" text type="info" @click="openReturnRecords(row)">归还记录</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="whDialog" :title="whForm.mode === 'lend' ? '借出仓库' : '归还仓库'" width="400px">
      <el-form label-width="90px">
        <el-form-item label="仓库" required>
          <el-select v-model="whForm.warehouseId" class="w-full" placeholder="选择仓库">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="whDialog=false">取消</n-button>
          <n-button type="primary" @click="confirmWh">确认</n-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="rrDialog" :title="`归还记录 · ${rrBorrowNo}`" width="640px">
      <el-table :data="rrRows" v-loading="rrLoading" border size="small" max-height="420">
        <el-table-column prop="returnTime" label="归还时间" min-width="150" />
        <el-table-column label="物资" min-width="140">
          <template #default="{ row }">
            {{ (materials.find(m => m.id === row.materialId) || {}).materialName || ('#' + row.materialId) }}
          </template>
        </el-table-column>
        <el-table-column prop="returnQty" label="归还数量" width="90" />
        <el-table-column prop="conditionStatus" label="状态" width="90" />
        <el-table-column prop="remark" label="备注" min-width="100" />
      </el-table>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="rrDialog=false">关闭</n-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="dialog" title="新建借用单" width="720px">
      <el-form label-width="100px">
        <el-form-item label="类型">
          <el-select v-model="form.borrowType" class="w-full">
            <el-option v-for="o in borrowTypeOptions" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目"><el-input v-model="form.projectName" /></el-form-item>
        <el-form-item label="预计归还"><el-date-picker v-model="form.expectReturnDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
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
import { tBorrowStatus, tBorrowType, loadDicts, dictOptionsOr } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'
import ApprovalFlowDialog from '../components/ApprovalFlowDialog.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const status = ref('')
const statusOptions = computed(() =>
  dictOptionsOr('BORROW_STATUS', ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'LENT', 'PART_RETURNED', 'RETURNED', 'OVERDUE'])
)
const borrowTypeOptions = computed(() => dictOptionsOr('BORROW_TYPE', ['TEMP', 'LONG']))
const dialog = ref(false)
const materials = ref([])
const form = reactive({
  borrowType: 'TEMP', projectName: '', expectReturnDate: '', purpose: '',
  items: [{ materialId: null, qty: 1 }]
})

async function load() {
  const res = await api.get('/borrows', {
    params: { page: page.value, size: 10, keyword: keyword.value, status: status.value }
  })
  rows.value = res.data.records
  total.value = res.data.total
}

async function loadOverdue() {
  try {
    const res = await api.get('/borrows/overdue')
    const list = Array.isArray(res.data) ? res.data : []
    rows.value = list.map((r) => ({ ...(r.order || {}), unreturnedQty: r.unreturnedQty, overdueDays: r.overdueDays }))
    total.value = rows.value.length
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  }
}

function openCreate() {
  form.borrowType = 'TEMP'
  form.projectName = ''
  form.expectReturnDate = ''
  form.purpose = ''
  form.items = [{ materialId: null, qty: 1 }]
  dialog.value = true
}

async function save() {
  try {
    await api.post('/borrows', { ...form })
    ElMessage.success('已创建')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '创建失败') }
}

async function submit(row) {
  try {
    await api.post(`/borrows/${row.id}/submit`)
    ElMessage.success('已提交审批')
    load()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
}

async function approve(row, approved) {
  const { value } = await ElMessageBox.prompt('审批意见', approved ? '通过' : '驳回', { inputValue: '' })
  try {
    await api.post(`/borrows/${row.id}/approve`, { approved, comment: value || '' })
    ElMessage.success('已处理')
    load()
  } catch (e) { ElMessage.error(e.message || '审批失败') }
}

const whDialog = ref(false)
const warehouses = ref([])
const whForm = reactive({ mode: 'lend', row: null, warehouseId: null, items: [] })

async function ensureWarehouses() {
  if (warehouses.value.length) return
  try {
    const w = await api.get('/warehouses/list')
    warehouses.value = w.data || []
  } catch { /* ignore */ }
}

async function lend(row) {
  await ensureWarehouses()
  whForm.mode = 'lend'
  whForm.row = row
  whForm.items = []
  whForm.warehouseId = warehouses.value[0]?.id || null
  whDialog.value = true
}

async function doReturn(row) {
  const detail = await api.get(`/borrows/${row.id}`)
  const items = (detail.data.items || []).map((it) => ({
    itemId: it.id, materialId: it.materialId, returnQty: it.qty
  }))
  await ensureWarehouses()
  whForm.mode = 'return'
  whForm.row = row
  whForm.items = items
  whForm.warehouseId = warehouses.value[0]?.id || null
  whDialog.value = true
}

async function confirmWh() {
  if (!whForm.warehouseId) {
    ElMessage.warning('请选择仓库')
    return
  }
  try {
    if (whForm.mode === 'lend') {
      await api.post(`/borrows/${whForm.row.id}/lend`, { warehouseId: Number(whForm.warehouseId) })
      ElMessage.success('已借出')
    } else {
      await api.post(`/borrows/${whForm.row.id}/return`, {
        warehouseId: Number(whForm.warehouseId),
        conditionStatus: 'GOOD',
        items: whForm.items
      })
      ElMessage.success('已归还')
    }
    whDialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || (whForm.mode === 'lend' ? '借出失败' : '归还失败'))
  }
}

const flowDialog = ref(false)
const flowBizType = ref('')
const flowBizId = ref(null)
const flowTitle = ref('')

const rrDialog = ref(false)
const rrLoading = ref(false)
const rrRows = ref([])
const rrBorrowNo = ref('')

async function openReturnRecords(row) {
  rrBorrowNo.value = row.borrowNo || `#${row.id}`
  rrDialog.value = true
  rrLoading.value = true
  try {
    const res = await api.get(`/borrows/${row.id}/return-records`)
    rrRows.value = res.data || []
  } catch (e) { ElMessage.error(e.message || '加载失败') } finally { rrLoading.value = false }
}

function openFlow(row) {
  flowBizType.value = 'BORROW'
  flowBizId.value = row.id
  flowTitle.value = `审批流程 · ${row.borrowNo || row.id}`
  flowDialog.value = true
}

onMounted(async () => {
  await loadDicts()
  load()
  const m = await api.get('/materials', { params: { page: 1, size: 100 } })
  materials.value = m.data.records
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; }
</style>

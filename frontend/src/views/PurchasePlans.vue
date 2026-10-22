<template>
  <el-card>
    <div class="bar">
      <el-select v-model="query.status" clearable placeholder="状态" style="width:140px">
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <el-input-number v-model="query.planYear" :min="2020" :max="2100" style="width:120px" placeholder="年度" />
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'po:plan:create'" @click="openCreate">新建计划</n-button>
    </div>

    <el-table :data="rows" border stripe>
      <el-table-column prop="planNo" label="计划号" min-width="150" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column prop="planYear" label="年度" width="80" />
      <el-table-column prop="planMonth" label="月份" width="70" />
      <el-table-column label="类型" width="90">
        <template #default="{ row }">
          <StatusTag :value="row.planType" dict-type="PLAN_TYPE" effect="plain" />
        </template>
      </el-table-column>
      <el-table-column label="预估金额" min-width="110">
        <template #default="{ row }">{{ money(row.totalAmount) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="PLAN_STATUS" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text type="primary" @click="openDetail(row)">明细</n-button>
            <n-button size="small" text type="success" v-perm="'po:plan:convert'" v-if="row.status==='APPROVED'" @click="openDetail(row)">转申请</n-button>
            <n-button size="small" text v-perm="'po:plan:submit'" v-if="row.status==='DRAFT'||row.status==='REJECTED'" @click="act(row,'submit')">提交</n-button>
            <n-button size="small" text type="success" v-perm="'po:plan:approve'" v-if="row.status==='SUBMITTED'" @click="act(row,'approve')">通过</n-button>
            <n-button size="small" text type="warning" v-perm="'po:plan:reject'" v-if="row.status==='SUBMITTED'" @click="act(row,'reject')">驳回</n-button>
            <n-button size="small" text type="error" v-perm="'po:plan:cancel'" v-if="!['CLOSED','CANCELLED'].includes(row.status)" @click="act(row,'cancel')">作废</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total"
        :page-size="query.size" :current-page="query.page" @current-change="p => { query.page = p; load() }" />
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '编辑计划' : '新建采购计划'" width="900px" top="5vh">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px" @submit.prevent>
        <el-form-item label="标题" prop="title">
          <el-input v-model="form.title" placeholder="请输入计划标题" />
        </el-form-item>
        <el-form-item label="年度" prop="planYear">
          <el-input-number v-model="form.planYear" :min="2020" :max="2100" style="width:120px" />
        </el-form-item>
        <el-form-item label="月份" prop="planMonth">
          <el-input-number v-model="form.planMonth" :min="1" :max="12" style="width:120px" />
        </el-form-item>
        <el-form-item label="类型" prop="planType">
          <el-select v-model="form.planType" class="w-full" placeholder="请选择类型">
            <el-option v-for="t in planTypeOptions" :key="t.value" :value="t.value" :label="t.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目号" prop="projectCode">
          <el-input v-model="form.projectCode" placeholder="请输入项目号" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
        <el-form-item label="需求明细" prop="items">
          <div class="w-full">
            <el-table :data="form.items" size="small" border>
              <el-table-column label="来源" width="110">
                <template #default="{ row }">
                  <el-select v-model="row.sourceType" size="small" @change="() => onSourceTypeChange(row)">
                    <el-option label="物资档案" value="MATERIAL" />
                    <el-option label="供应商商品" value="SUPPLIER_PRODUCT" />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="选择物资 / 商品" min-width="220">
                <template #default="{ row }">
                  <el-select
                    v-if="row.sourceType === 'MATERIAL'"
                    v-model="row.materialId"
                    size="small"
                    filterable
                    class="w-full"
                    placeholder="请选择已登记物资"
                    @change="v => onPickMaterial(row, v)"
                  >
                    <el-option
                      v-for="m in materials"
                      :key="m.id"
                      :value="m.id"
                      :label="materialLabel(m)"
                    />
                  </el-select>
                  <el-select
                    v-else
                    v-model="row.supplierProductId"
                    size="small"
                    filterable
                    class="w-full"
                    placeholder="请选择供应商商品"
                    @change="v => onPickSupplierProduct(row, v)"
                  >
                    <el-option
                      v-for="p in supplierProducts"
                      :key="p.id"
                      :value="p.id"
                      :label="productLabel(p)"
                    />
                  </el-select>
                </template>
              </el-table-column>
              <el-table-column label="物资名称" min-width="120">
                <template #default="{ row }">
                  <el-input v-model="row.materialName" size="small" disabled />
                </template>
              </el-table-column>
              <el-table-column label="规格" min-width="90">
                <template #default="{ row }">
                  <el-input v-model="row.spec" size="small" disabled />
                </template>
              </el-table-column>
              <el-table-column label="数量" width="100">
                <template #default="{ row }">
                  <el-input-number v-model="row.qty" :min="0" :precision="3" size="small" controls-position="right" style="width:90px" />
                </template>
              </el-table-column>
              <el-table-column label="预估单价" width="110">
                <template #default="{ row }">
                  <el-input-number v-model="row.estimatedPrice" :min="0" :precision="2" size="small" controls-position="right" style="width:100px" />
                </template>
              </el-table-column>
              <el-table-column label="单位" width="70">
                <template #default="{ row }">
                  <el-input v-model="row.unit" size="small" disabled />
                </template>
              </el-table-column>
              <el-table-column label="操作" width="70">
                <template #default="{ $index }">
                  <n-button size="small" text type="error" @click="form.items.splice($index, 1)">删</n-button>
                </template>
              </el-table-column>
            </el-table>
            <n-button size="small" class="mt-2" @click="form.items.push(emptyItem())">加行</n-button>
            <div class="hint mt-1">
              先建档再采购：请从「物资档案」或「供应商商品」选择已登记物资，禁止手填新物资。
              目录里没有的，请先到「物资档案」建档，或在供应商商品中关联系统物资后再采购。
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex justify-end gap-2">
          <n-button @click="dialog=false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="detailDlg" title="计划明细" size="56%">
      <div v-if="detailPlan" class="mb-3 text-sm text-slate-600">
        计划号 <b>{{ detailPlan.planNo }}</b>
        · 状态 <StatusTag :value="detailPlan.status" dict-type="PLAN_STATUS" />
        <template v-if="detailPlan.status === 'APPROVED'">
          · 勾选未转行后点「转采购申请」，生成的申请会出现在「采购申请」板块
        </template>
      </div>
      <el-table
        v-if="detailPlan?.status === 'APPROVED'"
        ref="itemTableRef"
        :data="detailItems"
        border
        size="small"
        @selection-change="onItemSelect"
      >
        <el-table-column type="selection" width="42" :selectable="row => row.status !== 'CONVERTED'" />
        <el-table-column prop="materialName" label="物资" min-width="120" />
        <el-table-column prop="spec" label="规格" min-width="90" />
        <el-table-column prop="qty" label="数量" width="90" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="预估金额" width="110">
          <template #default="{ row }">{{ money(row.estimatedAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.status" dict-type="PLAN_ITEM_STATUS" effect="plain" />
          </template>
        </el-table-column>
        <el-table-column label="转出申请" min-width="150">
          <template #default="{ row }">
            <span v-if="row.applyNo" class="text-primary">{{ row.applyNo }}</span>
            <span v-else class="text-slate-400">—</span>
          </template>
        </el-table-column>
      </el-table>
      <el-table v-else :data="detailItems" border size="small">
        <el-table-column prop="materialName" label="物资" min-width="120" />
        <el-table-column prop="spec" label="规格" min-width="90" />
        <el-table-column prop="qty" label="数量" width="90" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column label="预估金额" width="110">
          <template #default="{ row }">{{ money(row.estimatedAmount) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <StatusTag :value="row.status" dict-type="PLAN_ITEM_STATUS" effect="plain" />
          </template>
        </el-table-column>
        <el-table-column label="转出申请" min-width="150">
          <template #default="{ row }">
            <span v-if="row.applyNo" class="text-primary">{{ row.applyNo }}</span>
            <span v-else class="text-slate-400">—</span>
          </template>
        </el-table-column>
      </el-table>
      <div v-if="detailPlan?.status === 'APPROVED'" class="mt-4 flex flex-wrap items-center gap-2">
        <n-button
          type="success"
          v-perm="'po:plan:convert'"
          :loading="converting"
          :disabled="!selectedItems.length"
          @click="convertToApply"
        >
          转采购申请{{ selectedItems.length ? `（${selectedItems.length} 行）` : '' }}
        </n-button>
        <n-button v-perm="'po:plan:convert'" :loading="converting" @click="convertAllPending">
          转全部未转行
        </n-button>
        <span class="text-xs text-slate-500">生成草稿采购申请后，可到「采购申请」提交审批，再走询价 / 订单。</span>
      </div>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { loadDicts, dictOptionsOr } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const statusOptions = computed(() =>
  dictOptionsOr('PLAN_STATUS', ['DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CLOSED', 'CANCELLED'])
)
const planTypeLabels = {
  MONTHLY: '月度', QUARTERLY: '季度', YEARLY: '年度', PROJECT: '项目'
}
const planTypeOptions = computed(() => {
  const fromDict = dictOptionsOr('PLAN_TYPE', [])
  if (fromDict.length) return fromDict
  return Object.entries(planTypeLabels).map(([value, label]) => ({ value, label }))
})
const rows = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, status: '', planYear: new Date().getFullYear() })
const dialog = ref(false)
const detailDlg = ref(false)
const detailItems = ref([])
const detailPlan = ref(null)
const selectedItems = ref([])
const converting = ref(false)
const materials = ref([])
const supplierProducts = ref([])
const form = reactive({ id: null, title: '', planYear: new Date().getFullYear(), planMonth: new Date().getMonth()+1, planType: 'MONTHLY', projectCode: '', remark: '', items: [] })
const formRef = ref()
const itemTableRef = ref()

const rules = {
  title: [{ required: true, message: '请输入计划标题', trigger: 'blur' }],
  planYear: [{ required: true, message: '请选择年度', trigger: 'change' }],
  planMonth: [{ required: true, message: '请选择月份', trigger: 'change' }],
  planType: [{ required: true, message: '请选择计划类型', trigger: 'change' }],
  projectCode: [{ required: true, message: '请输入项目号', trigger: 'blur' }],
  items: [{
    validator: (rule, value, callback) => {
      if (!value || !value.length) {
        callback(new Error('请添加需求明细'))
        return
      }
      for (let i = 0; i < value.length; i++) {
        const it = value[i]
        const row = i + 1
        if (it.sourceType === 'SUPPLIER_PRODUCT') {
          if (it.supplierProductId == null || it.supplierProductId === '') {
            callback(new Error(`第${row}行请选择供应商商品`))
            return
          }
          if (it.materialId == null || it.materialId === '') {
            callback(new Error(`第${row}行该商品未关联系统物资，请先到供应商商品中关联后再采购`))
            return
          }
        } else if (it.materialId == null || it.materialId === '') {
          callback(new Error(`第${row}行请选择已登记物资（请先在物资档案建档）`))
          return
        }
        if (it.qty == null || Number(it.qty) <= 0) {
          callback(new Error(`第${row}行数量须大于0`))
          return
        }
      }
      callback()
    },
    trigger: 'change'
  }]
}

const money = v => v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

function emptyItem() {
  return {
    sourceType: 'MATERIAL',
    materialId: null,
    supplierProductId: null,
    materialName: '',
    spec: '',
    qty: 1,
    estimatedPrice: 0,
    unit: ''
  }
}

const suppliersMap = ref({})

function productLabel(p) {
  const supplier = suppliersMap.value[p.supplierId]?.supplierName || ''
  const code = p.supplierProductCode || p.supplierSku || ''
  return [supplier, code, p.productName].filter(Boolean).join(' · ')
}

function materialLabel(m) {
  return [m.materialCode, m.materialName].filter(Boolean).join(' ')
}

async function loadOptions() {
  const [m, sp, s] = await Promise.all([
    api.get('/materials', { params: { page: 1, size: 200 } }).catch(() => ({ data: { records: [] } })),
    api.get('/supplier-products', { params: { page: 1, size: 200 } }).catch(() => ({ data: { records: [] } })),
    api.get('/suppliers', { params: { page: 1, size: 200 } }).catch(() => ({ data: { records: [] } }))
  ])
  materials.value = m.data?.records || []
  supplierProducts.value = sp.data?.records || []
  const list = s.data?.records || []
  suppliersMap.value = Object.fromEntries(list.map(x => [x.id, x]))
}

function onSourceTypeChange(row) {
  if (row.sourceType === 'MATERIAL') {
    row.supplierProductId = null
    row.materialId = null
    row.materialName = ''
    row.spec = ''
    row.unit = ''
    row.estimatedPrice = 0
    return
  }
  row.materialId = null
  row.supplierProductId = null
  row.materialName = ''
  row.spec = ''
  row.unit = ''
  row.estimatedPrice = 0
}

function onPickMaterial(row, id) {
  const m = materials.value.find(x => x.id === id)
  if (!m) {
    row.materialId = null
    row.materialName = ''
    row.spec = ''
    row.unit = ''
    return
  }
  row.materialId = m.id
  row.supplierProductId = null
  row.materialName = m.materialName || ''
  row.spec = m.spec || ''
  row.unit = m.unit || ''
  if (m.refPrice != null) row.estimatedPrice = Number(m.refPrice)
}

function onPickSupplierProduct(row, id) {
  const p = supplierProducts.value.find(x => x.id === id)
  if (!p) {
    row.supplierProductId = null
    row.materialId = null
    row.materialName = ''
    row.spec = ''
    row.unit = ''
    return
  }
  row.supplierProductId = p.id
  row.materialId = p.materialId ?? null
  row.materialName = p.productName || row.materialName
  row.spec = p.spec || row.spec
  row.unit = p.unit || row.unit
  const price = p.taxInclusivePrice ?? p.taxExclusivePrice
  if (price != null) row.estimatedPrice = Number(price)
  if (p.materialId == null) {
    ElMessage.warning(`商品「${p.productName || p.supplierProductCode}」未关联系统物资，请先关联后再采购`)
  }
}

async function load() {
  const res = await api.get('/purchase-plans', { params: { page: query.page, size: query.size, status: query.status || undefined, planYear: query.planYear || undefined } })
  rows.value = res.data.records || []
  total.value = res.data.total || 0
}

function openCreate() {
  Object.assign(form, { id: null, title: '', planYear: new Date().getFullYear(), planMonth: new Date().getMonth()+1, planType: 'MONTHLY', projectCode: '', remark: '', items: [emptyItem()] })
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function openDetail(row) {
  detailPlan.value = { ...row }
  selectedItems.value = []
  const res = await api.get(`/purchase-plans/${row.id}/items`)
  detailItems.value = res.data || []
  detailDlg.value = true
}

function onItemSelect(list) {
  selectedItems.value = list
}

async function convertToApply() {
  const ids = selectedItems.value.map(x => x.id)
  if (!ids.length) return
  await ElMessageBox.confirm(
    `将所选 ${ids.length} 行计划明细生成一张草稿采购申请？生成后请到「采购申请」提交审批。`,
    '转采购申请'
  )
  converting.value = true
  try {
    const res = await api.post(`/purchase-plans/${detailPlan.value.id}/to-apply`, { itemIds: ids })
    const applyNo = res.data?.applyNo
    ElMessage.success(applyNo ? `已生成采购申请 ${applyNo}` : '已生成采购申请')
    await refreshDetail()
    load()
  } finally {
    converting.value = false
  }
}

async function convertAllPending() {
  const pending = detailItems.value.filter(x => x.status !== 'CONVERTED')
  if (!pending.length) {
    ElMessage.info('没有可转的计划行')
    return
  }
  await ElMessageBox.confirm(
    `将全部 ${pending.length} 行未转明细生成一张草稿采购申请？`,
    '转采购申请'
  )
  converting.value = true
  try {
    const res = await api.post(`/purchase-plans/${detailPlan.value.id}/to-apply`, {})
    const applyNo = res.data?.applyNo
    ElMessage.success(applyNo ? `已生成采购申请 ${applyNo}` : '已生成采购申请')
    await refreshDetail()
    load()
  } finally {
    converting.value = false
  }
}

async function refreshDetail() {
  const res = await api.get(`/purchase-plans/${detailPlan.value.id}/items`)
  detailItems.value = res.data || []
  selectedItems.value = []
  const detail = await api.get(`/purchase-plans/${detailPlan.value.id}`)
  detailPlan.value = detail.data
}

async function save() {
  try {
    await formRef.value.validate()
  } catch {
    ElMessage.warning('请完善必填项后再保存')
    return
  }
  const payload = {
    ...form,
    items: form.items.map(it => ({
      materialId: it.materialId ?? null,
      materialName: it.materialName || '',
      spec: it.spec || '',
      unit: it.unit || '',
      qty: it.qty,
      estimatedPrice: it.estimatedPrice
    }))
  }
  if (form.id) await api.put(`/purchase-plans/${form.id}`, payload)
  else await api.post('/purchase-plans', payload)
  ElMessage.success('已保存')
  dialog.value = false
  load()
}

async function act(row, action) {
  if (action === 'reject') {
    const { value } = await ElMessageBox.prompt('驳回原因', '驳回计划')
    await api.post(`/purchase-plans/${row.id}/reject`, null, { params: { reason: value } })
  } else if (action === 'cancel') {
    await ElMessageBox.confirm('确认作废该计划？', '提示')
    await api.post(`/purchase-plans/${row.id}/cancel`)
  } else {
    await api.post(`/purchase-plans/${row.id}/${action}`)
  }
  ElMessage.success('操作成功')
  load()
}

onMounted(async () => {
  await loadDicts()
  load()
  loadOptions()
})
</script>

<style scoped>
.bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; align-items: center; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
.hint { font-size: 12px; color: #94a3b8; line-height: 1.5; }
</style>

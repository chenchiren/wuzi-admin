<template>
  <el-card v-loading="loading">
    <div class="bar">
      <el-select v-model="status" placeholder="状态" clearable style="width:140px" @change="load">
        <el-option v-for="s in statuses" :key="s" :value="s" :label="tInquiryStatus(s)" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'po:inquiry:create'" @click="openFromApply">从申请生成询价</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="inquiryNo" label="询价单号" min-width="180" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column label="来源申请" min-width="150">
        <template #default="{ row }">
          <span v-if="row.applyNo || row.applyId" class="text-primary">{{ row.applyNo || `#${row.applyId}` }}</span>
          <span v-else class="text-slate-400">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="PURCHASE_INQUIRY_STATUS" />
        </template>
      </el-table-column>
      <el-table-column prop="inquireTime" label="询价时间" min-width="160" />
      <el-table-column label="操作" min-width="360">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text @click="openDetail(row)">明细/比价</n-button>
            <n-button v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'po:inquiry:submit'" size="small" text type="primary" @click="submit(row)">提交审批</n-button>
            <n-button v-if="row.status === 'PENDING_APPROVAL'" v-perm="'po:inquiry:approve'" size="small" text type="success" @click="approve(row)">通过</n-button>
            <n-button v-if="row.status === 'PENDING_APPROVAL'" v-perm="'po:inquiry:reject'" size="small" text type="warning" @click="reject(row)">驳回</n-button>
            <n-button v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'po:inquiry:quote'" size="small" text type="primary" @click="openQuote(row)">录入报价</n-button>
            <n-button v-if="row.status === 'APPROVED' && !row.selectedSupplierId" v-perm="'po:inquiry:select'" size="small" text type="success" @click="openSelect(row)">选定供应商</n-button>
            <n-button v-if="row.status === 'APPROVED' && row.selectedSupplierId" v-perm="'po:inquiry:complete'" size="small" text type="success" @click="complete(row)">完成询价</n-button>
            <n-button v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'po:inquiry:cancel'" size="small" text type="warning" @click="cancel(row)">作废</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="fromDialog" title="从已通过采购申请生成询价" width="560px">
      <el-form label-width="100px">
        <el-form-item label="采购申请">
          <el-select v-model="fromApplyId" filterable class="w-full" placeholder="输入申请单号搜索">
            <el-option v-for="a in approvedApplies" :key="a.id" :value="a.id" :label="applyLabel(a)" />
          </el-select>
        </el-form-item>
        <div class="text-xs leading-5 text-slate-500">仅列出「已通过」的采购申请；生成后请在询价单中录入供应商报价，再提交审批、选定供应商生成订单。</div>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="fromDialog=false">取消</n-button>
          <n-button type="primary" :disabled="!fromApplyId" :loading="creating" @click="createFromApply">生成</n-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="detailDialog" title="询价明细 / 比价" width="900px">
      <el-table :data="detailItems" border size="small">
        <el-table-column prop="supplierId" label="供应商" min-width="90">
          <template #default="{ row }">{{ supplierLabel(row.supplierId) }}</template>
        </el-table-column>
        <el-table-column prop="materialId" label="物资" min-width="140">
          <template #default="{ row }">{{ materialLabel(row.materialId) }}</template>
        </el-table-column>
        <el-table-column prop="qty" label="数量" min-width="80" />
        <el-table-column prop="taxInclusivePrice" label="含税单价" min-width="100" />
        <el-table-column prop="taxRate" label="税率%" min-width="80" />
        <el-table-column prop="taxCode" label="税种" min-width="90" />
        <el-table-column prop="taxInclusiveAmount" label="含税金额" min-width="100" />
        <el-table-column prop="isSelected" label="已选" min-width="70">
          <template #default="{ row }">{{ row.isSelected ? '是' : '否' }}</template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <el-dialog v-model="quoteDialog" title="录入供应商报价" width="720px">
      <el-form label-width="100px">
        <el-form-item label="供应商">
          <el-select v-model="quoteForm.supplierId" class="w-full">
            <el-option v-for="s in suppliers" :key="s.id" :value="s.id" :label="s.supplierName" />
          </el-select>
        </el-form-item>
        <el-form-item label="报价明细">
          <el-table :data="quoteForm.lines" size="small" border>
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
            <el-table-column label="含税单价" min-width="110">
              <template #default="{ row }"><el-input-number v-model="row.taxInclusivePrice" :min="0.000001" :precision="2" size="small" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="税率" min-width="140">
              <template #default="{ row }">
                <TaxRateSelect v-model:tax-code="row.taxCode" v-model:tax-rate="row.taxRate" />
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="60">
              <template #default="{ $index }"><n-button size="small" text type="error" @click="quoteForm.lines.splice($index,1)">删</n-button></template>
            </el-table-column>
          </el-table>
          <n-button class="mt-2" @click="quoteForm.lines.push({ materialId: null, qty: 1, taxInclusivePrice: 1, taxCode: null, taxRate: 13 })">加行</n-button>
        </el-form-item>
      </el-form>
      <template #footer>
        
        <div class="flex items-center justify-end gap-2">
        <n-button @click="quoteDialog=false">取消</n-button>
        <n-button type="primary" :disabled="!quoteForm.supplierId" @click="saveQuotes">保存报价</n-button>
        </div>
      
      </template>
    </el-dialog>

    <el-dialog v-model="selectDialog" title="选定供应商并可生成订单" width="480px">
      <el-form label-width="100px">
        <el-form-item label="供应商">
          <el-select v-model="selectForm.supplierId" class="w-full">
            <el-option v-for="s in suppliers" :key="s.id" :value="s.id" :label="s.supplierName" />
          </el-select>
        </el-form-item>
        <el-form-item label="生成订单">
          <el-switch v-model="selectForm.createOrder" />
        </el-form-item>
        <el-form-item label="预计到货">
          <el-date-picker v-model="selectForm.expectArrivalDate" value-format="YYYY-MM-DD" class="w-full" />
        </el-form-item>
      </el-form>
      <template #footer>
        
        <div class="flex items-center justify-end gap-2">
        <n-button @click="selectDialog=false">取消</n-button>
        <n-button type="primary" :disabled="!selectForm.supplierId" @click="doSelect">确定</n-button>
        </div>
      
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { tInquiryStatus, tBiz, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'
import TaxRateSelect from '../components/TaxRateSelect.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const status = ref('')
const statuses = ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'COMPLETED', 'CANCELLED']
const loading = ref(false)

const suppliers = ref([])
const materials = ref([])
const approvedApplies = ref([])

const fromDialog = ref(false)
const fromApplyId = ref(null)
const creating = ref(false)
const detailDialog = ref(false)
const detailItems = ref([])
const quoteDialog = ref(false)
const selectDialog = ref(false)

function applyLabel(a) {
  const plan = a.planNo ? ` · 计划${a.planNo}` : ''
  const amt = a.totalTaxInclAmount != null ? ` · ¥${Number(a.totalTaxInclAmount).toFixed(2)}` : ''
  return `${a.applyNo}${plan}${amt}`
}

const quoteForm = reactive({ inquiryId: null, supplierId: null, lines: [{ materialId: null, qty: 1, taxInclusivePrice: 1, taxCode: null, taxRate: 13 }] })
const selectForm = reactive({ inquiryId: null, supplierId: null, createOrder: true, expectArrivalDate: '' })

function supplierLabel(id) {
  const s = suppliers.value.find(x => x.id === id)
  return s ? s.supplierName : (id || '-')
}
function materialLabel(id) {
  const m = materials.value.find(x => x.id === id)
  return m ? `${m.materialCode} ${m.materialName}` : (id || '-')
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/purchase/inquiries', { params: { page: page.value, size: 10, status: status.value } })
    rows.value = res.data.records
    total.value = res.data.total
  } catch (e) { ElMessage.error(e.message || '加载失败') } finally { loading.value = false }
}

async function openFromApply() {
  fromDialog.value = true
  try {
    const res = await api.get('/purchase/applies', { params: { page: 1, size: 50, status: 'APPROVED' } })
    approvedApplies.value = res.data.records || []
  } catch (e) { ElMessage.error(e.message || '加载申请失败') }
}

async function createFromApply() {
  creating.value = true
  try {
    const res = await api.post(`/purchase/inquiries/from-apply/${fromApplyId.value}`)
    const inquiryNo = res.data?.inquiry?.inquiryNo
    ElMessage.success(inquiryNo ? `询价单 ${inquiryNo} 已生成` : '询价单已生成')
    fromDialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '生成失败')
  } finally {
    creating.value = false
  }
}

async function openDetail(row) {
  try {
    const res = await api.get(`/purchase/inquiries/${row.id}`)
    detailItems.value = res.data.items || []
    detailDialog.value = true
  } catch (e) { ElMessage.error(e.message || '加载明细失败') }
}

function openQuote(row) {
  quoteForm.inquiryId = row.id
  quoteForm.supplierId = null
  quoteForm.lines = [{ materialId: null, qty: 1, taxInclusivePrice: 1, taxCode: null, taxRate: 13 }]
  quoteDialog.value = true
}

async function saveQuotes() {
  try {
    await api.post(`/purchase/inquiries/${quoteForm.inquiryId}/quotes`, {
      supplierId: quoteForm.supplierId,
      lines: quoteForm.lines
    })
    ElMessage.success('报价已录入')
    quoteDialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '录入失败') }
}

function openSelect(row) {
  selectForm.inquiryId = row.id
  selectForm.supplierId = null
  selectForm.createOrder = true
  selectDialog.value = true
}

async function doSelect() {
  try {
    const res = await api.post(`/purchase/inquiries/${selectForm.inquiryId}/select-supplier`, {
      supplierId: selectForm.supplierId,
      createOrder: selectForm.createOrder,
      expectArrivalDate: selectForm.expectArrivalDate || null
    })
    const orderNo = res.data?.order?.orderNo
    if (selectForm.createOrder && orderNo) {
      await ElMessageBox.alert(
        `已选定供应商并生成采购订单 ${orderNo}。\n下一步：采购订单 → 提交审批 → 生成到货 / 入库。`,
        '生成成功',
        { confirmButtonText: '知道了' }
      )
    } else {
      ElMessage.success('已选定供应商')
    }
    selectDialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '选定失败') }
}

async function complete(row) {
  await ElMessageBox.confirm(`完成询价 ${row.inquiryNo || row.id}？`, '提示')
  try {
    await api.post(`/purchase/inquiries/${row.id}/complete`)
    ElMessage.success('询价已完成')
    load()
  } catch (e) { ElMessage.error(e.message || '完成失败') }
}

async function submit(row) {
  await ElMessageBox.confirm(`提交询价 ${row.inquiryNo || row.id} 审批？`, '提示')
  try {
    await api.post(`/purchase/inquiries/${row.id}/submit`, {})
    ElMessage.success(row.status === 'REJECTED' ? '已重新发起' : '已提交审批')
    load()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
}

async function approve(row) {
  await ElMessageBox.confirm(`审批通过询价 ${row.inquiryNo || row.id}？`, '提示')
  try {
    await api.post(`/purchase/inquiries/${row.id}/approve`, { comment: '同意' })
    ElMessage.success('已通过')
    load()
  } catch (e) { ElMessage.error(e.message || '审批失败') }
}

async function reject(row) {
  const { value } = await ElMessageBox.prompt(`驳回询价 ${row.inquiryNo || row.id}，请填写原因`, '驳回', {
    inputPlaceholder: '驳回原因（可选）'
  }).catch(() => ({ value: null }))
  try {
    await api.post(`/purchase/inquiries/${row.id}/reject`, { comment: value || '' })
    ElMessage.success('已驳回')
    load()
  } catch (e) { ElMessage.error(e.message || '驳回失败') }
}

async function cancel(row) {
  await ElMessageBox.confirm(`作废询价 ${row.inquiryNo || row.id}？`, '提示')
  try {
    await api.post(`/purchase/inquiries/${row.id}/cancel`)
    ElMessage.success('已作废')
    load()
  } catch (e) { ElMessage.error(e.message || '作废失败') }
}

onMounted(async () => {
  await loadDicts()
  load()
  try {
    const [s, m] = await Promise.all([
      api.get('/suppliers', { params: { page: 1, size: 200 } }),
      api.get('/materials', { params: { page: 1, size: 200 } })
    ])
    suppliers.value = s.data.records || []
    materials.value = m.data.records || []
  } catch { /* ignore */ }
})
</script>

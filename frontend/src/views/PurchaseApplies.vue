<template>
  <el-card v-loading="loading">
    <div class="bar">
      <el-select v-model="status" placeholder="状态" clearable style="width:140px" @change="load">
        <el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'po:apply:create'" @click="openCreate">新建采购申请</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="applyNo" label="申请单号" min-width="180" />
      <el-table-column label="来源计划" min-width="150">
        <template #default="{ row }">
          <span v-if="row.planNo" class="text-primary">{{ row.planNo }}</span>
          <span v-else class="text-slate-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="PURCHASE_APPLY_STATUS" />
        </template>
      </el-table-column>
      <el-table-column label="紧急度" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.urgency" dict-type="URGENCY" effect="plain" />
        </template>
      </el-table-column>
      <el-table-column prop="needDate" label="需求日" min-width="110" />
      <el-table-column prop="purpose" label="用途" min-width="140" />
      <el-table-column prop="totalTaxInclAmount" label="含税合计" min-width="110" />
      <el-table-column prop="applyTime" label="申请时间" min-width="160" />
      <el-table-column label="操作" min-width="320">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text @click="openFlow(row)">流程</n-button>
            <n-button size="small" v-if="['DRAFT','REJECTED'].includes(row.status)" text type="warning" @click="openEdit(row)">修改</n-button>
            <n-button size="small" v-if="row.status==='DRAFT'" v-perm="'po:apply:submit'" text type="primary" @click="submit(row)">提交</n-button>
            <n-button size="small" v-if="row.status==='REJECTED'" v-perm="'po:apply:submit'" text type="primary" @click="submit(row)">重新发起</n-button>
            <n-button
              size="small"
              v-if="row.status==='APPROVED'"
              v-perm="'po:inquiry:create'"
              text
              type="success"
              :loading="genInquiryId === row.id"
              @click="genInquiry(row)"
            >生成询价</n-button>
            <n-button size="small" v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'po:apply:cancel'" text type="warning" @click="cancel(row)">作废</n-button>
            <n-button size="small" v-perm="'po:apply:delete'" text type="error" @click="remove(row)">删除</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="dialog" :title="form.id ? (form.status === 'REJECTED' ? '修改采购申请（驳回后）' : '修改采购申请') : '新建采购申请'" width="720px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item v-if="form.planNo" label="来源计划">
          <el-input :model-value="form.planNo" disabled />
        </el-form-item>
        <el-form-item label="需求日" prop="needDate">
          <el-date-picker v-model="form.needDate" value-format="YYYY-MM-DD" class="w-full" placeholder="请选择需求日" />
        </el-form-item>
        <el-form-item label="紧急度" prop="urgency">
          <el-select v-model="form.urgency" class="w-full">
            <el-option v-for="o in urgencyOptions" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="用途"><el-input v-model="form.purpose" /></el-form-item>
        <el-form-item label="明细" prop="items">
          <el-table :data="form.items" size="small" border>
            <el-table-column label="物资" min-width="180">
              <template #default="{ row }">
                <el-select v-model="row.materialId" class="w-full" placeholder="请选择物资" filterable>
                  <el-option v-for="m in materials" :key="m.id" :value="m.id" :label="`${m.materialCode} ${m.materialName}`" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" min-width="110">
              <template #default="{ row }"><el-input-number v-model="row.qty" :min="1" size="small" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="参考税率" min-width="140">
              <template #default="{ row }">
                <TaxRateSelect v-model:tax-code="row.taxCode" v-model:tax-rate="row.taxRate" />
              </template>
            </el-table-column>
            <el-table-column label="操作" min-width="70">
              <template #default="{ $index }"><n-button size="small" text type="error" @click="form.items.splice($index,1)">删</n-button></template>
            </el-table-column>
          </el-table>
          <n-button class="mt-2" @click="form.items.push({ materialId: null, qty: 1, taxCode: null, taxRate: null })">加行</n-button>
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
  
    <ApprovalFlowDialog
      v-model="flowDialog"
      :biz-type="flowBizType"
      :biz-id="flowBizId"
      :title="flowTitle"
    />
  </el-card>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { tPurchaseApplyStatus, tUrgency, loadDicts, dictOptionsOr } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'
import ApprovalFlowDialog from '../components/ApprovalFlowDialog.vue'
import TaxRateSelect from '../components/TaxRateSelect.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const status = ref('')
const statusOptions = computed(() =>
  dictOptionsOr('PURCHASE_APPLY_STATUS', ['DRAFT', 'SUBMITTED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'CANCELLED', 'COMPLETED'])
)
const urgencyOptions = computed(() => dictOptionsOr('URGENCY', ['NORMAL', 'URGENT']))

function statusLabel(s) {
  return tPurchaseApplyStatus(s)
}



function urgencyLabel(u) {
  return tUrgency(u)
}


const dialog = ref(false)
const materials = ref([])
const loading = ref(false)
const genInquiryId = ref(null)
const form = reactive({
  id: null, status: '', needDate: '', urgency: 'NORMAL', purpose: '', planNo: '',
  items: [{ materialId: null, qty: 1, taxCode: null, taxRate: null }]
})
const formRef = ref()
const rules = {
  needDate: [{ required: true, message: '请选择需求日', trigger: 'change' }],
  urgency: [{ required: true, message: '请选择紧急度', trigger: 'change' }],
  items: [{
    validator: (rule, value, callback) => {
      if (!value || !value.length) {
        callback(new Error('请添加申请明细'))
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
      }
      callback()
    },
    trigger: 'change'
  }]
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/purchase/applies', { params: { page: page.value, size: 10, status: status.value } })
    rows.value = res.data.records
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

function openCreate() {
  form.id = null
  form.status = ''
  form.needDate = ''
  form.urgency = 'NORMAL'
  form.purpose = ''
  form.planNo = ''
  form.items = [{ materialId: null, qty: 1, taxCode: null, taxRate: null }]
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function openEdit(row) {
  try {
    const res = await api.get(`/purchase/applies/${row.id}`)
    const apply = res.data.apply || row
    const items = res.data.items || []
    form.id = apply.id
    form.status = apply.status
    form.needDate = apply.needDate || ''
    form.urgency = apply.urgency || 'NORMAL'
    form.purpose = apply.purpose || ''
    form.planNo = apply.planNo || row.planNo || ''
    form.items = items.length
      ? items.map((it) => ({
          materialId: it.materialId,
          qty: it.qty,
          taxCode: it.taxCode,
          taxRate: it.taxRate
        }))
      : [{ materialId: null, qty: 1, taxCode: null, taxRate: null }]
    dialog.value = true
    nextTick(() => formRef.value?.clearValidate())
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  }
}

function payload() {
  return {
    needDate: form.needDate,
    urgency: form.urgency,
    purpose: form.purpose,
    items: form.items
  }
}

async function validateForm() {
  try {
    await formRef.value.validate()
    return true
  } catch {
    ElMessage.warning('请完善必填项后再保存')
    return false
  }
}

async function save() {
  if (!(await validateForm())) return
  try {
    if (form.id) {
      await api.put(`/purchase/applies/${form.id}`, payload())
      ElMessage.success('已保存修改')
    } else {
      await api.post('/purchase/applies', payload())
      ElMessage.success('已创建')
    }
    dialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function saveAndResubmit() {
  if (!(await validateForm())) return
  try {
    if (!form.id) {
      await save()
      return
    }
    await api.put(`/purchase/applies/${form.id}`, payload())
    await api.post(`/purchase/applies/${form.id}/submit`, {})
    ElMessage.success('已保存并重新发起（从头审批）')
    dialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '重新发起失败')
  }
}

async function submit(row) {
  try {
    await api.post(`/purchase/applies/${row.id}/submit`, {})
    ElMessage.success(row.status === 'REJECTED' ? '已重新发起（从头审批）' : '已提交')
    load()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
}

/** 已通过申请 → 生成询价草稿，进入询价板块继续报价/比价/下单 */
async function genInquiry(row) {
  await ElMessageBox.confirm(
    `将根据申请「${row.applyNo || row.id}」的明细生成一张询价草稿单，请到「询价」继续录入报价并选定供应商。`,
    '生成询价'
  )
  genInquiryId.value = row.id
  try {
    const res = await api.post(`/purchase/inquiries/from-apply/${row.id}`)
    const inquiryNo = res.data?.inquiry?.inquiryNo
    await ElMessageBox.alert(
      `询价单已生成：${inquiryNo || '（见询价列表）'}。\n下一步：询价 → 录报价 → 提交审批 → 选定供应商（可生成采购订单）。`,
      '生成成功',
      { confirmButtonText: '知道了' }
    )
    load()
  } catch (e) {
    ElMessage.error(e.message || '生成询价失败')
  } finally {
    genInquiryId.value = null
  }
}

async function cancel(row) {
  try {
    await api.post(`/purchase/applies/${row.id}/cancel`)
    ElMessage.success('已作废')
    load()
  } catch (e) { ElMessage.error(e.message || '作废失败') }
}

async function remove(row) {
  await ElMessageBox.confirm('确认删除该申请？', '提示')
  try {
    await api.delete(`/purchase/applies/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

const flowDialog = ref(false)
const flowBizType = ref('')
const flowBizId = ref(null)
const flowTitle = ref('')

function openFlow(row) {
  flowBizType.value = 'PURCHASE_APPLY'
  flowBizId.value = row.id
  flowTitle.value = `审批流程 · ${row.applyNo || row.id}`
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

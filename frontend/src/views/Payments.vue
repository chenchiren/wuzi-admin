<template>
  <el-card>
    <div class="bar">
      <el-select v-model="query.status" clearable placeholder="状态" style="width:130px">
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'fin:payment:create'" @click="openCreate">新建付款</n-button>
      <n-button @click="loadAging">应付账龄</n-button>
    </div>

    <el-table :data="rows" border stripe>
      <el-table-column prop="paymentNo" label="付款单号" min-width="150" />
      <el-table-column label="供应商" min-width="120">
        <template #default="{ row }">{{ supplierLabel(row.supplierId) }}</template>
      </el-table-column>
      <el-table-column label="合同" min-width="140">
        <template #default="{ row }">{{ contractLabel(row.contractId) }}</template>
      </el-table-column>
      <el-table-column prop="settleId" label="结算ID" width="90" />
      <el-table-column label="金额" min-width="110">
        <template #default="{ row }">{{ money(row.amount) }}</template>
      </el-table-column>
      <el-table-column label="方式" width="90">
        <template #default="{ row }"><StatusTag :value="row.payMethod" dict-type="PAY_METHOD" effect="plain" /></template>
      </el-table-column>
      <el-table-column prop="payDate" label="付款日" width="110" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><StatusTag :value="row.status" dict-type="PAYMENT_STATUS" /></template>
      </el-table-column>
      <el-table-column label="操作" min-width="300">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text v-perm="'fin:payment:submit'" v-if="row.status==='DRAFT'||row.status==='REJECTED'" @click="act(row,'submit')">提交</n-button>
            <n-button size="small" text type="success" v-perm="'fin:payment:confirm'" v-if="row.status==='PENDING'" @click="act(row,'confirm')">确认付款</n-button>
            <n-button size="small" text type="warning" v-perm="'fin:payment:reject'" v-if="row.status==='PENDING'||row.status==='DRAFT'" @click="reject(row)">驳回</n-button>
            <n-button size="small" text type="error" v-perm="'fin:payment:cancel'" v-if="row.status!=='PAID'&&row.status!=='CANCELLED'" @click="act(row,'cancel')">作废</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total"
        :page-size="query.size" :current-page="query.page" @current-change="p => { query.page = p; load() }" />
    </div>

    <el-dialog v-model="dialog" title="新建付款单" width="560px">
      <el-form label-width="100px">
        <el-form-item label="供应商">
          <el-select v-model="form.supplierId" filterable class="w-full" placeholder="选择供应商">
            <el-option v-for="s in suppliers" :key="s.id" :value="s.id" :label="s.supplierName" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同">
          <el-select v-model="form.contractId" filterable clearable class="w-full" placeholder="选择合同（可选）" @change="onContractChange">
            <el-option v-for="c in contracts" :key="c.id" :value="c.id" :label="`${c.contractNo} · ${c.supplierName || ''}`" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="form.contractId" label="付款节点">
          <el-select v-model="form.payNodeId" clearable class="w-full" placeholder="选择付款节点（可选）" @change="onNodeChange">
            <el-option v-for="n in payNodes" :key="n.id" :value="n.id" :label="`${n.nodeName || n.nodeName || '节点'} · 应付 ${n.planAmount ?? n.amount ?? ''}`" />
          </el-select>
        </el-form-item>
        <el-form-item label="结算单">
          <el-select v-model="form.settleId" class="w-full" clearable filterable placeholder="选择结算单（可选）">
            <el-option v-for="s in settles" :key="s.id" :label="`${s.settleNo}（${s.settleAmount ?? '—'}）`" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="金额"><el-input-number v-model="form.amount" :min="0" :precision="2" class="w-full" /></el-form-item>
        <el-form-item label="方式">
          <el-select v-model="form.payMethod" class="w-full">
            <el-option value="BANK" label="银行转账" />
            <el-option value="ACCEPTANCE" label="承兑" />
            <el-option value="CASH" label="现金" />
          </el-select>
        </el-form-item>
        <el-form-item label="付款日">
          <el-date-picker v-model="form.payDate" type="date" value-format="YYYY-MM-DD" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <div class="flex justify-end gap-2">
          <n-button @click="dialog=false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="agingDlg" title="应付账龄（按供应商）" size="48%">
      <el-table :data="aging" border size="small">
        <el-table-column label="供应商" min-width="120">
          <template #default="{ row }">{{ supplierLabel(row.supplierId) }}</template>
        </el-table-column>
        <el-table-column label="未付合计" width="110">
          <template #default="{ row }">{{ money(row.totalUnpaid) }}</template>
        </el-table-column>
        <el-table-column label="0-30" width="100">
          <template #default="{ row }">{{ money(row.b0_30) }}</template>
        </el-table-column>
        <el-table-column label="31-60" width="100">
          <template #default="{ row }">{{ money(row.b31_60) }}</template>
        </el-table-column>
        <el-table-column label="61-90" width="100">
          <template #default="{ row }">{{ money(row.b61_90) }}</template>
        </el-table-column>
        <el-table-column label="90+" width="100">
          <template #default="{ row }">{{ money(row.b90p) }}</template>
        </el-table-column>
      </el-table>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { loadDicts, dictOptionsOr } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const statusOptions = computed(() =>
  dictOptionsOr('PAYMENT_STATUS', ['DRAFT', 'PENDING', 'PAID', 'REJECTED', 'CANCELLED'])
)
const rows = ref([])
const total = ref(0)
const aging = ref([])
const query = reactive({ page: 1, size: 10, status: '' })
const dialog = ref(false)
const agingDlg = ref(false)
const form = reactive({ supplierId: null, contractId: null, settleId: null, payNodeId: null, amount: 0, payMethod: 'BANK', payDate: '', remark: '' })
const suppliers = ref([])
const contracts = ref([])
const payNodes = ref([])
const settles = ref([])

const money = v => v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

function supplierLabel(id) {
  const s = suppliers.value.find(x => x.id === id)
  return s ? s.supplierName : (id == null ? '—' : `#${id}`)
}

function contractLabel(id) {
  const c = contracts.value.find(x => x.id === id)
  return c ? c.contractNo : (id == null ? '—' : `#${id}`)
}

async function loadOptions() {
  const [s, c, st] = await Promise.all([
    api.get('/suppliers', { params: { page: 1, size: 200 } }).catch(() => ({ data: { records: [] } })),
    api.get('/contracts', { params: { page: 1, size: 100 } }).catch(() => ({ data: { records: [] } })),
    api.get('/settles', { params: { page: 1, size: 200 } }).catch(() => ({ data: { records: [] } }))
  ])
  suppliers.value = s.data?.records || []
  contracts.value = c.data?.records || []
  settles.value = st.data?.records || []
}

async function onContractChange(id) {
  form.payNodeId = null
  payNodes.value = []
  if (!id) return
  try {
    const res = await api.get(`/contracts/${id}/pay-nodes`)
    payNodes.value = res.data || []
  } catch { /* ignore */ }
}

function onNodeChange(id) {
  const n = payNodes.value.find(x => x.id === id)
  if (n) {
    const amt = n.planAmount ?? n.amount ?? n.nodeAmount
    if (amt != null) form.amount = Number(amt)
  }
}

async function load() {
  const res = await api.get('/payments', { params: { page: query.page, size: query.size, status: query.status || undefined } })
  rows.value = res.data.records || []
  total.value = res.data.total || 0
}

async function loadAging() {
  const res = await api.get('/payments/ap-aging')
  aging.value = res.data || []
  agingDlg.value = true
}

function openCreate() {
  Object.assign(form, { supplierId: null, contractId: null, settleId: null, payNodeId: null, amount: 0, payMethod: 'BANK', payDate: '', remark: '' })
  payNodes.value = []
  dialog.value = true
  loadOptions()
}

async function save() {
  try {
    await api.post('/payments', form)
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

async function act(row, action) {
  try {
    if (action === 'cancel') await ElMessageBox.confirm('确认作废付款单？', '提示')
    await api.post(`/payments/${row.id}/${action}`)
    ElMessage.success('操作成功')
    load()
  } catch (e) { if (e !== 'cancel') ElMessage.error(e.message || '操作失败') }
}

async function reject(row) {
  const { value } = await ElMessageBox.prompt('驳回原因', '驳回付款')
  await api.post(`/payments/${row.id}/reject`, null, { params: { reason: value } })
  load()
}

onMounted(async () => {
  await loadDicts()
  await load()
  await loadOptions()
})
</script>

<style scoped>
.bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; align-items: center; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>

<template>
  <el-card>
    <div class="bar">
      <el-input v-model="query.keyword" placeholder="合同号/标题" style="width:180px" clearable />
      <el-select v-model="query.status" clearable placeholder="状态" style="width:130px">
        <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'po:contract:create'" @click="openCreate">新建合同</n-button>
    </div>

    <el-table :data="rows" border stripe>
      <el-table-column prop="contractNo" label="合同号" min-width="150" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }"><StatusTag :value="row.contractType" dict-type="CONTRACT_TYPE" effect="plain" /></template>
      </el-table-column>
      <el-table-column label="合同额" min-width="110">
        <template #default="{ row }">{{ money(row.contractAmount) }}</template>
      </el-table-column>
      <el-table-column label="已付" min-width="100">
        <template #default="{ row }">{{ money(row.paidAmount) }}</template>
      </el-table-column>
      <el-table-column prop="signDate" label="签订日" width="110" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }"><StatusTag :value="row.status" dict-type="CONTRACT_STATUS" /></template>
      </el-table-column>
      <el-table-column label="操作" min-width="300">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text type="primary" @click="openDetail(row)">明细</n-button>
            <n-button size="small" text v-perm="'po:contract:submit'" v-if="row.status==='DRAFT'" @click="act(row,'submit')">提交</n-button>
            <n-button size="small" text type="success" v-perm="'po:contract:activate'" v-if="row.status==='PENDING'||row.status==='DRAFT'" @click="act(row,'activate')">生效</n-button>
            <n-button size="small" text v-perm="'po:contract:close'" v-if="row.status==='ACTIVE'" @click="close(row,'CLOSED')">关闭</n-button>
            <n-button size="small" text type="warning" v-perm="'po:contract:close'" v-if="row.status==='ACTIVE'" @click="close(row,'TERMINATED')">终止</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total"
        :page-size="query.size" :current-page="query.page" @current-change="p => { query.page = p; load() }" />
    </div>

    <el-dialog v-model="dialog" title="新建合同" width="760px">
      <el-form label-width="100px">
        <el-form-item label="标题"><el-input v-model="form.title" /></el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.contractType" class="w-full">
            <el-option value="ORDINARY" label="普通合同" />
            <el-option value="FRAMEWORK" label="框架协议" />
          </el-select>
        </el-form-item>
        <el-form-item label="供应商" prop="supplierId">
          <el-select v-model="form.supplierId" class="w-full" filterable placeholder="请选择供应商">
            <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierName" :value="s.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="签订/起止">
          <el-date-picker v-model="form.signDate" type="date" value-format="YYYY-MM-DD" style="width:130px" />
          <el-date-picker v-model="form.startDate" type="date" value-format="YYYY-MM-DD" style="width:130px;margin-left:6px" />
          <el-date-picker v-model="form.endDate" type="date" value-format="YYYY-MM-DD" style="width:130px;margin-left:6px" />
        </el-form-item>
        <el-form-item label="预算">
          <el-select v-model="form.budgetId" class="w-full" clearable filterable placeholder="选择预算（可选）">
            <el-option v-for="b in budgets" :key="b.id" :label="`${b.budgetNo}（${b.budgetYear} ${b.category}）`" :value="b.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="合同行">
          <div class="w-full">
            <div v-for="(it, i) in form.items" :key="i" class="flex gap-2 mb-2 items-center">
              <el-input v-model="it.materialName" placeholder="物资" style="width:120px" />
              <el-input-number v-model="it.qty" :min="0" :precision="3" style="width:90px" />
              <el-input-number v-model="it.price" :min="0" :precision="2" style="width:100px" />
              <el-input-number v-model="it.taxRate" :min="0" :max="100" :precision="2" style="width:90px" />
              <el-input v-model="it.unit" placeholder="单位" style="width:60px" />
              <n-button size="small" text type="error" @click="form.items.splice(i,1)">删</n-button>
            </div>
            <n-button size="small" @click="form.items.push({ materialName:'', qty:1, price:0, taxRate:13, unit:'' })">加行</n-button>
          </div>
        </el-form-item>
        <el-form-item label="付款节点">
          <div class="w-full">
            <div v-for="(n, i) in form.payNodes" :key="i" class="flex gap-2 mb-2 items-center">
              <el-input v-model="n.nodeName" placeholder="节点名" style="width:120px" />
              <el-date-picker v-model="n.planDate" type="date" value-format="YYYY-MM-DD" style="width:130px" />
              <el-input-number v-model="n.planAmount" :min="0" :precision="2" style="width:110px" />
              <n-button size="small" text type="error" @click="form.payNodes.splice(i,1)">删</n-button>
            </div>
            <n-button size="small" @click="form.payNodes.push({ nodeName:'', planDate:'', planAmount:0 })">加节点</n-button>
          </div>
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

    <el-drawer v-model="detailDlg" title="合同明细" size="52%">
      <div v-if="detail">
        <p class="mb-2 text-sm text-slate-600">{{ detail.contract?.contractNo }} · {{ detail.contract?.title }}</p>
        <el-table :data="detail.items || []" border size="small" class="mb-4">
          <el-table-column prop="materialName" label="物资" min-width="110" />
          <el-table-column prop="qty" label="数量" width="80" />
          <el-table-column label="含税金额" width="110">
            <template #default="{ row }">{{ money(row.taxInclusiveAmount) }}</template>
          </el-table-column>
          <el-table-column label="税额" width="100">
            <template #default="{ row }">{{ money(row.taxAmount) }}</template>
          </el-table-column>
        </el-table>
        <el-table :data="detail.payNodes || []" border size="small">
          <el-table-column prop="nodeName" label="节点" min-width="100" />
          <el-table-column prop="planDate" label="计划日" width="110" />
          <el-table-column label="计划金额" width="110">
            <template #default="{ row }">{{ money(row.planAmount) }}</template>
          </el-table-column>
          <el-table-column label="已付" width="100">
            <template #default="{ row }">{{ money(row.paidAmount) }}</template>
          </el-table-column>
          <el-table-column label="状态" width="90">
            <template #default="{ row }"><StatusTag :value="row.status" dict-type="CONTRACT_PAY_NODE_STATUS" effect="plain" /></template>
          </el-table-column>
        </el-table>
      </div>
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
  dictOptionsOr('CONTRACT_STATUS', ['DRAFT', 'PENDING', 'ACTIVE', 'CLOSED', 'TERMINATED'])
)
const rows = ref([])
const total = ref(0)
const detail = ref(null)
const query = reactive({ page: 1, size: 10, status: '', keyword: '' })
const dialog = ref(false)
const detailDlg = ref(false)
const suppliers = ref([])
const budgets = ref([])
const form = reactive({
  title: '', contractType: 'ORDINARY', supplierId: null, signDate: '', startDate: '', endDate: '',
  budgetId: null, remark: '', items: [], payNodes: []
})

async function loadOptions() {
  try {
    const [s, b] = await Promise.all([
      api.get('/suppliers', { params: { page: 1, size: 200 } }),
      api.get('/budgets', { params: { page: 1, size: 200 } })
    ])
    suppliers.value = s.data.records || []
    budgets.value = b.data.records || []
  } catch (_) { /* 下拉数据失败不阻塞表单 */ }
}

const money = v => v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

async function load() {
  const res = await api.get('/contracts', { params: { page: query.page, size: query.size, status: query.status || undefined, keyword: query.keyword || undefined } })
  rows.value = res.data.records || []
  total.value = res.data.total || 0
}

function openCreate() {
  Object.assign(form, {
    title: '', contractType: 'ORDINARY', supplierId: null, signDate: '', startDate: '', endDate: '',
    budgetId: null, remark: '',
    items: [{ materialName: '', qty: 1, price: 0, taxRate: 13, unit: '' }],
    payNodes: [{ nodeName: '首款', planDate: '', planAmount: 0 }]
  })
  loadOptions()
  dialog.value = true
}

async function openDetail(row) {
  const res = await api.get(`/contracts/${row.id}`)
  // detail endpoint returns contract only; fetch children
  const items = await api.get(`/contracts/${row.id}/items`)
  const payNodes = await api.get(`/contracts/${row.id}/pay-nodes`)
  detail.value = { contract: res.data, items: items.data, payNodes: payNodes.data }
  detailDlg.value = true
}

async function save() {
  try {
    await api.post('/contracts', form)
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

async function act(row, action) {
  try {
    await api.post(`/contracts/${row.id}/${action}`)
    ElMessage.success('操作成功')
    load()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

async function close(row, status) {
  await ElMessageBox.confirm(status === 'TERMINATED' ? '确认终止合同？' : '确认关闭合同？', '提示')
  await api.post(`/contracts/${row.id}/close`, null, { params: { status } })
  load()
}

onMounted(async () => {
  await loadDicts()
  await load()
})
</script>

<style scoped>
.bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; align-items: center; }
.pager { margin-top: 12px; display: flex; justify-content: flex-end; }
</style>

<template>
  <el-card>
    <div class="bar">
      <el-input-number v-model="query.budgetYear" :min="2020" :max="2100" style="width:120px" />
      <el-select v-model="query.category" clearable placeholder="类别" style="width:140px">
        <el-option value="PURCHASE" label="采购" />
        <el-option value="ASSET" label="资产" />
        <el-option value="OTHER" label="其他" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'fin:budget:create'" @click="openCreate">新建预算</n-button>
    </div>

    <el-table :data="rows" border stripe>
      <el-table-column prop="budgetNo" label="预算号" min-width="150" />
      <el-table-column prop="budgetYear" label="年度" width="80" />
      <el-table-column prop="category" label="类别" width="90" />
      <el-table-column prop="projectCode" label="项目号" min-width="100" />
      <el-table-column label="预算额" min-width="110">
        <template #default="{ row }">{{ money(row.budgetAmount) }}</template>
      </el-table-column>
      <el-table-column label="已用" min-width="100">
        <template #default="{ row }">{{ money(row.usedAmount) }}</template>
      </el-table-column>
      <el-table-column label="冻结" min-width="100">
        <template #default="{ row }">{{ money(row.frozenAmount) }}</template>
      </el-table-column>
      <el-table-column label="可用" min-width="100">
        <template #default="{ row }">{{ money(available(row)) }}</template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }"><StatusTag :value="row.status" dict-type="BUDGET_STATUS" /></template>
      </el-table-column>
      <el-table-column label="操作" min-width="220">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text type="primary" @click="openLogs(row)">流水</n-button>
            <n-button size="small" text v-perm="'fin:budget:update'" @click="openEdit(row)">调额</n-button>
            <n-button size="small" text v-perm="'fin:budget:toggle'" @click="toggle(row)">{{ row.status==='ACTIVE' ? '冻结' : '启用' }}</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <div class="pager">
      <el-pagination background layout="total, prev, pager, next" :total="total"
        :page-size="query.size" :current-page="query.page" @current-change="p => { query.page = p; load() }" />
    </div>

    <el-dialog v-model="dialog" :title="form.id ? '调整预算' : '新建预算'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="年度" prop="budgetYear"><el-input-number v-model="form.budgetYear" :min="2020" :max="2100" /></el-form-item>
        <el-form-item label="类别" prop="category">
          <el-select v-model="form.category" class="w-full">
            <el-option value="PURCHASE" label="采购" />
            <el-option value="ASSET" label="资产" />
            <el-option value="OTHER" label="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="项目号"><el-input v-model="form.projectCode" /></el-form-item>
        <el-form-item label="金额" prop="budgetAmount"><el-input-number v-model="form.budgetAmount" :min="0" :precision="2" class="w-full" placeholder="请输入预算金额" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <div class="flex justify-end gap-2">
          <n-button @click="dialog=false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="logDlg" title="预算控制流水" size="48%">
      <el-table :data="logs" border size="small">
        <el-table-column label="动作" width="100">
          <template #default="{ row }"><StatusTag :value="row.action" dict-type="BUDGET_ACTION" effect="plain" /></template>
        </el-table-column>
        <el-table-column label="金额" width="110">
          <template #default="{ row }">{{ money(row.amount) }}</template>
        </el-table-column>
        <el-table-column prop="bizType" label="业务" width="110" />
        <el-table-column prop="bizId" label="业务ID" width="90" />
        <el-table-column label="后可用变化" min-width="140">
          <template #default="{ row }">用 {{ money(row.afterUsed) }} / 冻 {{ money(row.afterFrozen) }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="140" />
      </el-table>
    </el-drawer>
  </el-card>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const total = ref(0)
const logs = ref([])
const query = reactive({ page: 1, size: 10, budgetYear: new Date().getFullYear(), category: '' })
const dialog = ref(false)
const logDlg = ref(false)
const form = reactive({ id: null, budgetYear: new Date().getFullYear(), category: 'PURCHASE', projectCode: '', budgetAmount: 0, remark: '' })
const formRef = ref()
const rules = {
  budgetYear: [{ required: true, message: '请输入年度', trigger: 'blur' }],
  category: [{ required: true, message: '请选择类别', trigger: 'change' }],
  budgetAmount: [
    { required: true, message: '请输入预算金额', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value == null || value === '') {
          callback(new Error('请输入预算金额'))
          return
        }
        if (Number(value) <= 0) {
          callback(new Error('预算金额须大于0'))
          return
        }
        callback()
      },
      trigger: 'blur'
    }
  ]
}

const money = v => v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })
const available = row => Number(row.budgetAmount || 0) - Number(row.usedAmount || 0) - Number(row.frozenAmount || 0)

async function load() {
  const res = await api.get('/budgets', { params: { page: query.page, size: query.size, budgetYear: query.budgetYear || undefined, category: query.category || undefined } })
  rows.value = res.data.records || []
  total.value = res.data.total || 0
}

function openCreate() {
  Object.assign(form, { id: null, budgetYear: new Date().getFullYear(), category: 'PURCHASE', projectCode: '', budgetAmount: null, remark: '' })
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}
function openEdit(row) {
  Object.assign(form, { id: row.id, budgetYear: row.budgetYear, category: row.category, projectCode: row.projectCode || '', budgetAmount: row.budgetAmount, remark: row.remark || '' })
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}
async function openLogs(row) {
  const res = await api.get(`/budgets/${row.id}/logs`)
  logs.value = res.data || []
  logDlg.value = true
}
async function save() {
  try {
    await formRef.value.validate()
  } catch {
    ElMessage.warning('请完善必填项后再保存')
    return
  }
  if (form.id) await api.put(`/budgets/${form.id}`, form)
  else await api.post('/budgets', form)
  ElMessage.success('已保存')
  dialog.value = false
  load()
}
async function toggle(row) {
  await ElMessageBox.confirm(`确认切换预算 ${row.budgetNo} 状态？`, '提示')
  await api.post(`/budgets/${row.id}/toggle`)
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

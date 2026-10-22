<template>
  <el-card>
    <div class="bar">
      <el-input v-model="keyword" placeholder="编码/名称" style="width:180px" clearable @change="load" />
      <el-select v-model="projectType" placeholder="类型" style="width:130px" clearable @change="load">
        <el-option v-for="c in typeOptions" :key="c.value" :label="c.label" :value="c.value" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'base:project:create'" @click="openCreate">新建项目</n-button>
      <span class="muted">项目归集工程/专项费用，成本中心归集部门日常费用</span>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="id" label="ID" min-width="70" />
      <el-table-column prop="projectCode" label="编码" min-width="130" />
      <el-table-column prop="projectName" label="名称" min-width="150" />
      <el-table-column label="类型" min-width="100">
        <template #default="{ row }">
          <el-tag size="small" :type="row.projectType === 'COST_CENTER' ? 'warning' : 'primary'">
            {{ tProjectType(row.projectType) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="managerName" label="负责人" min-width="100" />
      <el-table-column label="起止日期" min-width="180">
        <template #default="{ row }">
          <span v-if="row.startDate || row.endDate">{{ row.startDate || '—' }} ~ {{ row.endDate || '—' }}</span>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="tNumStatus(row.status)" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" />
      <el-table-column label="操作" min-width="180">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text type="primary" v-perm="'base:project:update'" @click="openEdit(row)">编辑</n-button>
            <n-button size="small" text type="error" v-perm="'base:project:delete'" @click="remove(row)">删除</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑项目' : '新建项目'" width="520px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="编码" prop="projectCode"><el-input v-model="form.projectCode" placeholder="如 PRJ-2026-001" /></el-form-item>
        <el-form-item label="名称" prop="projectName"><el-input v-model="form.projectName" /></el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="form.projectType">
            <el-radio v-for="c in typeOptions" :key="c.value" :value="c.value">{{ c.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="负责人"><el-input v-model="form.managerName" /></el-form-item>
        <el-form-item label="起止日期">
          <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD"
            start-placeholder="开始" end-placeholder="结束" class="w-full" />
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="dialog=false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
        </div>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { tNumStatus, loadDicts, dictOptionsOr, t } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const keyword = ref('')
const projectType = ref('')
const dialog = ref(false)
const dateRange = ref([])
const form = reactive({
  id: null, projectCode: '', projectName: '', projectType: 'PROJECT',
  managerName: '', status: 1, remark: ''
})
const formRef = ref()
const rules = {
  projectCode: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  projectName: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

const typeOptions = computed(() =>
  dictOptionsOr('PROJECT_TYPE', ['PROJECT', 'COST_CENTER']))

const tProjectType = (c) => t('PROJECT_TYPE', c)

async function load() {
  const res = await api.get('/projects', {
    params: { keyword: keyword.value || undefined, projectType: projectType.value || undefined }
  })
  rows.value = res.data || []
}

function openCreate() {
  Object.assign(form, {
    id: null, projectCode: '', projectName: '', projectType: 'PROJECT',
    managerName: '', status: 1, remark: ''
  })
  dateRange.value = []
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    projectCode: row.projectCode,
    projectName: row.projectName,
    projectType: row.projectType || 'PROJECT',
    managerName: row.managerName || '',
    status: row.status ?? 1,
    remark: row.remark || ''
  })
  dateRange.value = row.startDate || row.endDate ? [row.startDate || '', row.endDate || ''] : []
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function save() {
  try {
    await formRef.value.validate()
  } catch {
    ElMessage.warning('请完善必填项后再保存')
    return
  }
  try {
    const payload = {
      ...form,
      startDate: dateRange.value && dateRange.value[0] ? dateRange.value[0] : null,
      endDate: dateRange.value && dateRange.value[1] ? dateRange.value[1] : null
    }
    if (form.id) await api.put(`/projects/${form.id}`, payload)
    else await api.post('/projects', payload)
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除 ${row.projectName}？`, '提示')
  try {
    await api.delete(`/projects/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

onMounted(async () => {
  await loadDicts()
  load()
})
</script>

<style scoped>
.bar { display:flex; align-items:center; gap:8px; margin-bottom:12px; flex-wrap:wrap; }
.muted { color:#888; font-size:12px; }
</style>

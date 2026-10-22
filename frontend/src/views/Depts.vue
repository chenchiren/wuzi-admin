<template>
  <el-card>
    <div class="bar">
      <n-button type="success" v-perm="'sys:dept:create'" @click="openCreate">新建部门</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="id" label="ID" min-width="70" />
      <el-table-column prop="parentId" label="上级ID" min-width="80" />
      <el-table-column prop="deptCode" label="编码" min-width="110" />
      <el-table-column prop="deptName" label="名称" min-width="130" />
      <el-table-column label="类型" min-width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="typeTag(row.deptType)">{{ tDeptType(row.deptType || 'DEPT') }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortNo" label="排序" min-width="70" />
      <el-table-column label="状态" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="tNumStatus(row.status)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text type="primary" v-perm="'sys:dept:update'" @click="openEdit(row)">编辑</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑部门' : '新建部门'" width="420px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级">
          <el-select v-model="form.parentId" class="w-full" clearable placeholder="根节点/公司">
            <el-option :value="0" label="（根 / 公司）" />
            <el-option v-for="d in parentOptions" :key="d.id" :value="d.id" :label="`${d.deptName}（${tDeptType(d.deptType)}）`" />
          </el-select>
        </el-form-item>
        <el-form-item label="类型">
          <el-select v-model="form.deptType" class="w-full">
            <el-option value="COMPANY" label="公司" />
            <el-option value="DEPT" label="部门" />
            <el-option value="GROUP" label="小组" />
          </el-select>
          <div class="muted">小组挂在部门下；部长/副部长挂部门，小组长/组员挂小组</div>
        </el-form-item>
        <el-form-item label="编码" prop="deptCode"><el-input v-model="form.deptCode" /></el-form-item>
        <el-form-item label="名称" prop="deptName"><el-input v-model="form.deptName" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" class="w-full" /></el-form-item>
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
import { nextTick, onMounted, reactive, ref, computed } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/request'
import { tNumStatus, tDeptType, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const dialog = ref(false)
const form = reactive({ id: null, parentId: 0, deptCode: '', deptName: '', deptType: 'DEPT', sortNo: 0 })
const formRef = ref()
const rules = {
  deptCode: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  deptName: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

const parentOptions = computed(() => (rows.value || []).filter((d) => d.id !== form.id))

function typeTag(t) {
  if (t === 'COMPANY') return 'success'
  if (t === 'GROUP') return 'warning'
  return 'info'
}

async function load() {
  const res = await api.get('/depts')
  rows.value = res.data
}

function openCreate() {
  Object.assign(form, { id: null, parentId: 0, deptCode: '', deptName: '', deptType: 'DEPT', sortNo: 0 })
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id, parentId: row.parentId ?? 0,
    deptCode: row.deptCode, deptName: row.deptName,
    deptType: row.deptType || 'DEPT', sortNo: row.sortNo ?? 0
  })
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
    if (form.id) await api.put(`/depts/${form.id}`, { ...form })
    else await api.post('/depts', { ...form })
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

onMounted(async () => {
  await loadDicts()
  load()
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; }
.muted { color:#888; font-size:12px; margin-top:4px; }
</style>

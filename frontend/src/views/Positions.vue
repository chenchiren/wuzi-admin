<template>
  <el-card>
    <div class="bar">
      <n-button type="success" v-perm="'sys:position:create'" @click="openCreate">新建职位</n-button>
      <span class="muted">角色管权限，职位管组织职务（部长/副部长等），供审批流按职位找人</span>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="id" label="ID" min-width="70" />
      <el-table-column prop="positionCode" label="编码" min-width="120" />
      <el-table-column prop="positionName" label="名称" min-width="120" />
      <el-table-column prop="positionLevel" label="职级" min-width="80" />
      <el-table-column label="领导岗" min-width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.isLeader === 1 ? 'success' : 'info'">
            {{ row.isLeader === 1 ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="sortNo" label="排序" min-width="70" />
      <el-table-column label="状态" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="tNumStatus(row.status)" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="140" />
      <el-table-column label="操作" min-width="180">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text type="primary" v-perm="'sys:position:update'" @click="openEdit(row)">编辑</n-button>
            <n-button size="small" text type="error" v-perm="'sys:position:delete'" @click="remove(row)">删除</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑职位' : '新建职位'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="编码" prop="positionCode"><el-input v-model="form.positionCode" placeholder="如 DEPT_HEAD" /></el-form-item>
        <el-form-item label="名称" prop="positionName"><el-input v-model="form.positionName" placeholder="如 部长" /></el-form-item>
        <el-form-item label="职级">
          <el-input-number v-model="form.positionLevel" :min="0" :max="999" class="w-full" />
          <div class="muted">数值越大越高，如部长 80 &gt; 副部长 70</div>
        </el-form-item>
        <el-form-item label="领导岗">
          <el-switch v-model="form.isLeader" :active-value="1" :inactive-value="0" />
          <span class="muted ml-2">部门负责人取本部门最高职级领导岗</span>
        </el-form-item>
        <el-form-item label="排序"><el-input-number v-model="form.sortNo" :min="0" class="w-full" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
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
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { tNumStatus, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const dialog = ref(false)
const form = reactive({
  id: null, positionCode: '', positionName: '', positionLevel: 30,
  isLeader: 0, sortNo: 0, status: 1, remark: ''
})
const formRef = ref()
const rules = {
  positionCode: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  positionName: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

async function load() {
  const res = await api.get('/positions')
  rows.value = res.data || []
}

function openCreate() {
  Object.assign(form, {
    id: null, positionCode: '', positionName: '', positionLevel: 30,
    isLeader: 0, sortNo: 0, status: 1, remark: ''
  })
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    positionCode: row.positionCode,
    positionName: row.positionName,
    positionLevel: row.positionLevel ?? 0,
    isLeader: row.isLeader ?? 0,
    sortNo: row.sortNo ?? 0,
    status: row.status ?? 1,
    remark: row.remark || ''
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
    if (form.id) await api.put(`/positions/${form.id}`, { ...form })
    else await api.post('/positions', { ...form })
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除职位 ${row.positionName}？`, '提示')
  try {
    await api.delete(`/positions/${row.id}`)
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
.bar { display:flex; align-items:center; gap:8px; margin-bottom:12px; }
.muted { color:#888; font-size:12px; }
.ml-2 { margin-left:8px; }
</style>

<template>
  <el-card>
    <div class="bar">
      <el-input v-model="keyword" placeholder="编码/名称" style="width:200px" clearable @change="load" />
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'base:category:create'" @click="openCreate">新建分类</n-button>
    </div>
    <el-table :data="rows" border stripe row-key="id" default-expand-all>
      <el-table-column prop="categoryCode" label="编码" min-width="140" />
      <el-table-column prop="categoryName" label="名称" min-width="160" />
      <el-table-column label="上级分类" min-width="140">
        <template #default="{ row }">
          <span v-if="row.parentId">{{ parentLabel(row.parentId) }}</span>
          <span v-else class="text-slate-400">顶级</span>
        </template>
      </el-table-column>
      <el-table-column prop="sortNo" label="排序" min-width="80" />
      <el-table-column label="状态" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="tNumStatus(row.status)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text type="primary" v-perm="'base:category:update'" @click="openEdit(row)">编辑</n-button>
            <n-button size="small" text type="error" v-perm="'base:category:delete'" @click="remove(row)">删除</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑分类' : '新建分类'" width="420px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="上级分类">
          <el-select v-model="form.parentId" clearable class="w-full" placeholder="不选则为顶级分类">
            <el-option :value="0" label="顶级分类" />
            <el-option v-for="c in parentOptions" :key="c.id" :value="c.id" :label="c.categoryName" />
          </el-select>
        </el-form-item>
        <el-form-item label="编码" prop="categoryCode"><el-input v-model="form.categoryCode" /></el-form-item>
        <el-form-item label="名称" prop="categoryName"><el-input v-model="form.categoryName" /></el-form-item>
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
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { tNumStatus, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const keyword = ref('')
const dialog = ref(false)
const form = reactive({ id: null, parentId: 0, categoryCode: '', categoryName: '', sortNo: 0 })
const formRef = ref()
const rules = {
  categoryCode: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  categoryName: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

const parentOptions = computed(() => rows.value.filter(c => c.id !== form.id))

function parentLabel(parentId) {
  return rows.value.find(c => c.id === parentId)?.categoryName || `#${parentId}`
}

async function load() {
  const res = await api.get('/categories', { params: { keyword: keyword.value } })
  rows.value = res.data
}

function openCreate() {
  Object.assign(form, { id: null, parentId: 0, categoryCode: '', categoryName: '', sortNo: 0 })
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id, parentId: row.parentId ?? 0,
    categoryCode: row.categoryCode, categoryName: row.categoryName,
    sortNo: row.sortNo ?? 0
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
    const payload = { ...form, parentId: form.parentId == null || form.parentId === '' ? 0 : form.parentId }
    if (form.id) await api.put(`/categories/${form.id}`, payload)
    else await api.post('/categories', payload)
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除分类 ${row.categoryName}？`, '提示')
  try {
    await api.delete(`/categories/${row.id}`)
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
.bar { display:flex; gap:8px; margin-bottom:12px; }
</style>

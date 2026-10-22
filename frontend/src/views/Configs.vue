<template>
  <el-card>
    <div class="bar">
      <el-input v-model="keyword" placeholder="key/名称" style="width:200px" clearable @change="load" />
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'sys:config:save'" @click="openCreate">新建配置</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="configKey" label="Key" min-width="160" />
      <el-table-column prop="configName" label="名称" min-width="120" />
      <el-table-column prop="configValue" label="值" min-width="180" />
      <el-table-column prop="remark" label="备注" min-width="140" />
      <el-table-column label="操作" min-width="140">
        <template #default="{ row }">
          <div class="flex gap-2">
            <n-button size="small" text type="primary" v-perm="'sys:config:save'" @click="openEdit(row)">编辑</n-button>
            <n-button size="small" text type="error" v-perm="'sys:config:delete'" @click="remove(row)">删除</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑配置' : '新建配置'" width="480px">
      <el-form label-width="90px">
        <el-form-item label="Key"><el-input v-model="form.configKey" :disabled="!!form.id" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.configName" /></el-form-item>
        <el-form-item label="值"><el-input v-model="form.configValue" type="textarea" :rows="3" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <div class="flex justify-end gap-2">
          <n-button @click="dialog=false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
        </div>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'

const rows = ref([])
const keyword = ref('')
const dialog = ref(false)
const form = reactive({ id: null, configKey: '', configName: '', configValue: '', remark: '' })

async function load() {
  const res = await api.get('/configs', { params: { keyword: keyword.value || undefined } })
  rows.value = res.data || []
}
function openCreate() {
  Object.assign(form, { id: null, configKey: '', configName: '', configValue: '', remark: '' })
  dialog.value = true
}
function openEdit(row) {
  Object.assign(form, { id: row.id, configKey: row.configKey, configName: row.configName || '', configValue: row.configValue || '', remark: row.remark || '' })
  dialog.value = true
}
async function save() {
  try {
    await api.post('/configs', form)
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}
async function remove(row) {
  await ElMessageBox.confirm('确认删除配置？', '提示')
  try {
    await api.delete(`/configs/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}
onMounted(load)
</script>

<style scoped>
.bar { display: flex; gap: 8px; margin-bottom: 12px; flex-wrap: wrap; align-items: center; }
</style>

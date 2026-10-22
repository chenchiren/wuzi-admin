<template>
  <el-card>
    <div class="bar">
      <el-input v-model="keyword" placeholder="编码/名称" style="width:180px" clearable @change="load" />
      <el-select v-model="category" placeholder="分类" style="width:120px" clearable @change="load">
        <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'base:unit:create'" @click="openCreate">新建单位</n-button>
      <span class="muted">换算系数相对基准单位，如克→千克 = 0.001</span>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="id" label="ID" min-width="70" />
      <el-table-column prop="unitCode" label="编码" min-width="100" />
      <el-table-column prop="unitName" label="名称" min-width="100" />
      <el-table-column label="分类" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.category" dict-type="UNIT_CATEGORY" />
        </template>
      </el-table-column>
      <el-table-column prop="convertFactor" label="换算系数" min-width="100" />
      <el-table-column prop="baseUnitCode" label="基准单位" min-width="100" />
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
            <n-button size="small" text type="primary" v-perm="'base:unit:update'" @click="openEdit(row)">编辑</n-button>
            <n-button size="small" text type="error" v-perm="'base:unit:delete'" @click="remove(row)">删除</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑计量单位' : '新建计量单位'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="编码" prop="unitCode"><el-input v-model="form.unitCode" placeholder="如 KG / EA" /></el-form-item>
        <el-form-item label="名称" prop="unitName"><el-input v-model="form.unitName" placeholder="如 千克" /></el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.category" class="w-full">
            <el-option v-for="c in categoryOptions" :key="c.value" :label="c.label" :value="c.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="换算系数">
          <el-input-number v-model="form.convertFactor" :min="0.000001" :precision="6" class="w-full" />
          <div class="muted">相对基准单位的换算系数，必须大于 0</div>
        </el-form-item>
        <el-form-item label="基准单位">
          <el-select v-model="form.baseUnitCode" filterable allow-create class="w-full" placeholder="自身为基准时填自身编码">
            <el-option v-for="u in rows" :key="u.unitCode" :label="`${u.unitCode} ${u.unitName}`" :value="u.unitCode" />
          </el-select>
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
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { tNumStatus, loadDicts, dictOptionsOr } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const keyword = ref('')
const category = ref('')
const dialog = ref(false)
const form = reactive({
  id: null, unitCode: '', unitName: '', category: 'COUNT',
  convertFactor: 1, baseUnitCode: '', sortNo: 0, status: 1, remark: ''
})
const formRef = ref()
const rules = {
  unitCode: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  unitName: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

const categoryOptions = computed(() =>
  dictOptionsOr('UNIT_CATEGORY', ['COUNT', 'LENGTH', 'WEIGHT', 'VOLUME']))

async function load() {
  const res = await api.get('/units', {
    params: { keyword: keyword.value || undefined, category: category.value || undefined }
  })
  rows.value = res.data || []
}

function openCreate() {
  Object.assign(form, {
    id: null, unitCode: '', unitName: '', category: 'COUNT',
    convertFactor: 1, baseUnitCode: '', sortNo: 0, status: 1, remark: ''
  })
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    unitCode: row.unitCode,
    unitName: row.unitName,
    category: row.category || 'COUNT',
    convertFactor: row.convertFactor != null ? Number(row.convertFactor) : 1,
    baseUnitCode: row.baseUnitCode || '',
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
    if (form.id) await api.put(`/units/${form.id}`, { ...form })
    else await api.post('/units', { ...form })
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除单位 ${row.unitName}？`, '提示')
  try {
    await api.delete(`/units/${row.id}`)
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

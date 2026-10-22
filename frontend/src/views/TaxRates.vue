<template>
  <el-card>
    <div class="bar">
      <el-input v-model="keyword" placeholder="编码/名称" style="width:200px" clearable @change="filterLocal" />
      <n-button type="primary" @click="filterLocal">查询</n-button>
      <n-button type="success" v-perm="'base:tax-rate:create'" @click="openCreate">新建税率</n-button>
    </div>

    <el-table :data="filtered" border stripe>
      <el-table-column prop="taxCode" label="编码" min-width="110" />
      <el-table-column prop="taxName" label="名称" min-width="140" />
      <el-table-column prop="taxRate" label="税率%" min-width="90" />
      <el-table-column label="分类" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.taxCategory" dict-type="TAX_CATEGORY" />
        </template>
      </el-table-column>
      <el-table-column label="默认" min-width="70">
        <template #default="{ row }">
          <el-tag v-if="row.isDefault === 1" size="small" type="success" effect="plain">是</el-tag>
          <span v-else class="text-slate-400">—</span>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="80">
        <template #default="{ row }">
          <el-tag v-if="row.isEnabled === 1" size="small" type="success" effect="plain">{{ tNumStatus(1) }}</el-tag>
          <el-tag v-else size="small" type="info" effect="plain">{{ tNumStatus(0) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text type="primary" v-perm="'base:tax-rate:update'" @click="openEdit(row)">编辑</n-button>
            <n-button size="small" text v-perm="'base:tax-rate:toggle'" :type="row.isEnabled === 1 ? 'warning' : 'success'" @click="toggle(row)">
              {{ row.isEnabled === 1 ? tNumStatus(0) : tNumStatus(1) }}
            </n-button>
            <n-button size="small" text type="error" v-perm="'base:tax-rate:delete'" @click="remove(row)">删除</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="dialog" :title="form.id ? '编辑税率' : '新建税率'" width="460px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="编码" prop="taxCode">
          <el-input v-model="form.taxCode" placeholder="如 VAT13" />
        </el-form-item>
        <el-form-item label="名称" prop="taxName">
          <el-input v-model="form.taxName" placeholder="如 增值税13%" />
        </el-form-item>
        <el-form-item label="税率%">
          <el-input-number v-model="form.taxRate" :min="0" :max="100" :precision="2" class="w-full" />
        </el-form-item>
        <el-form-item label="分类">
          <el-select v-model="form.taxCategory" clearable class="w-full" placeholder="可选">
            <el-option value="VAT" label="增值税" />
            <el-option value="EXTRA" label="附加税" />
            <el-option value="OTHER" label="其他" />
          </el-select>
        </el-form-item>
        <el-form-item label="设为默认">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
          <span class="ml-2 text-xs text-slate-500">默认税率仅一条，保存时自动取消其他默认</span>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.isEnabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="form.remark" type="textarea" :rows="2" />
        </el-form-item>
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
import { tTaxCategory, tNumStatus, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const keyword = ref('')
const dialog = ref(false)
const form = reactive({
  id: null, taxCode: '', taxName: '', taxRate: 13,
  taxCategory: 'VAT', isDefault: 0, isEnabled: 1, remark: ''
})
const formRef = ref()
const rules = {
  taxCode: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  taxName: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

const filtered = computed(() => {
  const k = keyword.value.trim().toLowerCase()
  if (!k) return rows.value
  return rows.value.filter(r =>
    (r.taxCode || '').toLowerCase().includes(k) ||
    (r.taxName || '').toLowerCase().includes(k)
  )
})

function filterLocal() { /* computed handles filter */ }

async function load() {
  const res = await api.get('/tax-rates')
  rows.value = res.data || []
}

function openCreate() {
  Object.assign(form, {
    id: null, taxCode: '', taxName: '', taxRate: 13,
    taxCategory: 'VAT', isDefault: 0, isEnabled: 1, remark: ''
  })
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    taxCode: row.taxCode || '',
    taxName: row.taxName || '',
    taxRate: row.taxRate ?? 0,
    taxCategory: row.taxCategory || 'VAT',
    isDefault: row.isDefault ?? 0,
    isEnabled: row.isEnabled ?? 1,
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
    await api.post('/tax-rates', { ...form })
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function toggle(row) {
  try {
    await api.post(`/tax-rates/${row.id}/toggle`)
    ElMessage.success(row.isEnabled === 1 ? '已停用' : '已启用')
    load()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除税率「${row.taxName || row.taxCode}」？`, '提示')
  try {
    await api.delete(`/tax-rates/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

onMounted(async () => {
  await loadDicts()
  load()
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; align-items:center; }
</style>

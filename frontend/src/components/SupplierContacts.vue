<template>
  <div>
    <div class="mb-3 flex flex-wrap items-center gap-2">
      <n-button type="success" size="small" v-perm="'base:supplier-contact:create'" @click="openCreate">
        新建联系人
      </n-button>
      <span class="muted">同一供应商仅保留一个主联系人</span>
    </div>

    <el-table :data="rows" border stripe v-loading="loading">
      <el-table-column prop="contactName" label="姓名" min-width="100" />
      <el-table-column prop="phone" label="电话" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="150" />
      <el-table-column prop="position" label="职位" min-width="100" />
      <el-table-column label="主联系人" min-width="90">
        <template #default="{ row }">
          <el-tag v-if="row.isPrimary === 1" size="small" type="success">主</el-tag>
          <n-button
            v-else
            size="small"
            text
            type="primary"
            v-perm="'base:supplier-contact:set-primary'"
            @click="setPrimary(row)"
          >设为主</n-button>
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" />
      <el-table-column label="操作" min-width="160">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text type="primary" v-perm="'base:supplier-contact:update'" @click="openEdit(row)">编辑</n-button>
            <n-button size="small" text type="error" v-perm="'base:supplier-contact:delete'" @click="remove(row)">删除</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-empty v-if="!loading && !rows.length" description="暂无联系人" :image-size="60" />

    <el-dialog v-model="dialog" :title="form.id ? '编辑联系人' : '新建联系人'" width="480px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="姓名"><el-input v-model="form.contactName" /></el-form-item>
        <el-form-item label="电话"><el-input v-model="form.phone" /></el-form-item>
        <el-form-item label="邮箱"><el-input v-model="form.email" /></el-form-item>
        <el-form-item label="职位"><el-input v-model="form.position" placeholder="如 销售经理" /></el-form-item>
        <el-form-item label="主联系人">
          <el-switch v-model="form.isPrimary" :active-value="1" :inactive-value="0" />
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
  </div>
</template>

<script setup>
import { reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'

const props = defineProps({
  supplierId: { type: [Number, String], default: null }
})

const rows = ref([])
const loading = ref(false)
const dialog = ref(false)
const form = reactive({
  id: null, contactName: '', phone: '', email: '', position: '', isPrimary: 0, remark: ''
})

async function load() {
  if (!props.supplierId) {
    rows.value = []
    return
  }
  loading.value = true
  try {
    const res = await api.get(`/suppliers/${props.supplierId}/contacts`)
    rows.value = res.data || []
  } catch (e) {
    ElMessage.error(e.message || '加载联系人失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, {
    id: null, contactName: '', phone: '', email: '', position: '', isPrimary: 0, remark: ''
  })
  dialog.value = true
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    contactName: row.contactName || '',
    phone: row.phone || '',
    email: row.email || '',
    position: row.position || '',
    isPrimary: row.isPrimary ?? 0,
    remark: row.remark || ''
  })
  dialog.value = true
}

async function save() {
  try {
    if (!form.contactName) {
      ElMessage.warning('请填写联系人姓名')
      return
    }
    if (form.id) {
      await api.put(`/supplier-contacts/${form.id}`, { ...form })
    } else {
      await api.post('/supplier-contacts', { ...form, supplierId: props.supplierId })
    }
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function setPrimary(row) {
  try {
    await api.put(`/supplier-contacts/${row.id}`, { isPrimary: 1 })
    ElMessage.success('已设为主联系人')
    load()
  } catch (e) {
    ElMessage.error(e.message || '设置失败')
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除联系人「${row.contactName}」？`, '提示')
  try {
    await api.delete(`/supplier-contacts/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

watch(() => props.supplierId, load, { immediate: true })
</script>

<style scoped>
.muted { color:#888; font-size:12px; }
.mb-3 { margin-bottom:12px; }
</style>

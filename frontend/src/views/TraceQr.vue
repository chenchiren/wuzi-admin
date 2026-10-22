<template>
  <el-card>
    <div class="bar">
      <el-input v-model="keyword" placeholder="码/名称/批次" style="width:220px" clearable @change="load" />
      <n-button @click="load">查询</n-button>
      <n-button type="primary" v-perm="'trace:qr:generate'" @click="openCreate">生成二维码</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="qrCode" label="码" min-width="160" />
      <el-table-column label="类型" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.qrType" dict-type="QR_TYPE" />
        </template>
      </el-table-column>
      <el-table-column prop="materialName" label="物资" min-width="120" />
      <el-table-column prop="batchNo" label="批次" min-width="120" />
      <el-table-column prop="serialNo" label="序列号" min-width="120" />
      <el-table-column label="状态" min-width="100">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="QR_STATUS" />
        </template>
      </el-table-column>
      <el-table-column prop="traceToken" label="溯源Token" min-width="140" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button text type="primary" size="small" v-perm="'trace:qr:activate'" @click="activate(row)">激活</n-button>
            <n-button text size="small" @click="preview(row)">溯源</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="dialog" title="生成二维码" width="520px">
      <el-form label-width="90px">
        <el-form-item label="类型">
          <el-radio-group v-model="form.qrType">
            <el-radio value="SKU">品码</el-radio>
            <el-radio value="BATCH">批码</el-radio>
            <el-radio value="SERIAL">件码</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="物资">
          <el-select
            v-model="form.materialId"
            filterable
            placeholder="选择物资"
            class="w-full"
            :loading="materialsLoading"
            @change="onMaterialChange"
          >
            <el-option
              v-for="m in materials"
              :key="m.id"
              :value="m.id"
              :label="`${m.materialCode} ${m.materialName}`"
            />
          </el-select>
        </el-form-item>
        <el-form-item label="物资名称">
          <el-input v-model="form.materialName" disabled />
        </el-form-item>
        <el-form-item v-if="form.qrType==='BATCH'" label="批次号">
          <el-input v-model="form.batchNo" placeholder="必填，如 B20260315" />
        </el-form-item>
        <el-form-item v-if="form.qrType==='SERIAL'" label="序列号">
          <el-input v-model="form.serialNo" placeholder="必填，全局唯一" />
        </el-form-item>
        <el-alert
          v-if="hint"
          :title="hint"
          type="info"
          :closable="false"
          show-icon
          style="margin-bottom:8px"
        />
      </el-form>
      <template #footer>
        
        <div class="flex items-center justify-end gap-2">
        <n-button @click="dialog=false">取消</n-button>
        <n-button type="primary" :loading="submitting" @click="generate">生成</n-button>
        </div>
      
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" title="公开溯源（脱敏）" width="420px">
      <pre>{{ previewText }}</pre>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/request'
import { tQrStatus, tQrType, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const dialog = ref(false)
const previewVisible = ref(false)
const previewText = ref('')
const materials = ref([])
const materialsLoading = ref(false)
const submitting = ref(false)
const form = reactive({ qrType: 'SKU', materialId: null, materialName: '', batchNo: '', serialNo: '' })

const hint = computed(() => {
  if (form.qrType === 'SKU') return '同一物资只能生成一个品码；若已存在会提示重复。'
  if (form.qrType === 'BATCH') return '同一物资 + 批次号只能生成一个批码。'
  if (form.qrType === 'SERIAL') return '序列号全局唯一，重复会生成失败。'
  return ''
})

async function load() {
  const res = await api.get('/trace/qr', { params: { page: page.value, size: 10, keyword: keyword.value } })
  rows.value = res.data.records || []
  total.value = res.data.total
}

async function loadMaterials() {
  materialsLoading.value = true
  try {
    const res = await api.get('/materials', { params: { page: 1, size: 200 } })
    materials.value = res.data.records || []
    if (!form.materialId && materials.value.length) {
      form.materialId = materials.value[0].id
      onMaterialChange(form.materialId)
    }
  } catch (e) {
    ElMessage.error(e.message || '加载物资失败')
  } finally {
    materialsLoading.value = false
  }
}

function openCreate() {
  form.qrType = 'SKU'
  form.batchNo = ''
  form.serialNo = ''
  if (materials.value.length && !form.materialId) {
    form.materialId = materials.value[0].id
  }
  onMaterialChange(form.materialId)
  dialog.value = true
}

function onMaterialChange(id) {
  const m = materials.value.find(x => x.id === id)
  form.materialName = m ? m.materialName : ''
}

async function generate() {
  if (!form.materialId) {
    ElMessage.warning('请选择物资')
    return
  }
  if (form.qrType === 'BATCH' && !String(form.batchNo || '').trim()) {
    ElMessage.warning('请填写批次号')
    return
  }
  if (form.qrType === 'SERIAL' && !String(form.serialNo || '').trim()) {
    ElMessage.warning('请填写序列号')
    return
  }
  submitting.value = true
  try {
    await api.post('/trace/qr/generate', {
      qrType: form.qrType,
      materialId: form.materialId,
      materialName: form.materialName,
      batchNo: form.batchNo,
      serialNo: form.serialNo
    })
    ElMessage.success('已生成')
    dialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '生成失败')
  } finally {
    submitting.value = false
  }
}

async function activate(row) {
  try {
    await api.post(`/trace/qr/${row.id}/activate`)
    ElMessage.success('已激活')
    load()
  } catch (e) {
    ElMessage.error(e.message || '激活失败')
  }
}

async function preview(row) {
  try {
    const res = await api.get(`/trace/public/${row.traceToken}`)
    previewText.value = JSON.stringify(res.data, null, 2)
    previewVisible.value = true
  } catch (e) {
    ElMessage.error(e.message || '溯源查询失败')
  }
}

onMounted(async () => {
  await loadDicts()
  await Promise.all([load(), loadMaterials()])
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; }
</style>

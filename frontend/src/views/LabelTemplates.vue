<template>
  <el-card>
    <div class="bar">
      <n-button type="success" v-perm="'label:template:create'" @click="openCreate">新建模板</n-button>
      <span class="muted">标签尺寸 / 显示字段 / 可选自定义 HTML；打印时按模板编码生成</span>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="id" label="ID" min-width="60" />
      <el-table-column prop="templateCode" label="编码" min-width="130" />
      <el-table-column prop="templateName" label="名称" min-width="120" />
      <el-table-column label="尺寸" min-width="110">
        <template #default="{ row }">{{ row.widthMm }} × {{ row.heightMm }} mm</template>
      </el-table-column>
      <el-table-column prop="dpi" label="DPI" min-width="70" />
      <el-table-column prop="title" label="标题" min-width="100" />
      <el-table-column label="显示字段" min-width="180">
        <template #default="{ row }">
          <el-tag v-if="row.showBatch === 1" size="small" class="tag">批次</el-tag>
          <el-tag v-if="row.showSerial === 1" size="small" class="tag">序列</el-tag>
          <el-tag v-if="row.showSupplier === 1" size="small" class="tag">供应商</el-tag>
          <el-tag v-if="row.showPrice === 1" size="small" class="tag">价格</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="自定义HTML" min-width="90">
        <template #default="{ row }">
          <el-tag size="small" :type="row.htmlBody ? 'warning' : 'info'">{{ row.htmlBody ? '有' : '无' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="80">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="row.status === 1 ? '启用' : '停用'" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="220">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text type="primary" @click="preview(row)">预览</n-button>
            <n-button size="small" text type="primary" v-perm="'label:template:update'" @click="openEdit(row)">编辑</n-button>
            <n-button size="small" text type="error" v-perm="'label:template:delete'" @click="remove(row)">删除</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 编辑对话框 -->
    <el-dialog v-model="dialog" :title="form.id ? '编辑模板' : '新建模板'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="模板编码" prop="templateCode">
          <el-input v-model="form.templateCode" placeholder="如 DEFAULT_50X30" :disabled="!!form.id" />
        </el-form-item>
        <el-form-item label="模板名称" prop="templateName">
          <el-input v-model="form.templateName" placeholder="如 默认 50×30" />
        </el-form-item>
        <el-form-item label="尺寸">
          <div class="flex items-center gap-2">
            <el-input-number v-model="form.widthMm" :min="1" :max="500" :precision="1" />
            <span>×</span>
            <el-input-number v-model="form.heightMm" :min="1" :max="500" :precision="1" />
            <span class="muted">mm</span>
          </div>
        </el-form-item>
        <el-form-item label="DPI">
          <el-input-number v-model="form.dpi" :min="72" :max="600" class="w-full" />
        </el-form-item>
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="标签顶部标题" />
        </el-form-item>
        <el-form-item label="二维码模块">
          <el-input-number v-model="form.qrModuleSize" :min="2" :max="10" class="w-full" />
          <div class="muted">ZPL ^BQN 模块倍数 2–10，越大二维码越清晰</div>
        </el-form-item>
        <el-form-item label="显示字段">
          <el-checkbox v-model="form.showBatch" :true-value="1" :false-value="0">批次</el-checkbox>
          <el-checkbox v-model="form.showSerial" :true-value="1" :false-value="0">序列号</el-checkbox>
          <el-checkbox v-model="form.showSupplier" :true-value="1" :false-value="0">供应商</el-checkbox>
          <el-checkbox v-model="form.showPrice" :true-value="1" :false-value="0">价格</el-checkbox>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="form.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="自定义HTML">
          <el-input v-model="form.htmlBody" type="textarea" :rows="5" :placeholder="htmlPlaceholder" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="dialog = false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
        </div>
      </template>
    </el-dialog>

    <!-- 预览 -->
    <el-dialog v-model="previewVisible" :title="`标签预览 · ${previewRow?.templateName || ''}`" width="640px">
      <el-tabs v-model="previewTab">
        <el-tab-pane label="HTML" name="html">
          <iframe class="preview-frame" :srcdoc="previewHtml"></iframe>
        </el-tab-pane>
        <el-tab-pane label="ZPL" name="zpl">
          <pre class="zpl">{{ previewZpl }}</pre>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const dialog = ref(false)
const previewVisible = ref(false)
const previewTab = ref('html')
const previewHtml = ref('')
const previewZpl = ref('')
const previewRow = ref(null)
// 字面量占位符说明（避免被 Vue 模板插值）
const htmlPlaceholder = '可选。占位符：{{' + 'qrSvg}} {{' + 'title}} {{' + 'materialName}} {{' + 'code}} {{' + 'batch}} {{' + 'serial}} {{' + 'spec}} {{' + 'templateCode}}。留空使用内置布局。'

const form = reactive({
  id: null, templateCode: '', templateName: '', widthMm: 50, heightMm: 30,
  dpi: 203, title: '物资标签', qrModuleSize: 5,
  showBatch: 1, showSerial: 1, showSupplier: 0, showPrice: 0,
  htmlBody: '', status: 1
})
const formRef = ref()
const rules = {
  templateCode: [{ required: true, message: '请输入模板编码', trigger: 'blur' }],
  templateName: [{ required: true, message: '请输入模板名称', trigger: 'blur' }]
}

async function load() {
  const res = await api.get('/label-templates/all')
  rows.value = res.data || []
}

function openCreate() {
  Object.assign(form, {
    id: null, templateCode: '', templateName: '', widthMm: 50, heightMm: 30,
    dpi: 203, title: '物资标签', qrModuleSize: 5,
    showBatch: 1, showSerial: 1, showSupplier: 0, showPrice: 0,
    htmlBody: '', status: 1
  })
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    templateCode: row.templateCode || '',
    templateName: row.templateName || '',
    widthMm: Number(row.widthMm ?? 50),
    heightMm: Number(row.heightMm ?? 30),
    dpi: row.dpi ?? 203,
    title: row.title || '',
    qrModuleSize: row.qrModuleSize ?? 5,
    showBatch: row.showBatch ?? 1,
    showSerial: row.showSerial ?? 1,
    showSupplier: row.showSupplier ?? 0,
    showPrice: row.showPrice ?? 0,
    htmlBody: row.htmlBody || '',
    status: row.status ?? 1
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
    const payload = { ...form }
    if (form.id) await api.put(`/label-templates/${form.id}`, payload)
    else await api.post('/label-templates', payload)
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除模板「${row.templateName || row.templateCode}」？`, '提示')
  try {
    await api.delete(`/label-templates/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) {
    ElMessage.error(e.message || '删除失败')
  }
}

async function preview(row) {
  previewRow.value = row
  previewTab.value = 'html'
  previewHtml.value = ''
  previewZpl.value = ''
  previewVisible.value = true
  try {
    const token = localStorage.getItem('token')
    const headers = {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    }
    const [hRes, zRes] = await Promise.all([
      fetch(`/api/label-templates/${row.id}/preview`, {
        method: 'POST', headers, body: JSON.stringify({ type: 'html' })
      }),
      fetch(`/api/label-templates/${row.id}/preview`, {
        method: 'POST', headers, body: JSON.stringify({ type: 'zpl' })
      })
    ])
    previewHtml.value = await hRes.text()
    previewZpl.value = await zRes.text()
  } catch (e) {
    ElMessage.error(e.message || '预览失败')
  }
}

onMounted(load)
</script>

<style scoped>
.bar { display:flex; align-items:center; gap:8px; margin-bottom:12px; flex-wrap:wrap; }
.muted { color:#888; font-size:12px; }
.tag { margin-right:4px; }
.preview-frame { width:100%; height:260px; border:1px dashed #ccc; border-radius:6px; background:#fff; }
.zpl {
  background:#0f172a; color:#a7f3d0; padding:12px; border-radius:6px;
  font-family: Consolas, monospace; font-size:12px; line-height:1.5;
  white-space:pre-wrap; word-break:break-all; min-height:160px; max-height:280px; overflow:auto;
}
</style>

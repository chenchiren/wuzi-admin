<template>
  <el-card>
    <div class="bar">
      <el-input v-model="keyword" placeholder="码/名称/批次" style="width:220px" clearable @change="loadQrs" />
      <n-button @click="loadQrs">查询</n-button>
      <el-select v-model="selectedId" placeholder="选择码" style="width:280px" filterable @change="onSelect">
        <el-option v-for="q in qrs" :key="q.id" :value="q.id"
          :label="`${q.qrCode} | ${q.materialName || '-'} | ${q.batchNo || '-'}`" />
      </el-select>
      <n-button type="primary" :disabled="!selectedId" @click="loadLabel">预览标签</n-button>
    </div>

    <el-table :data="qrs" border stripe height="280" @row-click="onRow" highlight-current-row>
      <el-table-column prop="qrCode" label="码值" min-width="160" />
      <el-table-column prop="materialName" label="名称" min-width="140" />
      <el-table-column prop="batchNo" label="批次" min-width="120" />
      <el-table-column label="类型" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.qrType" dict-type="QR_TYPE" />
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="100">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="QR_STATUS" />
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="loadQrs" />

    <el-tabs v-model="tab" style="margin-top:16px">
      <el-tab-pane label="ZPL 预览" name="zpl">
        <pre class="zpl">{{ zpl || '请选择码并点击预览' }}</pre>
        <n-button size="small" @click="copyZpl" :disabled="!zpl">复制 ZPL</n-button>
      </el-tab-pane>
      <el-tab-pane label="HTML 标签" name="html">
        <iframe v-if="html" class="preview" :srcdoc="html"></iframe>
        <el-empty v-else description="请选择码并点击预览" />
        <div class="mt-2">
          <n-button size="small" type="primary" @click="openHtml" :disabled="!html">新窗口打开/打印</n-button>
        </div>
      </el-tab-pane>
      <el-tab-pane label="模板" name="tpl">
        <el-descriptions v-if="template" :column="2" border>
          <el-descriptions-item label="模板编码">{{ template.templateCode }}</el-descriptions-item>
          <el-descriptions-item label="标题">{{ template.title }}</el-descriptions-item>
          <el-descriptions-item label="尺寸">{{ template.widthMm }} × {{ template.heightMm }} mm</el-descriptions-item>
          <el-descriptions-item label="DPI">{{ template.dpi }}</el-descriptions-item>
        </el-descriptions>
      </el-tab-pane>
    </el-tabs>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/request'
import { tQrStatus, tQrType, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const keyword = ref('')
const qrs = ref([])
const total = ref(0)
const page = ref(1)
const selectedId = ref(null)
const tab = ref('zpl')
const zpl = ref('')
const html = ref('')
const template = ref(null)

async function loadQrs() {
  const res = await api.get('/trace/qr', {
    params: { page: page.value, size: 10, keyword: keyword.value }
  })
  qrs.value = res.data.records
  total.value = res.data.total
}

function onRow(row) {
  selectedId.value = row.id
  loadLabel()
}

function onSelect() {
  zpl.value = ''
  html.value = ''
}

async function loadLabel() {
  if (!selectedId.value) return
  try {
    const token = localStorage.getItem('token')
    const headers = token ? { Authorization: `Bearer ${token}` } : {}
    const [zRes, hRes, tRes] = await Promise.all([
      fetch(`/api/labels/zpl/${selectedId.value}`, { headers }),
      fetch(`/api/labels/html/${selectedId.value}`, { headers }),
      api.get('/labels/template')
    ])
    zpl.value = await zRes.text()
    html.value = await hRes.text()
    template.value = tRes.data
    ElMessage.success('标签已生成')
  } catch (e) {
    ElMessage.error(e.message || '标签生成失败')
  }
}

function copyZpl() {
  navigator.clipboard?.writeText(zpl.value)
  ElMessage.success('已复制')
}

function openHtml() {
  const w = window.open('', '_blank')
  if (!w) return
  w.document.write(html.value)
  w.document.close()
}

onMounted(async () => {
  await loadDicts()
  await loadQrs()
  try {
    const t = await api.get('/labels/template')
    template.value = t.data
  } catch { /* ignore */ }
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; align-items:center; flex-wrap:wrap; }
.zpl {
  background:#0f172a; color:#a7f3d0; padding:12px; border-radius:6px;
  font-family: Consolas, monospace; font-size:12px; line-height:1.5;
  white-space:pre-wrap; word-break:break-all; min-height:160px; max-height:320px; overflow:auto;
}
.preview {
  width:100%; height:280px; border:1px dashed #ccc; border-radius:6px; background:#fff;
}
</style>

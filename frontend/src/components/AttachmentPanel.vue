<template>
  <div class="att-panel">
    <div class="att-toolbar">
      <el-upload :show-file-list="false" :auto-upload="false" accept=".jpg,.jpeg,.png,.gif,.pdf,.doc,.docx,.xls,.xlsx,.ppt,.pptx,.txt,.csv,.zip" @change="onFile">
        <n-button size="small" type="primary" :loading="uploading">上传附件</n-button>
      </el-upload>
      <n-button size="small" quaternary @click="load">刷新</n-button>
      <span class="att-hint">支持图片 / PDF / Office / 文本 / zip，≤20MB</span>
    </div>
    <el-table :data="rows" v-loading="loading" size="small" border max-height="300" empty-text="暂无附件">
      <el-table-column prop="originName" label="文件名" min-width="180" show-overflow-tooltip />
      <el-table-column label="大小" width="90">
        <template #default="{ row }">{{ sizeText(row.fileSize) }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="上传时间" min-width="140" />
      <el-table-column label="操作" width="130">
        <template #default="{ row }">
          <n-button size="small" text type="primary" @click="download(row)">下载</n-button>
          <n-button size="small" text type="error" v-perm="'sys:attachment:delete'" @click="remove(row)">删除</n-button>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'

const props = defineProps({
  bizType: { type: String, required: true },
  bizId: { type: [Number, String], default: null }
})

const rows = ref([])
const loading = ref(false)
const uploading = ref(false)

const sizeText = n => {
  if (n == null) return '—'
  if (n < 1024) return n + ' B'
  if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB'
  return (n / 1024 / 1024).toFixed(2) + ' MB'
}

async function load() {
  if (!props.bizType) return
  loading.value = true
  try {
    const res = await api.get('/attachments', { params: { bizType: props.bizType, bizId: props.bizId ?? undefined } })
    rows.value = res.data || []
  } catch (e) { ElMessage.error(e.message || '加载失败') } finally { loading.value = false }
}

async function onFile(file) {
  if (!file?.raw) return
  const fd = new FormData()
  fd.append('file', file.raw)
  fd.append('bizType', props.bizType)
  if (props.bizId != null) fd.append('bizId', props.bizId)
  uploading.value = true
  try {
    await api.post('/attachments', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    ElMessage.success('已上传')
    load()
  } catch (e) { ElMessage.error(e.message || '上传失败') } finally { uploading.value = false }
}

function download(row) {
  const token = localStorage.getItem('token') || ''
  // 附件下载是文件流，用带 token 的直连请求触发浏览器下载
  fetch(`/api/attachments/${row.id}/download`, { headers: { Authorization: `Bearer ${token}` } })
    .then(async (r) => {
      if (!r.ok) throw new Error('下载失败')
      const blob = await r.blob()
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = row.originName || row.fileName || 'attachment'
      a.click()
      URL.revokeObjectURL(url)
    })
    .catch((e) => ElMessage.error(e.message || '下载失败'))
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确认删除附件「${row.originName}」？`, '提示')
  } catch { return }
  try {
    await api.delete(`/attachments/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

onMounted(load)
watch(() => [props.bizType, props.bizId], load)
</script>

<style scoped>
.att-toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
.att-hint { font-size: 12px; color: #909399; }
</style>

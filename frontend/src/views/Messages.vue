<template>
  <el-card v-loading="loading">
    <div class="bar">
      <el-select v-model="isRead" placeholder="已读状态" clearable style="width:140px" @change="load">
        <el-option :value="false" :label="tMsgRead(0)" /><el-option :value="true" :label="tMsgRead(1)" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="warning" v-perm="'msg:read-all'" @click="readAll">全部已读</n-button>
      <el-badge :value="unread" :hidden="!unread" class="ml-2">
        <span class="muted">未读消息</span>
      </el-badge>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="id" label="ID" min-width="70" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column prop="content" label="内容" min-width="240" />
      <el-table-column label="业务类型" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.bizType" dict-type="BIZ_TYPE" />
        </template>
      </el-table-column>
      <el-table-column prop="bizId" label="业务ID" min-width="90" />
      <el-table-column prop="isRead" label="状态" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.isRead ? 1 : 0" dict-type="MSG_READ" />
        </template>
      </el-table-column>
      <el-table-column prop="createdAt" label="时间" min-width="160" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <n-button v-if="!row.isRead" size="small" text type="primary" @click="markRead(row)">标记已读</n-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/request'
import { tMsgRead, tBiz, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const isRead = ref(null)
const unread = ref(0)
const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const params = { page: page.value, size: 10 }
    if (isRead.value !== null && isRead.value !== '') params.isRead = isRead.value
    const res = await api.get('/messages', { params })
    rows.value = res.data.records
    total.value = res.data.total
    const u = await api.get('/messages/unread-count')
    unread.value = u.data.count || 0
  } catch (e) { ElMessage.error(e.message || '加载失败') } finally { loading.value = false }
}

async function markRead(row) {
  try {
    await api.post(`/messages/${row.id}/read`)
    load()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

async function readAll() {
  try {
    await api.post('/messages/read-all')
    ElMessage.success('已全部标记已读')
    load()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

onMounted(async () => { await loadDicts(); load() })
</script>

<style scoped>
.muted { color:#888; font-size:13px; }
</style>

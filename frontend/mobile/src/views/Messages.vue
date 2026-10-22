<template>
  <div class="page">
    <NavBar title="消息" />
    <div class="card">
      <div class="row">
        <b>未读 {{ unread }}</b>
        <button class="btn plain" @click="readAll">全部已读</button>
      </div>
    </div>
    <div class="card">
      <Toast :message="tip" />
      <div v-if="!rows.length" class="empty">暂无消息</div>
      <div v-for="m in rows" :key="m.id" class="list-item" @click="markRead(m)">
        <div class="row">
          <b>{{ m.title }}</b>
          <span class="tag" :class="m.isRead ? '' : 'warn'">{{ m.isRead ? '已读' : '未读' }}</span>
        </div>
        <div class="muted">{{ m.content }}</div>
        <div class="muted">{{ m.createdAt }} · {{ m.bizType ? tBiz(m.bizType) : '—' }}</div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { loadDicts, tBiz } from '../utils/dict'
import NavBar from '../components/NavBar.vue'
import Toast from '../components/Toast.vue'
import { request } from '../api'
import { showToast } from '../components/toastBus'

const tip = ref('')

const rows = ref([])
const unread = ref(0)

async function load() {
  try {
    const res = await request('/api/messages?page=1&size=20')
    rows.value = res.data?.records || []
    const u = await request('/api/messages/unread-count')
    unread.value = u.data?.count || 0
  } catch (e) {
    showToast(e.message || '加载失败')
  }
}

async function markRead(m) {
  if (m.isRead) return
  try {
    await request(`/api/messages/${m.id}/read`, { method: 'POST', body: {} })
    load()
  } catch (e) {
    showToast(e.message || '操作失败')
  }
}

async function readAll() {
  try {
    await request('/api/messages/read-all', { method: 'POST', body: {} })
    showToast('已全部已读')
    load()
  } catch (e) {
    showToast(e.message || '操作失败')
  }
}

onMounted(async () => {
  await loadDicts()
  load()
})
</script>

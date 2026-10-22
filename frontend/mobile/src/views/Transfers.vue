<template>
  <div class="page">
    <NavBar title="调拨" />
    <div class="card">
      <h3>调拨单</h3>
      <button class="btn block" @click="load">刷新</button>
    </div>
    <div class="card">
      <div v-if="!rows.length" class="empty">暂无调拨单</div>
      <div v-for="r in rows" :key="r.id" class="list-item">
        <div class="row">
          <b>{{ r.transferNo || r.id }}</b>
          <span class="tag">{{ tTransferStatus(r.status) }}</span>
        </div>
        <div class="muted">从 #{{ r.fromWarehouseId }} → 到 #{{ r.toWarehouseId }}</div>
        <div class="btn-row">
          <button v-if="r.status==='DRAFT' || r.status==='REJECTED'" class="btn" @click="submit(r)">提交</button>
          <button v-if="r.status==='PENDING_APPROVAL' || r.status==='APPROVED' || r.status==='SUBMITTED'" class="btn" @click="transferOut(r)">调出</button>
          <button v-if="['OUT','IN_TRANSIT','TRANSFERRED'].includes(r.status)" class="btn" @click="transferIn(r)">调入</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import NavBar from '../components/NavBar.vue'
import { request } from '../api'
import { loadDicts, tTransferStatus } from '../utils/dict'
import { showToast } from '../components/toastBus'

const rows = ref([])

async function load() {
  try {
    const res = await request('/api/transfer/orders?page=1&size=20')
    rows.value = res.data?.records || []
  } catch (e) {
    showToast(e.message || '加载失败')
  }
}

async function submit(r) {
  try {
    await request(`/api/transfer/orders/${r.id}/submit`, { method: 'POST', body: {} })
    showToast('已提交')
    load()
  } catch (e) {
    showToast(e.message || '提交失败')
  }
}

async function transferOut(r) {
  try {
    await request(`/api/transfer/orders/${r.id}/out`, { method: 'POST', body: {} })
    showToast('已调出')
    load()
  } catch (e) {
    showToast(e.message || '调出失败')
  }
}

async function transferIn(r) {
  try {
    await request(`/api/transfer/orders/${r.id}/in`, { method: 'POST', body: {} })
    showToast('已调入')
    load()
  } catch (e) {
    showToast(e.message || '调入失败')
  }
}

onMounted(async () => {
  await loadDicts()
  load()
})
</script>

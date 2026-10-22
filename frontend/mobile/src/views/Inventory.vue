<template>
  <div class="page">
    <NavBar title="盘点" />
    <div class="card">
      <h3>盘点任务</h3>
      <div class="field">
        <label>状态</label>
        <select v-model="status" @change="load">
          <option value="">全部</option>
          <option v-for="o in statusOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
      </div>
      <button class="btn block" @click="load">刷新</button>
    </div>
    <div class="card">
      <div v-if="!rows.length" class="empty">暂无盘点单</div>
      <div v-for="r in rows" :key="r.id" class="list-item">
        <div class="row">
          <b>{{ r.checkNo || r.id }}</b>
          <span class="tag">{{ tInventoryStatus(r.status) }}</span>
        </div>
        <div class="muted">仓库 #{{ r.warehouseId }} · {{ r.createdAt || r.checkTime }}</div>
        <div class="btn-row">
          <button v-if="r.status==='DRAFT'" class="btn" @click="submit(r)">提交</button>
          <button v-if="r.status==='APPROVED'" class="btn" @click="confirmCheck(r)">确认调账</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import NavBar from '../components/NavBar.vue'
import { request } from '../api'
import { loadDicts, tInventoryStatus, dictOptions } from '../utils/dict'
import { showToast } from '../components/toastBus'

const rows = ref([])
const status = ref('')
const statusOptions = computed(() => {
  const opts = dictOptions('INVENTORY_STATUS')
  return opts.length ? opts : [
    { value: 'DRAFT', label: '草稿' },
    { value: 'PENDING_APPROVAL', label: '待审批' },
    { value: 'APPROVED', label: '已通过' },
    { value: 'CONFIRMED', label: '已确认' }
  ]
})

async function load() {
  try {
    const q = new URLSearchParams({ page: 1, size: 20 })
    if (status.value) q.set('status', status.value)
    const res = await request(`/api/inventory/checks?${q}`)
    rows.value = res.data?.records || []
  } catch (e) {
    showToast(e.message || '加载失败')
  }
}

async function submit(r) {
  try {
    await request(`/api/inventory/checks/${r.id}/submit`, { method: 'POST', body: {} })
    showToast('已提交')
    load()
  } catch (e) {
    showToast(e.message || '提交失败')
  }
}

async function confirmCheck(r) {
  try {
    await request(`/api/inventory/checks/${r.id}/confirm`, { method: 'POST', body: {} })
    showToast('已确认')
    load()
  } catch (e) {
    showToast(e.message || '确认失败')
  }
}

onMounted(async () => {
  await loadDicts()
  load()
})
</script>

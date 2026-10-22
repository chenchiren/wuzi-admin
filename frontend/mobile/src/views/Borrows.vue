<template>
  <div class="page">
    <NavBar title="借还" />
    <div class="card">
      <h3>借用单</h3>
      <div class="btn-row">
        <button class="btn plain" @click="loadOverdue">逾期</button>
        <button class="btn" @click="load">全部</button>
      </div>
    </div>
    <div class="card">
      <div v-if="!rows.length" class="empty">暂无借用</div>
      <div v-for="r in rows" :key="r.id" class="list-item">
        <div class="row">
          <b>{{ r.borrowNo || r.id }}</b>
          <span class="tag" :class="r.status==='LENT' ? 'warn' : ''">{{ tBorrowStatus(r.status) }}</span>
        </div>
        <div class="muted">{{ r.projectName || '' }} · 预计归还 {{ r.expectReturnDate || '—' }}</div>
        <div class="btn-row">
          <button v-if="r.status==='DRAFT' || r.status==='REJECTED'" class="btn" @click="submit(r)">提交</button>
          <button v-if="r.status==='APPROVED'" class="btn" @click="openAction(r, 'lend')">借出</button>
          <button v-if="['LENT','PART_RETURNED','OVERDUE'].includes(r.status)" class="btn" @click="openAction(r, 'return')">归还</button>
        </div>
      </div>
    </div>

    <div class="card">
      <h3>{{ actionType === 'lend' ? '借出' : '归还' }}仓库</h3>
      <div class="field">
        <label>仓库 ID（主仓 1 / 设备仓 2 / 现场仓 3）</label>
        <input v-model.number="warehouseId" type="number" placeholder="如 1" />
      </div>
      <div class="field">
        <label>备注（可选）</label>
        <input v-model.trim="actionRemark" placeholder="设备状况 / 归还说明" />
      </div>
      <button class="btn block" :disabled="!activeRow || busy" @click="submitAction">
        确认{{ actionType === 'lend' ? '借出' : '归还' }}
      </button>
      <p v-if="activeRow" class="muted" style="margin-top: 8px">
        当前单据：{{ activeRow.borrowNo || activeRow.id }}
      </p>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import NavBar from '../components/NavBar.vue'
import { request } from '../api'
import { loadDicts, tBorrowStatus } from '../utils/dict'
import { showToast } from '../components/toastBus'

const rows = ref([])
const activeRow = ref(null)
const actionType = ref('return')
const warehouseId = ref(1)
const actionRemark = ref('')
const busy = ref(false)

async function load() {
  try {
    const res = await request('/api/borrows?page=1&size=20')
    rows.value = res.data?.records || []
  } catch (e) {
    showToast(e.message || '加载失败')
  }
}

async function loadOverdue() {
  try {
    const res = await request('/api/borrows/overdue?page=1&size=20')
    rows.value = res.data?.records || res.data || []
  } catch (e) {
    showToast(e.message || '加载失败')
  }
}

async function submit(r) {
  try {
    await request(`/api/borrows/${r.id}/submit`, { method: 'POST', body: {} })
    showToast('已提交')
    load()
  } catch (e) {
    showToast(e.message || '提交失败')
  }
}

function openAction(r, type) {
  activeRow.value = r
  actionType.value = type
  showToast(`已选择 ${r.borrowNo || r.id}，请确认仓库后提交`)
}

async function submitAction() {
  if (!activeRow.value) {
    showToast('请先点选「借出」或「归还」')
    return
  }
  if (!warehouseId.value) {
    showToast('请填写仓库 ID')
    return
  }
  busy.value = true
  try {
    const path = actionType.value === 'lend' ? 'lend' : 'return'
    const body = { warehouseId: Number(warehouseId.value) }
    if (actionRemark.value) body.remark = actionRemark.value
    await request(`/api/borrows/${activeRow.value.id}/${path}`, { method: 'POST', body })
    showToast(actionType.value === 'lend' ? '已借出' : '已归还')
    activeRow.value = null
    actionRemark.value = ''
    load()
  } catch (e) {
    showToast(e.message || (actionType.value === 'lend' ? '借出失败' : '归还失败'))
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  await loadDicts()
  load()
})
</script>

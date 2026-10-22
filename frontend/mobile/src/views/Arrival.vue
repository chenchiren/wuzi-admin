<template>
  <div class="page">
    <NavBar title="到货验收" />

    <div class="card">
      <h3>到货单</h3>
      <div class="field">
        <label>状态筛选</label>
        <select v-model="status">
          <option value="">全部</option>
          <option value="DRAFT">草稿/待验收</option>
          <option value="COMPLETED">已完成</option>
        </select>
      </div>
      <div v-if="!orders.length" class="empty">暂无到货单</div>
      <div v-for="o in orders" :key="o.id" class="list-item" @click="open(o.id)">
        <div class="row">
          <b>#{{ o.id }} {{ o.orderNo || o.arrivalNo || '' }}</b>
          <span class="tag" :class="o.status === 'DRAFT' ? 'warn' : 'ok'">{{ tArrivalStatus(o.status) }}</span>
        </div>
        <div class="muted">订单 #{{ o.orderId }} · {{ fmt(o.arrivalTime || o.createdAt) }}</div>
      </div>
    </div>

    <template v-if="detail">
      <div class="card">
        <div class="row">
          <b>到货 #{{ detail.arrival.id }}</b>
          <span class="tag warn">{{ tArrivalStatus(detail.arrival.status) }}</span>
        </div>
        <div class="muted">PO #{{ detail.arrival.orderId }} · {{ detail.arrival.transportInfo || '' }}</div>
      </div>

      <div class="card">
        <h3>明细实收</h3>
        <div v-for="it in detail.items" :key="it.id" class="list-item">
          <div class="row">
            <span>物料 #{{ it.materialId || it.purchaseOrderItemId }}</span>
            <span class="muted">应到 {{ it.planQty ?? it.shouldQty ?? '—' }}</span>
          </div>
          <div class="qty-box" style="margin-top: 8px">
            <button @click="bump(it, -1)">−</button>
            <input v-model.number="it.arriveQty" type="number" step="0.001" />
            <button @click="bump(it, 1)">+</button>
          </div>
          <div v-if="it.taxInclusiveAmount != null && showPrice" class="muted" style="margin-top: 4px">
            含税 {{ it.taxInclusiveAmount }}
          </div>
        </div>
        <div v-if="!detail.items?.length" class="empty">无明细</div>
        <div class="btn-row">
          <button class="btn ghost" @click="saveDraft">保存草稿</button>
          <button class="btn plain" @click="fakeScan">扫货</button>
        </div>
      </div>

      <div class="fixed-action">
        <button class="btn block" :disabled="busy || detail.arrival.status !== 'DRAFT'" @click="doSubmit">
          提交验收
        </button>
      </div>
    </template>

    <TabBar />
    <Toast :message="toast" />
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { api, getUser } from '../api'
import { loadDicts, tArrivalStatus } from '../utils/dict'
import { enqueueDraft } from '../offline'
import NavBar from '../components/NavBar.vue'
import TabBar from '../components/TabBar.vue'
import Toast from '../components/Toast.vue'

const status = ref('DRAFT')
const orders = ref([])
const detail = ref(null)
const busy = ref(false)
const toast = ref('')
const user = getUser() || {}
const showPrice = ref((user.roles || []).some((r) => ['ADMIN', 'PURCHASER', 'FINANCE', 'WAREHOUSE'].includes(r)))

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : ''
}

function bump(it, d) {
  const n = Number(it.arriveQty || 0) + d
  it.arriveQty = Math.max(0, Number(n.toFixed(3)))
}

async function load() {
  try {
    const resp = await api.arrivalList({ page: 1, size: 20, status: status.value })
    orders.value = resp.data?.records || []
  } catch (e) {
    toast.value = e.message
  }
}

async function open(id) {
  try {
    const resp = await api.arrivalDetail(id)
    detail.value = resp.data
  } catch (e) {
    toast.value = e.message
  }
}

function saveDraft() {
  if (!detail.value) return
  for (const it of detail.value.items) {
    enqueueDraft({
      checkId: detail.value.arrival.id,
      itemId: it.id,
      materialId: it.materialId,
      bookQty: it.planQty ?? it.shouldQty ?? null,
      actualQty: it.arriveQty ?? 0,
      remark: 'arrival-draft'
    })
  }
  toast.value = '验收数量草稿已暂存（离线队列）'
}

function fakeScan() {
  toast.value = '扫码枪可直接输码；相机扫码请用底部「扫码」页'
}

async function doSubmit() {
  if (!detail.value) return
  if (!window.confirm('提交验收？')) return
  busy.value = true
  try {
    await api.arrivalSubmit(detail.value.arrival.id)
    toast.value = '已提交'
    detail.value = null
    load()
  } catch (e) {
    toast.value = e.message
  } finally {
    busy.value = false
  }
}

watch(status, load)
onMounted(async () => {
  await loadDicts()
  load()
})
</script>

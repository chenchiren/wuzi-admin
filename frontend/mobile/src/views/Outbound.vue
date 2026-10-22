<template>
  <div class="page">
    <NavBar title="扫码出库" />

    <div class="card">
      <h3>出库单（草稿）</h3>
      <div v-if="!orders.length" class="empty">
        暂无草稿出库单
        <div style="margin-top: 8px">
          <button class="btn plain" @click="loadOrders">刷新</button>
        </div>
      </div>
      <div v-for="o in orders" :key="o.id" class="list-item" @click="openOrder(o.id)">
        <div class="row">
          <b>{{ o.outboundNo }}</b>
          <span class="tag warn">{{ tOutboundStatus(o.status) }}</span>
        </div>
        <div class="muted">仓库 #{{ o.warehouseId }} · {{ fmt(o.outboundTime || o.createdAt) }}</div>
      </div>
      <div class="field" style="margin-top: 10px">
        <label>或直接输入出库单 ID</label>
        <div class="qty-box">
          <input v-model.number="orderId" type="number" placeholder="ID" />
          <button class="btn" @click="openOrder(orderId)">打开</button>
        </div>
      </div>
    </div>

    <template v-if="detail">
      <div class="card">
        <div class="row">
          <b>{{ detail.order.outboundNo }}</b>
          <span class="tag" :class="detail.order.status === 'DRAFT' ? 'warn' : 'ok'">{{ tOutboundStatus(detail.order.status) }}</span>
        </div>
        <div class="muted">领用人/部门 · 仓库 #{{ detail.order.warehouseId }}</div>
      </div>

      <div class="card">
        <h3>明细</h3>
        <div v-for="it in detail.items" :key="it.id" class="list-item">
          <div class="row">
            <span>物资 #{{ it.materialId }}</span>
            <span>可发 {{ it.qty ?? '—' }} / 已扫 {{ it.actualQty || 0 }}</span>
          </div>
          <div class="muted">批次 {{ it.batchNo || '—' }}</div>
        </div>
        <div v-if="!detail.items?.length" class="empty">无明细</div>
        <p v-if="availableQty != null" class="muted">可用库存：{{ availableQty }}</p>
      </div>

      <div class="card">
        <h3>扫码核验</h3>
        <div class="field">
          <label>码 / 批次</label>
          <input v-model.trim="scanCode" placeholder="扫码或输入" @keyup.enter="doScan" />
        </div>
        <div class="field">
          <label>数量</label>
          <div class="qty-box">
            <button @click="step(-1)">−</button>
            <input v-model.number="qty" type="number" step="0.001" />
            <button @click="step(1)">+</button>
          </div>
        </div>
        <div class="btn-row">
          <button class="btn" :disabled="!scanCode || busy" @click="doScan">扫入</button>
          <button class="btn plain" @click="$router.push({ path: '/scan', query: { mode: 'OUTBOUND' } })">全屏扫</button>
        </div>
        <p v-if="hint" class="muted" style="color: var(--danger)">{{ hint }}</p>
      </div>

      <div class="fixed-action">
        <button class="btn block success" :disabled="busy || detail.order.status !== 'DRAFT'" @click="doConfirm">
          确认出库（在线事务）
        </button>
      </div>
    </template>

    <TabBar />
    <Toast :message="toast" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'
import { loadDicts, tOutboundStatus } from '../utils/dict'
import { enqueueAction, enqueueScan } from '../offline'
import NavBar from '../components/NavBar.vue'
import TabBar from '../components/TabBar.vue'
import Toast from '../components/Toast.vue'

const orders = ref([])
const orderId = ref(null)
const detail = ref(null)
const scanCode = ref('')
const qty = ref(1)
const busy = ref(false)
const hint = ref('')
const availableQty = ref(null)
const toast = ref('')

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : ''
}

function step(d) {
  const n = Number(qty.value || 0) + d
  qty.value = Math.max(0, Number(n.toFixed(3)))
}

async function loadOrders() {
  try {
    const resp = await api.outboundList({ page: 1, size: 20, status: 'DRAFT' })
    orders.value = resp.data?.records || []
  } catch (e) {
    toast.value = e.message
  }
}

async function openOrder(id) {
  if (!id) return
  try {
    const resp = await api.outboundDetail(id)
    detail.value = resp.data
  } catch (e) {
    toast.value = e.message
  }
}

async function doScan() {
  if (!detail.value || !scanCode.value) return
  busy.value = true
  hint.value = ''
  try {
    const resp = await api.outboundScan(detail.value.order.id, {
      code: scanCode.value,
      qty: qty.value
    })
    if (resp.data.matched) {
      toast.value = `+${resp.data.addQty}`
      detail.value.items = resp.data.items
      availableQty.value = resp.data.availableQty
    } else {
      hint.value = resp.data.hint || resp.data.failReason || '未匹配'
    }
  } catch (e) {
    if (e.offline) {
      enqueueScan({ code: scanCode.value, scanType: 'OUTBOUND', bizType: 'OUTBOUND', bizId: detail.value.order.id, result: 'FAIL', failReason: 'offline' })
      enqueueAction({
        kind: 'OUTBOUND_SCAN',
        method: 'POST',
        path: `/api/m/outbounds/${detail.value.order.id}/scan`,
        body: { code: scanCode.value, qty: qty.value },
        label: `出库扫 ${scanCode.value}`
      })
      toast.value = '已暂存，网络恢复后自动重试'
    } else {
      hint.value = e.message
    }
  } finally {
    busy.value = false
  }
}

async function doConfirm() {
  if (!detail.value) return
  if (!window.confirm('确认出库？将扣减库存，需在线。')) return
  busy.value = true
  try {
    await api.outboundConfirm(detail.value.order.id)
    toast.value = '出库完成'
    detail.value = null
    loadOrders()
  } catch (e) {
    toast.value = e.message || '确认失败'
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  await loadDicts()
  loadOrders()
})
</script>

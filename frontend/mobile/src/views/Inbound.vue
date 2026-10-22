<template>
  <div class="page">
    <NavBar title="按单收货" />

    <div class="card">
      <h3>待收货单据</h3>
      <div v-if="!orders.length" class="empty">
        暂无草稿入库单
        <div style="margin-top: 8px">
          <button class="btn plain" @click="loadOrders">刷新</button>
        </div>
      </div>
      <div v-for="o in orders" :key="o.id" class="list-item" @click="openOrder(o.id)">
        <div class="row">
          <b>{{ o.inboundNo }}</b>
          <span class="tag warn">{{ tInboundStatus(o.status) }}</span>
        </div>
        <div class="muted">
          {{ o.sourceNo ? `采购单 ${o.sourceNo}` : `仓库 #${o.warehouseId}` }}
          · {{ fmt(o.inboundTime || o.createdAt) }}
        </div>
      </div>
      <div class="field" style="margin-top: 10px">
        <label>或直接输入入库单 ID</label>
        <div class="qty-box">
          <input v-model.number="orderId" type="number" placeholder="ID" />
          <button class="btn" @click="openOrder(orderId)">打开</button>
        </div>
      </div>
    </div>

    <template v-if="detail">
      <div class="card">
        <div class="row">
          <b>{{ detail.order.inboundNo }}</b>
          <span class="tag" :class="detail.order.status === 'DRAFT' ? 'warn' : 'ok'">{{ tInboundStatus(detail.order.status) }}</span>
        </div>
        <div class="muted">
          {{ detail.order.sourceNo ? `采购单 ${detail.order.sourceNo}` : '' }}
          · 仓库 #{{ detail.order.warehouseId }}
          · 进度 {{ doneCount }}/{{ detail.items?.length || 0 }} 行
        </div>
      </div>

      <div class="card">
        <h3>明细（改本次收数量）</h3>
        <div v-for="it in detail.items" :key="it.id" class="list-item">
          <div class="row">
            <span>物资 #{{ it.materialId }}</span>
            <span class="muted">计划 {{ it.planQty ?? '—' }}</span>
          </div>
          <div class="row" style="margin-top: 6px">
            <span class="muted">批次 {{ it.batchNo || '—' }}</span>
            <span class="muted">库位 #{{ it.locationId || '—' }}</span>
          </div>
          <div class="qty-box" style="margin-top: 8px">
            <button @click="bump(it, -1)">−</button>
            <input
              :value="it.inQty ?? 0"
              type="number"
              step="0.001"
              @change="setQty(it, $event.target.value)"
            />
            <button @click="bump(it, 1)">+</button>
            <button class="btn plain" style="margin-left: 6px" @click="fillRemain(it)">填满</button>
          </div>
        </div>
        <div v-if="!detail.items?.length" class="empty">无明细</div>
      </div>

      <div class="card">
        <h3>扫码定位行</h3>
        <p class="muted">扫批码/箱码只定位明细并带出数量，不会按件 +1。</p>
        <div class="field">
          <label>码 / 批次</label>
          <input v-model.trim="scanCode" placeholder="扫码或输入" @keyup.enter="doScan('SET')" />
        </div>
        <div class="field">
          <label>箱数/包装（可选，用于多箱累加）</label>
          <div class="qty-box">
            <button @click="step(-1)">−</button>
            <input v-model.number="qty" type="number" step="0.001" />
            <button @click="step(1)">+</button>
          </div>
        </div>
        <div class="btn-row">
          <button class="btn" :disabled="!scanCode || busy" @click="doScan('SET')">定位并填数量</button>
          <button class="btn plain" :disabled="!scanCode || busy" @click="doScan('ADD')">按箱累加</button>
          <button class="btn ghost" @click="$router.push({ path: '/scan', query: { mode: 'INBOUND' } })">全屏扫</button>
        </div>
        <p v-if="hint" class="muted" style="color: var(--warning)">{{ hint }}</p>
      </div>

      <div class="fixed-action">
        <button class="btn block success" :disabled="busy || detail.order.status !== 'DRAFT'" @click="doConfirm">
          完成入库（整单确认）
        </button>
      </div>
    </template>

    <TabBar />
    <Toast :message="toast" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import { loadDicts, tInboundStatus } from '../utils/dict'
import { enqueueAction, enqueueScan } from '../offline'
import NavBar from '../components/NavBar.vue'
import TabBar from '../components/TabBar.vue'
import Toast from '../components/Toast.vue'

const route = useRoute()
const orders = ref([])
const orderId = ref(null)
const detail = ref(null)
const scanCode = ref('')
const qty = ref(1)
const busy = ref(false)
const hint = ref('')
const toast = ref('')

const doneCount = computed(() =>
  (detail.value?.items || []).filter((it) => Number(it.inQty || 0) > 0).length
)

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : ''
}

function step(d) {
  const n = Number(qty.value || 0) + d
  qty.value = Math.max(0, Number(n.toFixed(3)))
}

function setQty(it, v) {
  it.inQty = Math.max(0, Number(v || 0))
}

function bump(it, d) {
  it.inQty = Math.max(0, Number((Number(it.inQty || 0) + d).toFixed(3)))
}

function fillRemain(it) {
  if (it.planQty != null) {
    it.inQty = Number(it.planQty)
    toast.value = '已填满计划数量'
  } else {
    toast.value = '无计划数量，请手动填写'
  }
}

async function loadOrders() {
  try {
    const resp = await api.inboundList({ page: 1, size: 20, status: 'DRAFT' })
    orders.value = resp.data?.records || []
  } catch (e) {
    toast.value = e.message
  }
}

async function openOrder(id) {
  if (!id) return
  try {
    const resp = await api.inboundDetail(id)
    detail.value = resp.data
  } catch (e) {
    toast.value = e.message
  }
}

async function doScan(fillMode) {
  if (!detail.value || !scanCode.value) return
  busy.value = true
  hint.value = ''
  try {
    const body = {
      code: scanCode.value,
      fillMode,
      qty: fillMode === 'ADD' ? qty.value : qty.value
    }
    const resp = await api.inboundScan(detail.value.order.id, body)
    if (resp.data.matched) {
      detail.value.items = resp.data.items
      toast.value =
        fillMode === 'ADD'
          ? `已累加 ${resp.data.addQty}`
          : `已填数量 ${resp.data.filledQty ?? ''}`
    } else {
      hint.value = resp.data.hint || resp.data.failReason || '未匹配明细'
    }
  } catch (e) {
    if (e.offline) {
      enqueueScan({ code: scanCode.value, scanType: 'INBOUND', bizType: 'INBOUND', bizId: detail.value.order.id, result: 'FAIL', failReason: 'offline' })
      enqueueAction({
        kind: 'INBOUND_SCAN',
        method: 'POST',
        path: `/api/m/inbounds/${detail.value.order.id}/scan`,
        body: { code: scanCode.value, fillMode, qty: qty.value },
        label: `入库扫 ${scanCode.value}`
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
  if (!window.confirm('确认整单入库？将写库存与流水，需在线。')) return
  busy.value = true
  try {
    await api.inboundConfirm(detail.value.order.id)
    toast.value = '入库完成'
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
  const qid = route.query.id
  if (qid) openOrder(qid)
})
</script>

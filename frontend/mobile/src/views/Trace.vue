<template>
  <div class="page">
    <NavBar title="物资溯源" />

    <div class="card">
      <div class="field">
        <label>trace_token / 溯源码</label>
        <input v-model.trim="token" placeholder="粘贴 token 或从扫码进入" @keyup.enter="load" />
      </div>
      <div class="btn-row">
        <button class="btn" :disabled="!token || loading" @click="load">查询溯源</button>
        <button class="btn plain" @click="$router.push('/scan')">去扫码</button>
      </div>
    </div>

    <div v-if="data" class="card">
      <h3>物资溯源</h3>
      <div class="kv"><span>名称</span><span>{{ data.materialName || '—' }}</span></div>
      <div class="kv"><span>编码</span><span>{{ data.materialCode || '—' }}</span></div>
      <div class="kv"><span>规格</span><span>{{ data.spec || '—' }}</span></div>
      <div class="kv"><span>批次</span><span>{{ data.batchNo || '—' }}</span></div>
      <div class="kv"><span>序列</span><span>{{ data.serialNo || '—' }}</span></div>
      <div class="kv"><span>状态</span><span>{{ tQrStatus(data.status) || '—' }}</span></div>
      <div class="kv"><span>有效期</span><span>{{ data.expireDate || '—' }}</span></div>
      <div class="kv"><span>查验次数</span><span>{{ data.scanCount ?? 0 }}{{ data.firstScan ? ' · 含首次' : '' }}</span></div>
      <div class="kv"><span>供应商</span><span>{{ data.supplierName || (data.supplierMasked ? '（脱敏）' : '—') }}</span></div>

      <h3 style="margin-top: 12px">链路</h3>
      <ul class="timeline">
        <li v-if="data.purchaseOrderId">采购订单 #{{ data.purchaseOrderId }}</li>
        <li v-if="data.inboundOrderId">入库单 #{{ data.inboundOrderId }}</li>
        <li v-if="data.activatedAt">激活 {{ fmt(data.activatedAt) }}</li>
        <li>当前仓库 #{{ data.currentWarehouseId || '—' }}</li>
        <li>状态 {{ tQrStatus(data.status) || '—' }}</li>
      </ul>
      <p class="muted">对外 token 页无内部价；登录授权角色可看价格字段。</p>
    </div>

    <TabBar />
    <Toast :message="toast" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import { loadDicts, tQrStatus } from '../utils/dict'
import NavBar from '../components/NavBar.vue'
import TabBar from '../components/TabBar.vue'
import Toast from '../components/Toast.vue'

const route = useRoute()
const token = ref(String(route.query.token || route.query.code || ''))
const data = ref(null)
const loading = ref(false)
const toast = ref('')

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  if (!token.value) return
  loading.value = true
  try {
    // 支持直接贴完整 URL
    let t = token.value
    const idx = t.toLowerCase().indexOf('/trace/')
    if (idx >= 0) t = t.slice(idx + 7).split('?')[0].split('#')[0]
    const resp = await api.trace(t)
    data.value = resp.data
  } catch (e) {
    toast.value = e.message
    data.value = null
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  await loadDicts()
  if (token.value) load()
})
</script>

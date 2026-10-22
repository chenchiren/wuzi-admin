<template>
  <div class="page">
    <NavBar title="库存查询" :back="false" />

    <div class="card">
      <div class="field">
        <label>关键字（物资名/编码）</label>
        <div class="qty-box">
          <input v-model.trim="keyword" placeholder="如 A4 / 物资编码" @keyup.enter="load" />
          <button class="btn" @click="load">查</button>
        </div>
      </div>
      <div class="field">
        <label>物资 ID（可选）</label>
        <input v-model.number="materialId" type="number" placeholder="materialId" />
      </div>
    </div>

    <div class="card">
      <h3>库存</h3>
      <div v-if="!rows.length" class="empty">{{ loaded ? '无匹配库存' : '输入条件后查询' }}</div>
      <div v-for="s in rows" :key="s.stockId" class="list-item">
        <div class="row">
          <b>{{ s.materialName || '物资 #' + s.materialId }}</b>
          <span class="tag ok">{{ s.qty }}</span>
        </div>
        <div class="muted">
          {{ s.materialCode }} · {{ s.spec || '' }} · 批次 {{ s.batchNo || '—' }}
        </div>
        <div class="muted">仓库 #{{ s.warehouseId }} · 库位 #{{ s.locationId || '—' }} · 冻结 {{ s.frozenQty || 0 }}</div>
        <div v-if="s.costTaxIncl != null" class="muted">含税成本 {{ s.costTaxIncl }}</div>
        <div class="btn-row">
          <button class="btn plain" @click="draft(s)">暂存实盘</button>
          <button class="btn ghost" @click="$router.push({ path: '/scan', query: { code: s.batchNo || s.materialCode || '' } })">扫</button>
        </div>
      </div>
    </div>

    <div class="card">
      <h3>盘点草稿（离线）</h3>
      <div class="field">
        <label>盘点单 ID（可选）</label>
        <input v-model.number="draftForm.checkId" type="number" />
      </div>
      <div class="field">
        <label>物资 ID</label>
        <input v-model.number="draftForm.materialId" type="number" />
      </div>
      <div class="field">
        <label>实盘数量</label>
        <input v-model.number="draftForm.actualQty" type="number" step="0.001" />
      </div>
      <button class="btn block ghost" @click="saveDraft">保存盘点草稿</button>
    </div>

    <TabBar active="stocks" />
    <Toast :message="toast" />
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { api } from '../api'
import { enqueueDraft, getQueue } from '../offline'
import NavBar from '../components/NavBar.vue'
import TabBar from '../components/TabBar.vue'
import Toast from '../components/Toast.vue'

const keyword = ref('')
const materialId = ref(null)
const rows = ref([])
const loaded = ref(false)
const toast = ref('')
const draftForm = reactive({ checkId: null, materialId: null, actualQty: 1 })

async function load() {
  try {
    const params = { page: 1, size: 20 }
    if (materialId.value) params.materialId = materialId.value
    if (keyword.value) params.keyword = keyword.value
    const resp = await api.stocks(params)
    rows.value = resp.data?.records || []
    loaded.value = true
  } catch (e) {
    toast.value = e.message
  }
}

function draft(s) {
  draftForm.checkId = draftForm.checkId
  draftForm.materialId = s.materialId
  draftForm.actualQty = s.qty
  toast.value = '已填入实盘草稿，点保存'
}

function saveDraft() {
  if (!draftForm.materialId) {
    toast.value = '物资 ID 必填'
    return
  }
  enqueueDraft({
    checkId: draftForm.checkId,
    materialId: draftForm.materialId,
    actualQty: draftForm.actualQty,
    remark: 'mobile-count'
  })
  const n = getQueue().drafts.length
  toast.value = `已暂存盘点草稿（队列 ${n}）`
}

onMounted(load)
</script>

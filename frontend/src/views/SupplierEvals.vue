<template>
  <el-card v-loading="loading">
    <div class="bar">
      <el-select v-model="supplierId" filterable placeholder="选择供应商" style="width:280px" @change="loadAll">
        <el-option v-for="s in suppliers" :key="s.id" :label="s.supplierName" :value="s.id" />
      </el-select>
      <el-date-picker v-model="period" type="daterange" value-format="YYYY-MM-DD"
        start-placeholder="期间起" end-placeholder="期间止" style="width:240px" />
      <n-button type="primary" :disabled="!supplierId" @click="evaluate">计算评分</n-button>
      <n-button type="success" v-perm="'base:supplier:eval-recalc'" :disabled="!supplierId" @click="recalc">按权重重算</n-button>
    </div>

    <template v-if="result">
      <el-row :gutter="16" style="margin-bottom:16px">
        <el-col :span="6">
          <el-card shadow="hover"><div class="kpi">综合得分</div>
            <div class="num primary">{{ fmt(result.totalScore) }}</div></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover"><div class="kpi">准时到货率</div>
            <div class="num">{{ pct(result.onTimeRate) }}</div>
            <div class="hint">权重 {{ result.weightOnTime }}</div></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover"><div class="kpi">退货率</div>
            <div class="num">{{ pct(result.returnRate) }}</div>
            <div class="hint">权重 {{ result.weightReturn }}</div></el-card>
        </el-col>
        <el-col :span="6">
          <el-card shadow="hover"><div class="kpi">价格得分</div>
            <div class="num">{{ fmt(result.priceScore) }}</div>
            <div class="hint">权重 {{ result.weightPrice }}</div></el-card>
        </el-col>
      </el-row>
      <el-descriptions :column="3" border size="small" class="mb-4">
        <el-descriptions-item label="供应商">{{ result.supplierName }}</el-descriptions-item>
        <el-descriptions-item label="统计期间">{{ result.periodStart || '—' }} ~ {{ result.periodEnd || '—' }}</el-descriptions-item>
        <el-descriptions-item label="订单数">{{ result.orderCount }}</el-descriptions-item>
      </el-descriptions>
    </template>

    <el-card v-if="summary" style="margin-bottom:16px">
      <template #header><div class="card-title">供应商概况</div></template>
      <el-descriptions :column="4" border size="small">
        <el-descriptions-item label="采购总额">{{ money(summary.purchaseAmount) }}</el-descriptions-item>
        <el-descriptions-item label="订单总数">{{ summary.orderCount }}</el-descriptions-item>
        <el-descriptions-item label="已完成订单">{{ summary.completedOrders }}</el-descriptions-item>
        <el-descriptions-item label="在供产品数">{{ summary.productCount }}</el-descriptions-item>
      </el-descriptions>
    </el-card>

    <el-card>
      <template #header>
        <div class="card-title">历史评分</div>
        <div class="card-desc">每次计算都会写入一条评分记录（默认权重 40/30/30）</div>
      </template>
      <el-table :data="history" border stripe empty-text="暂无评分记录">
        <el-table-column prop="evalDate" label="评分日期" min-width="110" />
        <el-table-column prop="periodStart" label="期间起" min-width="100" />
        <el-table-column prop="periodEnd" label="期间止" min-width="100" />
        <el-table-column prop="onTimeRate" label="准时率" min-width="90" />
        <el-table-column prop="returnRate" label="退货率" min-width="90" />
        <el-table-column prop="priceScore" label="价格分" min-width="90" />
        <el-table-column prop="totalScore" label="综合分" min-width="90">
          <template #default="{ row }"><b>{{ fmt(row.totalScore) }}</b></template>
        </el-table-column>
      </el-table>
    </el-card>
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/request'

const suppliers = ref([])
const supplierId = ref(null)
const period = ref(null)
const result = ref(null)
const history = ref([])
const summary = ref(null)
const loading = ref(false)

const fmt = v => v == null ? '—' : Number(v).toFixed(2)
const pct = v => v == null ? '—' : (Number(v) <= 1 ? (Number(v) * 100).toFixed(1) + '%' : Number(v).toFixed(2))
const money = v => v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

async function loadAll() {
  if (!supplierId.value) return
  loading.value = true
  try {
    await evaluate(true)
    const [h, s] = await Promise.all([
      api.get(`/suppliers/${supplierId.value}/eval/history`).catch(() => ({ data: [] })),
      api.get(`/suppliers/${supplierId.value}/summary`).catch(() => ({ data: null }))
    ])
    history.value = h.data || []
    summary.value = s.data
  } finally { loading.value = false }
}

async function evaluate(silent = false) {
  try {
    const res = await api.get(`/suppliers/${supplierId.value}/eval`, { params: buildPeriod() })
    result.value = res.data.result
    history.value = res.data.history || history.value
    if (!silent) ElMessage.success('已计算')
  } catch (e) { if (!silent) ElMessage.error(e.message || '计算失败') }
}

async function recalc() {
  const [ps, pe] = period.value || []
  try {
    const res = await api.post(`/suppliers/${supplierId.value}/eval/recalc`, {
      periodStart: ps || null, periodEnd: pe || null
    })
    result.value = res.data.result
    const h = await api.get(`/suppliers/${supplierId.value}/eval/history`).catch(() => ({ data: [] }))
    history.value = h.data || []
    ElMessage.success('已重算并写入历史')
  } catch (e) { ElMessage.error(e.message || '重算失败') }
}

function buildPeriod() {
  const [ps, pe] = period.value || []
  return { periodStart: ps || undefined, periodEnd: pe || undefined }
}

onMounted(async () => {
  const s = await api.get('/suppliers', { params: { page: 1, size: 200 } }).catch(() => ({ data: { records: [] } }))
  suppliers.value = s.data.records || []
})
</script>

<style scoped>
.bar { display:flex; gap:10px; margin-bottom:16px; align-items:center; flex-wrap:wrap; }
.kpi { color:#888; font-size:13px; }
.num { font-size:26px; font-weight:600; margin-top:8px; }
.num.primary { color:#2f6bff; }
.hint { margin-top:4px; font-size:12px; color:#909399; }
.card-title { font-size:15px; font-weight:600; }
.card-desc { margin-top:4px; font-size:12px; color:#909399; }
</style>

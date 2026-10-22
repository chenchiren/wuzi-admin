<template>
  <el-card v-loading="loading">
    <div class="bar">
      <el-select v-model="materialId" filterable clearable placeholder="选择物资（必选）" style="width:240px" @change="onMaterialChange">
        <el-option v-for="m in materials" :key="m.id" :value="m.id" :label="`${m.materialCode} ${m.materialName}`" />
      </el-select>
      <el-select v-model="supplierId" filterable clearable placeholder="供应商（可选筛选）" style="width:200px" @change="load">
        <el-option v-for="s in suppliers" :key="s.id" :value="s.id" :label="s.supplierName" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
    </div>

    <el-alert
      v-if="!materialId"
      type="info"
      :closable="false"
      title="请选择物资后查看价格趋势（接口要求 materialId 或 supplierProductId 至少一个）"
      style="margin-bottom:12px"
    />
    <el-alert
      v-else-if="hidePrice"
      type="warning"
      :closable="false"
      title="当前角色无价格查看权限，已隐藏金额字段"
      style="margin-bottom:12px"
    />

    <el-row :gutter="16">
      <el-col :span="14">
        <div ref="chartEl" class="chart-box"></div>
      </el-col>
      <el-col :span="10">
        <div class="kpi-row">
          <div class="kpi">
            <div class="kpi-label">数据点</div>
            <div class="kpi-num">{{ rows.length }}</div>
          </div>
          <div class="kpi">
            <div class="kpi-label">最新含税价</div>
            <div class="kpi-num">{{ lastPrice }}</div>
          </div>
          <div class="kpi">
            <div class="kpi-label">区间涨跌</div>
            <div class="kpi-num" :class="changeClass">{{ changeText }}</div>
          </div>
        </div>
        <el-table :data="rows" border stripe height="320" size="small">
          <el-table-column prop="date" label="日期" min-width="100">
            <template #default="{ row }">{{ fmtDate(row.date) }}</template>
          </el-table-column>
          <el-table-column label="来源" min-width="110">
            <template #default="{ row }">
              <StatusTag :value="row.source" dict-type="PRICE_SOURCE" />
            </template>
          </el-table-column>
          <el-table-column label="供应商" min-width="110">
            <template #default="{ row }">{{ supplierLabel(row.supplierId) }}</template>
          </el-table-column>
          <el-table-column prop="taxInclusivePrice" label="含税价" min-width="90">
            <template #default="{ row }">{{ row.taxInclusivePrice ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="taxExclusivePrice" label="不含税" min-width="90">
            <template #default="{ row }">{{ row.taxExclusivePrice ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="taxRate" label="税率%" min-width="70">
            <template #default="{ row }">{{ row.taxRate ?? '—' }}</template>
          </el-table-column>
          <el-table-column prop="orderNo" label="单号" min-width="110">
            <template #default="{ row }">{{ row.orderNo || '—' }}</template>
          </el-table-column>
        </el-table>
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import api from '../api/request'
import { loadDicts, tPriceSource } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const materials = ref([])
const suppliers = ref([])
const materialId = ref(null)
const supplierId = ref(null)
const loading = ref(false)
const hidePrice = ref(false)
const chartEl = ref(null)
let chart = null

const visibleRows = computed(() => {
  if (!supplierId.value) return rows.value
  return rows.value.filter((r) => !r.supplierId || r.supplierId === supplierId.value)
})

const lastPrice = computed(() => {
  const list = visibleRows.value.filter((r) => r.taxInclusivePrice != null)
  if (!list.length) return '—'
  return list[list.length - 1].taxInclusivePrice
})

const changeText = computed(() => {
  const list = visibleRows.value.filter((r) => r.taxInclusivePrice != null)
  if (list.length < 2) return '—'
  const first = Number(list[0].taxInclusivePrice)
  const last = Number(list[list.length - 1].taxInclusivePrice)
  if (!first) return '—'
  const rate = ((last - first) / first) * 100
  const sign = rate > 0 ? '+' : ''
  return `${sign}${rate.toFixed(2)}%`
})

const changeClass = computed(() => {
  const t = changeText.value
  if (t === '—') return ''
  if (t.startsWith('+')) return 'up'
  if (t.startsWith('-')) return 'down'
  return ''
})

function fmtDate(v) {
  return v ? String(v).slice(0, 10) : '—'
}

function supplierLabel(id) {
  const s = suppliers.value.find((x) => x.id === id)
  return s ? s.supplierName : id ? `#${id}` : '—'
}

function onMaterialChange() {
  load()
}

function renderChart() {
  if (!chartEl.value) return
  if (!chart) chart = echarts.init(chartEl.value)
  const list = visibleRows.value
  const dates = list.map((r) => fmtDate(r.date))
  const prices = list.map((r) => (r.taxInclusivePrice == null ? null : Number(r.taxInclusivePrice)))
  chart.setOption(
    {
      tooltip: { trigger: 'axis', confine: true },
      grid: { left: 48, right: 16, top: 24, bottom: 32 },
      xAxis: { type: 'category', data: dates, axisLabel: { fontSize: 11 } },
      yAxis: { type: 'value', scale: true, axisLabel: { fontSize: 11 } },
      series: [
        {
          name: '含税价',
          type: 'line',
          smooth: true,
          showSymbol: true,
          symbolSize: 6,
          data: prices,
          itemStyle: { color: '#2f6bff' },
          areaStyle: { color: 'rgba(47,107,255,0.08)' }
        }
      ]
    },
    true
  )
}

async function load() {
  if (!materialId.value) {
    rows.value = []
    hidePrice.value = false
    renderChart()
    return
  }
  loading.value = true
  try {
    const res = await api.get('/price-trends', {
      params: { materialId: materialId.value }
    })
    const data = res.data || {}
    hidePrice.value = !!data.hidePrice
    let points = data.points || []
    if (supplierId.value) {
      points = points.filter((p) => !p.supplierId || p.supplierId === supplierId.value)
    }
    points.sort((a, b) => String(a.date || '').localeCompare(String(b.date || '')))
    rows.value = points
    await nextTick()
    renderChart()
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
    rows.value = []
    renderChart()
  } finally {
    loading.value = false
  }
}

function onResize() {
  chart?.resize()
}

onMounted(async () => {
  await loadDicts()
  window.addEventListener('resize', onResize)
  try {
    const [m, s] = await Promise.all([
      api.get('/materials', { params: { page: 1, size: 200 } }),
      api.get('/suppliers', { params: { page: 1, size: 200 } })
    ])
    materials.value = m.data.records || []
    suppliers.value = s.data.records || []
  } catch {
    /* ignore */
  }
  await nextTick()
  renderChart()
})

watch(visibleRows, async () => {
  await nextTick()
  renderChart()
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  chart?.dispose()
  chart = null
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; flex-wrap:wrap; }
.chart-box { width:100%; height:360px; }
.kpi-row { display:flex; gap:8px; margin-bottom:12px; }
.kpi {
  flex:1;
  background:#f8fafc;
  border:1px solid #eef2f7;
  border-radius:8px;
  padding:10px 12px;
}
.kpi-label { font-size:12px; color:#909399; }
.kpi-num { font-size:18px; font-weight:600; margin-top:4px; }
.kpi-num.up { color:#f56c6c; }
.kpi-num.down { color:#67c23a; }
</style>

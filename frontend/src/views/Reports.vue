<template>
  <div class="reports-page">
    <div class="toolbar">
      <div class="toolbar-left">
        <span class="title">高级报表</span>
        <span class="desc">按采购 / 出入库 / 库存 / 领用维度交叉分析</span>
      </div>
      <div class="toolbar-right">
        <el-select v-model="months" style="width:130px" @change="loadAll">
          <el-option :value="6" label="近6个月" />
          <el-option :value="12" label="近12个月" />
          <el-option :value="24" label="近24个月" />
        </el-select>
        <el-select v-model="focus" style="width:180px" @change="scrollTo">
          <el-option v-for="o in panels" :key="o.key" :value="o.key" :label="o.title" />
        </el-select>
        <n-button type="primary" @click="loadAll">刷新</n-button>
        <n-button @click="exportCsv">导出CSV</n-button>
      </div>
    </div>

    <!-- 概览指标 -->
    <div class="summary-grid">
      <div v-for="s in summaries" :key="s.label" class="summary-card">
        <div class="s-label">{{ s.label }}</div>
        <div class="s-value">{{ s.value }}</div>
        <div class="s-sub">{{ s.sub }}</div>
      </div>
    </div>

    <!-- 图表总览：有序网格，互不遮挡 -->
    <div class="board">
      <section
        v-for="p in panels"
        :key="p.key"
        :id="`panel-${p.key}`"
        class="panel"
        :class="{ 'is-focus': focus === p.key }"
      >
        <header class="panel-hd">
          <div>
            <h3>{{ p.title }}</h3>
            <p class="panel-sub">{{ p.desc }}</p>
          </div>
          <div class="panel-actions">
            <span class="badge">{{ p.badge }}</span>
            <n-button size="small" text type="primary" @click="exportOne(p.key)">导出</n-button>
          </div>
        </header>

        <div :ref="el => setRef(p.key, el)" class="chart-box" :class="p.tall ? 'tall' : ''"></div>

        <el-table :data="tableData(p.key)" border stripe size="small" class="tbl" max-height="220">
          <el-table-column
            v-for="c in columns(p.key)"
            :key="c.prop"
            :prop="c.prop"
            :label="c.label"
            :min-width="c.width || 110"
            :formatter="c.fmt ? (row) => c.fmt(row[c.prop]) : undefined"
          />
        </el-table>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import * as echarts from 'echarts'
import axios from 'axios'
import api from '../api/request'

const months = ref(12)
const focus = ref('purchase-monthly')
const purchaseMonthly = ref([])
const inoutMonthly = ref([])
const categoryStock = ref([])
const warehouseStock = ref([])
const deptUse = ref([])
const supplierRank = ref([])

const panels = [
  { key: 'purchase-monthly', title: '月度采购金额', desc: '订单含税金额随时间变化，看采购节奏', badge: '采购', tall: true },
  { key: 'inout-monthly', title: '入出库趋势', desc: '入库与出库数量对照，看周转是否平衡', badge: '仓储', tall: true },
  { key: 'category-stock', title: '分类库存结构', desc: '按物资分类看库存金额占比', badge: '库存' },
  { key: 'warehouse-stock', title: '仓库库存对比', desc: '各仓库库存金额横向对比', badge: '库存' },
  { key: 'supplier-rank', title: '供应商采购排行', desc: 'Top 供应商采购金额与订单数', badge: '采购', tall: true },
  { key: 'dept-use', title: '部门领用排行', desc: '各部门领用数量与申请单数', badge: '使用', tall: true }
]

const summaries = computed(() => {
  const purchaseSum = purchaseMonthly.value.reduce((a, r) => a + (Number(r.amount) || 0), 0)
  const inSum = inoutMonthly.value.reduce((a, r) => a + (Number(r.inQty) || 0), 0)
  const outSum = inoutMonthly.value.reduce((a, r) => a + (Number(r.outQty) || 0), 0)
  const catSum = categoryStock.value.reduce((a, r) => a + (Number(r.amount) || 0), 0)
  const topSupplier = supplierRank.value[0]
  const topDept = deptUse.value[0]
  return [
    { label: `近${months.value}月采购`, value: money(purchaseSum), sub: '含税金额合计' },
    { label: '入库总量', value: fmt(inSum), sub: `出库 ${fmt(outSum)}` },
    { label: '库存金额', value: money(catSum), sub: `${categoryStock.value.length} 个分类` },
    { label: 'TOP供应商', value: topSupplier?.name || '—', sub: topSupplier ? money(topSupplier.amount) : '暂无数据' },
    { label: 'TOP领用部门', value: topDept?.name || '—', sub: topDept ? `${fmt(topDept.qty)} 件` : '暂无数据' },
    { label: '图表面板', value: String(panels.length), sub: '全部展开，无遮挡' }
  ]
})

const refs = {}
function setRef(key, el) {
  refs[key] = el
}
const charts = {}

function money(v) {
  const n = Number(v) || 0
  return '¥' + n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}
function fmt(v) {
  if (v === null || v === undefined || v === '') return '0'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}
function shortMonth(m) {
  return String(m).slice(2)
}

function tableData(key) {
  if (key === 'purchase-monthly') return purchaseMonthly.value
  if (key === 'inout-monthly') return inoutMonthly.value
  if (key === 'category-stock') return categoryStock.value
  if (key === 'warehouse-stock') return warehouseStock.value
  if (key === 'supplier-rank') return supplierRank.value
  if (key === 'dept-use') return deptUse.value
  return []
}

function columns(key) {
  if (key === 'purchase-monthly') return [
    { prop: 'month', label: '月份', width: 100 },
    { prop: 'amount', label: '采购金额', width: 140, fmt: money }
  ]
  if (key === 'inout-monthly') return [
    { prop: 'month', label: '月份', width: 100 },
    { prop: 'inQty', label: '入库量', width: 110, fmt: fmt },
    { prop: 'outQty', label: '出库量', width: 110, fmt: fmt }
  ]
  if (key === 'category-stock') return [
    { prop: 'name', label: '分类', width: 130 },
    { prop: 'qty', label: '数量', width: 100, fmt: fmt },
    { prop: 'amount', label: '金额', width: 120, fmt: money },
    { prop: 'ratio', label: '占比%', width: 90, fmt: v => fmt(v) }
  ]
  if (key === 'warehouse-stock') return [
    { prop: 'name', label: '仓库', width: 130 },
    { prop: 'qty', label: '数量', width: 100, fmt: fmt },
    { prop: 'amount', label: '金额', width: 120, fmt: money },
    { prop: 'ratio', label: '占比%', width: 90, fmt: v => fmt(v) }
  ]
  if (key === 'supplier-rank') return [
    { prop: 'name', label: '供应商', width: 160 },
    { prop: 'code', label: '编码', width: 110 },
    { prop: 'amount', label: '采购金额', width: 130, fmt: money },
    { prop: 'orderCount', label: '订单数', width: 100, fmt: fmt }
  ]
  return [
    { prop: 'name', label: '部门', width: 140 },
    { prop: 'qty', label: '领用数量', width: 120, fmt: fmt },
    { prop: 'applyCount', label: '申请单数', width: 110, fmt: fmt }
  ]
}

function ensure(key) {
  const el = refs[key]
  if (!el) return null
  let chart = echarts.getInstanceByDom(el)
  if (!chart) {
    chart = echarts.init(el)
    charts[key] = chart
  }
  return chart
}

function drawPurchase() {
  const chart = ensure('purchase-monthly')
  if (!chart) return
  chart.setOption({
    tooltip: { trigger: 'axis', confine: true, valueFormatter: money },
    grid: { left: 64, right: 16, top: 24, bottom: 32 },
    xAxis: { type: 'category', data: purchaseMonthly.value.map(r => shortMonth(r.month)), axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } }, axisLabel: { fontSize: 11, formatter: v => fmt(v) } },
    series: [{
      type: 'bar',
      barMaxWidth: 28,
      itemStyle: { color: '#2f6bff', borderRadius: [4, 4, 0, 0] },
      data: purchaseMonthly.value.map(r => Number(r.amount) || 0)
    }]
  }, true)
}

function drawInOut() {
  const chart = ensure('inout-monthly')
  if (!chart) return
  chart.setOption({
    color: ['#22c55e', '#f97316'],
    tooltip: { trigger: 'axis', confine: true },
    legend: { top: 0, right: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10 },
    grid: { left: 48, right: 16, top: 36, bottom: 32 },
    xAxis: { type: 'category', data: inoutMonthly.value.map(r => shortMonth(r.month)), axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } }, axisLabel: { fontSize: 11 } },
    series: [
      { name: '入库', type: 'line', smooth: true, symbolSize: 6, areaStyle: { opacity: 0.12 }, data: inoutMonthly.value.map(r => Number(r.inQty) || 0) },
      { name: '出库', type: 'line', smooth: true, symbolSize: 6, areaStyle: { opacity: 0.12 }, data: inoutMonthly.value.map(r => Number(r.outQty) || 0) }
    ]
  }, true)
}

function drawPie(key, rows, colorKey = 'amount') {
  const chart = ensure(key)
  if (!chart) return
  const data = rows.slice(0, 8)
  chart.setOption({
    color: ['#2f6bff', '#22c55e', '#f59e0b', '#a855f7', '#06b6d4', '#f43f5e', '#84cc16', '#64748b'],
    tooltip: {
      trigger: 'item', confine: true,
      formatter: p => `${p.name}<br/>${fmt(p.value)}（${p.percent}%）`
    },
    legend: { type: 'scroll', bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '44%'],
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{d}%', fontSize: 11, overflow: 'break', width: 72 },
      labelLine: { length: 8, length2: 6 },
      data: data.map(r => ({ name: r.name, value: Number(r[colorKey]) || 0 }))
    }]
  }, true)
}

function drawBarH(key, rows, valueKey, color) {
  const chart = ensure(key)
  if (!chart) return
  const data = rows.slice(0, 8)
  chart.setOption({
    tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 72, top: 8, bottom: 8, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } }, axisLabel: { fontSize: 10 } },
    yAxis: {
      type: 'category',
      data: data.map(r => r.name).reverse(),
      axisLabel: { fontSize: 11, width: 92, overflow: 'truncate' }
    },
    series: [{
      type: 'bar',
      barMaxWidth: 14,
      itemStyle: { color, borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', fontSize: 11, formatter: p => fmt(p.value) },
      data: data.map(r => Number(r[valueKey]) || 0).reverse()
    }]
  }, true)
}

function drawWarehouse() {
  const chart = ensure('warehouse-stock')
  if (!chart) return
  const data = warehouseStock.value.slice(0, 8)
  chart.setOption({
    tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 72, top: 8, bottom: 8, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } }, axisLabel: { fontSize: 10 } },
    yAxis: {
      type: 'category',
      data: data.map(r => r.name).reverse(),
      axisLabel: { fontSize: 11, width: 88, overflow: 'truncate' }
    },
    series: [{
      type: 'bar',
      barMaxWidth: 16,
      itemStyle: { color: '#0ea5a4', borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', fontSize: 11, formatter: p => fmt(p.value) },
      data: data.map(r => Number(r.amount) || 0).reverse()
    }]
  }, true)
}

function drawAll() {
  drawPurchase()
  drawInOut()
  drawPie('category-stock', categoryStock.value)
  drawWarehouse()
  drawBarH('supplier-rank', supplierRank.value, 'amount', '#2f6bff')
  drawBarH('dept-use', deptUse.value, 'qty', '#a855f7')
}

function scrollTo(key) {
  const el = document.getElementById(`panel-${key}`)
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function loadAll() {
  try {
    const [p, io, c, w, d, s] = await Promise.all([
      api.get('/reports/purchase-monthly', { params: { months: months.value } }),
      api.get('/reports/inout-monthly', { params: { months: months.value } }),
      api.get('/reports/category-stock'),
      api.get('/reports/warehouse-stock'),
      api.get('/reports/dept-use', { params: { limit: 10 } }),
      api.get('/reports/supplier-rank', { params: { limit: 10 } })
    ])
    purchaseMonthly.value = p.data || []
    inoutMonthly.value = io.data || []
    categoryStock.value = c.data || []
    warehouseStock.value = w.data || []
    deptUse.value = d.data || []
    supplierRank.value = s.data || []
    await nextTick()
    drawAll()
  } catch (e) {
    ElMessage.error(e.message || '报表加载失败')
  }
}

async function exportOne(type) {
  try {
    const res = await axios.get('/api/reports/export', {
      params: { type, months: months.value, limit: 10 },
      responseType: 'blob',
      headers: { Authorization: `Bearer ${localStorage.getItem('token')}` }
    })
    downloadBlob(res.data, `report-${type}.csv`)
    ElMessage.success('已导出')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

async function exportCsv() {
  await exportOne(focus.value)
}

function downloadBlob(blob, filename) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.click()
  URL.revokeObjectURL(url)
}

function onResize() {
  Object.values(charts).forEach(c => c && c.resize())
}

onMounted(async () => {
  await loadAll()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  Object.values(charts).forEach(c => c && c.dispose())
})
</script>

<style scoped>
.reports-page { display: flex; flex-direction: column; gap: 16px; }

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 12px;
  padding: 14px 16px;
}
.toolbar-left { display: flex; flex-direction: column; gap: 4px; }
.title { font-size: 16px; font-weight: 700; color: #0f172a; }
.desc { font-size: 12px; color: #94a3b8; }
.toolbar-right { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }

.summary-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}
@media (max-width: 1400px) { .summary-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 800px) { .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }

.summary-card {
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 12px;
  padding: 12px 14px;
  min-width: 0;
}
.s-label { font-size: 12px; color: #64748b; }
.s-value {
  font-size: 18px;
  font-weight: 700;
  color: #0f172a;
  margin-top: 6px;
  line-height: 1.3;
  word-break: break-all;
}
.s-sub { font-size: 11px; color: #94a3b8; margin-top: 4px; }

.board {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
@media (max-width: 960px) { .board { grid-template-columns: 1fr; } }

.panel {
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 12px;
  padding: 14px 16px 16px;
  min-width: 0;
  overflow: visible;
  scroll-margin-top: 16px;
}
.panel.is-focus { border-color: #bfdbfe; box-shadow: 0 0 0 3px rgba(47, 107, 255, 0.08); }

.panel-hd {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.panel-hd h3 { margin: 0; font-size: 15px; font-weight: 600; color: #0f172a; }
.panel-sub { margin: 4px 0 0; font-size: 12px; color: #94a3b8; }
.panel-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.badge {
  display: inline-flex;
  align-items: center;
  height: 22px;
  padding: 0 8px;
  border-radius: 999px;
  background: #eef3ff;
  color: #2f6bff;
  font-size: 12px;
}

.chart-box {
  width: 100%;
  height: 260px;
}
.chart-box.tall { height: 300px; }

.tbl { margin-top: 12px; }
</style>

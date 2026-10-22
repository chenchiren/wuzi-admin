<template>
  <div class="stats-page">
    <!-- 核心指标 -->
    <div class="kpi-grid">
      <div v-for="k in kpis" :key="k.label" class="kpi-card" :style="{ '--c': k.color }">
        <div class="kpi-ico" aria-hidden="true">{{ k.icon }}</div>
        <div class="kpi-body">
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-value">{{ k.value }}</div>
          <div class="kpi-hint">{{ k.hint }}</div>
        </div>
      </div>
    </div>

    <!-- 趋势区 -->
    <div class="chart-grid">
      <section class="panel span-2">
        <header class="panel-hd">
          <h3>近6个月入出库趋势</h3>
          <span class="panel-sub">按出入库流水汇总，辅助判断周转节奏</span>
        </header>
        <div ref="inoutEl" class="chart-box chart-tall"></div>
      </section>

      <section class="panel">
        <header class="panel-hd">
          <h3>本月业务构成</h3>
          <span class="panel-sub">入库 / 出库数量对比</span>
        </header>
        <div ref="bizEl" class="chart-box"></div>
        <div v-if="bizRows.length" class="mini-table">
          <div v-for="r in bizRows" :key="r.bizType" class="mini-row">
            <span class="tag">{{ bizLabel(r.bizType) }}</span>
            <span class="num">{{ fmt(r.qty) }}</span>
            <span class="money">{{ r.recordCount }} 笔</span>
          </div>
        </div>
      </section>

      <section class="panel">
        <header class="panel-hd">
          <h3>库存结构 · 分类</h3>
          <span class="panel-sub">按库存金额占比</span>
        </header>
        <div ref="catEl" class="chart-box"></div>
      </section>

      <section class="panel">
        <header class="panel-hd">
          <h3>库存分布 · 仓库</h3>
          <span class="panel-sub">按库存金额排序</span>
        </header>
        <div ref="whEl" class="chart-box"></div>
      </section>

      <section class="panel span-2">
        <header class="panel-hd">
          <h3>采购金额走势</h3>
          <span class="panel-sub">近6个月订单含税金额</span>
        </header>
        <div ref="purchaseEl" class="chart-box chart-tall"></div>
      </section>

      <section class="panel">
        <header class="panel-hd">
          <h3>供应商采购排行</h3>
          <span class="panel-sub">Top 8 · 金额</span>
        </header>
        <div ref="supplierEl" class="chart-box chart-tall"></div>
      </section>

      <section class="panel">
        <header class="panel-hd">
          <h3>部门领用排行</h3>
          <span class="panel-sub">Top 8 · 领用数量</span>
        </header>
        <div ref="deptEl" class="chart-box chart-tall"></div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import api from '../api/request'

const dash = ref({
  materialKinds: 0, stockQty: 0, stockAmount: 0, supplierCount: 0,
  monthPurchaseAmount: 0, monthInboundQty: 0, monthOutboundQty: 0,
  lowStockCount: 0, stockMaterialKinds: 0
})
const inout = ref({ start: '', end: '', inboundQty: 0, outboundQty: 0, byBizType: [] })
const purchaseMonthly = ref([])
const inoutMonthly = ref([])
const categoryStock = ref([])
const warehouseStock = ref([])
const supplierRank = ref([])
const deptUse = ref([])
const pendingApprovals = ref(0)

const bizRows = computed(() =>
  (inout.value.byBizType || [])
    .filter(r => r.bizType && r.bizType !== 'BALANCE_FIX')
    .map(r => ({
      bizType: r.bizType,
      qty: Math.abs(Number(r.totalChangeQty ?? r.qty) || 0),
      amount: Number(r.amount ?? r.taxInclAmount ?? 0),
      recordCount: Number(r.recordCount || 0)
    }))
    .filter(r => r.qty > 0)
    .sort((a, b) => b.qty - a.qty)
    .slice(0, 8)
)

const kpis = computed(() => [
  {
    label: '物资种类', value: fmt(dash.value.materialKinds), icon: '物',
    color: '#2f6bff', hint: `在库 ${fmt(dash.value.stockMaterialKinds)} 种`
  },
  {
    label: '库存金额', value: money(dash.value.stockAmount), icon: '库',
    color: '#0ea5a4', hint: `总量 ${fmt(dash.value.stockQty)}`
  },
  {
    label: '本月采购', value: money(dash.value.monthPurchaseAmount), icon: '购',
    color: '#f59e0b', hint: '订单含税金额'
  },
  {
    label: '本月入/出', value: `${fmt(dash.value.monthInboundQty)} / ${fmt(dash.value.monthOutboundQty)}`,
    icon: '流', color: '#8b5cf6', hint: '入库量 / 出库量'
  },
  {
    label: '低库存预警', value: fmt(dash.value.lowStockCount), icon: '警',
    color: dash.value.lowStockCount > 0 ? '#ef4444' : '#64748b',
    hint: dash.value.lowStockCount > 0 ? '需尽快补货' : '库存充足'
  },
  {
    label: '待我审批', value: fmt(pendingApprovals.value), icon: '审',
    color: pendingApprovals.value > 0 ? '#f97316' : '#64748b',
    hint: pendingApprovals.value > 0 ? '有待办事项' : '暂无待办'
  }
])

const charts = []
const els = {
  inout: ref(null),
  biz: ref(null),
  cat: ref(null),
  wh: ref(null),
  purchase: ref(null),
  supplier: ref(null),
  dept: ref(null)
}
const { inout: inoutEl, biz: bizEl, cat: catEl, wh: whEl, purchase: purchaseEl, supplier: supplierEl, dept: deptEl } = els

const COLORS = ['#2f6bff', '#22c55e', '#f59e0b', '#a855f7', '#06b6d4', '#f43f5e', '#84cc16', '#64748b']

function mount(id, option) {
  const el = els[id].value
  if (!el) return
  let chart = echarts.getInstanceByDom(el)
  if (!chart) {
    chart = echarts.init(el)
    charts.push(chart)
  }
  chart.setOption(option, true)
}

function baseTooltip() {
  return {
    trigger: 'axis',
    confine: true,
    appendToBody: false,
    textStyle: { fontSize: 12 }
  }
}

function fmt(v) {
  if (v === null || v === undefined || v === '') return '0'
  const n = Number(v)
  if (Number.isNaN(n)) return String(v)
  return n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function money(v) {
  const n = Number(v) || 0
  return '¥' + n.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

function bizLabel(t) {
  const map = {
    IN: '入库', OUT: '出库', USE_ISSUE: '领用发料', USE_RETURN: '退库',
    TRANSFER_IN: '调拨入', TRANSFER_OUT: '调拨出', SCRAP: '报废',
    BORROW_LEND: '借出', BORROW_RETURN: '归还', RETURN_PURCHASE: '采购退货',
    INBOUND: '采购入库', OUTBOUND: '出库'
  }
  return map[t] || t
}

function drawInout() {
  const months = inoutMonthly.value.map(r => String(r.month).slice(2))
  mount('inout', {
    color: ['#22c55e', '#f97316'],
    tooltip: baseTooltip(),
    legend: { top: 0, right: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10 },
    grid: { left: 48, right: 16, top: 36, bottom: 28 },
    xAxis: { type: 'category', data: months, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } }, axisLabel: { fontSize: 11 } },
    series: [
      { name: '入库', type: 'line', smooth: true, symbolSize: 6, areaStyle: { opacity: 0.12 }, data: inoutMonthly.value.map(r => Number(r.inQty) || 0) },
      { name: '出库', type: 'line', smooth: true, symbolSize: 6, areaStyle: { opacity: 0.12 }, data: inoutMonthly.value.map(r => Number(r.outQty) || 0) }
    ]
  })
}

function drawBiz() {
  const rows = bizRows.value
  mount('biz', {
    color: COLORS,
    tooltip: { trigger: 'item', confine: true, formatter: p => `${p.name}<br/>${fmt(p.value)}` },
    legend: { type: 'scroll', bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '44%'],
      avoidLabelOverlap: true,
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{c}', fontSize: 11, overflow: 'break', width: 80 },
      labelLine: { length: 8, length2: 6 },
      data: rows.map(r => ({ name: bizLabel(r.bizType), value: Number(r.qty) || 0 }))
    }]
  })
}

function drawCategory() {
  const rows = categoryStock.value.slice(0, 6)
  mount('cat', {
    color: COLORS,
    tooltip: {
      trigger: 'item', confine: true,
      formatter: p => `${p.name}<br/>金额 ${fmt(p.value)}（${p.percent}%）`
    },
    legend: { type: 'scroll', bottom: 0, icon: 'circle', itemWidth: 8, itemHeight: 8, textStyle: { fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['40%', '66%'],
      center: ['50%', '42%'],
      itemStyle: { borderColor: '#fff', borderWidth: 2 },
      label: { show: true, formatter: '{b}\n{d}%', fontSize: 11 },
      data: rows.map(r => ({ name: r.name, value: Number(r.amount) || 0 }))
    }]
  })
}

function drawWarehouse() {
  const rows = warehouseStock.value.slice(0, 8)
  mount('wh', {
    tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'shadow' } },
    grid: { left: 72, right: 24, top: 16, bottom: 24 },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } }, axisLabel: { fontSize: 11 } },
    yAxis: {
      type: 'category',
      data: rows.map(r => r.name).reverse(),
      axisLabel: { fontSize: 11, width: 64, overflow: 'truncate' }
    },
    series: [{
      type: 'bar',
      barMaxWidth: 16,
      itemStyle: { color: '#0ea5a4', borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', fontSize: 11, formatter: p => fmt(p.value) },
      data: rows.map(r => Number(r.amount) || 0).reverse()
    }]
  })
}

function drawPurchase() {
  const months = purchaseMonthly.value.map(r => String(r.month).slice(2))
  mount('purchase', {
    tooltip: {
      ...baseTooltip(),
      valueFormatter: v => money(v)
    },
    grid: { left: 64, right: 16, top: 24, bottom: 28 },
    xAxis: { type: 'category', data: months, axisLabel: { fontSize: 11 } },
    yAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } }, axisLabel: { fontSize: 11, formatter: v => fmt(v) } },
    series: [{
      name: '采购金额',
      type: 'bar',
      barMaxWidth: 28,
      itemStyle: { color: '#2f6bff', borderRadius: [4, 4, 0, 0] },
      data: purchaseMonthly.value.map(r => Number(r.amount) || 0)
    }]
  })
}

function drawSupplier() {
  const rows = supplierRank.value.slice(0, 8)
  mount('supplier', {
    tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 72, top: 8, bottom: 8, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } }, axisLabel: { fontSize: 10 } },
    yAxis: {
      type: 'category',
      data: rows.map(r => r.name).reverse(),
      axisLabel: { fontSize: 11, width: 90, overflow: 'truncate' }
    },
    series: [{
      type: 'bar',
      barMaxWidth: 14,
      itemStyle: { color: '#2f6bff', borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', fontSize: 11, formatter: p => fmt(p.value) },
      data: rows.map(r => Number(r.amount) || 0).reverse()
    }]
  })
}

function drawDept() {
  const rows = deptUse.value.slice(0, 8)
  mount('dept', {
    tooltip: { trigger: 'axis', confine: true, axisPointer: { type: 'shadow' } },
    grid: { left: 8, right: 64, top: 8, bottom: 8, containLabel: true },
    xAxis: { type: 'value', splitLine: { lineStyle: { color: '#eef2f7' } }, axisLabel: { fontSize: 10 } },
    yAxis: {
      type: 'category',
      data: rows.map(r => r.name).reverse(),
      axisLabel: { fontSize: 11, width: 80, overflow: 'truncate' }
    },
    series: [{
      type: 'bar',
      barMaxWidth: 14,
      itemStyle: { color: '#a855f7', borderRadius: [0, 4, 4, 0] },
      label: { show: true, position: 'right', fontSize: 11, formatter: p => fmt(p.value) },
      data: rows.map(r => Number(r.qty) || 0).reverse()
    }]
  })
}

function drawAll() {
  drawInout()
  drawBiz()
  drawCategory()
  drawWarehouse()
  drawPurchase()
  drawSupplier()
  drawDept()
}

function onResize() {
  charts.forEach(c => c.resize())
}

function asList(v) {
  if (Array.isArray(v)) return v
  if (v && typeof v === 'object') return [v]
  return []
}

onMounted(async () => {
  const jobs = [
    api.get('/stats/dashboard').catch((e) => { console.error('dashboard', e); return { data: {} } }),
    api.get('/stats/inout').catch((e) => { console.error('inout', e); return { data: {} } }),
    api.get('/reports/purchase-monthly', { params: { months: 6 } }).catch((e) => { console.error('purchase-monthly', e); return { data: [] } }),
    api.get('/reports/inout-monthly', { params: { months: 6 } }).catch((e) => { console.error('inout-monthly', e); return { data: [] } }),
    api.get('/reports/category-stock').catch((e) => { console.error('category-stock', e); return { data: [] } }),
    api.get('/reports/warehouse-stock').catch((e) => { console.error('warehouse-stock', e); return { data: [] } }),
    api.get('/reports/supplier-rank', { params: { limit: 8 } }).catch((e) => { console.error('supplier-rank', e); return { data: [] } }),
    api.get('/reports/dept-use', { params: { limit: 8 } }).catch((e) => { console.error('dept-use', e); return { data: [] } }),
    api.get('/approvals/todo', { params: { page: 1, size: 1 } }).catch(() => ({ data: { total: 0 } }))
  ]
  const [d, io, p, im, c, w, s, dt, ap] = await Promise.all(jobs)
  dash.value = { ...dash.value, ...(d?.data || {}) }
  inout.value = { ...inout.value, ...(io?.data || {}) }
  purchaseMonthly.value = asList(p?.data)
  inoutMonthly.value = asList(im?.data)
  categoryStock.value = asList(c?.data)
  warehouseStock.value = asList(w?.data)
  supplierRank.value = asList(s?.data)
  deptUse.value = asList(dt?.data)
  pendingApprovals.value = ap?.data?.total || 0
  // 等 DOM 就绪
  requestAnimationFrame(() => {
    drawAll()
    window.addEventListener('resize', onResize)
  })
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach(c => c.dispose())
  charts.length = 0
})
</script>

<style scoped>
.stats-page { display: flex; flex-direction: column; gap: 16px; }

.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}
@media (max-width: 1400px) { .kpi-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); } }
@media (max-width: 900px) { .kpi-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }

.kpi-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 12px;
  padding: 14px 14px;
  min-height: 88px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}
.kpi-ico {
  width: 42px;
  height: 42px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  color: #fff;
  font-weight: 700;
  font-size: 16px;
  background: var(--c, #2f6bff);
  flex-shrink: 0;
}
.kpi-body { min-width: 0; }
.kpi-label { font-size: 12px; color: #64748b; line-height: 1.2; }
.kpi-value {
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.25;
  margin-top: 4px;
  word-break: break-all;
}
.kpi-hint { font-size: 11px; color: #94a3b8; margin-top: 2px; }

.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.span-2 { grid-column: span 2; }
@media (max-width: 960px) {
  .chart-grid { grid-template-columns: 1fr; }
  .span-2 { grid-column: span 1; }
}

.panel {
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 12px;
  padding: 14px 16px 16px;
  min-width: 0;
  overflow: hidden;
}
.panel-hd {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  flex-wrap: wrap;
}
.panel-hd h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}
.panel-sub { font-size: 12px; color: #94a3b8; }

.chart-box {
  width: 100%;
  height: 260px;
}
.chart-tall { height: 300px; }

.mini-table { margin-top: 4px; }
.mini-row {
  display: grid;
  grid-template-columns: 1fr auto auto;
  gap: 12px;
  align-items: center;
  padding: 6px 0;
  border-top: 1px dashed #eef2f7;
  font-size: 12px;
}
.mini-row .tag { color: #475569; }
.mini-row .num { color: #0f172a; font-weight: 600; min-width: 48px; text-align: right; }
.mini-row .money { color: #64748b; min-width: 72px; text-align: right; }
</style>

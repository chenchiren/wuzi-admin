<template>
  <div class="home" v-loading="loading">
    <!-- 问候 -->
    <section class="hero">
      <div class="hero-main">
        <div class="hero-badge">
          <BrandLogo :size="40" bg="#2F6BFF" :radius="12" />
        </div>
        <div class="hero-text">
          <h2 class="hero-title">{{ greeting }}，{{ userName }}</h2>
          <p class="hero-sub">{{ todayText }} · 物资全链路管理工作台</p>
        </div>
      </div>
      <div class="hero-actions">
        <n-button type="primary" @click="go('/purchase-applies')">新建采购申请</n-button>
        <n-button quaternary @click="go('/approvals')">处理待办</n-button>
      </div>
    </section>

    <!-- KPI -->
    <section class="kpi-grid">
      <article v-for="k in kpis" :key="k.key" class="kpi-card">
        <div class="kpi-ico" :style="{ color: k.color, background: k.bg }">
          <AppIcon :name="k.icon" :size="20" />
        </div>
        <div class="kpi-body">
          <div class="kpi-label">{{ k.label }}</div>
          <div class="kpi-value">{{ k.value }}</div>
          <div class="kpi-hint">{{ k.hint }}</div>
        </div>
      </article>
    </section>

    <!-- 待办 + 快捷 -->
    <section class="mid-grid">
      <article class="panel">
        <header class="panel-hd">
          <h3>我的待办</h3>
          <span class="panel-sub">点击直达</span>
        </header>
        <div class="todo-list">
          <button
            v-for="t in todos"
            :key="t.key"
            class="todo-item"
            :class="{ 'has-task': t.count > 0 }"
            type="button"
            @click="t.path && go(t.path)"
          >
            <span class="todo-ico" :style="{ background: t.bg, color: t.color }">
              <AppIcon :name="t.icon" :size="18" />
            </span>
            <span class="todo-meta">
              <span class="todo-label">{{ t.label }}</span>
              <span class="todo-desc">{{ t.desc }}</span>
            </span>
            <span class="todo-count" :class="{ zero: !t.count }">{{ t.count }}</span>
          </button>
        </div>
      </article>

      <article class="panel">
        <header class="panel-hd">
          <h3>快捷入口</h3>
          <span class="panel-sub">常用作业</span>
        </header>
        <div class="quick-grid">
          <button
            v-for="q in quickLinks"
            :key="q.path"
            type="button"
            class="quick-item"
            @click="go(q.path)"
          >
            <span class="quick-ico"><AppIcon :name="q.icon" :size="20" /></span>
            <span class="quick-label">{{ q.label }}</span>
          </button>
        </div>
      </article>
    </section>

    <!-- 图表 -->
    <section class="chart-grid">
      <article class="panel span-2">
        <header class="panel-hd">
          <h3>近 6 个月入出库</h3>
          <span class="panel-sub">周转节奏</span>
        </header>
        <div ref="inoutChart" class="chart-box"></div>
      </article>
      <article class="panel span-2">
        <header class="panel-hd">
          <h3>采购金额走势</h3>
          <span class="panel-sub">订单含税金额</span>
        </header>
        <div ref="purchaseChart" class="chart-box"></div>
      </article>
    </section>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import * as echarts from 'echarts'
import api from '../api/request'
import BrandLogo from '../components/BrandLogo.vue'
import { AppIcon } from '../icons'

const router = useRouter()
const loading = ref(true)
const dash = ref({
  materialKinds: 0,
  stockQty: 0,
  stockAmount: 0,
  supplierCount: 0,
  monthPurchaseAmount: 0,
  monthInboundQty: 0,
  monthOutboundQty: 0,
  lowStockCount: 0,
  stockMaterialKinds: 0
})
const todoCounts = ref({ approval: 0, unread: 0, overdueBorrow: 0, lowStock: 0, alert: 0 })
const purchaseChart = ref(null)
const inoutChart = ref(null)
const charts = []

const user = computed(() => {
  try {
    return JSON.parse(localStorage.getItem('user') || '{}')
  } catch {
    return {}
  }
})
const userName = computed(() => user.value.realName || user.value.username || '同事')

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 6) return '夜深了'
  if (h < 12) return '早上好'
  if (h < 14) return '中午好'
  if (h < 18) return '下午好'
  return '晚上好'
})

const todayText = computed(() => {
  const d = new Date()
  const week = ['日', '一', '二', '三', '四', '五', '六'][d.getDay()]
  return `${d.getFullYear()} 年 ${d.getMonth() + 1} 月 ${d.getDate()} 日 · 星期${week}`
})

function fmt(n) {
  const v = Number(n) || 0
  return v.toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}
function money(n) {
  return '¥' + (Number(n) || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 })
}

const kpis = computed(() => [
  {
    key: 'kinds',
    label: '物资种类',
    value: fmt(dash.value.materialKinds),
    hint: `在库 ${fmt(dash.value.stockMaterialKinds)} 种`,
    icon: 'package',
    color: '#2f6bff',
    bg: 'rgba(47,107,255,0.12)'
  },
  {
    key: 'stock',
    label: '库存总量',
    value: fmt(dash.value.stockQty),
    hint: '各仓合计数量',
    icon: 'boxes',
    color: '#0ea5a4',
    bg: 'rgba(14,165,164,0.12)'
  },
  {
    key: 'amount',
    label: '库存金额',
    value: money(dash.value.stockAmount),
    hint: '含税成本',
    icon: 'chart',
    color: '#8b5cf6',
    bg: 'rgba(139,92,246,0.12)'
  },
  {
    key: 'purchase',
    label: '本月采购',
    value: money(dash.value.monthPurchaseAmount),
    hint: '订单含税金额',
    icon: 'cart',
    color: '#f59e0b',
    bg: 'rgba(245,158,11,0.12)'
  },
  {
    key: 'flow',
    label: '本月入 / 出',
    value: `${fmt(dash.value.monthInboundQty)} / ${fmt(dash.value.monthOutboundQty)}`,
    hint: '入库量 / 出库量',
    icon: 'transfer',
    color: '#06b6d4',
    bg: 'rgba(6,182,212,0.12)'
  },
  {
    key: 'alert',
    label: '低库存',
    value: fmt(dash.value.lowStockCount),
    hint: dash.value.lowStockCount > 0 ? '建议尽快补货' : '库存充足',
    icon: 'alert',
    color: dash.value.lowStockCount > 0 ? '#ef4444' : '#64748b',
    bg: dash.value.lowStockCount > 0 ? 'rgba(239,68,68,0.12)' : 'rgba(100,116,139,0.12)'
  }
])

const todos = computed(() => [
  {
    key: 'approval',
    label: '待我审批',
    desc: '采购 / 领用 / 调拨等',
    count: todoCounts.value.approval,
    path: '/approvals',
    icon: 'stamp',
    color: '#f97316',
    bg: 'rgba(249,115,22,0.12)'
  },
  {
    key: 'unread',
    label: '未读消息',
    desc: '审批与系统通知',
    count: todoCounts.value.unread,
    path: '',
    icon: 'history',
    color: '#2f6bff',
    bg: 'rgba(47,107,255,0.12)'
  },
  {
    key: 'borrow',
    label: '逾期借用',
    desc: '需催还或归还',
    count: todoCounts.value.overdueBorrow,
    path: '/borrows',
    icon: 'clock',
    color: '#a855f7',
    bg: 'rgba(168,85,247,0.12)'
  },
  {
    key: 'low',
    label: '低库存物资',
    desc: '低于安全库存',
    count: todoCounts.value.lowStock,
    path: '/alerts',
    icon: 'alert',
    color: '#ef4444',
    bg: 'rgba(239,68,68,0.12)'
  },
  {
    key: 'alert',
    label: '库存预警',
    desc: '低库存 + 超库存',
    count: todoCounts.value.alert,
    path: '/alerts',
    icon: 'boxes',
    color: '#f59e0b',
    bg: 'rgba(245,158,11,0.12)'
  }
])

const quickLinks = [
  { label: '采购申请', path: '/purchase-applies', icon: 'file-text' },
  { label: '采购订单', path: '/purchase-orders', icon: 'receipt' },
  { label: '到货验收', path: '/arrivals', icon: 'package-check' },
  { label: '入库', path: '/inbounds', icon: 'arrow-in' },
  { label: '出库', path: '/outbounds', icon: 'arrow-out' },
  { label: '库存查询', path: '/stock', icon: 'boxes' },
  { label: '领用', path: '/material-use', icon: 'hand' },
  { label: '调拨', path: '/transfers', icon: 'transfer' },
  { label: '盘点', path: '/inventory-checks', icon: 'clipboard' },
  { label: '二维码', path: '/trace', icon: 'qr-code' },
  { label: '统计看板', path: '/stats', icon: 'gauge' },
  { label: '高级报表', path: '/reports', icon: 'file-chart' }
]

function go(path) {
  if (path) router.push(path)
}

function asList(v) {
  if (Array.isArray(v)) return v
  if (v && typeof v === 'object') return [v]
  return []
}

function initChart(el) {
  if (!el) return null
  let c = echarts.getInstanceByDom(el)
  if (!c) {
    c = echarts.init(el)
    charts.push(c)
  }
  return c
}

async function loadCharts() {
  await nextTick()
  try {
    const [purchase, inout] = await Promise.all([
      api.get('/reports/purchase-monthly', { params: { months: 6 } }).catch(() => ({ data: [] })),
      api.get('/reports/inout-monthly', { params: { months: 6 } }).catch(() => ({ data: [] }))
    ])
    const p = asList(purchase?.data)
    const io = asList(inout?.data)

    const c1 = initChart(purchaseChart.value)
    if (c1) {
      c1.setOption(
        {
          color: ['#2f6bff'],
          tooltip: { trigger: 'axis', confine: true, valueFormatter: (v) => money(v) },
          grid: { left: 56, right: 16, top: 24, bottom: 28 },
          xAxis: {
            type: 'category',
            data: p.map((x) => String(x.month || '').slice(2)),
            axisLabel: { fontSize: 11 },
            axisLine: { lineStyle: { color: '#e2e8f0' } }
          },
          yAxis: {
            type: 'value',
            splitLine: { lineStyle: { color: '#eef2f7' } },
            axisLabel: { fontSize: 11 }
          },
          series: [
            {
              name: '采购金额',
              type: 'bar',
              barMaxWidth: 28,
              itemStyle: { borderRadius: [6, 6, 0, 0] },
              data: p.map((x) => Number(x.amount) || 0)
            }
          ]
        },
        true
      )
    }

    const c2 = initChart(inoutChart.value)
    if (c2) {
      c2.setOption(
        {
          color: ['#22c55e', '#f97316'],
          tooltip: { trigger: 'axis', confine: true },
          legend: { top: 0, right: 0, icon: 'roundRect', itemWidth: 10, itemHeight: 10 },
          grid: { left: 48, right: 16, top: 36, bottom: 28 },
          xAxis: {
            type: 'category',
            data: io.map((x) => String(x.month || '').slice(2)),
            axisLabel: { fontSize: 11 },
            axisLine: { lineStyle: { color: '#e2e8f0' } }
          },
          yAxis: {
            type: 'value',
            splitLine: { lineStyle: { color: '#eef2f7' } },
            axisLabel: { fontSize: 11 }
          },
          series: [
            {
              name: '入库',
              type: 'line',
              smooth: true,
              symbolSize: 6,
              areaStyle: { opacity: 0.12 },
              data: io.map((x) => Number(x.inQty) || 0)
            },
            {
              name: '出库',
              type: 'line',
              smooth: true,
              symbolSize: 6,
              areaStyle: { opacity: 0.12 },
              data: io.map((x) => Number(x.outQty) || 0)
            }
          ]
        },
        true
      )
    }
  } catch {
    /* 图表失败不阻断工作台 */
  }
}

function onResize() {
  charts.forEach((c) => c.resize())
}

onMounted(async () => {
  loading.value = true
  try {
    const [stats, todo, unread, overdue, alerts] = await Promise.allSettled([
      api.get('/stats/dashboard'),
      api.get('/approvals/todo', { params: { page: 1, size: 1 } }),
      api.get('/messages/unread-count'),
      api.get('/borrows/overdue', { params: { page: 1, size: 1 } }),
      api.get('/stock/alerts')
    ])
    if (stats.status === 'fulfilled') dash.value = { ...dash.value, ...(stats.value.data || {}) }
    if (todo.status === 'fulfilled') todoCounts.value.approval = todo.value.data?.total || 0
    if (unread.status === 'fulfilled') {
      const u = unread.value.data
      todoCounts.value.unread = u?.count ?? u ?? 0
    }
    if (overdue.status === 'fulfilled') {
      const d = overdue.value.data
      todoCounts.value.overdueBorrow = d?.total ?? (Array.isArray(d) ? d.length : 0)
    }
    if (alerts.status === 'fulfilled') {
      const a = alerts.value.data || {}
      todoCounts.value.lowStock = a.lowCount || 0
      todoCounts.value.alert = (a.lowCount || 0) + (a.overCount || 0)
    }
  } finally {
    loading.value = false
  }
  loadCharts()
  window.addEventListener('resize', onResize)
})

onBeforeUnmount(() => {
  window.removeEventListener('resize', onResize)
  charts.forEach((c) => c.dispose())
  charts.length = 0
})
</script>

<style scoped>
.home {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

/* Hero */
.hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 20px 22px;
  border-radius: 16px;
  background:
    radial-gradient(1200px 200px at 10% 0%, rgba(47, 107, 255, 0.14), transparent 55%),
    linear-gradient(135deg, #ffffff 0%, #f3f7ff 100%);
  border: 1px solid #e6ecf8;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}
.hero-main {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}
.hero-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.25;
}
.hero-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #64748b;
}
.hero-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

/* KPI */
.kpi-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}
@media (max-width: 1400px) {
  .kpi-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 900px) {
  .kpi-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
.kpi-card {
  display: flex;
  align-items: center;
  gap: 12px;
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 14px;
  padding: 14px;
  min-height: 92px;
  box-shadow: 0 1px 2px rgba(15, 23, 42, 0.03);
}
.kpi-ico {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.kpi-body {
  min-width: 0;
}
.kpi-label {
  font-size: 12px;
  color: #64748b;
}
.kpi-value {
  margin-top: 4px;
  font-size: 22px;
  font-weight: 700;
  color: #0f172a;
  line-height: 1.2;
  word-break: break-all;
}
.kpi-hint {
  margin-top: 2px;
  font-size: 11px;
  color: #94a3b8;
}

/* Mid */
.mid-grid {
  display: grid;
  grid-template-columns: 1.1fr 1fr;
  gap: 16px;
}
@media (max-width: 1100px) {
  .mid-grid {
    grid-template-columns: 1fr;
  }
}
.panel {
  background: #fff;
  border: 1px solid #eef1f6;
  border-radius: 14px;
  padding: 14px 16px 16px;
  min-width: 0;
}
.panel-hd {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}
.panel-hd h3 {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #0f172a;
}
.panel-sub {
  font-size: 12px;
  color: #94a3b8;
}

.todo-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.todo-item {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  border: 1px solid #eef1f6;
  background: #fafbfc;
  border-radius: 12px;
  padding: 10px 12px;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s, background 0.15s, transform 0.15s;
}
.todo-item:hover {
  background: #fff;
  border-color: #c7d7fe;
  transform: translateY(-1px);
}
.todo-item.has-task {
  background: #fff;
}
.todo-ico {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  flex-shrink: 0;
}
.todo-meta {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.todo-label {
  font-size: 14px;
  font-weight: 600;
  color: #0f172a;
}
.todo-desc {
  font-size: 12px;
  color: #94a3b8;
}
.todo-count {
  min-width: 36px;
  text-align: right;
  font-size: 20px;
  font-weight: 700;
  color: #0f172a;
}
.todo-count.zero {
  color: #cbd5e1;
}

.quick-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 10px;
}
@media (max-width: 900px) {
  .quick-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
.quick-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 14px 8px;
  border: 1px solid #eef1f6;
  border-radius: 12px;
  background: #fafbfc;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s, transform 0.15s;
}
.quick-item:hover {
  background: #fff;
  border-color: #c7d7fe;
  transform: translateY(-1px);
}
.quick-ico {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #2f6bff;
  background: rgba(47, 107, 255, 0.1);
}
.quick-label {
  font-size: 12px;
  color: #334155;
  font-weight: 500;
}

/* Charts */
.chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}
.span-2 {
  grid-column: span 1;
}
@media (max-width: 1100px) {
  .chart-grid {
    grid-template-columns: 1fr;
  }
}
.chart-box {
  width: 100%;
  height: 280px;
}
</style>

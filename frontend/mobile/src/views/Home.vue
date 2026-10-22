<template>
  <div class="page">
    <NavBar title="首页" :back="false" />

    <div class="card">
      <div class="row">
        <div>
          <b>{{ greeting }}，{{ user.realName || user.username || '同事' }}</b>
          <div class="muted">{{ (user.roles || []).join(' / ') || '未分配角色' }}</div>
        </div>
        <button class="btn plain" style="min-height: 36px" @click="load">刷新</button>
      </div>
    </div>

    <div class="card">
      <h3>待办</h3>
      <div class="row" style="gap: 10px">
        <div class="stat" @click="go('/approvals')">
          <div class="n">{{ home.todos?.pendingApprovals ?? 0 }}</div>
          <div class="muted">待审批</div>
        </div>
        <div class="stat" @click="go('/arrival')">
          <div class="n">{{ home.todos?.draftArrivals ?? 0 }}</div>
          <div class="muted">待验收</div>
        </div>
        <div class="stat" @click="go('/inbound')">
          <div class="n">{{ home.todos?.draftInbounds ?? 0 }}</div>
          <div class="muted">待入库</div>
        </div>
      </div>
    </div>

    <div class="card">
      <h3>作业宫格</h3>
      <div class="grid">
        <div class="grid-item" @click="go('/arrival')">
          <span class="ico">到</span>
          <span>到货验收</span>
        </div>
        <div class="grid-item" @click="go('/inbound')">
          <span class="ico">入</span>
          <span>按单收货</span>
        </div>
        <div class="grid-item" @click="go('/outbound')">
          <span class="ico">出</span>
          <span>扫码出库</span>
        </div>
        <div class="grid-item" @click="go('/use-apply')">
          <span class="ico">领</span>
          <span>领用申请</span>
        </div>
        <div class="grid-item" @click="go('/approvals')">
          <span class="ico">审</span>
          <span>待我审批</span>
        </div>
        <div class="grid-item" @click="go('/stocks')">
          <span class="ico">库</span>
          <span>库存查询</span>
        </div>
        <div class="grid-item" @click="go('/scan')">
          <span class="ico">扫</span>
          <span>扫码查询</span>
        </div>
        <div class="grid-item" @click="go('/inventory')">
          <span class="ico">盘</span>
          <span>盘点</span>
        </div>
        <div class="grid-item" @click="go('/transfers')">
          <span class="ico">调</span>
          <span>调拨</span>
        </div>
        <div class="grid-item" @click="go('/borrows')">
          <span class="ico">借</span>
          <span>借还</span>
        </div>
        <div class="grid-item" @click="go('/messages')">
          <span class="ico">讯</span>
          <span>消息</span>
        </div>
        <div class="grid-item" @click="go('/trace')">
          <span class="ico">溯</span>
          <span>溯源</span>
        </div>
        <div class="grid-item" @click="go('/me')">
          <span class="ico">我</span>
          <span>我的</span>
        </div>
      </div>
    </div>

    <div class="card">
      <h3>库存预警</h3>
      <div class="row">
        <span class="muted">低库存</span>
        <span class="tag warn">{{ home.alerts?.lowStockCount ?? 0 }}</span>
      </div>
      <div class="row" style="margin-top: 8px">
        <span class="muted">超储</span>
        <span class="tag">{{ home.alerts?.overStockCount ?? 0 }}</span>
      </div>
    </div>

    <div class="card">
      <h3>最近扫码</h3>
      <div v-if="!(home.recentScans || []).length" class="empty">暂无扫码记录</div>
      <div v-for="item in home.recentScans || []" :key="item.id" class="list-item" @click="goScan(item)">
        <div class="row">
          <span>{{ item.qrCode || '—' }}</span>
          <span class="tag" :class="item.result === 'SUCCESS' ? 'ok' : 'danger'">{{ item.result }}</span>
        </div>
        <div class="muted">{{ item.scanType }} · {{ item.channel }} · {{ fmt(item.scanTime) }}</div>
      </div>
    </div>

    <TabBar active="home" />
    <Toast :message="toast" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, getUser } from '../api'
import NavBar from '../components/NavBar.vue'
import TabBar from '../components/TabBar.vue'
import Toast from '../components/Toast.vue'

const router = useRouter()
const user = ref(getUser() || {})
const home = ref({ todos: {}, alerts: {}, recentScans: [] })
const toast = ref('')

const greeting = computed(() => {
  const h = new Date().getHours()
  if (h < 12) return '早上好'
  if (h < 18) return '下午好'
  return '晚上好'
})

function go(path) {
  router.push(path)
}

function goScan(item) {
  router.push({ path: '/scan', query: { code: item.qrCode || '' } })
}

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  try {
    const resp = await api.home()
    home.value = resp.data || home.value
  } catch (e) {
    toast.value = e.message || '加载失败'
  }
}

onMounted(async () => {
  if (!user.value.username) {
    try {
      const me = await api.me()
      user.value = me.data
    } catch {
      /* ignore */
    }
  }
  load()
})
</script>

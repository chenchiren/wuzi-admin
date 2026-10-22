<template>
  <n-config-provider :theme-overrides="themeOverrides" class="h-full">
    <div class="flex h-screen overflow-hidden bg-[#f4f6fa]">
      <!-- Sidebar -->
      <aside class="flex w-64 shrink-0 flex-col bg-[#0f172a]">
        <!-- Brand -->
        <div class="flex h-16 shrink-0 items-center gap-3 px-4">
          <BrandLogo :size="36" bg="#2F6BFF" :radius="10" />
          <div class="min-w-0 leading-tight">
            <div class="truncate text-[15px] font-semibold text-white">物资全链路</div>
            <div class="truncate text-[11px] text-slate-400">Supply Chain Admin</div>
          </div>
        </div>

        <!-- Nav -->
        <nav class="nav-scroll flex-1 overflow-y-auto px-3 pb-4">
          <!-- Home -->
          <button
            class="nav-item"
            :class="{ 'is-active': isActive('/home') }"
            @click="go('/home')"
          >
            <span class="nav-ico"><AppIcon nav-key="home" :size="18" /></span>
            <span class="nav-label">工作台</span>
          </button>

          <section v-for="group in groups" :key="group.key" class="mt-4">
            <button
              class="flex w-full items-center justify-between rounded-md px-3 py-2 text-[13px] font-medium text-slate-300 hover:bg-white/5"
              @click="toggle(group.key)"
            >
              <span class="flex items-center gap-2">
                <span class="nav-ico nav-ico--dim"><AppIcon :nav-key="group.key" :size="18" /></span>
                {{ group.label }}
              </span>
              <span class="text-slate-500 text-xs">{{ openKeys.includes(group.key) ? '▾' : '▸' }}</span>
            </button>

            <div v-show="openKeys.includes(group.key)" class="mt-1 space-y-0.5">
              <button
                v-for="it in group.items"
                :key="it.path"
                class="nav-item nav-item--sub"
                :class="{ 'is-active': isActive(it.path) }"
                @click="go(it.path)"
              >
                <span class="nav-ico nav-ico--sm"><AppIcon :nav-key="it.path" :size="16" :stroke-width="1.7" /></span>
                <span class="nav-label">{{ it.label }}</span>
              </button>
            </div>
          </section>
        </nav>

        <div class="shrink-0 border-t border-white/10 px-4 py-3 text-[11px] text-slate-500">
          v1.0 · wuzi_test
        </div>
      </aside>

      <!-- Main -->
      <div class="flex min-w-0 flex-1 flex-col">
        <header class="flex h-16 shrink-0 items-center justify-between border-b border-slate-200 bg-white px-6">
          <div class="min-w-0">
            <h1 class="m-0 truncate text-[17px] font-semibold text-slate-900">
              {{ route.meta.title || '工作台' }}
            </h1>
            <p class="m-0 mt-0.5 truncate text-xs text-slate-500">{{ breadcrumb }}</p>
          </div>
          <div class="flex shrink-0 items-center gap-3">
            <n-tag size="small" type="info" :bordered="false">{{ primaryRole }}</n-tag>
            <span class="max-w-[120px] truncate text-sm text-slate-700">
              {{ user.realName || user.username }}
            </span>
            <n-button quaternary type="error" size="small" @click="logout">退出</n-button>
          </div>
        </header>

        <main class="flex-1 overflow-auto bg-[#f4f6fa] p-5">
          <router-view />
          <AiAssistant v-perm="'ai:chat'" />
        </main>
      </div>
    </div>
  </n-config-provider>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NConfigProvider, NButton, NTag } from 'naive-ui'
import BrandLogo from '../components/BrandLogo.vue'
import AiAssistant from '../components/ai/AiAssistant.vue'
import { AppIcon } from '../icons'
import { tRole, loadDicts } from '../utils/dict'

const route = useRoute()
const router = useRouter()

// 进入系统确保字典已加载
loadDicts()

const themeOverrides = {
  common: {
    primaryColor: '#2F6BFF',
    primaryColorHover: '#1F56E0',
    primaryColorPressed: '#1A45B8',
    borderRadius: '8px'
  }
}

const user = computed(() => {
  try { return JSON.parse(localStorage.getItem('user') || '{}') } catch { return {} }
})

const primaryRole = computed(() => {
  const roles = user.value.roles || []
  return tRole(roles[0]) || '用户'
})

const groups = [
  {
    key: 'base', label: '基础数据',
    items: [
      { path: '/materials', label: '物资档案' },
      { path: '/categories', label: '物资分类' },
      { path: '/suppliers', label: '供应商' },
      { path: '/supplier-evals', label: '供应商评价' },
      { path: '/units', label: '计量单位' },
      { path: '/projects', label: '项目/成本中心' },
      { path: '/price-trends', label: '价格趋势' },
      { path: '/tax-rates', label: '税率字典' }
    ]
  },
  {
    key: 'po', label: '采购管理',
    items: [
      { path: '/purchase-plans', label: '采购计划' },
      { path: '/purchase-applies', label: '采购申请' },
      { path: '/inquiries', label: '询价' },
      { path: '/purchase-orders', label: '采购订单' },
      { path: '/contracts', label: '合同管理' },
      { path: '/purchase-returns', label: '采购退货' },
      { path: '/approvals', label: '审批待办' },
      { path: '/arrivals', label: '到货验收' }
    ]
  },
  {
    key: 'wh', label: '仓储作业',
    items: [
      { path: '/warehouses', label: '仓库库位' },
      { path: '/inbounds', label: '入库' },
      { path: '/outbounds', label: '出库' },
      { path: '/stock', label: '库存' },
      { path: '/stock-ledgers', label: '序列/批次台账' },
      { path: '/transfers', label: '调拨' },
      { path: '/inventory-checks', label: '盘点' }
    ]
  },
  {
    key: 'asset', label: '领用资产',
    items: [
      { path: '/material-use', label: '领用' },
      { path: '/return-stores', label: '退库' },
      { path: '/borrows', label: '借用' },
      { path: '/scraps', label: '报废' },
      { path: '/alerts', label: '库存预警' }
    ]
  },
  {
    key: 'fin', label: '财务结算',
    items: [
      { path: '/finance', label: '发票结算' },
      { path: '/budgets', label: '预算控制' },
      { path: '/payments', label: '付款应付' }
    ]
  },
  {
    key: 'trace', label: '溯源标签',
    items: [
      { path: '/trace', label: '二维码溯源' },
      { path: '/label-print', label: '标签打印' },
      { path: '/label-templates', label: '标签模板' }
    ]
  },
  {
    key: 'report', label: '分析报表',
    items: [
      { path: '/stats', label: '统计看板' },
      { path: '/reports', label: '高级报表' }
    ]
  },
  {
    key: 'sys', label: '系统管理',
    items: [
      { path: '/messages', label: '消息中心' },
      { path: '/flow-configs', label: '审批流配置' },
      { path: '/users', label: '用户' },
      { path: '/positions', label: '职位' },
      { path: '/depts', label: '部门' },
      { path: '/roles', label: '角色权限' },
      { path: '/dicts', label: '字典管理' },
      { path: '/configs', label: '系统配置' },
      { path: '/excel-import', label: 'Excel 导入' },
      { path: '/logs', label: '操作日志' }
    ]
  }
]

function groupKeyOf(path) {
  const p = '/' + (path.split('/')[1] || '')
  const g = groups.find(g => g.items.some(i => i.path === p))
  return g ? g.key : null
}

const openKeys = ref([])
function syncOpen() {
  const k = groupKeyOf(route.path)
  if (k && !openKeys.value.includes(k)) openKeys.value = [...openKeys.value, k]
}
syncOpen()

const breadcrumb = computed(() => {
  const p = '/' + (route.path.split('/')[1] || '')
  if (p === '/home') return '首页'
  for (const g of groups) {
    const hit = g.items.find(i => i.path === p)
    if (hit) return `${g.label} / ${hit.label}`
  }
  return route.meta.title || ''
})

function isActive(path) {
  return route.path === path || route.path.startsWith(path + '/')
}

function toggle(key) {
  if (openKeys.value.includes(key)) {
    openKeys.value = openKeys.value.filter(k => k !== key)
  } else {
    openKeys.value = [...openKeys.value, key]
  }
}

function go(path) {
  const k = groupKeyOf(path)
  if (k && !openKeys.value.includes(k)) openKeys.value = [...openKeys.value, k]
  router.push(path)
}

function logout() {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/login')
}
</script>

<style scoped>
.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 42px;
  padding: 0 12px;
  margin: 2px 0;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: #e2e8f0;
  font-size: 14px;
  font-weight: 500;
  text-align: left;
  cursor: pointer;
}
.nav-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #fff;
}
.nav-item.is-active {
  background: #2f6bff;
  color: #fff;
  box-shadow: 0 4px 12px rgba(47, 107, 255, 0.35);
}
.nav-item--sub {
  min-height: 38px;
  padding-left: 22px;
  font-weight: 400;
}
.nav-ico {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 8px;
  background: rgba(255, 255, 255, 0.12);
  color: #e2e8f0;
  flex-shrink: 0;
}
.nav-ico--dim {
  background: rgba(255, 255, 255, 0.08);
  color: #94a3b8;
}
.nav-ico--sm {
  width: 26px;
  height: 26px;
  background: rgba(255, 255, 255, 0.08);
}
.nav-item.is-active .nav-ico {
  background: rgba(255, 255, 255, 0.22);
  color: #fff;
}
.nav-item:hover .nav-ico {
  color: #fff;
}
.nav-label {
  color: inherit;
}

/* Sidebar scrollbar — dark rail matching #0f172a */
.nav-scroll {
  scrollbar-width: thin;
  scrollbar-color: rgba(255, 255, 255, 0.18) transparent;
}
.nav-scroll::-webkit-scrollbar {
  width: 6px;
}
.nav-scroll::-webkit-scrollbar-track {
  background: transparent;
}
.nav-scroll::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.16);
  border-radius: 999px;
}
.nav-scroll::-webkit-scrollbar-thumb:hover {
  background: rgba(255, 255, 255, 0.28);
}
.nav-scroll::-webkit-scrollbar-corner {
  background: transparent;
}
</style>

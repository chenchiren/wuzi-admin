import { createRouter, createWebHashHistory } from 'vue-router'
import { isLoggedIn } from '../api'

const routes = [
  { path: '/login', name: 'login', component: () => import('../views/Login.vue'), meta: { public: true } },
  { path: '/', name: 'home', component: () => import('../views/Home.vue') },
  { path: '/scan', name: 'scan', component: () => import('../views/Scan.vue') },
  { path: '/inbound', name: 'inbound', component: () => import('../views/Inbound.vue') },
  { path: '/outbound', name: 'outbound', component: () => import('../views/Outbound.vue') },
  { path: '/arrival', name: 'arrival', component: () => import('../views/Arrival.vue') },
  { path: '/use-apply', name: 'useApply', component: () => import('../views/UseApply.vue') },
  { path: '/approvals', name: 'approvals', component: () => import('../views/Approvals.vue') },
  { path: '/stocks', name: 'stocks', component: () => import('../views/Stocks.vue') },
  { path: '/inventory', name: 'inventory', component: () => import('../views/Inventory.vue') },
  { path: '/transfers', name: 'transfers', component: () => import('../views/Transfers.vue') },
  { path: '/borrows', name: 'borrows', component: () => import('../views/Borrows.vue') },
  { path: '/messages', name: 'messages', component: () => import('../views/Messages.vue') },
  { path: '/trace', name: 'trace', component: () => import('../views/Trace.vue') },
  { path: '/me', name: 'me', component: () => import('../views/Me.vue') },
  { path: '/:pathMatch(.*)*', redirect: '/' }
]

const router = createRouter({
  history: createWebHashHistory(),
  routes
})

router.beforeEach((to) => {
  if (to.meta.public) return true
  if (!isLoggedIn()) return { name: 'login', query: { redirect: to.fullPath } }
  return true
})

export default router

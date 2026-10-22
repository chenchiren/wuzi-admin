import { createRouter, createWebHashHistory } from 'vue-router'
import Login from '../views/Login.vue'
import Layout from '../views/Layout.vue'
import Home from '../views/Home.vue'
import Materials from '../views/Materials.vue'
import Suppliers from '../views/Suppliers.vue'
import PurchaseOrders from '../views/PurchaseOrders.vue'
import TaxRates from '../views/TaxRates.vue'
import TraceQr from '../views/TraceQr.vue'
import Categories from '../views/Categories.vue'
import PurchaseApplies from '../views/PurchaseApplies.vue'
import Inquiries from '../views/Inquiries.vue'
import PurchaseReturns from '../views/PurchaseReturns.vue'
import Approvals from '../views/Approvals.vue'
import Arrivals from '../views/Arrivals.vue'
import Inbounds from '../views/Inbounds.vue'
import Outbounds from '../views/Outbounds.vue'
import Stock from '../views/Stock.vue'
import MaterialUse from '../views/MaterialUse.vue'
import ReturnStores from '../views/ReturnStores.vue'
import Transfers from '../views/Transfers.vue'
import InventoryChecks from '../views/InventoryChecks.vue'
import Borrows from '../views/Borrows.vue'
import Scraps from '../views/Scraps.vue'
import Alerts from '../views/Alerts.vue'
import Warehouses from '../views/Warehouses.vue'
import Finance from '../views/Finance.vue'
import PriceTrends from '../views/PriceTrends.vue'
import Messages from '../views/Messages.vue'
import FlowConfigs from '../views/FlowConfigs.vue'
import Users from '../views/Users.vue'
import Positions from '../views/Positions.vue'
import Depts from '../views/Depts.vue'
import Roles from '../views/Roles.vue'
import ExcelImport from '../views/ExcelImport.vue'
import Logs from '../views/Logs.vue'
import Stats from '../views/Stats.vue'
import Reports from '../views/Reports.vue'
import LabelPrint from '../views/LabelPrint.vue'
import Dicts from '../views/Dicts.vue'
import PurchasePlans from '../views/PurchasePlans.vue'
import Budgets from '../views/Budgets.vue'
import Contracts from '../views/Contracts.vue'
import Payments from '../views/Payments.vue'
import Configs from '../views/Configs.vue'
import Units from '../views/Units.vue'
import Projects from '../views/Projects.vue'
import StockLedgers from '../views/StockLedgers.vue'
import LabelTemplates from '../views/LabelTemplates.vue'
import PublicTrace from '../views/PublicTrace.vue'
import SupplierEvals from '../views/SupplierEvals.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/login', component: Login },
    { path: '/public-trace', component: PublicTrace, meta: { title: '物资溯源' } },
    {
      path: '/',
      component: Layout,
      redirect: '/home',
      children: [
        { path: 'home', component: Home, meta: { title: '工作台' } },
        { path: 'materials', component: Materials, meta: { title: '物资档案' } },
        { path: 'categories', component: Categories, meta: { title: '物资分类' } },
        { path: 'suppliers', component: Suppliers, meta: { title: '供应商' } },
        { path: 'price-trends', component: PriceTrends, meta: { title: '价格趋势' } },
        { path: 'purchase-plans', component: PurchasePlans, meta: { title: '采购计划' } },
        { path: 'purchase-applies', component: PurchaseApplies, meta: { title: '采购申请' } },
        { path: 'inquiries', component: Inquiries, meta: { title: '询价' } },
        { path: 'purchase-orders', component: PurchaseOrders, meta: { title: '采购订单' } },
        { path: 'purchase-returns', component: PurchaseReturns, meta: { title: '采购退货' } },
        { path: 'approvals', component: Approvals, meta: { title: '审批待办' } },
        { path: 'arrivals', component: Arrivals, meta: { title: '到货' } },
        { path: 'inbounds', component: Inbounds, meta: { title: '入库' } },
        { path: 'outbounds', component: Outbounds, meta: { title: '出库' } },
        { path: 'stock', component: Stock, meta: { title: '库存' } },
        { path: 'warehouses', component: Warehouses, meta: { title: '仓库库位' } },
        { path: 'material-use', component: MaterialUse, meta: { title: '领用' } },
        { path: 'return-stores', component: ReturnStores, meta: { title: '退库' } },
        { path: 'transfers', component: Transfers, meta: { title: '调拨' } },
        { path: 'inventory-checks', component: InventoryChecks, meta: { title: '盘点' } },
        { path: 'borrows', component: Borrows, meta: { title: '借用' } },
        { path: 'scraps', component: Scraps, meta: { title: '报废' } },
        { path: 'alerts', component: Alerts, meta: { title: '预警' } },
        { path: 'finance', component: Finance, meta: { title: '发票结算' } },
        { path: 'budgets', component: Budgets, meta: { title: '预算控制' } },
        { path: 'contracts', component: Contracts, meta: { title: '合同管理' } },
        { path: 'payments', component: Payments, meta: { title: '付款应付' } },
        { path: 'messages', component: Messages, meta: { title: '消息中心' } },
        { path: 'flow-configs', component: FlowConfigs, meta: { title: '审批流配置' } },
        { path: 'users', component: Users, meta: { title: '用户' } },
        { path: 'positions', component: Positions, meta: { title: '职位' } },
        { path: 'depts', component: Depts, meta: { title: '部门' } },
        { path: 'roles', component: Roles, meta: { title: '角色权限' } },
        { path: 'excel-import', component: ExcelImport, meta: { title: 'Excel导入' } },
        { path: 'logs', component: Logs, meta: { title: '操作日志' } },
        { path: 'dicts', component: Dicts, meta: { title: '字典管理' } },
        { path: 'configs', component: Configs, meta: { title: '系统配置' } },
        { path: 'stats', component: Stats, meta: { title: '统计' } },
        { path: 'reports', component: Reports, meta: { title: '高级报表' } },
        { path: 'label-print', component: LabelPrint, meta: { title: '标签打印' } },
        { path: 'tax-rates', component: TaxRates, meta: { title: '税率字典' } },
        { path: 'units', component: Units, meta: { title: '计量单位' } },
        { path: 'projects', component: Projects, meta: { title: '项目/成本中心' } },
        { path: 'stock-ledgers', component: StockLedgers, meta: { title: '序列号/批次台账' } },
        { path: 'supplier-evals', component: SupplierEvals, meta: { title: '供应商评价' } },
        { path: 'label-templates', component: LabelTemplates, meta: { title: '标签模板' } },
        { path: 'trace', component: TraceQr, meta: { title: '二维码溯源' } }
      ]
    }
  ]
})

const publicPaths = ['/login', '/public-trace']

router.beforeEach((to) => {
  const token = localStorage.getItem('token')
  if (!publicPaths.includes(to.path) && !token) return '/login'
})

export default router

/**
 * 界面字典（按事件类型）。
 * 后端：GET /api/dicts/map → { TYPE_CODE: { key: label } }
 * 例如 t('INBOUND_STATUS','DRAFT') → 草稿
 * 管理页：系统管理 → 字典管理（左事件、右该事件全部项）
 */
import axios from 'axios'

let cache = null
let loading = null

export async function loadDicts(force = false) {
  if (cache && !force) return cache
  if (loading) return loading
  loading = (async () => {
    try {
      const token = localStorage.getItem('token')
      const res = await axios.get('/api/dicts/map', {
        headers: token ? { Authorization: `Bearer ${token}` } : {}
      })
      if (res.data && res.data.code === 200) {
        cache = res.data.data || {}
      }
    } catch {
      cache = cache || {}
    } finally {
      loading = null
    }
    return cache
  })()
  return loading
}

export function reloadDicts() {
  cache = null
  return loadDicts(true)
}

/** 按事件类型取中文；无字典回退原码 */
export function t(type, code) {
  if (code === null || code === undefined || code === '') return '-'
  const key = String(code)
  const hit = cache && cache[type] && cache[type][key]
  return hit || key
}

/** 某事件的全部项：[{ value, label }] —— 用于筛选下拉 */
export function dictOptions(type) {
  const m = (cache && cache[type]) || {}
  return Object.entries(m).map(([value, label]) => ({ value, label }))
}

/** 读某一项 */
export function dictItem(type, code) {
  return t(type, code)
}

// ---- 事件状态（按业务拆分，勿再混用 STATUS）----
export function tInboundStatus(c) { return t('INBOUND_STATUS', c) }
export function tOutboundStatus(c) { return t('OUTBOUND_STATUS', c) }
export function tPurchaseApplyStatus(c) { return t('PURCHASE_APPLY_STATUS', c) }
export function tPurchaseOrderStatus(c) { return t('PURCHASE_ORDER_STATUS', c) }
export function tPlanStatus(c) { return t('PLAN_STATUS', c) }
export function tPlanType(c) { return t('PLAN_TYPE', c) }
export function tContractStatus(c) { return t('CONTRACT_STATUS', c) }
export function tContractType(c) { return t('CONTRACT_TYPE', c) }
export function tBudgetStatus(c) { return t('BUDGET_STATUS', c) }
export function tBudgetAction(c) { return t('BUDGET_ACTION', c) }
export function tPaymentStatus(c) { return t('PAYMENT_STATUS', c) }
export function tPayMethod(c) { return t('PAY_METHOD', c) }
export function tPurchaseReturnStatus(c) { return t('PURCHASE_RETURN_STATUS', c) }
export function tArrivalStatus(c) { return t('PURCHASE_ARRIVAL_STATUS', c) }
export function tMaterialUseStatus(c) { return t('MATERIAL_USE_STATUS', c) }
export function tBorrowStatus(c) { return t('BORROW_STATUS', c) }
export function tScrapStatus(c) { return t('SCRAP_STATUS', c) }
export function tTransferStatus(c) { return t('TRANSFER_STATUS', c) }
export function tInventoryStatus(c) { return t('INVENTORY_STATUS', c) }
export function tReturnStoreStatus(c) { return t('RETURN_STORE_STATUS', c) }
export function tApprovalStatus(c) { return t('APPROVAL_STATUS', c) }
export function tQrStatus(c) { return t('QR_STATUS', c) }
export function tInquiryStatus(c) { return t('PURCHASE_INQUIRY_STATUS', c) || t('STATUS', c) }
export function tInvoiceStatus(c) { return t('INVOICE_STATUS', c) || t('STATUS', c) }
export function tSettleStatus(c) { return t('SETTLE_STATUS', c) || t('STATUS', c) }
export function tMatchStatus(c) { return t('MATCH_STATUS', c) }
export function tAlertStatus(c) { return t('ALERT_STATUS', c) }
export function tAlertType(c) { return t('ALERT_TYPE', c) }
export function tConditionStatus(c) { return t('CONDITION_STATUS', c) }

export function tMsgRead(c) { return t('MSG_READ', c) }
export function tFlowStatus(c) { return t('FLOW_STATUS', c) }
export function tImportStatus(c) { return t('IMPORT_STATUS', c) }

// ---- 兼容：通用状态（旧调用）----
export function tStatus(code) {
  return t('STATUS', code)
}

/**
 * 启用/停用、是/否 等 0/1 开关。
 * 兼容 true/false、'0'/'1'、数字 0/1。
 */
export function tNumStatus(code) {
  if (code === null || code === undefined || code === '') return '-'
  if (code === true || code === 1 || code === '1' || code === 'true') return t('NUM_STATUS', '1')
  if (code === false || code === 0 || code === '0' || code === 'false') return t('NUM_STATUS', '0')
  return t('NUM_STATUS', String(code))
}

/** 是否 */
export function tYesNo(code) {
  return t('YES_NO', code === true || code === 1 || code === '1' ? 'Y' : code === false || code === 0 || code === '0' ? 'N' : code)
}

export function tBiz(code) { return t('BIZ_TYPE', code) }
export function tRole(code) { return t('ROLE', code) }
export function tInboundType(code) { return t('INBOUND_TYPE', code) }
export function tOutboundType(code) { return t('OUTBOUND_TYPE', code) }
export function tQrType(code) { return t('QR_TYPE', code) }
export function tQrGranularity(code) { return t('QR_GRANULARITY', code) }
export function tUseType(code) { return t('USE_TYPE', code) }
export function tBorrowType(code) { return t('BORROW_TYPE', code) }
export function tUrgency(code) { return t('URGENCY', code) }
export function tCoop(code) { return t('COOP_STATUS', code) }
export function tApprovalAction(code) { return t('APPROVAL_ACTION', code) }
export function tLogResult(code) { return t('LOG_RESULT', code) }
export function tTaxCategory(code) { return t('TAX_CATEGORY', code) }
export function tUserSource(code) { return t('USER_SOURCE', code) }
export function tPriceSource(code) { return t('PRICE_SOURCE', code) }
export function tPriceDir(code) { return t('PRICE_TREND_DIR', code) }
export function tApproverType(code) { return t('APPROVER_TYPE', code) || code }
export function tDeptScope(code) { return t('DEPT_SCOPE', code) || code }
export function tDeptType(code) { return t('DEPT_TYPE', code) || code }

/**
 * 筛选/下拉选项。优先字典；字典为空时回退 codes（label 再走 t）。
 * @param {string} type 字典类型
 * @param {string[]} [fallbackCodes]
 */
export function dictOptionsOr(type, fallbackCodes = []) {
  const opts = dictOptions(type)
  if (opts.length) return opts
  return fallbackCodes.map((value) => ({ value, label: t(type, value) }))
}

/**
 * 状态码 → el-tag 颜色。覆盖各业务通用编码。
 * @returns {'success'|'warning'|'danger'|'info'|'primary'}
 */
export function statusTagType(code) {
  if (code === null || code === undefined || code === '') return 'info'
  const k = String(code).toUpperCase()
  if (code === true || k === 'TRUE' || k === '1' || k === 'Y' || k === 'YES') return 'success'
  if (code === false || k === 'FALSE' || k === '0' || k === 'N' || k === 'NO') return 'info'
  const map = {
    SUCCESS: 'success', COMPLETED: 'success', CONFIRMED: 'success', APPROVED: 'success',
    ISSUED: 'success', LENT: 'success', RETURNED: 'success', ACCEPTED: 'success',
    ACTIVE: 'success', MATCHED: 'success', PAID: 'success', SETTLED: 'success',
    HANDLED: 'success', GOOD: 'success', CREATED: 'info', DONE: 'success',
    DRAFT: 'info', CANCELLED: 'info', CLOSED: 'info', VOID: 'info', INACTIVE: 'info',
    READ: 'info', REGISTERED: 'info', NONE: 'info', UNMATCHED: 'info', FLAT: 'info',
    PENDING: 'warning', PENDING_APPROVAL: 'warning', SUBMITTED: 'warning',
    APPLIED: 'warning', PART_RETURNED: 'warning', PART_RECEIVED: 'warning',
    PARTIAL: 'warning', OUT_DONE: 'warning', IN_TRANSIT: 'warning', OUT: 'warning',
    TRANSFERRED: 'warning', OPEN: 'warning', OVERDUE: 'warning', REPAIRABLE: 'warning',
    PAUSED: 'warning', PENDING_CONFIRM: 'warning', DIFF: 'warning', UP: 'danger', DOWN: 'success',
    REJECTED: 'danger', RED_FLUSHED: 'danger', FAILED: 'danger', FAIL: 'danger',
    RESUBMIT: 'warning',
    ERROR: 'danger', DAMAGED: 'danger', SCRAPPED: 'danger', ENDED: 'danger',
    URGENT: 'danger', EXPIRED: 'danger', LOW_STOCK: 'danger', OVER_STOCK: 'warning',
    EXPIRY: 'warning', BORROW_OVERDUE: 'danger', NORMAL: 'info'
  }
  return map[k] || 'info'
}

/** el-table formatter */
export function fmt(type) {
  return (_, __, v) => t(type, v)
}

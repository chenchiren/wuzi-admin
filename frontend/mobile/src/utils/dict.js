/**
 * 手机端界面字典（与 PC 端同源：GET /api/dicts/map）
 * 例如 t('MATERIAL_USE_STATUS','DRAFT') → 草稿
 */
import { request } from '../api'

let cache = null
let loading = null

export async function loadDicts(force = false) {
  if (cache && !force) return cache
  if (loading) return loading
  loading = (async () => {
    try {
      const res = await request('/api/dicts/map')
      if (res && res.code === 200) {
        cache = res.data || {}
      } else if (res && res.data) {
        cache = res.data
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

/** 某事件的全部项：[{ value, label }] —— 用于筛选/下拉 */
export function dictOptions(type) {
  const m = (cache && cache[type]) || {}
  return Object.entries(m).map(([value, label]) => ({ value, label }))
}

export function tMaterialUseStatus(c) { return t('MATERIAL_USE_STATUS', c) }
export function tUseType(c) { return t('USE_TYPE', c) }
export function tBorrowStatus(c) { return t('BORROW_STATUS', c) }
export function tBorrowType(c) { return t('BORROW_TYPE', c) }
export function tTransferStatus(c) { return t('TRANSFER_STATUS', c) }
export function tInventoryStatus(c) { return t('INVENTORY_STATUS', c) }
export function tInboundStatus(c) { return t('INBOUND_STATUS', c) }
export function tOutboundStatus(c) { return t('OUTBOUND_STATUS', c) }
export function tArrivalStatus(c) { return t('PURCHASE_ARRIVAL_STATUS', c) }
export function tApprovalStatus(c) { return t('APPROVAL_STATUS', c) }
export function tApprovalAction(c) { return t('APPROVAL_ACTION', c) }
export function tBiz(c) { return t('BIZ_TYPE', c) }
export function tUserSource(c) { return t('USER_SOURCE', c) }
export function tRole(c) { return t('ROLE', c) }
export function tConditionStatus(c) { return t('CONDITION_STATUS', c) }
export function tQrStatus(c) { return t('QR_STATUS', c) }
export function tQrType(c) { return t('QR_TYPE', c) }
export function tLogResult(c) { return t('LOG_RESULT', c) }
export function tMsgRead(c) { return t('MSG_READ', c) }

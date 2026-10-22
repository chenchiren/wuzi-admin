/**
 * 离线队列（localStorage）：
 * - 扫码日志 → POST /api/m/sync/batch
 * - 盘点草稿 → POST /api/m/sync/batch
 * - 业务动作（入库/出库扫码等）→ 恢复后按原接口重试
 */
import { api, request } from './api'

const KEY_SCAN = 'wuzi_m_offline_scans'
const KEY_DRAFT = 'wuzi_m_offline_drafts'
const KEY_ACTION = 'wuzi_m_offline_actions'
const KEY_SYNC_AT = 'wuzi_m_sync_at'

function read(key) {
  try {
    return JSON.parse(localStorage.getItem(key) || '[]')
  } catch {
    return []
  }
}

function write(key, list) {
  localStorage.setItem(key, JSON.stringify(list))
}

export function uuid() {
  return 'r-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10)
}

export function enqueueScan(log) {
  const item = {
    requestId: log.requestId || uuid(),
    code: log.code,
    qrCode: log.qrCode || log.code,
    scanType: log.scanType || 'QUERY',
    bizType: log.bizType || null,
    bizId: log.bizId || null,
    warehouseId: log.warehouseId || null,
    channel: log.channel || 'H5',
    result: log.result || 'FAIL',
    failReason: log.failReason || null,
    scanTime: log.scanTime || new Date().toISOString().slice(0, 19),
    clientIp: null,
    queuedAt: Date.now()
  }
  const list = read(KEY_SCAN)
  list.unshift(item)
  write(KEY_SCAN, list.slice(0, 500))
  return item
}

export function enqueueDraft(draft) {
  const item = {
    requestId: draft.requestId || uuid(),
    checkId: draft.checkId || null,
    itemId: draft.itemId || null,
    materialId: draft.materialId || null,
    batchNo: draft.batchNo || null,
    locationId: draft.locationId || null,
    bookQty: draft.bookQty ?? null,
    actualQty: draft.actualQty ?? null,
    remark: draft.remark || null,
    queuedAt: Date.now()
  }
  const list = read(KEY_DRAFT)
  // 同盘点单同物资覆盖旧草稿
  const idx = list.findIndex(
    (d) =>
      d.checkId === item.checkId &&
      ((item.itemId && d.itemId === item.itemId) ||
        (item.materialId && d.materialId === item.materialId))
  )
  if (idx >= 0) list.splice(idx, 1)
  list.unshift(item)
  write(KEY_DRAFT, list.slice(0, 500))
  return item
}

export function enqueueAction(action) {
  const item = {
    requestId: action.requestId || uuid(),
    kind: action.kind || 'API',
    method: action.method || 'POST',
    path: action.path,
    body: action.body ?? null,
    label: action.label || action.path,
    queuedAt: Date.now()
  }
  const list = read(KEY_ACTION)
  list.unshift(item)
  write(KEY_ACTION, list.slice(0, 200))
  return item
}

export function getQueue() {
  return {
    scans: read(KEY_SCAN),
    drafts: read(KEY_DRAFT),
    actions: read(KEY_ACTION),
    lastSyncAt: localStorage.getItem(KEY_SYNC_AT) || ''
  }
}

export function queueSize() {
  const q = getQueue()
  return q.scans.length + q.drafts.length + q.actions.length
}

export function clearQueue() {
  write(KEY_SCAN, [])
  write(KEY_DRAFT, [])
  write(KEY_ACTION, [])
}

export function removeById(kind, requestId) {
  const key = kind === 'scan' ? KEY_SCAN : kind === 'draft' ? KEY_DRAFT : KEY_ACTION
  write(
    key,
    read(key).filter((x) => x.requestId !== requestId)
  )
}

/**
 * 冲刷离线队列。优先 POST /api/m/sync/batch；
 * 业务动作按原路径重试；若 sync/batch 不可用，扫码日志逐条 fallback 到 /api/m/scan/resolve。
 */
export async function flushQueue() {
  const q = getQueue()
  const summary = {
    scanOk: 0,
    draftOk: 0,
    actionOk: 0,
    failed: 0,
    messages: []
  }

  if (q.scans.length || q.drafts.length) {
    try {
      const resp = await api.syncBatch({
        scanLogs: q.scans.map(({ queuedAt, ...rest }) => rest),
        inventoryDrafts: q.drafts.map(({ queuedAt, ...rest }) => rest)
      })
      const data = resp.data || {}
      const results = data.results || []
      for (const r of results) {
        if (r.status === 'INSERTED' || r.status === 'DUPLICATE') {
          removeById(r.kind === 'SCAN_LOG' ? 'scan' : 'draft', r.requestId)
          if (r.kind === 'SCAN_LOG') summary.scanOk++
          else summary.draftOk++
        } else {
          summary.failed++
          summary.messages.push(`${r.requestId}: ${r.message || r.status}`)
        }
      }
      // 兼容：无 per-item results 时按数量清空
      if (!results.length && (data.insertedScanLogs || data.duplicatedScanLogs)) {
        q.scans.forEach((s) => removeById('scan', s.requestId))
        summary.scanOk = q.scans.length
      }
      if (!results.length && (data.insertedDrafts || data.duplicatedDrafts)) {
        q.drafts.forEach((d) => removeById('draft', d.requestId))
        summary.draftOk = q.drafts.length
      }
    } catch (e) {
      // 后端无 /api/m/sync/batch 时：逐条调已有扫码接口
      if (e.offline || e.code === -1) {
        summary.failed += q.scans.length + q.drafts.length
        summary.messages.push('网络不可用，稍后重试')
      } else {
        for (const s of q.scans) {
          try {
            await api.scanResolve({
              code: s.code || s.qrCode,
              scanType: s.scanType,
              bizType: s.bizType,
              bizId: s.bizId,
              warehouseId: s.warehouseId,
              channel: s.channel
            })
            removeById('scan', s.requestId)
            summary.scanOk++
          } catch (err) {
            if (err.offline) {
              summary.failed++
              break
            }
            // 码无法识别也记为已尽力上报
            removeById('scan', s.requestId)
            summary.scanOk++
          }
        }
        for (const d of q.drafts) {
          // 盘点草稿无独立接口时保留本地，等 /api/m/sync/batch
          summary.failed++
          summary.messages.push(`盘点草稿 ${d.requestId} 待 /api/m/sync/batch`)
        }
        if (e.message) summary.messages.push(e.message)
      }
    }
  }

  for (const a of [...read(KEY_ACTION)]) {
    try {
      await request(a.path, { method: a.method, body: a.body ?? undefined })
      removeById('action', a.requestId)
      summary.actionOk++
    } catch (e) {
      if (e && e.offline) {
        summary.failed++
        break
      }
      // 业务校验失败（如单据状态）也移出队列，避免死循环
      removeById('action', a.requestId)
      summary.failed++
      summary.messages.push(`${a.label}: ${e.message || '失败'}`)
    }
  }

  localStorage.setItem(KEY_SYNC_AT, new Date().toISOString())
  return summary
}

let timer = null
export function startAutoSync(intervalMs = 30000) {
  if (timer) return
  const tick = async () => {
    if (typeof navigator !== 'undefined' && navigator.onLine === false) return
    if (queueSize() === 0) return
    try {
      await flushQueue()
    } catch {
      /* ignore */
    }
  }
  timer = setInterval(tick, intervalMs)
  if (typeof window !== 'undefined') {
    window.addEventListener('online', tick)
  }
}

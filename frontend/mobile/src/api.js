/**
 * 手机端 API 封装。默认直连 http://localhost:8081（也可走 vite 代理 /api）。
 * 响应约定：{ code, msg, data }，code===200 为成功。
 */
export const API_BASE =
  (typeof localStorage !== 'undefined' && localStorage.getItem('m_api_base')) ||
  import.meta.env.VITE_API_BASE ||
  'http://localhost:8081'

export function getToken() {
  return localStorage.getItem('m_token') || ''
}

export function setSession({ token, ...rest }) {
  if (token) localStorage.setItem('m_token', token)
  localStorage.setItem('m_user', JSON.stringify(rest || {}))
}

export function getUser() {
  try {
    return JSON.parse(localStorage.getItem('m_user') || 'null')
  } catch {
    return null
  }
}

export function clearSession() {
  localStorage.removeItem('m_token')
  localStorage.removeItem('m_user')
}

export function isLoggedIn() {
  return !!getToken()
}

export class ApiError extends Error {
  constructor(msg, code, payload) {
    super(msg || '请求失败')
    this.code = code
    this.payload = payload
  }
}

/**
 * @param {string} path 以 / 开头，如 /api/m/home
 * @param {{method?:string, body?:any, timeout?:number, raw?:boolean}} [options]
 */
export async function request(path, options = {}) {
  const { method = 'GET', body, timeout = 15000, raw = false } = options
  const ctrl = new AbortController()
  const timer = setTimeout(() => ctrl.abort(), timeout)
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  const token = getToken()
  if (token) headers.Authorization = `Bearer ${token}`

  try {
    const resp = await fetch(API_BASE + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      signal: ctrl.signal
    })
    if (resp.status === 401) {
      clearSession()
      if (location.hash !== '#/login') location.hash = '#/login'
      throw new ApiError('未登录或令牌失效', 401)
    }
    const data = await resp.json().catch(() => ({}))
    if (raw) return data
    if (data && data.code !== undefined && data.code !== 200) {
      throw new ApiError(data.msg || '请求失败', data.code, data)
    }
    return data
  } catch (e) {
    if (e.name === 'AbortError') throw new ApiError('请求超时', 0)
    if (e instanceof ApiError) throw e
    // 网络错误：标记给离线队列
    const err = new ApiError(e.message || '网络异常', -1)
    err.offline = true
    throw err
  } finally {
    clearTimeout(timer)
  }
}

export const api = {
  // 会话
  login: (username, password) => request('/api/m/auth/login', { method: 'POST', body: { username, password } }),
  me: () => request('/api/m/me'),
  wecomLogin: (code) => request('/api/m/auth/wechat-work/login', { method: 'POST', body: { code } }),
  wecomStatus: () => request('/api/m/auth/wechat-work/status', { method: 'POST', body: {} }),
  dingtalkLogin: (code) => request('/api/m/auth/dingtalk/login', { method: 'POST', body: { code } }),
  dingtalkStatus: () => request('/api/m/auth/dingtalk/status', { method: 'POST', body: {} }),

  // 首页 / 扫码
  home: () => request('/api/m/home'),
  todos: (role) => request('/api/m/todos' + (role ? `?role=${role}` : '')),
  scanResolve: (payload) => request('/api/m/scan/resolve', { method: 'POST', body: payload }),
  scanRecent: (params = {}) => {
    const q = new URLSearchParams(params).toString()
    return request('/api/m/scan/recent' + (q ? `?${q}` : ''))
  },

  // 作业
  inboundDetail: (id) => request(`/api/m/inbounds/${id}`),
  inboundScan: (id, body) => request(`/api/m/inbounds/${id}/scan`, { method: 'POST', body }),
  inboundConfirm: (id) => request(`/api/m/inbounds/${id}/confirm`, { method: 'POST', body: {} }),
  inboundList: (params = {}) => {
    const q = new URLSearchParams(params).toString()
    return request('/api/inbound/orders' + (q ? `?${q}` : ''))
  },
  lookupWork: (body) => request('/api/m/scan/lookup-work', { method: 'POST', body }),
  borrowScanReturn: (body) => request('/api/m/borrows/scan-return', { method: 'POST', body }),

  outboundDetail: (id) => request(`/api/m/outbounds/${id}`),
  outboundScan: (id, body) => request(`/api/m/outbounds/${id}/scan`, { method: 'POST', body }),
  outboundConfirm: (id) => request(`/api/m/outbounds/${id}/confirm`, { method: 'POST', body: {} }),
  outboundList: (params = {}) => {
    const q = new URLSearchParams(params).toString()
    return request('/api/outbound/orders' + (q ? `?${q}` : ''))
  },

  arrivalDetail: (id) => request(`/api/m/arrivals/${id}`),
  arrivalSubmit: (id) => request(`/api/m/arrivals/${id}/submit`, { method: 'POST', body: {} }),
  arrivalList: (params = {}) => {
    const q = new URLSearchParams(params).toString()
    return request('/api/purchase/arrivals' + (q ? `?${q}` : ''))
  },

  // 领用 / 审批
  useApplyList: (params = {}) => {
    const q = new URLSearchParams(params).toString()
    return request('/api/material-use' + (q ? `?${q}` : ''))
  },
  useApplyCreate: (body) => request('/api/m/material-use', { method: 'POST', body }),
  approvalTodo: (params = {}) => {
    const q = new URLSearchParams(params).toString()
    return request('/api/approvals/todo' + (q ? `?${q}` : ''))
  },
  approvalDetail: (id) => request(`/api/approvals/${id}`),
  approvalApprove: (id, body) => request(`/api/m/approvals/${id}/approve`, { method: 'POST', body: body || {} }),
  approvalReject: (id, body) => request(`/api/m/approvals/${id}/reject`, { method: 'POST', body: body || {} }),

  // 查询 / 溯源
  materials: (keyword) => request('/api/materials?size=20' + (keyword ? `&keyword=${encodeURIComponent(keyword)}` : '')),
  stocks: (params = {}) => {
    const q = new URLSearchParams(params).toString()
    return request('/api/m/stocks' + (q ? `?${q}` : ''))
  },
  trace: (token) => request(`/api/m/trace/${encodeURIComponent(token)}`),

  // 消息
  messages: (params = {}) => {
    const q = new URLSearchParams(params).toString()
    return request('/api/messages' + (q ? `?${q}` : ''))
  },
  messageUnread: () => request('/api/messages/unread-count'),
  messageRead: (id) => request(`/api/messages/${id}/read`, { method: 'POST', body: {} }),
  messageReadAll: () => request('/api/messages/read-all', { method: 'POST', body: {} }),

  // 盘点 / 调拨 / 借还（二期）
  inventoryList: (params = {}) => {
    const q = new URLSearchParams(params).toString()
    return request('/api/inventory/checks' + (q ? `?${q}` : ''))
  },
  inventorySubmit: (id) => request(`/api/inventory/checks/${id}/submit`, { method: 'POST', body: {} }),
  inventoryConfirm: (id) => request(`/api/inventory/checks/${id}/confirm`, { method: 'POST', body: {} }),
  transferList: (params = {}) => {
    const q = new URLSearchParams(params).toString()
    return request('/api/transfer/orders' + (q ? `?${q}` : ''))
  },
  borrowList: (params = {}) => {
    const q = new URLSearchParams(params).toString()
    return request('/api/borrows' + (q ? `?${q}` : ''))
  },
  borrowOverdue: () => request('/api/borrows/overdue'),

  // 离线同步
  syncBatch: (body) => request('/api/m/sync/batch', { method: 'POST', body })
}

export default api

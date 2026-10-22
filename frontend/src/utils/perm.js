/**
 * 按钮级权限：登录后写入 localStorage.user.perms
 * v-perm="'po:apply:create'" 或 v-perm="['a','b']"（任一满足）
 */
const state = {
  perms: new Set(),
  roles: [],
  loaded: false
}

export function setPerms(perms, roles) {
  state.perms = new Set(perms || [])
  state.roles = roles || []
  state.loaded = true
}

export function loadPermsFromStorage() {
  try {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    setPerms(user.perms || [], user.roles || [])
  } catch {
    setPerms([], [])
  }
}

export function hasPerm(code) {
  if (!state.loaded) loadPermsFromStorage()
  // ADMIN 全放行
  if ((state.roles || []).includes('ADMIN')) return true
  if (!code) return true
  if (Array.isArray(code)) {
    if (!code.length) return true
    return code.some((c) => state.perms.has(c))
  }
  return state.perms.has(code)
}

export function isAdmin() {
  if (!state.loaded) loadPermsFromStorage()
  return (state.roles || []).includes('ADMIN')
}

export function getPerms() {
  if (!state.loaded) loadPermsFromStorage()
  return [...state.perms]
}

/** 登录 / 刷新权限后同步 */
export async function refreshPerms(api) {
  try {
    const res = await api.get('/auth/perms')
    const perms = res.data?.perms || []
    const roles = res.data?.roles || state.roles
    setPerms(perms, roles)
    try {
      const user = JSON.parse(localStorage.getItem('user') || '{}')
      user.perms = perms
      user.roles = roles
      localStorage.setItem('user', JSON.stringify(user))
    } catch { /* ignore */ }
  } catch {
    loadPermsFromStorage()
  }
}

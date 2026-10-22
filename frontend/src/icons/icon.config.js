/**
 * 图标自定义配置（唯一推荐改动入口）
 *
 * 1) navMap：导航栏「业务键 → 图标名」映射，可改成任意已注册图标名
 * 2) customIcons：新增/覆盖图标定义
 * 3) applyIconConfig()：启动时调用，把自定义写入 registry
 */
import { registerIcons, overrideIcon, listBuiltinIcons } from './registry'

/**
 * 导航映射：key 与 Layout 中 group.key / item.path 对应
 * - 分组用 group 自己的 key
 * - 菜单项用 path（如 /materials）
 */
export const navMap = {
  // 分组
  home: 'dashboard',
  base: 'database',
  po: 'cart',
  wh: 'warehouse',
  asset: 'assets',
  trace: 'qr-code',
  report: 'chart',
  sys: 'settings',

  // 基础数据
  '/materials': 'package',
  '/categories': 'folders',
  '/suppliers': 'truck',
  '/tax-rates': 'percent',

  // 采购管理
  '/purchase-applies': 'file-text',
  '/purchase-orders': 'receipt',
  '/approvals': 'stamp',
  '/arrivals': 'package-check',

  // 仓储作业
  '/inbounds': 'arrow-in',
  '/outbounds': 'arrow-out',
  '/stock': 'boxes',
  '/transfers': 'transfer',
  '/inventory-checks': 'clipboard',

  // 领用资产
  '/material-use': 'hand',
  '/borrows': 'clock',
  '/scraps': 'trash',
  '/alerts': 'alert',

  // 溯源标签
  '/trace': 'scan',
  '/label-print': 'printer',

  // 分析报表
  '/stats': 'gauge',
  '/reports': 'file-chart',

  // 系统管理
  '/users': 'user',
  '/positions': 'user',
  '/depts': 'building',
  '/roles': 'shield',
  '/dicts': 'settings',
  '/excel-import': 'sheet',
  '/logs': 'history'
}

/**
 * 自定义图标（可选）
 * 例：
 * customIcons: {
 *   'my-home': { viewBox: '0 0 24 24', parts: [{ tag: 'path', attrs: { d: '...' } }] }
 * }
 */
export const customIcons = {
  // 在此新增图标，或覆盖同名内置图标
}

/**
 * 直接覆盖内置图标的 parts（不想换名字、只想换造型时用）
 * 例：overrides: { cart: [ { tag: 'path', attrs: { d: '...' } } ] }
 */
export const overrides = {}

/** 解析导航键 → 图标名（未配置时回退键本身） */
export function resolveNavIcon(key) {
  return navMap[key] || key
}

/** 应用全部自定义配置到 registry */
export function applyIconConfig() {
  registerIcons(customIcons)
  Object.entries(overrides).forEach(([name, parts]) => {
    overrideIcon(name, parts)
  })
}

export { listBuiltinIcons }

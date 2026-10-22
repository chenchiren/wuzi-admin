import { builtinIcons } from './paths'

/**
 * 可配置图标注册表
 * - 启动时载入 builtinIcons
 * - registerIcon(name, def) 新增/覆盖
 * - removeIcon(name) 删除自定义
 * - getIcon(name) 读取（含自定义）
 */
const icons = { ...builtinIcons }

/** 新增或覆盖图标 */
export function registerIcon(name, def) {
  if (!name || !def || !Array.isArray(def.parts)) {
    throw new Error(`registerIcon("${name}") 需要 { viewBox, parts[] }`)
  }
  icons[name] = {
    viewBox: def.viewBox || '0 0 24 24',
    parts: def.parts
  }
  return icons[name]
}

/** 批量注册 */
export function registerIcons(map) {
  Object.entries(map || {}).forEach(([name, def]) => registerIcon(name, def))
}

/** 仅覆盖内置图标定义（保留名字，换路径） */
export function overrideIcon(name, parts, viewBox) {
  return registerIcon(name, { viewBox, parts })
}

/** 删除图标（内置也可删，一般用于精简） */
export function removeIcon(name) {
  delete icons[name]
}

/** 恢复某内置图标 */
export function resetIcon(name) {
  if (builtinIcons[name]) {
    icons[name] = { ...builtinIcons[name], parts: [...builtinIcons[name].parts] }
  } else {
    delete icons[name]
  }
  return icons[name]
}

/** 全部恢复出厂 */
export function resetAllIcons() {
  Object.keys(icons).forEach((k) => delete icons[k])
  Object.assign(icons, builtinIcons)
}

export function getIcon(name) {
  return icons[name] || null
}

export function hasIcon(name) {
  return Boolean(icons[name])
}

export function listIcons() {
  return Object.keys(icons).sort()
}

export function listBuiltinIcons() {
  return Object.keys(builtinIcons).sort()
}

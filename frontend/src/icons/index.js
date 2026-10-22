import AppIcon from './AppIcon.vue'
import {
  registerIcon,
  registerIcons,
  overrideIcon,
  removeIcon,
  resetIcon,
  resetAllIcons,
  getIcon,
  hasIcon,
  listIcons,
  listBuiltinIcons
} from './registry'
import {
  navMap,
  customIcons,
  overrides,
  resolveNavIcon,
  applyIconConfig
} from './icon.config'
import { builtinIcons } from './paths'

export {
  AppIcon,
  builtinIcons,
  registerIcon,
  registerIcons,
  overrideIcon,
  removeIcon,
  resetIcon,
  resetAllIcons,
  getIcon,
  hasIcon,
  listIcons,
  listBuiltinIcons,
  navMap,
  customIcons,
  overrides,
  resolveNavIcon,
  applyIconConfig
}

/** main.js 启动时调用一次 */
export function setupIcons() {
  applyIconConfig()
  return {
    AppIcon,
    listIcons,
    registerIcon
  }
}

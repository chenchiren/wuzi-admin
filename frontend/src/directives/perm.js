import { hasPerm } from '../utils/perm'

/**
 * v-perm="'po:apply:create'"  或  v-perm="['a','b']"
 * 无权限时隐藏元素（display:none），保留占位由父级 flex 决定。
 */
export const vPerm = {
  mounted(el, binding) {
    toggle(el, binding.value)
  },
  updated(el, binding) {
    toggle(el, binding.value)
  }
}

function toggle(el, code) {
  const ok = hasPerm(code)
  el.style.display = ok ? '' : 'none'
  if (!ok) {
    el.setAttribute('aria-hidden', 'true')
    el.setAttribute('disabled', 'disabled')
  } else {
    el.removeAttribute('aria-hidden')
    el.removeAttribute('disabled')
  }
}

export default vPerm

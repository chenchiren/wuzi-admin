import { ref } from 'vue'

export const tipBus = ref('')

let timer = null
export function showToast(text) {
  tipBus.value = text || ''
  clearTimeout(timer)
  timer = setTimeout(() => { tipBus.value = '' }, 2200)
}

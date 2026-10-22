<template>
  <el-select
    :model-value="selected"
    filterable
    allow-create
    default-first-option
    clearable
    size="small"
    placeholder="选税种或手填"
    class="tax-rate-select"
    @update:model-value="onChange"
    @clear="onClear"
  >
    <el-option
      v-for="t in options"
      :key="t.taxCode"
      :value="t.taxCode"
      :label="`${t.taxRate}% · ${t.taxName || t.taxCode}`"
    />
  </el-select>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import api from '../api/request'

/**
 * 税率选择：常见税从字典选，也可手填实际税率（发票票面税）。
 * 选字典 → taxCode + taxRate；手填 → taxCode=null + taxRate=数值。
 */
const props = defineProps({
  taxCode: { type: String, default: null },
  taxRate: { type: [Number, String], default: null }
})
const emit = defineEmits(['update:taxCode', 'update:taxRate'])

const options = ref([])

const selected = computed(() => {
  if (props.taxCode && options.value.some((t) => t.taxCode === props.taxCode)) {
    return props.taxCode
  }
  if (props.taxRate === null || props.taxRate === undefined || props.taxRate === '') {
    return ''
  }
  return String(props.taxRate)
})

function onChange(val) {
  if (val === null || val === undefined || val === '') {
    onClear()
    return
  }
  const hit = options.value.find((t) => t.taxCode === val)
  if (hit) {
    emit('update:taxCode', hit.taxCode)
    emit('update:taxRate', Number(hit.taxRate))
    return
  }
  const n = Number(String(val).replace('%', '').trim())
  if (Number.isNaN(n) || n < 0) {
    return
  }
  emit('update:taxCode', null)
  emit('update:taxRate', n)
}

function onClear() {
  emit('update:taxCode', null)
  emit('update:taxRate', null)
}

onMounted(async () => {
  try {
    const res = await api.get('/tax-rates', { params: { onlyEnabled: true } })
    options.value = res.data || []
  } catch {
    options.value = []
  }
})
</script>

<style scoped>
.tax-rate-select {
  width: 100%;
}
</style>

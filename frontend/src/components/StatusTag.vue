<template>
  <el-tag size="small" :type="tagType" :effect="effect">{{ text }}</el-tag>
</template>

<script setup>
import { computed } from 'vue'
import { t, statusTagType } from '../utils/dict'

const props = defineProps({
  value: { type: [String, Number, Boolean], default: null },
  /** 字典类型，如 MATERIAL_USE_STATUS */
  dictType: { type: String, default: '' },
  /** 直接指定文案（优先） */
  label: { type: String, default: '' },
  effect: { type: String, default: 'light' }
})

const tagType = computed(() => statusTagType(props.value))
const text = computed(() => {
  if (props.label) return props.label
  if (props.dictType) return t(props.dictType, props.value)
  return props.value === null || props.value === undefined || props.value === '' ? '-' : String(props.value)
})
</script>

<template>
  <div v-if="msg" class="toast">{{ msg }}</div>
</template>

<script setup>
import { ref, watch, onBeforeUnmount } from 'vue'
import { tipBus } from './toastBus'

const props = defineProps({
  message: { type: String, default: '' }
})
const msg = ref('')
let timer = null

function flash(text) {
  msg.value = text
  clearTimeout(timer)
  timer = setTimeout(() => { msg.value = '' }, 2200)
}

watch(() => props.message, (v) => { if (v) flash(v) })
watch(tipBus, (v) => { if (v) flash(v) })

onBeforeUnmount(() => clearTimeout(timer))
</script>

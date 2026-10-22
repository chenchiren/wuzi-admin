<template>
  <div class="nav">
    <a v-if="back" class="back" href="javascript:history.back()">‹ 返回</a>
    <span>{{ title }}</span>
  </div>
</template>

<script setup>
defineProps({
  title: { type: String, default: '' },
  back: { type: Boolean, default: true }
})
</script>

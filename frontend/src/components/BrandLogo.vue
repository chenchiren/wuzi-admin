<template>
  <svg
    class="brand-logo"
    :width="size"
    :height="size"
    viewBox="0 0 36 36"
    fill="none"
    xmlns="http://www.w3.org/2000/svg"
    role="img"
    aria-label="物资全链路"
  >
    <rect width="36" height="36" :rx="radius" :fill="bg" />
    <!-- 等距物资箱 -->
    <g stroke="#fff" stroke-width="1.55" stroke-linejoin="round" stroke-linecap="round">
      <path d="M18 9.2l7.2 3.9v8.6L18 25.6l-7.2-3.9v-8.6L18 9.2z" />
      <path d="M10.8 13.1L18 17l7.2-3.9" />
      <path d="M18 17v8.6" />
    </g>
    <!-- 全链路节点弧 -->
    <g stroke="#fff" stroke-linecap="round" fill="#fff">
      <path
        d="M8.1 22.6c-1.35-1.45-2.15-3.35-2.15-5.4 0-1.45.4-2.8 1.15-4"
        stroke-width="1.3"
        opacity="0.55"
        fill="none"
      />
      <path
        d="M27.9 13.4c1.35 1.45 2.15 3.35 2.15 5.4 0 1.45-.4 2.8-1.15 4"
        stroke-width="1.3"
        opacity="0.55"
        fill="none"
      />
      <circle cx="7.2" cy="9.6" r="1.15" opacity="0.9" />
      <circle cx="28.8" cy="26.4" r="1.15" opacity="0.9" />
    </g>
  </svg>
</template>

<script setup>
defineProps({
  size: { type: Number, default: 36 },
  bg: { type: String, default: '#2F6BFF' },
  radius: { type: Number, default: 10 }
})
</script>

<style scoped>
.brand-logo {
  display: block;
  flex-shrink: 0;
}
</style>

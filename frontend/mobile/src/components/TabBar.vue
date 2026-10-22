<template>
  <nav class="tabbar">
    <router-link to="/" :class="{ active: active === 'home' }">
      <span class="t-ico">⌂</span>
      <span>首页</span>
    </router-link>
    <router-link to="/scan" :class="{ active: active === 'scan' }">
      <span class="t-ico">▣</span>
      <span>扫码</span>
    </router-link>
    <router-link to="/stocks" :class="{ active: active === 'stocks' }">
      <span class="t-ico">▤</span>
      <span>库存</span>
    </router-link>
    <router-link to="/messages" :class="{ active: active === 'messages' }">
      <span class="t-ico">✉</span>
      <span>消息</span>
    </router-link>
    <router-link to="/me" :class="{ active: active === 'me' }">
      <span class="t-ico">◉</span>
      <span>我的</span>
    </router-link>
  </nav>
</template>

<script setup>
defineProps({
  active: { type: String, default: 'home' }
})
</script>

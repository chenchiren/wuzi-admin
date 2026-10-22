<template>
  <div class="page no-tab">
    <div class="login-hero">
      <h1>物资管理</h1>
      <p>现场作业台 · 扫码入口 · 审批口袋</p>
    </div>

    <div class="card">
      <h3>账号登录</h3>
      <div class="field">
        <label>用户名</label>
        <input v-model.trim="username" placeholder="admin" autocomplete="username" />
      </div>
      <div class="field">
        <label>密码</label>
        <input v-model="password" type="password" placeholder="••••••" autocomplete="current-password" @keyup.enter="doLogin" />
      </div>
      <button class="btn block" :disabled="loading" @click="doLogin">
        {{ loading ? '登录中…' : '登录' }}
      </button>
    </div>

    <div class="divider">企业协同免登</div>

    <div class="card">
      <button class="btn block ghost" style="margin-bottom: 10px" :disabled="loading" @click="wecomLogin">
        企业微信扫码 / 免登
      </button>
      <button class="btn block plain" :disabled="loading" @click="dingtalkLogin">
        钉钉免登
      </button>
      <p class="muted" style="margin: 12px 0 0">
        未配置真实密钥时为 mock：code=<b>demo</b> 直接发 token。配置方法见 frontend/mobile/README.md。
      </p>
    </div>

    <Toast :message="toast" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api, setSession } from '../api'
import Toast from '../components/Toast.vue'

const router = useRouter()
const route = useRoute()
const username = ref('admin')
const password = ref('admin123')
const loading = ref(false)
const toast = ref('')

function afterLogin(data) {
  setSession(data)
  const redirect = route.query.redirect || '/'
  router.replace(String(redirect))
}

async function doLogin() {
  loading.value = true
  toast.value = ''
  try {
    const resp = await api.login(username.value, password.value)
    afterLogin(resp.data)
  } catch (e) {
    toast.value = e.message || '登录失败'
  } finally {
    loading.value = false
  }
}

async function wecomLogin() {
  loading.value = true
  toast.value = ''
  try {
    let code = 'demo'
    try {
      const st = await api.wecomStatus()
      if (st.data && st.data.mock && st.data.mockCode) code = st.data.mockCode
      else {
        const input = window.prompt('请输入企微 OAuth code', '')
        if (!input) {
          loading.value = false
          return
        }
        code = input
      }
    } catch {
      /* status 可选 */
    }
    const resp = await api.wecomLogin(code)
    afterLogin(resp.data)
  } catch (e) {
    toast.value = e.message || '企微登录失败'
  } finally {
    loading.value = false
  }
}

async function dingtalkLogin() {
  loading.value = true
  toast.value = ''
  try {
    let code = 'demo'
    try {
      const st = await api.dingtalkStatus()
      if (st.data && st.data.mock && st.data.mockCode) code = st.data.mockCode
      else {
        const input = window.prompt('请输入钉钉 OAuth authCode', '')
        if (!input) {
          loading.value = false
          return
        }
        code = input
      }
    } catch {
      /* ignore */
    }
    const resp = await api.dingtalkLogin(code)
    afterLogin(resp.data)
  } catch (e) {
    toast.value = e.message || '钉钉登录失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const token = localStorage.getItem('m_token')
  if (token) router.replace('/')
})
</script>

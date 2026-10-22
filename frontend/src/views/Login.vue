<template>
  <div class="login-wrap">
    <el-card class="login-card">
      <div class="login-brand">
        <BrandLogo :size="44" bg="#2F6BFF" :radius="12" />
        <h2>物资全链路管理系统</h2>
      </div>
      <el-form @submit.prevent="onLogin">
        <el-form-item label="账号">
          <el-input v-model="username" placeholder="admin" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="password" type="password" placeholder="admin123" show-password @keyup.enter="onLogin" />
        </el-form-item>
        <n-button type="primary" class="w-full" :loading="loading" @click="onLogin">登录</n-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import api from '../api/request'
import BrandLogo from '../components/BrandLogo.vue'
import { loadDicts } from '../utils/dict'
import { setPerms } from '../utils/perm'

const username = ref('admin')
const password = ref('admin123')
const loading = ref(false)
const router = useRouter()

async function onLogin() {
  loading.value = true
  try {
    const res = await api.post('/auth/login', { username: username.value, password: password.value })
    localStorage.setItem('token', res.data.token)
    localStorage.setItem('user', JSON.stringify(res.data))
    setPerms(res.data.perms || [], res.data.roles || [])
    await loadDicts(true)
    ElMessage.success('登录成功')
    router.push('/home')
  } catch (e) {
    ElMessage.error(e.message || '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrap { min-height: 100vh; display: flex; align-items: center; justify-content: center; background: linear-gradient(135deg,#e8f0ff,#f5f6f8); }
.login-card { width: 360px; padding: 12px; }
.login-brand { display: flex; flex-direction: column; align-items: center; gap: 10px; margin-bottom: 20px; }
h2 { text-align: center; margin: 0; color: #2f6bff; }
</style>

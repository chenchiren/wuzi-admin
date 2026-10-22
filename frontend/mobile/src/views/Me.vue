<template>
  <div class="page">
    <NavBar title="我的" :back="false" />

    <div class="card">
      <div class="row">
        <div>
          <b>{{ user.realName || user.username || '未登录' }}</b>
          <div class="muted">{{ (user.roles || []).join(' / ') || '—' }} · 部门 #{{ user.deptId || '—' }}</div>
        </div>
        <span class="tag">{{ tUserSource(user.source || 'PASSWORD') }}</span>
      </div>
    </div>

    <div class="card">
      <h3>离线队列</h3>
      <div class="kv"><span>扫码日志</span><span>{{ q.scans.length }}</span></div>
      <div class="kv"><span>盘点草稿</span><span>{{ q.drafts.length }}</span></div>
      <div class="kv"><span>作业动作</span><span>{{ q.actions.length }}</span></div>
      <div class="kv"><span>上次同步</span><span>{{ q.lastSyncAt || '—' }}</span></div>
      <div class="btn-row">
        <button class="btn" :disabled="!total || syncing" @click="doSync">
          {{ syncing ? '同步中…' : '立即同步' }}
        </button>
        <button class="btn plain" @click="refreshQ">刷新</button>
        <button class="btn danger" @click="clearAll">清空</button>
      </div>
      <p class="muted" style="margin-top: 8px">
        同步接口：POST /api/m/sync/batch（幂等 requestId）。不可用时扫码日志回退 /api/m/scan/resolve。
      </p>
    </div>

    <div class="card">
      <h3>扫码反馈</h3>
      <div class="row">
        <span>成功震动</span>
        <input type="checkbox" v-model="prefs.vibrate" style="width: 22px; height: 22px" />
      </div>
      <div class="row" style="margin-top: 8px">
        <span>提示音</span>
        <input type="checkbox" v-model="prefs.sound" style="width: 22px; height: 22px" />
      </div>
    </div>

    <div class="card">
      <h3>账号</h3>
      <div class="btn-row">
        <button class="btn plain" @click="$router.push('/approvals')">审批中心</button>
        <button class="btn plain" @click="$router.push('/use-apply')">我的领用</button>
      </div>
      <button class="btn block danger" style="margin-top: 12px" @click="logout">退出登录</button>
    </div>

    <div class="card">
      <h3>说明</h3>
      <p class="muted">
        默认仓库进入后可选（仓管）；员工可跳过。确认性库存变更必须在线，过程扫码/盘点可离线暂存。
      </p>
    </div>

    <TabBar active="me" />
    <Toast :message="toast" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { clearSession, getUser } from '../api'
import { loadDicts, tUserSource } from '../utils/dict'
import { clearQueue, flushQueue, getQueue } from '../offline'
import NavBar from '../components/NavBar.vue'
import TabBar from '../components/TabBar.vue'
import Toast from '../components/Toast.vue'

const router = useRouter()
const user = ref(getUser() || {})
const q = ref(getQueue())
const syncing = ref(false)
const toast = ref('')
const prefs = reactive({
  vibrate: localStorage.getItem('m_vibrate') !== '0',
  sound: localStorage.getItem('m_sound') !== '0'
})

const total = computed(() => q.value.scans.length + q.value.drafts.length + q.value.actions.length)

function refreshQ() {
  q.value = getQueue()
}

async function doSync() {
  syncing.value = true
  try {
    const summary = await flushQueue()
    refreshQ()
    toast.value = `同步完成：扫码 ${summary.scanOk} 草稿 ${summary.draftOk} 动作 ${summary.actionOk}${summary.failed ? ' 失败 ' + summary.failed : ''}`
  } catch (e) {
    toast.value = e.message || '同步失败'
  } finally {
    syncing.value = false
  }
}

function clearAll() {
  if (!window.confirm('清空本地离线队列？未同步数据将丢失')) return
  clearQueue()
  refreshQ()
  toast.value = '已清空'
}

function logout() {
  clearSession()
  router.replace('/login')
}

function savePrefs() {
  localStorage.setItem('m_vibrate', prefs.vibrate ? '1' : '0')
  localStorage.setItem('m_sound', prefs.sound ? '1' : '0')
}

onMounted(async () => {
  await loadDicts()
  refreshQ()
  setInterval(refreshQ, 5000)
  setInterval(savePrefs, 1000)
})
</script>

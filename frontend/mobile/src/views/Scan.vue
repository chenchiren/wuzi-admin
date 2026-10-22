<template>
  <div class="page">
    <NavBar title="扫码" :back="false" />

    <div class="card">
      <div class="scan-view">
        <video v-show="scanning" ref="videoEl" class="scan-video" playsinline muted autoplay></video>
        <div class="scan-frame" :class="{ 'is-live': scanning }"></div>
        <div style="position: absolute; bottom: 8px; font-size: 12px; opacity: 0.8; z-index: 3">
          {{ statusText }}
        </div>
      </div>
      <div class="btn-row">
        <button v-if="!scanning" class="btn block" @click="startCamera">● 开始扫码</button>
        <button v-else class="btn block ghost" @click="stopCamera">■ 停止扫码</button>
      </div>
      <div class="btn-row">
        <button class="btn ghost" @click="mode = 'QUERY'">查询</button>
        <button class="btn ghost" @click="mode = 'INBOUND'">入库</button>
        <button class="btn ghost" @click="mode = 'OUTBOUND'">出库</button>
      </div>
      <p class="muted" style="margin: 8px 0 0">当前模式：{{ modeLabel }}</p>
    </div>

    <div class="card">
      <h3>手动输入码</h3>
      <div class="field">
        <input v-model.trim="manualCode" placeholder="qr_code / 批次 / SN / 溯源 URL" @keyup.enter="resolve(manualCode)" />
      </div>
      <div class="btn-row">
        <button class="btn" :disabled="!manualCode || loading" @click="resolve(manualCode)">解析</button>
        <button class="btn plain" @click="manualCode = ''">清空</button>
      </div>
    </div>

    <div class="card">
      <h3>最近扫过</h3>
      <div v-if="!recent.length" class="empty">暂无</div>
      <div v-for="item in recent" :key="item.id || item.requestId" class="list-item" @click="resolve(item.qrCode || item.code)">
        <div class="row">
          <span>{{ item.qrCode || item.code }}</span>
          <span class="tag" :class="item.result === 'SUCCESS' ? 'ok' : 'warn'">{{ tLogResult(item.result) || '离线' }}</span>
        </div>
        <div class="muted">{{ item.scanType || mode }} · {{ item.scanTime || item.queuedAt }}</div>
      </div>
    </div>

    <div v-if="showSheet" class="sheet-mask" @click="showSheet = false"></div>
    <div v-if="showSheet" class="sheet">
      <template v-if="result && result.success">
        <h3>✓ {{ result.materialName || result.rawCode }}</h3>
        <div class="muted">规格 {{ result.spec || '—' }} ｜ 批次 {{ result.batchNo || '—' }}</div>
        <div class="kv"><span>编码</span><span>{{ result.materialCode || '—' }}</span></div>
        <div class="kv"><span>序列</span><span>{{ result.serialNo || '—' }}</span></div>
        <div class="kv"><span>状态</span><span>{{ tQrStatus(result.status) || '—' }}</span></div>
        <div class="kv"><span>码类型</span><span>{{ tQrType(result.codeType) || '—' }}</span></div>

        <template v-if="work.pendingInbounds?.length">
          <h4 style="margin: 12px 0 6px">待收货单据</h4>
          <div v-for="w in work.pendingInbounds" :key="w.id" class="list-item" @click="goInbound(w.id)">
            <div class="row">
              <b>{{ w.inboundNo }}</b>
              <span class="tag warn">待收 {{ w.inQty ?? 0 }}</span>
            </div>
            <div class="muted">{{ w.sourceNo ? `采购单 ${w.sourceNo}` : '' }} · 点击进单</div>
          </div>
        </template>

        <template v-if="work.activeBorrows?.length">
          <h4 style="margin: 12px 0 6px">借用中</h4>
          <div v-for="b in work.activeBorrows" :key="`${b.id}-${b.itemId}`" class="list-item">
            <div class="row">
              <b>{{ b.borrowNo }}</b>
              <span class="tag" :class="b.overdue ? 'warn' : 'ok'">
                {{ b.overdue ? '逾期' : '借用中' }} · 已借 {{ b.borrowDays }} 天
              </span>
            </div>
            <div class="muted">
              借出 {{ b.borrowDate || '—' }} · 未还 {{ b.remainQty }}
              · 预计归还 {{ b.expectReturnDate || '—' }}
            </div>
            <div class="btn-row">
              <button class="btn success" :disabled="busy" @click="doBorrowReturn(b)">登记归还</button>
            </div>
          </div>
        </template>

        <div class="btn-row">
          <button class="btn ghost" @click="goTrace">查看溯源</button>
          <button class="btn" @click="lookup">反查待办</button>
        </div>
      </template>
      <template v-else>
        <h3 style="color: var(--danger)">✕ 解析失败</h3>
        <p class="muted">{{ result?.failReason || offlineMsg || '无法识别的码' }}</p>
        <div class="btn-row">
          <button class="btn plain" @click="showSheet = false">手动输码</button>
          <button class="btn" @click="showSheet = false">知道了</button>
        </div>
      </template>
    </div>

    <TabBar active="scan" />
    <Toast :message="toast" />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../api'
import { loadDicts, tQrStatus, tQrType, tLogResult } from '../utils/dict'
import { enqueueScan, getQueue } from '../offline'
import NavBar from '../components/NavBar.vue'
import TabBar from '../components/TabBar.vue'
import Toast from '../components/Toast.vue'

const route = useRoute()
const router = useRouter()
const mode = ref(String(route.query.mode || 'QUERY'))
const manualCode = ref(String(route.query.code || ''))
const loading = ref(false)
const busy = ref(false)
const scanning = ref(false)
const showSheet = ref(false)
const result = ref(null)
const work = ref({ pendingInbounds: [], activeBorrows: [] })
const offlineMsg = ref('')
const toast = ref('')
const recent = ref([])
let stream = null
let track = null

const modeLabel = computed(
  () => ({ QUERY: '查询', INBOUND: '入库', OUTBOUND: '出库', INVENTORY: '盘点' }[mode.value] || mode.value)
)

async function loadRecent() {
  try {
    const resp = await api.scanRecent({ page: 1, size: 8, onlyMine: true })
    recent.value = resp.data?.records || []
  } catch {
    recent.value = getQueue().scans.slice(0, 8)
  }
}

async function resolve(code) {
  if (!code) return
  loading.value = true
  offlineMsg.value = ''
  try {
    const resp = await api.scanResolve({
      code,
      scanType: mode.value,
      channel: 'H5'
    })
    result.value = resp.data
    showSheet.value = true
    // 查询模式自动反查待办（待收货 / 借用中）
    if (mode.value === 'QUERY' && resp.data?.success) {
      lookup()
    }
  } catch (e) {
    if (e.offline || e.code === -1) {
      enqueueScan({
        code,
        scanType: mode.value,
        result: 'FAIL',
        failReason: 'offline'
      })
      offlineMsg.value = '网络不可用，扫码日志已暂存，恢复后自动同步'
      result.value = { success: false, failReason: offlineMsg.value, rawCode: code }
    } else {
      result.value = { success: false, failReason: e.message, rawCode: code }
    }
    showSheet.value = true
  } finally {
    loading.value = false
    loadRecent()
  }
}

const videoEl = ref(null)
const statusText = ref('点击「开始扫码」打开摄像头')
let loopTimer = null

function stopCamera() {
  scanning.value = false
  if (loopTimer) { clearTimeout(loopTimer); loopTimer = null }
  track?.stop()
  stream?.getTracks?.().forEach((t) => t.stop())
  stream = null
  track = null
  if (videoEl.value) videoEl.value.srcObject = null
  statusText.value = '已停止 · 点击「开始扫码」重新打开'
}

async function startCamera() {
  if (scanning.value) return
  scanning.value = true
  try {
    if (!navigator.mediaDevices?.getUserMedia) {
      toast.value = '当前环境无相机，请手动输码'
      statusText.value = '当前环境无相机 · 请手动输码'
      scanning.value = false
      return
    }
    stream = await navigator.mediaDevices.getUserMedia({
      video: { facingMode: 'environment' },
      audio: false
    })
    track = stream.getVideoTracks()[0]
    // 画面渲染进取景框（此前为离屏 video，用户看不到画面）
    await nextTick()
    if (videoEl.value) {
      videoEl.value.srcObject = stream
      await videoEl.value.play().catch(() => {})
    }
    statusText.value = '识别中… 对准二维码/条形码'
    // 浏览器 BarcodeDetector 可用则自动识别（iOS Safari 16.4+ / Android Chrome）
    if ('BarcodeDetector' in window) {
      const detector = new window.BarcodeDetector({ formats: ['qr_code', 'code_128', 'ean_13'] })
      const loop = async () => {
        if (!scanning.value || !videoEl.value) return
        try {
          const codes = await detector.detect(videoEl.value)
          if (codes && codes[0]?.rawValue) {
            stopCamera()
            resolve(codes[0].rawValue)
            return
          }
        } catch {
          /* 单帧失败忽略，继续下一帧 */
        }
        loopTimer = setTimeout(loop, 300)
      }
      loop()
    } else {
      // 无解码器：保持画面可见供人工核对，配合手动输码
      statusText.value = '此浏览器不支持自动识别 · 请对照画面手动输码'
    }
  } catch {
    stopCamera()
    toast.value = '相机权限失败，请手动输码'
    statusText.value = '相机权限失败 · 请手动输码'
  }
}

function goTrace() {
  const token = result.value?.traceToken
  router.push({ path: '/trace', query: token ? { token } : { code: result.value?.rawCode || '' } })
}

function goInbound(id) {
  showSheet.value = false
  router.push({ path: '/inbound', query: { id } })
}

async function lookup() {
  const code = result.value?.rawCode || manualCode.value
  if (!code) return
  busy.value = true
  try {
    const resp = await api.lookupWork({ code })
    work.value = resp.data || {}
    if (resp.data?.resolved) result.value = resp.data.resolved
    if (!work.value.pendingInbounds?.length && !work.value.activeBorrows?.length) {
      toast.value = '无待收货/借用中匹配'
    }
  } catch (e) {
    toast.value = e.message || '反查失败'
  } finally {
    busy.value = false
  }
}

async function doBorrowReturn(b) {
  const warehouseId = window.prompt('归还入库仓库 ID', '')
  if (!warehouseId) return
  busy.value = true
  try {
    const resp = await api.borrowScanReturn({
      code: result.value?.rawCode || manualCode.value,
      warehouseId: Number(warehouseId)
    })
    toast.value = `已归还 ${resp.data?.returnQty ?? ''} · ${resp.data?.borrowNo || ''}`
    showSheet.value = false
  } catch (e) {
    toast.value = e.message || '归还失败'
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  await loadDicts()
  loadRecent()
  if (manualCode.value) resolve(manualCode.value)
})

onUnmounted(() => {
  stopCamera()
})
</script>

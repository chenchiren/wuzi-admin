<template>
  <div class="public-page">
    <header class="hero">
      <div class="hero-inner">
        <div class="brand">物资溯源</div>
        <div class="brand-sub">公开查验 · 免登录 · 信息脱敏</div>
      </div>
    </header>

    <main class="main">
      <section class="search-card">
        <label class="label">溯源 Token</label>
        <div class="search-row">
          <input
            v-model.trim="token"
            class="input"
            placeholder="粘贴 traceToken 或扫码进入（支持完整 URL）"
            @keyup.enter="load"
          />
          <button class="btn primary" :disabled="!token || loading" @click="load">
            {{ loading ? '查询中…' : '查询溯源' }}
          </button>
        </div>
        <p v-if="error" class="error">{{ error }}</p>
        <p class="tip">本页仅展示公开脱敏信息，不含内部采购价、税率与供应商全称。</p>
      </section>

      <section v-if="data" class="result">
        <div class="card">
          <h2 class="card-title">物资信息</h2>
          <div class="grid">
            <div class="item"><span class="k">名称</span><span class="v">{{ data.materialName || '—' }}</span></div>
            <div class="item"><span class="k">编码</span><span class="v">{{ data.materialCode || '—' }}</span></div>
            <div class="item"><span class="k">规格</span><span class="v">{{ data.spec || '—' }}</span></div>
            <div class="item"><span class="k">单位</span><span class="v">{{ data.unit || '—' }}</span></div>
            <div class="item"><span class="k">批次</span><span class="v">{{ data.batchNo || '—' }}</span></div>
            <div class="item"><span class="k">序列号</span><span class="v">{{ data.serialNo || '—' }}</span></div>
            <div class="item"><span class="k">状态</span><span class="v">{{ statusText(data.status) }}</span></div>
            <div class="item"><span class="k">有效期</span><span class="v">{{ fmtDate(data.expireDate) }}</span></div>
            <div class="item"><span class="k">激活时间</span><span class="v">{{ fmtTime(data.activatedAt) }}</span></div>
            <div class="item"><span class="k">查验次数</span><span class="v">{{ data.scanCount ?? 0 }}{{ data.firstScan ? ' · 含首次' : '' }}</span></div>
            <div class="item"><span class="k">供应商</span><span class="v">{{ data.supplierMasked ? '（已脱敏）' : '—' }}</span></div>
            <div class="item"><span class="k">价格</span><span class="v muted">不对外公开</span></div>
          </div>
        </div>

        <div class="chains">
          <div class="card">
            <h2 class="card-title">
              <span class="dir-badge forward">正向</span>
              {{ data.forwardChain?.label || '正向链路（来源 → 当前）' }}
            </h2>
            <ul class="timeline">
              <li v-for="(n, i) in (data.forwardChain?.nodes || [])" :key="'f' + i" class="tl-item">
                <div class="tl-dot forward"></div>
                <div class="tl-body">
                  <div class="tl-title">{{ n.title }}</div>
                  <div class="tl-desc">{{ n.desc }}</div>
                  <div class="tl-time">{{ fmtTime(n.time) }}</div>
                </div>
              </li>
              <li v-if="!(data.forwardChain?.nodes || []).length" class="tl-empty">暂无正向节点</li>
            </ul>
          </div>

          <div class="card">
            <h2 class="card-title">
              <span class="dir-badge reverse">反向</span>
              {{ data.reverseChain?.label || '反向链路（当前 → 去向）' }}
            </h2>
            <ul class="timeline">
              <li v-for="(n, i) in (data.reverseChain?.nodes || [])" :key="'r' + i" class="tl-item">
                <div class="tl-dot reverse"></div>
                <div class="tl-body">
                  <div class="tl-title">{{ n.title }}</div>
                  <div class="tl-desc">{{ n.desc }}</div>
                  <div class="tl-time">{{ fmtTime(n.time) }}</div>
                </div>
              </li>
              <li v-if="!(data.reverseChain?.nodes || []).length" class="tl-empty">暂无反向节点</li>
            </ul>
          </div>
        </div>

        <p class="foot-note">数据仅供溯源查验，最终解释以供应方合同与质检报告为准。</p>
      </section>

      <section v-else-if="!loading && !error" class="placeholder card">
        <p>输入溯源 Token 或扫描标签二维码，即可查看该物资的公开流转链路。</p>
      </section>
    </main>

    <footer class="footer">物资全链路管理系统 · 公开溯源页</footer>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import api from '../api/request'

const route = useRoute()
const token = ref('')
const data = ref(null)
const loading = ref(false)
const error = ref('')

function pickToken() {
  // 兼容 hash 路由 #/public-trace?token=xxx 与普通 query
  const q = route.query?.token || route.query?.code
  if (q) return String(q)
  const hash = window.location.hash || ''
  const qi = hash.indexOf('?')
  if (qi >= 0) {
    const params = new URLSearchParams(hash.slice(qi + 1))
    const t = params.get('token') || params.get('code')
    if (t) return t
  }
  return ''
}

function normalize(raw) {
  let t = String(raw || '').trim()
  const idx = t.toLowerCase().indexOf('/trace/')
  if (idx >= 0) t = t.slice(idx + 7).split('?')[0].split('#')[0]
  return t
}

function fmtTime(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : '—'
}

function fmtDate(v) {
  return v ? String(v).slice(0, 10) : '—'
}

function statusText(s) {
  const map = {
    CREATED: '已创建', ACTIVE: '已启用', RETIRED: '已停用',
    EXPIRED: '已过期', DISABLED: '已禁用'
  }
  return map[s] || s || '—'
}

async function load() {
  const t = normalize(token.value)
  if (!t) {
    error.value = '请输入溯源 Token'
    return
  }
  loading.value = true
  error.value = ''
  data.value = null
  try {
    const res = await api.get(`/trace/public/${encodeURIComponent(t)}`)
    data.value = res.data
  } catch (e) {
    error.value = e?.response?.data?.msg || e.message || '查询失败'
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const t = pickToken()
  if (t) {
    token.value = t
    load()
  }
})
</script>

<style scoped>
.public-page {
  min-height: 100vh;
  background: linear-gradient(160deg, #eef4ff 0%, #f7f8fb 40%, #ffffff 100%);
  display: flex;
  flex-direction: column;
}
.hero {
  background: linear-gradient(120deg, #1e3a8a 0%, #2563eb 55%, #3b82f6 100%);
  color: #fff;
  padding: 36px 20px 42px;
}
.hero-inner { max-width: 960px; margin: 0 auto; }
.brand { font-size: 28px; font-weight: 700; letter-spacing: 2px; }
.brand-sub { margin-top: 8px; opacity: 0.85; font-size: 14px; }

.main { flex: 1; max-width: 960px; width: 100%; margin: -24px auto 0; padding: 0 16px 40px; }
.search-card {
  background: #fff; border-radius: 12px; padding: 20px;
  box-shadow: 0 8px 24px rgba(30, 58, 138, 0.10);
}
.label { display:block; font-size: 13px; color: #555; margin-bottom: 8px; }
.search-row { display: flex; gap: 10px; flex-wrap: wrap; }
.input {
  flex: 1; min-width: 240px; height: 42px; padding: 0 14px;
  border: 1px solid #d0d7e2; border-radius: 8px; font-size: 14px; outline: none;
}
.input:focus { border-color: #2563eb; box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.15); }
.btn {
  height: 42px; padding: 0 22px; border-radius: 8px; border: none;
  font-size: 14px; cursor: pointer;
}
.btn.primary { background: #2563eb; color: #fff; }
.btn.primary:disabled { background: #93b4f5; cursor: not-allowed; }
.tip { margin: 10px 0 0; font-size: 12px; color: #8a93a6; }
.error { margin: 10px 0 0; color: #dc2626; font-size: 13px; }

.result { margin-top: 18px; }
.card {
  background: #fff; border-radius: 12px; padding: 18px 20px;
  box-shadow: 0 4px 16px rgba(15, 23, 42, 0.06); margin-bottom: 16px;
}
.card-title { margin: 0 0 14px; font-size: 16px; color: #1e293b; display: flex; align-items: center; gap: 8px; }
.dir-badge {
  display: inline-block; font-size: 11px; padding: 2px 8px; border-radius: 999px; color: #fff;
}
.dir-badge.forward { background: #2563eb; }
.dir-badge.reverse { background: #f59e0b; }

.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 10px 16px; }
.item { display: flex; gap: 8px; font-size: 13px; line-height: 1.6; }
.k { color: #7c879b; min-width: 64px; }
.v { color: #1e293b; word-break: break-all; }
.muted { color: #94a3b8; }

.chains { display: grid; grid-template-columns: 1fr 1fr; gap: 16px; }
@media (max-width: 800px) { .chains { grid-template-columns: 1fr; } }

.timeline { list-style: none; margin: 0; padding: 0 0 0 6px; }
.tl-item { position: relative; padding: 0 0 16px 22px; border-left: 2px solid #e2e8f0; margin-left: 6px; }
.tl-item:last-child { border-left-color: transparent; padding-bottom: 0; }
.tl-dot {
  position: absolute; left: -7px; top: 4px; width: 12px; height: 12px;
  border-radius: 50%; border: 2px solid #fff;
}
.tl-dot.forward { background: #2563eb; }
.tl-dot.reverse { background: #f59e0b; }
.tl-title { font-size: 14px; font-weight: 600; color: #1e293b; }
.tl-desc { font-size: 12px; color: #64748b; margin-top: 2px; word-break: break-all; }
.tl-time { font-size: 11px; color: #94a3b8; margin-top: 2px; }
.tl-empty { color: #94a3b8; font-size: 13px; padding: 8px 0; }

.placeholder { color: #64748b; font-size: 14px; text-align: center; padding: 40px 20px; }
.foot-note { text-align: center; color: #94a3b8; font-size: 12px; margin: 8px 0 0; }
.footer {
  text-align: center; color: #94a3b8; font-size: 12px; padding: 18px;
  border-top: 1px solid #e8edf5; background: #fff;
}
</style>

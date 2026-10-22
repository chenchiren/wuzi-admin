<template>
  <div class="page">
    <NavBar title="待我审批" />

    <div class="card">
      <div class="row">
        <h3 style="margin: 0">待办</h3>
        <button class="btn plain" style="min-height: 36px" @click="load">刷新</button>
      </div>
      <div v-if="!list.length" class="empty">暂无待审批</div>
      <div v-for="a in list" :key="a.id" class="list-item">
        <div class="row">
          <b>{{ a.title || a.bizNo || a.id }}</b>
          <span class="tag warn">{{ tBiz(a.bizType) }}</span>
        </div>
        <div class="muted">{{ a.bizNo || '' }} · 提交于 {{ fmt(a.createdAt) }}</div>
        <div class="btn-row">
          <button class="btn success" :disabled="busy" @click="doApprove(a)">通过</button>
          <button class="btn danger" :disabled="busy" @click="doReject(a)">驳回</button>
          <button class="btn plain" @click="openDetail(a)">详情</button>
        </div>
      </div>
    </div>

    <div class="card">
      <h3>审批意见</h3>
      <div class="field">
        <label>意见（驳回必填）</label>
        <input v-model.trim="comment" placeholder="同意 / 驳回原因" />
      </div>
      <p class="muted">通过/驳回时自动带上当前意见；单据：{{ pending?.bizNo || pending?.id || '未选' }}</p>
    </div>

    <div v-if="detail" class="card">
      <h3>详情 #{{ detail.instance?.id }}</h3>
      <div class="kv"><span>业务</span><span>{{ tBiz(detail.instance?.bizType) }} {{ detail.instance?.bizNo }}</span></div>
      <div class="kv"><span>标题</span><span>{{ detail.instance?.title || '—' }}</span></div>
      <div class="kv"><span>状态</span><span>{{ tApprovalStatus(detail.instance?.status) }}</span></div>
      <p class="muted" style="margin-top: 8px">采购单审批可显示含税口径：不含税 + 税额 = 含税（授权角色）</p>
      <div v-if="detail.records?.length">
        <h3>流转</h3>
        <ul class="timeline">
          <li v-for="r in detail.records" :key="r.id">
            {{ tApprovalAction(r.action) }} · {{ r.operatorName || r.operatorId }} · {{ fmt(r.createdAt) }}
            <div class="muted">{{ r.comment || '' }}</div>
          </li>
        </ul>
      </div>
    </div>

    <TabBar />
    <Toast :message="toast" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { api } from '../api'
import { loadDicts, tBiz, tApprovalStatus, tApprovalAction } from '../utils/dict'
import NavBar from '../components/NavBar.vue'
import TabBar from '../components/TabBar.vue'
import Toast from '../components/Toast.vue'

const list = ref([])
const detail = ref(null)
const busy = ref(false)
const toast = ref('')
const comment = ref('')
const pending = ref(null)

function fmt(v) {
  return v ? String(v).replace('T', ' ').slice(0, 16) : ''
}

async function load() {
  try {
    const resp = await api.approvalTodo({ page: 1, size: 20 })
    list.value = resp.data?.records || []
  } catch (e) {
    toast.value = e.message
  }
}

async function openDetail(a) {
  pending.value = a
  try {
    const resp = await api.approvalDetail(a.id)
    detail.value = resp.data
  } catch (e) {
    toast.value = e.message
  }
}

async function doApprove(a) {
  if (busy.value) return
  pending.value = a
  busy.value = true
  try {
    await api.approvalApprove(a.id, { comment: comment.value || '' })
    toast.value = '已通过'
    detail.value = null
    comment.value = ''
    load()
  } catch (e) {
    toast.value = e.message
  } finally {
    busy.value = false
  }
}

async function doReject(a) {
  if (busy.value) return
  if (!comment.value) {
    toast.value = '请填写驳回原因'
    return
  }
  pending.value = a
  busy.value = true
  try {
    await api.approvalReject(a.id, { comment: comment.value })
    toast.value = '已驳回'
    detail.value = null
    comment.value = ''
    load()
  } catch (e) {
    toast.value = e.message
  } finally {
    busy.value = false
  }
}

onMounted(async () => {
  await loadDicts()
  load()
})
</script>

<template>
  <div class="page">
    <NavBar title="领用申请" />

    <div class="card">
      <h3>我的领用</h3>
      <div v-if="!list.length" class="empty">暂无领用单</div>
      <div v-for="a in list" :key="a.id" class="list-item">
        <div class="row">
          <b>{{ a.applyNo }}</b>
          <span class="tag" :class="statusClass(a.status)">{{ tMaterialUseStatus(a.status) }}</span>
        </div>
        <div class="muted">{{ tUseType(a.useType) }} · 需求 {{ a.needDate || '—' }} · {{ a.purpose || '' }}</div>
      </div>
    </div>

    <div class="card">
      <h3>新建申请</h3>
      <div class="field">
        <label>用途类型</label>
        <select v-model="form.useType">
          <option v-for="o in useTypeOptions" :key="o.value" :value="o.value">{{ o.label }}</option>
        </select>
      </div>
      <div class="field">
        <label>项目名称</label>
        <input v-model.trim="form.projectName" placeholder="可选" />
      </div>
      <div class="field">
        <label>需求日期</label>
        <input v-model="form.needDate" type="date" />
      </div>
      <div class="field">
        <label>用途说明</label>
        <textarea v-model.trim="form.purpose" rows="2" placeholder="写清楚用途"></textarea>
      </div>

      <h3 style="margin-top: 8px">物资明细</h3>
      <div class="field">
        <label>搜索物资</label>
        <input v-model.trim="keyword" placeholder="名称/编码" @keyup.enter="searchMat" />
      </div>
      <div v-for="m in mats" :key="m.id" class="list-item" @click="pick(m)">
        <div class="row">
          <span>{{ m.materialName }}</span>
          <span class="tag">加入</span>
        </div>
        <div class="muted">{{ m.materialCode }} · {{ m.spec || '' }} · {{ m.unit || '' }}</div>
      </div>

      <div v-for="(it, idx) in form.items" :key="idx" class="list-item">
        <div class="row">
          <b>{{ it.materialName }}</b>
          <button class="btn plain" style="min-height: 32px" @click="form.items.splice(idx, 1)">删</button>
        </div>
        <div class="qty-box" style="margin-top: 8px">
          <button @click="bump(it, -1)">−</button>
          <input v-model.number="it.qty" type="number" step="0.001" />
          <button @click="bump(it, 1)">+</button>
        </div>
      </div>

      <button class="btn block" style="margin-top: 12px" :disabled="busy" @click="submit">提交申请</button>
    </div>

    <TabBar />
    <Toast :message="toast" />
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { api } from '../api'
import { loadDicts, tMaterialUseStatus, tUseType, dictOptions } from '../utils/dict'
import NavBar from '../components/NavBar.vue'
import TabBar from '../components/TabBar.vue'
import Toast from '../components/Toast.vue'

const list = ref([])
const mats = ref([])
const keyword = ref('')
const busy = ref(false)
const toast = ref('')
const form = reactive({
  useType: 'DAILY',
  projectName: '',
  needDate: '',
  purpose: '',
  items: []
})

const useTypeOptions = computed(() => {
  const opts = dictOptions('USE_TYPE')
  return opts.length ? opts : [
    { value: 'DAILY', label: '日常' },
    { value: 'PROJECT', label: '项目' },
    { value: 'OTHER', label: '其他' }
  ]
})

function statusClass(s) {
  if (s === 'APPROVED' || s === 'ISSUED') return 'ok'
  if (s === 'REJECTED') return 'danger'
  return 'warn'
}

function bump(it, d) {
  it.qty = Math.max(0, Number((Number(it.qty || 0) + d).toFixed(3)))
}

function pick(m) {
  const exist = form.items.find((x) => x.materialId === m.id)
  if (exist) {
    exist.qty = Number((exist.qty + 1).toFixed(3))
    return
  }
  form.items.push({
    materialId: m.id,
    materialName: m.materialName,
    unit: m.unit,
    qty: 1
  })
}

async function searchMat() {
  try {
    const resp = await api.materials(keyword.value)
    mats.value = resp.data?.records || []
  } catch (e) {
    toast.value = e.message
  }
}

async function load() {
  try {
    const resp = await api.useApplyList({ page: 1, size: 10 })
    list.value = resp.data?.records || []
  } catch (e) {
    toast.value = e.message
  }
}

async function submit() {
  if (!form.items.length) {
    toast.value = '请加入至少一项物资'
    return
  }
  busy.value = true
  try {
    await api.useApplyCreate({
      useType: form.useType,
      projectName: form.projectName || null,
      needDate: form.needDate || null,
      purpose: form.purpose || null,
      items: form.items.map((i) => ({
        materialId: i.materialId,
        qty: i.qty,
        unit: i.unit
      }))
    })
    toast.value = '已提交，待部门负责人审批'
    form.items = []
    form.purpose = ''
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
  searchMat()
})
</script>

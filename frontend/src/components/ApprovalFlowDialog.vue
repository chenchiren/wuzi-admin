<template>
  <el-dialog
    v-model="visible"
    :title="title || '审批流程'"
    width="720px"
    top="8vh"
    destroy-on-close
    @closed="onClosed"
  >
    <div v-loading="loading">
      <template v-if="!data.hasInstance">
        <el-empty description="该单据尚未发起审批" :image-size="80" />
      </template>

      <template v-else>
        <el-descriptions :column="2" border size="small" class="mb-3">
          <el-descriptions-item label="审批状态">
            <el-tag size="small" :type="statusTag">{{ tApprovalStatus(data.instance?.status) }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="当前节点">
            {{ data.currentNode?.nodeName || (data.hasFlow ? '-' : '单级审批') }}
          </el-descriptions-item>
          <el-descriptions-item label="当前审批人">
            {{ (data.currentApprovers || []).join('、') || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="发起时间">
            {{ data.instance?.createdAt || '-' }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="(data.nodes || []).length" class="mb-3">
          <div class="section-title">审批节点</div>
          <el-steps :active="activeStep" align-center finish-status="success" process-status="process">
            <el-step
              v-for="n in data.nodes"
              :key="n.id || n.nodeNo"
              :title="n.nodeName"
              :description="nodeDesc(n)"
            />
          </el-steps>
          <div class="mt-2 flex flex-wrap gap-2 text-xs text-slate-500">
            <span v-for="(n, i) in data.nodes" :key="'lg-' + (n.id || n.nodeNo)" class="inline-flex items-center gap-1">
              <span class="dot" :class="nodeDotClass(i)"></span>
              {{ n.nodeName }}{{ nodeStatusText(i) }}
            </span>
          </div>
        </div>

        <div>
          <div class="section-title">审批记录</div>
          <el-timeline v-if="(data.records || []).length">
            <el-timeline-item
              v-for="r in data.records"
              :key="r.id"
              :timestamp="formatTime(r.createdAt)"
              :type="timelineType(r.action)"
              placement="top"
            >
              <div class="text-sm">
                <span class="font-medium">[{{ actionLabel(r.action) }}]</span>
                <span v-if="r.nodeName" class="ml-1">{{ r.nodeName }}</span>
                <span v-if="r.operatorName || r.operatorId" class="ml-1 text-slate-500">
                  · {{ r.operatorName || `#${r.operatorId}` }}
                </span>
              </div>
              <div v-if="r.comment" class="mt-0.5 text-xs text-slate-500">{{ r.comment }}</div>
            </el-timeline-item>
          </el-timeline>
          <el-empty v-else description="暂无审批记录" :image-size="60" />
        </div>
      </template>
    </div>

    <template #footer>
      <div class="flex items-center justify-end gap-2">
        <n-button @click="visible=false">关闭</n-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/request'
import { tApprovalStatus, tApprovalAction, loadDicts } from '../utils/dict'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  bizType: { type: String, required: true },
  bizId: { type: [Number, String], default: null },
  title: { type: String, default: '' }
})
const emit = defineEmits(['update:modelValue'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v)
})

const loading = ref(false)
const data = ref({
  hasInstance: false,
  hasFlow: false,
  instance: null,
  nodes: [],
  records: [],
  currentNode: null,
  currentApprovers: []
})

const statusTag = computed(() => {
  const s = data.value.instance?.status
  if (s === 'APPROVED' || s === 'COMPLETED') return 'success'
  if (s === 'REJECTED' || s === 'CANCELLED') return 'danger'
  if (s === 'PENDING_APPROVAL' || s === 'SUBMITTED' || s === 'PENDING') return 'warning'
  return 'info'
})

const activeStep = computed(() => {
  const nodes = data.value.nodes || []
  const cur = data.value.currentNode
  if (!nodes.length) return 0
  if (!cur) {
    const st = data.value.instance?.status
    if (st === 'APPROVED' || st === 'COMPLETED') return nodes.length
    return 0
  }
  const idx = nodes.findIndex(n => (n.nodeNo && n.nodeNo === cur.nodeNo) || n.id === cur.id)
  return idx >= 0 ? idx : 0
})

function nodeDesc(n) {
  return n.approverRole || (n.assigneeId ? `#${n.assigneeId}` : '')
}

function nodeDotClass(i) {
  const a = activeStep.value
  const st = data.value.instance?.status
  if (i < a || (!data.value.currentNode && (st === 'APPROVED' || st === 'COMPLETED'))) return 'is-done'
  if (i === a && data.value.currentNode) return 'is-active'
  return ''
}

function nodeStatusText(i) {
  const a = activeStep.value
  const st = data.value.instance?.status
  if (i < a || (!data.value.currentNode && (st === 'APPROVED' || st === 'COMPLETED'))) return '（已完成）'
  if (i === a && data.value.currentNode) return '（进行中）'
  return ''
}

function actionLabel(action) {
  return tApprovalAction(action)
}

function timelineType(action) {
  if (action === 'APPROVE' || action === 'START') return 'success'
  if (action === 'REJECT') return 'danger'
  if (action === 'TRANSFER' || action === 'ADD_SIGN') return 'warning'
  return 'primary'
}

function formatTime(t) {
  return t ? String(t).replace('T', ' ').slice(0, 19) : ''
}

async function load() {
  if (!props.bizId) {
    data.value = { hasInstance: false, hasFlow: false, instance: null, nodes: [], records: [], currentNode: null, currentApprovers: [] }
    return
  }
  loading.value = true
  try {
    const res = await api.get('/approvals/by-biz', {
      params: { bizType: props.bizType, bizId: props.bizId }
    })
    data.value = {
      hasInstance: false,
      hasFlow: false,
      instance: null,
      nodes: [],
      records: [],
      currentNode: null,
      currentApprovers: [],
      ...(res.data || {})
    }
  } catch (e) {
    ElMessage.error(e.message || '加载审批流程失败')
  } finally {
    loading.value = false
  }
}

function onClosed() {
  // keep last data until reopen
}

watch(() => visible.value, (v) => {
  if (v) {
    loadDicts()
    load()
  }
})
</script>

<style scoped>
.section-title {
  font-weight: 600;
  margin-bottom: 8px;
  color: #0f172a;
}
.dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #cbd5e1;
  display: inline-block;
}
.dot.is-done { background: #67c23a; }
.dot.is-active { background: #2f6bff; }
</style>

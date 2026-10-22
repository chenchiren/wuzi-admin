<template>
  <el-card v-loading="loading">
    <div class="bar">
      <el-select v-model="bizType" placeholder="业务类型" clearable style="width:170px" @change="load">
        <el-option v-for="t in bizTypes" :key="t.value" :value="t.value" :label="t.label" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <span class="hint">多级流程按节点权限展示；旧单级待办仍可见</span>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column label="业务类型" min-width="130">
        <template #default="{ row }">
          <StatusTag :value="row.bizType" dict-type="BIZ_TYPE" />
        </template>
      </el-table-column>
      <el-table-column prop="bizNo" label="业务单号" min-width="170" />
      <el-table-column prop="title" label="标题" min-width="160" />
      <el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="APPROVAL_STATUS" />
        </template>
      </el-table-column>
      <el-table-column prop="currentNodeName" label="当前节点" min-width="130" />
      <el-table-column prop="createdAt" label="发起时间" min-width="150" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button text type="success" size="small" v-perm="'approval:approve'" @click="act(row, 'approve')">通过</n-button>
            <n-button text type="error" size="small" v-perm="'approval:reject'" @click="act(row, 'reject')">驳回</n-button>
            <n-button v-if="row.hasFlow" text type="warning" size="small" v-perm="'approval:transfer'" @click="transfer(row)">转交</n-button>
            <n-button v-if="row.hasFlow" text type="info" size="small" v-perm="'approval:add-sign'" @click="addSign(row)">加签</n-button>
            <n-button text size="small" @click="openDetail(row)">详情</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="detailDialog" title="审批详情" width="720px">
      <el-descriptions v-if="detail.instance" :column="2" border size="small">
        <el-descriptions-item label="标题">{{ detail.instance.title }}</el-descriptions-item>
        <el-descriptions-item label="单号">{{ detail.instance.bizNo }}</el-descriptions-item>
        <el-descriptions-item label="类型"><StatusTag :value="detail.instance.bizType" dict-type="BIZ_TYPE" /></el-descriptions-item>
        <el-descriptions-item label="状态"><StatusTag :value="detail.instance.status" dict-type="APPROVAL_STATUS" /></el-descriptions-item>
        <el-descriptions-item label="当前节点">{{ detail.currentNode?.nodeName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="当前审批人">{{ (detail.currentApprovers || []).join(',') || '-' }}</el-descriptions-item>
      </el-descriptions>

      <div v-if="(detail.nodes || []).length" style="margin-top:12px">
        <div class="section">流程节点</div>
        <el-steps :active="activeStep" align-center finish-status="success">
          <el-step v-for="n in detail.nodes" :key="n.id" :title="n.nodeName" :description="n.approverRole || ''" />
        </el-steps>
      </div>

      <div style="margin-top:12px">
        <div class="section">审批记录</div>
        <el-timeline>
          <el-timeline-item v-for="r in detail.records || []" :key="r.id" :timestamp="r.createdAt" :type="timelineType(r.action)">
            [{{ tApprovalAction(r.action) }}] {{ r.nodeName || '' }} {{ r.comment || '' }}
          </el-timeline-item>
        </el-timeline>
      </div>

      <template #footer>
        <div class="dlg-footer">
          <div class="dlg-actions">
            <n-button
              type="success"
              :disabled="!canActInDetail"
              :loading="acting"
              @click="actFromDetail('approve')"
            >通过</n-button>
            <n-button
              type="error"
              :disabled="!canActInDetail"
              :loading="acting"
              @click="actFromDetail('reject')"
            >驳回</n-button>
            <n-button
              v-if="detailRow?.hasFlow"
              type="warning"
              quaternary
              :disabled="!canActInDetail"
              @click="transferFromDetail"
            >转交</n-button>
            <n-button
              v-if="detailRow?.hasFlow"
              type="info"
              quaternary
              :disabled="!canActInDetail"
              @click="addSignFromDetail"
            >加签</n-button>
          </div>
          <n-button @click="detailDialog = false">关闭</n-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog
      v-model="pickDialog"
      :title="pickMode === 'transfer' ? '转交审批' : '加签'"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form label-width="80px" @submit.prevent>
        <el-form-item label="目标用户" required>
          <el-select
            v-model="pickUserId"
            filterable
            clearable
            class="w-full"
            placeholder="输入姓名或用户名搜索"
            :loading="usersLoading"
          >
            <el-option
              v-for="u in userOptions"
              :key="u.value"
              :label="u.label"
              :value="u.value"
            />
          </el-select>
        </el-form-item>
        <el-form-item :label="pickMode === 'transfer' ? '转交说明' : '加签说明'">
          <el-input
            v-model="pickComment"
            type="textarea"
            :rows="3"
            :placeholder="pickMode === 'transfer' ? '可选，将通知对方' : '可选，将通知加签人'"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex justify-end gap-2">
          <n-button @click="pickDialog = false">取消</n-button>
          <n-button type="primary" :loading="acting" :disabled="!pickUserId" @click="submitPick">
            确认{{ pickMode === 'transfer' ? '转交' : '加签' }}
          </n-button>
        </div>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { tApprovalStatus, tBiz, tApprovalAction, loadDicts, dictOptionsOr } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const bizType = ref('')
const detailDialog = ref(false)
const detail = ref({})
const detailRow = ref(null)
const loading = ref(false)
const acting = ref(false)

const canActInDetail = computed(() => {
  const st = detail.value.instance?.status
  return detailRow.value && st === 'PENDING'
})

const bizTypes = computed(() =>
  dictOptionsOr('BIZ_TYPE', [
    'PURCHASE_APPLY', 'PURCHASE_ORDER', 'PURCHASE_RETURN', 'MATERIAL_USE',
    'BORROW', 'SCRAP', 'TRANSFER', 'RETURN_STORE', 'INVENTORY_CHECK',
    'PURCHASE_ARRIVAL', 'OUTBOUND', 'PURCHASE_INQUIRY'
  ])
)

const activeStep = computed(() => {
  const nodes = detail.value.nodes || []
  const cur = detail.value.currentNode
  if (!cur) return 0
  const idx = nodes.findIndex(n => n.nodeNo === cur.nodeNo)
  return idx >= 0 ? idx : 0
})

async function load() {
  loading.value = true
  try {
    const res = await api.get('/approvals/todo', {
      params: { page: page.value, size: 10, bizType: bizType.value }
    })
    rows.value = res.data.records || []
    total.value = res.data.total
  } finally {
    loading.value = false
  }
}

async function act(row, action) {
  let value = ''
  try {
    const r = await ElMessageBox.prompt(
      action === 'approve' ? '审批意见（可选）' : '驳回原因（可选）',
      action === 'approve' ? '审批通过' : '审批驳回',
      { inputPlaceholder: '意见', confirmButtonText: action === 'approve' ? '通过' : '驳回' }
    )
    value = r.value || ''
  } catch {
    return
  }
  try {
    await api.post(`/approvals/${row.id}/${action}`, { comment: value })
    ElMessage.success(action === 'approve' ? '已通过' : '已驳回')
    load()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  }
}

/** 详情弹窗内直接通过/驳回，避免关掉再操作漏看信息 */
async function actFromDetail(action) {
  if (!detailRow.value || !canActInDetail.value) return
  let value = ''
  try {
    const r = await ElMessageBox.prompt(
      action === 'approve' ? '审批意见（可选）' : '驳回原因（可选）',
      action === 'approve' ? '审批通过' : '审批驳回',
      { inputPlaceholder: '意见', confirmButtonText: action === 'approve' ? '通过' : '驳回' }
    )
    value = r.value || ''
  } catch {
    return
  }
  acting.value = true
  try {
    await api.post(`/approvals/${detailRow.value.id}/${action}`, { comment: value })
    ElMessage.success(action === 'approve' ? '已通过' : '已驳回')
    await refreshDetail(detailRow.value.id)
    load()
  } catch (e) {
    ElMessage.error(e.message || '操作失败')
  } finally {
    acting.value = false
  }
}

async function transferFromDetail() {
  if (!detailRow.value) return
  openPick(detailRow.value, 'transfer')
}

async function addSignFromDetail() {
  if (!detailRow.value) return
  openPick(detailRow.value, 'add-sign')
}

async function refreshDetail(id) {
  try {
    const res = await api.get(`/approvals/${id}`)
    detail.value = res.data || {}
    if (detail.value.instance) {
      detailRow.value = { ...detailRow.value, ...detail.value.instance, hasFlow: detailRow.value?.hasFlow ?? detail.value.hasFlow }
    }
  } catch (e) {
    ElMessage.error(e.message || '刷新详情失败')
  }
}

const pickDialog = ref(false)
const pickMode = ref('transfer')
const pickUserId = ref(null)
const pickComment = ref('')
const pickRow = ref(null)
const userOptions = ref([])
const usersLoading = ref(false)

async function loadUserOptions() {
  if (userOptions.value.length) return
  usersLoading.value = true
  try {
    const res = await api.get('/flow-approvals/flows/config-options')
    userOptions.value = res.data?.users || []
  } catch (e) {
    ElMessage.error(e.message || '加载用户列表失败')
  } finally {
    usersLoading.value = false
  }
}

function openPick(row, mode) {
  pickRow.value = row
  pickMode.value = mode
  pickUserId.value = null
  pickComment.value = ''
  pickDialog.value = true
  loadUserOptions()
}

function transfer(row) {
  openPick(row, 'transfer')
}

function addSign(row) {
  openPick(row, 'add-sign')
}

async function submitPick() {
  if (!pickRow.value || !pickUserId.value) return
  const isTransfer = pickMode.value === 'transfer'
  acting.value = true
  try {
    const payload = isTransfer
      ? { targetUserId: Number(pickUserId.value), comment: pickComment.value || '' }
      : { signUserId: Number(pickUserId.value), comment: pickComment.value || '' }
    await api.post(`/approvals/${pickRow.value.id}/${isTransfer ? 'transfer' : 'add-sign'}`, payload)
    ElMessage.success(isTransfer ? '已转交' : '已加签')
    pickDialog.value = false
    load()
    if (detailDialog.value && detailRow.value) {
      await refreshDetail(detailRow.value.id)
    }
  } catch (e) {
    ElMessage.error(e.message || (isTransfer ? '转交失败' : '加签失败'))
  } finally {
    acting.value = false
  }
}

async function openDetail(row) {
  try {
    detailRow.value = { ...row }
    const res = await api.get(`/approvals/${row.id}`)
    detail.value = res.data || {}
    detailDialog.value = true
  } catch (e) {
    ElMessage.error(e.message || '加载详情失败')
  }
}

function timelineType(action) {
  if (action === 'APPROVE' || action === 'START') return 'success'
  if (action === 'REJECT') return 'danger'
  return 'primary'
}

onMounted(async () => {
  await loadDicts()
  load()
  loadUserOptions()
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; align-items:center; }
.hint { color:#909399; font-size:12px; }
.section { font-weight:600; margin-bottom:8px; }
.dlg-footer { display:flex; align-items:center; justify-content:space-between; gap:12px; }
.dlg-actions { display:flex; flex-wrap:wrap; gap:8px; }
</style>

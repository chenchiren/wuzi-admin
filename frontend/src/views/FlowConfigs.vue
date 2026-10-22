<template>
  <el-card v-loading="loading">
    <div class="bar">
      <n-button type="success" v-perm="'flow:config:create'" @click="openCreateFlow">新建流程</n-button>
      <n-button @click="load">刷新</n-button>
      <span class="muted">可配置流程定义与审批节点；小组长=本组领导，部门负责人=部门节点正职，副部长用按职位+本部门</span>
    </div>
    <el-table :data="flows" border stripe>
      <el-table-column prop="flowCode" label="流程编码" min-width="140" />
      <el-table-column prop="flowName" label="流程名称" min-width="160" />
      <el-table-column label="业务类型" min-width="130">
        <template #default="{ row }">
          <StatusTag :value="row.bizType" dict-type="BIZ_TYPE" />
        </template>
      </el-table-column>
      <el-table-column label="状态" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="tFlowStatus(row.status === 1 || row.status === '1' ? '1' : '0')" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="150" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text type="primary" @click="openNodes(row)">节点配置</n-button>
            <n-button size="small" text type="primary" v-perm="'flow:config:update'" @click="openEditFlow(row)">编辑</n-button>
            <n-button size="small" text type="warning" v-perm="'flow:config:update'" @click="toggleFlow(row)">
              {{ row.status === 1 ? '停用' : '启用' }}
            </n-button>
            <n-button size="small" text type="error" v-perm="'flow:config:delete'" @click="removeFlow(row)">删除</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 流程编辑 -->
    <el-dialog v-model="flowDialog" :title="flowForm.id ? '编辑流程' : '新建流程'" width="520px">
      <el-form label-width="100px">
        <el-form-item label="流程编码" required>
          <el-input v-model="flowForm.flowCode" placeholder="如 PURCHASE_APPLY" :disabled="!!flowForm.id" />
        </el-form-item>
        <el-form-item label="流程名称" required>
          <el-input v-model="flowForm.flowName" placeholder="如 采购申请审批" />
        </el-form-item>
        <el-form-item label="业务类型">
          <el-select v-model="flowForm.bizType" filterable clearable class="w-full" placeholder="选择业务类型">
            <el-option v-for="o in bizTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="flowForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="flowForm.remark" type="textarea" :rows="2" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="flowDialog=false">取消</n-button>
          <n-button type="primary" @click="saveFlow">保存</n-button>
        </div>
      </template>
    </el-dialog>

    <!-- 节点配置 -->
    <el-dialog v-model="nodeDialog" :title="`节点配置 - ${currentFlow?.flowCode || ''}`" width="860px">
      <div class="bar">
        <n-button type="success" size="small" v-perm="'flow:config:create'" @click="openCreateNode">添加节点</n-button>
        <span class="muted">按「顺序」从小到大依次审批</span>
      </div>
      <el-table :data="nodes" border size="small">
        <el-table-column prop="nodeNo" label="节点编码" min-width="100" />
        <el-table-column prop="nodeName" label="节点名称" min-width="130" />
        <el-table-column label="审批人类型" min-width="110">
          <template #default="{ row }">{{ tApproverType(row.approverType || 'ROLE') }}</template>
        </el-table-column>
        <el-table-column label="审批目标" min-width="150">
          <template #default="{ row }">{{ approverTarget(row) }}</template>
        </el-table-column>
        <el-table-column label="部门范围" min-width="90">
          <template #default="{ row }">{{ tDeptScope(row.deptScope || 'ALL') }}</template>
        </el-table-column>
        <el-table-column prop="orderNo" label="顺序" min-width="70" />
        <el-table-column label="操作" min-width="140">
          <template #default="{ row }">
            <div class="flex flex-wrap items-center gap-2">
              <n-button size="small" text type="primary" v-perm="'flow:config:update'" @click="openEditNode(row)">编辑</n-button>
              <n-button size="small" text type="error" v-perm="'flow:config:delete'" @click="removeNode(row)">删除</n-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- 节点编辑 -->
    <el-dialog v-model="nodeFormDialog" :title="nodeForm.id ? '编辑节点' : '添加节点'" width="560px">
      <el-form label-width="100px">
        <el-form-item label="节点编码" required>
          <el-input v-model="nodeForm.nodeNo" placeholder="如 N1" />
        </el-form-item>
        <el-form-item label="节点名称" required>
          <el-input v-model="nodeForm.nodeName" placeholder="如 部门负责人审批" />
        </el-form-item>
        <el-form-item label="顺序">
          <el-input-number v-model="nodeForm.orderNo" :min="1" :max="99" class="w-full" />
        </el-form-item>
        <el-form-item label="审批人类型">
          <el-select v-model="nodeForm.approverType" class="w-full" @change="onApproverTypeChange">
            <el-option v-for="o in approverTypeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="nodeForm.approverType === 'USER'" label="指定人员" required>
          <el-select v-model="nodeForm.assigneeId" filterable class="w-full" placeholder="选择审批人">
            <el-option v-for="o in userOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="nodeForm.approverType === 'ROLE'" label="审批角色" required>
          <el-select v-model="nodeForm.approverRole" filterable class="w-full" placeholder="选择角色">
            <el-option v-for="o in roleOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="nodeForm.approverType === 'POSITION'" label="审批职位" required>
          <el-select v-model="nodeForm.positionCode" filterable class="w-full" placeholder="选择职位">
            <el-option v-for="o in positionOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
        </el-form-item>
        <el-form-item
          v-if="nodeForm.approverType === 'POSITION' || nodeForm.approverType === 'DEPT_LEADER'"
          label="部门范围"
        >
          <el-select v-model="nodeForm.deptScope" class="w-full">
            <el-option v-for="o in deptScopeOptions" :key="o.value" :label="o.label" :value="o.value" />
          </el-select>
          <div class="muted">副部长/部长用「按职位 + 本部门含兄弟组」；部门负责人自动向上找到部门节点正职</div>
        </el-form-item>
        <div v-if="nodeForm.approverType === 'TEAM_LEADER'" class="muted mb-2">
          解析申请人所在小组的组长；无小组时回退部门领导
        </div>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="nodeFormDialog=false">取消</n-button>
          <n-button type="primary" @click="saveNode">保存</n-button>
        </div>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import {
  tFlowStatus, tApproverType, tDeptScope,
  dictOptionsOr, loadDicts
} from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const flows = ref([])
const nodes = ref([])
const currentFlow = ref(null)
const nodeDialog = ref(false)
const flowDialog = ref(false)
const nodeFormDialog = ref(false)
const loading = ref(false)

const roleOptions = ref([])
const positionOptions = ref([])
const userOptions = ref([])

const flowForm = reactive({
  id: null, flowCode: '', flowName: '', bizType: '', status: 1, remark: ''
})
const nodeForm = reactive({
  id: null, nodeNo: '', nodeName: '', orderNo: 1,
  approverType: 'ROLE', approverRole: '', assigneeId: null,
  positionCode: '', deptScope: 'ALL'
})

const bizTypeOptions = computed(() => dictOptionsOr('BIZ_TYPE', [
  'PURCHASE_APPLY', 'PURCHASE_ORDER', 'PURCHASE_RETURN', 'PURCHASE_INQUIRY', 'PURCHASE_ARRIVAL',
  'MATERIAL_USE', 'BORROW', 'SCRAP', 'TRANSFER', 'RETURN_STORE', 'INVENTORY_CHECK', 'OUTBOUND'
]))
const approverTypeOptions = computed(() => dictOptionsOr('APPROVER_TYPE', [
  'USER', 'ROLE', 'POSITION', 'DEPT_LEADER', 'TEAM_LEADER'
]))
const deptScopeOptions = computed(() => dictOptionsOr('DEPT_SCOPE', ['ALL', 'SELF', 'DEPT', 'PARENT']))

function approverTarget(row) {
  const type = (row.approverType || 'ROLE').toUpperCase()
  if (type === 'DEPT_LEADER') return '部门负责人'
  if (type === 'TEAM_LEADER') return '小组长'
  if (type === 'USER') {
    const u = userOptions.value.find(x => x.value === row.assigneeId)
    return u ? u.label : (row.assigneeId ? `用户 #${row.assigneeId}` : '—')
  }
  if (type === 'POSITION') {
    const p = positionOptions.value.find(x => x.value === row.positionCode)
    return p ? p.label : (row.positionCode || '—')
  }
  const r = roleOptions.value.find(x => x.value === row.approverRole)
  return r ? r.label : (row.approverRole || '—')
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/flow-approvals/flows')
    flows.value = res.data || []
  } catch (e) { ElMessage.error(e.message || '加载失败') } finally { loading.value = false }
}

async function loadOptions() {
  try {
    const res = await api.get('/flow-approvals/flows/config-options')
    const d = res.data || {}
    roleOptions.value = d.roles || []
    positionOptions.value = d.positions || []
    userOptions.value = d.users || []
  } catch (e) {
    ElMessage.error(e.message || '加载配置选项失败')
  }
}

function openCreateFlow() {
  Object.assign(flowForm, {
    id: null, flowCode: '', flowName: '', bizType: '', status: 1, remark: ''
  })
  flowDialog.value = true
}

function openEditFlow(row) {
  Object.assign(flowForm, {
    id: row.id,
    flowCode: row.flowCode || '',
    flowName: row.flowName || '',
    bizType: row.bizType || '',
    status: row.status ?? 1,
    remark: row.remark || ''
  })
  flowDialog.value = true
}

async function saveFlow() {
  if (!flowForm.flowCode?.trim()) return ElMessage.warning('请填写流程编码')
  if (!flowForm.flowName?.trim()) return ElMessage.warning('请填写流程名称')
  try {
    const payload = {
      flowCode: flowForm.flowCode.trim(),
      flowName: flowForm.flowName.trim(),
      bizType: flowForm.bizType || '',
      status: flowForm.status,
      remark: flowForm.remark
    }
    if (flowForm.id) await api.put(`/flow-approvals/flows/${flowForm.id}`, payload)
    else await api.post('/flow-approvals/flows', payload)
    ElMessage.success('已保存')
    flowDialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

async function toggleFlow(row) {
  try {
    await api.put(`/flow-approvals/flows/${row.id}`, { status: row.status === 1 ? 0 : 1 })
    ElMessage.success(row.status === 1 ? '已停用' : '已启用')
    load()
  } catch (e) { ElMessage.error(e.message || '操作失败') }
}

async function removeFlow(row) {
  await ElMessageBox.confirm(`确认删除流程「${row.flowName}」及其全部节点？`, '提示')
  try {
    await api.delete(`/flow-approvals/flows/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

async function openNodes(row) {
  currentFlow.value = row
  try {
    const res = await api.get(`/flow-approvals/flows/${row.flowCode}/nodes`)
    nodes.value = res.data || []
    nodeDialog.value = true
  } catch (e) { ElMessage.error(e.message || '加载节点失败') }
}

function nextOrderNo() {
  const max = nodes.value.reduce((m, n) => Math.max(m, Number(n.orderNo) || 0), 0)
  return max + 1
}

function openCreateNode() {
  Object.assign(nodeForm, {
    id: null, nodeNo: `N${nextOrderNo()}`, nodeName: '', orderNo: nextOrderNo(),
    approverType: 'ROLE', approverRole: '', assigneeId: null,
    positionCode: '', deptScope: 'ALL'
  })
  nodeFormDialog.value = true
}

function openEditNode(row) {
  const type = (row.approverType || 'ROLE').toUpperCase()
  Object.assign(nodeForm, {
    id: row.id,
    nodeNo: row.nodeNo || '',
    nodeName: row.nodeName || '',
    orderNo: row.orderNo ?? nextOrderNo(),
    approverType: type,
    approverRole: row.approverRole || '',
    assigneeId: row.assigneeId ?? null,
    positionCode: row.positionCode || '',
    deptScope: (row.deptScope || 'ALL').toUpperCase()
  })
  nodeFormDialog.value = true
}

function onApproverTypeChange(type) {
  if (type === 'DEPT_LEADER' && (nodeForm.deptScope === 'ALL' || nodeForm.deptScope === '')) {
    nodeForm.deptScope = 'DEPT'
  }
  if (type === 'POSITION' && (nodeForm.deptScope === 'ALL' || nodeForm.deptScope === '')) {
    nodeForm.deptScope = 'DEPT'
  }
  if (type === 'USER') {
    nodeForm.approverRole = ''
    nodeForm.positionCode = ''
  } else if (type === 'ROLE') {
    nodeForm.assigneeId = null
    nodeForm.positionCode = ''
  } else if (type === 'POSITION') {
    nodeForm.assigneeId = null
    nodeForm.approverRole = ''
  } else if (type === 'DEPT_LEADER' || type === 'TEAM_LEADER') {
    nodeForm.assigneeId = null
    nodeForm.approverRole = ''
    nodeForm.positionCode = ''
  }
}

async function saveNode() {
  if (!nodeForm.nodeNo?.trim()) return ElMessage.warning('请填写节点编码')
  if (!nodeForm.nodeName?.trim()) return ElMessage.warning('请填写节点名称')
  const type = nodeForm.approverType
  if (type === 'USER' && !nodeForm.assigneeId) return ElMessage.warning('请选择指定人员')
  if (type === 'ROLE' && !nodeForm.approverRole) return ElMessage.warning('请选择审批角色')
  if (type === 'POSITION' && !nodeForm.positionCode) return ElMessage.warning('请选择审批职位')
  try {
    const payload = {
      nodeNo: nodeForm.nodeNo.trim(),
      nodeName: nodeForm.nodeName.trim(),
      orderNo: nodeForm.orderNo,
      approverType: type,
      approverRole: type === 'ROLE' ? nodeForm.approverRole : null,
      assigneeId: type === 'USER' ? nodeForm.assigneeId : null,
      positionCode: type === 'POSITION' ? nodeForm.positionCode : null,
      deptScope: nodeForm.deptScope || 'ALL'
    }
    if (nodeForm.id) await api.put(`/flow-approvals/flows/nodes/${nodeForm.id}`, payload)
    else await api.post(`/flow-approvals/flows/${currentFlow.value.id}/nodes`, payload)
    ElMessage.success('已保存')
    nodeFormDialog.value = false
    openNodes(currentFlow.value)
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

async function removeNode(row) {
  await ElMessageBox.confirm(`确认删除节点「${row.nodeName}」？`, '提示')
  try {
    await api.delete(`/flow-approvals/flows/nodes/${row.id}`)
    ElMessage.success('已删除')
    openNodes(currentFlow.value)
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

onMounted(async () => {
  await loadDicts()
  await Promise.all([load(), loadOptions()])
})
</script>

<style scoped>
.bar { display:flex; align-items:center; gap:8px; margin-bottom:12px; flex-wrap: wrap; }
.muted { color:#888; font-size:12px; }
.w-full { width:100%; }
</style>

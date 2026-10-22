<template>
  <el-card>
    <div class="bar">
      <el-input v-model="keyword" placeholder="用户名/姓名/手机" style="width:200px" clearable @change="load" />
      <el-select v-model="deptId" placeholder="部门" clearable style="width:160px" @change="load">
        <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'sys:user:create'" @click="openCreate">新建用户</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="username" label="用户名" min-width="110" />
      <el-table-column prop="realName" label="姓名" min-width="100" />
      <el-table-column prop="phone" label="手机" min-width="120" />
      <el-table-column prop="email" label="邮箱" min-width="140" />
      <el-table-column label="部门" min-width="110">
        <template #default="{ row }">{{ deptName(row.deptId) }}</template>
      </el-table-column>
      <el-table-column label="职位" min-width="100">
        <template #default="{ row }">
          <el-tag v-if="positionName(row.positionId)" size="small">{{ positionName(row.positionId) }}</el-tag>
          <span v-else class="muted">—</span>
        </template>
      </el-table-column>
      <el-table-column label="角色" min-width="130">
        <template #default="{ row }">{{ (row.roles || []).map(r => tRole(r)).join(', ') }}</template>
      </el-table-column>
      <el-table-column label="状态" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="tNumStatus(row.status)" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="260">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text type="primary" v-perm="'sys:user:update'" @click="openEdit(row)">编辑</n-button>
            <n-button size="small" text type="primary" v-perm="'sys:user:reset-pwd'" @click="resetPwd(row)">重置密码</n-button>
            <n-button size="small" text v-perm="'sys:user:assign-role'" @click="assignRoles(row)">分配角色</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="dialog" :title="form.id ? '编辑用户' : '新建用户'" width="480px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item v-if="!form.id" label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
        </el-form-item>
        <el-form-item label="姓名" prop="realName">
          <el-input v-model="form.realName" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="手机">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-form-item label="邮箱">
          <el-input v-model="form.email" />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.deptId" clearable filterable class="w-full" placeholder="选择部门">
            <el-option v-for="d in depts" :key="d.id" :label="d.deptName" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="职位">
          <el-select v-model="form.positionId" clearable filterable class="w-full" placeholder="选择职位（部长/副部长…）">
            <el-option v-for="p in positions" :key="p.id"
              :label="`${p.positionName}（L${p.positionLevel}${p.isLeader === 1 ? '·领导' : ''}）`"
              :value="p.id" />
          </el-select>
          <div class="muted">职位决定审批流里的组织身份，与角色权限分离</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="dialog=false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="roleDialog" title="分配角色" width="440px">
      <div class="role-user">
        <span class="muted">用户：</span>
        <b>{{ roleUser?.realName || roleUser?.username }}</b>
        <span class="muted">（{{ roleUser?.username }}）</span>
      </div>
      <el-checkbox-group v-model="selectedRoleIds" class="role-list">
        <el-checkbox v-for="r in allRoles" :key="r.id" :value="r.id" class="role-item">
          <span class="role-code">{{ r.roleCode }}</span>
          <span class="role-name">{{ r.roleName }}</span>
          <span v-if="r.remark" class="muted">{{ r.remark }}</span>
        </el-checkbox>
      </el-checkbox-group>
      <div class="muted" style="margin-top:8px">可多选；取消全部勾选即清空该用户角色</div>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="roleDialog=false">取消</n-button>
          <n-button type="primary" :loading="roleSaving" @click="saveRoles">确定</n-button>
        </div>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { tRole, tNumStatus, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const deptId = ref(null)
const depts = ref([])
const positions = ref([])
const dialog = ref(false)
const roleDialog = ref(false)
const roleSaving = ref(false)
const allRoles = ref([])
const selectedRoleIds = ref([])
const roleUser = ref(null)
const form = reactive({
  id: null, username: '', password: '', realName: '', phone: '', email: '',
  deptId: null, positionId: null
})
const formRef = ref()
const rules = computed(() => ({
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  // 编辑时密码字段隐藏，不应校验
  ...(form.id
    ? {}
    : { password: [{ required: true, message: '请输入密码', trigger: 'blur' }] }),
  realName: [{ required: true, message: '请输入姓名', trigger: 'blur' }]
}))

function deptName(id) {
  if (id == null) return '—'
  return depts.value.find(d => d.id === id)?.deptName || id
}

function positionName(id) {
  if (id == null) return ''
  return positions.value.find(p => p.id === id)?.positionName || ''
}

async function loadMeta() {
  const [d, p] = await Promise.all([
    api.get('/depts'),
    api.get('/positions')
  ])
  depts.value = d.data || []
  positions.value = p.data || []
}

async function load() {
  const res = await api.get('/users', {
    params: { page: page.value, size: 10, keyword: keyword.value, deptId: deptId.value || undefined }
  })
  rows.value = res.data.records
  total.value = res.data.total
}

function openCreate() {
  Object.assign(form, {
    id: null, username: '', password: '', realName: '', phone: '', email: '',
    deptId: null, positionId: null
  })
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    username: row.username,
    password: '',
    realName: row.realName,
    phone: row.phone,
    email: row.email,
    deptId: row.deptId ?? null,
    positionId: row.positionId ?? null
  })
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

async function save() {
  try {
    await formRef.value.validate()
  } catch {
    ElMessage.warning('请完善必填项后再保存')
    return
  }
  try {
    if (form.id) await api.put(`/users/${form.id}`, { ...form })
    else await api.post('/users', { ...form })
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

async function resetPwd(row) {
  const { value } = await ElMessageBox.prompt('新密码（8位以上含字母数字，留空生成默认密码）', `重置 ${row.username} 密码`, {
    inputPattern: /.*/, inputErrorMessage: ''
  })
  try {
    const res = await api.put(`/users/${row.id}/password`, { password: value || '', reset: true })
    const d = res.data || {}
    if (d.defaultPassword) {
      await ElMessageBox.alert(`${d.hint || '密码已重置'}\n默认密码：${d.defaultPassword}`, '重置成功', { type: 'warning' })
    } else {
      ElMessage.success('已重置')
    }
  } catch (e) { ElMessage.error(e.message || '重置失败') }
}

async function assignRoles(row) {
  if (!allRoles.value.length) {
    const all = await api.get('/roles/all')
    allRoles.value = all.data || []
  }
  roleUser.value = row
  selectedRoleIds.value = [...(row.roleIds || [])]
  roleDialog.value = true
}

async function saveRoles() {
  if (!roleUser.value) return
  roleSaving.value = true
  try {
    await api.post(`/users/${roleUser.value.id}/roles`, { roleIds: selectedRoleIds.value })
    ElMessage.success('已分配')
    roleDialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '分配失败')
  } finally {
    roleSaving.value = false
  }
}

onMounted(async () => {
  await loadDicts()
  await loadMeta()
  load()
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; align-items:center; }
.muted { color:#888; font-size:12px; }
.w-full { width:100%; }
.role-user { margin-bottom: 12px; font-size: 14px; }
.role-list { display:flex; flex-direction:column; gap:4px; }
.role-item { height:auto; padding:8px 10px; margin-right:0 !important; border:1px solid #e5e7eb; border-radius:6px; }
.role-item:hover { border-color:#c0c4cc; }
.role-code { display:inline-block; min-width:110px; font-family: Consolas, Menlo, monospace; font-size:12px; color:#4b5563; }
.role-name { margin-right:8px; font-weight:500; }
</style>

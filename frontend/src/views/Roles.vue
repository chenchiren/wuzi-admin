<template>
  <el-card v-loading="loading">
    <div class="bar">
      <el-input v-model="keyword" placeholder="编码/名称" style="width:200px" clearable @change="load" />
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'sys:role:create'" @click="openCreate">新建角色</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="id" label="ID" min-width="70" />
      <el-table-column prop="roleCode" label="编码" min-width="140" />
      <el-table-column prop="roleName" label="名称" min-width="140" />
      <el-table-column prop="remark" label="备注" min-width="140" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text type="primary" v-perm="'sys:role:update'" @click="openEdit(row)">编辑</n-button>
            <n-button size="small" text type="warning" v-perm="'sys:role:assign-perm'" @click="openPerms(row)">权限</n-button>
            <n-button size="small" text type="error" v-perm="'sys:role:delete'" @click="remove(row)">删除</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="dialog" :title="form.id ? '编辑角色' : '新建角色'" width="420px">
      <el-form label-width="90px">
        <el-form-item label="编码"><el-input v-model="form.roleCode" /></el-form-item>
        <el-form-item label="名称"><el-input v-model="form.roleName" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="dialog=false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
        </div>
      </template>
    </el-dialog>

    <!-- 权限分配 -->
    <el-dialog
      v-model="permDialog"
      class="perm-dialog"
      width="760px"
      top="6vh"
      destroy-on-close
      :show-close="false"
    >
      <template #header>
        <div class="perm-head">
          <div class="perm-head-left">
            <div class="perm-avatar">{{ (currentRole?.roleName || '角')[0] }}</div>
            <div>
              <div class="perm-title">分配权限</div>
              <div class="perm-sub">
                角色 <b>{{ currentRole?.roleName || '—' }}</b>
                <span class="perm-code">{{ currentRole?.roleCode || '' }}</span>
              </div>
            </div>
          </div>
          <div class="perm-head-right">
            <div class="perm-stat">
              <span class="perm-stat-n">{{ checkedCount }}</span>
              <span class="perm-stat-l">已选</span>
            </div>
            <button class="perm-close" type="button" @click="permDialog = false">✕</button>
          </div>
        </div>
      </template>

      <div class="perm-toolbar">
        <el-input
          v-model="permFilter"
          class="perm-search"
          placeholder="搜索菜单 / 权限码 / 按钮"
          clearable
          :prefix-icon="Search"
        />
        <div class="perm-filters" role="tablist">
          <button
            v-for="f in typeFilters"
            :key="f.value"
            type="button"
            class="perm-chip"
            :class="{ 'is-on': typeFilter === f.value }"
            :aria-pressed="typeFilter === f.value"
            @click.stop="setTypeFilter(f.value)"
          >{{ f.label }}</button>
        </div>
        <div class="perm-actions">
          <n-button size="small" quaternary @click="checkAll">全选</n-button>
          <n-button size="small" quaternary @click="invertCheck">反选</n-button>
          <n-button size="small" quaternary @click="clearCheck">清空</n-button>
          <n-button size="small" quaternary @click="toggleExpand">
            {{ expanded ? '收起' : '展开' }}
          </n-button>
        </div>
      </div>

      <div class="perm-body">
        <el-tree
          :key="treeKey"
          ref="treeRef"
          class="perm-tree"
          :data="filteredTree"
          node-key="id"
          show-checkbox
          default-expand-all
          :expand-on-click-node="false"
          :check-strictly="false"
          :props="{ label: 'permName', children: 'children' }"
          @check="onCheck"
        >
          <template #default="{ data }">
            <div class="perm-node" :class="`is-${(data.permType || 'MENU').toLowerCase()}`">
              <span class="perm-ico" aria-hidden="true">
                <svg v-if="data.permType === 'BUTTON'" viewBox="0 0 16 16" width="14" height="14">
                  <rect x="2" y="4" width="12" height="8" rx="2" fill="currentColor" opacity=".2"/>
                  <rect x="3.5" y="5.5" width="9" height="5" rx="1.5" fill="none" stroke="currentColor" stroke-width="1.2"/>
                </svg>
                <svg v-else viewBox="0 0 16 16" width="14" height="14">
                  <path d="M2.5 4.5h4l1.2 1.5H13.5v6.5a1 1 0 0 1-1 1h-9a1 1 0 0 1-1-1v-7a1 1 0 0 1 1-1z"
                    fill="currentColor" opacity=".18" stroke="currentColor" stroke-width="1.2" stroke-linejoin="round"/>
                </svg>
              </span>
              <span class="perm-name">{{ data.permName }}</span>
              <span v-if="data.permType === 'BUTTON'" class="perm-tag is-btn">按钮</span>
              <span v-else-if="data.permType === 'MENU' && data.path" class="perm-tag is-menu">菜单</span>
              <span v-if="data.permCode" class="perm-code-inline">{{ data.permCode }}</span>
            </div>
          </template>
        </el-tree>
        <div v-if="!filteredTree.length" class="perm-empty">没有匹配的权限</div>
      </div>

      <template #footer>
        <div class="perm-foot">
          <div class="perm-hint">勾选菜单与按钮；子节点勾选会带上父级</div>
          <div class="flex items-center gap-2">
            <n-button @click="permDialog = false">取消</n-button>
            <n-button type="primary" :loading="saving" @click="savePerms">保存权限</n-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import api from '../api/request'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const dialog = ref(false)
const permDialog = ref(false)
const loading = ref(false)
const saving = ref(false)
const form = reactive({ id: null, roleCode: '', roleName: '', remark: '' })
const currentRole = ref(null)
const permTree = ref([])
const treeRef = ref(null)
const permFilter = ref('')
const typeFilter = ref('ALL')
const expanded = ref(true)
const checkedCount = ref(0)
const lastChecked = ref([])

const typeFilters = [
  { value: 'ALL', label: '全部' },
  { value: 'MENU', label: '菜单' },
  { value: 'BUTTON', label: '按钮' }
]

const treeKey = ref(0)

const filteredTree = computed(() => filterTree(permTree.value))

watch([permFilter, typeFilter], () => {
  treeKey.value += 1
  nextTick(() => {
    if (!treeRef.value) return
    const keys = lastChecked.value?.length
      ? lastChecked.value
      : (treeRef.value.getCheckedKeys?.(false) || [])
    treeRef.value.setCheckedKeys(keys)
    refreshCount()
  })
})

function matchType(n, tip) {
  return tip === 'ALL' || (n.permType || 'MENU') === tip
}

function matchKw(n, kw) {
  if (!kw) return true
  return (
    String(n.permName || '').toLowerCase().includes(kw) ||
    String(n.permCode || '').toLowerCase().includes(kw) ||
    String(n.path || '').toLowerCase().includes(kw)
  )
}

/**
 * 类型/关键词过滤。
 * - 选「菜单」：只留菜单节点（按钮全部隐藏）
 * - 选「按钮」：只留按钮，菜单作分组容器
 * - 自身命中关键词：展开其类型匹配的子级
 */
function filterTree(list) {
  const kw = permFilter.value.trim().toLowerCase()
  const tip = typeFilter.value

  const walk = (nodes) => {
    const out = []
    for (const n of nodes || []) {
      const kids = walk(n.children)
      const selfOk = matchType(n, tip) && matchKw(n, kw)
      if (selfOk || kids.length) {
        let children = kids
        // 父节点被搜到且子级无命中时，展示类型过滤后的直接子级（仍递归处理孙级）
        if (selfOk && kw && !kids.length && n.children?.length) {
          children = walk(n.children)
        }
        out.push({ ...n, children })
      }
    }
    return out
  }
  return walk(list)
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/roles', { params: { page: page.value, size: 10, keyword: keyword.value } })
    rows.value = res.data.records
    total.value = res.data.total
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function openCreate() {
  Object.assign(form, { id: null, roleCode: '', roleName: '', remark: '' })
  dialog.value = true
}

function openEdit(row) {
  Object.assign(form, { id: row.id, roleCode: row.roleCode, roleName: row.roleName, remark: row.remark })
  dialog.value = true
}

async function save() {
  try {
    if (form.id) await api.put(`/roles/${form.id}`, { ...form })
    else await api.post('/roles', { ...form })
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

async function remove(row) {
  await ElMessageBox.confirm(`确认删除角色 ${row.roleName}？`, '提示')
  try {
    await api.delete(`/roles/${row.id}`)
    ElMessage.success('已删除')
    load()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

function collectIds(nodes, acc = []) {
  for (const n of nodes || []) {
    acc.push(n.id)
    if (n.children?.length) collectIds(n.children, acc)
  }
  return acc
}

function refreshCount() {
  if (!treeRef.value) {
    checkedCount.value = 0
    return
  }
  const keys = treeRef.value.getCheckedKeys(false) || []
  checkedCount.value = keys.length
}

function setTypeFilter(v) {
  if (typeFilter.value === v) return
  // 先记下当前勾选，过滤重建后恢复
  if (treeRef.value) {
    lastChecked.value = [
      ...(treeRef.value.getCheckedKeys(false) || []),
      ...(treeRef.value.getHalfCheckedKeys?.() || [])
    ]
  }
  typeFilter.value = v
}

function onCheck() {
  if (treeRef.value) {
    lastChecked.value = [
      ...(treeRef.value.getCheckedKeys(false) || []),
      ...(treeRef.value.getHalfCheckedKeys?.() || [])
    ]
  }
  refreshCount()
}

async function openPerms(row) {
  currentRole.value = row
  permFilter.value = ''
  typeFilter.value = 'ALL'
  try {
    const res = await api.get(`/roles/${row.id}/permissions`)
    permTree.value = res.data.permissions || []
    const checked = res.data.permIds || []
    lastChecked.value = [...checked]
    permDialog.value = true
    await nextTick()
    if (treeRef.value) {
      treeRef.value.setCheckedKeys(checked)
      refreshCount()
    }
  } catch (e) {
    ElMessage.error(e.message || '加载权限失败')
  }
}

function checkAll() {
  if (!treeRef.value) return
  const ids = collectIds(filteredTree.value)
  treeRef.value.setCheckedKeys(ids)
  lastChecked.value = [...ids]
  refreshCount()
}

function clearCheck() {
  if (!treeRef.value) return
  treeRef.value.setCheckedKeys([])
  lastChecked.value = []
  refreshCount()
}

function invertCheck() {
  if (!treeRef.value) return
  const all = collectIds(filteredTree.value)
  const cur = new Set(treeRef.value.getCheckedKeys(false) || [])
  // 含半选父级，反选叶子更直观：用全量 id 中未勾选的
  const half = new Set(treeRef.value.getHalfCheckedKeys?.() || [])
  const next = all.filter((id) => !cur.has(id) || half.has(id))
  treeRef.value.setCheckedKeys(next)
  refreshCount()
}

async function toggleExpand() {
  expanded.value = !expanded.value
  await nextTick()
  const tree = treeRef.value
  if (!tree) return
  const nodes = tree.store?.nodesMap || {}
  Object.values(nodes).forEach((n) => {
    if (n.childNodes?.length) n.expanded = expanded.value
  })
}

async function savePerms() {
  saving.value = true
  try {
    let keys = []
    if (treeRef.value) {
      keys = [...(treeRef.value.getCheckedKeys(false) || []), ...(treeRef.value.getHalfCheckedKeys?.() || [])]
    }
    await api.post(`/roles/${currentRole.value.id}/permissions`, { permIds: keys })
    ElMessage.success(`已保存 ${keys.length} 个权限点`)
    permDialog.value = false
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; }
</style>

<style>
/* 权限分配弹窗（全局，穿透 el-dialog） */
.perm-dialog {
  --perm-blue: #2f6bff;
  --perm-ink: #0f172a;
  --perm-muted: #64748b;
  --perm-line: #e2e8f0;
  --perm-bg: #f8fafc;
  border-radius: 16px !important;
  overflow: hidden;
  padding: 0 !important;
}
.perm-dialog .el-dialog__header {
  padding: 0 !important;
  margin: 0 !important;
  border-bottom: 1px solid var(--perm-line);
  background: linear-gradient(180deg, #f8fbff 0%, #fff 100%);
}
.perm-dialog .el-dialog__body {
  padding: 14px 18px 8px !important;
}
.perm-dialog .el-dialog__footer {
  padding: 12px 18px 16px !important;
  border-top: 1px solid var(--perm-line);
  background: var(--perm-bg);
}

.perm-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 16px 20px 14px;
}
.perm-head-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.perm-avatar {
  width: 40px;
  height: 40px;
  border-radius: 12px;
  background: linear-gradient(135deg, #2f6bff, #5b8cff);
  color: #fff;
  font-weight: 700;
  font-size: 18px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  box-shadow: 0 6px 16px rgba(47, 107, 255, 0.28);
}
.perm-title {
  font-size: 16px;
  font-weight: 700;
  color: var(--perm-ink);
  line-height: 1.2;
}
.perm-sub {
  margin-top: 3px;
  font-size: 12px;
  color: var(--perm-muted);
}
.perm-sub b { color: var(--perm-ink); font-weight: 600; }
.perm-code {
  margin-left: 6px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 4px;
  background: #eef2ff;
  color: #4338ca;
}
.perm-head-right {
  display: flex;
  align-items: center;
  gap: 12px;
}
.perm-stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 48px;
  padding: 6px 10px;
  border-radius: 10px;
  background: #f1f5f9;
}
.perm-stat-n {
  font-size: 18px;
  font-weight: 700;
  color: var(--perm-blue);
  line-height: 1.1;
}
.perm-stat-l {
  font-size: 11px;
  color: var(--perm-muted);
}
.perm-close {
  width: 32px;
  height: 32px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--perm-muted);
  cursor: pointer;
  font-size: 14px;
}
.perm-close:hover {
  background: #f1f5f9;
  color: var(--perm-ink);
}

.perm-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  flex-wrap: wrap;
}
.perm-search {
  width: 240px;
  flex-shrink: 0;
}
.perm-search .el-input__wrapper {
  border-radius: 10px !important;
  background: var(--perm-bg);
}
.perm-filters {
  display: flex;
  gap: 6px;
}
.perm-chip {
  border: 1px solid var(--perm-line);
  background: #fff;
  color: var(--perm-muted);
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s ease;
}
.perm-chip:hover {
  border-color: #bfdbfe;
  color: var(--perm-blue);
}
.perm-chip.is-on {
  background: #eef2ff;
  border-color: #c7d2fe;
  color: var(--perm-blue);
  font-weight: 600;
}
.perm-actions {
  margin-left: auto;
  display: flex;
  gap: 2px;
}

.perm-body {
  border: 1px solid var(--perm-line);
  border-radius: 12px;
  background: #fff;
  min-height: 360px;
  max-height: 52vh;
  overflow: auto;
  padding: 8px 6px;
}
.perm-body::-webkit-scrollbar { width: 8px; }
.perm-body::-webkit-scrollbar-thumb {
  background: #e2e8f0;
  border-radius: 8px;
}
.perm-empty {
  text-align: center;
  color: var(--perm-muted);
  padding: 48px 0;
  font-size: 13px;
}

.perm-tree {
  background: transparent;
}
.perm-tree .el-tree-node__content {
  height: 36px;
  border-radius: 8px;
  margin: 1px 4px;
  padding-right: 8px !important;
  transition: background 0.15s ease;
}
.perm-tree .el-tree-node__content:hover {
  background: #f1f5f9;
}
.perm-tree .el-tree-node.is-current > .el-tree-node__content {
  background: #eef2ff;
}
.perm-tree .el-checkbox__inner {
  border-radius: 4px;
}
.perm-tree .el-checkbox__input.is-checked .el-checkbox__inner,
.perm-tree .el-checkbox__input.is-indeterminate .el-checkbox__inner {
  background: var(--perm-blue);
  border-color: var(--perm-blue);
}

.perm-node {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  width: 100%;
}
.perm-ico {
  display: inline-flex;
  color: #94a3b8;
  flex-shrink: 0;
}
.perm-node.is-menu .perm-ico { color: var(--perm-blue); }
.perm-node.is-button .perm-ico { color: #0ea5e9; }
.perm-name {
  font-size: 13px;
  color: var(--perm-ink);
  font-weight: 500;
  white-space: nowrap;
}
.perm-node.is-button .perm-name {
  font-weight: 400;
  color: #334155;
}
.perm-tag {
  font-size: 10px;
  line-height: 1;
  padding: 3px 6px;
  border-radius: 4px;
  flex-shrink: 0;
}
.perm-tag.is-menu {
  background: #e0e7ff;
  color: #3730a3;
}
.perm-tag.is-btn {
  background: #e0f2fe;
  color: #0369a1;
}
.perm-code-inline {
  margin-left: auto;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 11px;
  color: #94a3b8;
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.perm-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.perm-hint {
  font-size: 12px;
  color: var(--perm-muted);
}
</style>

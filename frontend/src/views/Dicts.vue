<template>
  <div v-loading="loading" class="page">
    <el-card>
      <div class="bar">
        <el-input v-model="typeKeyword" placeholder="搜索事件/类型" style="width:200px" clearable @change="filterTypes" />
        <el-button type="primary" @click="filterTypes">查询</el-button>
        <el-button type="success" @click="openCreateType">新增事件类型</el-button>
        <el-button @click="reloadAll">刷新全局缓存</el-button>
        <span class="hint">左侧选「事件」，右侧是该事件下全部状态/枚举；页面按类型调用，不再混在一起。</span>
      </div>

      <div class="layout">
        <!-- 左：事件类型列表 -->
        <div class="type-panel">
          <div class="panel-title">事件 / 字典类型</div>
          <div
            v-for="t in filteredTypes"
            :key="t.id || t.typeCode"
            class="type-item"
            :class="{ active: currentType?.typeCode === t.typeCode }"
            @click="selectType(t)"
          >
            <div class="type-name">{{ t.typeName }}</div>
            <div class="type-code">{{ t.typeCode }}</div>
            <div class="type-usage" v-if="t.usageRemark">{{ t.usageRemark }}</div>
            <div class="type-actions">
              <el-button link type="primary" size="small" @click.stop="openEditType(t)">编辑</el-button>
              <el-button link type="danger" size="small" @click.stop="removeType(t)">删除</el-button>
            </div>
          </div>
          <el-empty v-if="!filteredTypes.length" description="无类型" :image-size="60" />
        </div>

        <!-- 右：该事件全部字典项 -->
        <div class="item-panel">
          <div class="panel-title">
            <span v-if="currentType">
              {{ currentType.typeName }}（{{ currentType.typeCode }}）的全部项
            </span>
            <span v-else class="muted">← 先选择左侧事件</span>
            <span class="spacer" />
            <el-button
              v-if="currentType"
              type="success"
              size="small"
              @click="openCreateItem"
            >新增该项</el-button>
          </div>

          <el-table v-if="currentType" :data="items" border stripe size="small">
            <el-table-column prop="dictKey" label="编码" min-width="140" />
            <el-table-column prop="dictLabel" label="中文显示" min-width="160" />
            <el-table-column prop="sortNo" label="排序" width="70" />
            <el-table-column label="启用" width="70">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'info'" size="small">
                  {{ row.enabled ? '是' : '否' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="120" />
            <el-table-column label="操作" width="130">
              <template #default="{ row }">
                <el-button link type="primary" @click="openEditItem(row)">编辑</el-button>
                <el-button link type="danger" @click="removeItem(row)">删除</el-button>
              </template>
            </el-table-column>
          </el-table>
          <el-empty v-else description="请选择左侧事件类型" />
        </div>
      </div>
    </el-card>

    <!-- 类型编辑 -->
    <el-dialog v-model="typeDialog" :title="typeForm.id ? '编辑事件类型' : '新增事件类型'" width="480px">
      <el-form label-width="100px">
        <el-form-item label="类型编码" required>
          <el-input v-model="typeForm.typeCode" placeholder="如 INBOUND_STATUS" :disabled="!!typeForm.id" />
        </el-form-item>
        <el-form-item label="中文名称" required>
          <el-input v-model="typeForm.typeName" placeholder="如 入库单状态" />
        </el-form-item>
        <el-form-item label="分组">
          <el-select v-model="typeForm.typeGroup" style="width:100%">
            <el-option value="BUSINESS" label="业务" />
            <el-option value="BASE" label="基础" />
          </el-select>
        </el-form-item>
        <el-form-item label="使用说明">
          <el-input v-model="typeForm.usageRemark" placeholder="哪个页面调用，如 入库列表/筛选" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="typeForm.sortNo" :min="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="typeDialog=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveType">保存</el-button>
      </template>
    </el-dialog>

    <!-- 字典项编辑 -->
    <el-dialog v-model="itemDialog" :title="itemForm.id ? '编辑字典项' : '新增字典项'" width="440px">
      <el-form label-width="90px">
        <el-form-item label="所属类型">
          <el-input :model-value="currentType?.typeCode" disabled />
        </el-form-item>
        <el-form-item label="编码" required>
          <el-input v-model="itemForm.dictKey" placeholder="如 DRAFT" :disabled="!!itemForm.id" />
        </el-form-item>
        <el-form-item label="中文显示" required>
          <el-input v-model="itemForm.dictLabel" placeholder="如 草稿" />
        </el-form-item>
        <el-form-item label="排序">
          <el-input-number v-model="itemForm.sortNo" :min="0" />
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="itemForm.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="itemForm.remark" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="itemDialog=false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveItem">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { reloadDicts } from '../utils/dict'

const loading = ref(false)
const saving = ref(false)
const types = ref([])
const items = ref([])
const currentType = ref(null)
const typeKeyword = ref('')
const typeDialog = ref(false)
const itemDialog = ref(false)

const typeForm = reactive({
  id: null, typeCode: '', typeName: '', typeGroup: 'BUSINESS', usageRemark: '', sortNo: 0, enabled: 1
})
const itemForm = reactive({
  id: null, dictType: '', dictKey: '', dictLabel: '', sortNo: 0, enabled: 1, remark: ''
})

const filteredTypes = computed(() => {
  const k = (typeKeyword.value || '').trim().toLowerCase()
  if (!k) return types.value
  return types.value.filter(t =>
    (t.typeName || '').includes(k) ||
    (t.typeCode || '').toLowerCase().includes(k) ||
    (t.usageRemark || '').includes(k)
  )
})

async function loadCatalog() {
  loading.value = true
  try {
    const res = await api.get('/dicts/catalog')
    types.value = res.data.types || []
    const allItems = res.data.items || []
    if (!currentType.value && types.value.length) {
      selectType(types.value[0], allItems)
    } else if (currentType.value) {
      const keep = types.value.find(t => t.typeCode === currentType.value.typeCode)
      if (keep) selectType(keep, allItems)
      else currentType.value = null
    }
    // 预挂 items 到选择
    if (currentType.value) {
      items.value = allItems.filter(i => i.dictType === currentType.value.typeCode)
    }
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

async function selectType(t, preloadedItems) {
  currentType.value = t
  if (preloadedItems) {
    items.value = preloadedItems.filter(i => i.dictType === t.typeCode)
    return
  }
  try {
    const res = await api.get('/dicts/items', { params: { typeCode: t.typeCode } })
    items.value = res.data || []
  } catch (e) {
    ElMessage.error(e.message || '加载字典项失败')
  }
}

function filterTypes() { /* computed 已过滤 */ }

function openCreateType() {
  Object.assign(typeForm, { id: null, typeCode: '', typeName: '', typeGroup: 'BUSINESS', usageRemark: '', sortNo: 0, enabled: 1 })
  typeDialog.value = true
}

function openEditType(t) {
  Object.assign(typeForm, {
    id: t.id, typeCode: t.typeCode, typeName: t.typeName,
    typeGroup: t.typeGroup || 'BUSINESS', usageRemark: t.usageRemark || '',
    sortNo: t.sortNo || 0, enabled: t.enabled == null ? 1 : t.enabled
  })
  typeDialog.value = true
}

async function saveType() {
  if (!typeForm.typeCode || !typeForm.typeName) {
    ElMessage.warning('编码与中文名必填')
    return
  }
  saving.value = true
  try {
    if (typeForm.id) await api.put(`/dicts/types/${typeForm.id}`, { ...typeForm })
    else await api.post('/dicts/types', { ...typeForm })
    ElMessage.success('已保存')
    typeDialog.value = false
    await Promise.all([loadCatalog(), reloadDicts()])
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeType(t) {
  await ElMessageBox.confirm(`删除类型「${t.typeName}」？其下明细将不可见。`, '提示')
  try {
    await api.delete(`/dicts/types/${t.id}`)
    ElMessage.success('已删除')
    currentType.value = null
    await Promise.all([loadCatalog(), reloadDicts()])
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

function openCreateItem() {
  if (!currentType.value) return
  Object.assign(itemForm, {
    id: null, dictType: currentType.value.typeCode, dictKey: '', dictLabel: '', sortNo: 0, enabled: 1, remark: ''
  })
  itemDialog.value = true
}

function openEditItem(row) {
  Object.assign(itemForm, {
    id: row.id, dictType: row.dictType, dictKey: row.dictKey, dictLabel: row.dictLabel,
    sortNo: row.sortNo || 0, enabled: row.enabled == null ? 1 : row.enabled, remark: row.remark || ''
  })
  itemDialog.value = true
}

async function saveItem() {
  if (!itemForm.dictKey || !itemForm.dictLabel) {
    ElMessage.warning('编码与中文显示必填')
    return
  }
  saving.value = true
  try {
    const body = { ...itemForm, dictType: currentType.value.typeCode }
    if (itemForm.id) await api.put(`/dicts/items/${itemForm.id}`, body)
    else await api.post('/dicts/items', body)
    ElMessage.success('已保存')
    itemDialog.value = false
    await Promise.all([selectType(currentType.value), reloadDicts()])
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function removeItem(row) {
  await ElMessageBox.confirm(`删除「${row.dictKey}」？`, '提示')
  try {
    await api.delete(`/dicts/items/${row.id}`)
    ElMessage.success('已删除')
    await Promise.all([selectType(currentType.value), reloadDicts()])
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

async function reloadAll() {
  await reloadDicts()
  ElMessage.success('全局缓存已刷新')
}

onMounted(loadCatalog)
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; flex-wrap:wrap; align-items:center; }
.hint { color:#909399; font-size:12px; }
.page { min-height: 520px; }
.layout { display:flex; gap:12px; min-height:460px; }
.type-panel {
  width: 280px; flex-shrink:0; border:1px solid #e5e7eb; border-radius:8px;
  background:#fafbfc; overflow:auto; max-height:560px;
}
.item-panel {
  flex:1; min-width:0; border:1px solid #e5e7eb; border-radius:8px;
  padding:12px; background:#fff;
}
.panel-title {
  font-weight:600; font-size:13px; padding:8px 10px;
  border-bottom:1px solid #eef2f7; margin-bottom:8px;
  display:flex; align-items:center; gap:8px;
}
.spacer { flex:1; }
.muted { color:#94a3b8; font-weight:400; }
.type-item {
  padding:10px 12px; border-bottom:1px solid #eef2f7; cursor:pointer;
}
.type-item:hover { background:#f0f7ff; }
.type-item.active { background:#e8f1ff; border-left:3px solid #2f6bff; }
.type-name { font-weight:600; font-size:13px; color:#0f172a; }
.type-code { font-size:11px; color:#64748b; font-family:ui-monospace,monospace; margin-top:2px; }
.type-usage { font-size:11px; color:#94a3b8; margin-top:2px; }
.type-actions { margin-top:4px; }
</style>

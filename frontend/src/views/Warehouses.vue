<template>
  <el-card v-loading="loading">
    <el-tabs v-model="tab">
      <el-tab-pane label="仓库" name="wh">
        <div class="bar">
          <el-input v-model="keyword" placeholder="编码/名称" style="width:200px" clearable @change="loadWh" />
          <n-button type="primary" @click="loadWh">查询</n-button>
          <n-button type="success" v-perm="'wh:warehouse:create'" @click="openCreateWh">新建仓库</n-button>
        </div>
        <el-table :data="warehouses" border stripe>
          <el-table-column prop="id" label="ID" min-width="70" />
          <el-table-column prop="warehouseCode" label="编码" min-width="120" />
          <el-table-column prop="warehouseName" label="名称" min-width="140" />
          <el-table-column prop="address" label="地址" min-width="160" />
          <el-table-column prop="status" label="状态" min-width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ tNumStatus(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="280">
            <template #default="{ row }">
              <div class="flex flex-wrap items-center gap-2">
                <n-button size="small" text type="primary" v-perm="'wh:warehouse:update'" @click="openEditWh(row)">编辑</n-button>
                <n-button size="small" text type="warning" @click="openLocs(row)">库位</n-button>
                <n-button size="small" text type="error" v-perm="'wh:warehouse:delete'" @click="removeWh(row)">删除</n-button>
              </div>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="库位" name="loc">
        <div class="bar">
          <el-select v-model="locWarehouseId" placeholder="仓库" style="width:180px" @change="loadLocs" clearable>
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
          <n-button type="primary" @click="loadLocs">查询</n-button>
          <n-button type="success" v-perm="'wh:location:create'" @click="openCreateLoc">新建库位</n-button>
        </div>
        <el-table :data="locations" border stripe>
          <el-table-column prop="id" label="ID" min-width="70" />
          <el-table-column prop="warehouseId" label="仓库" min-width="110">
            <template #default="{ row }">{{ warehouseLabel(row.warehouseId) }}</template>
          </el-table-column>
          <el-table-column prop="locationCode" label="库位编码" min-width="120" />
          <el-table-column prop="locationName" label="库位名称" min-width="140" />
          <el-table-column prop="status" label="状态" min-width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="row.status === 1 ? 'success' : 'info'">{{ tNumStatus(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="280">
            <template #default="{ row }">
              <n-button size="small" text type="primary" v-perm="'wh:location:update'" @click="openEditLoc(row)">编辑</n-button>
              <n-button size="small" text type="error" v-perm="'wh:location:delete'" @click="removeLoc(row)">删除</n-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="whDialog" :title="whForm.id ? '编辑仓库' : '新建仓库'" width="480px">
      <el-form ref="whFormRef" :model="whForm" :rules="whRules" label-width="90px">
        <el-form-item label="编码" prop="warehouseCode"><el-input v-model="whForm.warehouseCode" /></el-form-item>
        <el-form-item label="名称" prop="warehouseName"><el-input v-model="whForm.warehouseName" /></el-form-item>
        <el-form-item label="地址"><el-input v-model="whForm.address" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="whForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        
        <div class="flex items-center justify-end gap-2">
        <n-button @click="whDialog=false">取消</n-button>
        <n-button type="primary" @click="saveWh">保存</n-button>
        </div>
      
      </template>
    </el-dialog>

    <el-dialog v-model="locDialog" :title="locForm.id ? '编辑库位' : '新建库位'" width="480px">
      <el-form ref="locFormRef" :model="locForm" :rules="locRules" label-width="90px">
        <el-form-item label="仓库" prop="warehouseId">
          <el-select v-model="locForm.warehouseId" class="w-full">
            <el-option v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.warehouseName" />
          </el-select>
        </el-form-item>
        <el-form-item label="编码" prop="locationCode"><el-input v-model="locForm.locationCode" /></el-form-item>
        <el-form-item label="名称" prop="locationName"><el-input v-model="locForm.locationName" /></el-form-item>
        <el-form-item label="状态">
          <el-switch v-model="locForm.status" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        
        <div class="flex items-center justify-end gap-2">
        <n-button @click="locDialog=false">取消</n-button>
        <n-button type="primary" @click="saveLoc">保存</n-button>
        </div>
      
      </template>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { tNumStatus, tBiz, loadDicts } from '../utils/dict'

const tab = ref('wh')
const loading = ref(false)
const keyword = ref('')
const warehouses = ref([])
const locations = ref([])
const locWarehouseId = ref(null)
const whDialog = ref(false)
const locDialog = ref(false)

const whForm = reactive({ id: null, warehouseCode: '', warehouseName: '', address: '', status: 1 })
const locForm = reactive({ id: null, warehouseId: null, locationCode: '', locationName: '', status: 1 })
const whFormRef = ref()
const locFormRef = ref()
const whRules = {
  warehouseCode: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  warehouseName: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}
const locRules = {
  warehouseId: [{ required: true, message: '请选择仓库', trigger: 'change' }],
  locationCode: [{ required: true, message: '请输入编码', trigger: 'blur' }],
  locationName: [{ required: true, message: '请输入名称', trigger: 'blur' }]
}

function warehouseLabel(id) {
  const w = warehouses.value.find(x => x.id === id)
  return w ? w.warehouseName : (id || '-')
}

async function loadWh() {
  loading.value = true
  try {
    const res = await api.get('/warehouses', { params: { page: 1, size: 100, keyword: keyword.value } })
    warehouses.value = res.data.records || []
  } catch (e) { ElMessage.error(e.message || '加载失败') } finally { loading.value = false }
}

async function loadLocs() {
  loading.value = true
  try {
    if (locWarehouseId.value) {
      const res = await api.get(`/warehouses/${locWarehouseId.value}/locations`)
      locations.value = res.data || []
    } else {
      // 无仓库筛选时汇总各仓库库位
      const list = warehouses.value.length ? warehouses.value : (await api.get('/warehouses/list')).data || []
      const all = []
      for (const w of list) {
        try {
          const res = await api.get(`/warehouses/${w.id}/locations`)
          all.push(...(res.data || []))
        } catch { /* skip */ }
      }
      locations.value = all
    }
  } catch (e) {
    locations.value = []
  } finally { loading.value = false }
}

function openCreateWh() {
  Object.assign(whForm, { id: null, warehouseCode: '', warehouseName: '', address: '', status: 1 })
  whDialog.value = true
  nextTick(() => whFormRef.value?.clearValidate())
}
function openEditWh(row) {
  Object.assign(whForm, { ...row })
  whDialog.value = true
  nextTick(() => whFormRef.value?.clearValidate())
}
async function saveWh() {
  try {
    await whFormRef.value.validate()
  } catch {
    ElMessage.warning('请完善必填项后再保存')
    return
  }
  try {
    if (whForm.id) await api.put(`/warehouses/${whForm.id}`, { ...whForm })
    else await api.post('/warehouses', { ...whForm })
    ElMessage.success('已保存')
    whDialog.value = false
    loadWh()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}
async function removeWh(row) {
  await ElMessageBox.confirm(`删除仓库 ${row.warehouseName}？`, '提示')
  try {
    await api.delete(`/warehouses/${row.id}`)
    ElMessage.success('已删除')
    loadWh()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

function openCreateLoc() {
  Object.assign(locForm, { id: null, warehouseId: locWarehouseId.value, locationCode: '', locationName: '', status: 1 })
  locDialog.value = true
  nextTick(() => locFormRef.value?.clearValidate())
}
function openEditLoc(row) {
  Object.assign(locForm, { ...row })
  locDialog.value = true
  nextTick(() => locFormRef.value?.clearValidate())
}
async function saveLoc() {
  try {
    await locFormRef.value.validate()
  } catch {
    ElMessage.warning('请完善必填项后再保存')
    return
  }
  try {
    if (locForm.id) await api.put(`/warehouses/locations/${locForm.id}`, { ...locForm })
    else await api.post(`/warehouses/${locForm.warehouseId}/locations`, { ...locForm })
    ElMessage.success('已保存')
    locDialog.value = false
    loadLocs()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}
async function removeLoc(row) {
  await ElMessageBox.confirm('删除该库位？', '提示')
  try {
    await api.delete(`/warehouses/locations/${row.id}`)
    ElMessage.success('已删除')
    loadLocs()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

function openLocs(row) {
  locWarehouseId.value = row.id
  tab.value = 'loc'
  loadLocs()
}

onMounted(() => {
  loadDicts()
  loadWh()
  loadLocs()
})
</script>

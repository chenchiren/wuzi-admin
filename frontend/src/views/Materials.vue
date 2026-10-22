<template>
  <div v-loading="loading" class="page">
    <el-card>
      <div class="bar">
        <el-input v-model="keyword" placeholder="编码/名称" style="width:200px" clearable @change="onSearch" />
        <el-select v-model="filterCategoryId" clearable filterable placeholder="所属分类" style="width:180px" @change="onSearch">
          <el-option v-for="c in categoryOptions" :key="c.id" :value="c.id" :label="c.label" />
        </el-select>
        <el-button type="primary" @click="load">查询</el-button>
        <el-button type="success" v-perm="'base:material:create'" @click="openCreate">新建物资</el-button>
        <el-button @click="doExport">导出CSV</el-button>
      </div>
      <el-table :data="rows" border stripe>
        <el-table-column prop="materialCode" label="编码" min-width="110" />
        <el-table-column prop="materialName" label="名称" min-width="130" />
        <el-table-column label="所属分类" min-width="130">
          <template #default="{ row }">
            <span v-if="row.categoryId">{{ categoryLabel(row.categoryId) }}</span>
            <span v-else class="text-slate-400">未分类</span>
          </template>
        </el-table-column>
        <el-table-column prop="spec" label="规格" min-width="100" />
        <el-table-column prop="unit" label="单位" width="70" />
        <el-table-column prop="refPrice" label="参考含税价" min-width="100" />
        <el-table-column prop="safetyStock" label="安全库存" min-width="90">
          <template #default="{ row }">
            <span :class="{ warn: isLow(row) }">{{ row.safetyStock ?? '—' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="maxStock" label="最大库存" min-width="90">
          <template #default="{ row }">{{ row.maxStock ?? '—' }}</template>
        </el-table-column>
        <el-table-column label="码粒度" width="90">
          <template #default="{ row }">
            <StatusTag :value="row.qrGranularity" dict-type="QR_GRANULARITY" />
          </template>
        </el-table-column>
        <el-table-column label="批次" width="70">
          <template #default="{ row }">
            <StatusTag :value="row.batchManaged ? 'Y' : 'N'" dict-type="YES_NO" />
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160">
          <template #default="{ row }">
            <n-button size="small" text type="primary" v-perm="'base:material:update'" @click="openEdit(row)">编辑库存阈值</n-button>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
        v-model:current-page="page" :page-size="10" @current-change="load" />
    </el-card>

    <el-dialog v-model="dialog" :title="form.id ? '编辑物资 / 库存阈值' : '新建物资'" width="560px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="编码" prop="materialCode">
          <el-input v-model="form.materialCode" :disabled="!!form.id" placeholder="请输入物资编码" />
        </el-form-item>
        <el-form-item label="名称" prop="materialName">
          <el-input v-model="form.materialName" placeholder="请输入物资名称" />
        </el-form-item>
        <el-form-item label="所属分类" prop="categoryId">
          <el-select v-model="form.categoryId" clearable filterable class="w-full" placeholder="选择物资分类（可选）">
            <el-option v-for="c in categoryOptions" :key="c.id" :value="c.id" :label="c.label" />
          </el-select>
        </el-form-item>
        <el-form-item label="规格">
          <el-input v-model="form.spec" />
        </el-form-item>
        <el-form-item label="单位" prop="unit">
          <el-input v-model="form.unit" style="width:120px" placeholder="请输入单位" />
        </el-form-item>
        <el-form-item label="安全库存下限">
          <el-input-number v-model="form.safetyStock" :min="0" :precision="3" style="width:160px" />
          <div class="hint">当前库存 ≤ 此值时，在「库存预警」提示需补货；填 0 表示不预警</div>
        </el-form-item>
        <el-form-item label="最大库存上限">
          <el-input-number v-model="form.maxStock" :min="0" :precision="3" style="width:160px" />
          <div class="hint">当前库存 ≥ 此值时，提示超库存；填 0 表示不预警</div>
        </el-form-item>
        <el-form-item label="参考含税价">
          <el-input-number v-model="form.refPrice" :min="0" :precision="2" style="width:160px" />
        </el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" /></el-form-item>
      </el-form>
      <template #footer>
        <div class="flex justify-end gap-2">
          <n-button @click="dialog=false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/request'
import { downloadExportCsv } from '../utils/export'
import { loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const filterCategoryId = ref(null)
const loading = ref(false)
const dialog = ref(false)
const categories = ref([])
const form = reactive({
  id: null,
  materialCode: '',
  materialName: '',
  categoryId: null,
  spec: '',
  unit: '',
  safetyStock: 0,
  maxStock: 0,
  refPrice: 0,
  remark: ''
})
const formRef = ref()
const rules = {
  materialCode: [{ required: true, message: '请输入物资编码', trigger: 'blur' }],
  materialName: [{ required: true, message: '请输入物资名称', trigger: 'blur' }],
  unit: [{ required: true, message: '请输入单位', trigger: 'blur' }]
}

/** 扁平化分类，label 带父级路径，如「办公用品 / 文具」 */
const categoryOptions = computed(() => {
  const byId = new Map(categories.value.map(c => [c.id, c]))
  const pathOf = (id) => {
    const names = []
    let cur = byId.get(id)
    let guard = 0
    while (cur && guard++ < 10) {
      names.unshift(cur.categoryName || cur.categoryCode || `#${cur.id}`)
      cur = cur.parentId ? byId.get(cur.parentId) : null
    }
    return names.join(' / ')
  }
  return categories.value
    .slice()
    .sort((a, b) => pathOf(a.id).localeCompare(pathOf(b.id), 'zh-CN'))
    .map(c => ({ id: c.id, label: pathOf(c.id) }))
})

function categoryLabel(id) {
  return categoryOptions.value.find(c => c.id === id)?.label || `分类#${id}`
}

function onSearch() {
  page.value = 1
  load()
}

function isLow(row) {
  const s = Number(row.safetyStock || 0)
  return s > 0 && Number(row.qty || 0) <= s
}

async function doExport() {
  try {
    await downloadExportCsv('/exports/materials', {
      keyword: keyword.value || undefined,
      categoryId: filterCategoryId.value || undefined
    }, '物资档案')
    ElMessage.success('已导出')
  } catch (e) {
    ElMessage.error(e.message || '导出失败')
  }
}

async function loadCategories() {
  try {
    const res = await api.get('/categories')
    categories.value = res.data || []
  } catch {
    categories.value = []
  }
}

async function load() {
  loading.value = true
  try {
    const res = await api.get('/materials', {
      params: {
        page: page.value,
        size: 10,
        keyword: keyword.value || undefined,
        categoryId: filterCategoryId.value || undefined
      }
    })
    rows.value = res.data.records || []
    total.value = res.data.total
  } catch (e) {
    ElMessage.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

function resetForm() {
  Object.assign(form, {
    id: null, materialCode: '', materialName: '', categoryId: null, spec: '', unit: '',
    safetyStock: 0, maxStock: 0, refPrice: 0, remark: ''
  })
}

function openCreate() {
  resetForm()
  dialog.value = true
  nextTick(() => formRef.value?.clearValidate())
}

function openEdit(row) {
  Object.assign(form, {
    id: row.id,
    materialCode: row.materialCode,
    materialName: row.materialName,
    categoryId: row.categoryId ?? null,
    spec: row.spec || '',
    unit: row.unit || '',
    safetyStock: row.safetyStock == null ? 0 : Number(row.safetyStock),
    maxStock: row.maxStock == null ? 0 : Number(row.maxStock),
    refPrice: row.refPrice == null ? 0 : Number(row.refPrice),
    remark: row.remark || ''
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
  const max = Number(form.maxStock || 0)
  const safety = Number(form.safetyStock || 0)
  if (max > 0 && safety > max) {
    ElMessage.warning('安全库存不能大于最大库存')
    return
  }
  try {
    if (form.id) {
      await api.put(`/materials/${form.id}`, { ...form })
    } else {
      await api.post('/materials', { ...form })
    }
    ElMessage.success('已保存')
    dialog.value = false
    load()
  } catch (e) {
    ElMessage.error(e.message || '保存失败')
  }
}

onMounted(async () => {
  await loadDicts()
  loadCategories()
  load()
})
</script>

<style scoped>
.bar { display:flex; gap:8px; align-items:center; flex-wrap:wrap; }
.page { min-height: 240px; }
.hint { font-size:12px; color:#909399; line-height:1.4; margin-top:2px; }
.warn { color:#e6a23c; font-weight:600; }
</style>

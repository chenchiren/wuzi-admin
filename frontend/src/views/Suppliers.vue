<template>
  <el-card>
    <div class="bar">
      <el-input v-model="keyword" placeholder="编码/名称" style="width:220px" clearable @change="load" />
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'base:supplier:create'" @click="openCreateSupplier">新建供应商</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="supplierCode" label="编码" min-width="110" />
      <el-table-column prop="supplierName" label="名称" min-width="160" />
      <el-table-column prop="contactName" label="联系人" min-width="100" />
      <el-table-column prop="contactPhone" label="电话" min-width="130" />
      <el-table-column prop="defaultTaxRate" label="默认税率%" min-width="100" />
      <el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.coopStatus" dict-type="COOP_STATUS" />
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="320">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text type="primary" @click="openProducts(row)">所属商品</n-button>
            <n-button size="small" text type="primary" @click="openContacts(row)">联系人</n-button>
            <n-button size="small" text type="primary" v-perm="'base:supplier:update'" @click="openEditSupplier(row)">编辑</n-button>
            <n-button size="small" text type="error" v-perm="'base:supplier:delete'" @click="removeSupplier(row)">删除</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <!-- 供应商编辑 -->
    <el-dialog v-model="supplierDialog" :title="supplierForm.id ? '编辑供应商' : '新建供应商'" width="480px">
      <el-form ref="supplierFormRef" :model="supplierForm" :rules="supplierRules" label-width="90px">
        <el-form-item label="编码" prop="supplierCode">
          <el-input v-model="supplierForm.supplierCode" placeholder="如 GYS001" />
        </el-form-item>
        <el-form-item label="名称" prop="supplierName">
          <el-input v-model="supplierForm.supplierName" placeholder="请输入供应商名称" />
        </el-form-item>
        <el-form-item label="联系人">
          <el-input v-model="supplierForm.contactName" />
        </el-form-item>
        <el-form-item label="电话">
          <el-input v-model="supplierForm.contactPhone" />
        </el-form-item>
        <el-form-item label="地址">
          <el-input v-model="supplierForm.address" />
        </el-form-item>
        <el-form-item label="默认税率%">
          <el-input-number v-model="supplierForm.defaultTaxRate" :min="0" :max="100" :precision="2" class="w-full" />
        </el-form-item>
        <el-form-item label="合作状态">
          <el-select v-model="supplierForm.coopStatus" class="w-full">
            <el-option v-for="o in coopOptions" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="supplierDialog=false">取消</n-button>
          <n-button type="primary" @click="saveSupplier">保存</n-button>
        </div>
      </template>
    </el-dialog>

    <!-- 联系人 -->
    <el-dialog v-model="contactDialog" :title="`联系人 · ${currentContactSupplier?.supplierName || ''}`" width="860px" top="8vh">
      <SupplierContacts :supplier-id="currentContactSupplier?.id" />
    </el-dialog>

    <!-- 所属商品 -->
    <el-dialog v-model="productDialog" :title="`所属商品 · ${currentSupplier?.supplierName || ''}`" width="920px" top="6vh">
      <div class="mb-3 flex flex-wrap items-center gap-2">
        <el-input v-model="productKeyword" placeholder="产品名称/编码" style="width:220px" clearable @change="loadProducts" />
        <n-button type="primary" @click="loadProducts">查询</n-button>
        <n-button type="success" v-perm="'base:supplier-product:create'" @click="openCreateProduct">新增商品</n-button>
      </div>

      <el-table :data="products" border stripe v-loading="productLoading">
        <el-table-column prop="supplierProductCode" label="商品编码" min-width="130" />
        <el-table-column prop="productName" label="产品名称" min-width="140" />
        <el-table-column prop="spec" label="规格" min-width="110" />
        <el-table-column prop="unit" label="单位" min-width="70" />
        <el-table-column prop="taxRate" label="税率%" min-width="80" />
        <el-table-column prop="taxInclusivePrice" label="含税价" min-width="90" />
        <el-table-column prop="taxExclusivePrice" label="不含税价" min-width="90" />
        <el-table-column label="关联系统物资" min-width="140">
          <template #default="{ row }">
            {{ materialLabel(row.materialId) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="280">
          <template #default="{ row }">
            <div class="flex flex-wrap items-center gap-2">
              <n-button size="small" text type="primary" v-perm="'base:supplier-product:update'" @click="openEditProduct(row)">编辑</n-button>
              <n-button size="small" text type="error" v-perm="'base:supplier-product:delete'" @click="removeProduct(row)">删除</n-button>
            </div>
          </template>
        </el-table-column>
      </el-table>
      <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="productTotal"
        v-model:current-page="productPage" :page-size="10" @current-change="loadProducts" />

      <!-- 商品表单 -->
      <el-dialog v-model="productFormDialog" :title="productForm.id ? '编辑商品' : '新增商品'" width="560px" append-to-body>
        <el-form ref="productFormRef" :model="productForm" :rules="productRules" label-width="100px">
          <el-form-item label="商品编码" prop="supplierProductCode">
            <el-input v-model="productForm.supplierProductCode" placeholder="供应商侧产品编码" />
          </el-form-item>
          <el-form-item label="产品名称" prop="productName">
            <el-input v-model="productForm.productName" placeholder="请输入产品名称" />
          </el-form-item>
          <el-form-item label="规格">
            <el-input v-model="productForm.spec" />
          </el-form-item>
          <el-form-item label="单位">
            <el-input v-model="productForm.unit" style="width:120px" />
          </el-form-item>
          <el-form-item label="品牌">
            <el-input v-model="productForm.brand" />
          </el-form-item>
          <el-form-item label="关联系统物资">
            <el-select v-model="productForm.materialId" clearable filterable class="w-full" placeholder="可选，映射到内部物资档案">
              <el-option v-for="m in materials" :key="m.id" :value="m.id" :label="`${m.materialCode} ${m.materialName}`" />
            </el-select>
          </el-form-item>
          <el-form-item label="税率%">
            <el-input-number v-model="productForm.taxRate" :min="0" :max="100" :precision="2" class="w-full" />
          </el-form-item>
          <el-form-item label="含税单价" prop="taxInclusivePrice">
            <el-input-number v-model="productForm.taxInclusivePrice" :min="0" :precision="2" class="w-full" />
          </el-form-item>
          <el-form-item label="计价方式">
            <el-select v-model="productForm.priceTaxMode" class="w-full">
              <el-option value="INCLUSIVE" label="含税价" />
              <el-option value="EXCLUSIVE" label="不含税价" />
            </el-select>
          </el-form-item>
          <el-form-item label="税号">
            <el-input v-model="productForm.taxCode" />
          </el-form-item>
          <el-form-item label="起订量">
            <el-input-number v-model="productForm.moq" :min="0" class="w-full" />
          </el-form-item>
          <el-form-item label="交付周期(天)">
            <el-input-number v-model="productForm.leadTimeDays" :min="0" class="w-full" />
          </el-form-item>
        </el-form>
        <template #footer>
          <div class="flex items-center justify-end gap-2">
            <n-button @click="productFormDialog=false">取消</n-button>
            <n-button type="primary" @click="saveProduct">保存</n-button>
          </div>
        </template>
      </el-dialog>
    </el-dialog>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { tCoop, loadDicts, dictOptionsOr } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'
import SupplierContacts from '../components/SupplierContacts.vue'

const coopOptions = computed(() => dictOptionsOr('COOP_STATUS', ['ACTIVE', 'PAUSED', 'ENDED']))

const rows = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')

const supplierDialog = ref(false)
const supplierForm = reactive({
  id: null, supplierCode: '', supplierName: '', contactName: '',
  contactPhone: '', address: '', defaultTaxRate: 13, coopStatus: 'ACTIVE'
})

const productDialog = ref(false)
const productFormDialog = ref(false)
const productLoading = ref(false)
const currentSupplier = ref(null)
const contactDialog = ref(false)
const currentContactSupplier = ref(null)
const products = ref([])
const productTotal = ref(0)
const productPage = ref(1)
const productKeyword = ref('')
const materials = ref([])
const materialsLoaded = ref(false)

const productForm = reactive({
  id: null, supplierId: null, materialId: null,
  supplierProductCode: '', productName: '', spec: '', unit: '', brand: '',
  taxRate: 13, taxInclusivePrice: null, priceTaxMode: 'INCLUSIVE',
  taxCode: '', moq: null, leadTimeDays: null
})

const supplierFormRef = ref()
const productFormRef = ref()
const supplierRules = {
  supplierCode: [{ required: true, message: '请输入供应商编码', trigger: 'blur' }],
  supplierName: [{ required: true, message: '请输入供应商名称', trigger: 'blur' }]
}
const productRules = {
  supplierProductCode: [{ required: true, message: '请输入商品编码', trigger: 'blur' }],
  productName: [{ required: true, message: '请输入产品名称', trigger: 'blur' }],
  taxInclusivePrice: [{ required: true, message: '请输入含税单价', trigger: 'change' }]
}

async function load() {
  const res = await api.get('/suppliers', { params: { page: page.value, size: 10, keyword: keyword.value } })
  rows.value = res.data.records
  total.value = res.data.total
}

function openCreateSupplier() {
  Object.assign(supplierForm, {
    id: null, supplierCode: '', supplierName: '', contactName: '',
    contactPhone: '', address: '', defaultTaxRate: 13, coopStatus: 'ACTIVE'
  })
  supplierDialog.value = true
  nextTick(() => supplierFormRef.value?.clearValidate())
}

function openEditSupplier(row) {
  Object.assign(supplierForm, {
    id: row.id,
    supplierCode: row.supplierCode || '',
    supplierName: row.supplierName || '',
    contactName: row.contactName || '',
    contactPhone: row.contactPhone || '',
    address: row.address || '',
    defaultTaxRate: row.defaultTaxRate ?? 13,
    coopStatus: row.coopStatus || 'ACTIVE'
  })
  supplierDialog.value = true
  nextTick(() => supplierFormRef.value?.clearValidate())
}

async function saveSupplier() {
  try {
    await supplierFormRef.value.validate()
  } catch {
    ElMessage.warning('请完善必填项后再保存')
    return
  }
  try {
    const code = String(supplierForm.supplierCode || '').trim()
    const name = String(supplierForm.supplierName || '').trim()
    const dup = (rows.value || []).find(r =>
      r.supplierCode === code && r.id !== supplierForm.id
    )
    if (dup) {
      ElMessage.error(`供应商编码「${code}」已存在，请更换编码`)
      return
    }
    await api.post('/suppliers', { ...supplierForm, supplierCode: code, supplierName: name })
    ElMessage.success('已保存')
    supplierDialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

async function ensureMaterials() {
  if (materialsLoaded.value) return
  const res = await api.get('/materials', { params: { page: 1, size: 200 } })
  materials.value = res.data.records || []
  materialsLoaded.value = true
}

function materialLabel(materialId) {
  if (!materialId) return '—'
  const m = materials.value.find(x => x.id === materialId)
  return m ? `${m.materialCode} ${m.materialName}` : `#${materialId}`
}

async function openProducts(row) {
  currentSupplier.value = row
  productDialog.value = true
  productPage.value = 1
  productKeyword.value = ''
  await ensureMaterials()
  await loadProducts()
}

function openContacts(row) {
  currentContactSupplier.value = row
  contactDialog.value = true
}

async function loadProducts() {
  if (!currentSupplier.value) return
  productLoading.value = true
  try {
    const res = await api.get('/supplier-products', {
      params: {
        supplierId: currentSupplier.value.id,
        page: productPage.value,
        size: 10,
        keyword: productKeyword.value || undefined
      }
    })
    products.value = res.data.records
    productTotal.value = res.data.total
  } catch (e) {
    ElMessage.error(e.message || '加载商品失败')
  } finally {
    productLoading.value = false
  }
}

function openCreateProduct() {
  Object.assign(productForm, {
    id: null,
    supplierId: currentSupplier.value?.id,
    materialId: null,
    supplierProductCode: '', productName: '', spec: '', unit: '', brand: '',
    taxRate: currentSupplier.value?.defaultTaxRate ?? 13,
    taxInclusivePrice: null,
    priceTaxMode: 'INCLUSIVE',
    taxCode: '', moq: null, leadTimeDays: null
  })
  productFormDialog.value = true
  nextTick(() => productFormRef.value?.clearValidate())
}

function openEditProduct(row) {
  Object.assign(productForm, {
    id: row.id,
    supplierId: row.supplierId,
    materialId: row.materialId,
    supplierProductCode: row.supplierProductCode || '',
    productName: row.productName || '',
    spec: row.spec || '',
    unit: row.unit || '',
    brand: row.brand || '',
    taxRate: row.taxRate ?? 13,
    taxInclusivePrice: row.taxInclusivePrice,
    priceTaxMode: row.priceTaxMode || 'INCLUSIVE',
    taxCode: row.taxCode || '',
    moq: row.moq,
    leadTimeDays: row.leadTimeDays
  })
  productFormDialog.value = true
  nextTick(() => productFormRef.value?.clearValidate())
}

async function saveProduct() {
  try {
    await productFormRef.value.validate()
  } catch {
    ElMessage.warning('请完善必填项后再保存')
    return
  }
  try {
    await api.post('/supplier-products', {
      ...productForm,
      supplierId: currentSupplier.value.id
    })
    ElMessage.success('已保存')
    productFormDialog.value = false
    loadProducts()
  } catch (e) { ElMessage.error(e.message || '保存失败') }
}

async function removeProduct(row) {
  await ElMessageBox.confirm(`确认删除商品「${row.productName || row.supplierProductCode}」？`, '提示')
  try {
    await api.delete(`/supplier-products/${row.id}`)
    ElMessage.success('已删除')
    loadProducts()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

async function removeSupplier(row) {
  await ElMessageBox.confirm(
    `确认删除供应商「${row.supplierName || row.supplierCode}」？其名下商品将一并下架。`,
    '删除供应商',
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
  try {
    await api.delete(`/suppliers/${row.id}`)
    ElMessage.success('已删除')
    await load()
  } catch (e) { ElMessage.error(e.message || '删除失败') }
}

onMounted(async () => {
  await loadDicts()
  load()
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; }
</style>

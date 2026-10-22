<template>
  <el-card>
    <div class="bar">
      <el-input v-model="keyword" placeholder="单号/原因/备注" style="width:200px" clearable @change="load" />
      <el-select v-model="status" placeholder="状态" clearable style="width:140px" @change="load">
        <el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label" />
      </el-select>
      <n-button type="primary" @click="load">查询</n-button>
      <n-button type="success" v-perm="'asset:scrap:create'" @click="openCreate">新建报废</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="scrapNo" label="报废单号" min-width="180" />
      <el-table-column prop="scrapDate" label="报废日" min-width="110" />
      <el-table-column prop="scrapReason" label="原因" min-width="140" />
      <el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="SCRAP_STATUS" />
        </template>
      </el-table-column>
      <el-table-column prop="remark" label="备注" min-width="120" />
      <el-table-column label="操作" min-width="280">
        <template #default="{ row }">
          <div class="flex flex-wrap items-center gap-2">
            <n-button size="small" text @click="openFlow(row)">流程</n-button>
            <n-button size="small" v-if="['DRAFT','REJECTED'].includes(row.status)" v-perm="'asset:scrap:submit'" text type="primary" @click="submit(row)">提交审批</n-button>
            <n-button size="small" v-if="['APPROVED','SUBMITTED'].includes(row.status)" v-perm="'asset:scrap:confirm'" text type="success" @click="confirm(row)">确认报废</n-button>
            <n-button size="small" v-if="['PENDING_APPROVAL','SUBMITTED'].includes(row.status)" v-perm="'asset:scrap:reject'" text type="error" @click="reject(row)">驳回</n-button>
          </div>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />

    <el-dialog v-model="dialog" title="新建报废单" width="720px">
      <el-form label-width="90px">
        <el-form-item label="报废日"><el-date-picker v-model="form.scrapDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
        <el-form-item label="原因"><el-input v-model="form.scrapReason" /></el-form-item>
        <el-form-item label="明细">
          <el-table :data="form.items" size="small" border>
            <el-table-column label="物资" min-width="160">
              <template #default="{ row }">
                <el-select v-model="row.materialId" class="w-full">
                  <el-option v-for="m in materials" :key="m.id" :value="m.id" :label="m.materialName" />
                </el-select>
              </template>
            </el-table-column>
            <el-table-column label="数量" min-width="100">
              <template #default="{ row }"><el-input-number v-model="row.qty" :min="1" size="small" controls-position="right" /></template>
            </el-table-column>
            <el-table-column label="操作" min-width="70">
              <template #default="{ $index }"><n-button size="small" text type="error" @click="form.items.splice($index,1)">删</n-button></template>
            </el-table-column>
          </el-table>
          <n-button class="mt-2" @click="form.items.push({ materialId: null, qty: 1 })">加行</n-button>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="dialog=false">取消</n-button>
          <n-button type="primary" @click="save">保存</n-button>
        </div>
      </template>
    </el-dialog>
  
    <ApprovalFlowDialog
      v-model="flowDialog"
      :biz-type="flowBizType"
      :biz-id="flowBizId"
      :title="flowTitle"
    />
  </el-card>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import api from '../api/request'
import { tScrapStatus, loadDicts, dictOptionsOr } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'
import ApprovalFlowDialog from '../components/ApprovalFlowDialog.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const status = ref('')
const statusOptions = computed(() =>
  dictOptionsOr('SCRAP_STATUS', ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'SUBMITTED', 'CONFIRMED', 'REJECTED'])
)
const dialog = ref(false)
const materials = ref([])
const form = reactive({
  scrapDate: '', scrapReason: '',
  items: [{ materialId: null, qty: 1 }]
})

async function load() {
  const res = await api.get('/scraps', {
    params: { page: page.value, size: 10, keyword: keyword.value, status: status.value }
  })
  rows.value = res.data.records
  total.value = res.data.total
}

function openCreate() {
  form.scrapDate = ''
  form.scrapReason = ''
  form.items = [{ materialId: null, qty: 1 }]
  dialog.value = true
}

async function save() {
  try {
    await api.post('/scraps', { ...form })
    ElMessage.success('已创建')
    dialog.value = false
    load()
  } catch (e) { ElMessage.error(e.message || '创建失败') }
}

async function submit(row) {
  try {
    await api.post(`/scraps/${row.id}/submit`)
    ElMessage.success('已提交')
    load()
  } catch (e) { ElMessage.error(e.message || '提交失败') }
}

async function confirm(row) {
  const { value } = await ElMessageBox.prompt('请输入仓库ID', '确认报废', {
    inputPattern: /^\d+$/, inputErrorMessage: '请输入数字ID'
  })
  try {
    await api.post(`/scraps/${row.id}/confirm`, { warehouseId: Number(value) })
    ElMessage.success('已确认')
    load()
  } catch (e) { ElMessage.error(e.message || '确认失败') }
}

async function reject(row) {
  const { value } = await ElMessageBox.prompt('驳回原因', '驳回', { inputValue: '' })
  try {
    await api.post(`/scraps/${row.id}/reject`, { warehouseId: null, comment: value || '' })
    ElMessage.success('已驳回')
    load()
  } catch (e) { ElMessage.error(e.message || '驳回失败') }
}

const flowDialog = ref(false)
const flowBizType = ref('')
const flowBizId = ref(null)
const flowTitle = ref('')

function openFlow(row) {
  flowBizType.value = 'SCRAP'
  flowBizId.value = row.id
  flowTitle.value = `审批流程 · ${row.scrapNo || row.id}`
  flowDialog.value = true
}

onMounted(async () => {
  await loadDicts()
  load()
  const m = await api.get('/materials', { params: { page: 1, size: 100 } })
  materials.value = m.data.records
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; }
</style>

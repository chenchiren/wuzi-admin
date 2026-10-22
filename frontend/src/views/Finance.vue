<template>
  <el-card v-loading="loading">
    <el-tabs v-model="tab">
      <el-tab-pane label="发票登记" name="inv">
        <div class="bar">
          <n-button type="primary" @click="loadInvoices">刷新</n-button>
          <n-button type="success" v-perm="'fin:invoice:create'" @click="openRegister">登记发票</n-button>
        </div>
        <el-table :data="invoices" border stripe>
          <el-table-column prop="invoiceNo" label="发票号" min-width="140" />
          <el-table-column prop="invoiceCode" label="发票代码" min-width="110" />
          <el-table-column label="订单" min-width="120">
            <template #default="{ row }">{{ orderLabel(row.orderId) }}</template>
          </el-table-column>
          <el-table-column prop="supplierId" label="供应商" min-width="100">
            <template #default="{ row }">{{ supplierLabel(row.supplierId) }}</template>
          </el-table-column>
          <el-table-column prop="taxInclusiveAmount" label="含税金额" min-width="100" />
          <el-table-column prop="taxAmount" label="税额" min-width="90" />
          <el-table-column prop="matchStatus" label="匹配" min-width="90">
            <template #default="{ row }">
              <StatusTag :value="row.matchStatus" dict-type="MATCH_STATUS" />
            </template>
          </el-table-column>
          <el-table-column label="状态" min-width="100">
            <template #default="{ row }">
              <StatusTag :value="row.status" dict-type="INVOICE_STATUS" />
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="280">
            <template #default="{ row }">
              <n-button size="small" text type="primary" v-perm="'fin:invoice:match'" @click="doMatch(row)">三单匹配</n-button>
              <n-button size="small" text @click="openMatchDetail(row)">结果</n-button>
              <n-button size="small" text type="info" @click="openAtt(row, 'INVOICE')">附件</n-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>

      <el-tab-pane label="结算单" name="settle">
        <div class="bar">
          <n-button type="primary" @click="loadSettles">刷新</n-button>
          <n-button type="success" v-perm="'fin:settle:create'" @click="openSettle">生成结算</n-button>
        </div>
        <el-table :data="settles" border stripe>
          <el-table-column prop="settleNo" label="结算单号" min-width="180" />
          <el-table-column label="订单" min-width="120">
            <template #default="{ row }">{{ orderLabel(row.orderId) }}</template>
          </el-table-column>
          <el-table-column prop="invoiceId" label="发票" min-width="80" />
          <el-table-column prop="settleDate" label="结算日" min-width="110" />
          <el-table-column prop="settleAmount" label="结算金额" min-width="100" />
          <el-table-column prop="taxAmount" label="税额" min-width="90" />
          <el-table-column label="状态" min-width="100">
            <template #default="{ row }">
              <StatusTag :value="row.status" dict-type="SETTLE_STATUS" />
            </template>
          </el-table-column>
          <el-table-column label="操作" min-width="120">
            <template #default="{ row }">
              <n-button size="small" text type="info" @click="openAtt(row, 'SETTLE')">附件</n-button>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="attDialog" :title="`附件管理 · ${attBizType} #${attBizId ?? ''}`" width="640px">
      <AttachmentPanel :biz-type="attBizType" :biz-id="attBizId" />
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="attDialog=false">关闭</n-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="regDialog" title="登记发票" width="520px">
      <el-form label-width="100px">
        <el-form-item label="发票号码"><el-input v-model="regForm.invoiceNo" /></el-form-item>
        <el-form-item label="发票代码"><el-input v-model="regForm.invoiceCode" /></el-form-item>
        <el-form-item label="采购订单">
          <el-select v-model="regForm.orderId" class="w-full">
            <el-option v-for="o in orders" :key="o.id" :value="o.id" :label="o.orderNo || String(o.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="开票日"><el-date-picker v-model="regForm.invoiceDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
        <el-form-item label="含税金额"><el-input-number v-model="regForm.taxInclusiveAmount" :min="0.01" :precision="2" class="w-full" /></el-form-item>
        <el-form-item label="税额"><el-input-number v-model="regForm.taxAmount" :min="0" :precision="2" class="w-full" /></el-form-item>
        <el-form-item label="税率">
          <TaxRateSelect v-model:tax-code="regForm.taxCode" v-model:tax-rate="regForm.taxRate" />
        </el-form-item>
      </el-form>
      <template #footer>
        
        <div class="flex items-center justify-end gap-2">
        <n-button @click="regDialog=false">取消</n-button>
        <n-button type="primary" :disabled="!regForm.invoiceNo || !regForm.orderId" @click="register">保存</n-button>
        </div>
      
      </template>
    </el-dialog>

    <el-dialog v-model="settleDialog" title="按订单行生成结算" width="640px">
      <el-form label-width="100px">
        <el-form-item label="采购订单">
          <el-select v-model="settleForm.orderId" class="w-full" filterable @change="onSettleOrderChange">
            <el-option v-for="o in orders" :key="o.id" :value="o.id" :label="o.orderNo || String(o.id)" />
          </el-select>
        </el-form-item>
        <el-form-item label="发票">
          <el-select v-model="settleForm.invoiceId" class="w-full" clearable>
            <el-option v-for="i in invoices" :key="i.id" :value="i.id" :label="i.invoiceNo" />
          </el-select>
        </el-form-item>
        <el-form-item label="结算行">
          <div class="w-full">
            <div v-if="!settleItems.length" class="muted">请选择订单以加载待核销行</div>
            <el-table v-else :data="settleItems" size="small" border>
              <el-table-column prop="materialName" label="物资" min-width="120" />
              <el-table-column label="行金额" width="90">
                <template #default="{ row }">{{ money(row.lineAmount) }}</template>
              </el-table-column>
              <el-table-column label="已结" width="80">
                <template #default="{ row }">{{ money(row.settledAmount) }}</template>
              </el-table-column>
              <el-table-column label="本次核销" width="130">
                <template #default="{ row }">
                  <el-input-number v-model="row.settleAmount" :min="0" :max="Number(row.remainAmount)" :precision="2" size="small" controls-position="right" style="width:110px" />
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-form-item>
        <el-form-item label="结算日"><el-date-picker v-model="settleForm.settleDate" value-format="YYYY-MM-DD" class="w-full" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="settleForm.remark" /></el-form-item>
      </el-form>
      <template #footer>
        <div class="flex items-center justify-end gap-2">
          <n-button @click="settleDialog=false">取消</n-button>
          <n-button type="primary" :disabled="!settleForm.orderId || !settleItems.some(i => Number(i.settleAmount) > 0)" @click="createSettle">生成</n-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="matchDialog" title="三单匹配结果" width="720px">
      <el-alert v-if="matchResult && matchResult.incomplete" type="warning" :closable="false" show-icon style="margin-bottom:12px"
        :title="matchResult.message || '结果不完整，请点击「三单匹配」重新计算'" />
      <el-descriptions v-if="matchResult" :column="2" border size="small">
        <el-descriptions-item label="发票号">{{ matchResult.invoiceNo || '—' }}</el-descriptions-item>
        <el-descriptions-item label="采购订单">{{ matchResult.orderNo || matchResult.orderId || '—' }}</el-descriptions-item>
        <el-descriptions-item label="订单含税金额">{{ money(matchResult.orderAmount) }}</el-descriptions-item>
        <el-descriptions-item label="入库金额">{{ money(matchResult.inboundAmount) }}</el-descriptions-item>
        <el-descriptions-item label="发票金额">{{ money(matchResult.invoiceAmount) }}</el-descriptions-item>
        <el-descriptions-item label="匹配状态">
          <StatusTag :value="matchResult.matchStatus" dict-type="MATCH_STATUS" />
        </el-descriptions-item>
        <el-descriptions-item label="订单数量">{{ matchResult.orderQty ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="入库数量">{{ matchResult.inboundQty ?? '—' }}</el-descriptions-item>
        <el-descriptions-item label="匹配时间">{{ matchResult.matchedAt || '—' }}</el-descriptions-item>
        <el-descriptions-item label="金额容差">{{ matchResult.amountTolerance ?? '—' }}</el-descriptions-item>
      </el-descriptions>
      <div v-if="matchResult && matchResult.diffs && matchResult.diffs.length" style="margin-top:14px">
        <div class="muted" style="margin-bottom:6px">差异明细</div>
        <el-table :data="matchResult.diffs" size="small" border>
          <el-table-column label="对比" min-width="160">
            <template #default="{ row }">{{ pairLabel(row.pair) }}</template>
          </el-table-column>
          <el-table-column label="左侧金额" width="100">
            <template #default="{ row }">{{ money(row.leftAmount) }}</template>
          </el-table-column>
          <el-table-column label="右侧金额" width="100">
            <template #default="{ row }">{{ money(row.rightAmount) }}</template>
          </el-table-column>
          <el-table-column label="差额" width="100">
            <template #default="{ row }">{{ money(row.diff) }}</template>
          </el-table-column>
          <el-table-column label="差异率%" width="90" prop="diffRate" />
          <el-table-column label="是否在容差内" width="110">
            <template #default="{ row }">
              <el-tag size="small" :type="row.withinTolerance ? 'success' : 'danger'">
                {{ row.withinTolerance ? '是' : '否' }}
              </el-tag>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <el-empty v-else-if="matchResult" description="暂无差异明细" :image-size="60" />
      <el-empty v-else description="尚未匹配，请点击「三单匹配」" :image-size="60" />
    </el-dialog>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import api from '../api/request'
import { tMatchStatus, tInvoiceStatus, tSettleStatus, tBiz, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'
import TaxRateSelect from '../components/TaxRateSelect.vue'
import AttachmentPanel from '../components/AttachmentPanel.vue'

const attDialog = ref(false)
const attBizType = ref('INVOICE')
const attBizId = ref(null)

function openAtt(row, bizType) {
  attBizType.value = bizType
  attBizId.value = row.id
  attDialog.value = true
}

const tab = ref('inv')
const loading = ref(false)
const invoices = ref([])
const settles = ref([])
const orders = ref([])
const suppliers = ref([])
const regDialog = ref(false)
const settleDialog = ref(false)
const matchDialog = ref(false)
const matchResult = ref(null)

const regForm = reactive({
  invoiceNo: '', invoiceCode: '', orderId: null, invoiceDate: '',
  taxInclusiveAmount: 0, taxAmount: 0, taxCode: null, taxRate: 13
})
const settleForm = reactive({ orderId: null, invoiceId: null, settleDate: '', remark: '' })
const settleItems = ref([])
const money = v => v == null ? '—' : Number(v).toLocaleString('zh-CN', { minimumFractionDigits: 2 })

function supplierLabel(id) {
  const s = suppliers.value.find(x => x.id === id)
  return s ? s.supplierName : (id || '-')
}

function orderLabel(id) {
  const o = orders.value.find(x => x.id === id)
  return o ? o.orderNo : (id == null ? '—' : `#${id}`)
}

async function loadInvoices() {
  loading.value = true
  try {
    const res = await api.get('/invoices', { params: { page: 1, size: 50 } })
    invoices.value = res.data.records || []
  } catch (e) { ElMessage.error(e.message || '加载发票失败') } finally { loading.value = false }
}

async function loadSettles() {
  loading.value = true
  try {
    const res = await api.get('/settles', { params: { page: 1, size: 50 } })
    settles.value = res.data.records || []
  } catch (e) { ElMessage.error(e.message || '加载结算失败') } finally { loading.value = false }
}

function openRegister() {
  Object.assign(regForm, { invoiceNo: '', invoiceCode: '', orderId: null, invoiceDate: '', taxInclusiveAmount: 0, taxAmount: 0, taxCode: null, taxRate: 13 })
  regDialog.value = true
}

async function register() {
  try {
    await api.post('/invoices', { ...regForm })
    ElMessage.success('发票已登记')
    regDialog.value = false
    loadInvoices()
  } catch (e) { ElMessage.error(e.message || '登记失败') }
}

async function doMatch(row) {
  try {
    const res = await api.post(`/invoices/${row.id}/match`)
    matchResult.value = res.data
    matchDialog.value = true
    loadInvoices()
  } catch (e) { ElMessage.error(e.message || '匹配失败') }
}

async function openMatchDetail(row) {
  try {
    const res = await api.get(`/invoices/${row.id}`)
    // 详情返回 { match, invoice, order }；match 可能为 null
    if (res.data.match) {
      matchResult.value = res.data.match
    } else {
      const inv = res.data.invoice || {}
      matchResult.value = {
        invoiceNo: inv.invoiceNo,
        orderId: inv.orderId,
        matchStatus: inv.matchStatus,
        invoiceAmount: inv.taxInclusiveAmount,
        incomplete: inv.matchStatus && inv.matchStatus !== 'NONE',
        message: inv.matchStatus && inv.matchStatus !== 'NONE'
          ? '暂无完整匹配明细，请点击「三单匹配」重新计算'
          : '尚未执行三单匹配'
      }
    }
    matchDialog.value = true
  } catch (e) { ElMessage.error(e.message || '加载失败') }
}

function pairLabel(pair) {
  const map = {
    ORDER_VS_INBOUND: '订单 vs 入库',
    ORDER_VS_INVOICE: '订单 vs 发票',
    INBOUND_VS_INVOICE: '入库 vs 发票'
  }
  return map[pair] || pair
}

async function onSettleOrderChange() {
  settleItems.value = []
  if (!settleForm.orderId) return
  try {
    const det = await api.get(`/purchase/orders/${settleForm.orderId}`)
    const items = det.data.items || []
    const rows = await Promise.all(items.map(async (it) => {
      try {
        const bal = await api.get(`/settles/order-items/${it.id}/balance`)
        return {
          orderItemId: it.id,
          materialName: it.materialName || it.materialId,
          lineAmount: bal.data.lineAmount,
          settledAmount: bal.data.settledAmount,
          remainAmount: bal.data.remainAmount,
          taxAmount: it.taxAmount,
          settleAmount: Number(bal.data.remainAmount) > 0 ? Number(bal.data.remainAmount) : 0
        }
      } catch {
        return null
      }
    }))
    settleItems.value = rows.filter(r => r && Number(r.remainAmount) > 0)
  } catch (e) {
    ElMessage.error(e.message || '加载订单行失败')
  }
}

function openSettle() {
  Object.assign(settleForm, { orderId: null, invoiceId: null, settleDate: '', remark: '' })
  settleItems.value = []
  settleDialog.value = true
}

async function createSettle() {
  const items = settleItems.value
    .filter(i => Number(i.settleAmount) > 0)
    .map(i => ({
      orderItemId: i.orderItemId,
      settleAmount: Number(i.settleAmount),
      taxAmount: i.taxAmount == null ? null : Number(i.taxAmount)
    }))
  if (!items.length) {
    ElMessage.warning('请至少填写一行核销金额')
    return
  }
  try {
    await api.post('/settles', {
      orderId: settleForm.orderId,
      invoiceId: settleForm.invoiceId,
      settleDate: settleForm.settleDate || null,
      remark: settleForm.remark,
      items
    })
    ElMessage.success('结算单已生成')
    settleDialog.value = false
    loadSettles()
  } catch (e) { ElMessage.error(e.message || '生成失败') }
}

onMounted(async () => {
  await loadDicts()
  loadInvoices()
  loadSettles()
  try {
    const [o, s] = await Promise.all([
      api.get('/purchase/orders', { params: { page: 1, size: 100 } }),
      api.get('/suppliers', { params: { page: 1, size: 200 } })
    ])
    orders.value = o.data.records || []
    suppliers.value = s.data.records || []
  } catch { /* ignore */ }
})
</script>

<style scoped>
.muted { color: #888; font-size: 12px; }
.w-full { width: 100%; }
</style>

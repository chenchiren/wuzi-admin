# -*- coding: utf-8 -*-
from pathlib import Path

ROOT = Path(r"E:\aicode\relex\物资管理系统\frontend\src")

def patch(path, pairs):
    p = Path(path)
    text = p.read_text(encoding="utf-8-sig")
    orig = text
    for old, new in pairs:
        if old not in text:
            print("MISS", p.name, "::", repr(old[:90]))
        else:
            text = text.replace(old, new, 1)
    if text != orig:
        p.write_text(text, encoding="utf-8")
        print("OK", p.name)
    else:
        print("NOCHANGE", p.name)

def ensure_import(text, import_line, after=None):
    if import_line in text:
        return text
    if after and after in text:
        return text.replace(after, after + "\n" + import_line, 1)
    # insert after first import block line containing from 'vue' or similar
    marker = "import api from '../api/request'"
    if marker in text:
        return text.replace(marker, marker + "\n" + import_line, 1)
    marker2 = "import { ElMessage"
    if marker2 in text:
        idx = text.find(marker2)
        end = text.find("\n", idx)
        return text[: end + 1] + import_line + "\n" + text[end + 1 :]
    return text

# ========== Categories ==========
patch(ROOT / "views/Categories.vue", [
    (
        '<el-table-column prop="status" label="状态" min-width="80" :formatter="(_, __, v) => tNumStatus(v)" />',
        '''<el-table-column label="状态" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="tNumStatus(row.status)" />
        </template>
      </el-table-column>''',
    ),
    (
        "import { tNumStatus, loadDicts } from '../utils/dict'",
        "import { tNumStatus, loadDicts } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== Depts ==========
patch(ROOT / "views/Depts.vue", [
    (
        '<el-table-column prop="status" label="状态" min-width="80" :formatter="(_, __, v) => tNumStatus(v)" />',
        '''<el-table-column label="状态" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="tNumStatus(row.status)" />
        </template>
      </el-table-column>''',
    ),
])

# ========== Users ==========
patch(ROOT / "views/Users.vue", [
    (
        '<el-table-column prop="status" label="状态" min-width="80" :formatter="(_, __, v) => tNumStatus(v)" />',
        '''<el-table-column label="状态" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.status" :label="tNumStatus(row.status)" />
        </template>
      </el-table-column>''',
    ),
])

# ========== Suppliers ==========
patch(ROOT / "views/Suppliers.vue", [
    (
        '<el-table-column prop="coopStatus" label="状态" min-width="110" :formatter="(_, __, v) => tCoop(v)" />',
        '''<el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.coopStatus" dict-type="COOP_STATUS" />
        </template>
      </el-table-column>''',
    ),
    (
        '''          <el-select v-model="supplierForm.coopStatus" class="w-full">
            <el-option value="ACTIVE" label="合作中" />
            <el-option value="PAUSED" label="暂停" />
            <el-option value="ENDED" label="终止" />
          </el-select>''',
        '''          <el-select v-model="supplierForm.coopStatus" class="w-full">
            <el-option v-for="o in coopOptions" :key="o.value" :value="o.value" :label="o.label" />
          </el-select>''',
    ),
    (
        "import { onMounted, reactive, ref } from 'vue'\n"
        "import { ElMessage, ElMessageBox } from 'element-plus'\n"
        "import api from '../api/request'\n"
        "import { tCoop, loadDicts } from '../utils/dict'",
        "import { computed, onMounted, reactive, ref } from 'vue'\n"
        "import { ElMessage, ElMessageBox } from 'element-plus'\n"
        "import api from '../api/request'\n"
        "import { tCoop, loadDicts, dictOptionsOr } from '../utils/dict'\n"
        "import StatusTag from '../components/StatusTag.vue'\n\n"
        "const coopOptions = computed(() => dictOptionsOr('COOP_STATUS', ['ACTIVE', 'PAUSED', 'ENDED']))",
    ),
])

# ========== PurchaseOrders ==========
patch(ROOT / "views/PurchaseOrders.vue", [
    (
        '<el-table-column prop="status" label="状态" min-width="100" :formatter="(_, __, v) => tPurchaseOrderStatus(v)" />',
        '''<el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="PURCHASE_ORDER_STATUS" />
        </template>
      </el-table-column>''',
    ),
    (
        "import { tPurchaseOrderStatus, loadDicts } from '../utils/dict'",
        "import { tPurchaseOrderStatus, loadDicts } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== Approvals ==========
patch(ROOT / "views/Approvals.vue", [
    (
        '<el-table-column prop="bizType" label="业务类型" min-width="130" :formatter="(_, __, v) => tBiz(v)" />',
        '''<el-table-column label="业务类型" min-width="130">
        <template #default="{ row }">
          <StatusTag :value="row.bizType" dict-type="BIZ_TYPE" />
        </template>
      </el-table-column>''',
    ),
    (
        '<el-table-column prop="status" label="状态" min-width="110" :formatter="(_, __, v) => tApprovalStatus(v)" />',
        '''<el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="APPROVAL_STATUS" />
        </template>
      </el-table-column>''',
    ),
    (
        '<el-descriptions-item label="类型">{{ tBiz(detail.instance.bizType) }}</el-descriptions-item>',
        '<el-descriptions-item label="类型"><StatusTag :value="detail.instance.bizType" dict-type="BIZ_TYPE" /></el-descriptions-item>',
    ),
    (
        '<el-descriptions-item label="状态">{{ tApprovalStatus(detail.instance.status) }}</el-descriptions-item>',
        '<el-descriptions-item label="状态"><StatusTag :value="detail.instance.status" dict-type="APPROVAL_STATUS" /></el-descriptions-item>',
    ),
    (
        "import { tApprovalStatus, tBiz, tApprovalAction, loadDicts, dictOptionsOr } from '../utils/dict'",
        "import { tApprovalStatus, tBiz, tApprovalAction, loadDicts, dictOptionsOr } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== Arrivals ==========
patch(ROOT / "views/Arrivals.vue", [
    (
        '<el-table-column prop="status" label="状态" min-width="110" :formatter="(_, __, v) => tArrivalStatus(v)" />',
        '''<el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="PURCHASE_ARRIVAL_STATUS" />
        </template>
      </el-table-column>''',
    ),
    (
        ':label="`${o.orderNo} (${o.status})`"',
        ':label="`${o.orderNo} · ${tPurchaseOrderStatus(o.status)}`"',
    ),
    (
        "import { tArrivalStatus, loadDicts } from '../utils/dict'",
        "import { tArrivalStatus, tPurchaseOrderStatus, loadDicts, dictOptionsOr } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
    (
        "const statuses = ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'COMPLETED', 'CANCELLED', 'REJECTED']",
        "const statusOptions = computed(() =>\n"
        "  dictOptionsOr('PURCHASE_ARRIVAL_STATUS', ['DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'COMPLETED', 'CANCELLED', 'REJECTED'])\n"
        ")\n"
        "const statuses = computed(() => statusOptions.value.map((s) => s.value))",
    ),
    (
        '<el-option v-for="s in statuses" :key="s" :value="s" :label="tArrivalStatus(s)" />',
        '<el-option v-for="s in statusOptions" :key="s.value" :value="s.value" :label="s.label" />',
    ),
    (
        "import { onMounted, reactive, ref } from 'vue'",
        "import { computed, onMounted, reactive, ref } from 'vue'",
    ),
])

# ========== Inbounds ==========
patch(ROOT / "views/Inbounds.vue", [
    (
        '<el-table-column prop="status" label="状态" min-width="110" :formatter="(_, __, v) => tInboundStatus(v)" />',
        '''<el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="INBOUND_STATUS" />
        </template>
      </el-table-column>''',
    ),
    (
        "import { tInboundStatus, tInboundType, loadDicts } from '../utils/dict'",
        "import { tInboundStatus, tInboundType, loadDicts } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== Outbounds ==========
patch(ROOT / "views/Outbounds.vue", [
    (
        '<el-table-column prop="status" label="状态" min-width="110" :formatter="(_, __, v) => tOutboundStatus(v)" />',
        '''<el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="OUTBOUND_STATUS" />
        </template>
      </el-table-column>''',
    ),
    (
        "import { tOutboundStatus, tOutboundType, loadDicts, dictOptionsOr } from '../utils/dict'",
        "import { tOutboundStatus, tOutboundType, loadDicts, dictOptionsOr } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== MaterialUse ==========
patch(ROOT / "views/MaterialUse.vue", [
    (
        '<el-table-column prop="useType" label="类型" min-width="90" :formatter="(_, __, v) => tUseType(v)" />',
        '''<el-table-column label="类型" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.useType" dict-type="USE_TYPE" />
        </template>
      </el-table-column>''',
    ),
    (
        '<el-table-column prop="status" label="状态" min-width="110" :formatter="(_, __, v) => tMaterialUseStatus(v)" />',
        '''<el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="MATERIAL_USE_STATUS" />
        </template>
      </el-table-column>''',
    ),
    (
        "import { tMaterialUseStatus, tUseType, tBiz, loadDicts, dictOptionsOr } from '../utils/dict'",
        "import { tMaterialUseStatus, tUseType, tBiz, loadDicts, dictOptionsOr } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== Borrows ==========
patch(ROOT / "views/Borrows.vue", [
    (
        '<el-table-column prop="borrowType" label="类型" min-width="90" :formatter="(_, __, v) => tBorrowType(v)" />',
        '''<el-table-column label="类型" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.borrowType" dict-type="BORROW_TYPE" />
        </template>
      </el-table-column>''',
    ),
    (
        '<el-table-column prop="status" label="状态" min-width="110" :formatter="(_, __, v) => tBorrowStatus(v)" />',
        '''<el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="BORROW_STATUS" />
        </template>
      </el-table-column>''',
    ),
    (
        "import { tBorrowStatus, tBorrowType, loadDicts, dictOptionsOr } from '../utils/dict'",
        "import { tBorrowStatus, tBorrowType, loadDicts, dictOptionsOr } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== Scraps ==========
patch(ROOT / "views/Scraps.vue", [
    (
        '<el-table-column prop="status" label="状态" min-width="110" :formatter="(_, __, v) => tScrapStatus(v)" />',
        '''<el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="SCRAP_STATUS" />
        </template>
      </el-table-column>''',
    ),
    (
        "import { tScrapStatus, loadDicts, dictOptionsOr } from '../utils/dict'",
        "import { tScrapStatus, loadDicts, dictOptionsOr } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== ReturnStores ==========
patch(ROOT / "views/ReturnStores.vue", [
    (
        '<el-tag size="small" :type="statusTag(row.status)">{{ tReturnStoreStatus(row.status) }}</el-tag>',
        '<StatusTag :value="row.status" dict-type="RETURN_STORE_STATUS" />',
    ),
    (
        '<el-table-column prop="conditionStatus" label="状态" min-width="90" :formatter="(_, __, v) => tConditionStatus(v)" />',
        '''<el-table-column label="成色" min-width="90">
          <template #default="{ row }">
            <StatusTag :value="row.conditionStatus" dict-type="CONDITION_STATUS" />
          </template>
        </el-table-column>''',
    ),
    (
        "import { tReturnStoreStatus, tConditionStatus, tBiz, loadDicts, dictOptionsOr } from '../utils/dict'",
        "import { tReturnStoreStatus, tConditionStatus, tBiz, loadDicts, dictOptionsOr } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
    (
        """function statusTag(s) {
  if (s === 'CONFIRMED') return 'success'
  if (s === 'REJECTED' || s === 'CANCELLED') return 'danger'
  return 'warning'
}""",
        "",
    ),
])

# ========== Transfers ==========
patch(ROOT / "views/Transfers.vue", [
    (
        '<el-table-column prop="status" label="状态" min-width="110" :formatter="(_, __, v) => tTransferStatus(v)" />',
        '''<el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="TRANSFER_STATUS" />
        </template>
      </el-table-column>''',
    ),
    (
        "import { tTransferStatus, loadDicts } from '../utils/dict'",
        "import { tTransferStatus, loadDicts } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== InventoryChecks ==========
patch(ROOT / "views/InventoryChecks.vue", [
    (
        '<el-table-column prop="status" label="状态" min-width="110" :formatter="(_, __, v) => tInventoryStatus(v)" />',
        '''<el-table-column label="状态" min-width="110">
        <template #default="{ row }">
          <StatusTag :value="row.status" dict-type="INVENTORY_STATUS" />
        </template>
      </el-table-column>''',
    ),
    (
        "import { tInventoryStatus, loadDicts } from '../utils/dict'",
        "import { tInventoryStatus, loadDicts } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== PurchaseApplies ==========
patch(ROOT / "views/PurchaseApplies.vue", [
    (
        '''          <el-tag size="small" :type="statusTagType(row.status)" effect="light">
            {{ statusLabel(row.status) }}
          </el-tag>''',
        '''          <StatusTag :value="row.status" dict-type="PURCHASE_APPLY_STATUS" />''',
    ),
    (
        '''          <el-tag size="small" :type="urgencyTagType(row.urgency)" effect="plain">
            {{ urgencyLabel(row.urgency) }}
          </el-tag>''',
        '''          <StatusTag :value="row.urgency" dict-type="URGENCY" effect="plain" />''',
    ),
    (
        "import { tPurchaseApplyStatus, tUrgency, loadDicts } from '../utils/dict'",
        "import { tPurchaseApplyStatus, tUrgency, loadDicts, dictOptionsOr } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
    (
        """function statusTagType(s) {
  const map = {
    DRAFT: 'info',
    SUBMITTED: 'warning',
    PENDING_APPROVAL: 'warning',
    APPROVED: 'success',
    REJECTED: 'danger',
    CANCELLED: 'info',
    COMPLETED: 'success'
  }
  return map[s] || 'info'
}""",
        "",
    ),
    (
        """function urgencyTagType(u) {
  return u === 'URGENT' ? 'danger' : 'info'
}""",
        "",
    ),
])

# ========== PurchaseReturns ==========
patch(ROOT / "views/PurchaseReturns.vue", [
    (
        '<el-tag size="small" :type="statusTag(row.status)">{{ tPurchaseReturnStatus(row.status) }}</el-tag>',
        '<StatusTag :value="row.status" dict-type="PURCHASE_RETURN_STATUS" />',
    ),
    (
        "import { tPurchaseReturnStatus, tBiz, loadDicts } from '../utils/dict'",
        "import { tPurchaseReturnStatus, tBiz, loadDicts } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== Inquiries ==========
patch(ROOT / "views/Inquiries.vue", [
    (
        '''          <el-tag size="small" :type="row.status === 'APPROVED' ? 'success' : row.status === 'DRAFT' ? 'info' : 'warning'">{{ tInquiryStatus(row.status) }}</el-tag>''',
        '''          <StatusTag :value="row.status" dict-type="PURCHASE_INQUIRY_STATUS" />''',
    ),
    (
        "import { tInquiryStatus, tBiz, loadDicts } from '../utils/dict'",
        "import { tInquiryStatus, tBiz, loadDicts } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== Finance ==========
patch(ROOT / "views/Finance.vue", [
    (
        '''              <el-tag size="small" :type="row.matchStatus === 'MATCHED' ? 'success' : row.matchStatus === 'DIFF' ? 'warning' : 'info'">
                {{ tMatchStatus(row.matchStatus) }}
              </el-tag>''',
        '''              <StatusTag :value="row.matchStatus" dict-type="MATCH_STATUS" />''',
    ),
    (
        '<el-table-column prop="status" label="状态" min-width="100" :formatter="(_, __, v) => tInvoiceStatus(v)" />',
        '''<el-table-column label="状态" min-width="100">
            <template #default="{ row }">
              <StatusTag :value="row.status" dict-type="INVOICE_STATUS" />
            </template>
          </el-table-column>''',
    ),
    (
        "import { tMatchStatus, tInvoiceStatus, tSettleStatus, tBiz, loadDicts } from '../utils/dict'",
        "import { tMatchStatus, tInvoiceStatus, tSettleStatus, tBiz, loadDicts } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== Logs ==========
patch(ROOT / "views/Logs.vue", [
    (
        '<el-table-column prop="result" label="结果" min-width="90" :formatter="(_, __, v) => tLogResult(v)" />',
        '''<el-table-column label="结果" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.result" dict-type="LOG_RESULT" />
        </template>
      </el-table-column>''',
    ),
    (
        "import { tLogResult, loadDicts } from '../utils/dict'",
        "import { tLogResult, loadDicts } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== Materials (qrGranularity / batch as tags optional) ==========
patch(ROOT / "views/Materials.vue", [
    (
        '<el-table-column prop="batchManaged" label="批次" min-width="70" :formatter="(_, __, v) => tYesNo(v)" />',
        '''<el-table-column label="批次" min-width="80">
        <template #default="{ row }">
          <StatusTag :value="row.batchManaged ? 'Y' : 'N'" dict-type="YES_NO" />
        </template>
      </el-table-column>''',
    ),
    (
        "import { tQrGranularity, tNumStatus, tYesNo, loadDicts } from '../utils/dict'",
        "import { tQrGranularity, tNumStatus, tYesNo, loadDicts } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

# ========== Alerts ==========
patch(ROOT / "views/Alerts.vue", [
    (
        '<el-table-column prop="alertType" label="类型" min-width="110" :formatter="(_, __, v) => tAlertType(v)" />',
        '''<el-table-column label="类型" min-width="110">
          <template #default="{ row }">
            <StatusTag :value="row.alertType" dict-type="ALERT_TYPE" />
          </template>
        </el-table-column>''',
    ),
    (
        '<el-table-column prop="status" label="状态" min-width="90" :formatter="(_, __, v) => tAlertStatus(v)" />',
        '''<el-table-column label="状态" min-width="90">
          <template #default="{ row }">
            <StatusTag :value="row.status" dict-type="ALERT_STATUS" />
          </template>
        </el-table-column>''',
    ),
    (
        "import { tAlertStatus, tAlertType, loadDicts, dictOptionsOr } from '../utils/dict'",
        "import { tAlertStatus, tAlertType, loadDicts, dictOptionsOr } from '../utils/dict'\nimport StatusTag from '../components/StatusTag.vue'",
    ),
])

print("batch done")

<template>
  <div class="ai-msg" :class="message.role === 'user' ? 'ai-msg--user' : 'ai-msg--assistant'">
    <div v-if="message.role === 'user'" class="ai-bubble">{{ message.content }}</div>

    <div v-else class="ai-answer">
      <!-- 工具调用过程 -->
      <div v-for="(t, i) in message.tools" :key="i" class="ai-tool">
        <div class="ai-tool__head">
          <span class="ai-tool__dot" :class="{ 'ai-tool__dot--done': t.rows }"></span>
          {{ t.rows ? t.label : t.label + '…' }}
        </div>
        <table v-if="t.rows && t.rows.length" class="ai-table">
          <thead>
            <tr>
              <th v-for="col in columns(t.rows)" :key="col">{{ colLabel(col) }}</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(row, ri) in t.rows" :key="ri">
              <td v-for="col in columns(t.rows)" :key="col">{{ fmt(row[col]) }}</td>
            </tr>
          </tbody>
        </table>
        <div v-else-if="t.rows && !t.rows.length" class="ai-tool__empty">无数据</div>
      </div>

      <!-- 回答正文（Markdown） -->
      <div v-if="html" class="ai-md" v-html="html"></div>
      <div v-else-if="!message.tools.length" class="ai-md ai-md--muted">…</div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { marked } from 'marked'
import DOMPurify from 'dompurify'

const props = defineProps({
  message: { type: Object, required: true }
})

const html = computed(() => {
  const text = props.message.content || ''
  if (!text.trim()) return ''
  return DOMPurify.sanitize(marked.parse(text, { breaks: true }))
})

const COL_LABELS = {
  materialName: '物资', materialCode: '编码', spec: '规格', unit: '单位',
  warehouseName: '仓库', warehouseId: '仓库ID', batchNo: '批次',
  qty: '数量', frozenQty: '冻结', availableQty: '可用',
  productionDate: '生产日期', expireDate: '有效期',
  status: '状态', statusLabel: '状态', orderNo: '单号', bizNo: '单号',
  title: '标题', currentNodeName: '当前节点', updatedAt: '更新时间',
  supplierCode: '编码', supplierName: '供应商', coopStatus: '合作状态',
  contactName: '联系人', contactPhone: '电话', defaultTaxRate: '默认税率',
  orderCount: '订单数', purchaseAmount: '采购额',
  date: '日期', source: '来源', taxRate: '税率', taxInclusivePrice: '含税价',
  taxExclusivePrice: '不含税价', month: '月份', amount: '金额',
  inQty: '入库量', outQty: '出库量',
  materialCode2: '编码'
}

function columns(rows) {
  if (!rows || !rows.length) return []
  return Object.keys(rows[0]).slice(0, 8)
}

function colLabel(col) {
  return COL_LABELS[col] || col
}

function fmt(v) {
  if (v === null || v === undefined) return '-'
  if (typeof v === 'number') return String(Math.round(v * 100) / 100)
  return String(v)
}
</script>

<style scoped>
.ai-msg { display: flex; margin-bottom: 12px; }
.ai-msg--user { justify-content: flex-end; }
.ai-msg--assistant { justify-content: flex-start; }

.ai-bubble {
  max-width: 85%;
  background: #2f6bff;
  color: #fff;
  padding: 8px 12px;
  border-radius: 12px 12px 3px 12px;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

.ai-answer {
  max-width: 100%;
  background: #fff;
  border: 1px solid rgba(15, 23, 42, 0.08);
  border-radius: 12px 12px 12px 3px;
  padding: 10px 12px;
  font-size: 13px;
  line-height: 1.65;
  color: #1e293b;
  overflow-x: auto;
}

.ai-tool { margin-bottom: 8px; }
.ai-tool__head {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #64748b;
  margin-bottom: 4px;
}
.ai-tool__dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: #f79009;
  animation: ai-pulse 1s ease-in-out infinite;
}
.ai-tool__dot--done { background: #22a06b; animation: none; }
@keyframes ai-pulse { 50% { opacity: 0.35; } }
.ai-tool__empty { font-size: 12px; color: #94a3b8; }

.ai-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.ai-table th, .ai-table td {
  border: 1px solid rgba(15, 23, 42, 0.08);
  padding: 4px 8px;
  text-align: left;
  white-space: nowrap;
}
.ai-table th { background: #f1f5f9; color: #475569; font-weight: 500; }

.ai-md :deep(p) { margin: 0 0 6px; }
.ai-md :deep(p:last-child) { margin-bottom: 0; }
.ai-md :deep(table) { border-collapse: collapse; font-size: 12px; margin: 6px 0; }
.ai-md :deep(th), .ai-md :deep(td) { border: 1px solid rgba(15,23,42,0.1); padding: 3px 8px; }
.ai-md :deep(code) { background: #f1f5f9; padding: 1px 5px; border-radius: 4px; font-size: 12px; }
.ai-md--muted { color: #94a3b8; }
</style>

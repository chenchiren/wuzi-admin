<template>
  <el-card>
    <div class="bar">
      <el-input v-model="keyword" placeholder="关键字" style="width:180px" clearable @change="load" />
      <el-input v-model="module" placeholder="模块" style="width:140px" clearable @change="load" />
      <el-input v-model="action" placeholder="动作" style="width:140px" clearable @change="load" />
      <n-button type="primary" @click="load">查询</n-button>
    </div>
    <el-table :data="rows" border stripe>
      <el-table-column prop="id" label="ID" min-width="80" />
      <el-table-column prop="operatorName" label="操作人" min-width="110" />
      <el-table-column prop="module" label="模块" min-width="120" />
      <el-table-column prop="action" label="动作" min-width="130" />
      <el-table-column prop="requestUri" label="URI" min-width="160" />
      <el-table-column prop="params" label="参数" min-width="160" />
      <el-table-column label="结果" min-width="90">
        <template #default="{ row }">
          <StatusTag :value="row.result" dict-type="LOG_RESULT" />
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" min-width="120" />
      <el-table-column prop="createdAt" label="时间" min-width="160" />
    </el-table>
    <el-pagination style="margin-top:12px" layout="total, prev, pager, next" :total="total"
      v-model:current-page="page" :page-size="10" @current-change="load" />
  </el-card>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import api from '../api/request'
import { tLogResult, loadDicts } from '../utils/dict'
import StatusTag from '../components/StatusTag.vue'

const rows = ref([])
const total = ref(0)
const page = ref(1)
const keyword = ref('')
const module = ref('')
const action = ref('')

async function load() {
  const res = await api.get('/logs', {
    params: {
      page: page.value, size: 10,
      keyword: keyword.value,
      module: module.value,
      action: action.value
    }
  })
  rows.value = res.data.records
  total.value = res.data.total
}

onMounted(async () => {
  await loadDicts()
  load()
})
</script>

<style scoped>
.bar { display:flex; gap:8px; margin-bottom:12px; }
</style>

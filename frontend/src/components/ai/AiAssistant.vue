<template>
  <div v-if="available" class="ai-assistant">
    <!-- 悬浮球 -->
    <button v-if="!open" class="ai-fab" title="智能助手" @click="open = true">
      <svg viewBox="0 0 24 24" width="26" height="26" fill="none" stroke="#fff"
           stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
        <path d="M21 11.5a8.38 8.38 0 0 1-.9 3.8 8.5 8.5 0 0 1-7.6 4.7 8.38 8.38 0 0 1-3.8-.9L3 21l1.9-5.7a8.38 8.38 0 0 1-.9-3.8 8.5 8.5 0 0 1 4.7-7.6 8.38 8.38 0 0 1 3.8-.9h.5a8.48 8.48 0 0 1 8 8v.5z" />
      </svg>
    </button>

    <!-- 抽屉 -->
    <div v-else class="ai-panel">
      <div class="ai-panel__head">
        <div>
          <div class="ai-panel__title">智能助手</div>
          <div class="ai-panel__sub">库存 · 单据 · 预警 · 报表，一句话直查</div>
        </div>
        <div class="ai-panel__actions">
          <button class="ai-icon-btn" title="新对话" @click="reset">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round"><path d="M12 5v14M5 12h14" /></svg>
          </button>
          <button class="ai-icon-btn" title="收起" @click="open = false">
            <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round"><path d="M6 9l6 6 6-6" /></svg>
          </button>
        </div>
      </div>

      <div ref="listEl" class="ai-panel__list">
        <div v-if="!messages.length" class="ai-welcome">
          <p>你好，我是物资管理系统智能助手。</p>
          <p class="ai-welcome__hint">可以问我库存、单据进度、库存预警、价格趋势、报表汇总等问题。</p>
        </div>
        <AiMessage v-for="(m, i) in messages" :key="i" :message="m" />
      </div>

      <div class="ai-panel__chips">
        <button v-for="c in CHIPS" :key="c" class="ai-chip"
                :disabled="streaming" @click="send(c)">{{ c }}</button>
      </div>

      <div class="ai-panel__input">
        <input
          v-model="draft"
          class="ai-input"
          type="text"
          placeholder="输入问题，回车发送"
          :disabled="streaming"
          @keydown.enter="onSend"
        />
        <button class="ai-send" :disabled="streaming || !draft.trim()" @click="onSend">
          {{ streaming ? '…' : '发送' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import AiMessage from './AiMessage.vue'
import { useAiChat } from './useAiChat'
import api from '../../api/request'

const CHIPS = ['库存预警', '我的待办', '本月部门消耗排行', 'A4纸库存多少']

const available = ref(false)
const open = ref(false)
const draft = ref('')
const listEl = ref(null)
const { messages, streaming, send, reset } = useAiChat()

onMounted(async () => {
  try {
    const r = await api.get('/ai/status')
    available.value = !!(r.data && r.data.enabled)
  } catch (e) {
    available.value = false
  }
})

function onSend() {
  const text = draft.value
  draft.value = ''
  send(text)
}

watch(
  () => messages.value.length + (messages.value[messages.value.length - 1]?.content.length || 0),
  async () => {
    await nextTick()
    if (listEl.value) listEl.value.scrollTop = listEl.value.scrollHeight
  }
)
</script>

<style scoped>
.ai-fab {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 900;
  width: 52px;
  height: 52px;
  border: none;
  border-radius: 50%;
  background: #2f6bff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 6px 20px rgba(47, 107, 255, 0.4);
}
.ai-fab:hover { background: #2559db; }

.ai-panel {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  z-index: 950;
  width: 420px;
  background: #f5f6f8;
  border-left: 1px solid rgba(15, 23, 42, 0.08);
  display: flex;
  flex-direction: column;
}

.ai-panel__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: #fff;
  border-bottom: 1px solid rgba(15, 23, 42, 0.08);
}
.ai-panel__title { font-size: 15px; font-weight: 600; color: #0f172a; }
.ai-panel__sub { font-size: 11px; color: #94a3b8; margin-top: 2px; }
.ai-panel__actions { display: flex; gap: 6px; }
.ai-icon-btn {
  width: 28px; height: 28px;
  border: 1px solid rgba(15, 23, 42, 0.1);
  background: #fff;
  border-radius: 8px;
  color: #475569;
  cursor: pointer;
  display: flex; align-items: center; justify-content: center;
}
.ai-icon-btn:hover { background: #f1f5f9; }

.ai-panel__list {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}
.ai-welcome { font-size: 13px; color: #475569; }
.ai-welcome__hint { color: #94a3b8; font-size: 12px; margin-top: 4px; }

.ai-panel__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding: 8px 16px 0;
}
.ai-chip {
  border: 1px solid rgba(47, 107, 255, 0.35);
  background: #fff;
  color: #2f6bff;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  cursor: pointer;
}
.ai-chip:hover { background: #eef3ff; }
.ai-chip:disabled { opacity: 0.5; cursor: default; }

.ai-panel__input {
  display: flex;
  gap: 8px;
  padding: 12px 16px 16px;
}
.ai-input {
  flex: 1;
  height: 36px;
  border: 1px solid rgba(15, 23, 42, 0.15);
  border-radius: 10px;
  padding: 0 12px;
  font-size: 13px;
  outline: none;
  background: #fff;
  color: #0f172a;
}
.ai-input:focus { border-color: #2f6bff; }
.ai-send {
  height: 36px;
  padding: 0 14px;
  border: none;
  border-radius: 10px;
  background: #2f6bff;
  color: #fff;
  font-size: 13px;
  cursor: pointer;
}
.ai-send:disabled { opacity: 0.5; cursor: default; }
</style>

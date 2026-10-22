import { ref, reactive } from 'vue'

/**
 * 智能助手对话状态 + SSE 流解析。
 * SSE 事件协议（与后端 AiChatService 对齐）：
 *   delta       {text}                      正文增量
 *   tool        {name, label}               工具开始执行
 *   tool_result {name, rows[]}              工具返回（渲染迷你表格）
 *   done        {sessionId}                 结束
 *   error       {msg}                       降级文案
 */
export function useAiChat() {
  const messages = ref([]) // {role:'user'|'assistant', content, tools:[{name,label,rows}]}
  const streaming = ref(false)
  const sessionId = ref(null)

  async function send(text) {
    const content = (text || '').trim()
    if (!content || streaming.value) {
      return
    }
    messages.value.push({ role: 'user', content })
    // 必须用 reactive 包装后再 push：后续闭包里修改的是同一个响应式代理，
    // 否则改的是原始对象，Vue 检测不到，界面永远空白
    const assistant = reactive({ role: 'assistant', content: '', tools: [] })
    messages.value.push(assistant)
    streaming.value = true

    try {
      const resp = await fetch('/api/ai/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${localStorage.getItem('token')}`
        },
        body: JSON.stringify({ sessionId: sessionId.value, message: content })
      })

      const ct = resp.headers.get('content-type') || ''
      if (!ct.includes('event-stream')) {
        // 未开始流式即出错（限流/未登录/未启用）：统一响应是 R JSON
        let msg = '请求失败'
        try {
          const j = await resp.json()
          msg = j.msg || msg
        } catch (e) {
          /* ignore */
        }
        assistant.content = msg
        return
      }

      const reader = resp.body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buf = ''
      for (;;) {
        const { done, value } = await reader.read()
        if (done) break
        buf += decoder.decode(value, { stream: true })
        const frames = buf.split('\n\n')
        buf = frames.pop()
        for (const frame of frames) {
          let event = 'message'
          let data = ''
          for (const line of frame.split('\n')) {
            if (line.startsWith('event:')) {
              event = line.slice(6).trim()
            } else if (line.startsWith('data:')) {
              data += line.slice(5).trim()
            }
          }
          if (!data) continue
          try {
            handleEvent(event, JSON.parse(data), assistant)
          } catch (e) {
            /* 忽略坏帧 */
          }
        }
      }
    } catch (e) {
      assistant.content += (assistant.content ? '\n\n' : '') + '网络异常，请稍后再试'
    } finally {
      streaming.value = false
    }
  }

  function handleEvent(event, data, assistant) {
    if (event === 'delta') {
      assistant.content += data.text || ''
    } else if (event === 'tool') {
      assistant.tools.push({ name: data.name, label: data.label || data.name, rows: null })
    } else if (event === 'tool_result') {
      const pending = [...assistant.tools].reverse().find((t) => t.name === data.name && !t.rows)
      if (pending) {
        pending.rows = data.rows || []
      }
    } else if (event === 'done') {
      if (data.sessionId) sessionId.value = data.sessionId
    } else if (event === 'error') {
      assistant.content += (assistant.content ? '\n\n' : '') + (data.msg || '助手暂时不可用')
    }
  }

  function reset() {
    messages.value = []
    sessionId.value = null
  }

  return { messages, streaming, sessionId, send, reset }
}

<template>
  <div class="agent-page">
    <ConversationList
      :conversations="conversations"
      :current-id="currentId"
      @create="createConversation"
      @select="switchConversation"
      @remove="deleteConversation"
    />

    <div class="chat-area">
      <div class="chat-head">
        <span class="chat-title">{{ currentTitle }}</span>
        <el-button v-if="running" size="small" type="warning" @click="cancelRun">停止</el-button>
      </div>

      <div ref="messageList" class="message-list">
        <div v-if="!currentId" class="chat-empty">
          <p>新建会话，或从左侧选择</p>
          <p class="chat-hint">例：查看项目列表 / 根据上传文档生成用例</p>
        </div>
        <MessageItem v-for="msg in messages" :key="msg.key" :msg="msg" @confirm="respondConfirmation" />
      </div>

      <div class="input-area">
        <input ref="fileInput" type="file" style="display: none" @change="uploadFile" />
        <el-button :disabled="!currentId || running" @click="$refs.fileInput.click()">上传</el-button>
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="输入消息…"
          :disabled="!currentId"
          @keydown.enter.exact.prevent="sendMessage"
        />
        <el-button
          type="primary"
          :disabled="running || !currentId || !inputText.trim()"
          @click="sendMessage"
        >
          发送
        </el-button>
      </div>
    </div>

    <ResourcePanel :files="files" :templates="templates" :memories="memories" :knowledge-docs="knowledgeDocs"
                   @confirm-memory="confirmMemory" @confirm-knowledge="confirmKnowledge" />
  </div>
</template>

<script>
import { ref, computed, nextTick } from 'vue'
import axios from 'axios'
import ConversationList from '../components/agent/ConversationList.vue'
import MessageItem from '../components/agent/MessageItem.vue'
import ResourcePanel from '../components/agent/ResourcePanel.vue'

export default {
  name: 'Agent',
  components: { ConversationList, MessageItem, ResourcePanel },
  setup() {
    const conversations = ref([])
    const currentId = ref(null)
    const messages = ref([])
    const inputText = ref('')
    const running = ref(false)
    const files = ref([])
    const templates = ref([])
    const memories = ref([])
    const knowledgeDocs = ref([])
    let eventSource = null
    let lastEventId = 0
    const messageList = ref(null)
    const fileInput = ref(null)
    const token = localStorage.getItem('token') || ''

    const currentTitle = computed(() => {
      const conv = conversations.value.find((c) => c.id === currentId.value)
      return conv ? conv.title : ''
    })

    const scrollToBottom = () => {
      nextTick(() => {
        if (messageList.value) {
          messageList.value.scrollTop = messageList.value.scrollHeight
        }
      })
    }

    const loadResources = async () => {
      if (!currentId.value) {
        files.value = []
        return
      }
      try {
        const [fr, tr, mr, kr] = await Promise.all([
          axios.get(`/agent/conversations/${currentId.value}/files`),
          axios.get('/agent/templates'),
          axios.get('/agent/memory'),
          axios.get('/agent/knowledge', { params: { includeCandidates: true } })
        ])
        files.value = (fr.data && fr.data.data) || []
        templates.value = (tr.data && tr.data.data) || []
        memories.value = (mr.data && mr.data.data) || []
        knowledgeDocs.value = (kr.data && kr.data.data) || []
      } catch (e) {
        // 资源加载失败不阻塞主流程
      }
    }

    const loadConversations = async () => {
      const res = await axios.get('/agent/conversations')
      if (res.data && res.data.code === 1) {
        conversations.value = res.data.data || []
        if (!currentId.value && conversations.value.length) {
          switchConversation(conversations.value[0].id)
        }
      }
    }

    const createConversation = async () => {
      const res = await axios.post('/agent/conversations', { title: `新会话 ${conversations.value.length + 1}` })
      if (res.data && res.data.code === 1) {
        const conv = res.data.data
        conversations.value.unshift(conv)
        switchConversation(conv.id)
      }
    }

    const switchConversation = async (id) => {
      closeStream()
      currentId.value = id
      running.value = false
      lastEventId = 0
      messages.value = []
      const res = await axios.get(`/agent/conversations/${id}`)
      if (res.data && res.data.code === 1) {
        const data = res.data.data
        const conv = conversations.value.find((c) => c.id === id)
        if (conv) {
          conv.title = data.title || conv.title
        }
        messages.value = (data.messages || []).map(renderHistoryMessage)
        scrollToBottom()
      }
      loadResources()
    }

    const renderHistoryMessage = (m) => {
      const meta = m.toolMeta || {}
      if (m.type === 'file') {
        return { key: `m${m.id}`, role: m.role || 'user', type: 'file', content: m.content || '' }
      }
      if (m.type === 'case_preview') {
        let cases = []
        try {
          cases = JSON.parse(m.content || '[]')
        } catch (e) {
          cases = []
        }
        return { key: `m${m.id}`, role: 'assistant', type: 'case_preview', cases }
      }
      if (m.type === 'tool_call') {
        return {
          key: `m${m.id}`,
          role: 'assistant',
          type: 'tool_call',
          toolName: meta.toolName || 'tool',
          status: meta.status || 'done',
          argsText: truncate(String(m.content || ''), 200)
        }
      }
      if (m.type === 'tool_result') {
        let summary = ''
        try {
          const parsed = JSON.parse(m.content || '{}')
          summary = parsed.message || parsed.status || m.content
        } catch (e) {
          summary = m.content
        }
        return {
          key: `m${m.id}`,
          role: 'tool',
          type: 'tool_result',
          toolName: meta.toolName || 'tool',
          status: meta.status || 'done',
          content: String(summary || '')
        }
      }
      if (m.type === 'confirmation') {
        return {
          key: `m${m.id}`,
          role: 'assistant',
          type: 'confirmation',
          toolName: meta.toolName || 'tool',
          confirmationId: meta.confirmationId,
          payloadText: truncate(JSON.stringify(meta.payload || {}), 200),
          handled: false
        }
      }
      return {
        key: `m${m.id}`,
        role: m.role,
        type: m.type,
        content: m.content || ''
      }
    }

    const deleteConversation = async (id) => {
      await axios.delete(`/agent/conversations/${id}`)
      conversations.value = conversations.value.filter((c) => c.id !== id)
      if (currentId.value === id) {
        closeStream()
        currentId.value = null
        messages.value = []
        running.value = false
        files.value = []
        if (conversations.value.length) {
          switchConversation(conversations.value[0].id)
        }
      }
    }

    const sendMessage = async () => {
      const content = inputText.value.trim()
      if (!content || !currentId.value || running.value) return
      messages.value.push({ key: `u${Date.now()}`, role: 'user', type: 'text', content })
      inputText.value = ''
      scrollToBottom()
      const idempotencyKey = crypto.randomUUID()
      try {
        const res = await axios.post(`/agent/conversations/${currentId.value}/messages`, {
          content,
          idempotencyKey
        })
        if (res.status === 202) {
          openStream()
        } else if (res.data && res.data.code === 0) {
          pushSystemMessage(res.data.msg || '发送失败')
        }
      } catch (e) {
        pushSystemMessage((e.response && e.response.data && e.response.data.msg) || '发送失败')
      }
    }

    const openStream = () => {
      closeStream()
      running.value = true
      lastEventId = 0
      const url = `/api/agent/conversations/${currentId.value}/stream?token=${encodeURIComponent(token)}&lastEventId=0`
      eventSource = new EventSource(url)

      const handleData = (event) => {
        if (!event.data) return
        let data
        try {
          data = JSON.parse(event.data)
        } catch (e) {
          return
        }
        if (data.id) lastEventId = data.id
        // 后端 SSE 负载嵌套在 data.data 下（data.id/data.type 在顶层）
        const payload = data.data && typeof data.data === 'object' ? data.data : {}
        switch (data.type) {
          case 'agent_start':
            running.value = true
            break
          case 'message_start': {
            // 用户消息已本地渲染；其余消息重置流式条目，保证重连重放不重复
            if (payload.role !== 'user') {
              messages.value = messages.value.filter(
                (m) => m.key !== `s${payload.messageId}` && m.key !== `m${payload.messageId}`
              )
            }
            break
          }
          case 'message_update': {
            if (payload.confirmationId) {
              upsertConfirmation(payload)
            } else if (payload.type === 'case_preview' || payload.cases) {
              upsertCasePreview(payload)
            } else {
              upsertStreamingText(payload)
            }
            break
          }
          case 'message_end': {
            flushStreamingMessage(payload)
            break
          }
          case 'tool_execution_start':
            upsertToolCard(payload)
            break
          case 'tool_execution_end':
            updateToolCard(payload)
            break
          case 'agent_end': {
            if (payload.stopReason === 'error') {
              pushSystemMessage(payload.errorMessage || '执行出错')
            }
            running.value = false
            closeStream()
            break
          }
          case 'error':
            pushSystemMessage(payload.message || '发生错误')
            break
          case 'heartbeat':
          default:
            break
        }
        scrollToBottom()
      }

      // 后端 SSE 使用命名事件（event: xxx），需要逐一注册监听；onmessage 兜底未命名事件
      const namedEvents = [
        'agent_start',
        'message_start',
        'message_update',
        'message_end',
        'tool_execution_start',
        'tool_execution_end',
        'turn_start',
        'turn_end',
        'agent_end',
        'heartbeat',
        'error'
      ]
      namedEvents.forEach((type) => eventSource.addEventListener(type, handleData))
      eventSource.onmessage = handleData
      eventSource.onerror = () => {
        // 服务端主动关闭连接：可能是 run 已结束但我们错过了 agent_end，
        // 拉取会话详情同步运行状态，避免一直卡在"运行中"
        if (eventSource && eventSource.readyState === EventSource.CLOSED) {
          syncRunState()
        }
      }
    }

    const syncRunState = async () => {
      try {
        const res = await axios.get(`/agent/conversations/${currentId.value}`)
        if (res.data && res.data.code === 1) {
          running.value = !!res.data.data.running
          if (!running.value) {
            // 保留确认卡的已处理状态，避免刷新后被重置可重复点击
            const handledMap = new Map()
            messages.value.forEach((m) => {
              if (m.type === 'confirmation' && m.confirmationId) {
                handledMap.set(m.confirmationId, m.handled)
              }
            })
            messages.value = (res.data.data.messages || []).map(renderHistoryMessage)
            messages.value.forEach((m) => {
              if (m.type === 'confirmation' && handledMap.has(m.confirmationId)) {
                m.handled = handledMap.get(m.confirmationId)
              }
            })
            scrollToBottom()
          }
        }
      } catch (e) {
        // ignore
      }
    }

    const upsertStreamingText = (data) => {
      // 用户消息已在前端本地渲染，跳过 SSE 中重复的用户消息
      if (data.role === 'user') return
      const key = `s${data.messageId}`
      const existing = messages.value.find((m) => m.key === key)
      if (data.thinking !== undefined) {
        if (existing) {
          existing.thinking = data.thinking
        } else if (data.thinking) {
          messages.value.push({
            key,
            role: 'assistant',
            type: 'text',
            content: '',
            thinking: true
          })
        }
        return
      }
      if (existing) {
        existing.content += data.delta || ''
      } else {
        messages.value.push({
          key,
          role: 'assistant',
          type: 'text',
          content: data.delta || ''
        })
      }
    }

    const flushStreamingMessage = (data) => {
      const key = `s${data.messageId}`
      const existing = messages.value.find((m) => m.key === key)
      if (existing && !messages.value.some((m) => m.key === `m${data.messageId}`)) {
        existing.key = `m${data.messageId}`
      }
    }

    const upsertToolCard = (data) => {
      const key = `t${data.toolCallId}`
      if (!messages.value.find((m) => m.key === key)) {
        messages.value.push({
          key,
          role: 'assistant',
          type: 'tool_call',
          toolName: data.toolName,
          status: 'running',
          argsText: ''
        })
      }
    }

    const updateToolCard = (data) => {
      const key = `t${data.toolCallId}`
      const card = messages.value.find((m) => m.key === key)
      if (!card) {
        upsertToolCard(data)
      }
      const target = messages.value.find((m) => m.key === key)
      if (data.status === 'awaiting_confirmation') {
        // 确认卡片由 message_update 事件统一创建（key=c{confirmationId}），
        // 这里移除工具卡片，避免同一次确认出现两张卡片
        messages.value = messages.value.filter((m) => m.key !== key)
        upsertConfirmation(data)
      } else {
        target.status = data.status
        if (data.durationMs != null) {
          target.status = `${data.status} (${data.durationMs}ms)`
        }
      }
    }

    const upsertConfirmation = (data) => {
      if (!data.confirmationId) return
      const existing = messages.value.find((m) => m.type === 'confirmation' && m.confirmationId === data.confirmationId)
      if (existing) {
        if (data.payload) {
          existing.payloadText = truncate(JSON.stringify(data.payload), 200)
        }
      } else {
        messages.value.push({
          key: `c${data.confirmationId}`,
          role: 'assistant',
          type: 'confirmation',
          toolName: data.toolName || 'tool',
          confirmationId: data.confirmationId,
          payloadText: data.payload ? truncate(JSON.stringify(data.payload), 200) : '',
          handled: false
        })
      }
    }

    const upsertCasePreview = (data) => {
      const key = `p${data.messageId}`
      const cases = data.cases || []
      const existing = messages.value.find((m) => m.key === key)
      if (existing) {
        existing.cases = cases
      } else {
        messages.value.push({ key, role: 'assistant', type: 'case_preview', cases })
      }
    }

    const uploadFile = async (event) => {
      const file = event.target.files && event.target.files[0]
      if (!file || !currentId.value) return
      const formData = new FormData()
      formData.append('file', file)
      try {
        const res = await axios.post(`/agent/conversations/${currentId.value}/files`, formData)
        if (res.data && res.data.code === 1) {
          messages.value.push({
            key: `f${Date.now()}`,
            role: 'user',
            type: 'file',
            content: res.data.data.fileName
          })
          scrollToBottom()
          loadResources()
        } else {
          pushSystemMessage((res.data && res.data.msg) || '上传失败')
        }
      } catch (e) {
        pushSystemMessage((e.response && e.response.data && e.response.data.msg) || '上传失败')
      } finally {
        event.target.value = ''
      }
    }

    const respondConfirmation = async (msg, decision) => {
      if (!msg.confirmationId) return
      msg.handled = true
      try {
        await axios.post(`/agent/conversations/${currentId.value}/confirmations/${msg.confirmationId}`, { decision })
        // 后端批准/拒绝后会续跑 run，重新连接 SSE 接收后续事件
        if (decision === 'approved') {
          openStream()
        }
      } catch (e) {
        msg.handled = false
        pushSystemMessage((e.response && e.response.data && e.response.data.msg) || '确认操作失败')
      }
    }

    const confirmMemory = async (memory) => {
      try {
        await axios.post(`/agent/memory/${memory.id}/confirm`)
        loadResources()
      } catch (e) {
        pushSystemMessage((e.response && e.response.data && e.response.data.msg) || '确认记忆失败')
      }
    }

    const confirmKnowledge = async (knowledge) => {
      try {
        await axios.post(`/agent/knowledge/${knowledge.slug}/confirm`)
        loadResources()
      } catch (e) {
        pushSystemMessage((e.response && e.response.data && e.response.data.msg) || '确认知识失败')
      }
    }

    const cancelRun = async () => {
      try {
        await axios.post(`/agent/conversations/${currentId.value}/cancel`)
      } catch (e) {
        // ignore
      }
      running.value = false
      closeStream()
    }

    const closeStream = () => {
      if (eventSource) {
        eventSource.close()
        eventSource = null
      }
    }

    const pushSystemMessage = (content) => {
      messages.value.push({ key: `sys${Date.now()}`, role: 'system', type: 'system', content })
      scrollToBottom()
    }

    const truncate = (text, max) => {
      if (!text) return ''
      return text.length > max ? text.slice(0, max) + '...' : text
    }

    loadConversations()

    return {
      conversations,
      currentId,
      currentTitle,
      messages,
      inputText,
      running,
      files,
      templates,
      memories,
      knowledgeDocs,
      messageList,
      fileInput,
      uploadFile,
      createConversation,
      switchConversation,
      deleteConversation,
      sendMessage,
      cancelRun,
      respondConfirmation,
      confirmMemory,
      confirmKnowledge
    }
  }
}
</script>

<style scoped>
.agent-page {
  display: flex;
  height: calc(100vh - 104px);
  background: var(--canvas);
  border: 1px solid var(--line);
  border-radius: var(--radius-card);
  overflow: hidden;
}

.chat-area {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.chat-head {
  height: 52px;
  padding: 0 18px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: var(--panel);
  border-bottom: 1px solid var(--line);
}

.chat-title {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 15px;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
}

.chat-empty {
  text-align: center;
  padding-top: 100px;
  color: var(--ink-muted);
}

.chat-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--ink-muted);
}

.input-area {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  padding: 12px 16px;
  background: var(--panel);
  border-top: 1px solid var(--line);
}
</style>

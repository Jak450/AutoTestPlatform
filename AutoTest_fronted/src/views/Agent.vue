<template>
  <div class="agent-page">
    <!-- 左侧会话列表 -->
    <div class="conversation-sidebar">
      <div class="sidebar-header">
        <span class="sidebar-title">会话列表</span>
        <el-button type="primary" size="small" @click="createConversation">新建会话</el-button>
      </div>
      <div class="conversation-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conversation-item"
          :class="{ active: conv.id === currentId }"
          @click="switchConversation(conv.id)"
        >
          <span class="conv-title">{{ conv.title }}</span>
          <span class="conv-delete" @click.stop="deleteConversation(conv.id)">×</span>
        </div>
        <div v-if="!conversations.length" class="empty-list">暂无会话，点击上方新建</div>
      </div>
    </div>

    <!-- 右侧聊天区 -->
    <div class="chat-area">
      <div class="chat-header">
        <span class="chat-title">{{ currentTitle }}</span>
        <div>
          <el-button v-if="running" size="small" type="warning" @click="cancelRun">停止</el-button>
          <el-tag v-if="running" size="small" type="primary" effect="dark">运行中</el-tag>
        </div>
      </div>

      <div ref="messageList" class="message-list">
        <div v-if="!currentId" class="empty-chat">
          <p>新建一个会话，或从左侧选择会话开始</p>
          <p class="tip">示例：查看项目列表 / 项目 1 下有哪些用例 / 执行用例 1、2</p>
        </div>

        <div v-for="msg in messages" :key="msg.key" class="message" :class="msg.role">
          <!-- 普通文本 -->
          <div v-if="msg.type === 'text'" class="bubble">{{ msg.content }}</div>

          <!-- 文件消息 -->
          <div v-else-if="msg.type === 'file'" class="bubble file-bubble">📄 {{ msg.content }}</div>

          <!-- 用例草稿预览 -->
          <div v-else-if="msg.type === 'case_preview'" class="case-preview-card">
            <div class="preview-title">用例草稿（{{ msg.cases.length }} 条）</div>
            <table class="preview-table">
              <thead>
                <tr><th>名称</th><th>方法</th><th>URL</th></tr>
              </thead>
              <tbody>
                <tr v-for="(c, i) in msg.cases" :key="i">
                  <td>{{ c.name }}</td>
                  <td>{{ c.method }}</td>
                  <td class="preview-url">{{ c.url }}</td>
                </tr>
              </tbody>
            </table>
            <div class="preview-tip">确认无误后告诉 Agent 保存，将写入用例库（保存前需要确认）</div>
          </div>

          <!-- 工具调用卡片 -->
          <div v-else-if="msg.type === 'tool_call'" class="tool-card">
            <div class="tool-card-header">
              <span class="tool-name">{{ msg.toolName }}</span>
              <el-tag size="small" :type="msg.status === 'success' ? 'success' : (msg.status === 'error' ? 'danger' : 'warning')">
                {{ msg.status === 'running' ? '运行中' : msg.status }}
              </el-tag>
            </div>
            <div class="tool-card-body" v-if="msg.argsText">参数：{{ msg.argsText }}</div>
          </div>

          <!-- 工具结果卡片 -->
          <div v-else-if="msg.type === 'tool_result'" class="tool-card result">
            <div class="tool-card-header">
              <span class="tool-name">{{ msg.toolName }}</span>
              <el-tag size="small" :type="msg.status === 'success' ? 'success' : 'danger'">{{ msg.status }}</el-tag>
            </div>
            <div class="tool-card-body">{{ msg.content }}</div>
          </div>

          <!-- 确认卡片 -->
          <div v-else-if="msg.type === 'confirmation'" class="confirm-card">
            <div class="confirm-title">需要确认</div>
            <div class="confirm-tool">操作：{{ msg.toolName }}</div>
            <div class="confirm-payload" v-if="msg.payloadText">内容：{{ msg.payloadText }}</div>
            <div class="confirm-actions">
              <el-button size="small" type="primary" :disabled="msg.handled" @click="respondConfirmation(msg, 'approved')">
                批准
              </el-button>
              <el-button size="small" type="danger" :disabled="msg.handled" @click="respondConfirmation(msg, 'rejected')">
                拒绝
              </el-button>
            </div>
          </div>

          <!-- 系统消息 -->
          <div v-else-if="msg.type === 'system'" class="system-msg">{{ msg.content }}</div>
        </div>
      </div>

      <div class="input-area">
        <input ref="fileInput" type="file" style="display: none" @change="uploadFile" />
        <el-button :disabled="!currentId || running" @click="$refs.fileInput.click()">上传</el-button>
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          resize="none"
          placeholder="输入消息，Enter 发送（Shift+Enter 换行）"
          :disabled="!currentId"
          @keydown.enter.exact.prevent="sendMessage"
        />
        <el-button
          type="primary"
          class="send-button"
          :disabled="running || !currentId || !inputText.trim()"
          @click="sendMessage"
        >
          发送
        </el-button>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, computed, nextTick } from 'vue'
import axios from 'axios'

export default {
  name: 'Agent',
  setup() {
    const conversations = ref([])
    const currentId = ref(null)
    const messages = ref([])
    const inputText = ref('')
    const running = ref(false)
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
            messages.value = (res.data.data.messages || []).map(renderHistoryMessage)
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
      messageList,
      fileInput,
      uploadFile,
      createConversation,
      switchConversation,
      deleteConversation,
      sendMessage,
      cancelRun,
      respondConfirmation
    }
  }
}
</script>

<style scoped>
.agent-page {
  display: flex;
  height: calc(100vh - 100px);
  background: #fff;
  border-radius: 8px;
  overflow: hidden;
}

.conversation-sidebar {
  width: 240px;
  border-right: 1px solid #ebeef5;
  display: flex;
  flex-direction: column;
  background: #f7f8fa;
}

.sidebar-header {
  padding: 14px 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
}

.sidebar-title {
  font-weight: 600;
  color: #303133;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  margin-bottom: 6px;
  border-radius: 6px;
  cursor: pointer;
  color: #303133;
  font-size: 14px;
}

.conversation-item:hover {
  background: #ecf5ff;
}

.conversation-item.active {
  background: #d9ecff;
  color: #1577ff;
  font-weight: 500;
}

.conv-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-delete {
  color: #c0c4cc;
  font-size: 16px;
  padding: 0 4px;
}

.conv-delete:hover {
  color: #f56c6c;
}

.empty-list {
  color: #909399;
  font-size: 13px;
  text-align: center;
  padding: 24px 8px;
}

.chat-area {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.chat-header {
  height: 56px;
  padding: 0 20px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  border-bottom: 1px solid #ebeef5;
}

.chat-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #fafafa;
}

.empty-chat {
  text-align: center;
  color: #909399;
  padding-top: 120px;
  font-size: 14px;
}

.empty-chat .tip {
  font-size: 12px;
  color: #c0c4cc;
  margin-top: 8px;
}

.message {
  margin-bottom: 14px;
  display: flex;
}

.message.user {
  justify-content: flex-end;
}

.message.user .bubble {
  background: #1577ff;
  color: #fff;
  border-radius: 10px 10px 2px 10px;
}

.message.assistant .bubble,
.message.tool .bubble,
.message.system .bubble {
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 10px 10px 10px 2px;
}

.bubble {
  max-width: 75%;
  padding: 10px 14px;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 14px;
  line-height: 1.6;
}

.file-bubble {
  background: #f0f9eb !important;
  border-color: #e1f3d8 !important;
  color: #529b2e;
}

.case-preview-card {
  max-width: 85%;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-left: 3px solid #722ed1;
  border-radius: 6px;
  padding: 12px 14px;
  font-size: 13px;
}

.preview-title {
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.preview-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}

.preview-table th,
.preview-table td {
  border: 1px solid #ebeef5;
  padding: 6px 8px;
  text-align: left;
  word-break: break-all;
}

.preview-table th {
  background: #f5f7fa;
  color: #606266;
}

.preview-url {
  max-width: 280px;
}

.preview-tip {
  margin-top: 8px;
  color: #909399;
  font-size: 12px;
}

.tool-card {
  max-width: 75%;
  background: #fff;
  border: 1px solid #e4e7ed;
  border-left: 3px solid #1577ff;
  border-radius: 6px;
  padding: 10px 14px;
  font-size: 13px;
}

.tool-card.result {
  border-left-color: #67c23a;
}

.tool-card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4px;
}

.tool-name {
  font-weight: 600;
  color: #303133;
}

.tool-card-body {
  color: #606266;
  word-break: break-all;
}

.confirm-card {
  max-width: 75%;
  background: #fff7e6;
  border: 1px solid #ffd591;
  border-radius: 8px;
  padding: 12px 16px;
  font-size: 13px;
}

.confirm-title {
  font-weight: 600;
  color: #d46b08;
  margin-bottom: 6px;
}

.confirm-tool,
.confirm-payload {
  color: #874d00;
  word-break: break-all;
  margin-bottom: 4px;
}

.confirm-actions {
  margin-top: 10px;
  display: flex;
  gap: 8px;
}

.system-msg {
  color: #f56c6c;
  font-size: 13px;
}

.input-area {
  display: flex;
  gap: 10px;
  padding: 12px 16px;
  border-top: 1px solid #ebeef5;
  background: #fff;
}

.send-button {
  align-self: flex-end;
}
</style>

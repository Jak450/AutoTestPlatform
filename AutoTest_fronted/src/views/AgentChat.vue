<template>
  <div class="agent-page">
    <!-- 左侧会话列表 -->
    <div class="conversation-panel">
      <div class="panel-header">
        <span>会话列表</span>
        <el-button type="primary" size="small" @click="createConversation">
          <el-icon><i-ep-plus /></el-icon>
          新建会话
        </el-button>
      </div>
      <div class="conversation-list">
        <div
          v-for="conv in conversations"
          :key="conv.id"
          class="conversation-item"
          :class="{ active: conv.id === currentConversationId }"
          @click="switchConversation(conv.id)"
        >
          <div class="conv-title">{{ conv.title || '新会话' }}</div>
          <el-icon class="delete-btn" @click.stop="deleteConversation(conv.id)"><i-ep-delete /></el-icon>
        </div>
        <el-empty v-if="conversations.length === 0" description="暂无会话" :image-size="60" />
      </div>
    </div>

    <!-- 右侧对话工作台 -->
    <div class="chat-panel">
      <div class="chat-messages" ref="messageContainer">
        <template v-if="messages.length > 0">
          <div v-for="msg in messages" :key="msg.id" class="message-row" :class="msg.role">
            <div class="message-avatar">{{ msg.role === 'user' ? '我' : 'AI' }}</div>
            <div class="message-bubble">
              <div v-if="msg.type === 'tool_result'" class="tool-result">
                <div class="tool-tag">{{ msg.toolName || '工具' }}</div>
                <div class="tool-content">{{ msg.content }}</div>
              </div>
              <div v-else-if="msg.type === 'confirmation'" class="confirmation-card">
                <div class="confirmation-title">需要确认</div>
                <div class="confirmation-content">{{ msg.content }}</div>
              </div>
              <div v-else class="text-content" v-html="renderMarkdown(msg.content)"></div>
            </div>
          </div>
        </template>
        <div v-if="streaming" class="message-row assistant">
          <div class="message-avatar">AI</div>
          <div class="message-bubble">
            <div class="text-content streaming-text">{{ streamingText }}<span class="cursor">▍</span></div>
          </div>
        </div>
        <el-empty v-if="messages.length === 0 && !streaming" description="开始与 AI Agent 对话吧" />
      </div>

      <!-- 确认卡片区 -->
      <div v-if="pendingConfirmations.length > 0" class="confirmation-area">
        <div v-for="conf in pendingConfirmations" :key="conf.confirmationId" class="confirmation-card">
          <div class="confirmation-title">需要确认的操作</div>
          <div class="confirmation-tool">工具：{{ conf.toolName }}（{{ conf.permission }}）</div>
          <pre class="confirmation-payload">{{ prettyArgs(conf.arguments) }}</pre>
          <div class="confirmation-actions">
            <el-button type="primary" size="small" @click="respondConfirmation(conf, 'approved')">批准</el-button>
            <el-button type="danger" size="small" @click="respondConfirmation(conf, 'rejected')">拒绝</el-button>
          </div>
        </div>
      </div>

      <!-- 输入区 -->
      <div class="chat-input">
        <el-upload
          :auto-upload="false"
          :show-file-list="false"
          accept=".md,.pdf,.doc,.docx,.txt"
          :on-change="handleFileSelect"
        >
          <el-button :icon="PaperclipIcon">附件</el-button>
        </el-upload>
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="3"
          resize="none"
          placeholder="输入消息，例如：查看项目下有哪些用例；执行用例 1 和 2；解析这份需求文档并生成用例"
          @keydown.enter.exact.prevent="sendMessage"
        />
        <div class="input-actions">
          <div v-if="selectedFiles.length > 0" class="selected-files">
            <el-tag v-for="f in selectedFiles" :key="f.name" closable @close="selectedFiles = selectedFiles.filter(x => x !== f)">
              {{ f.name }}
            </el-tag>
          </div>
          <el-button type="primary" :loading="running" :disabled="running" @click="sendMessage">
            发送
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { ref, reactive, onMounted, nextTick, computed } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Paperclip } from '@element-plus/icons-vue'

export default {
  name: 'AgentChat',
  setup() {
    const conversations = ref([])
    const currentConversationId = ref(null)
    const messages = ref([])
    const inputText = ref('')
    const running = ref(false)
    const streaming = ref(false)
    const streamingText = ref('')
    const pendingConfirmations = ref([])
    const selectedFiles = ref([])
    const messageContainer = ref(null)
    let eventSource = null
    let streamController = null
    let lastEventId = 0

    const loadConversations = async () => {
      try {
        const res = await axios.get('/agent/conversations')
        conversations.value = res.data.data || []
      } catch (e) {
        ElMessage.error('加载会话失败')
      }
    }

    const createConversation = async () => {
      try {
        const res = await axios.post('/agent/conversations', { title: '新会话' })
        const conv = res.data.data
        conversations.value.unshift(conv)
        await switchConversation(conv.id)
      } catch (e) {
        ElMessage.error('创建会话失败')
      }
    }

    const switchConversation = async (id) => {
      closeStream()
      currentConversationId.value = id
      pendingConfirmations.value = []
      try {
        const res = await axios.get(`/agent/conversations/${id}/messages`)
        messages.value = res.data.data || []
        scrollToBottom()
      } catch (e) {
        ElMessage.error('加载消息失败')
      }
    }

    const deleteConversation = async (id) => {
      try {
        await ElMessageBox.confirm('确定删除该会话吗？', '警告', { type: 'warning' })
        await axios.delete(`/agent/conversations/${id}`)
        conversations.value = conversations.value.filter(c => c.id !== id)
        if (currentConversationId.value === id) {
          currentConversationId.value = null
          messages.value = []
        }
        ElMessage.success('删除成功')
      } catch (e) {
        if (e !== 'cancel') ElMessage.error('删除失败')
      }
    }

    const handleFileSelect = async (file) => {
      if (!currentConversationId.value) {
        ElMessage.warning('请先选择或创建会话')
        return
      }
      const formData = new FormData()
      formData.append('file', file.raw)
      try {
        const res = await axios.post(`/agent/conversations/${currentConversationId.value}/files`, formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        const attachment = res.data.data
        // 将文件内容作为消息发送
        const content = `我上传了文档《${attachment.fileName}》，请分析它的内容`
        inputText.value = content
        ElMessage.success('文件上传成功，已解析')
      } catch (e) {
        ElMessage.error(e.response?.data?.msg || '文件上传失败')
      }
    }

    const sendMessage = async () => {
      const content = inputText.value.trim()
      if (!content) return
      if (!currentConversationId.value) {
        await createConversation()
      }
      inputText.value = ''
      selectedFiles.value = []
      running.value = true
      streaming.value = true
      streamingText.value = ''
      pendingConfirmations.value = []

      // 立即显示用户消息
      messages.value.push({
        id: `u_${Date.now()}`,
        role: 'user',
        type: 'text',
        content
      })
      scrollToBottom()

      openStream(currentConversationId.value)

      try {
        await axios.post(`/agent/conversations/${currentConversationId.value}/messages`, {
          content,
          idempotencyKey: `msg_${Date.now()}`
        })
      } catch (e) {
        ElMessage.error(e.response?.data?.msg || '发送失败')
        closeStream()
        running.value = false
        streaming.value = false
      }
    }

    const openStream = (conversationId) => {
      closeStream()
      lastEventId = 0
      const token = localStorage.getItem('token')
      // 使用 fetch 流式读取实现 SSE（可携带 Authorization 头）
      fetchStream(conversationId, token)
    }

    // 使用 fetch 实现 SSE（可携带 Authorization 头）
    const fetchStream = async (conversationId, token) => {
      const controller = new AbortController()
      streamController = controller
      try {
        const response = await fetch(`/api/agent/conversations/${conversationId}/stream`, {
          headers: {
            'Authorization': `Bearer ${token}`,
            'Accept': 'text/event-stream'
          },
          signal: controller.signal
        })
        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })
          // 解析 SSE 块
          const blocks = buffer.split('\n\n')
          buffer = blocks.pop()
          for (const block of blocks) {
            handleSseBlock(block)
          }
        }
      } catch (e) {
        if (e.name === 'AbortError') {
          console.log('SSE 连接已中止')
        } else {
          console.error('SSE 连接失败', e)
        }
      } finally {
        streamController = null
        running.value = false
        streaming.value = false
        if (streamingText.value) {
          messages.value.push({
            id: `a_${Date.now()}`,
            role: 'assistant',
            type: 'text',
            content: streamingText.value
          })
          streamingText.value = ''
        }
        scrollToBottom()
      }
    }

    const handleSseBlock = (block) => {
      let eventName = 'message'
      let data = ''
      for (const line of block.split('\n')) {
        if (line.startsWith('event:')) {
          eventName = line.substring(6).trim()
        } else if (line.startsWith('data:')) {
          data = line.substring(5).trim()
        } else if (line.startsWith('id:')) {
          lastEventId = parseInt(line.substring(3).trim()) || 0
        }
      }
      if (!data) return

      if (eventName === 'message_update') {
        try {
          const payload = JSON.parse(data)
          if (payload.delta) {
            streamingText.value += payload.delta
            scrollToBottom()
          }
        } catch (e) { }
      } else if (eventName === 'confirmation_created') {
        try {
          const payload = JSON.parse(data)
          pendingConfirmations.value.push(payload)
        } catch (e) { }
      } else if (eventName === 'tool_execution_start') {
        try {
          const payload = JSON.parse(data)
          streamingText.value += `\n[工具调用] ${payload.toolName} 执行中...\n`
        } catch (e) { }
      } else if (eventName === 'tool_execution_end') {
        try {
          const payload = JSON.parse(data)
          streamingText.value += `[工具调用] ${payload.toolName} 完成（${payload.status}）\n`
        } catch (e) { }
      }
    }

    const respondConfirmation = async (conf, decision) => {
      try {
        await axios.post(`/agent/conversations/${currentConversationId.value}/confirmations/${conf.confirmationId}`, {
          decision
        })
        pendingConfirmations.value = pendingConfirmations.value.filter(c => c.confirmationId !== conf.confirmationId)
        ElMessage.success(decision === 'approved' ? '已批准，继续执行' : '已拒绝')
        if (decision === 'approved') {
          // SSE 连接保持打开，后端异步继续执行并推送事件
          streaming.value = true
          streamingText.value = '\n[继续执行中...]\n'
          scrollToBottom()
        }
      } catch (e) {
        ElMessage.error(e.response?.data?.msg || '确认失败')
      }
    }

    const closeStream = () => {
      if (streamController) {
        streamController.abort()
        streamController = null
      }
      if (eventSource) {
        eventSource.close()
        eventSource = null
      }
    }

    const prettyArgs = (args) => {
      try {
        return JSON.stringify(JSON.parse(args), null, 2)
      } catch (e) {
        return args
      }
    }

    const renderMarkdown = (text) => {
      if (!text) return ''
      // 简单转义 + 换行处理
      let escaped = String(text)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
      // 代码块
      escaped = escaped.replace(/```([\s\S]*?)```/g, '<pre class="code-block">$1</pre>')
      // 行内代码
      escaped = escaped.replace(/`([^`]+)`/g, '<code>$1</code>')
      // 换行
      escaped = escaped.replace(/\n/g, '<br/>')
      return escaped
    }

    const scrollToBottom = () => {
      nextTick(() => {
        if (messageContainer.value) {
          messageContainer.value.scrollTop = messageContainer.value.scrollHeight
        }
      })
    }

    onMounted(async () => {
      await loadConversations()
      if (conversations.value.length > 0) {
        await switchConversation(conversations.value[0].id)
      }
    })

    return {
      conversations,
      currentConversationId,
      messages,
      inputText,
      running,
      streaming,
      streamingText,
      pendingConfirmations,
      selectedFiles,
      messageContainer,
      createConversation,
      switchConversation,
      deleteConversation,
      handleFileSelect,
      sendMessage,
      respondConfirmation,
      prettyArgs,
      renderMarkdown
    }
  }
}
</script>

<style scoped>
.agent-page {
  display: flex;
  height: calc(100vh - 140px);
  gap: 16px;
}

.conversation-panel {
  width: 260px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.panel-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border-bottom: 1px solid #f0f0f0;
  font-weight: 600;
}

.conversation-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.conversation-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 10px 12px;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 4px;
  transition: background 0.2s;
}

.conversation-item:hover {
  background: #f5f7fa;
}

.conversation-item.active {
  background: #ecf5ff;
  color: #1577ff;
}

.conv-title {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 14px;
}

.delete-btn {
  display: none;
  color: #f56c6c;
}

.conversation-item:hover .delete-btn {
  display: inline-flex;
}

.chat-panel {
  flex: 1;
  background: #fff;
  border-radius: 8px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: #f5f7fa;
}

.message-row {
  display: flex;
  margin-bottom: 16px;
  gap: 10px;
}

.message-row.user {
  flex-direction: row-reverse;
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  flex-shrink: 0;
  color: #fff;
}

.message-row.user .message-avatar {
  background: #1577ff;
}

.message-row.assistant .message-avatar {
  background: #13c2c2;
}

.message-bubble {
  max-width: 70%;
  background: #fff;
  border-radius: 8px;
  padding: 12px 14px;
  box-shadow: 0 1px 2px rgba(0, 0, 0, 0.06);
  font-size: 14px;
  line-height: 1.7;
  word-break: break-word;
}

.message-row.user .message-bubble {
  background: #1577ff;
  color: #fff;
}

.streaming-text {
  color: #262626;
}

.cursor {
  animation: blink 1s infinite;
  color: #1577ff;
}

@keyframes blink {
  0%, 100% { opacity: 1; }
  50% { opacity: 0; }
}

.code-block {
  background: #f6f8fa;
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 13px;
}

.tool-result {
  font-size: 13px;
}

.tool-tag {
  display: inline-block;
  background: #ecf5ff;
  color: #1577ff;
  padding: 2px 8px;
  border-radius: 4px;
  margin-bottom: 6px;
  font-size: 12px;
}

.tool-content {
  color: #666;
}

.confirmation-area {
  padding: 12px 20px;
  border-top: 1px solid #f0f0f0;
  background: #fffbe6;
}

.confirmation-card {
  border: 1px solid #ffd591;
  border-radius: 8px;
  padding: 12px;
  background: #fff7e6;
  margin-bottom: 8px;
}

.confirmation-title {
  font-weight: 600;
  margin-bottom: 6px;
}

.confirmation-tool {
  color: #666;
  margin-bottom: 6px;
  font-size: 13px;
}

.confirmation-payload {
  background: #fff;
  padding: 8px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 160px;
  overflow-y: auto;
}

.confirmation-actions {
  margin-top: 8px;
  display: flex;
  gap: 8px;
}

.chat-input {
  padding: 12px 20px;
  border-top: 1px solid #f0f0f0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.input-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.selected-files {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}
</style>

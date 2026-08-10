<template>
  <div class="msg" :class="`msg-${msg.role}`">
    <!-- 文本 -->
    <div v-if="msg.type === 'text'" class="bubble" :class="msg.role === 'user' ? 'bubble-user' : 'bubble-ai'">
      <span v-if="msg.thinking && !msg.content" class="thinking">思考中…</span>
      <template v-else>{{ msg.content }}</template>
    </div>

    <!-- 文件 -->
    <div v-else-if="msg.type === 'file'" class="file-line mono">📄 {{ msg.content }}</div>

    <!-- 工具调用 -->
    <div v-else-if="msg.type === 'tool_call'" class="tool-card">
      <div class="tool-head">
        <span class="mono tool-name">{{ msg.toolName }}</span>
        <div class="tool-status">
          <StatusStamp :status="stampStatus" />
          <span v-if="durationText" class="mono duration">{{ durationText }}</span>
        </div>
      </div>
      <div v-if="msg.argsText" class="tool-body mono">{{ msg.argsText }}</div>
    </div>

    <!-- 工具结果 -->
    <div v-else-if="msg.type === 'tool_result'" class="tool-card result">
      <div class="tool-head">
        <span class="mono tool-name">{{ msg.toolName }}</span>
        <div class="tool-status">
          <StatusStamp :status="stampStatus" />
          <span v-if="durationText" class="mono duration">{{ durationText }}</span>
        </div>
      </div>
      <div v-if="msg.content" class="tool-body">{{ msg.content }}</div>
    </div>

    <!-- 确认卡 -->
    <div v-else-if="msg.type === 'confirmation'" class="confirm-card">
      <div class="confirm-title">需要确认 · {{ msg.toolName }}</div>
      <div v-if="msg.payloadText" class="mono confirm-payload">{{ msg.payloadText }}</div>
      <div class="confirm-actions">
        <el-button size="small" type="primary" :disabled="msg.handled" @click="$emit('confirm', msg, 'approved')">
          批准
        </el-button>
        <el-button size="small" type="danger" plain :disabled="msg.handled" @click="$emit('confirm', msg, 'rejected')">
          拒绝
        </el-button>
      </div>
    </div>

    <!-- 用例预览 -->
    <div v-else-if="msg.type === 'case_preview'" class="preview-card">
      <div class="preview-title">用例草稿（{{ msg.cases.length }} 条）</div>
      <table class="preview-table">
        <thead>
          <tr><th>名称</th><th>方法</th><th>URL</th></tr>
        </thead>
        <tbody>
          <tr v-for="(c, i) in msg.cases" :key="i">
            <td>{{ c.name }}</td>
            <td><MethodBadge :method="c.method" /></td>
            <td class="mono preview-url">{{ c.url }}</td>
          </tr>
        </tbody>
      </table>
    </div>

    <!-- 系统 -->
    <div v-else-if="msg.type === 'system'" class="sys-msg">{{ msg.content }}</div>
  </div>
</template>

<script>
import StatusStamp from '../ui/StatusStamp.vue'
import MethodBadge from '../ui/MethodBadge.vue'

export default {
  name: 'MessageItem',
  components: { StatusStamp, MethodBadge },
  props: {
    msg: { type: Object, required: true }
  },
  emits: ['confirm'],
  computed: {
    stampStatus() {
      const s = String(this.msg.status || 'done')
      const first = s.split(' ')[0]
      return first || 'done'
    },
    durationText() {
      const s = String(this.msg.status || '')
      const m = s.match(/\(([^)]+)\)/)
      return m ? m[1] : ''
    }
  }
}
</script>

<style scoped>
.msg {
  display: flex;
  margin-bottom: 16px;
}
.msg-user {
  justify-content: flex-end;
}
.bubble {
  max-width: 75%;
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
  border-radius: var(--radius-bubble);
}
.bubble-user {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 10px 10px 2px 10px;
}
.bubble-ai {
  background: var(--primary-soft);
  color: var(--ink-strong);
  border-radius: 10px 10px 10px 2px;
}
.file-line {
  background: var(--panel);
  border: 1px solid var(--line);
  border-left: 3px solid var(--primary);
  padding: 8px 12px;
  border-radius: 6px;
  font-size: 13px;
}
.tool-card {
  max-width: 78%;
  background: var(--panel);
  border: 1px solid var(--line);
  border-left: 3px solid var(--primary);
  border-radius: 6px;
  padding: 10px 14px;
}
.tool-card.result {
  border-left-color: var(--success);
}
.tool-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
}
.tool-status {
  display: flex;
  align-items: center;
  gap: 6px;
}
.tool-name {
  font-weight: 500;
}
.duration {
  font-size: 11px;
  color: var(--ink-muted);
}
.tool-body {
  margin-top: 6px;
  font-size: 12px;
  color: var(--ink-muted);
  word-break: break-all;
}
.confirm-card {
  max-width: 78%;
  background: #FFFDF7;
  border: 1px solid #F3D9A8;
  border-radius: 8px;
  padding: 12px 16px;
}
.confirm-title {
  font-weight: 600;
  color: #9A5B00;
  margin-bottom: 6px;
}
.confirm-payload {
  font-size: 12px;
  color: #7A5B2A;
  word-break: break-all;
  margin-bottom: 10px;
}
.preview-card {
  max-width: 88%;
  background: var(--panel);
  border: 1px solid var(--line);
  border-left: 3px solid var(--primary);
  border-radius: 6px;
  padding: 12px 14px;
}
.preview-title {
  font-weight: 600;
  margin-bottom: 8px;
}
.preview-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 12px;
}
.preview-table th,
.preview-table td {
  border: 1px solid var(--line-soft);
  padding: 6px 8px;
  text-align: left;
  word-break: break-all;
}
.preview-table th {
  background: #F8FAFD;
  color: var(--ink-muted);
  font-family: var(--font-mono);
  font-size: 11px;
}
.sys-msg {
  color: var(--danger);
  font-size: 13px;
}
.thinking {
  color: var(--ink-muted);
  font-size: 13px;
  font-style: italic;
}
</style>

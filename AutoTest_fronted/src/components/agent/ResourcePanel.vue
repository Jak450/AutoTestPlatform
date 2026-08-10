<template>
  <aside class="resource-panel">
    <div class="rp-head">资源</div>
    <div class="rp-group">
      <div class="rp-group-title mono">文件</div>
      <div v-if="files.length" class="rp-item mono" v-for="f in files" :key="f.id">📄 {{ f.fileName }}</div>
      <p v-else class="rp-empty">暂无文件</p>
    </div>
    <div class="rp-group">
      <div class="rp-group-title mono">模板</div>
      <div v-if="templates.length" class="rp-item" v-for="t in templates" :key="t.id">{{ t.name }}</div>
      <p v-else class="rp-empty">暂无模板</p>
    </div>
    <div class="rp-group">
      <div class="rp-group-title mono">记忆</div>
      <div v-if="memories.length" class="rp-item" v-for="m in memories" :key="m.id || m.key">
        <div class="rp-memory-line">
          <span>{{ m.key }}</span>
          <el-tag v-if="m.confirmed === 0" size="small" type="warning">待确认</el-tag>
        </div>
        <p v-if="m.content" class="rp-snippet">{{ m.content }}</p>
        <el-button v-if="m.confirmed === 0" size="small" type="primary" plain @click="$emit('confirm-memory', m)">
          确认
        </el-button>
      </div>
      <p v-else class="rp-empty">暂无记忆</p>
    </div>
    <div class="rp-group">
      <div class="rp-group-title mono">知识</div>
      <div v-if="knowledgeGroups.length" class="rp-cat-block" v-for="g in knowledgeGroups" :key="g.category">
        <div class="rp-cat-head mono">{{ g.category }} <span class="rp-count">{{ g.items.length }}</span></div>
        <div class="rp-item" v-for="k in g.items" :key="g.category + '/' + k.slug">
          <div class="rp-memory-line">
            <span>{{ k.title }}</span>
            <el-tag v-if="!k.confirmed" size="small" type="warning">待确认</el-tag>
          </div>
          <p v-if="k.snippet" class="rp-snippet">{{ k.snippet }}</p>
          <div class="rp-actions">
            <el-button size="small" plain @click="viewKnowledge(k)">查看全文</el-button>
            <el-button v-if="!k.confirmed" size="small" type="primary" plain @click="$emit('confirm-knowledge', k)">
              确认
            </el-button>
          </div>
        </div>
      </div>
      <p v-else class="rp-empty">暂无知识</p>
    </div>
  </aside>

  <el-dialog v-model="detailVisible" :title="detailDoc ? `${detailDoc.title} [${detailDoc.category}]` : '知识详情'"
             width="640px" top="8vh">
    <pre v-if="detailContent" class="rp-detail-content">{{ detailContent }}</pre>
    <p v-else class="rp-empty">加载中…</p>
  </el-dialog>
</template>

<script>
import axios from 'axios'

export default {
  name: 'ResourcePanel',
  props: {
    files: { type: Array, default: () => [] },
    templates: { type: Array, default: () => [] },
    memories: { type: Array, default: () => [] },
    knowledgeDocs: { type: Array, default: () => [] }
  },
  emits: ['confirm-memory', 'confirm-knowledge'],
  computed: {
    knowledgeGroups() {
      const groups = {}
      for (const k of this.knowledgeDocs) {
        const category = k.category || '默认'
        if (!groups[category]) groups[category] = []
        groups[category].push(k)
      }
      return Object.entries(groups).map(([category, items]) => ({ category, items }))
    }
  },
  data() {
    return {
      detailVisible: false,
      detailDoc: null,
      detailContent: ''
    }
  },
  methods: {
    async viewKnowledge(k) {
      this.detailDoc = k
      this.detailContent = ''
      this.detailVisible = true
      try {
        const res = await axios.get('/agent/knowledge/detail', {
          params: { slug: k.slug, category: k.category }
        })
        this.detailContent = (res.data && res.data.data && res.data.data.content) || '(无内容)'
      } catch (e) {
        this.detailContent = '加载失败: ' + ((e.response && e.response.data && e.response.data.msg) || e.message)
      }
    }
  }
}
</script>

<style scoped>
.resource-panel {
  width: 280px;
  flex: none;
  background: var(--panel);
  border-left: 1px solid var(--line);
  padding: 16px;
  overflow-y: auto;
}
.rp-head {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 15px;
  margin-bottom: 12px;
}
.rp-group {
  margin-bottom: 20px;
}
.rp-group-title {
  font-size: 11px;
  color: var(--ink-muted);
  margin-bottom: 6px;
  text-transform: uppercase;
}
.rp-item {
  font-size: 12px;
  color: var(--ink-body);
  padding: 4px 0;
  border-bottom: 1px dashed var(--line-soft);
  word-break: break-all;
}
.rp-memory-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 6px;
}
.rp-snippet {
  font-size: 12px;
  color: var(--ink-muted);
  margin: 4px 0;
  line-height: 1.5;
  word-break: break-all;
  max-height: 3.2em;
  overflow: hidden;
}
.rp-actions {
  display: flex;
  gap: 6px;
  margin-top: 4px;
}
.rp-detail-content {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  line-height: 1.7;
  max-height: 60vh;
  overflow-y: auto;
  margin: 0;
}
.rp-cat {
  font-style: normal;
  font-size: 11px;
  color: var(--ink-muted);
}
.rp-cat-block {
  margin-bottom: 10px;
}
.rp-cat-head {
  font-size: 11px;
  font-weight: 600;
  color: var(--ink-body);
  margin: 8px 0 2px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.rp-count {
  background: var(--line-soft);
  border-radius: 8px;
  padding: 0 6px;
  font-size: 10px;
  color: var(--ink-muted);
}
.rp-empty {
  font-size: 12px;
  color: var(--ink-muted);
}
</style>

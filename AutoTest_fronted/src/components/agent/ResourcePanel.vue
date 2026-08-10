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
        <el-button v-if="m.confirmed === 0" size="small" type="primary" plain @click="$emit('confirm-memory', m)">
          确认
        </el-button>
      </div>
      <p v-else class="rp-empty">暂无记忆</p>
    </div>
  </aside>
</template>

<script>
export default {
  name: 'ResourcePanel',
  props: {
    files: { type: Array, default: () => [] },
    templates: { type: Array, default: () => [] },
    memories: { type: Array, default: () => [] }
  },
  emits: ['confirm-memory']
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
.rp-empty {
  font-size: 12px;
  color: var(--ink-muted);
}
</style>

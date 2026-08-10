<template>
  <aside class="conv-list">
    <div class="conv-head">
      <span class="conv-title-label">会话</span>
      <el-button size="small" type="primary" plain @click="$emit('create')">新建</el-button>
    </div>
    <div class="conv-items">
      <div
        v-for="conv in conversations"
        :key="conv.id"
        class="conv-item"
        :class="{ active: conv.id === currentId }"
        @click="$emit('select', conv.id)"
      >
        <span class="conv-name">{{ conv.title }}</span>
        <span class="conv-del" @click.stop="$emit('remove', conv.id)">×</span>
      </div>
      <p v-if="!conversations.length" class="conv-empty">暂无会话，点上方新建</p>
    </div>
  </aside>
</template>

<script>
export default {
  name: 'ConversationList',
  props: {
    conversations: { type: Array, default: () => [] },
    currentId: { type: Number, default: null }
  },
  emits: ['create', 'select', 'remove']
}
</script>

<style scoped>
.conv-list {
  width: 240px;
  flex: none;
  background: var(--panel);
  border-right: 1px solid var(--line);
  display: flex;
  flex-direction: column;
}
.conv-head {
  padding: 14px 14px 10px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.conv-title-label {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--ink-muted);
}
.conv-items {
  flex: 1;
  overflow-y: auto;
  padding: 4px 10px 12px;
}
.conv-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 9px 10px;
  margin-bottom: 4px;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: var(--ink-body);
}
.conv-item:hover {
  background: var(--primary-soft);
}
.conv-item.active {
  background: var(--primary-soft);
  color: var(--primary);
  font-weight: 500;
  box-shadow: inset 3px 0 0 var(--primary);
}
.conv-name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.conv-del {
  color: var(--ink-muted);
  padding: 0 3px;
}
.conv-del:hover {
  color: var(--danger);
}
.conv-empty {
  padding: 20px 8px;
  text-align: center;
  color: var(--ink-muted);
  font-size: 12px;
}
</style>

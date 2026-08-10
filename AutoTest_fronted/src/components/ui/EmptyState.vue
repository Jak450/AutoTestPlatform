<template>
  <div class="empty-state">
    <div class="empty-mark"></div>
    <p class="empty-title">{{ title }}</p>
    <p v-if="hint" class="empty-hint">{{ hint }}</p>
    <div v-if="$slots.default" class="empty-actions"><slot /></div>
  </div>
</template>

<script>
export default {
  name: 'EmptyState',
  props: {
    title: String,
    hint: { type: String, default: '' }
  }
}
</script>

<style scoped>
.empty-state {
  padding: 56px 20px;
  text-align: center;
  background: var(--panel);
  border: 1px dashed var(--line);
  border-radius: var(--radius-card);
}

.empty-mark {
  width: 34px;
  height: 34px;
  margin: 0 auto 12px;
  border-radius: 8px;
  background: var(--primary-soft);
  border: 1px solid var(--primary);
  opacity: 0.7;
}

.empty-title {
  font-family: var(--font-display);
  font-weight: 600;
  color: var(--ink-strong);
}

.empty-hint {
  margin-top: 4px;
  font-size: 13px;
  color: var(--ink-muted);
}

.empty-actions {
  margin-top: 14px;
}
</style>

<template>
  <div class="stat-card">
    <div class="stat-label">{{ label }}</div>
    <div class="stat-value" :style="{ color }">{{ value }}</div>
  </div>
</template>

<script>
export default {
  name: 'StatCard',
  props: {
    label: String,
    value: [String, Number],
    color: { type: String, default: 'var(--ink-strong)' }
  }
}
</script>

<style scoped>
.stat-card {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-panel);
  padding: 16px 18px;
}

.stat-label {
  font-size: 12px;
  color: var(--ink-muted);
  font-family: var(--font-mono);
  margin-bottom: 6px;
}

.stat-value {
  font-family: var(--font-display);
  font-size: 26px;
  font-weight: 700;
  line-height: 1;
}
</style>

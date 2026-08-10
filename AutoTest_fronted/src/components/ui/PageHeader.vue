<template>
  <div class="page-header">
    <div>
      <h1 class="page-title">{{ title }}</h1>
      <p v-if="description" class="page-desc">{{ description }}</p>
    </div>
    <div class="page-actions"><slot /></div>
  </div>
</template>

<script>
export default {
  name: 'PageHeader',
  props: {
    title: String,
    description: { type: String, default: '' }
  }
}
</script>

<style scoped>
.page-header {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  margin-bottom: 20px;
  gap: 16px;
  flex-wrap: wrap;
}

.page-title {
  font-size: 22px;
  font-weight: 700;
  letter-spacing: -0.3px;
}

.page-desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--ink-muted);
}

.page-actions {
  display: flex;
  gap: 10px;
}
</style>

<template>
  <span class="stamp" :class="`stamp-${status}`">
    <span v-if="status === 'running'" class="dot"></span>
    {{ displayLabel }}
  </span>
</template>

<script>
export default {
  name: 'StatusStamp',
  props: {
    status: { type: String, default: 'idle' },
    label: { type: String, default: '' }
  },
  computed: {
    displayLabel() {
      return this.label || this.statusLabel[this.status] || this.status
    }
  },
  data() {
    return {
      statusLabel: {
        passed: '通过',
        success: '通过',
        failed: '失败',
        error: '失败',
        broken: '失败',
        running: '运行中',
        skipped: '跳过',
        pending: '等待'
      }
    }
  }
}
</script>

<style scoped>
.stamp {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 500;
  letter-spacing: 0.4px;
  text-transform: uppercase;
  padding: 2px 8px;
  border-radius: 3px;
  border: 1.5px solid currentColor;
  transform: rotate(-1deg);
}

.stamp-passed,
.stamp-success {
  color: var(--success);
  background: rgba(31, 157, 85, 0.06);
}

.stamp-failed,
.stamp-error,
.stamp-broken {
  color: var(--danger);
  background: rgba(220, 38, 38, 0.06);
}

.stamp-skipped {
  color: var(--ink-muted);
  background: rgba(123, 135, 158, 0.08);
}

.stamp-pending {
  color: var(--warning);
  background: rgba(217, 119, 6, 0.07);
}

.stamp-running {
  color: var(--primary);
  background: var(--primary-soft);
  transform: none;
}

.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--primary);
  animation: breathe 1.6s ease-in-out infinite;
}

@keyframes breathe {
  0%,
  100% {
    opacity: 0.35;
  }
  50% {
    opacity: 1;
  }
}
</style>

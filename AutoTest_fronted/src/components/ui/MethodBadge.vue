<template>
  <span class="method" :class="`m-${method.toLowerCase()}`">{{ method }}</span>
</template>

<script>
export default {
  name: 'MethodBadge',
  props: {
    method: { type: String, required: true }
  }
}
</script>

<style scoped>
.method {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 600;
  padding: 1px 7px;
  border-radius: 3px;
}

.m-get {
  color: #2456E6;
  background: #E8EEFA;
}

.m-post {
  color: #1F9D55;
  background: #E4F4EC;
}

.m-put {
  color: #D97706;
  background: #FBF0E0;
}

.m-delete {
  color: #DC2626;
  background: #FBE9E9;
}

.m-patch {
  color: #7C3AED;
  background: #F1EAFE;
}
</style>

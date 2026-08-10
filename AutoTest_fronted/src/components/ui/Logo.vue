<template>
  <div class="logo">
    <div class="logo-mark"></div>
    <span class="logo-text">AutoTest<span class="logo-sub">·Spec</span></span>
  </div>
</template>

<style scoped>
.logo {
  display: flex;
  align-items: center;
  gap: 10px;
}

.logo-mark {
  width: 26px;
  height: 26px;
  border-radius: 8px;
  background: var(--primary);
  background-image:
    linear-gradient(rgba(255, 255, 255, 0.25) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255, 255, 255, 0.25) 1px, transparent 1px);
  background-size: 8px 8px;
}

.logo-text {
  font-family: var(--font-display);
  font-weight: 700;
  font-size: 17px;
  letter-spacing: -0.3px;
  color: var(--ink-strong);
}

.logo-sub {
  color: var(--primary);
}
</style>

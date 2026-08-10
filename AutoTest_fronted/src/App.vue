<template>
  <div class="app-shell">
    <TopNav v-if="!isLoginPage" />
    <main class="app-main" :class="{ 'app-main--full': isLoginPage }">
      <router-view v-slot="{ Component }">
        <transition name="fade" mode="out-in">
          <component :is="Component" />
        </transition>
      </router-view>
    </main>
  </div>
</template>

<script>
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import TopNav from './components/layout/TopNav.vue'

export default {
  name: 'App',
  components: { TopNav },
  setup() {
    const route = useRoute()
    const isLoginPage = computed(() => route.path === '/login')
    return { isLoginPage }
  }
}
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
}

.app-main {
  padding: 24px;
  max-width: 1440px;
  margin: 0 auto;
}

.app-main--full {
  max-width: none;
  padding: 0;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.16s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>

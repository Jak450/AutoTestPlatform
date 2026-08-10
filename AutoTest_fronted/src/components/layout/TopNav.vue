<template>
  <header class="topnav">
    <div class="topnav-left">
      <button class="burger" @click="drawerVisible = true" aria-label="菜单">☰</button>
      <Logo @click="$router.push('/agent')" style="cursor:pointer" />
    </div>
    <nav class="topnav-nav">
      <router-link to="/agent" class="nav-item">AI Agent</router-link>
      <el-dropdown trigger="click">
        <span class="nav-item">接口测试 <span class="caret">▾</span></span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="$router.push('/projects')">项目管理</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/use-cases')">用例管理</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/api-test')">API 测试</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/batch-execute')">批量执行</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <el-dropdown trigger="click">
        <span class="nav-item">UI 测试 <span class="caret">▾</span></span>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item @click="$router.push('/ui-test')">UI 测试工作台</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/ui-projects')">UI 项目管理</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/ui-use-cases')">UI 用例管理</el-dropdown-item>
            <el-dropdown-item @click="$router.push('/ui-batch-execute')">UI 批量执行</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>
      <router-link to="/ai-requirement" class="nav-item">AI 需求分析</router-link>
      <router-link v-if="isAdmin" to="/admin" class="nav-item">系统管理</router-link>
    </nav>
    <div class="topnav-right">
      <span class="user-name">{{ currentUser?.displayName || currentUser?.username }}</span>
      <el-button size="small" @click="logout">退出</el-button>
    </div>
  </header>

  <!-- 移动端抽屉菜单 -->
  <el-drawer v-model="drawerVisible" title="导航" size="240px">
    <div class="drawer-nav">
      <router-link to="/agent" class="drawer-item" @click="drawerVisible = false">AI Agent</router-link>
      <router-link to="/projects" class="drawer-item" @click="drawerVisible = false">接口 · 项目管理</router-link>
      <router-link to="/use-cases" class="drawer-item" @click="drawerVisible = false">接口 · 用例管理</router-link>
      <router-link to="/api-test" class="drawer-item" @click="drawerVisible = false">接口 · API 测试</router-link>
      <router-link to="/batch-execute" class="drawer-item" @click="drawerVisible = false">接口 · 批量执行</router-link>
      <router-link to="/ui-test" class="drawer-item" @click="drawerVisible = false">UI · 测试工作台</router-link>
      <router-link to="/ui-projects" class="drawer-item" @click="drawerVisible = false">UI · 项目管理</router-link>
      <router-link to="/ui-use-cases" class="drawer-item" @click="drawerVisible = false">UI · 用例管理</router-link>
      <router-link to="/ui-batch-execute" class="drawer-item" @click="drawerVisible = false">UI · 批量执行</router-link>
      <router-link to="/ai-requirement" class="drawer-item" @click="drawerVisible = false">AI 需求分析</router-link>
      <router-link v-if="isAdmin" to="/admin" class="drawer-item" @click="drawerVisible = false">系统管理</router-link>
    </div>
  </el-drawer>
</template>

<script>
import Logo from '../ui/Logo.vue'

export default {
  name: 'TopNav',
  components: { Logo },
  data() {
    return {
      currentUser: JSON.parse(localStorage.getItem('user') || 'null'),
      drawerVisible: false
    }
  },
  computed: {
    isAdmin() {
      return this.currentUser && this.currentUser.role === 'admin'
    }
  },
  methods: {
    async logout() {
      try {
        await this.$axios.post('/auth/logout')
      } catch (e) {
        // 登出接口失败不阻塞本地登出
      }
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      this.$router.push('/login')
    }
  }
}
</script>

<style scoped>
.topnav {
  height: 56px;
  background: var(--panel);
  border-bottom: 1px solid var(--line);
  display: flex;
  align-items: center;
  padding: 0 20px;
  gap: 24px;
  position: sticky;
  top: 0;
  z-index: 100;
}

.topnav-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.burger {
  display: none;
  background: none;
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 4px 8px;
  cursor: pointer;
}

.topnav-nav {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
}

.nav-item {
  font-size: 14px;
  color: var(--ink-body);
  padding: 6px 12px;
  border-radius: 6px;
  cursor: pointer;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.nav-item:hover,
.nav-item.router-link-active {
  color: var(--primary);
  background: var(--primary-soft);
}

.caret {
  font-size: 11px;
}

.drawer-nav {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.drawer-item {
  padding: 10px 12px;
  border-radius: 6px;
  color: var(--ink-body);
  text-decoration: none;
  font-size: 14px;
}

.drawer-item:hover,
.drawer-item.router-link-active {
  color: var(--primary);
  background: var(--primary-soft);
}

.topnav-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.user-name {
  font-size: 13px;
  color: var(--ink-muted);
}

@media (max-width: 960px) {
  .burger {
    display: block;
  }

  .topnav-nav {
    display: none;
  }
}
</style>

# 前端重设计（AutoTest · Blueprint）实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按已确认的设计文档（`docs/superpowers/specs/2026-08-10-frontend-redesign-design.md`）把 AutoTestPlatform 前端全部 10 个页面重做为"浅色蓝图"设计系统。

**Architecture:** Vue 3 + Vite 单页应用。设计 Token 与全局样式用纯 CSS 变量文件落地；门面（顶部导航、登录页、Agent 页、状态图章、统计卡）自建组件；数据密集页（表格/表单/弹窗）继续用 Element Plus 并做深度换肤。页面功能逻辑（axios、SSE、CRUD）保持不变，只改结构与样式。

**Tech Stack:** Vue 3.3、Vite 4.4、Element Plus 2.3（换肤）、@fontsource/sora、@fontsource/inter、@fontsource/ibm-plex-mono。

---

## 文件结构

```text
AutoTest_fronted/
├── package.json                          # 新增 3 个 fontsource 依赖
├── src/
│   ├── main.js                           # 引入全局样式
│   ├── App.vue                           # 重构：登录页无 TopNav，其余 TopNav + router-view
│   ├── styles/
│   │   ├── tokens.css                    # 设计 Token（CSS 变量）
│   │   ├── global.css                    # reset / 网格画布 / 滚动条 / 焦点环 / 动效
│   │   └── element-override.css          # Element Plus 深度换肤
│   ├── components/
│   │   ├── layout/TopNav.vue             # 顶部导航（含下拉与汉堡）
│   │   ├── ui/Logo.vue                   # 蓝图方块 Logo + 字标
│   │   ├── ui/StatusStamp.vue            # 状态图章（通过/失败/跳过/运行）
│   │   ├── ui/MethodBadge.vue            # HTTP 方法徽章
│   │   ├── ui/StatCard.vue               # 统计卡
│   │   ├── ui/PageHeader.vue             # 页面标题条
│   │   ├── ui/EmptyState.vue             # 空状态
│   │   └── agent/
│   │       ├── ConversationList.vue      # Agent 会话列表
│   │       ├── MessageItem.vue           # Agent 消息渲染（文本/文件/工具卡/确认卡/预览）
│   │       └── ResourcePanel.vue         # 右侧资源面板（文件/模板/记忆）
│   └── views/
│       ├── Login.vue                     # 重做
│       ├── Agent.vue                     # 重构（布局 + 使用子组件，逻辑保留）
│       ├── Projects.vue / UseCases.vue / ApiTest.vue / BatchExecute.vue
│       ├── UiTest.vue / UiProjects.vue / UiUseCases.vue / UiBatchExecute.vue
│       └── AiRequirement.vue             # 套用设计系统
```

**验证约定：** 每个 Task 结尾运行 `npm run build`（在 `AutoTest_fronted` 下），预期输出含 `✓ built`；功能回归在浏览器手测（登录 → 各路由可达 → Agent 对话流正常）。

---

### Task 1: 安装字体依赖

**Files:**
- Modify: `AutoTest_fronted/package.json`

- [ ] **Step 1: 添加依赖并安装**

```bash
cd AutoTest_fronted
npm install @fontsource/sora @fontsource/inter @fontsource/ibm-plex-mono
```

预期：`package.json` dependencies 出现三个 `@fontsource/*` 条目，`npm run build` 通过。

- [ ] **Step 2: 提交**

```bash
git add AutoTest_fronted/package.json AutoTest_fronted/package-lock.json
git commit -m "chore: 引入 Sora/Inter/IBM Plex Mono 字体包"
```

---

### Task 2: 设计 Token 与全局样式

**Files:**
- Create: `AutoTest_fronted/src/styles/tokens.css`
- Create: `AutoTest_fronted/src/styles/global.css`
- Modify: `AutoTest_fronted/src/main.js`

- [ ] **Step 1: 写 tokens.css**

```css
:root {
  --canvas: #F6F7F9;
  --grid: rgba(36, 86, 230, 0.05);
  --grid-size: 18px;
  --panel: #FFFFFF;
  --line: #DDE4F0;
  --line-soft: #EDF1F8;
  --primary: #2456E6;
  --primary-hover: #1B45C4;
  --primary-soft: #E8EEFA;
  --ink-strong: #17233B;
  --ink-body: #3D4A63;
  --ink-muted: #7B879E;
  --success: #1F9D55;
  --danger: #DC2626;
  --warning: #D97706;
  --mono-ink: #1E293B;
  --radius-card: 4px;
  --radius-btn: 6px;
  --radius-input: 6px;
  --radius-bubble: 8px;
  --shadow-panel: 0 1px 3px rgba(23, 35, 59, 0.06);
  --font-display: 'Sora', 'Inter', system-ui, sans-serif;
  --font-body: 'Inter', system-ui, -apple-system, sans-serif;
  --font-mono: 'IBM Plex Mono', Consolas, monospace;
}
```

- [ ] **Step 2: 写 global.css**

```css
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body { height: 100%; }
body {
  font-family: var(--font-body);
  font-size: 14px;
  color: var(--ink-body);
  background-color: var(--canvas);
  background-image:
    linear-gradient(var(--grid) 1px, transparent 1px),
    linear-gradient(90deg, var(--grid) 1px, transparent 1px);
  background-size: var(--grid-size) var(--grid-size);
}
h1, h2, h3, h4 { font-family: var(--font-display); color: var(--ink-strong); }
.mono { font-family: var(--font-mono); color: var(--mono-ink); }
::-webkit-scrollbar { width: 8px; height: 8px; }
::-webkit-scrollbar-thumb { background: #C9D3E5; border-radius: 4px; }
:focus-visible { outline: 2px solid var(--primary); outline-offset: 2px; }
@media (prefers-reduced-motion: reduce) {
  *, *::before, *::after { animation-duration: 0.01ms !important; transition-duration: 0.01ms !important; }
}
```

- [ ] **Step 3: main.js 引入样式（在 Element Plus css 之后）**

```js
import 'element-plus/dist/index.css'
import './styles/tokens.css'
import './styles/global.css'
import './styles/element-override.css'
```

- [ ] **Step 4: 验证**

Run: `npm run build`（AutoTest_fronted 下），预期 `✓ built`。

- [ ] **Step 5: 提交**

```bash
git add AutoTest_fronted/src/styles AutoTest_fronted/src/main.js
git commit -m "style: 设计 Token 与全局网格画布样式"
```

---

### Task 3: Element Plus 深度换肤

**Files:**
- Create: `AutoTest_fronted/src/styles/element-override.css`

- [ ] **Step 1: 写换肤文件（覆盖变量 + 组件样式）**

```css
:root {
  --el-color-primary: #2456E6;
  --el-color-primary-light-3: #5B7FED;
  --el-color-primary-light-5: #8CA6F2;
  --el-color-primary-light-7: #BDCEF8;
  --el-color-primary-light-8: #D5E0FA;
  --el-color-primary-light-9: #E8EEFA;
  --el-color-primary-dark-2: #1B45C4;
  --el-border-radius-base: 6px;
  --el-border-color: #DDE4F0;
  --el-border-color-lighter: #EDF1F8;
  --el-text-color-primary: #17233B;
  --el-text-color-regular: #3D4A63;
  --el-text-color-secondary: #7B879E;
  --el-font-family: 'Inter', system-ui, sans-serif;
}

.el-table { --el-table-border-color: #EDF1F8; --el-table-header-bg-color: #F8FAFD; --el-table-row-hover-bg-color: rgba(36,86,230,0.06); }
.el-table th.el-table__cell { font-family: var(--font-mono); font-size: 12px; font-weight: 500; color: var(--ink-muted); background: #F8FAFD; }
.el-table .el-table__cell { font-size: 13px; }
.el-button { border-radius: var(--radius-btn); font-weight: 500; }
.el-button--primary { --el-button-hover-bg-color: #1B45C4; --el-button-active-bg-color: #173BA8; }
.el-dialog { border-radius: 8px; border: 1px solid var(--line); }
.el-dialog__title { font-family: var(--font-display); font-size: 16px; font-weight: 600; }
.el-input__wrapper, .el-textarea__inner { border-radius: var(--radius-input); }
.el-input__wrapper.is-focus, .el-textarea__inner:focus { box-shadow: 0 0 0 1px var(--primary) inset; }
.el-tag { border-radius: 4px; font-family: var(--font-mono); }
.el-message-box { border-radius: 8px; }
```

- [ ] **Step 2: 验证**

Run: `npm run build`，预期 `✓ built`。

- [ ] **Step 3: 提交**

```bash
git add AutoTest_fronted/src/styles/element-override.css
git commit -m "style: Element Plus 深度换肤"
```

---

### Task 4: 自建基础 UI 组件

**Files:**
- Create: `AutoTest_fronted/src/components/ui/Logo.vue`
- Create: `AutoTest_fronted/src/components/ui/StatusStamp.vue`
- Create: `AutoTest_fronted/src/components/ui/MethodBadge.vue`
- Create: `AutoTest_fronted/src/components/ui/StatCard.vue`
- Create: `AutoTest_fronted/src/components/ui/PageHeader.vue`
- Create: `AutoTest_fronted/src/components/ui/EmptyState.vue`

- [ ] **Step 1: Logo.vue**

```vue
<template>
  <div class="logo">
    <div class="logo-mark"></div>
    <span class="logo-text">AutoTest<span class="logo-sub">·Spec</span></span>
  </div>
</template>
<style scoped>
.logo { display: flex; align-items: center; gap: 10px; }
.logo-mark { width: 26px; height: 26px; border-radius: 8px; background: var(--primary);
  background-image: linear-gradient(rgba(255,255,255,.25) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,.25) 1px, transparent 1px);
  background-size: 8px 8px; }
.logo-text { font-family: var(--font-display); font-weight: 700; font-size: 17px; letter-spacing: -.3px; color: var(--ink-strong); }
.logo-sub { color: var(--primary); }
</style>
```

- [ ] **Step 2: StatusStamp.vue（状态图章）**

```vue
<template>
  <span class="stamp" :class="`stamp-${status}`">
    <span v-if="status === 'running'" class="dot"></span>
    {{ label }}
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
    label() { return this.label || this.statusLabel[this.status] || this.status }
  },
  data() { return { statusLabel: { passed: '通过', success: '通过', failed: '失败', error: '失败', broken: '失败', running: '运行中', skipped: '跳过', pending: '等待' } } }
}
</script>
<style scoped>
.stamp { display: inline-flex; align-items: center; gap: 5px; font-family: var(--font-mono);
  font-size: 12px; font-weight: 500; letter-spacing: .4px; text-transform: uppercase;
  padding: 2px 8px; border-radius: 3px; border: 1.5px solid currentColor; transform: rotate(-1deg); }
.stamp-passed, .stamp-success { color: var(--success); background: rgba(31,157,85,.06); }
.stamp-failed, .stamp-error, .stamp-broken { color: var(--danger); background: rgba(220,38,38,.06); }
.stamp-skipped { color: var(--ink-muted); background: rgba(123,135,158,.08); }
.stamp-pending { color: var(--warning); background: rgba(217,119,6,.07); }
.stamp-running { color: var(--primary); background: var(--primary-soft); transform: none; }
.dot { width: 7px; height: 7px; border-radius: 50%; background: var(--primary); animation: breathe 1.6s ease-in-out infinite; }
@keyframes breathe { 0%,100% { opacity: .35; } 50% { opacity: 1; } }
</style>
```

- [ ] **Step 3: MethodBadge.vue**

```vue
<template>
  <span class="method" :class="`m-${method.toLowerCase()}`">{{ method }}</span>
</template>
<script>
export default { name: 'MethodBadge', props: { method: { type: String, required: true } } }
</script>
<style scoped>
.method { font-family: var(--font-mono); font-size: 11px; font-weight: 600; padding: 1px 7px; border-radius: 3px; }
.m-get { color: #2456E6; background: #E8EEFA; }
.m-post { color: #1F9D55; background: #E4F4EC; }
.m-put { color: #D97706; background: #FBF0E0; }
.m-delete { color: #DC2626; background: #FBE9E9; }
.m-patch { color: #7C3AED; background: #F1EAFE; }
</style>
```

- [ ] **Step 4: StatCard.vue**

```vue
<template>
  <div class="stat-card">
    <div class="stat-label">{{ label }}</div>
    <div class="stat-value" :style="{ color }">{{ value }}</div>
  </div>
</template>
<script>
export default { name: 'StatCard', props: { label: String, value: [String, Number], color: { type: String, default: 'var(--ink-strong)' } } }
</script>
<style scoped>
.stat-card { background: var(--panel); border: 1px solid var(--line); border-radius: var(--radius-card); box-shadow: var(--shadow-panel); padding: 16px 18px; }
.stat-label { font-size: 12px; color: var(--ink-muted); font-family: var(--font-mono); margin-bottom: 6px; }
.stat-value { font-family: var(--font-display); font-size: 26px; font-weight: 700; line-height: 1; }
</style>
```

- [ ] **Step 5: PageHeader.vue 与 EmptyState.vue**

```vue
<!-- PageHeader.vue -->
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
export default { name: 'PageHeader', props: { title: String, description: { type: String, default: '' } } }
</script>
<style scoped>
.page-header { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 20px; gap: 16px; flex-wrap: wrap; }
.page-title { font-size: 22px; font-weight: 700; letter-spacing: -.3px; }
.page-desc { margin-top: 4px; font-size: 13px; color: var(--ink-muted); }
.page-actions { display: flex; gap: 10px; }
</style>
```

```vue
<!-- EmptyState.vue -->
<template>
  <div class="empty-state">
    <div class="empty-mark"></div>
    <p class="empty-title">{{ title }}</p>
    <p v-if="hint" class="empty-hint">{{ hint }}</p>
    <div v-if="$slots.default" class="empty-actions"><slot /></div>
  </div>
</template>
<script>
export default { name: 'EmptyState', props: { title: String, hint: { type: String, default: '' } } }
</script>
<style scoped>
.empty-state { padding: 56px 20px; text-align: center; background: var(--panel); border: 1px dashed var(--line); border-radius: var(--radius-card); }
.empty-mark { width: 34px; height: 34px; margin: 0 auto 12px; border-radius: 8px; background: var(--primary-soft); border: 1px solid var(--primary); opacity: .7; }
.empty-title { font-family: var(--font-display); font-weight: 600; color: var(--ink-strong); }
.empty-hint { margin-top: 4px; font-size: 13px; color: var(--ink-muted); }
.empty-actions { margin-top: 14px; }
</style>
```

- [ ] **Step 6: 验证与提交**

Run: `npm run build`，预期 `✓ built`。

```bash
git add AutoTest_fronted/src/components/ui
git commit -m "feat: 自建基础 UI 组件（Logo/状态图章/方法徽章/统计卡/页头/空状态）"
```

---

### Task 5: 顶部导航与 App 布局重构

**Files:**
- Create: `AutoTest_fronted/src/components/layout/TopNav.vue`
- Modify: `AutoTest_fronted/src/App.vue`（替换侧边栏布局）

- [ ] **Step 1: TopNav.vue**

```vue
<template>
  <header class="topnav">
    <div class="topnav-left">
      <button class="burger" @click="$emit('toggle-menu')" aria-label="菜单">☰</button>
      <Logo @click="$router.push('/agent')" style="cursor:pointer" />
    </div>
    <nav class="topnav-nav">
      <router-link to="/agent" class="nav-item">AI Agent</router-link>
      <el-dropdown trigger="click">
        <span class="nav-item">接口测试 <el-icon><arrow-down /></el-icon></span>
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
        <span class="nav-item">UI 测试 <el-icon><arrow-down /></el-icon></span>
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
    </nav>
    <div class="topnav-right">
      <span class="user-name">{{ currentUser?.displayName || currentUser?.username }}</span>
      <el-button size="small" @click="logout">退出</el-button>
    </div>
  </header>
</template>
<script>
import Logo from '../ui/Logo.vue'
export default {
  name: 'TopNav',
  components: { Logo },
  data() { return { currentUser: JSON.parse(localStorage.getItem('user') || 'null') } },
  methods: {
    async logout() {
      try { await this.$axios.post('/auth/logout') } catch (e) {}
      localStorage.removeItem('token'); localStorage.removeItem('user')
      this.$router.push('/login')
    }
  }
}
</script>
<style scoped>
.topnav { height: 56px; background: var(--panel); border-bottom: 1px solid var(--line);
  display: flex; align-items: center; padding: 0 20px; gap: 24px; position: sticky; top: 0; z-index: 100; }
.topnav-left { display: flex; align-items: center; gap: 12px; }
.burger { display: none; background: none; border: 1px solid var(--line); border-radius: 6px; padding: 4px 8px; cursor: pointer; }
.topnav-nav { display: flex; align-items: center; gap: 6px; flex: 1; }
.nav-item { font-size: 14px; color: var(--ink-body); padding: 6px 12px; border-radius: 6px; cursor: pointer; text-decoration: none; display: inline-flex; align-items: center; gap: 4px; }
.nav-item:hover, .nav-item.router-link-active { color: var(--primary); background: var(--primary-soft); }
.topnav-right { display: flex; align-items: center; gap: 10px; }
.user-name { font-size: 13px; color: var(--ink-muted); }
@media (max-width: 960px) {
  .burger { display: block; }
  .topnav-nav { display: none; }
}
</style>
```

- [ ] **Step 2: 重构 App.vue（保留淡入过渡，去掉侧边栏/页头，登录页不渲染 TopNav）**

```vue
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
.app-shell { min-height: 100vh; }
.app-main { padding: 24px; max-width: 1440px; margin: 0 auto; }
.app-main--full { max-width: none; padding: 0; }
.fade-enter-active, .fade-leave-active { transition: opacity .16s ease; }
.fade-enter-from, .fade-leave-to { opacity: 0; }
</style>
```

> 注意：删除旧 App.vue 的侧边栏/页头逻辑；`isLoginPage` 判断保留；路由跳转/退出逻辑迁到 TopNav。

- [ ] **Step 3: 验证与提交**

Run: `npm run build`，预期 `✓ built`。浏览器手测：登录后顶栏出现，各下拉可达，`/login` 无顶栏。

```bash
git add AutoTest_fronted/src/components/layout AutoTest_fronted/src/App.vue
git commit -m "feat: 顶部导航与全局布局重构"
```

---

### Task 6: 登录页重做

**Files:**
- Modify: `AutoTest_fronted/src/views/Login.vue`（整体替换）

- [ ] **Step 1: 重写 Login.vue（保留登录逻辑）**

```vue
<template>
  <div class="login-page">
    <div class="login-brand">
      <Logo />
      <h1 class="brand-title">自动化测试平台</h1>
      <p class="brand-desc">项目 · 用例 · 执行 · 报告 · AI Agent，一张图纸管到底</p>
    </div>
    <div class="login-card">
      <h2 class="card-title">登录</h2>
      <el-form @submit.prevent="handleLogin">
        <el-form-item><el-input v-model="form.username" placeholder="用户名" size="large" /></el-form-item>
        <el-form-item><el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password @keyup.enter="handleLogin" /></el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">登录</el-button>
      </el-form>
      <el-alert v-if="errorMsg" :title="errorMsg" type="error" :closable="false" style="margin-top:12px" />
    </div>
  </div>
</template>
<script>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import Logo from '../components/ui/Logo.vue'
export default {
  name: 'Login',
  components: { Logo },
  setup() {
    const router = useRouter()
    const form = ref({ username: 'admin', password: '' })
    const loading = ref(false)
    const errorMsg = ref('')
    const handleLogin = async () => {
      if (!form.value.username || !form.value.password) { errorMsg.value = '请输入用户名和密码'; return }
      loading.value = true; errorMsg.value = ''
      try {
        const res = await axios.post('/auth/login', { username: form.value.username, password: form.value.password })
        if (res.data && res.data.code === 1 && res.data.data?.token) {
          localStorage.setItem('token', res.data.data.token)
          localStorage.setItem('user', JSON.stringify(res.data.data.user))
          router.push('/agent')
        } else { errorMsg.value = res.data?.msg || '登录失败' }
      } catch (e) { errorMsg.value = e.response?.data?.msg || '网络错误，请稍后重试' }
      finally { loading.value = false }
    }
    return { form, loading, errorMsg, handleLogin }
  }
}
</script>
<style scoped>
.login-page { min-height: 100vh; display: flex; align-items: center; justify-content: center; gap: 80px; padding: 40px; flex-wrap: wrap; }
.login-brand { max-width: 380px; }
.brand-title { margin-top: 18px; font-size: 30px; letter-spacing: -.5px; }
.brand-desc { margin-top: 10px; color: var(--ink-muted); font-size: 14px; line-height: 1.7; }
.login-card { width: 360px; background: var(--panel); border: 1px solid var(--line); border-radius: 8px; box-shadow: var(--shadow-panel); padding: 32px 30px; }
.card-title { font-size: 20px; margin-bottom: 20px; }
.login-btn { width: 100%; }
</style>
```

- [ ] **Step 2: 验证与提交**

Run: `npm run build`；浏览器手测登录。

```bash
git add AutoTest_fronted/src/views/Login.vue
git commit -m "feat: 登录页重做（蓝图风格）"
```

---

### Task 7: Agent 页重构（布局 + 子组件）

**Files:**
- Create: `AutoTest_fronted/src/components/agent/ConversationList.vue`
- Create: `AutoTest_fronted/src/components/agent/MessageItem.vue`
- Create: `AutoTest_fronted/src/components/agent/ResourcePanel.vue`
- Modify: `AutoTest_fronted/src/views/Agent.vue`（保留全部 SSE/上传/确认逻辑，替换模板结构与样式）

- [ ] **Step 1: ConversationList.vue**

```vue
<template>
  <aside class="conv-list">
    <div class="conv-head">
      <span class="conv-title-label">会话</span>
      <el-button size="small" type="primary" plain @click="$emit('create')">新建</el-button>
    </div>
    <div class="conv-items">
      <div v-for="conv in conversations" :key="conv.id"
           class="conv-item" :class="{ active: conv.id === currentId }" @click="$emit('select', conv.id)">
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
  props: { conversations: Array, currentId: { type: Number, default: null } },
  emits: ['create', 'select', 'remove']
}
</script>
<style scoped>
.conv-list { width: 240px; flex: none; background: var(--panel); border-right: 1px solid var(--line); display: flex; flex-direction: column; }
.conv-head { padding: 14px 14px 10px; display: flex; align-items: center; justify-content: space-between; }
.conv-title-label { font-family: var(--font-mono); font-size: 12px; color: var(--ink-muted); }
.conv-items { flex: 1; overflow-y: auto; padding: 4px 10px 12px; }
.conv-item { display: flex; align-items: center; justify-content: space-between; padding: 9px 10px; margin-bottom: 4px; border-radius: 6px; cursor: pointer; font-size: 13px; color: var(--ink-body); }
.conv-item:hover { background: var(--primary-soft); }
.conv-item.active { background: var(--primary-soft); color: var(--primary); font-weight: 500; box-shadow: inset 3px 0 0 var(--primary); }
.conv-name { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.conv-del { color: var(--ink-muted); padding: 0 3px; }
.conv-del:hover { color: var(--danger); }
.conv-empty { padding: 20px 8px; text-align: center; color: var(--ink-muted); font-size: 12px; }
</style>
```

- [ ] **Step 2: MessageItem.vue（文本/文件/工具卡/确认卡/预览/系统）**

```vue
<template>
  <div class="msg" :class="`msg-${msg.role}`">
    <!-- 文本 -->
    <div v-if="msg.type === 'text'" class="bubble" :class="msg.role === 'user' ? 'bubble-user' : 'bubble-ai'">{{ msg.content }}</div>
    <!-- 文件 -->
    <div v-else-if="msg.type === 'file'" class="file-line mono">📄 {{ msg.content }}</div>
    <!-- 工具调用 -->
    <div v-else-if="msg.type === 'tool_call'" class="tool-card">
      <div class="tool-head">
        <span class="mono">{{ msg.toolName }}</span>
        <StatusStamp :status="msg.status === 'running' ? 'running' : (msg.status === 'success' ? 'passed' : 'failed')" />
      </div>
      <div v-if="msg.argsText" class="tool-body mono">{{ msg.argsText }}</div>
    </div>
    <!-- 确认卡 -->
    <div v-else-if="msg.type === 'confirmation'" class="confirm-card">
      <div class="confirm-title">需要确认 · {{ msg.toolName }}</div>
      <div v-if="msg.payloadText" class="mono confirm-payload">{{ msg.payloadText }}</div>
      <div class="confirm-actions">
        <el-button size="small" type="primary" :disabled="msg.handled" @click="$emit('confirm', msg, 'approved')">批准</el-button>
        <el-button size="small" type="danger" plain :disabled="msg.handled" @click="$emit('confirm', msg, 'rejected')">拒绝</el-button>
      </div>
    </div>
    <!-- 用例预览 -->
    <div v-else-if="msg.type === 'case_preview'" class="preview-card">
      <div class="preview-title">用例草稿（{{ msg.cases.length }} 条）</div>
      <table class="preview-table">
        <thead><tr><th>名称</th><th>方法</th><th>URL</th></tr></thead>
        <tbody>
          <tr v-for="(c, i) in msg.cases" :key="i">
            <td>{{ c.name }}</td>
            <td><MethodBadge :method="c.method" /></td>
            <td class="mono preview-url">{{ c.url }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <!-- 系统 -->
    <div v-else-if="msg.type === 'system'" class="sys-msg">{{ msg.content }}</div>
  </div>
</template>
<script>
import StatusStamp from '../ui/StatusStamp.vue'
import MethodBadge from '../ui/MethodBadge.vue'
export default {
  name: 'MessageItem',
  components: { StatusStamp, MethodBadge },
  props: { msg: { type: Object, required: true } },
  emits: ['confirm']
}
</script>
<style scoped>
.msg { display: flex; margin-bottom: 16px; }
.msg-user { justify-content: flex-end; }
.bubble { max-width: 75%; padding: 10px 14px; font-size: 14px; line-height: 1.6; white-space: pre-wrap; word-break: break-word; border-radius: var(--radius-bubble); }
.bubble-user { background: var(--panel); border: 1px solid var(--line); border-radius: 10px 10px 2px 10px; }
.bubble-ai { background: var(--primary-soft); color: var(--ink-strong); border-radius: 10px 10px 10px 2px; }
.file-line { background: var(--panel); border: 1px solid var(--line); border-left: 3px solid var(--primary); padding: 8px 12px; border-radius: 6px; font-size: 13px; }
.tool-card { max-width: 78%; background: var(--panel); border: 1px solid var(--line); border-left: 3px solid var(--primary); border-radius: 6px; padding: 10px 14px; }
.tool-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.tool-body { margin-top: 6px; font-size: 12px; color: var(--ink-muted); word-break: break-all; }
.confirm-card { max-width: 78%; background: #FFFDF7; border: 1px solid #F3D9A8; border-radius: 8px; padding: 12px 16px; }
.confirm-title { font-weight: 600; color: #9A5B00; margin-bottom: 6px; }
.confirm-payload { font-size: 12px; color: #7A5B2A; word-break: break-all; margin-bottom: 10px; }
.preview-card { max-width: 88%; background: var(--panel); border: 1px solid var(--line); border-left: 3px solid var(--primary); border-radius: 6px; padding: 12px 14px; }
.preview-title { font-weight: 600; margin-bottom: 8px; }
.preview-table { width: 100%; border-collapse: collapse; font-size: 12px; }
.preview-table th, .preview-table td { border: 1px solid var(--line-soft); padding: 6px 8px; text-align: left; word-break: break-all; }
.preview-table th { background: #F8FAFD; color: var(--ink-muted); font-family: var(--font-mono); font-size: 11px; }
.sys-msg { color: var(--danger); font-size: 13px; }
</style>
```

- [ ] **Step 3: ResourcePanel.vue（文件/模板/记忆，预留插槽）**

```vue
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
      <div v-if="memories.length" class="rp-item" v-for="m in memories" :key="m.key">{{ m.key }}</div>
      <p v-else class="rp-empty">暂无记忆</p>
    </div>
  </aside>
</template>
<script>
export default { name: 'ResourcePanel', props: { files: { type: Array, default: () => [] }, templates: { type: Array, default: () => [] }, memories: { type: Array, default: () => [] } } }
</script>
<style scoped>
.resource-panel { width: 280px; flex: none; background: var(--panel); border-left: 1px solid var(--line); padding: 16px; overflow-y: auto; }
.rp-head { font-family: var(--font-display); font-weight: 600; font-size: 15px; margin-bottom: 12px; }
.rp-group { margin-bottom: 20px; }
.rp-group-title { font-size: 11px; color: var(--ink-muted); margin-bottom: 6px; text-transform: uppercase; }
.rp-item { font-size: 12px; color: var(--ink-body); padding: 4px 0; border-bottom: 1px dashed var(--line-soft); word-break: break-all; }
.rp-empty { font-size: 12px; color: var(--ink-muted); }
</style>
```

- [ ] **Step 4: 重构 Agent.vue**

模板骨架（保留 script 中全部逻辑与事件处理，替换 template + 样式）：

```vue
<template>
  <div class="agent-page">
    <ConversationList :conversations="conversations" :current-id="currentId"
      @create="createConversation" @select="switchConversation" @remove="deleteConversation" />
    <div class="chat-area">
      <div class="chat-head">
        <span class="chat-title">{{ currentTitle }}</span>
        <el-button v-if="running" size="small" type="warning" @click="cancelRun">停止</el-button>
      </div>
      <div ref="messageList" class="message-list">
        <div v-if="!currentId" class="chat-empty">
          <p>新建会话，或从左侧选择</p>
          <p class="chat-hint">例：查看项目列表 / 根据上传文档生成用例</p>
        </div>
        <MessageItem v-for="msg in messages" :key="msg.key" :msg="msg" @confirm="respondConfirmation" />
      </div>
      <div class="input-area">
        <input ref="fileInput" type="file" style="display:none" @change="uploadFile" />
        <el-button :disabled="!currentId || running" @click="$refs.fileInput.click()">上传</el-button>
        <el-input v-model="inputText" type="textarea" :rows="2" resize="none" placeholder="输入消息…"
          :disabled="!currentId" @keydown.enter.exact.prevent="sendMessage" />
        <el-button type="primary" :disabled="running || !currentId || !inputText.trim()" @click="sendMessage">发送</el-button>
      </div>
    </div>
    <ResourcePanel :files="files" :templates="templates" :memories="memories" />
  </div>
</template>
<script>
// 保留现有 script：loadConversations/createConversation/switchConversation/deleteConversation/
// sendMessage/openStream/handleData/upsert*/respondConfirmation/cancelRun/closeStream/uploadFile/syncRunState
// 新增：files/templates/memories 数据（页面加载时并行拉取 /files、/templates、/memory），供 ResourcePanel 使用
</script>
<style scoped>
.agent-page { display: flex; height: calc(100vh - 104px); background: var(--canvas); border: 1px solid var(--line); border-radius: var(--radius-card); overflow: hidden; }
.chat-area { flex: 1; min-width: 0; display: flex; flex-direction: column; }
.chat-head { height: 52px; padding: 0 18px; display: flex; align-items: center; justify-content: space-between; background: var(--panel); border-bottom: 1px solid var(--line); }
.chat-title { font-family: var(--font-display); font-weight: 600; font-size: 15px; }
.message-list { flex: 1; overflow-y: auto; padding: 20px; }
.chat-empty { text-align: center; padding-top: 100px; color: var(--ink-muted); }
.chat-hint { margin-top: 6px; font-size: 12px; color: var(--ink-muted); }
.input-area { display: flex; gap: 10px; align-items: flex-end; padding: 12px 16px; background: var(--panel); border-top: 1px solid var(--line); }
</style>
```

> 必须保留的 script 逻辑清单（逐项核对，勿删）：`loadConversations`、`createConversation`、`switchConversation`、`deleteConversation`、`sendMessage`、`openStream`、`handleData`（含 payload 解包）、`syncRunState`、`upsertStreamingText`、`flushStreamingMessage`、`upsertToolCard`、`updateToolCard`、`upsertConfirmation`、`upsertCasePreview`、`respondConfirmation`、`cancelRun`、`closeStream`、`uploadFile`、`pushSystemMessage`、`renderHistoryMessage`、`scrollToBottom`、`truncate`。

- [ ] **Step 5: 验证与提交**

Run: `npm run build`；手测：会话 CRUD、发消息流式输出、上传、确认卡、用例预览、资源面板数据。

```bash
git add AutoTest_fronted/src/components/agent AutoTest_fronted/src/views/Agent.vue
git commit -m "feat: Agent 页重构（蓝图风格三栏 + 消息组件化）"
```

---

### Task 8: 项目管理页换肤

**Files:**
- Modify: `AutoTest_fronted/src/views/Projects.vue`

- [ ] **Step 1: 套用设计系统**

模板结构改为：`PageHeader`（标题"项目管理"，操作区放"新建项目"按钮）+ 项目卡片网格；删除旧 el-table 默认样式，卡片使用 `--panel/--line`；新建/编辑弹窗保留 el-dialog（换肤已生效）。script 逻辑不动。

关键结构：

```vue
<template>
  <div>
    <PageHeader title="项目管理" description="API 测试项目列表">
      <el-button type="primary" @click="openDialog()">新建项目</el-button>
    </PageHeader>
    <div class="project-grid">
      <div v-for="p in projects" :key="p.id" class="project-card" @click="openUseCases(p)">
        <div class="project-name">{{ p.name }}</div>
        <div class="project-meta mono">ID {{ p.id }}</div>
      </div>
      <EmptyState v-if="!projects.length" title="还没有项目" hint="点击右上角新建第一个项目" />
    </div>
    <!-- 保留原 el-dialog 表单 -->
  </div>
</template>
<style scoped>
.project-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 16px; }
.project-card { background: var(--panel); border: 1px solid var(--line); border-radius: var(--radius-card); box-shadow: var(--shadow-panel); padding: 18px; cursor: pointer; transition: transform .15s ease, box-shadow .15s ease; }
.project-card:hover { transform: translateY(-1px); box-shadow: 0 4px 14px rgba(23,35,59,.10); }
.project-name { font-family: var(--font-display); font-weight: 600; font-size: 16px; color: var(--ink-strong); }
.project-meta { margin-top: 8px; font-size: 12px; color: var(--ink-muted); }
</style>
```

> 若原页面已有"查看项目下用例"交互，保留对应跳转；卡片点击行为与旧"查看"一致。

- [ ] **Step 2: 验证与提交**

Run: `npm run build`；手测项目增删改与点击跳转。

```bash
git add AutoTest_fronted/src/views/Projects.vue
git commit -m "style: 项目管理页套用设计系统"
```

---

### Task 9: 用例管理页换肤

**Files:**
- Modify: `AutoTest_fronted/src/views/UseCases.vue`

- [ ] **Step 1: 套用设计系统**

`PageHeader` + 顶部筛选（项目选择、搜索）+ `el-table`（换肤已生效）。表格列调整：

- 名称：正文
- 方法：`<MethodBadge :method="row.method" />`
- URL：`<span class="mono">{{ row.url }}</span>`
- 描述：弱化色
- 操作：编辑/删除/执行按钮（小号，plain）

script 逻辑不动（pid 筛选、CRUD、跳转 API 测试）。

- [ ] **Step 2: 验证与提交**

Run: `npm run build`；手测筛选与 CRUD。

```bash
git add AutoTest_fronted/src/views/UseCases.vue
git commit -m "style: 用例管理页套用设计系统"
```

---

### Task 10: API 测试工作台换肤

**Files:**
- Modify: `AutoTest_fronted/src/views/ApiTest.vue`

- [ ] **Step 1: 套用设计系统**

两栏布局（左请求 / 右响应）保持功能，样式更新：

- 方法选择改为 `MethodBadge` 展示 + 下拉选择
- 左侧面板：白卡 + hairline，tabs 用 Element（换肤生效）
- 右侧响应：状态码大号 Sora（`<div class="resp-status" :class="ok ? 'ok' : 'bad'">`），响应体 `mono` pre 块，断言列表用 `StatusStamp`

```css
.resp-status { font-family: var(--font-display); font-size: 28px; font-weight: 700; }
.resp-status.ok { color: var(--success); } .resp-status.bad { color: var(--danger); }
.resp-body { font-family: var(--font-mono); font-size: 12px; background: #0F172A; color: #E2E8F0; padding: 14px; border-radius: 6px; overflow: auto; white-space: pre-wrap; }
```

- [ ] **Step 2: 验证与提交**

Run: `npm run build`；手测单接口执行。

```bash
git add AutoTest_fronted/src/views/ApiTest.vue
git commit -m "style: API 测试工作台套用设计系统"
```

---

### Task 11: 批量执行页换肤（含报告区）

**Files:**
- Modify: `AutoTest_fronted/src/views/BatchExecute.vue`

- [ ] **Step 1: 套用设计系统**

- 顶部：`PageHeader`（"批量执行"）+ 执行配置区（用例多选、执行次数、并发数 + 开始按钮）
- 结果概览：4 个 `StatCard`（总数 / 通过 / 失败 / 总耗时）
- 结果表格：`el-table` 换肤 + 状态列用 `StatusStamp`、耗时列 `mono`
- 报告查询区（原页面内含）与 AI 分析按钮保持；Allure 导出按钮放页头操作区

- [ ] **Step 2: 验证与提交**

Run: `npm run build`；手测批量执行与报告导出。

```bash
git add AutoTest_fronted/src/views/BatchExecute.vue
git commit -m "style: 批量执行页套用设计系统"
```

---

### Task 12: UI 系列四页换肤

**Files:**
- Modify: `AutoTest_fronted/src/views/UiTest.vue`
- Modify: `AutoTest_fronted/src/views/UiProjects.vue`
- Modify: `AutoTest_fronted/src/views/UiUseCases.vue`
- Modify: `AutoTest_fronted/src/views/UiBatchExecute.vue`

- [ ] **Step 1: 逐页套用**

- `UiProjects`：同 Task 8 卡片式
- `UiUseCases`：同 Task 9 表格（browser/headless 列用 mono；steps 列显示条数）
- `UiTest`：同 Task 10 两栏（步骤编辑区 + 执行结果），步骤动作用 mono 徽章
- `UiBatchExecute`：同 Task 11（StatCard + 结果表格）

script 逻辑全部保留。

- [ ] **Step 2: 验证与提交**

Run: `npm run build`；手测四页路由与基础操作。

```bash
git add AutoTest_fronted/src/views/UiTest.vue AutoTest_fronted/src/views/UiProjects.vue AutoTest_fronted/src/views/UiUseCases.vue AutoTest_fronted/src/views/UiBatchExecute.vue
git commit -m "style: UI 测试四页套用设计系统"
```

---

### Task 13: AI 需求分析页换肤

**Files:**
- Modify: `AutoTest_fronted/src/views/AiRequirement.vue`

- [ ] **Step 1: 套用设计系统**

上传区改为虚线框卡片（`EmptyState` 风格）；进度/流式输出区白卡 + mono 输出；步骤指示用简单分段条（当前段 `--primary`）。script 逻辑与 fetch 调用保留。

- [ ] **Step 2: 验证与提交**

Run: `npm run build`；手测文档上传与流式输出。

```bash
git add AutoTest_fronted/src/views/AiRequirement.vue
git commit -m "style: AI 需求分析页套用设计系统"
```

---

### Task 14: 收尾（空状态/响应式/动效/回归）

**Files:**
- Modify: `AutoTest_fronted/src/styles/global.css`（如需补充）
- Modify: 各页面（如发现问题）

- [ ] **Step 1: 统一空状态与错误态**

为数据列表页统一 `EmptyState`；接口错误提示统一为 `el-message`（换肤后样式一致）。

- [ ] **Step 2: 响应式检查**

浏览器宽度 <960px：TopNav 显示汉堡（当前为占位，如时间允许接入 el-drawer 菜单）；Agent 页资源面板可折叠；表格横向滚动。

- [ ] **Step 3: 动效与焦点检查**

确认运行呼吸点、hover、路由淡入存在；`prefers-reduced-motion` 生效；键盘 Tab 焦点环可见。

- [ ] **Step 4: 全量回归**

Run: `npm run build`；浏览器逐路由手测：登录 → Agent（对话/上传/确认）→ 项目/用例/API 测试/批量/报告/UI 四页/AI 需求分析。后端接口无改动，无需后端回归。

- [ ] **Step 5: 提交**

```bash
git add AutoTest_fronted
git commit -m "chore: 前端重设计收尾（空状态/响应式/动效/回归）"
```

---

## Self-Review 结论

- **Spec 覆盖**：设计文档 §2 Token → Task 2-3；§3 布局 → Task 5；§4.1 登录 → Task 6；§4.2 Agent → Task 7；§4.3 数据页 → Task 8-13；§5 组件策略 → Task 3-4/7；§6 动效 → Task 2/14；§7 阶段 → 与任务顺序一致；§8 验收 → Task 14。
- **占位符**：Task 7 中 Agent.vue script 以"保留清单"方式引用既有代码（非占位——逻辑已存在，不重写）；Task 8-13 指明"script 逻辑不动"，符合不重复粘贴大型既有代码的原则。
- **类型一致性**：组件 props/emits 在 Task 4/7 定义与后续引用一致（`StatusStamp status/label`、`MethodBadge method`、`ConversationList conversations/currentId + create/select/remove`、`MessageItem msg + confirm`、`ResourcePanel files/templates/memories`）。

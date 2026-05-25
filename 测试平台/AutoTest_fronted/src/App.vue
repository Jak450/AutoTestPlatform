<template>
  <div class="app-container">
    <el-container class="main-container">
      <!-- 侧边栏 -->
      <el-aside width="240px" class="sidebar">
        <div class="logo">
          <h2>自动化测试平台</h2>
        </div>
        <el-menu 
          :default-active="activeMenu" 
          class="el-menu-vertical"
          router
          :collapse="false"
          :collapse-transition="false"
          text-color="#ffffff"
        >
          <el-sub-menu index="ai-test">
            <template #title>
              <el-icon><i-ep-cpu /></el-icon>
              <span>AI 智能</span>
            </template>
            <el-menu-item index="/ai-requirement">
              <el-icon><i-ep-document /></el-icon>
              <span>AI需求分析</span>
            </el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="interface-test">
            <template #title>
              <el-icon><i-ep-edit /></el-icon>
              <span>接口测试</span>
            </template>
            <el-menu-item index="/projects">
              <el-icon><i-ep-folder /></el-icon>
              <span>项目管理</span>
            </el-menu-item>
            <el-menu-item index="/use-cases">
              <el-icon><i-ep-document /></el-icon>
              <span>用例管理</span>
            </el-menu-item>
            <el-menu-item index="/api-test">
              <el-icon><i-ep-edit /></el-icon>
              <span>API测试</span>
            </el-menu-item>
            <el-menu-item index="/batch-execute">
              <el-icon><i-ep-sort /></el-icon>
              <span>批量执行</span>
            </el-menu-item>
          </el-sub-menu>
          <el-sub-menu index="ui-test">
            <template #title>
              <el-icon><i-ep-monitor /></el-icon>
              <span>UI测试</span>
            </template>
            <el-menu-item index="/ui-test">
              <el-icon><i-ep-monitor /></el-icon>
              <span>UI测试工作台</span>
            </el-menu-item>
            <el-menu-item index="/ui-projects">
              <el-icon><i-ep-folder /></el-icon>
              <span>UI项目管理</span>
            </el-menu-item>
            <el-menu-item index="/ui-use-cases">
              <el-icon><i-ep-document /></el-icon>
              <span>UI用例管理</span>
            </el-menu-item>
            <el-menu-item index="/ui-batch-execute">
              <el-icon><i-ep-sort /></el-icon>
              <span>UI批量执行</span>
            </el-menu-item>
          </el-sub-menu>
        </el-menu>
      </el-aside>

      <!-- 主内容区 -->
      <el-container>
        <el-header height="60px" class="header">
          <div class="header-title">{{ pageTitle }}</div>
          <div class="header-actions">
            <!-- 刷新按钮已删除 -->
          </div>
        </el-header>
        <el-main class="content">
          <router-view v-slot="{ Component }">
            <transition name="fade" mode="out-in">
              <component :is="Component" />
            </transition>
          </router-view>
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script>
import { ref, computed, watch } from 'vue'
import { useRoute } from 'vue-router'

export default {
  name: 'App',
  setup() {
    const route = useRoute()
    const activeMenu = ref('/projects')
    const pageTitle = ref('项目管理')

    // 监听路由变化
    watch(
      () => route.path,
      (newPath) => {
        activeMenu.value = newPath
        // 根据路由设置页面标题
        const titleMap = {
          '/projects': '项目管理',
          '/use-cases': '用例管理',
          '/api-test': 'API测试',
          '/batch-execute': '批量执行',
          '/ui-test': 'UI测试工作台',
          '/ui-projects': 'UI项目管理',
          '/ui-use-cases': 'UI用例管理',
          '/ui-batch-execute': 'UI批量执行'
        }
        pageTitle.value = titleMap[newPath] || '自动化测试平台'
      },
      { immediate: true }
    )

    return {
      activeMenu,
      pageTitle
    }
  }
}
</script>

<style>
* {
  margin: 0;
  padding: 0;
  box-sizing: border-box;
}

html, body {
  height: 100%;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif;
}

.app-container {
  height: 100vh;
}

.main-container {
  height: 100%;
}

.sidebar {
  background-color: #001529;
  color: #fff;
  overflow: visible !important;
  width: 240px !important;
}

.logo {
  padding: 20px;
  text-align: center;
  border-bottom: 1px solid #1f2937;
}

.logo h2 {
  color: #fff;
  margin: 0;
  font-size: 20px;
}

.el-menu-vertical {
  border-right: none;
  height: calc(100vh - 60px);
  font-weight: 500;
  width: 240px !important;
}

/* 图标样式优化 */
.el-menu-item .el-icon {
  font-size: 18px;
  margin-right: 12px;
  display: inline-block !important;
  opacity: 1 !important;
  transform: none !important;
}

.el-menu-item {
  color: #ffffff !important;
  font-size: 16px;
  padding: 0 30px;
  height: 60px;
  line-height: 60px;
  opacity: 1 !important;
  width: 240px !important;
}

/* 确保菜单文字始终显示 - 更具体的选择器 */
.el-menu-vertical .el-menu-item > span,
.el-menu-vertical .el-menu-item > .el-icon {
  display: inline-flex !important;
  opacity: 1 !important;
  transform: none !important;
  width: auto !important;
  overflow: visible !important;
  visibility: visible !important;
}

/* 确保子菜单标题始终显示清晰可见 */
.el-sub-menu__title,
.el-sub-menu__title > span,
.el-sub-menu__title > .el-icon {
  display: inline-flex !important;
  opacity: 1 !important;
  transform: none !important;
  width: auto !important;
  overflow: visible !important;
  visibility: visible !important;
  color: #ffffff !important;
}

.sidebar .el-sub-menu {
  width: 100%;
  margin: 0 !important;
  border-radius: 0;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.03);
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}

.el-sub-menu__title {
  height: 56px !important;
  line-height: 56px !important;
  padding: 0 26px !important;
  display: flex !important;
  align-items: center;
  justify-content: flex-start;
  gap: 10px;
  font-size: 16px;
  font-weight: 600;
  border-radius: 0;
  color: #ffffff !important;
  transition: background 0.2s ease, box-shadow 0.2s ease;
  background: linear-gradient(90deg, #1577ff 0%, #40a8ff 100%);
  box-shadow: 0 4px 10px rgba(21, 119, 255, 0.3);
}

.el-sub-menu__title .el-icon {
  margin-right: 4px;
}

.el-sub-menu__icon-arrow {
  color: #ffffff !important;
  margin-left: auto;
  right: 0;
}

.el-sub-menu.is-active > .el-sub-menu__title,
.el-sub-menu.is-opened > .el-sub-menu__title {
  filter: brightness(1.05);
  box-shadow: 0 6px 18px rgba(21, 119, 255, 0.45);
}

.el-sub-menu.is-opened > .el-menu {
  background-color: #0f2236 !important;
  margin: 8px 0 12px 0;
  border-radius: 10px;
  padding: 8px 0;
  box-shadow: 0 8px 16px rgba(0, 0, 0, 0.25);
}

.el-sub-menu.is-opened > .el-menu .el-menu-item {
  width: auto !important;
  margin: 0 12px;
  border-radius: 6px;
  padding-left: 24px;
}

.el-sub-menu.is-opened > .el-menu .el-menu-item:hover,
.el-sub-menu.is-opened > .el-menu .el-menu-item.is-active {
  background-color: rgba(24, 144, 255, 0.15) !important;
}

/* 覆盖Element Plus的隐藏逻辑 */
.el-menu--collapse .el-menu-item__content,
.el-menu--collapse .el-menu-item__title,
.el-menu--collapse .el-sub-menu__title > span {
  display: inline-flex !important;
  opacity: 1 !important;
  transform: none !important;
  width: auto !important;
  overflow: visible !important;
}

.el-menu-item:hover {
  background-color: #1890ff;
  color: #fff;
}

.el-menu-item.is-active {
  background-color: #1890ff;
  color: #fff;
}

/* 子菜单项样式优化 */
.el-sub-menu {
  color: #ffffff !important;
}

.header {
  background-color: #fff;
  border-bottom: 1px solid #f0f0f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  box-shadow: 0 1px 4px rgba(0, 21, 41, 0.08);
}

.header-title {
  font-size: 18px;
  font-weight: 500;
  color: #262626;
}

.content {
  background-color: #f5f7fa;
  padding: 20px;
  overflow-y: auto;
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/projects'
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: {
      title: '登录'
    }
  },
  {
    path: '/agent',
    name: 'Agent',
    component: () => import('../views/Agent.vue'),
    meta: {
      title: 'AI Agent'
    }
  },
  {
    path: '/ai-requirement',
    name: 'AiRequirement',
    component: () => import('../views/AiRequirement.vue'),
    meta: {
      title: 'AI需求分析'
    }
  },
  {
    path: '/projects',
    name: 'Projects',
    component: () => import('../views/Projects.vue'),
    meta: {
      title: '项目管理'
    }
  },
  {
    path: '/use-cases',
    name: 'UseCases',
    component: () => import('../views/UseCases.vue'),
    meta: {
      title: '用例管理'
    }
  },
  {
    path: '/api-test',
    name: 'ApiTest',
    component: () => import('../views/ApiTest.vue'),
    meta: {
      title: 'API测试'
    }
  },
  {
    path: '/batch-execute',
    name: 'BatchExecute',
    component: () => import('../views/BatchExecute.vue'),
    meta: {
      title: '批量执行'
    }
  },
  {
    path: '/ui-test',
    name: 'UiTest',
    component: () => import('../views/UiTest.vue'),
    meta: {
      title: 'UI测试工作台'
    }
  },
  {
    path: '/ui-projects',
    name: 'UiProjects',
    component: () => import('../views/UiProjects.vue'),
    meta: {
      title: 'UI项目管理'
    }
  },
  {
    path: '/ui-use-cases',
    name: 'UiUseCases',
    component: () => import('../views/UiUseCases.vue'),
    meta: {
      title: 'UI用例管理'
    }
  },
  {
    path: '/ui-batch-execute',
    name: 'UiBatchExecute',
    component: () => import('../views/UiBatchExecute.vue'),
    meta: {
      title: 'UI批量执行'
    }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由前置守卫
router.beforeEach((to, from, next) => {
  // 设置页面标题
  document.title = to.meta.title ? `${to.meta.title} - 自动化测试平台` : '自动化测试平台'
  // 登录守卫：未登录只能访问登录页
  const token = localStorage.getItem('token')
  if (to.path !== '/login' && !token) {
    next('/login')
  } else if (to.path === '/login' && token) {
    next('/')
  } else {
    next()
  }
})

export default router

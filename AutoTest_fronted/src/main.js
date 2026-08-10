import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import '@fontsource/sora/400.css'
import '@fontsource/sora/600.css'
import '@fontsource/sora/700.css'
import '@fontsource/inter/400.css'
import '@fontsource/inter/500.css'
import '@fontsource/inter/600.css'
import '@fontsource/ibm-plex-mono/400.css'
import '@fontsource/ibm-plex-mono/500.css'
import 'element-plus/dist/index.css'
import './styles/tokens.css'
import './styles/global.css'
import './styles/element-override.css'
import App from './App.vue'
import router from './router'
import axios from 'axios'

// 配置axios
axios.defaults.baseURL = '/api'

// 注意：不要全局强制 Content-Type，否则 FormData 文件上传会被盖成 application/json，
// 导致后端报 "Current request is not a multipart request"。
// axios 对普通对象会自动设置 application/json，对 FormData 会自动设置 multipart 边界。

// 请求拦截器：附加 JWT token
axios.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：401 时清除登录态并跳转登录页
axios.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (router.currentRoute.value.path !== '/login') {
        router.push('/login')
      }
    }
    return Promise.reject(error)
  }
)

// 请求拦截器：附加 token
axios.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：401 跳转登录页
axios.interceptors.response.use(
  response => response,
  error => {
    if (error.response && error.response.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

const app = createApp(App)

app.use(ElementPlus)
app.use(router)

// 全局挂载axios
app.config.globalProperties.$axios = axios

app.mount('#app')

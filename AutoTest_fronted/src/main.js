import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import axios from 'axios'

// 配置axios
axios.defaults.baseURL = '/api'
axios.defaults.headers.post['Content-Type'] = 'application/json'

const app = createApp(App)

app.use(ElementPlus)
app.use(router)

// 全局挂载axios
app.config.globalProperties.$axios = axios

app.mount('#app')
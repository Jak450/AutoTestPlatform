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
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password
                    @keyup.enter="handleLogin" />
        </el-form-item>
        <el-button type="primary" size="large" class="login-btn" :loading="loading" @click="handleLogin">
          登录
        </el-button>
      </el-form>
      <el-alert v-if="errorMsg" :title="errorMsg" type="error" :closable="false" class="login-error" />
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
      if (!form.value.username || !form.value.password) {
        errorMsg.value = '请输入用户名和密码'
        return
      }
      loading.value = true
      errorMsg.value = ''
      try {
        const res = await axios.post('/auth/login', {
          username: form.value.username,
          password: form.value.password
        })
        if (res.data && res.data.code === 1 && res.data.data && res.data.data.token) {
          localStorage.setItem('token', res.data.data.token)
          localStorage.setItem('user', JSON.stringify(res.data.data.user))
          router.push('/agent')
        } else {
          errorMsg.value = (res.data && res.data.msg) || '登录失败'
        }
      } catch (e) {
        errorMsg.value = (e.response && e.response.data && e.response.data.msg) || '网络错误，请稍后重试'
      } finally {
        loading.value = false
      }
    }

    return { form, loading, errorMsg, handleLogin }
  }
}
</script>

<style scoped>
.login-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 80px;
  padding: 40px;
  flex-wrap: wrap;
}

.login-brand {
  max-width: 380px;
}

.brand-title {
  margin-top: 18px;
  font-size: 30px;
  letter-spacing: -0.5px;
}

.brand-desc {
  margin-top: 10px;
  color: var(--ink-muted);
  font-size: 14px;
  line-height: 1.7;
}

.login-card {
  width: 360px;
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: var(--shadow-panel);
  padding: 32px 30px;
}

.card-title {
  font-size: 20px;
  margin-bottom: 20px;
}

.login-btn {
  width: 100%;
}

.login-error {
  margin-top: 12px;
}
</style>

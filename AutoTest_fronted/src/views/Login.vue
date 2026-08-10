<template>
  <div class="login-page">
    <div class="login-card">
      <h2 class="login-title">自动化测试平台</h2>
      <p class="login-subtitle">请登录后使用 Agent 与测试功能</p>
      <el-form :model="form" @submit.prevent="handleLogin">
        <el-form-item>
          <el-input v-model="form.username" placeholder="用户名" size="large" />
        </el-form-item>
        <el-form-item>
          <el-input v-model="form.password" type="password" placeholder="密码" size="large" show-password
                    @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" size="large" class="login-button" :loading="loading" @click="handleLogin">
            登 录
          </el-button>
        </el-form-item>
      </el-form>
      <el-alert v-if="errorMsg" :title="errorMsg" type="error" :closable="false" class="login-error" />
    </div>
  </div>
</template>

<script>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'

export default {
  name: 'Login',
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
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f2236 0%, #1577ff 100%);
}

.login-card {
  width: 400px;
  padding: 40px 36px 32px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.25);
}

.login-title {
  margin: 0 0 8px;
  text-align: center;
  color: #1f2d3d;
  font-size: 24px;
}

.login-subtitle {
  margin: 0 0 28px;
  text-align: center;
  color: #8c939d;
  font-size: 13px;
}

.login-button {
  width: 100%;
}

.login-error {
  margin-top: 12px;
}
</style>

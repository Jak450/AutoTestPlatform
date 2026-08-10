<template>
  <div class="login-page">
    <div class="login-card">
      <h1 class="login-title">自动化测试平台</h1>
      <p class="login-subtitle">AutoTestPlatform Agent</p>
      <el-form :model="form" :rules="rules" ref="formRef" size="large">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="UserIcon" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password :prefix-icon="LockIcon" @keyup.enter="handleLogin" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">
            登录
          </el-button>
        </el-form-item>
      </el-form>
      <div class="login-tip">默认账号：admin / 12345678</div>
    </div>
  </div>
</template>

<script>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import axios from 'axios'
import { ElMessage } from 'element-plus'
import { User as UserIcon, Lock as LockIcon } from '@element-plus/icons-vue'

export default {
  name: 'Login',
  setup() {
    const router = useRouter()
    const formRef = ref(null)
    const loading = ref(false)
    const form = reactive({
      username: '',
      password: ''
    })
    const rules = {
      username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
      password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
    }

    const handleLogin = () => {
      formRef.value.validate(async (valid) => {
        if (!valid) return
        loading.value = true
        try {
          const res = await axios.post('/auth/login', {
            username: form.username,
            password: form.password
          })
          const data = res.data
          if (data.code !== 1 && data.code !== 0) {
            ElMessage.error(data.msg || '登录失败')
            return
          }
          if (!data.data || !data.data.token) {
            ElMessage.error(data.msg || '登录失败')
            return
          }
          localStorage.setItem('token', data.data.token)
          localStorage.setItem('user', JSON.stringify(data.data.user))
          ElMessage.success('登录成功')
          router.push('/')
        } catch (error) {
          ElMessage.error(error.response?.data?.msg || '登录失败')
        } finally {
          loading.value = false
        }
      })
    }

    return {
      form,
      formRef,
      rules,
      loading,
      handleLogin
    }
  }
}
</script>

<style scoped>
.login-page {
  height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1577ff 0%, #40a8ff 100%);
}

.login-card {
  width: 380px;
  padding: 40px 36px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 40px rgba(0, 21, 41, 0.3);
}

.login-title {
  text-align: center;
  margin: 0 0 4px 0;
  font-size: 24px;
  color: #262626;
}

.login-subtitle {
  text-align: center;
  margin: 0 0 28px 0;
  color: #8c8c8c;
  font-size: 14px;
}

.login-btn {
  width: 100%;
}

.login-tip {
  text-align: center;
  margin-top: 12px;
  color: #bfbfbf;
  font-size: 12px;
}
</style>

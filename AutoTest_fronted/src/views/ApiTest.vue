<template>
  <div class="api-test-container">
    <el-card class="api-test-card">
      <template #header>
        <div class="card-header">
          <span>API测试</span>
        </div>
      </template>

      <el-form :model="apiForm" ref="apiFormRef">
        <el-form-item label="请求方法">
          <el-select v-model="apiForm.method" style="width: 120px;">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
            <el-option label="PATCH" value="PATCH" />
          </el-select>
        </el-form-item>
        
        <el-form-item label="请求URL">
          <el-input v-model="apiForm.url" placeholder="请输入完整的请求URL" style="width: 100%;" />
        </el-form-item>

        <el-form-item label="请求头">
          <el-input 
            v-model="apiForm.header" 
            type="textarea" 
            :rows="4" 
            placeholder="JSON格式请求头，例如：{'Content-Type': 'application/json'}"
          />
          <el-button type="text" size="small" @click="formatJson(apiForm, 'header')">格式化</el-button>
        </el-form-item>

        <el-form-item label="请求体">
          <el-input 
            v-model="apiForm.param" 
            type="textarea" 
            :rows="8" 
            placeholder="JSON格式请求体"
          />
          <el-button type="text" size="small" @click="formatJson(apiForm, 'param')">格式化</el-button>
        </el-form-item>

        <el-form-item label="断言">
          <el-input 
            v-model="apiForm.assertStr" 
            type="textarea" 
            :rows="4" 
            placeholder="JSON格式断言，例如：{'status': 200}"
          />
          <el-button type="text" size="small" @click="formatJson(apiForm, 'assertStr')">格式化</el-button>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" size="large" @click="executeApi" :loading="loading">
            <el-icon><i-ep-run /></el-icon>
            执行请求
          </el-button>
          <el-button size="large" @click="clearForm">
            <el-icon><i-ep-delete /></el-icon>
            清空
          </el-button>
        </el-form-item>
        
        <!-- 进度条 -->
        <el-progress
          v-if="showProgress"
          :percentage="progress"
          :stroke-width="20"
          :text-inside="true"
          :status="progress === 100 ? 'success' : 'primary'"
          class="gradient-progress"
          style="margin-top: 10px;"
        />
      </el-form>
    </el-card>

    <!-- 执行结果 -->
    <el-card class="result-card" v-if="resultVisible">
      <template #header>
        <div class="card-header">
          <span>执行结果</span>
          <el-tag :type="resultType">
            {{ executionStatus }}
          </el-tag>
        </div>
      </template>

      <el-tabs>
        <el-tab-pane label="请求信息">
          <div class="result-section">
            <div class="result-item">
              <span class="label">请求方法：</span>
              <span class="value">{{ apiForm.method }}</span>
            </div>
            <div class="result-item">
              <span class="label">请求URL：</span>
              <span class="value">{{ apiForm.url }}</span>
            </div>
            <div class="result-item">
              <span class="label">请求头：</span>
            </div>
            <div class="code-content" style="margin: 10px 0;">
              <pre>{{ apiForm.header || '{}' }}</pre>
            </div>
            <div class="result-item">
              <span class="label">请求体：</span>
            </div>
            <div class="code-content" style="margin: 10px 0;">
              <pre>{{ apiForm.param || '{}' }}</pre>
            </div>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="响应信息">
          <div class="result-section">
            <div class="result-item">
              <span class="label">响应状态码：</span>
              <span class="value">{{ responseInfo.status }}</span>
            </div>
            <div class="result-item">
              <span class="label">HTTP状态：</span>
              <span class="value">{{ getHttpStatusText(responseInfo.status) }}</span>
            </div>
            <div class="result-item">
              <span class="label">响应时间：</span>
              <span class="value">{{ responseInfo.time }} ms</span>
            </div>
            <div class="result-item">
              <span class="label">响应大小：</span>
              <span class="value">{{ responseInfo.size }} bytes</span>
            </div>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="响应头">
          <div class="code-content">
            <pre>{{ formattedHeaders }}</pre>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="响应体">
          <div class="code-content">
            <pre>{{ formattedResponse }}</pre>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="断言结果">
          <div class="assert-result" v-if="assertResults.length > 0">
            <el-table :data="assertResults" stripe style="width: 100%">
              <el-table-column prop="field" label="字段" />
              <el-table-column prop="expected" label="期望值" />
              <el-table-column prop="actual" label="实际值" />
              <el-table-column prop="result" label="结果">
                <template #default="{ row }">
                  <el-tag :type="row.result ? 'success' : 'danger'">
                    {{ row.result ? '通过' : '失败' }}
                  </el-tag>
                </template>
              </el-table-column>
            </el-table>
          </div>
          <div v-else class="no-assert">
            未设置断言
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-card>
  </div>
</template>

<script>
import { ref, reactive } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

export default {
  name: 'ApiTest',
  setup() {
    const apiForm = reactive({
      method: 'GET',
      url: '',
      header: '',
      param: '',
      assertStr: ''
    })
    const apiFormRef = ref(null)
    const loading = ref(false)
    const resultVisible = ref(false)
    const executionStatus = ref('')
    const resultType = ref('')
    const responseInfo = reactive({
      status: '',
      time: '',
      size: ''
    })
    const formattedHeaders = ref('')
    const formattedResponse = ref('')
    const assertResults = ref([])
    
    // 进度条相关状态
    // 进度条相关状态
      const progress = ref(0)
      const showProgress = ref(false)

    // 格式化JSON
    const formatJson = (form, field) => {
      try {
        if (form[field] && form[field].trim()) {
          const parsed = JSON.parse(form[field])
          form[field] = JSON.stringify(parsed, null, 2)
        }
      } catch (error) {
        ElMessage.error('JSON格式不正确')
      }
    }

    // 执行API
    const executeApi = async () => {
      if (!apiForm.url.trim()) {
        ElMessage.warning('请输入请求URL')
        return
      }

      loading.value = true
      // 初始化进度条
      clearTimeout(window.progressTimeout)
      if (window.progressIntervalTimers) {
        window.progressIntervalTimers.forEach(timer => clearTimeout(timer))
      }
      window.progressIntervalTimers = []
      progress.value = 0
      showProgress.value = true
      
      // 启动模拟进度更新（因为单个API请求是异步的，我们无法精确知道进度）
      const updateProgress = () => {
        if (progress.value < 80) { // 最大到80%，留20%给实际响应
          const increment = Math.floor(Math.random() * 5) + 1
          const newProgress = Math.min(progress.value + increment, 80)
          
          // 平滑过渡
          const startProgress = progress.value
          const diff = newProgress - startProgress
          const steps = 3
          const stepValue = diff / steps
          
          for (let i = 1; i <= steps; i++) {
            const timer = setTimeout(() => {
              if (progress.value < 80) { // 确保不会超过80%
                progress.value = Math.round(startProgress + stepValue * i)
              }
            }, i * 50)
            window.progressIntervalTimers.push(timer)
          }
          
          // 继续更新进度
          if (progress.value < 80) {
            window.progressTimeout = setTimeout(updateProgress, 300)
          }
        }
      }
      
      updateProgress() // 开始模拟进度更新
      
      try {
        // 验证JSON格式
        const validateJson = (field, value) => {
          if (value && value.trim()) {
            try {
              JSON.parse(value)
            } catch (e) {
              throw new Error(`${field} 必须是有效的JSON格式`)
            }
          }
        }

        validateJson('请求头', apiForm.header)
        validateJson('请求体', apiForm.param)
        validateJson('断言', apiForm.assertStr)

        const start = Date.now()
        const response = await axios.post('/test', {
          method: apiForm.method,
          url: apiForm.url,
          header: apiForm.header || '{}',
          param: apiForm.param || '{}',
          assertStr: apiForm.assertStr || '{}'
        })
        const end = Date.now()

        const result = response.data
        responseInfo.time = end - start
        responseInfo.size = JSON.stringify(result.result).length
        responseInfo.status = result.status

        // 设置状态标签
        if (result.status >= 200 && result.status < 300) {
          executionStatus.value = '成功'
          resultType.value = 'success'
        } else {
          executionStatus.value = '失败'
          resultType.value = 'danger'
        }

        // 格式化响应数据
        formattedResponse.value = JSON.stringify(result.result, null, 2)
        formattedHeaders.value = JSON.stringify(result.headers, null, 2)

        // 处理断言结果 - 支持两种格式：assertResults数组和assertResult对象
        assertResults.value = []
        if (result.assertResults && Array.isArray(result.assertResults)) {
          // 处理新格式：assertResults数组
          result.assertResults.forEach(assertItem => {
            assertResults.value.push({
              field: assertItem.field,
              expected: assertItem.expected,
              actual: assertItem.actual,
              result: assertItem.result
            })
          })
        } else if (result.assertResult) {
          // 兼容旧格式：assertResult对象
          Object.keys(result.assertResult).forEach(key => {
            const assertItem = result.assertResult[key]
            assertResults.value.push({
              field: key,
              expected: assertItem.expected,
              actual: assertItem.actual,
              result: assertItem.result
            })
          })
        }

        resultVisible.value = true
      } catch (error) {
        console.error('执行API失败:', error)
        ElMessage.error(error.message || '执行API失败')
      } finally {
        loading.value = false
        
        // 完成进度条并设置延迟隐藏
        const startProgress = progress.value
        const diff = 100 - startProgress
        const steps = 5
        const stepValue = diff / steps
        
        for (let i = 1; i <= steps; i++) {
          const timer = setTimeout(() => {
            progress.value = Math.round(startProgress + stepValue * i)
          }, i * 30)
          window.progressIntervalTimers.push(timer)
        }
        
        // 延迟1秒后隐藏进度条并重置
        window.progressTimeout = setTimeout(() => {
          showProgress.value = false
          progress.value = 0
        }, 1000)
      }
    }

    // 获取HTTP状态文本
    const getHttpStatusText = (status) => {
      const statusMap = {
        200: 'OK',
        201: 'Created',
        202: 'Accepted',
        204: 'No Content',
        301: 'Moved Permanently',
        302: 'Found',
        304: 'Not Modified',
        400: 'Bad Request',
        401: 'Unauthorized',
        403: 'Forbidden',
        404: 'Not Found',
        405: 'Method Not Allowed',
        500: 'Internal Server Error',
        502: 'Bad Gateway',
        503: 'Service Unavailable',
        504: 'Gateway Timeout'
      }
      return statusMap[status] || 'Unknown Status'
    }

    // 清空表单
    const clearForm = () => {
      Object.keys(apiForm).forEach(key => {
        apiForm[key] = key === 'method' ? 'GET' : ''
      })
      resultVisible.value = false
      assertResults.value = []
    }

    return {
      apiForm,
      apiFormRef,
      loading,
      resultVisible,
      executionStatus,
      resultType,
      responseInfo,
      formattedHeaders,
      formattedResponse,
      assertResults,
      progress,
      showProgress,
      formatJson,
      executeApi,
      clearForm,
      getHttpStatusText
    }
  }
}
</script>

<style scoped>
.api-test-container {
  padding: 20px;
}

.api-test-card,
.result-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.code-content {
  max-height: 500px;
  overflow-y: auto;
  background-color: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
}

pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 14px;
}

.result-section {
  padding: 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.result-item {
  margin-bottom: 8px;
}

.result-item:last-child {
  margin-bottom: 0;
}

.label {
  font-weight: 500;
  color: #606266;
  margin-right: 10px;
}

.value {
  color: #303133;
}

.assert-result {
  max-height: 400px;
  overflow-y: auto;
}

.no-assert {
  text-align: center;
  color: #909399;
  padding: 20px;
}
    /* 渐变色进度条样式 */
    .gradient-progress {
      .el-progress-bar__inner {
        background: linear-gradient(90deg, #67c23a 0%, #409eff 100%);
        transition: width 0.5s ease;
      }
      
      .el-progress-bar__innerText {
        color: #fff;
        font-weight: bold;
      }
    }
  
</style>
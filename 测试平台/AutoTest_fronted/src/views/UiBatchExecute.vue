<template>
  <div class="ui-batch-execute-container">
    <el-card class="ui-batch-card">
      <template #header>
        <div class="card-header">
          <span>UI批量执行</span>
        </div>
      </template>

      <div class="filter-section">
        <el-select v-model="selectedProject" placeholder="选择项目" style="width: 200px; margin-right: 10px;" @change="loadUseCases">
          <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
        </el-select>
        
        <div style="display: inline-block; margin-right: 10px;">
          <span style="margin-right: 5px;">执行次数:</span>
          <el-input-number v-model="executionCount" :min="1" :max="100" :step="1" size="small" style="width: 120px;" />
        </div>
        
        <div style="display: inline-block; margin-right: 10px;">
          <span style="margin-right: 5px;">并发数:</span>
          <el-input-number v-model="maxConcurrency" :min="1" :max="20" :step="1" size="small" style="width: 120px;" />
        </div>

        <!-- 执行按钮组 -->
        <div class="execute-buttons">
          <el-button type="success" @click="batchExecute" :disabled="selectedUseCases.length === 0 || loading">
            <el-icon><i-ep-refresh /></el-icon>
            批量执行 (已选择 {{ selectedUseCases.length }} 个，执行 {{ executionCount }} 次)
          </el-button>
        </div>
      </div>
      
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

      <el-table 
        :data="useCases" 
        stripe 
        style="width: 100%; margin-top: 10px;"
        @selection-change="handleSelectionChange"
      >
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="用例名称" />
        <el-table-column prop="url" label="目标URL" width="200" show-overflow-tooltip />
        <el-table-column prop="browser" label="浏览器" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.browser }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewport" label="窗口大小" width="120" />
        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="runSingle(row)">
              执行
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 执行结果 -->
    <el-card class="result-card" v-if="batchResultVisible">
      <template #header>
        <div class="card-header">
          <span>执行结果</span>
          <div class="result-stats">
            <el-tag type="primary">总数: {{ batchResults.total }}</el-tag>
            <el-tag type="success">成功: {{ batchResults.success }}</el-tag>
            <el-tag type="danger">失败: {{ batchResults.failed }}</el-tag>
            <el-tag type="info">总耗时: {{ batchResults.totalTime }} ms</el-tag>
          </div>
        </div>
      </template>

      <div class="result-hint" v-if="executionDetails.length > 0">
        <el-alert
          title="提示"
          type="info"
          :closable="false"
          description="点击“查看”按钮可以查看每个用例的执行详情"
          style="margin-bottom: 10px;"
        />
      </div>

      <el-table v-if="executionDetails.length" :data="executionDetails" stripe style="width: 100%">
        <el-table-column prop="name" label="用例名称" />
        <el-table-column prop="url" label="目标URL" width="200" show-overflow-tooltip />
        <el-table-column prop="browser" label="浏览器" width="100" />
        <el-table-column prop="status" label="执行状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'success' ? 'success' : 'danger'">
              {{ row.status === 'success' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="耗时(ms)" width="100" />
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewDetail(row)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-else
        description="暂无执行结果"
        :image-size="120"
        class="executions-empty"
      />
    </el-card>
    


    <!-- 详细结果对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="执行详情"
      width="800px"
    >
      <el-tabs>
        <el-tab-pane label="基本信息">
          <div class="detail-section">
            <div class="detail-item">
              <span class="label">用例名称：</span>
              <span class="value">{{ currentDetail.name }}</span>
            </div>
            <div class="detail-item" v-if="currentDetail.description">
              <span class="label">描述：</span>
              <span class="value">{{ currentDetail.description }}</span>
            </div>
            <div class="detail-item">
              <span class="label">目标URL：</span>
              <span class="value">{{ currentDetail.url }}</span>
            </div>
            <div class="detail-item">
              <span class="label">浏览器：</span>
              <span class="value">{{ currentDetail.browser || 'N/A' }}</span>
            </div>
            <div class="detail-item">
              <span class="label">窗口大小：</span>
              <span class="value">{{ currentDetail.viewport || 'N/A' }}</span>
            </div>
            <div class="detail-item">
              <span class="label">运行模式：</span>
              <span class="value">{{ currentDetail.headless ? '无头' : '有头' }}</span>
            </div>
            <div class="detail-item">
              <span class="label">执行状态：</span>
              <span class="value">
                <el-tag :type="currentDetail.status === 'success' ? 'success' : 'danger'">
                  {{ currentDetail.status === 'success' ? '成功' : '失败' }}
                </el-tag>
              </span>
            </div>
            <div class="detail-item">
              <span class="label">执行时间：</span>
              <span class="value">{{ currentDetail.duration }} ms</span>
            </div>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="执行结果">
          <div class="code-content" v-if="currentDetail.result">
            <pre>{{ formatResult(currentDetail.result) }}</pre>
          </div>
          <div v-else class="no-result">
            无执行结果
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="错误信息" v-if="currentDetail.error">
          <div class="code-content error-content">
            <pre>{{ currentDetail.error }}</pre>
          </div>
        </el-tab-pane>
      </el-tabs>
    </el-dialog>
  </div>
</template>

<script>
import { ref, reactive, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

export default {
  name: 'UiBatchExecute',
  setup() {
    const projects = ref([])
    const useCases = ref([])
    const selectedProject = ref('')
    const selectedUseCases = ref([])
    const loading = ref(false)
    const batchResultVisible = ref(false)
    const detailVisible = ref(false)
    const currentDetail = reactive({})
    const executionCount = ref(1) // 执行次数，默认为1
    const maxConcurrency = ref(5) // 最大并发数，默认为5
    const batchResults = reactive({
      total: 0,
      success: 0,
      failed: 0,
      totalTime: 0
    })
    const executionDetails = ref([])
    // 进度条相关状态
    const progress = ref(0)
    const showProgress = ref(false)
    

    
    // 获取项目列表
    const fetchProjects = async () => {
      try {
        const response = await axios.get('/ui-projects')
        projects.value = response.data
        if (projects.value.length > 0 && !selectedProject.value) {
          selectedProject.value = projects.value[0].id
          loadUseCases()
        }
      } catch (error) {
        console.error('获取UI项目列表失败:', error)
        ElMessage.error('获取UI项目列表失败')
      }
    }

    // 加载用例
    const loadUseCases = async () => {
      if (!selectedProject.value) {
        ElMessage.warning('请选择项目')
        return
      }
      try {
        // 先清空数组，确保视图更新
        useCases.value = []
        const response = await axios.get(`/ui-use-cases?pid=${selectedProject.value}`)
        // 使用新数组替换，确保Vue能检测到变化
        useCases.value = [...response.data]
        selectedUseCases.value = []
      } catch (error) {
        console.error('加载UI用例失败:', error)
        ElMessage.error('加载UI用例失败')
      }
    }

    // 处理选择变化
    const handleSelectionChange = (selection) => {
      selectedUseCases.value = selection
    }

    // 单个执行
    const runSingle = async (row) => {
      try {
        loading.value = true
        const response = await axios.post('/ui-test/run', row)
        const result = response.data
        
        // 显示详情
        Object.assign(currentDetail, {
          ...row,
          status: result.code === 1 ? 'success' : 'failed',
          duration: result.data?.duration || 0,
          result: result.data || {},
          error: result.msg || ''
        })
        
        detailVisible.value = true
      } catch (error) {
        console.error('执行UI用例失败:', error)
        ElMessage.error('执行UI用例失败')
      } finally {
        loading.value = false
        // 隐藏进度条
        showProgress.value = false
        progress.value = 0
    }
    }

    // 批量执行
    const batchExecute = async () => {
      if (selectedUseCases.value.length === 0) {
        ElMessage.warning('请至少选择一个用例')
        return
      }
      
      loading.value = true
      executionDetails.value = []
      // 强制重置并显示进度条
      progress.value = 0
      showProgress.value = true
      
      try {
        // 获取幂等性Token
        const tokenRes = await axios.get('/idempotent/token')
        const idempotentToken = tokenRes.data.data
        
        // 构建请求参数
        const requestData = {
          useCaseIds: selectedUseCases.value.map(useCase => useCase.id),
          executionCount: executionCount.value,
          maxConcurrency: maxConcurrency.value
        }
        
        // 调用后端批量执行接口
        const response = await axios.post('/ui-batch-test', requestData, {
          headers: { 'Idempotent-Token': idempotentToken }
        })
        const result = response.data
        
        console.log('批量执行响应:', result)
        
        // 更新统计结果
        batchResults.total = result.total
        batchResults.success = result.success
        batchResults.failed = result.failed
        batchResults.totalTime = result.totalTime
        
        // 处理执行详情
        executionDetails.value = result.details.map(detail => {
          // 根据执行次数决定是否显示次数信息
          let displayName = detail.useCaseName;
          if (executionCount.value > 1) {
            displayName = `${detail.useCaseName} (第${detail.executionIndex}次)`;
          }
          
          return {
            id: detail.useCaseId,
            name: displayName,
            url: selectedUseCases.value.find(useCase => useCase.id === detail.useCaseId)?.url || '',
            browser: selectedUseCases.value.find(useCase => useCase.id === detail.useCaseId)?.browser || '',
            status: detail.success ? 'success' : 'failed',
            duration: detail.duration || 0,
            rawResult: detail.result,
            error: detail.result?.error || ''
          }
        })
        
        console.log('执行结果详情:', executionDetails.value)
        console.log('执行结果统计:', batchResults)
        
        // 强制显示结果
        batchResultVisible.value = true
        
        // 确保DOM更新
        setTimeout(() => {
          batchResultVisible.value = true
        }, 0)
        

        
        ElMessage.success(`批量执行完成！共执行 ${selectedUseCases.value.length} 个用例，每个用例执行 ${executionCount.value} 次，总计 ${batchResults.total} 次执行`)
      } catch (error) {
        console.error('批量执行失败:', error)
        ElMessage.error('批量执行失败')
      } finally {
        loading.value = false
        // 隐藏进度条
        setTimeout(() => {
          showProgress.value = false
          progress.value = 0
        }, 1000)
      }
    }

    // 格式化执行结果
    const formatResult = (result) => {
      if (result === null || result === undefined) {
        return 'null';
      }
      if (typeof result === 'string') {
        return result;
      }
      // 对于对象或数组，直接格式化
      return JSON.stringify(result, null, 2);
    }

    // 查看详情
    const viewDetail = (row) => {
      Object.assign(currentDetail, {
        ...row,
        // 后端返回的rawResult已经是一个包含result和error的对象
        result: row.rawResult || {},
        error: row.error || row.rawResult?.error || ''
      })
      detailVisible.value = true
    }



    // 初始化
    onMounted(() => {
      fetchProjects()
    })

    return {
        projects,
        useCases,
        selectedProject,
        selectedUseCases,
        loading,
        batchResultVisible,
        detailVisible,
        currentDetail,
        batchResults,
        executionDetails,
        executionCount,
        maxConcurrency,
        progress,
        showProgress,
        // 方法
        handleSelectionChange,
        loadUseCases,
        runSingle,
        batchExecute,
        viewDetail,
        formatResult
      }
  }
}
</script>

<style scoped>
.ui-batch-execute-container {
  padding: 20px;
}

.ui-batch-card,
.result-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.filter-section {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 15px;
  margin-bottom: 10px;
}

.execute-buttons {
  display: flex;
  gap: 10px;
}

.result-stats {
  display: flex;
  gap: 10px;
}

/* 渐变色进度条样式 */
.gradient-progress >>> .el-progress-bar__inner {
  background: linear-gradient(90deg, #409eff, #67c23a);
  transition: width 0.5s ease;
}

/* 进度条文字颜色 */
.gradient-progress >>> .el-progress-bar__innerText {
  color: white;
  font-weight: bold;
}

.code-content {
  max-height: 400px;
  overflow-y: auto;
  background-color: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
}

.code-content.error-content {
  background-color: #fef0f0;
  color: #f56c6c;
}

pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
  font-size: 14px;
}

.detail-section {
  padding: 10px;
  background-color: #f5f7fa;
  border-radius: 4px;
}

.detail-item {
  margin-bottom: 10px;
}

.detail-item:last-child {
  margin-bottom: 0;
}

.label {
  font-weight: 500;
  color: #606266;
  margin-right: 10px;
  display: inline-block;
  width: 120px;
}

.value {
  color: #303133;
}

.no-result,
.no-assert {
  text-align: center;
  color: #909399;
  padding: 20px;
}


</style>
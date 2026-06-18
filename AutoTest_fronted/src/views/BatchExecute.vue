<template>
  <div class="batch-execute-container">
    <el-card class="batch-card">
      <template #header>
        <div class="card-header">
          <span>批量执行</span>
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
          <span style="margin-right: 5px;">最大并发数:</span>
          <el-input-number v-model="maxConcurrency" :min="1" :max="50" :step="1" size="small" style="width: 120px;" />
        </div>

        <!-- 执行按钮组 -->
        <div class="execute-buttons">
          <el-button type="success" @click="batchExecute" :disabled="selectedUseCases.length === 0 || loading">
            <el-icon><i-ep-refresh /></el-icon>
            批量并发执行 (已选择 {{ selectedUseCases.length }} 个，执行 {{ executionCount }} 次)
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
        <el-table-column prop="url" label="URL" width="300" />
        <el-table-column prop="method" label="请求方法" width="100">
          <template #default="{ row }">
            <el-tag :type="getMethodTagType(row.method)">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" />
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


      
      <el-table :data="executionDetails" stripe style="width: 100%">
        <el-table-column prop="name" label="用例名称" />
        <el-table-column prop="url" label="URL" width="300" />
        <el-table-column prop="status" label="状态码" width="100" />
        <el-table-column prop="time" label="耗时(ms)" width="100" />
        <el-table-column prop="result" label="执行结果" width="100">
          <template #default="{ row }">
            <el-tag :type="row.result === 'success' ? 'success' : 'danger'">
              {{ row.result === 'success' ? '成功' : '失败' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="assertResult" label="断言结果" width="100">
          <template #default="{ row }">
            <el-tag :type="row.assertResult === 'passed' ? 'success' : row.assertResult === 'failed' ? 'danger' : 'info'">
              {{ getAssertResultText(row.assertResult) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewDetail(row)">
              查看
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
    
    <!-- 测试报告查询与导出区域 -->
    <div class="report-section" style="margin-top: 20px; background-color: #f5f7fa; border-radius: 6px; padding: 15px;">
      <h3 style="margin-bottom: 15px; font-weight: 500;">测试报告管理</h3>
      <el-row :gutter="20">
        <el-col :span="8">
          <el-date-picker
                v-model="timeRange"
                type="daterange"
                range-separator="至"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                style="width: 100%"
                value-format="YYYY-MM-DD HH:mm:ss"
                format="YYYY-MM-DD HH:mm:ss"
              />
        </el-col>
        <el-col :span="4">
          <el-input
            v-model="moduleName"
            placeholder="模块名称"
          />
        </el-col>
        <el-col :span="4">
          <el-input
            v-model="caseNameKeyword"
            placeholder="用例名称关键字"
          />
        </el-col>
        <el-col :span="4">
          <el-select v-model="statusFilter" placeholder="状态筛选">
            <el-option label="全部" value="" />
            <el-option label="成功" value="passed" />
            <el-option label="失败" value="failed" />
            <el-option label="跳过" value="broken" />
          </el-select>
        </el-col>
        <el-col :span="4">
              <div style="display: flex; gap: 10px; align-items: center; height: 100%;">
                <el-button 
                  type="primary" 
                  @click="queryTestCases"
                  :loading="reportLoading"
                  style="flex: 1;"
                >
                  查询记录
                </el-button>
                <el-button 
                  type="primary" 
                  @click="exportTestReport"
                  :loading="reportLoading"
                  style="flex: 1;"
                >
                  导出Allure报告
                </el-button>
              </div>
            </el-col>
      </el-row>
      
      <!-- 报告查询结果展示 -->
      <el-table 
        v-if="reportData.length > 0"
        :data="reportData" 
        style="margin-top: 15px; width: 100%"
        border
        stripe
      >
        <el-table-column prop="moduleName" label="模块名称" width="180" />
        <el-table-column prop="caseName" label="用例名称" min-width="200" />
        <el-table-column prop="status" label="执行状态" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.status === 'success' ? 'success' : scope.row.status === 'fail' ? 'danger' : 'warning'">
              {{ scope.row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="AI分析" width="90">
          <template #default="scope">
            <el-button type="warning" size="small" @click="aiAnalyze(scope.row)" :loading="aiLoadingId === scope.row.id">
              AI分析
            </el-button>
          </template>
        </el-table-column>
        <el-table-column prop="duration" label="执行时长(ms)" width="120" />
        <el-table-column prop="startTime" label="执行时间" width="180" />
      </el-table>
    </div>

    <!-- 详细结果对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="执行详情"
      width="800px"
    >
      <el-tabs>
        <el-tab-pane label="请求信息">
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
              <span class="label">请求URL：</span>
              <span class="value">{{ currentDetail.url }}</span>
            </div>
            <div class="detail-item">
              <span class="label">请求方法：</span>
              <span class="value">{{ currentDetail.method || 'N/A' }}</span>
            </div>
            <div class="detail-item" v-if="currentDetail.header">
              <span class="label">请求头：</span>
            </div>
            <div v-if="currentDetail.header" class="code-content" style="margin: 10px 0;">
              <pre>{{ currentDetail.header }}</pre>
            </div>
            <div class="detail-item" v-if="currentDetail.param">
              <span class="label">请求体：</span>
            </div>
            <div v-if="currentDetail.param" class="code-content" style="margin: 10px 0;">
              <pre>{{ currentDetail.param }}</pre>
            </div>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="响应信息">
          <div class="detail-section">
            <div class="detail-item">
              <span class="label">响应状态码：</span>
              <span class="value">{{ currentDetail.status }}</span>
            </div>
            <div class="detail-item">
              <span class="label">HTTP状态：</span>
              <span class="value">{{ getHttpStatusText(currentDetail.status) }}</span>
            </div>
            <div class="detail-item">
              <span class="label">响应时间：</span>
              <span class="value">{{ currentDetail.time }} ms</span>
            </div>
            <div class="detail-item" v-if="currentDetail.size">
              <span class="label">响应大小：</span>
              <span class="value">{{ currentDetail.size }} bytes</span>
            </div>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="响应头">
          <div class="code-content">
            <pre>{{ currentDetail.responseHeaders || '{}' }}</pre>
          </div>
        </el-tab-pane>
        
        <el-tab-pane label="响应体">
          <div class="code-content">
            <pre>{{ currentDetail.responseBody }}</pre>
          </div>
        </el-tab-pane>
        <el-tab-pane label="断言结果">
          <div v-if="currentDetail.assertDetails && currentDetail.assertDetails.length > 0">
            <el-table :data="currentDetail.assertDetails" stripe style="width: 100%">
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
    </el-dialog>

    <!-- AI分析结果对话框 -->
    <el-dialog
      v-model="aiAnalysisVisible"
      title="AI 测试结果分析"
      width="800px"
      @close="handleAiClose"
      top="5vh"
    >
      <div v-if="aiAnalysis" class="ai-analysis-container">
        <!-- 结论头 -->
        <div class="ai-verdict-header">
          <el-tag :type="verdictTag" size="large" class="ai-verdict-tag">
            {{ verdictLabel }}
          </el-tag>
          <span class="ai-confidence" :class="'confidence-' + (aiAnalysis.confidence || 'low')">
            可信度: {{ confidenceLabel }}
          </span>
        </div>

        <!-- 错误类型 -->
        <div v-if="aiAnalysis.verdict !== 'passed'" class="ai-section">
          <div class="ai-section-title">
            <el-icon><i-ep-warning-filled /></el-icon>
            错误类型
          </div>
          <el-tag :type="errorTypeTag" effect="plain">
            {{ errorTypeLabel }}
          </el-tag>
        </div>

        <!-- 根因分析 -->
        <div v-if="aiAnalysis.rootCause" class="ai-section">
          <div class="ai-section-title">
            <el-icon><i-ep-search /></el-icon>
            根因分析
          </div>
          <div class="ai-card-content">{{ aiAnalysis.rootCause }}</div>
        </div>

        <!-- 详细分析 -->
        <div v-if="aiAnalysis.analysis" class="ai-section">
          <div class="ai-section-title">
            <el-icon><i-ep-document /></el-icon>
            详细分析
          </div>
          <div class="ai-card-content">{{ aiAnalysis.analysis }}</div>
        </div>

        <!-- 修复建议 -->
        <div v-if="aiAnalysis.suggestion" class="ai-section">
          <div class="ai-section-title">
            <el-icon><i-ep-tools /></el-icon>
            修复建议
          </div>
          <div class="ai-card-content suggestion-text">{{ aiAnalysis.suggestion }}</div>
        </div>

        <!-- 原始数据 -->
        <el-collapse style="margin-top: 16px;">
          <el-collapse-item title="查看原始请求/响应数据" name="raw">
            <div class="raw-data-grid">
              <div class="raw-item">
                <span class="raw-label">接口地址</span>
                <span class="raw-value">{{ aiReportData?.apiUrl || '-' }}</span>
              </div>
              <div class="raw-item">
                <span class="raw-label">HTTP方法</span>
                <el-tag size="small" :type="methodTag2(aiReportData?.requestMethod)">{{ aiReportData?.requestMethod }}</el-tag>
              </div>
              <div class="raw-item">
                <span class="raw-label">响应状态码</span>
                <el-tag size="small" :type="aiReportData?.responseStatus < 400 ? 'success' : 'danger'">{{ aiReportData?.responseStatus }}</el-tag>
              </div>
              <div class="raw-item">
                <span class="raw-label">执行耗时</span>
                <span class="raw-value">{{ aiReportData?.duration ? aiReportData.duration + 'ms' : '-' }}</span>
              </div>
              <div class="raw-item full-width">
                <span class="raw-label">请求体</span>
                <pre class="raw-pre">{{ aiReportData?.requestBody || '(空)' }}</pre>
              </div>
              <div class="raw-item full-width">
                <span class="raw-label">响应体</span>
                <pre class="raw-pre">{{ aiReportData?.responseBody || '(空)' }}</pre>
              </div>
              <div class="raw-item full-width">
                <span class="raw-label">断言结果</span>
                <pre class="raw-pre">{{ aiReportData?.assertDetail || '(无断言)' }}</pre>
              </div>
            </div>
          </el-collapse-item>
        </el-collapse>
      </div>
      <div v-else class="ai-loading">
        <el-icon class="is-loading" :size="32"><i-ep-loading /></el-icon>
        <p>AI 正在分析...</p>
      </div>
    </el-dialog>
  </div>
</template>

<script>
import { ref, reactive, onMounted, computed } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'


export default {
  name: 'BatchExecute',
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
    const maxConcurrency = ref(10) // 最大并发数，默认为10
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
    
    // 测试报告相关状态
    const timeRange = ref([]) // 时间范围选择
    const moduleName = ref('') // 模块名称筛选
    const caseNameKeyword = ref('') // 用例名称关键字
    const statusFilter = ref('') // 状态筛选
    const reportLoading = ref(false) // 报告查询加载状态
    const reportData = ref([]) // 查询到的报告数据
    const aiLoadingId = ref(null) // AI分析加载中
    const aiAnalysisVisible = ref(false)
    const aiAnalysis = ref(null)
    const aiReportData = ref(null)

    // AI分析用例结果
    const aiAnalyze = async (row) => {
      aiLoadingId.value = row.id
      aiReportData.value = row
      aiAnalysis.value = null
      aiAnalysisVisible.value = true
      try {
        const res = await axios.post('/ai/analyze-result/' + row.id)
        const data = res.data.data
        aiAnalysis.value = typeof data === 'string' ? JSON.parse(data) : data
      } catch (e) {
        aiAnalysis.value = { verdict: 'error', analysis: '分析失败: ' + (e.response?.data?.msg || e.message), rootCause: '', suggestion: '' }
      } finally {
        aiLoadingId.value = null
      }
    }

    const handleAiClose = () => {
      aiAnalysis.value = null
      aiReportData.value = null
    }

    const verdictTag = computed(() => {
      const v = aiAnalysis.value?.verdict
      if (v === 'passed') return 'success'
      if (v === 'failed') return 'danger'
      if (v === 'broken') return 'warning'
      return 'info'
    })

    const verdictLabel = computed(() => {
      const v = aiAnalysis.value?.verdict
      if (v === 'passed') return '通过'
      if (v === 'failed') return '失败'
      if (v === 'broken') return '异常中断'
      if (v === 'error') return '分析出错'
      return '分析中'
    })

    const confidenceLabel = computed(() => {
      const c = aiAnalysis.value?.confidence
      if (c === 'high') return '高'
      if (c === 'medium') return '中'
      return '低'
    })

    const errorTypeTag = computed(() => {
      const t = aiAnalysis.value?.errorType
      if (t === 'http_error') return 'danger'
      if (t === 'assert_error') return 'warning'
      if (t === 'timeout') return 'info'
      if (t === 'server_error') return 'danger'
      return ''
    })

    const errorTypeLabel = computed(() => {
      const t = aiAnalysis.value?.errorType
      if (t === 'http_error') return 'HTTP 请求错误'
      if (t === 'assert_error') return '断言验证失败'
      if (t === 'timeout') return '请求超时'
      if (t === 'server_error') return '服务端异常'
      return t || '-'
    })

    const methodTag2 = (m) => {
      const map = { GET: '', POST: 'success', PUT: 'warning', DELETE: 'danger', PATCH: 'info' }
      return map[m] || ''
    }
    
    



    
    // 获取项目列表
    const fetchProjects = async () => {
      try {
        const response = await axios.get('/projects')
        projects.value = response.data
        if (projects.value.length > 0 && !selectedProject.value) {
          selectedProject.value = projects.value[0].id
          loadUseCases()
        }
      } catch (error) {
        console.error('获取项目列表失败:', error)
        ElMessage.error('获取项目列表失败')
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
        const response = await axios.get(`/use-cases?pid=${selectedProject.value}`)
        // 使用新数组替换，确保Vue能检测到变化
        useCases.value = [...response.data]
        selectedUseCases.value = []
        console.log('切换项目后加载的用例数据:', useCases.value)
      } catch (error) {
        console.error('加载用例失败:', error)
        ElMessage.error('加载用例失败')
      }
    }

    // 处理选择变化
    const handleSelectionChange = (selection) => {
      selectedUseCases.value = selection
    }

    // 获取方法标签类型
    const getMethodTagType = (method) => {
      const methodMap = {
        GET: 'success',
        POST: 'primary',
        PUT: 'warning',
        DELETE: 'danger',
        PATCH: 'info'
      }
      return methodMap[method] || 'default'
    }

    // 批量并发执行
    const batchExecute = async () => {
      if (selectedUseCases.value.length === 0) {
        ElMessage.warning('请至少选择一个用例')
        return
      }
      
      loading.value = true
      executionDetails.value = []
      // 强制重置并显示进度条
      clearTimeout(window.progressTimeout)
      if (window.progressIntervalTimers) {
        window.progressIntervalTimers.forEach(timer => clearTimeout(timer))
      }
      window.progressIntervalTimers = []
      
      // 立即设置为0并显示
      progress.value = 0
      showProgress.value = true
      
      try {
        // 收集选中的用例ID
        const useCaseIds = selectedUseCases.value.map(uc => uc.id)
        
        console.log('开始批量并发执行，选中用例数:', selectedUseCases.value.length)
        console.log('执行次数:', executionCount.value)
        console.log('最大并发数:', maxConcurrency.value)
        
        const response = await axios.post('/execute', {
          useCaseIds: useCaseIds,
          executionCount: executionCount.value,
          maxConcurrency: maxConcurrency.value
        })
        
        const result = response.data
        
        // 更新进度条到100%
        progress.value = 100
        
        // 转换执行详情格式，适配前端显示
        executionDetails.value = result.details.map(detail => {
          // 根据执行次数决定是否显示次数信息
          let displayName = detail.useCaseName
          if (executionCount.value > 1 && detail.executionIndex) {
            displayName = `${detail.useCaseName} (第${detail.executionIndex}次)`
          }
          
          const apiResult = detail.result || {}
          const isSuccess = detail.success !== null ? detail.success : (apiResult.status >= 200 && apiResult.status < 300)
          
          // 查找对应的用例信息（用于获取method等）
          const useCase = selectedUseCases.value.find(uc => uc.id === detail.useCaseId)
          
          return {
            id: detail.useCaseId,
            name: displayName,
            url: useCase?.url || '',
            method: useCase?.method || '',
            status: apiResult.status || 'N/A',
            time: detail.duration || apiResult.time || 0,
            result: isSuccess ? 'success' : 'failed',
            assertResult: getAssertResultStatus(apiResult.assertResults || apiResult.assertResult),
            rawResult: apiResult,
            executionNum: detail.executionIndex || 1
          }
        })
        
        // 更新统计结果
        batchResults.total = result.total || 0
        batchResults.success = result.success || 0
        batchResults.failed = result.failed || 0
        batchResults.totalTime = result.totalTime || 0
        
        console.log('执行结果统计:', batchResults)
        console.log('executionDetails长度:', executionDetails.value.length)
        
        // 显示结果
        batchResultVisible.value = true
        
        ElMessage.success(
          `批量并发执行完成！共执行 ${result.total} 次，成功 ${result.success} 次，失败 ${result.failed} 次，总耗时 ${result.totalTime}ms`
        )
      } catch (error) {
        console.error('批量并发执行失败:', error)
        ElMessage.error('批量并发执行失败: ' + (error.response?.data?.message || error.message))
        
        // 即使失败也显示结果（如果有部分成功）
        if (executionDetails.value.length > 0) {
          batchResultVisible.value = true
        }
      } finally {
        loading.value = false
        // 延迟隐藏进度条
        window.progressTimeout = setTimeout(() => {
          showProgress.value = false
          progress.value = 0
        }, 1000)
      }
    }
    


    // 获取断言结果状态 - 支持数组和对象两种格式
    const getAssertResultStatus = (assertData) => {
      if (!assertData) {
        return 'none'
      }
      
      // 检查是数组格式还是对象格式
      if (Array.isArray(assertData)) {
        // 处理数组格式
        if (assertData.length === 0) {
          return 'none'
        }
        // 检查数组中每个断言项是否都通过
        const allPassed = assertData.every(item => item.result === true)
        return allPassed ? 'passed' : 'failed'
      } else {
        // 处理对象格式（兼容旧格式）
        if (Object.keys(assertData).length === 0) {
          return 'none'
        }
        const allPassed = Object.values(assertData).every(item => item.result)
        return allPassed ? 'passed' : 'failed'
      }
    }

    // 获取断言结果文本
    const getAssertResultText = (status) => {
      const statusMap = {
        passed: '通过',
        failed: '失败',
        none: '无断言',
        error: '错误'
      }
      return statusMap[status] || '未知'
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

    // 格式化响应体，优化显示效果
    const formatResponseBody = (data) => {
      if (data === null || data === undefined) {
        return 'null';
      }
      if (typeof data === 'string') {
        // 尝试解析字符串为JSON并格式化
        try {
          const parsed = JSON.parse(data);
          return JSON.stringify(parsed, null, 2);
        } catch (e) {
          // 如果不是有效的JSON，则直接返回原始字符串
          return data;
        }
      }
      // 对于对象或数组，直接格式化
      return JSON.stringify(data, null, 2);
    }

    // 格式化响应头，确保正确显示
    const formatHeaders = (headers) => {
      if (!headers) return '{}';
      if (typeof headers === 'string') {
        try {
          const parsed = JSON.parse(headers);
          return JSON.stringify(parsed, null, 2);
        } catch (e) {
          return headers;
        }
      }
      return JSON.stringify(headers, null, 2);
    }


    
    // 查看详情
    const viewDetail = (row) => {
      Object.assign(currentDetail, {
        name: row.name,
        url: row.url,
        status: row.status,
        time: row.time,
        method: row.method,
        header: row.rawResult?.header ? formatHeaders(row.rawResult.header) : '',
        param: row.rawResult?.param ? formatResponseBody(row.rawResult.param) : '',
        size: row.rawResult?.result ? JSON.stringify(row.rawResult.result).length : 0,
        // 使用优化的格式化函数
        responseBody: formatResponseBody(row.rawResult?.result || {}),
        // 尝试从多个可能的字段获取响应头
        responseHeaders: formatHeaders(row.rawResult?.headers || row.rawResult?.header || {}),
        assertDetails: []
      })
      
      // 处理断言详情 - 支持两种格式：assertResults数组和assertResult对象
      if (row.rawResult?.assertResults && Array.isArray(row.rawResult.assertResults)) {
        // 处理新格式：assertResults数组
        row.rawResult.assertResults.forEach(assertItem => {
          currentDetail.assertDetails.push({
            field: assertItem.field,
            expected: assertItem.expected,
            actual: assertItem.actual,
            result: assertItem.result
          })
        })
      } else if (row.rawResult?.assertResult) {
        // 兼容旧格式：assertResult对象
        Object.keys(row.rawResult.assertResult).forEach(key => {
          const assertItem = row.rawResult.assertResult[key]
          currentDetail.assertDetails.push({
            field: key,
            expected: assertItem.expected,
            actual: assertItem.actual,
            result: assertItem.result
          })
        })
      }
      
      detailVisible.value = true
    }

    // 查询测试用例执行记录
    const queryTestCases = async () => {
      if (!timeRange.value || timeRange.value.length !== 2) {
        ElMessage.warning('请选择时间范围')
        return
      }
      
      reportLoading.value = true
      
      // 创建Date对象时调整时区偏移，确保传递用户选择的实际本地时间
      const createLocalISOString = (dateString) => {
        const date = new Date(dateString);
        // 处理时区偏移，保持本地时间不变
        const offset = date.getTimezoneOffset();
        const adjustedDate = new Date(date.getTime() - offset * 60 * 1000);
        return adjustedDate.toISOString();
      };
      
      const params = {
        startTime: createLocalISOString(timeRange.value[0]),
        endTime: createLocalISOString(timeRange.value[1]),
        moduleName: moduleName.value || undefined,
        caseName: caseNameKeyword.value || undefined,
        status: statusFilter.value || undefined
      }
      
      try {
        const response = await axios.post('/report/cases', params)
        if (response.data.code === 1) {
          reportData.value = response.data.data
          ElMessage.success(`查询到 ${reportData.value.length} 条记录`)
        } else {
          ElMessage.error(response.data.msg || '查询失败')
        }
      } catch (error) {
        console.error('查询测试用例记录失败:', error)
        ElMessage.error('查询失败，请稍后重试')
      } finally {
        reportLoading.value = false
      }
    }
    
    // 导出Allure HTML报告
    const exportTestReport = async () => {
      if (!timeRange.value || timeRange.value.length !== 2) {
        ElMessage.warning('请选择时间范围')
        return
      }
      
      reportLoading.value = true
      
      // 创建Date对象时调整时区偏移，确保传递用户选择的实际本地时间
      const createLocalISOString = (dateString) => {
        const date = new Date(dateString);
        // 处理时区偏移，保持本地时间不变
        const offset = date.getTimezoneOffset();
        const adjustedDate = new Date(date.getTime() - offset * 60 * 1000);
        return adjustedDate.toISOString();
      };
      
      const params = {
        startTime: createLocalISOString(timeRange.value[0]),
        endTime: createLocalISOString(timeRange.value[1]),
        moduleName: moduleName.value || undefined,
        caseName: caseNameKeyword.value || undefined,
        status: statusFilter.value || undefined
      }
      
      try {
        const response = await axios.post('/report/export', params, {
          responseType: 'blob'
        })
        
        if (response.status === 204) {
          ElMessage.warning('该时间范围内没有测试数据')
          return
        }
        
        // 获取文件名（从响应头）
        const contentDisposition = response.headers.get('Content-Disposition')
        const fileName = contentDisposition 
          ? decodeURIComponent(contentDisposition.split('filename=')[1].replace(/"/g, ''))
          : `allure-report-${Date.now()}.zip`
        
        // 创建下载链接
        const blob = new Blob([response.data])
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = fileName
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
        
        ElMessage.success('报告导出成功')
      } catch (error) {
        console.error('导出报告失败:', error)
        ElMessage.error('导出失败，请稍后重试')
      } finally {
        reportLoading.value = false
      }
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
        // 测试报告相关状态
        timeRange,
        moduleName,
        caseNameKeyword,
        statusFilter,
        reportLoading,
        reportData,
        // 方法
        getMethodTagType,
        getHttpStatusText,
        handleSelectionChange,
        loadUseCases,
        batchExecute,
        getAssertResultText,
        viewDetail,
        queryTestCases,
        exportTestReport,
        aiAnalyze,
        aiLoadingId,
        aiAnalysisVisible,
        aiAnalysis,
        aiReportData,
        verdictTag,
        verdictLabel,
        confidenceLabel,
        errorTypeTag,
        errorTypeLabel,
        methodTag2,
        handleAiClose
      }
  }
}
</script>

<style scoped>
.batch-execute-container {
  padding: 20px;
}

.batch-card,
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
  width: 100px;
}

.value {
  color: #303133;
}

.no-assert {
  text-align: center;
  color: #909399;
  padding: 20px;
}

/* AI 分析结果样式 */
.ai-analysis-container {
  padding: 0 4px;
}

.ai-verdict-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  background: #f5f7fa;
  border-radius: 8px;
  margin-bottom: 16px;
}

.ai-verdict-tag {
  font-size: 16px !important;
  font-weight: 600;
  padding: 8px 20px !important;
}

.ai-confidence {
  font-size: 13px;
  color: #909399;
}

.ai-confidence.confidence-high { color: #67c23a; }
.ai-confidence.confidence-medium { color: #e6a23c; }
.ai-confidence.confidence-low { color: #909399; }

.ai-section {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 14px 16px;
  margin-bottom: 12px;
}

.ai-section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 10px;
}

.ai-card-content {
  font-size: 14px;
  line-height: 1.8;
  color: #606266;
  white-space: pre-wrap;
}

.suggestion-text {
  color: #e6a23c;
  background: #fdf6ec;
  padding: 10px 12px;
  border-radius: 4px;
}

.raw-data-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 10px;
}

.raw-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.raw-item.full-width {
  grid-column: 1 / -1;
}

.raw-label {
  font-size: 12px;
  color: #909399;
  font-weight: 500;
}

.raw-value {
  font-size: 13px;
  color: #303133;
}

.raw-pre {
  background: #f5f7fa;
  padding: 8px 10px;
  border-radius: 4px;
  font-size: 12px;
  max-height: 120px;
  overflow: auto;
  margin: 0;
  white-space: pre-wrap;
  word-break: break-all;
}

.ai-loading {
  text-align: center;
  padding: 40px;
  color: #909399;
}
</style>
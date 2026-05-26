<template>
  <div class="ui-test-container">
    <el-card class="ui-test-card">
      <template #header>
        <div class="card-header">
          <span>UI测试工作台</span>
          <el-tag type="success">在线执行</el-tag>
        </div>
      </template>

      
          <el-alert
            title="说明"
            type="info"
            :closable="false"
            class="top-alert"
            description="该工作台不提供录制功能，直接按照前端配置的数据请求后端执行 UI 自动化。请完善目标信息、测试步骤，然后点击“立即执行”即可调用后端接口。"
          />

      <div class="section-title">基础配置</div>
      <el-form :model="testcaseForm" label-width="110px" class="config-form">
        <el-row :gutter="20">
          <el-col :span="24">
            <el-form-item label="目标 URL">
              <el-input v-model="testcaseForm.url" placeholder="https://example.com">
                <template #prepend>
                  <el-icon><i-ep-link /></el-icon>
                </template>
              </el-input>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="浏览器">
              <el-select v-model="testcaseForm.browser" style="width: 100%;">
                <el-option label="Chrome" value="chrome" />
                <el-option label="Firefox" value="firefox" />
                <el-option label="Edge" value="edge" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="窗口大小">
              <el-select v-model="testcaseForm.viewport" style="width: 100%;">
                <el-option label="1920x1080 (桌面)" value="1920x1080" />
                <el-option label="1366x768 (笔记本)" value="1366x768" />
                <el-option label="414x896 (iPhone)" value="414x896" />
                <el-option label="375x667 (移动)" value="375x667" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="运行模式">
              <el-switch
                v-model="testcaseForm.headless"
                active-text="无头"
                inactive-text="有头"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="超时时间 (秒)">
              <el-input-number v-model="testcaseForm.timeout" :min="5" :max="120" />
            </el-form-item>
          </el-col>
        </el-row>
      </el-form>

      <div class="section-title">
        测试步骤
        <el-button type="primary" size="small" @click="openStepDialog()">
          <el-icon><i-ep-plus /></el-icon>
          添加步骤
        </el-button>
      </div>

      <el-table
        v-if="testSteps.length"
        :data="testSteps"
        border
        stripe
        class="steps-table"
      >
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="name" label="步骤名称" min-width="140" show-overflow-tooltip />
        <el-table-column label="操作" width="120">
          <template #default="{ row }">
            <el-tag :type="getActionTag(row.action)">
              {{ actionOptions[row.action]?.label || row.action }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="locatorType" label="定位方式" width="110">
          <template #default="{ row }">
            <el-tag size="small">{{ row.locatorType?.toUpperCase() || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="locatorValue" label="定位值" min-width="160" show-overflow-tooltip />
        <el-table-column prop="actionValue" label="操作值" min-width="140" show-overflow-tooltip />
        <el-table-column prop="waitTime" label="等待(ms)" width="110" />
        <el-table-column label="断言" width="140">
          <template #default="{ row }">
            <el-tag v-if="row.assertion" size="small" type="success">
              {{ assertionText(row.assertion) }}
            </el-tag>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right">
          <template #default="{ $index }">
            <el-button type="primary" link size="small" @click="openStepDialog($index)">编辑</el-button>
            <el-button type="danger" link size="small" @click="removeStep($index)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-else
        description="暂无步骤，点击上方按钮添加"
        :image-size="120"
        class="steps-empty"
      />

      <div class="section-title">请求载荷预览</div>
      <el-card shadow="never" class="payload-card">
        <div class="payload-actions">
          <span>以下 JSON 将直接发送给后端接口</span>
          <el-button text type="primary" size="small" @click="copyPayload">复制</el-button>
        </div>
        <pre class="payload-preview">{{ payloadPreview }}</pre>
      </el-card>

      <div class="action-buttons">
        <el-button type="primary" size="large" :loading="executing" @click="executeTest">
          <el-icon><i-ep-video-play /></el-icon>
          {{ executing ? '执行中...' : '立即执行' }}
        </el-button>
        <el-button size="large" @click="resetForm">
          <el-icon><i-ep-delete /></el-icon>
          清空
        </el-button>
      </div>

      <el-card v-if="executeResult" class="result-card" shadow="never">
        <template #header>
          <div class="result-header">
            <span>执行结果</span>
            <el-tag :type="executeResult.success ? 'success' : 'danger'">
              {{ executeResult.success ? '成功' : '失败' }}
            </el-tag>
          </div>
        </template>

        <el-descriptions :column="2" border>
          <el-descriptions-item label="执行ID">
            {{ executeResult.executionId || '-' }}
          </el-descriptions-item>
          <el-descriptions-item label="总耗时">
            {{ executeResult.duration || 0 }} ms
          </el-descriptions-item>
          <el-descriptions-item label="通过步骤">
            {{ executeResult.passedSteps || 0 }}
          </el-descriptions-item>
          <el-descriptions-item label="失败步骤">
            {{ executeResult.failedSteps || 0 }}
          </el-descriptions-item>
        </el-descriptions>

        <div v-if="executeResult.error" class="result-error">
          <el-alert
            :title="executeResult.error"
            type="error"
            :closable="false"
          />
        </div>

        <el-table
          v-if="executeResult.steps && executeResult.steps.length"
          :data="executeResult.steps"
          stripe
          style="margin-top: 20px;"
        >
          <el-table-column type="index" label="#" width="50" />
          <el-table-column prop="name" label="步骤" min-width="140" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'passed' ? 'success' : 'danger'">
                {{ row.status === 'passed' ? '通过' : '失败' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="duration" label="耗时(ms)" width="120" />
          <el-table-column prop="message" label="提示" min-width="200" show-overflow-tooltip />
        </el-table>
      </el-card>
    </el-card>

    <el-dialog
      v-model="stepDialogVisible"
      :title="currentStepIndex === -1 ? '添加步骤' : '编辑步骤'"
      width="620px"
      destroy-on-close
    >
      <el-form :model="stepForm" :rules="stepRules" ref="stepFormRef" label-width="110px">
        <el-form-item label="步骤名称" prop="name">
          <el-input v-model="stepForm.name" placeholder="例如：输入用户名" />
        </el-form-item>

        <el-form-item label="操作类型" prop="action">
          <el-select v-model="stepForm.action" style="width: 100%;" @change="handleActionChange">
            <el-option
              v-for="item in actionOptionsList"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="定位方式" prop="locatorType" v-if="showLocator">
          <el-radio-group v-model="stepForm.locatorType">
            <el-radio label="id">ID</el-radio>
            <el-radio label="css">CSS</el-radio>
            <el-radio label="xpath">XPath</el-radio>
            <el-radio label="text">文本</el-radio>
            <el-radio label="name">Name</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="定位值" prop="locatorValue" v-if="showLocator">
          <el-input v-model="stepForm.locatorValue" placeholder="请输入定位值" />
        </el-form-item>

        <el-form-item label="操作值" v-if="showActionValue">
          <el-input v-model="stepForm.actionValue" :placeholder="actionValuePlaceholder" />
        </el-form-item>

        <el-form-item label="自定义扩展" v-if="showCodeEditor">
          <el-input
            v-model="stepForm.customCode"
            type="textarea"
            :rows="4"
            placeholder="// 示例1: 点击元素document.querySelector('#button').click();// 示例2: 返回数据return {username: document.querySelector('#username').value, status: 'success'};// 示例3: 复杂逻辑const elements = document.querySelectorAll('.list-item');const results = Array.from(elements).map(el => el.textContent);return {items: results};"
          />
          <div class="code-example-hint">
            <small>提示：代码将在浏览器环境中执行，可访问window、document等对象。支持DOM操作、数据提取和复杂逻辑。</small>
          </div>
        </el-form-item>

        <el-form-item label="等待时间(ms)">
          <el-input-number v-model="stepForm.waitTime" :min="0" :max="60000" :step="200" />
        </el-form-item>

        <el-form-item label="断言" v-if="showAssertion">
          <el-switch v-model="stepForm.useAssertion" />
          <div v-if="stepForm.useAssertion" class="assertion-box">
            <el-select v-model="stepForm.assertion.type" style="width: 160px; margin-right: 10px;">
              <el-option label="等于" value="equals" />
              <el-option label="包含" value="contains" />
              <el-option label="不为空" value="notEmpty" />
            </el-select>
            <el-input
              v-if="stepForm.assertion.type !== 'notEmpty'"
              v-model="stepForm.assertion.expected"
              placeholder="期望值"
            />
          </div>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="closeStepDialog">取消</el-button>
        <el-button type="primary" @click="confirmStep">确定</el-button>
      </template>
    </el-dialog>

    <!-- 项目编辑对话框 -->
    <el-dialog
      v-model="projectDialogVisible"
      :title="currentProject ? '编辑项目' : '新建项目'"
      width="500px"
      destroy-on-close
    >
      <el-form :model="projectForm" :rules="projectRules" ref="projectFormRef" label-width="100px">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="projectForm.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input
            v-model="projectForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入项目描述"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeProjectDialog">取消</el-button>
        <el-button type="primary" @click="confirmProject">确定</el-button>
      </template>
    </el-dialog>

    <!-- 用例编辑对话框 -->
    <el-dialog
      v-model="caseDialogVisible"
      :title="currentCase ? '编辑用例' : '新建用例'"
      width="800px"
      destroy-on-close
    >
      <el-form :model="caseForm" :rules="caseRules" ref="caseFormRef" label-width="100px">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="用例名称" prop="name">
              <el-input v-model="caseForm.name" placeholder="请输入用例名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属项目" prop="projectId">
              <el-select v-model="caseForm.projectId" placeholder="请选择所属项目">
                <el-option
                  v-for="project in projects"
                  :key="project.id"
                  :label="project.name"
                  :value="project.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="用例描述">
          <el-input
            v-model="caseForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入用例描述"
          />
        </el-form-item>
        <el-divider>用例配置</el-divider>
        <el-form-item label="目标 URL" prop="url">
          <el-input v-model="caseForm.url" placeholder="https://example.com" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="浏览器" prop="browser">
              <el-select v-model="caseForm.browser" placeholder="请选择浏览器">
                <el-option label="Chrome" value="chrome" />
                <el-option label="Firefox" value="firefox" />
                <el-option label="Edge" value="edge" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="窗口大小" prop="viewport">
              <el-select v-model="caseForm.viewport" placeholder="请选择窗口大小">
                <el-option label="1920x1080 (桌面)" value="1920x1080" />
                <el-option label="1366x768 (笔记本)" value="1366x768" />
                <el-option label="414x896 (iPhone)" value="414x896" />
                <el-option label="375x667 (移动)" value="375x667" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="运行模式">
              <el-switch
                v-model="caseForm.headless"
                active-text="无头"
                inactive-text="有头"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="超时时间 (秒)" prop="timeout">
          <el-input-number v-model="caseForm.timeout" :min="5" :max="120" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="closeCaseDialog">取消</el-button>
        <el-button type="primary" @click="confirmCase">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, reactive, computed } from 'vue'
import axios from 'axios'
import { ElMessage } from 'element-plus'

const ACTION_OPTIONS = {
  click: { value: 'click', label: '点击元素', tag: 'primary', requiresLocator: true },
  input: { value: 'input', label: '输入文本', tag: 'success', requiresLocator: true, requiresValue: true, valuePlaceholder: '请输入文本内容' },
  getText: { value: 'getText', label: '获取文本', tag: 'info', requiresLocator: true, supportsAssertion: true },
  getAttribute: { value: 'getAttribute', label: '获取属性', tag: 'warning', requiresLocator: true, requiresValue: true, valuePlaceholder: '请输入属性名，如 value', supportsAssertion: true },
  hover: { value: 'hover', label: '悬停', tag: 'default', requiresLocator: true },
  waitVisible: { value: 'waitVisible', label: '等待显示', tag: 'danger', requiresLocator: true },
  waitHidden: { value: 'waitHidden', label: '等待消失', tag: 'danger', requiresLocator: true },
  scroll: { value: 'scroll', label: '滚动', tag: 'info', requiresLocator: false, requiresValue: true, valuePlaceholder: 'top / bottom / 500' },
  screenshot: { value: 'screenshot', label: '截图', tag: 'success', requiresLocator: false },
  switchWindow: { value: 'switchWindow', label: '切换窗口', tag: 'warning', requiresLocator: false, requiresValue: true, valuePlaceholder: '窗口标题或索引' },
  customCode: { value: 'customCode', label: '自定义扩展', tag: 'info', requiresLocator: false, requiresCode: true }
}

export default {
  name: 'UiTest',
  setup() {
    // 标签页状态
    const activeTab = ref('online-execution')

    // 在线执行相关状态
    const testcaseForm = reactive({
      url: '',
      browser: 'chrome',
      viewport: '1920x1080',
      headless: true,
      timeout: 30
    })

    const testSteps = ref([])
    const executing = ref(false)
    const stepDialogVisible = ref(false)
    const stepFormRef = ref(null)
    const currentStepIndex = ref(-1)
    const executeResult = ref(null)

    const stepForm = reactive({
      name: '',
      action: 'click',
      locatorType: 'id',
      locatorValue: '',
      actionValue: '',
      customCode: '',
      waitTime: 0,
      useAssertion: false,
      assertion: {
        type: 'equals',
        expected: ''
      }
    })

    const stepRules = {
      name: [{ required: true, message: '请输入步骤名称', trigger: 'blur' }],
      action: [{ required: true, message: '请选择操作类型', trigger: 'change' }],
      locatorType: [
        {
          required: true,
          message: '请选择定位方式',
          trigger: 'change',
          validator: (_, value, callback) => {
            if (!showLocator.value) return callback()
            if (!value) return callback(new Error('请选择定位方式'))
            callback()
          }
        }
      ],
      locatorValue: [
        {
          required: true,
          message: '请输入定位值',
          trigger: 'blur',
          validator: (_, value, callback) => {
            if (!showLocator.value) return callback()
            if (!value?.trim()) return callback(new Error('请输入定位值'))
            callback()
          }
        }
      ]
    }

    // 项目管理相关状态
    const projects = ref([])
    const projectDialogVisible = ref(false)
    const projectFormRef = ref(null)
    const currentProject = ref(null)

    const projectForm = reactive({
      name: '',
      description: ''
    })

    const projectRules = {
      name: [{ required: true, message: '请输入项目名称', trigger: 'blur' }]
    }

    // 用例管理相关状态
    const testCases = ref([])
    const caseDialogVisible = ref(false)
    const caseFormRef = ref(null)
    const currentCase = ref(null)
    const selectedCases = ref([])

    const caseForm = reactive({
      name: '',
      projectId: '',
      description: '',
      url: '',
      browser: 'chrome',
      viewport: '1920x1080',
      headless: true,
      timeout: 30
    })

    const caseRules = {
      name: [{ required: true, message: '请输入用例名称', trigger: 'blur' }],
      projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }],
      url: [{ required: true, message: '请输入目标URL', trigger: 'blur' }],
      browser: [{ required: true, message: '请选择浏览器', trigger: 'change' }],
      viewport: [{ required: true, message: '请选择窗口大小', trigger: 'change' }],
      timeout: [{ required: true, message: '请输入超时时间', trigger: 'change' }]
    }

    // 批量执行相关状态
    const batchExecutions = ref([])

    const actionOptions = ACTION_OPTIONS
    const actionOptionsList = computed(() => Object.values(actionOptions))
    const showLocator = computed(() => actionOptions[stepForm.action]?.requiresLocator !== false)
    const showActionValue = computed(() => actionOptions[stepForm.action]?.requiresValue)
    const showAssertion = computed(() => actionOptions[stepForm.action]?.supportsAssertion)
    const showCodeEditor = computed(() => actionOptions[stepForm.action]?.requiresCode)
    const actionValuePlaceholder = computed(() => actionOptions[stepForm.action]?.valuePlaceholder || '请输入参数')

    const payloadPreview = computed(() => JSON.stringify({
      ...testcaseForm,
      steps: testSteps.value
    }, null, 2))

    const openStepDialog = (index = -1) => {
      currentStepIndex.value = index
      if (index !== -1) {
        Object.assign(stepForm, testSteps.value[index])
      } else {
        resetStepForm()
      }
      stepDialogVisible.value = true
    }

    const closeStepDialog = () => {
      stepDialogVisible.value = false
      resetStepForm()
    }

    const resetStepForm = () => {
      Object.assign(stepForm, {
        name: '',
        action: 'click',
        locatorType: 'id',
        locatorValue: '',
        actionValue: '',
        customCode: '',
        waitTime: 0,
        useAssertion: false,
        assertion: {
          type: 'equals',
          expected: ''
        }
      })
      if (stepFormRef.value) stepFormRef.value.clearValidate()
    }

    const handleActionChange = () => {
      if (!showLocator.value) {
        stepForm.locatorValue = ''
      }
      if (!showActionValue.value) {
        stepForm.actionValue = ''
      }
      if (!showCodeEditor.value) {
        stepForm.customCode = ''
      }
      if (!showAssertion.value) {
        stepForm.useAssertion = false
        stepForm.assertion.expected = ''
      }
    }

    const confirmStep = () => {
      if (!stepFormRef.value) return
      stepFormRef.value.validate((valid) => {
        if (!valid) return
        const step = {
          name: stepForm.name,
          action: stepForm.action,
          locatorType: showLocator.value ? stepForm.locatorType : '',
          locatorValue: showLocator.value ? stepForm.locatorValue : '',
          actionValue: showActionValue.value ? stepForm.actionValue : '',
          customCode: showCodeEditor.value ? stepForm.customCode : '',
          waitTime: stepForm.waitTime,
          assertion: stepForm.useAssertion ? { ...stepForm.assertion } : null
        }

        if (currentStepIndex.value === -1) {
          testSteps.value.push(step)
          ElMessage.success('步骤已添加')
        } else {
          testSteps.value[currentStepIndex.value] = step
          ElMessage.success('步骤已更新')
        }
        closeStepDialog()
      })
    }

    const removeStep = (index) => {
      testSteps.value.splice(index, 1)
      ElMessage.success('步骤已删除')
    }

    const assertionText = (assertion) => {
      if (!assertion) return ''
      const labelMap = {
        equals: '等于',
        contains: '包含',
        notEmpty: '不为空'
      }
      return assertion.type === 'notEmpty'
        ? `${labelMap[assertion.type]}`
        : `${labelMap[assertion.type]} ${assertion.expected}`
    }

    const getActionTag = (action) => ACTION_OPTIONS[action]?.tag || 'info'

    const validateBeforeExecute = () => {
      if (!testcaseForm.url.trim()) {
        ElMessage.warning('请填写目标 URL')
        return false
      }
      if (!testSteps.value.length) {
        ElMessage.warning('请至少配置一个步骤')
        return false
      }
      return true
    }

    const executeTest = async () => {
      if (!validateBeforeExecute()) return
      executing.value = true
      executeResult.value = null
      try {
        const payload = {
          ...testcaseForm,
          steps: testSteps.value
        }
        const { data } = await axios.post('/ui-test/run', payload)
        if (data.code === 1) {
          executeResult.value = data.data
          ElMessage.success('提交成功，已收到执行结果')
        } else {
          ElMessage.error(data.msg || '执行失败')
        }
      } catch (error) {
        console.error('executeTest error', error)
        ElMessage.error(error.response?.data?.msg || '执行失败，请检查后端服务')
      } finally {
        executing.value = false
      }
    }

    const resetForm = () => {
      Object.assign(testcaseForm, {
        url: '',
        browser: 'chrome',
        viewport: '1920x1080',
        headless: true,
        timeout: 30
      })
      testSteps.value = []
      executeResult.value = null
    }

    const copyPayload = async () => {
      try {
        await navigator.clipboard.writeText(payloadPreview.value)
        ElMessage.success('已复制')
      } catch {
        ElMessage.error('复制失败，请手动选择文本复制')
      }
    }

    // 项目管理相关方法
    const openProjectDialog = (project = null) => {
      currentProject.value = project
      if (project) {
        Object.assign(projectForm, {
          name: project.name,
          description: project.description
        })
      } else {
        resetProjectForm()
      }
      projectDialogVisible.value = true
    }

    const closeProjectDialog = () => {
      projectDialogVisible.value = false
      resetProjectForm()
    }

    const resetProjectForm = () => {
      Object.assign(projectForm, {
        name: '',
        description: ''
      })
      if (projectFormRef.value) projectFormRef.value.clearValidate()
    }

    const confirmProject = () => {
      if (!projectFormRef.value) return
      projectFormRef.value.validate((valid) => {
        if (!valid) return
        const project = {
          id: currentProject.value?.id || Date.now().toString(),
          name: projectForm.name,
          description: projectForm.description,
          createTime: currentProject.value?.createTime || new Date().toISOString(),
          updateTime: new Date().toISOString()
        }

        if (currentProject.value) {
          // 编辑现有项目
          const index = projects.value.findIndex(p => p.id === project.id)
          if (index !== -1) {
            projects.value[index] = project
            ElMessage.success('项目已更新')
          }
        } else {
          // 新建项目
          projects.value.push(project)
          ElMessage.success('项目已创建')
        }
        closeProjectDialog()
      })
    }

    const deleteProject = (id) => {
      projects.value = projects.value.filter(p => p.id !== id)
      ElMessage.success('项目已删除')
    }

    // 用例管理相关方法
    const openCaseDialog = (testCase = null) => {
      currentCase.value = testCase
      if (testCase) {
        Object.assign(caseForm, {
          name: testCase.name,
          projectId: testCase.projectId,
          description: testCase.description,
          url: testCase.url,
          browser: testCase.browser,
          viewport: testCase.viewport,
          headless: testCase.headless,
          timeout: testCase.timeout
        })
      } else {
        resetCaseForm()
      }
      caseDialogVisible.value = true
    }

    const closeCaseDialog = () => {
      caseDialogVisible.value = false
      resetCaseForm()
    }

    const resetCaseForm = () => {
      Object.assign(caseForm, {
        name: '',
        projectId: '',
        description: '',
        url: '',
        browser: 'chrome',
        viewport: '1920x1080',
        headless: true,
        timeout: 30
      })
      if (caseFormRef.value) caseFormRef.value.clearValidate()
    }

    const confirmCase = () => {
      if (!caseFormRef.value) return
      caseFormRef.value.validate((valid) => {
        if (!valid) return
        const project = projects.value.find(p => p.id === caseForm.projectId)
        const testCase = {
          id: currentCase.value?.id || Date.now().toString(),
          name: caseForm.name,
          projectId: caseForm.projectId,
          projectName: project?.name || '',
          description: caseForm.description,
          url: caseForm.url,
          browser: caseForm.browser,
          viewport: caseForm.viewport,
          headless: caseForm.headless,
          timeout: caseForm.timeout,
          createTime: currentCase.value?.createTime || new Date().toISOString(),
          updateTime: new Date().toISOString()
        }

        if (currentCase.value) {
          // 编辑现有用例
          const index = testCases.value.findIndex(c => c.id === testCase.id)
          if (index !== -1) {
            testCases.value[index] = testCase
            ElMessage.success('用例已更新')
          }
        } else {
          // 新建用例
          testCases.value.push(testCase)
          ElMessage.success('用例已创建')
        }
        closeCaseDialog()
      })
    }

    const deleteCase = (id) => {
      testCases.value = testCases.value.filter(c => c.id !== id)
      ElMessage.success('用例已删除')
    }

    const runSingleCase = (testCase) => {
      // 单个用例执行逻辑
      ElMessage.info(`执行用例：${testCase.name}`)
      // 这里可以添加实际的执行逻辑
    }

    // 批量执行相关方法
    const batchExecuteCases = () => {
      if (selectedCases.value.length === 0) {
        ElMessage.warning('请选择要执行的用例')
        return
      }
      ElMessage.success(`批量执行 ${selectedCases.value.length} 个用例`)
      // 这里可以添加实际的批量执行逻辑
    }

    const viewBatchResult = (batchId) => {
      ElMessage.info(`查看批次 ${batchId} 的执行结果`)
      // 这里可以添加查看批量执行结果的逻辑
    }

    return {
      // 标签页状态
      activeTab,
      // 在线执行相关
      testcaseForm,
      testSteps,
      executing,
      stepDialogVisible,
      stepForm,
      stepFormRef,
      stepRules,
      currentStepIndex,
      actionOptions,
      actionOptionsList,
      showLocator,
      showActionValue,
      showAssertion,
      showCodeEditor,
      actionValuePlaceholder,
      payloadPreview,
      executeResult,
      openStepDialog,
      closeStepDialog,
      confirmStep,
      removeStep,
      handleActionChange,
      getActionTag,
      assertionText,
      executeTest,
      resetForm,
      copyPayload,
      // 项目管理相关
      projects,
      projectDialogVisible,
      projectForm,
      projectFormRef,
      projectRules,
      openProjectDialog,
      closeProjectDialog,
      confirmProject,
      deleteProject,
      // 用例管理相关
      testCases,
      caseDialogVisible,
      caseForm,
      caseFormRef,
      caseRules,
      selectedCases,
      openCaseDialog,
      closeCaseDialog,
      confirmCase,
      deleteCase,
      runSingleCase,
      batchExecuteCases,
      // 批量执行相关
      batchExecutions,
      viewBatchResult
    }
  }
}
</script>

<style scoped>
.ui-test-container {
  padding: 20px;
}

.ui-test-card {
  min-height: 640px;
}

.ui-test-tabs {
  margin-top: 20px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.top-alert {
  margin-bottom: 20px;
}

.section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-weight: 600;
  margin: 24px 0 12px;
}

.config-form {
  background: #f9fafc;
  padding: 16px 16px 6px;
  border-radius: 8px;
}

.steps-table,
.projects-table,
.cases-table,
.batch-executions-table {
  margin-bottom: 20px;
}

.steps-empty,
.projects-empty,
.cases-empty,
.batch-executions-empty {
  padding: 30px 0;
}

.payload-card {
  margin-bottom: 20px;
}

.payload-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
}

.payload-preview {
  max-height: 240px;
  overflow: auto;
  background: #1e1e1e;
  color: #ecf0f1;
  padding: 12px;
  border-radius: 4px;
  font-family: 'JetBrains Mono', 'Consolas', monospace;
  font-size: 13px;
}

.action-buttons {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin: 24px 0;
}

.result-card {
  margin-top: 20px;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.result-error {
  margin-top: 16px;
}

.code-example-hint {
  margin-top: 5px;
  color: #606266;
  font-size: 12px;
}

.assertion-box {
  margin-top: 12px;
  display: flex;
  align-items: center;
  gap: 10px;
}
</style>


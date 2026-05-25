<template>
  <div class="ui-use-cases-container">
    <el-card class="ui-use-case-card">
      <template #header>
        <div class="card-header">
          <span>UI用例列表</span>
          <div class="header-actions">
            <el-select v-model="selectedProject" placeholder="选择项目" style="width: 200px; margin-right: 10px;" @change="handleProjectChange">
              <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
            </el-select>
            <el-button type="primary" size="small" @click="showAddDialog">
              <el-icon><i-ep-plus /></el-icon>
              添加用例
            </el-button>
          </div>
        </div>
      </template>

      <el-table :data="useCases" stripe style="width: 100%">
        <el-table-column type="selection" width="55" />
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="用例名称" />
        <el-table-column prop="projectName" label="所属项目" width="140" />
        <el-table-column prop="url" label="目标URL" width="200" show-overflow-tooltip />
        <el-table-column prop="browser" label="浏览器" width="100">
          <template #default="{ row }">
            <el-tag size="small">{{ row.browser }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="viewport" label="窗口大小" width="120" />
        <el-table-column prop="steps" label="步骤数" width="100">
          <template #default="{ row }">
            <el-tag size="small" type="info">{{ row.steps?.length || 0 }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="150" show-overflow-tooltip />
        <el-table-column label="操作" width="140" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="editUseCase(row)">
              编辑
            </el-button>
            <el-button type="danger" size="small" @click="deleteUseCase(row.id)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加/编辑用例对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="800px"
      @close="closeDialog"
    >
      <el-form :model="useCaseForm" :rules="rules" ref="useCaseFormRef">
        <el-row :gutter="20">
          <el-col :span="12">
            <el-form-item label="用例名称" prop="name">
              <el-input v-model="useCaseForm.name" placeholder="请输入用例名称" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属项目" prop="projectId">
              <el-select v-model="useCaseForm.projectId" placeholder="请选择所属项目">
                <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="用例描述">
          <el-input
            v-model="useCaseForm.description"
            type="textarea"
            :rows="3"
            placeholder="请输入用例描述"
          />
        </el-form-item>
        <el-divider>用例配置</el-divider>
        <el-form-item label="目标 URL" prop="url">
          <el-input v-model="useCaseForm.url" placeholder="https://example.com" />
        </el-form-item>
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="浏览器" prop="browser">
              <el-select v-model="useCaseForm.browser" placeholder="请选择浏览器">
                <el-option label="Chrome" value="chrome" />
                <el-option label="Firefox" value="firefox" />
                <el-option label="Edge" value="edge" />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="8">
            <el-form-item label="窗口大小" prop="viewport">
              <el-select v-model="useCaseForm.viewport" placeholder="请选择窗口大小">
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
                v-model="useCaseForm.headless"
                active-text="无头"
                inactive-text="有头"
              />
            </el-form-item>
          </el-col>
        </el-row>
        <el-form-item label="超时时间 (秒)" prop="timeout">
          <el-input-number v-model="useCaseForm.timeout" :min="5" :max="120" />
        </el-form-item>
        
        <el-divider>测试步骤</el-divider>
        <div class="section-title">
          测试步骤
          <el-button type="primary" size="small" @click="openStepDialog()">
            <el-icon><i-ep-plus /></el-icon>
            添加步骤
          </el-button>
        </div>

        <el-table
          v-if="useCaseForm.steps && useCaseForm.steps.length"
          :data="useCaseForm.steps"
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
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeDialog">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 步骤编辑对话框 -->
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
  </div>
</template>

<script>
import { ref, reactive, onMounted, computed } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

export default {
  name: 'UiUseCases',
  setup() {
    const projects = ref([])
    const useCases = ref([])
    const selectedProject = ref('')
    const selectedUseCases = ref([])
    const dialogVisible = ref(false)
    const dialogTitle = ref('添加用例')
    // 操作选项定义
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

    const useCaseForm = reactive({
      id: null,
      projectId: '',
      name: '',
      description: '',
      url: '',
      browser: 'chrome',
      viewport: '1920x1080',
      headless: true,
      timeout: 30,
      steps: []
    })

    // 步骤编辑相关状态
    const stepDialogVisible = ref(false)
    const stepFormRef = ref(null)
    const currentStepIndex = ref(-1)
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
            const action = ACTION_OPTIONS[stepForm.action]
            if (action?.requiresLocator !== false) {
              if (!value) return callback(new Error('请选择定位方式'))
            }
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
            const action = ACTION_OPTIONS[stepForm.action]
            if (action?.requiresLocator !== false) {
              if (!value?.trim()) return callback(new Error('请输入定位值'))
            }
            callback()
          }
        }
      ]
    }

    // 计算属性
    const actionOptionsList = computed(() => Object.values(ACTION_OPTIONS))
    const showLocator = computed(() => ACTION_OPTIONS[stepForm.action]?.requiresLocator !== false)
    const showActionValue = computed(() => ACTION_OPTIONS[stepForm.action]?.requiresValue)
    const showAssertion = computed(() => ACTION_OPTIONS[stepForm.action]?.supportsAssertion)
    const showCodeEditor = computed(() => ACTION_OPTIONS[stepForm.action]?.requiresCode)
    const actionValuePlaceholder = computed(() => ACTION_OPTIONS[stepForm.action]?.valuePlaceholder || '请输入参数')
    const useCaseFormRef = ref(null)
    const rules = {
      name: [{ required: true, message: '请输入用例名称', trigger: 'blur' }],
      projectId: [{ required: true, message: '请选择所属项目', trigger: 'change' }],
      url: [{ required: true, message: '请输入目标URL', trigger: 'blur' }],
      browser: [{ required: true, message: '请选择浏览器', trigger: 'change' }],
      viewport: [{ required: true, message: '请选择窗口大小', trigger: 'change' }],
      timeout: [{ required: true, message: '请输入超时时间', trigger: 'change' }]
    }

    // 获取项目列表
    const fetchProjects = async () => {
      try {
        const response = await axios.get('/ui-projects')
        projects.value = response.data
        if (projects.value.length > 0 && !selectedProject.value) {
          selectedProject.value = projects.value[0].id
          fetchUseCases()
        }
      } catch (error) {
        console.error('获取项目列表失败:', error)
        ElMessage.error('获取项目列表失败')
      }
    }

    // 获取用例列表
    const fetchUseCases = async () => {
      if (!selectedProject.value) return
      try {
        const response = await axios.get(`/ui-use-cases?pid=${selectedProject.value}`)
        useCases.value = response.data
      } catch (error) {
        console.error('获取用例列表失败:', error)
        ElMessage.error('获取用例列表失败')
      }
    }

    // 监听项目选择变化
    const handleProjectChange = () => {
      fetchUseCases()
    }

    // 处理选择变化
    const handleSelectionChange = (selection) => {
      selectedUseCases.value = selection
    }

    // 显示添加对话框
    const showAddDialog = () => {
      dialogTitle.value = '添加用例'
      Object.assign(useCaseForm, {
        id: null,
        projectId: selectedProject.value,
        name: '',
        description: '',
        url: '',
        browser: 'chrome',
        viewport: '1920x1080',
        headless: true,
        timeout: 30,
        steps: []
      })
      dialogVisible.value = true
    }

    // 打开步骤对话框
    const openStepDialog = (index = -1) => {
      currentStepIndex.value = index
      if (index !== -1) {
        Object.assign(stepForm, useCaseForm.steps[index])
      } else {
        resetStepForm()
      }
      stepDialogVisible.value = true
    }

    // 关闭步骤对话框
    const closeStepDialog = () => {
      stepDialogVisible.value = false
      resetStepForm()
    }

    // 重置步骤表单
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

    // 处理操作类型变化
    const handleActionChange = () => {
      const action = ACTION_OPTIONS[stepForm.action]
      if (action?.requiresLocator === false) {
        stepForm.locatorType = ''
        stepForm.locatorValue = ''
      }
      if (!action?.requiresValue) {
        stepForm.actionValue = ''
      }
      if (!action?.requiresCode) {
        stepForm.customCode = ''
      }
      if (!action?.supportsAssertion) {
        stepForm.useAssertion = false
      }
    }

    // 确认步骤
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
          useCaseForm.steps.push(step)
          ElMessage.success('步骤已添加')
        } else {
          useCaseForm.steps[currentStepIndex.value] = step
          ElMessage.success('步骤已更新')
        }
        closeStepDialog()
      })
    }

    // 删除步骤
    const removeStep = (index) => {
      useCaseForm.steps.splice(index, 1)
      ElMessage.success('步骤已删除')
    }

    // 获取操作标签类型
    const getActionTag = (action) => ACTION_OPTIONS[action]?.tag || 'info'

    // 断言文本格式化
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

    // 编辑用例
    const editUseCase = async (row) => {
      try {
        dialogTitle.value = '编辑用例'
        Object.assign(useCaseForm, {
          ...row,
          steps: row.steps || []
        })
        dialogVisible.value = true
      } catch (error) {
        console.error('获取用例详情失败:', error)
        ElMessage.error('获取用例详情失败，请稍后重试')
      }
    }

    // 关闭对话框
    const closeDialog = () => {
      dialogVisible.value = false
      if (useCaseFormRef.value) {
        useCaseFormRef.value.resetFields()
      }
    }

    // 提交表单
    const submitForm = async () => {
      if (!useCaseFormRef.value) return
      await useCaseFormRef.value.validate(async (valid) => {
        if (valid) {
          try {
            const formData = { ...useCaseForm }
            const project = projects.value.find(p => p.id === formData.projectId)
            formData.projectName = project?.name || ''

            if (formData.id) {
              // 更新用例
              await axios.put(`/ui-use-cases/${formData.id}`, formData)
              ElMessage.success('更新用例成功')
            } else {
              // 添加用例
              await axios.post('/ui-use-cases', formData)
              ElMessage.success('添加用例成功')
            }
            closeDialog()
            fetchUseCases()
          } catch (error) {
            console.error('保存用例失败:', error)
            ElMessage.error('保存用例失败')
          }
        }
      })
    }

    // 删除用例
    const deleteUseCase = async (id) => {
      try {
        await ElMessageBox.confirm('确定要删除该用例吗？', '警告', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        await axios.delete(`/ui-use-cases/${id}`)
        ElMessage.success('删除用例成功')
        fetchUseCases()
      } catch (error) {
        if (error !== 'cancel') {
          console.error('删除用例失败:', error)
          ElMessage.error('删除用例失败')
        }
      }
    }

    // 初始化
    onMounted(() => {
      fetchProjects()
    })

    return {
      // 项目和用例相关
      projects,
      useCases,
      selectedProject,
      selectedUseCases,
      dialogVisible,
      dialogTitle,
      useCaseForm,
      useCaseFormRef,
      rules,
      showAddDialog,
      editUseCase,
      closeDialog,
      submitForm,
      deleteUseCase,
      handleProjectChange,
      handleSelectionChange,
      // 步骤编辑相关
      stepDialogVisible,
      stepForm,
      stepFormRef,
      stepRules,
      currentStepIndex,
      actionOptions: ACTION_OPTIONS,
      actionOptionsList,
      showLocator,
      showActionValue,
      showAssertion,
      showCodeEditor,
      actionValuePlaceholder,
      openStepDialog,
      closeStepDialog,
      confirmStep,
      removeStep,
      handleActionChange,
      getActionTag,
      assertionText
    }
  }
}
</script>

<style scoped>
.ui-use-cases-container {
  padding: 20px;
}

.ui-use-case-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  align-items: center;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
}

.action-buttons {
  display: flex;
  gap: 16px;
  justify-content: center;
  margin: 24px 0;
}
</style>
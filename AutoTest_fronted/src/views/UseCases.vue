<template>
  <div class="use-cases-container">
    <el-card class="use-case-card">
      <template #header>
        <div class="card-header">
          <span>用例列表</span>
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
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="用例名称" />
        <el-table-column prop="url" label="URL" width="300" />
        <el-table-column prop="method" label="请求方法" width="100">
          <template #default="{ row }">
            <el-tag :type="getMethodTagType(row.method)">{{ row.method }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="描述" />
        <el-table-column label="操作" width="150" fixed="right">
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
      width="600px"
      @close="closeDialog"
    >
      <el-form :model="useCaseForm" :rules="rules" ref="useCaseFormRef">
        <el-form-item label="所属项目" prop="pid">
          <el-select v-model="useCaseForm.pid" placeholder="请选择项目">
            <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="用例名称" prop="name">
          <el-input v-model="useCaseForm.name" placeholder="请输入用例名称" />
        </el-form-item>
        <el-form-item label="请求URL" prop="url">
          <el-input v-model="useCaseForm.url" placeholder="请输入请求URL" />
        </el-form-item>
        <el-form-item label="请求方法" prop="method">
          <el-select v-model="useCaseForm.method">
            <el-option label="GET" value="GET" />
            <el-option label="POST" value="POST" />
            <el-option label="PUT" value="PUT" />
            <el-option label="DELETE" value="DELETE" />
            <el-option label="PATCH" value="PATCH" />
          </el-select>
        </el-form-item>
        <el-form-item label="请求头" prop="header">
          <el-input v-model="useCaseForm.header" type="textarea" :rows="4" placeholder="JSON格式请求头" />
        </el-form-item>
        <el-form-item label="请求体" prop="param">
          <el-input v-model="useCaseForm.param" type="textarea" :rows="6" placeholder="JSON格式请求体" />
        </el-form-item>
        <el-form-item label="断言" prop="assertStr">
          <el-input v-model="useCaseForm.assertStr" type="textarea" :rows="4" placeholder="JSON格式断言" />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="useCaseForm.description" placeholder="请输入用例描述" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="closeDialog">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 已移除执行结果对话框，执行功能移至批量执行页面 -->
  </div>
</template>

<script>
import { ref, reactive, onMounted, computed } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

export default {
  name: 'UseCases',
  setup() {
    const projects = ref([])
    const useCases = ref([])
    const selectedProject = ref('')
    const dialogVisible = ref(false)
    const dialogTitle = ref('添加用例')
    const useCaseForm = reactive({
      id: null,
      pid: '',
      name: '',
      url: '',
      method: 'GET',
      header: '',
      param: '',
      assertStr: '',
      description: ''
    })
    const useCaseFormRef = ref(null)
    const rules = {
      pid: [
        { required: true, message: '请选择所属项目', trigger: 'blur' }
      ],
      name: [
        { required: true, message: '请输入用例名称', trigger: 'blur' }
      ],
      url: [
        { required: true, message: '请输入请求URL', trigger: 'blur' }
      ],
      method: [
        { required: true, message: '请选择请求方法', trigger: 'blur' }
      ]
    }

    // 获取项目列表
    const fetchProjects = async () => {
      try {
        const response = await axios.get('/projects')
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
        // 先清空数组，确保视图更新
        useCases.value = []
        const response = await axios.get(`/use-cases?pid=${selectedProject.value}`)
        // 使用新数组替换，确保Vue能检测到变化
        useCases.value = [...response.data]
        console.log('切换项目后加载的用例数据:', useCases.value)
      } catch (error) {
        console.error('获取用例列表失败:', error)
        ElMessage.error('获取用例列表失败')
      }
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

    // 显示添加对话框
    const showAddDialog = () => {
      dialogTitle.value = '添加用例'
      Object.keys(useCaseForm).forEach(key => {
        useCaseForm[key] = ''
      })
      useCaseForm.id = null
      useCaseForm.pid = selectedProject.value
      useCaseForm.method = 'GET'
      dialogVisible.value = true
    }

    // 编辑用例
    const editUseCase = async (row) => {
      try {
        dialogTitle.value = '编辑用例'
        // 根据ID查询用例详情，确保展示最新数据
        const response = await axios.get(`/use-cases/${row.id}`)
        Object.assign(useCaseForm, response.data)
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
            // 确保method字段始终为大写
            formData.method = formData.method.toUpperCase();
            // 确保JSON字段格式正确
            const fields = ['header', 'param', 'assertStr'];
            fields.forEach(field => {
              if (formData[field] && formData[field].trim()) {
                try {
                  JSON.parse(formData[field])
                } catch (e) {
                  throw new Error(`${field} 必须是有效的JSON格式`)
                }
              }
            })

            if (formData.id) {
              // 更新用例
              await axios.put(`/use-cases/${formData.id}`, formData)
              ElMessage.success('更新用例成功')
            } else {
              // 添加用例
              await axios.post('/use-cases', formData)
              ElMessage.success('添加用例成功')
            }
            closeDialog()
            fetchUseCases()
          } catch (error) {
            console.error('保存用例失败:', error)
            ElMessage.error(error.message || '保存用例失败')
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
        await axios.delete(`/use-cases/${id}`)
        ElMessage.success('删除用例成功')
        fetchUseCases()
      } catch (error) {
        if (error !== 'cancel') {
          console.error('删除用例失败:', error)
          ElMessage.error('删除用例失败')
        }
      }
    }

    // 执行功能已移至批量执行页面

    // 监听项目选择变化
    const handleProjectChange = () => {
      fetchUseCases()
    }

    // 初始化
    onMounted(() => {
      fetchProjects()
    })

    return {
      projects,
      useCases,
      selectedProject,
      dialogVisible,
      dialogTitle,
      useCaseForm,
      useCaseFormRef,
      rules,
      getMethodTagType,
      showAddDialog,
      editUseCase,
      closeDialog,
      submitForm,
      deleteUseCase,
      handleProjectChange
    }
  }
}
</script>

<style scoped>
.use-cases-container {
  padding: 20px;
}

.use-case-card {
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

.result-content,
.assert-content {
  max-height: 400px;
  overflow-y: auto;
  background-color: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
  font-family: 'Monaco', 'Menlo', 'Ubuntu Mono', monospace;
}

pre {
  margin: 0;
  white-space: pre-wrap;
  word-wrap: break-word;
}
</style>
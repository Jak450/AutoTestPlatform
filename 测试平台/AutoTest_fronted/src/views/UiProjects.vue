<template>
  <div class="ui-projects-container">
    <el-card class="ui-project-card">
      <template #header>
        <div class="card-header">
          <span>UI项目列表</span>
          <el-button type="primary" size="small" @click="showAddDialog">
            <el-icon><i-ep-plus /></el-icon>
            添加UI项目
          </el-button>
        </div>
      </template>

      <el-table :data="projects" stripe style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="name" label="项目名称" />
        <el-table-column prop="description" label="项目描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="createTime" label="创建时间" width="180" />
        <el-table-column prop="updateTime" label="更新时间" width="180" />
        <el-table-column label="操作" width="150" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="editProject(row)">
              编辑
            </el-button>
            <el-button type="danger" size="small" @click="deleteProject(row.id)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 添加/编辑项目对话框 -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogTitle"
      width="400px"
      @close="closeDialog"
    >
      <el-form :model="projectForm" :rules="rules" ref="projectFormRef">
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
        <span class="dialog-footer">
          <el-button @click="closeDialog">取消</el-button>
          <el-button type="primary" @click="submitForm">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted, reactive } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

export default {
  name: 'UiProjects',
  setup() {
    const projects = ref([])
    const dialogVisible = ref(false)
    const dialogTitle = ref('添加UI项目')
    const projectForm = reactive({
      id: null,
      name: '',
      description: ''
    })
    const projectFormRef = ref(null)
    const rules = {
      name: [
        { required: true, message: '请输入项目名称', trigger: 'blur' },
        { min: 1, max: 50, message: '项目名称长度在 1 到 50 个字符', trigger: 'blur' }
      ]
    }

    // 获取项目列表
    const fetchProjects = async () => {
      try {
        const response = await axios.get('/ui-projects')
        projects.value = response.data
      } catch (error) {
        console.error('获取UI项目列表失败:', error)
        ElMessage.error('获取UI项目列表失败')
      }
    }

    // 显示添加对话框
    const showAddDialog = () => {
      dialogTitle.value = '添加UI项目'
      projectForm.id = null
      projectForm.name = ''
      projectForm.description = ''
      dialogVisible.value = true
    }

    // 编辑项目
    const editProject = (row) => {
      dialogTitle.value = '编辑UI项目'
      projectForm.id = row.id
      projectForm.name = row.name
      projectForm.description = row.description
      dialogVisible.value = true
    }

    // 关闭对话框
    const closeDialog = () => {
      dialogVisible.value = false
      if (projectFormRef.value) {
        projectFormRef.value.resetFields()
      }
    }

    // 提交表单
    const submitForm = async () => {
      if (!projectFormRef.value) return
      await projectFormRef.value.validate(async (valid) => {
        if (valid) {
          try {
            if (projectForm.id) {
              // 更新项目
              await axios.put(`/ui-projects/${projectForm.id}`, projectForm)
              ElMessage.success('更新UI项目成功')
            } else {
              // 添加项目
              await axios.post('/ui-projects', projectForm)
              ElMessage.success('添加UI项目成功')
            }
            closeDialog()
            fetchProjects()
          } catch (error) {
            console.error('保存UI项目失败:', error)
            ElMessage.error('保存UI项目失败')
          }
        }
      })
    }

    // 删除项目
    const deleteProject = async (id) => {
      try {
        await ElMessageBox.confirm('确定要删除该UI项目吗？', '警告', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        await axios.delete(`/ui-projects/${id}`)
        ElMessage.success('删除UI项目成功')
        fetchProjects()
      } catch (error) {
        // 用户取消操作不显示错误
        if (error !== 'cancel') {
          console.error('删除UI项目失败:', error)
          ElMessage.error('删除UI项目失败')
        }
      }
    }

    // 初始化
    onMounted(() => {
      fetchProjects()
    })

    return {
      projects,
      dialogVisible,
      dialogTitle,
      projectForm,
      projectFormRef,
      rules,
      showAddDialog,
      editProject,
      closeDialog,
      submitForm,
      deleteProject
    }
  }
}
</script>

<style scoped>
.ui-projects-container {
  padding: 20px;
}

.ui-project-card {
  margin-bottom: 20px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
}
</style>
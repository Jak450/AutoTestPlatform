<template>
  <div class="ui-projects-page">
    <PageHeader title="UI 项目管理" description="UI 自动化测试项目">
      <el-button type="primary" @click="showAddDialog">新建 UI 项目</el-button>
    </PageHeader>

    <div class="project-grid">
      <div v-for="p in projects" :key="p.id" class="project-card">
        <div class="project-name">{{ p.name }}</div>
        <div class="project-desc">{{ p.description || '暂无描述' }}</div>
        <div class="project-meta mono">ID {{ p.id }} · {{ p.createTime }}</div>
        <div class="project-actions">
          <el-button size="small" @click="editProject(p)">编辑</el-button>
          <el-button size="small" type="danger" plain @click="deleteProject(p.id)">删除</el-button>
        </div>
      </div>
      <EmptyState v-if="!projects.length" title="还没有 UI 项目" hint="点击右上角新建第一个项目" />
    </div>

    <!-- 添加/编辑项目对话框 -->
    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="400px" @close="closeDialog">
      <el-form :model="projectForm" :rules="rules" ref="projectFormRef">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="projectForm.name" placeholder="请输入项目名称" />
        </el-form-item>
        <el-form-item label="项目描述">
          <el-input v-model="projectForm.description" type="textarea" :rows="3" placeholder="请输入项目描述" />
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
import PageHeader from '../components/ui/PageHeader.vue'
import EmptyState from '../components/ui/EmptyState.vue'

export default {
  name: 'UiProjects',
  components: { PageHeader, EmptyState },
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

    const fetchProjects = async () => {
      try {
        const response = await axios.get('/ui-projects')
        projects.value = response.data
      } catch (error) {
        console.error('获取UI项目列表失败:', error)
        ElMessage.error('获取UI项目列表失败')
      }
    }

    const showAddDialog = () => {
      dialogTitle.value = '添加UI项目'
      projectForm.id = null
      projectForm.name = ''
      projectForm.description = ''
      dialogVisible.value = true
    }

    const editProject = (row) => {
      dialogTitle.value = '编辑UI项目'
      projectForm.id = row.id
      projectForm.name = row.name
      projectForm.description = row.description
      dialogVisible.value = true
    }

    const closeDialog = () => {
      dialogVisible.value = false
      if (projectFormRef.value) {
        projectFormRef.value.resetFields()
      }
    }

    const submitForm = async () => {
      if (!projectFormRef.value) return
      await projectFormRef.value.validate(async (valid) => {
        if (valid) {
          try {
            if (projectForm.id) {
              await axios.put(`/ui-projects/${projectForm.id}`, projectForm)
              ElMessage.success('更新UI项目成功')
            } else {
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

    const deleteProject = async (id) => {
      try {
        await ElMessageBox.confirm('确定要删除该项目吗？', '警告', {
          confirmButtonText: '确定',
          cancelButtonText: '取消',
          type: 'warning'
        })
        await axios.delete(`/ui-projects/${id}`)
        ElMessage.success('删除项目成功')
        fetchProjects()
      } catch (error) {
        if (error !== 'cancel') {
          console.error('删除UI项目失败:', error)
          ElMessage.error('删除UI项目失败')
        }
      }
    }

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
.project-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(240px, 1fr));
  gap: 16px;
}

.project-card {
  background: var(--panel);
  border: 1px solid var(--line);
  border-radius: var(--radius-card);
  box-shadow: var(--shadow-panel);
  padding: 18px;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.project-card:hover {
  transform: translateY(-1px);
  box-shadow: 0 4px 14px rgba(23, 35, 59, 0.1);
}

.project-name {
  font-family: var(--font-display);
  font-weight: 600;
  font-size: 16px;
  color: var(--ink-strong);
}

.project-desc {
  margin-top: 6px;
  font-size: 13px;
  color: var(--ink-body);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-meta {
  margin-top: 8px;
  font-size: 12px;
  color: var(--ink-muted);
}

.project-actions {
  margin-top: 14px;
  display: flex;
  gap: 8px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
}
</style>

<template>
  <div class="admin-page">
    <PageHeader title="系统管理" description="工具、技能与模板的启停管理" />

    <el-card class="admin-card">
      <template #header>
        <div class="card-header"><span>工具</span></div>
      </template>
      <el-table :data="tools" style="width: 100%">
        <el-table-column prop="name" label="工具名" width="200">
          <template #default="{ row }"><span class="mono">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="label" label="显示名" width="140" />
        <el-table-column prop="category" label="分类" width="100" />
        <el-table-column prop="permission" label="权限" width="120">
          <template #default="{ row }"><span class="mono">{{ row.permission }}</span></template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="(v) => toggleTool(row, v)" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="admin-card">
      <template #header>
        <div class="card-header"><span>技能</span><el-button size="small" type="primary" plain @click="openSkillDialog">新建技能</el-button></div>
      </template>
      <el-table :data="skills" style="width: 100%">
        <el-table-column prop="name" label="技能名" width="220">
          <template #default="{ row }"><span class="mono">{{ row.name }}</span></template>
        </el-table-column>
        <el-table-column prop="description" label="描述" min-width="260" show-overflow-tooltip />
        <el-table-column prop="version" label="版本" width="90" />
        <el-table-column label="启用" width="90">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled" @change="(v) => toggleSkill(row, v)" />
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-card class="admin-card">
      <template #header>
        <div class="card-header"><span>模板（全量）</span><el-button size="small" type="primary" plain @click="openTemplateDialog">新建模板</el-button></div>
      </template>
      <el-table :data="templates" style="width: 100%">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="userId" label="用户ID" width="100" />
        <el-table-column prop="name" label="名称" min-width="180" />
        <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
        <el-table-column prop="updatedAt" label="更新时间" width="180" />
        <el-table-column label="操作" width="90" fixed="right">
          <template #default="{ row }">
            <el-button size="small" type="danger" plain @click="deleteTemplate(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <el-dialog v-model="skillDialog" title="新建技能" width="640px" top="8vh">
      <el-form :model="skillForm" label-width="90px">
        <el-form-item label="技能名" required>
          <el-input v-model="skillForm.name" placeholder="如 my-test-guide（字母/数字/-/_，1-64位）" />
        </el-form-item>
        <el-form-item label="描述" required>
          <el-input v-model="skillForm.description" placeholder="技能用途说明（会展示在列表与对话中）" />
        </el-form-item>
        <el-form-item label="正文" required>
          <el-input v-model="skillForm.content" type="textarea" :rows="10"
                    placeholder="技能执行规范（Markdown），加载后注入系统提示词" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="skillDialog = false">取消</el-button>
        <el-button type="primary" @click="createSkill">创建</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="templateDialog" title="新建用例模板" width="680px" top="6vh">
      <el-form :model="templateForm" label-width="100px">
        <el-form-item label="名称" required>
          <el-input v-model="templateForm.name" placeholder="模板名称，如 登录接口标准模板" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="templateForm.description" />
        </el-form-item>
        <el-form-item label="caseShape" required>
          <el-input v-model="templateForm.caseShape" type="textarea" :rows="3"
                    placeholder='用例形状 JSON，如 {"method":"POST","header":{...}}' />
        </el-form-item>
        <el-form-item label="覆盖规则">
          <el-input v-model="templateForm.coverageRules" type="textarea" :rows="3"
                    placeholder="需覆盖的场景规则" />
        </el-form-item>
        <el-form-item label="断言规则">
          <el-input v-model="templateForm.assertRules" type="textarea" :rows="3"
                    placeholder="断言要求" />
        </el-form-item>
        <el-form-item label="示例">
          <el-input v-model="templateForm.examples" type="textarea" :rows="4"
                    placeholder="示例用例（JSON 或文本）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="templateDialog = false">取消</el-button>
        <el-button type="primary" @click="createTemplate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'
import PageHeader from '../components/ui/PageHeader.vue'

export default {
  name: 'Admin',
  components: { PageHeader },
  setup() {
    const tools = ref([])
    const skills = ref([])
    const templates = ref([])
    const skillDialog = ref(false)
    const skillForm = ref({ name: '', description: '', content: '' })
    const templateDialog = ref(false)
    const templateForm = ref({
      name: '',
      description: '',
      caseShape: '{}',
      coverageRules: '',
      assertRules: '',
      examples: ''
    })

    const load = async () => {
      try {
        const [tr, sr, tpr] = await Promise.all([
          axios.get('/agent/admin/tools'),
          axios.get('/agent/admin/skills'),
          axios.get('/agent/admin/templates')
        ])
        tools.value = tr.data?.data || []
        skills.value = sr.data?.data || []
        templates.value = tpr.data?.data || []
      } catch (e) {
        ElMessage.error((e.response && e.response.data && e.response.data.msg) || '加载管理数据失败')
      }
    }

    const toggleTool = async (row, enabled) => {
      try {
        await axios.post(`/agent/admin/tools/${row.name}/${enabled ? 'enable' : 'disable'}`)
        row.enabled = enabled
        ElMessage.success(`${row.name} 已${enabled ? '启用' : '禁用'}`)
      } catch (e) {
        ElMessage.error((e.response && e.response.data && e.response.data.msg) || '操作失败')
      }
    }

    const toggleSkill = async (row, enabled) => {
      try {
        await axios.post(`/agent/admin/skills/${row.name}/${enabled ? 'enable' : 'disable'}`)
        row.enabled = enabled
        ElMessage.success(`${row.name} 已${enabled ? '启用' : '禁用'}`)
      } catch (e) {
        ElMessage.error((e.response && e.response.data && e.response.data.msg) || '操作失败')
      }
    }

    const deleteTemplate = async (row) => {
      try {
        await ElMessageBox.confirm(`确定删除模板「${row.name}」吗？`, '警告', { type: 'warning' })
        await axios.delete(`/agent/admin/templates/${row.id}`)
        ElMessage.success('模板已删除')
        load()
      } catch (e) {
        if (e !== 'cancel') {
          ElMessage.error((e.response && e.response.data && e.response.data.msg) || '删除失败')
        }
      }
    }

    const openSkillDialog = () => {
      skillForm.value = { name: '', description: '', content: '' }
      skillDialog.value = true
    }

    const createSkill = async () => {
      if (!skillForm.value.name || !skillForm.value.content) {
        ElMessage.warning('请填写技能名与正文')
        return
      }
      try {
        await axios.post('/agent/admin/skills', skillForm.value)
        ElMessage.success('技能已创建')
        skillDialog.value = false
        load()
      } catch (e) {
        ElMessage.error((e.response && e.response.data && e.response.data.msg) || '创建技能失败')
      }
    }

    const openTemplateDialog = () => {
      templateForm.value = {
        name: '', description: '', caseShape: '{}', coverageRules: '', assertRules: '', examples: ''
      }
      templateDialog.value = true
    }

    const createTemplate = async () => {
      if (!templateForm.value.name) {
        ElMessage.warning('请填写模板名称')
        return
      }
      try {
        await axios.post('/agent/templates', templateForm.value)
        ElMessage.success('模板已创建')
        templateDialog.value = false
        load()
      } catch (e) {
        ElMessage.error((e.response && e.response.data && e.response.data.msg) || '创建模板失败')
      }
    }

    onMounted(load)
    return {
      tools, skills, templates,
      toggleTool, toggleSkill, deleteTemplate,
      skillDialog, skillForm, openSkillDialog, createSkill,
      templateDialog, templateForm, openTemplateDialog, createTemplate
    }
  }
}
</script>

<style scoped>
.admin-card {
  margin-bottom: 20px;
}
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>

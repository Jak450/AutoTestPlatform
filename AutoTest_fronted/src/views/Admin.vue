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
        <div class="card-header"><span>技能</span></div>
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
        <div class="card-header"><span>模板（全量）</span></div>
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

    onMounted(load)
    return { tools, skills, templates, toggleTool, toggleSkill, deleteTemplate }
  }
}
</script>

<style scoped>
.admin-card {
  margin-bottom: 20px;
}
</style>

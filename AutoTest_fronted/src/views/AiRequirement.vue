<template>
  <div class="ai-requirement-container">
    <el-card class="ai-card">
      <template #header>
        <div class="card-header">
          <span>AI 需求分析</span>
          <el-tag type="warning">Beta</el-tag>
        </div>
      </template>

      <el-alert
        title="使用说明"
        type="info"
        :closable="false"
        show-icon
        description="上传需求文档（支持 .md / .pdf / .docx），AI 将自动分析并生成澄清问题，回答完毕后自动生成可执行的 API 测试用例。"
        style="margin-bottom: 20px;"
      />

      <!-- Step 1: 上传需求文档 -->
      <div v-if="step === 1" class="step-section">
        <div class="section-title">第一步：上传需求文档</div>
        <el-form label-width="100px">
          <el-form-item label="选择项目">
            <el-select v-model="projectId" placeholder="请选择目标项目" style="width: 300px;">
              <el-option v-for="p in projects" :key="p.id" :label="p.name" :value="p.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="需求文档">
            <input
              ref="fileInputRef"
              type="file"
              accept=".md,.pdf,.doc,.docx"
              @change="handleFileChange"
              style="display: none;"
            />
            <el-button type="primary" @click="selectFile">
              <el-icon><i-ep-upload /></el-icon>
              选择文件
            </el-button>
            <span v-if="uploadFileName" style="margin-left: 10px; color: #67c23a;">
              <el-icon><i-ep-document /></el-icon>
              {{ uploadFileName }}
            </span>
            <span v-else style="margin-left: 10px; color: #909399; font-size: 12px;">
              支持 Markdown、PDF、Word 格式
            </span>
          </el-form-item>
          <el-form-item>
            <el-button type="success" @click="startAnalysis" :loading="analyzing">
              <el-icon><i-ep-cpu /></el-icon>
              开始分析
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 分析进度 -->
      <div v-if="analyzing" class="progress-section">
        <el-steps :active="progressStep" align-center finish-status="success" process-status="process">
          <el-step title="解析文档" :description="progressDesc.parsing" />
          <el-step title="需求分析" :description="progressDesc.analyzing" />
          <el-step title="生成问题" :description="progressDesc.generating_questions" />
        </el-steps>
        <div v-if="streamContent" class="stream-output">
          <div class="stream-label">{{ streamStageLabel }}</div>
          <pre class="stream-text">{{ streamContent }}</pre>
        </div>
      </div>

      <!-- Step 2: Q&A 交互 -->
      <div v-if="step === 2" class="step-section">
        <div class="section-title">第二步：回答澄清问题</div>
        <el-alert
          title="AI 已分析完需求，请回答以下问题以完善用例信息"
          type="success"
          :closable="false"
          show-icon
          style="margin-bottom: 15px;"
        />
        <div v-for="(q, index) in questions" :key="q.id" class="qa-item">
          <div class="qa-question">
            <el-tag size="small" type="warning">Q{{ index + 1 }}</el-tag>
            <span style="margin-left: 8px;">{{ q.question }}</span>
          </div>
          <div v-if="q.options && q.options.length > 0" class="qa-options">
            <el-radio-group v-model="qaAnswers[index]" @change="qaInputs[index] = qaAnswers[index]">
              <el-radio v-for="opt in q.options" :key="opt" :value="opt" class="qa-radio">
                {{ opt }}
              </el-radio>
            </el-radio-group>
          </div>
          <el-input
            v-model="qaInputs[index]"
            type="textarea"
            :rows="2"
            placeholder="输入你的回答..."
            style="margin-top: 8px;"
          />
        </div>
        <div style="margin-top: 20px; display: flex; gap: 10px;">
          <el-button type="primary" @click="submitAnswers" :loading="submitting">
            提交回答
          </el-button>
          <el-button v-if="canGenerate" type="success" @click="generateCases" :loading="generating">
            <el-icon><i-ep-document /></el-icon>
            直接生成用例
          </el-button>
        </div>
      </div>

      <!-- Step 3: 结果展示 -->
      <div v-if="step === 3" class="step-section">
        <div class="section-title">第三步：生成的测试用例</div>
        <el-alert
          :title="`成功生成 ${caseCount} 个测试用例`"
          type="success"
          :closable="false"
          show-icon
          style="margin-bottom: 15px;"
        />
        <el-table :data="generatedCases" stripe style="width: 100%;" max-height="400">
          <el-table-column prop="name" label="用例名称" min-width="180" />
          <el-table-column prop="url" label="URL" min-width="250" show-overflow-tooltip />
          <el-table-column prop="method" label="方法" width="90">
            <template #default="{ row }">
              <el-tag :type="methodTag(row.method)" size="small">{{ row.method }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="description" label="描述" min-width="200" show-overflow-tooltip />
        </el-table>
        <div style="margin-top: 15px;">
          <el-button type="success" @click="saveCases" :loading="saving">
            <el-icon><i-ep-check /></el-icon>
            导入到用例管理
          </el-button>
          <el-button @click="reset">重新开始</el-button>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import axios from 'axios'

export default {
  name: 'AiRequirement',
  setup() {
    const step = ref(1)
    const projects = ref([])
    const projectId = ref(null)
    const uploadFile = ref(null)
    const uploadFileName = ref('')
    const fileInputRef = ref(null)
    const analyzing = ref(false)
    const submitting = ref(false)
    const generating = ref(false)
    const saving = ref(false)
    const sessionId = ref('')
    const questions = ref([])
    const qaAnswers = ref([])
    const qaInputs = ref([])
    const canGenerate = ref(false)
    const generatedCases = ref([])
    const caseCount = ref(0)

    const progressStep = ref(0)
    const progressDesc = ref({ parsing: '', analyzing: '', generating_questions: '' })
    const streamContent = ref('')
    const streamStageLabel = ref('')
    const currentStage = ref('')

    const stageLabels = {
      parsing: '文档解析中...',
      analyzing: '需求分析中...',
      generating_questions: '生成澄清问题中...'
    }

    onMounted(async () => {
      try {
        const res = await axios.get('/projects')
        projects.value = res.data
      } catch (_) {}
    })

    const handleFileChange = (e) => {
      const file = e.target.files[0]
      if (file) {
        uploadFile.value = file
        uploadFileName.value = file.name
      }
    }

    const selectFile = () => {
      fileInputRef.value?.click()
    }

    const startAnalysis = async () => {
      if (!uploadFile.value) {
        ElMessage.warning('请先选择需求文档')
        return
      }
      if (!projectId.value) {
        ElMessage.warning('请选择目标项目')
        return
      }
      analyzing.value = true
      progressStep.value = 0
      streamContent.value = ''
      progressDesc.value = { parsing: '', analyzing: '', generating_questions: '' }

      try {
        const text = await uploadFile.value.text()
        const response = await fetch('/api/ai/analyze-requirement-stream', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({
            fileName: uploadFile.value.name,
            content: text,
            projectId: projectId.value
          })
        })

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        while (true) {
          const { done, value } = await reader.read()
          if (done) break

          buffer += decoder.decode(value, { stream: true })
          const lines = buffer.split('\n')
          buffer = lines.pop() || ''

          for (const line of lines) {
            if (line.startsWith('data:')) {
              const data = line.substring(5).trim()
              try {
                const event = JSON.parse(data)
                handleStreamEvent(event)
              } catch (_) {}
            }
          }
        }
      } catch (e) {
        ElMessage.error('分析失败: ' + e.message)
      } finally {
        analyzing.value = false
      }
    }

    const handleStreamEvent = (event) => {
      switch (event.type) {
        case 'stage':
          currentStage.value = event.stage
          streamStageLabel.value = stageLabels[event.stage] || event.message
          streamContent.value = ''

          if (event.stage === 'parsing') {
            progressStep.value = 0
            progressDesc.value.parsing = '进行中...'
          } else if (event.stage === 'analyzing') {
            progressStep.value = 1
            progressDesc.value.parsing = '完成'
            progressDesc.value.analyzing = '进行中...'
          } else if (event.stage === 'generating_questions') {
            progressStep.value = 2
            progressDesc.value.analyzing = '完成'
            progressDesc.value.generating_questions = '进行中...'
          }
          break

        case 'token':
          streamContent.value += event.content
          break

        case 'done':
          progressStep.value = 3
          progressDesc.value.generating_questions = '完成'
          questions.value = event.questions || []
          canGenerate.value = event.canGenerate || false
          qaAnswers.value = []
          qaInputs.value = []
          step.value = 2
          if (!canGenerate.value && (!questions.value || questions.value.length === 0)) {
            ElMessage.warning('AI 分析完成，但未生成澄清问题，可能需求信息不足')
          }
          break

        case 'session':
          sessionId.value = event.sessionId
          break

        case 'error':
          ElMessage.error('分析失败: ' + event.message)
          break
      }
    }

    const submitAnswers = async () => {
      const answers = questions.value.map((q, i) => ({
        question: q.question,
        answer: qaInputs.value[i] || qaAnswers.value[i] || ''
      }))
      if (answers.some(a => !a.answer.trim())) {
        ElMessage.warning('请回答所有问题')
        return
      }
      submitting.value = true
      try {
        const res = await axios.post('/ai/submit-answers?sessionId=' + sessionId.value, answers)
        const data = res.data.data
        questions.value = data.questions || []
        canGenerate.value = data.canGenerate || false
        qaAnswers.value = []
        qaInputs.value = []
        if (canGenerate.value && (!questions.value || questions.value.length === 0)) {
          ElMessage.success('所有信息已完善，可以生成用例了')
        }
      } catch (e) {
        ElMessage.error('提交失败: ' + (e.response?.data?.msg || e.message))
      } finally {
        submitting.value = false
      }
    }

    const generateCases = async () => {
      generating.value = true
      try {
        const res = await axios.post('/ai/generate-cases?sessionId=' + sessionId.value)
        const data = typeof res.data.data === 'string' ? JSON.parse(res.data.data) : res.data.data
        generatedCases.value = data.cases || []
        caseCount.value = generatedCases.value.length
        step.value = 3
      } catch (e) {
        ElMessage.error('生成用例失败: ' + (e.response?.data?.msg || e.message))
      } finally {
        generating.value = false
      }
    }

    const saveCases = async () => {
      saving.value = true
      try {
        let success = 0
        for (const c of generatedCases.value) {
          await axios.post('/use-cases', c)
          success++
        }
        ElMessage.success(`成功导入 ${success} 个用例到项目`)
      } catch (e) {
        ElMessage.error('导入失败: ' + (e.response?.data?.msg || e.message))
      } finally {
        saving.value = false
      }
    }

    const reset = () => {
      step.value = 1
      sessionId.value = ''
      questions.value = []
      generatedCases.value = []
      uploadFile.value = null
      canGenerate.value = false
      progressStep.value = 0
      streamContent.value = ''
      progressDesc.value = { parsing: '', analyzing: '', generating_questions: '' }
    }

    const methodTag = (m) => {
      const map = { GET: '', POST: 'success', PUT: 'warning', DELETE: 'danger', PATCH: 'info' }
      return map[m] || ''
    }

    return {
      step, projects, projectId, uploadFile, uploadFileName, fileInputRef, analyzing, submitting, generating, saving,
      sessionId, questions, qaAnswers, qaInputs, canGenerate, generatedCases, caseCount,
      progressStep, progressDesc, streamContent, streamStageLabel,
      handleFileChange, startAnalysis, submitAnswers, generateCases, saveCases, reset, methodTag, selectFile
    }
  }
}
</script>

<style scoped>
.ai-requirement-container { padding: 10px; }
.ai-card { max-width: 900px; margin: 0 auto; }
.card-header { display: flex; align-items: center; gap: 10px; }
.section-title { font-size: 16px; font-weight: 600; margin-bottom: 15px; color: #303133; }
.step-section { padding: 10px 0; }
.upload-tip { color: #909399; font-size: 12px; margin-left: 10px; }
.qa-item { background: #f5f7fa; border-radius: 6px; padding: 12px; margin-bottom: 12px; }
.qa-question { font-weight: 500; margin-bottom: 8px; display: flex; align-items: center; }
.qa-options { margin: 8px 0; }
.qa-radio { display: block; margin: 4px 0; }

.progress-section { margin: 20px 0; }
.stream-output { margin-top: 16px; background: #1e1e1e; border-radius: 8px; padding: 12px; }
.stream-label { color: #8b949e; font-size: 12px; margin-bottom: 6px; }
.stream-text { color: #c9d1d9; font-size: 13px; line-height: 1.6; margin: 0; white-space: pre-wrap; word-break: break-all; max-height: 300px; overflow-y: auto; }
</style>

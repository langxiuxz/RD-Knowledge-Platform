<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { listProjects } from '../api/project'
import * as assessment from '../api/assessment'
import { useAuthStore } from '../stores/auth'

const auth = useAuthStore()
const projects = ref([])
const projectId = ref(null)
const quizzes = ref([])
const scenarios = ref([])
const activeAttempt = ref(null)
const activeRun = ref(null)
const busy = ref(false)
const loading = ref(false)
const quizForm = reactive({ title: '', topic: '', knowledgeSnapshotId: null, questionCount: 5, difficulty: 'MEDIUM', types: ['SINGLE', 'SHORT'], passScore: 70 })
const quizAnswers = reactive({})
const drillAction = ref('')
const selectedEvidence = ref([])
const conclusion = reactive({ rootCause: '', recoveryPlan: '', validationPlan: '' })
const manager = computed(() => ['OWNER', 'MAINTAINER'].includes(projects.value.find(row => row.id === projectId.value)?.myRole)
  && projects.value.find(row => row.id === projectId.value)?.status === 'ACTIVE')
const scenarioDialog = ref(false)
const scenarioForm = reactive({
  name: '', description: '', severity: 'P2', knowledgeSnapshotId: null, initialSymptom: '', passScore: 70,
  rubric: JSON.stringify([
    { code: 'TRIAGE', name: '排查与证据', maxScore: 40 },
    { code: 'RECOVERY', name: '恢复方案', maxScore: 30 },
    { code: 'VALIDATION', name: '恢复验证', maxScore: 30 }
  ], null, 2),
  rules: JSON.stringify([
    { code: 'CHECK_METRICS', knowledgeKey: 'service.metrics', title: '检查关键指标', type: 'REQUIRED', points: 40, aliases: ['查看关键指标', '检查指标'] },
    { code: 'SAFE_RECOVERY', knowledgeKey: 'service.recovery', title: '制定安全恢复方案', type: 'OPTIONAL', points: 30, aliases: ['制定恢复方案', '安全恢复'] },
    { code: 'VERIFY_HEALTH', knowledgeKey: 'service.validation', title: '验证服务恢复', type: 'VALIDATION', points: 30, aliases: ['验证服务恢复', '检查恢复状态'] },
    { code: 'BLIND_RESTART', knowledgeKey: 'service.restart.risk', title: '缺少证据直接重启', type: 'RISKY', points: -15, aliases: ['直接重启服务'] }
  ], null, 2),
  evidence: JSON.stringify([
    { code: 'SERVICE_METRIC', type: 'METRIC', title: '服务指标', content: '错误率持续升高，队列延迟同时增长。', unlockRuleCode: 'CHECK_METRICS' }
  ], null, 2)
})

let generation = 0
let disposed = false
const unwrap = response => response.data.data
const reportError = error => ElMessage.error(error.response?.data?.msg || error.message || '操作失败')

async function loadProjectData() {
  const requestId = ++generation
  if (!projectId.value) return
  loading.value = true
  try {
    const [quizResponse, scenarioResponse] = await Promise.all([
      assessment.listProjectQuizzes(projectId.value, { page: 1, pageSize: 50 }),
      assessment.listDrillScenarios(projectId.value)
    ])
    if (disposed || requestId !== generation) return
    quizzes.value = unwrap(quizResponse).rows
    scenarios.value = unwrap(scenarioResponse)
  } catch (error) {
    if (!disposed && requestId === generation) reportError(error)
  } finally {
    if (!disposed && requestId === generation) loading.value = false
  }
}

async function loadProjects() {
  try {
    const response = await listProjects({ page: 1, pageSize: 100, status: 'ACTIVE' })
    projects.value = unwrap(response).rows
    if (!projectId.value && projects.value.length) projectId.value = projects.value[0].id
  } catch (error) { reportError(error) }
}

watch(projectId, () => {
  ++generation
  quizzes.value = []; scenarios.value = []; activeAttempt.value = null; activeRun.value = null; selectedEvidence.value = []
  loadProjectData()
})

async function generateQuiz() {
  busy.value = true
  try {
    const response = await assessment.generateProjectQuiz(projectId.value, { ...quizForm })
    ElMessage.success(`测验草稿已创建：#${unwrap(response).quizId}`)
    await loadProjectData()
  } catch (error) { reportError(error) }
  finally { busy.value = false }
}

async function publishQuiz(quiz) {
  busy.value = true
  try { await assessment.publishProjectQuiz(quiz.id); ElMessage.success('测验已发布'); await loadProjectData() }
  catch (error) { reportError(error) }
  finally { busy.value = false }
}

async function startAttempt(quiz) {
  busy.value = true
  try {
    const response = await assessment.startQuizAttempt(quiz.id)
    activeAttempt.value = unwrap(response)
    Object.keys(quizAnswers).forEach(key => delete quizAnswers[key])
  } catch (error) { reportError(error) }
  finally { busy.value = false }
}

function parseAnswer(raw) {
  const text = String(raw ?? '').trim()
  if (!text) return ''
  try { return JSON.parse(text) } catch { return text }
}

async function submitAttempt() {
  if (!activeAttempt.value) return
  busy.value = true
  try {
    const answers = activeAttempt.value.questions.map(question => ({ questionId: question.id, answer: parseAnswer(quizAnswers[question.id]) }))
    const response = await assessment.submitQuizAttempt(activeAttempt.value.attemptId, { answers })
    activeAttempt.value = unwrap(response)
    ElMessage.success(`作答已提交，状态：${activeAttempt.value.status}`)
  } catch (error) { reportError(error) }
  finally { busy.value = false }
}

async function createScenario() {
  busy.value = true
  try {
    const payload = {
      ...scenarioForm,
      knowledgeSnapshotId: Number(scenarioForm.knowledgeSnapshotId),
      rubric: JSON.parse(scenarioForm.rubric), rules: JSON.parse(scenarioForm.rules), evidence: JSON.parse(scenarioForm.evidence)
    }
    await assessment.createDrillScenario(projectId.value, payload)
    scenarioDialog.value = false
    ElMessage.success('演练草稿已创建')
    await loadProjectData()
  } catch (error) { reportError(error) }
  finally { busy.value = false }
}

async function publishScenario(scenario) {
  busy.value = true
  try { await assessment.publishDrillScenario(scenario.id); ElMessage.success('演练已发布'); await loadProjectData() }
  catch (error) { reportError(error) }
  finally { busy.value = false }
}

async function startRun(scenario) {
  busy.value = true
  try {
    const response = await assessment.startDrillRun(scenario.id)
    activeRun.value = { ...unwrap(response), actions: [], evidence: unwrap(response).evidence || [] }
    selectedEvidence.value = []
    conclusion.rootCause = ''; conclusion.recoveryPlan = ''; conclusion.validationPlan = ''
  } catch (error) { reportError(error) }
  finally { busy.value = false }
}

async function sendAction() {
  if (!activeRun.value || !drillAction.value.trim()) return
  busy.value = true
  try {
    const response = await assessment.submitDrillAction(activeRun.value.runId, {
      clientRequestId: crypto.randomUUID(), actionText: drillAction.value.trim()
    })
    activeRun.value.actions.push({ text: drillAction.value.trim(), ...unwrap(response) })
    const knownEvidence = new Set(activeRun.value.evidence.map(item => item.code))
    activeRun.value.evidence.push(...unwrap(response).unlockedEvidence.filter(item => !knownEvidence.has(item.code)))
    drillAction.value = ''
  } catch (error) { reportError(error) }
  finally { busy.value = false }
}

async function submitRun() {
  if (!activeRun.value) return
  busy.value = true
  try {
    const response = await assessment.submitDrillRun(activeRun.value.runId, {
      ...conclusion, evidenceCodes: [...selectedEvidence.value]
    })
    activeRun.value = unwrap(await assessment.getDrillRun(unwrap(response).runId))
    ElMessage.success(`演练已提交，程序分：${unwrap(response).programScore}`)
  } catch (error) { reportError(error) }
  finally { busy.value = false }
}

onMounted(loadProjects)
onBeforeUnmount(() => { disposed = true; ++generation })
</script>

<template>
  <div class="assessment-page">
    <header class="page-heading">
      <div><h2>项目能力评估</h2><p>Quiz 的确定性评分与 Drill 的规则分由服务端计算。</p></div>
      <el-select v-model="projectId" placeholder="选择项目" class="project-select">
        <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
      </el-select>
    </header>

    <el-tabs v-if="projectId" v-loading="loading">
      <el-tab-pane label="Quiz" name="quiz">
        <section v-if="manager" class="form-band">
          <h3>生成测验草稿</h3>
          <el-form inline @submit.prevent="generateQuiz">
            <el-form-item label="标题"><el-input v-model="quizForm.title" maxlength="128" /></el-form-item>
            <el-form-item label="主题"><el-input v-model="quizForm.topic" maxlength="255" /></el-form-item>
            <el-form-item label="KnowledgeSnapshot"><el-input-number v-model="quizForm.knowledgeSnapshotId" :min="1" /></el-form-item>
            <el-form-item label="题量"><el-input-number v-model="quizForm.questionCount" :min="1" :max="50" /></el-form-item>
            <el-form-item label="难度"><el-select v-model="quizForm.difficulty"><el-option v-for="item in ['EASY','MEDIUM','HARD']" :key="item" :label="item" :value="item" /></el-select></el-form-item>
            <el-button type="primary" :loading="busy" :disabled="!quizForm.title || !quizForm.topic || !quizForm.knowledgeSnapshotId" @click="generateQuiz">生成</el-button>
          </el-form>
        </section>
        <el-table :data="quizzes" row-key="id" empty-text="当前项目暂无测验">
          <el-table-column prop="title" label="测验" min-width="180" />
          <el-table-column prop="topic" label="主题" min-width="140" />
          <el-table-column prop="questionCount" label="题量" width="80" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column label="操作" width="190"><template #default="{ row }">
            <el-button v-if="manager && ['READY','DRAFT'].includes(row.status)" link type="primary" :disabled="busy" @click="publishQuiz(row)">发布</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="primary" :disabled="busy" @click="startAttempt(row)">开始作答</el-button>
          </template></el-table-column>
        </el-table>
        <el-card v-if="activeAttempt" class="work-panel" shadow="never">
          <h3>Attempt #{{ activeAttempt.attemptId }} <el-tag>{{ activeAttempt.status }}</el-tag></h3>
          <el-form v-if="activeAttempt.status === 'IN_PROGRESS'" label-position="top">
            <el-form-item v-for="question in activeAttempt.questions" :key="question.id" :label="question.content">
              <div v-if="question.options" class="options">{{ question.options }}</div>
              <el-input v-model="quizAnswers[question.id]" type="textarea" :rows="2" placeholder="选择题填写选项键；多选填写 JSON 数组；简答题填写文字" />
            </el-form-item>
            <el-button type="primary" :loading="busy" @click="submitAttempt">提交作答</el-button>
          </el-form>
          <p v-else>自动分：{{ activeAttempt.autoScore ?? '待处理' }}；最终分：{{ activeAttempt.finalScore ?? '待复核' }}</p>
        </el-card>
      </el-tab-pane>

      <el-tab-pane label="Drill" name="drill">
        <section class="toolbar"><h3>故障演练</h3><el-button v-if="manager" type="primary" @click="scenarioDialog = true">新建场景</el-button></section>
        <el-table :data="scenarios" row-key="id" empty-text="当前项目暂无演练场景">
          <el-table-column prop="name" label="场景" min-width="180" />
          <el-table-column prop="severity" label="等级" width="90" />
          <el-table-column prop="versionNo" label="版本" width="80" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column label="操作" width="170"><template #default="{ row }">
            <el-button v-if="manager && row.status === 'DRAFT'" link type="primary" :disabled="busy" @click="publishScenario(row)">发布</el-button>
            <el-button v-if="row.status === 'PUBLISHED'" link type="primary" :disabled="busy" @click="startRun(row)">开始演练</el-button>
          </template></el-table-column>
        </el-table>
        <el-card v-if="activeRun" class="work-panel" shadow="never">
          <h3>Drill Run #{{ activeRun.runId }} <el-tag>{{ activeRun.status }}</el-tag></h3>
          <p class="symptom">{{ activeRun.initialSymptom }}</p>
          <el-checkbox-group v-if="activeRun.status === 'IN_PROGRESS'" v-model="selectedEvidence" class="evidence-list">
            <el-checkbox v-for="evidence in activeRun.evidence" :key="evidence.code" :label="evidence.code">
              <strong>{{ evidence.title }}</strong>：{{ evidence.content }}
            </el-checkbox>
          </el-checkbox-group>
          <ul v-else><li v-for="evidence in activeRun.evidence" :key="evidence.code"><strong>{{ evidence.title }}</strong>：{{ evidence.content }}</li></ul>
          <div v-if="activeRun.status === 'IN_PROGRESS'">
            <el-input v-model="drillAction" maxlength="2000" placeholder="描述下一步排查动作" @keyup.enter="sendAction" />
            <el-button :disabled="busy || !drillAction.trim()" @click="sendAction">提交动作</el-button>
            <el-form label-position="top" class="conclusion-form">
              <el-form-item label="根因"><el-input v-model="conclusion.rootCause" type="textarea" /></el-form-item>
              <el-form-item label="恢复方案"><el-input v-model="conclusion.recoveryPlan" type="textarea" /></el-form-item>
              <el-form-item label="验证方案"><el-input v-model="conclusion.validationPlan" type="textarea" /></el-form-item>
              <el-button type="primary" :loading="busy" :disabled="!conclusion.rootCause || !conclusion.recoveryPlan || !conclusion.validationPlan || (activeRun.evidence.length > 0 && selectedEvidence.length === 0)" @click="submitRun">提交结论</el-button>
            </el-form>
          </div>
          <p v-else>程序分：{{ activeRun.programScore }}；候选结果：{{ activeRun.passCandidate ? '达到通过线' : '未达到通过线' }}</p>
        </el-card>
      </el-tab-pane>
    </el-tabs>
    <el-empty v-else description="没有可访问的进行中项目" />

    <el-dialog v-model="scenarioDialog" title="创建 Drill 场景" width="min(760px, 96vw)" :close-on-click-modal="!busy">
      <el-form label-position="top">
        <el-form-item label="场景名称"><el-input v-model="scenarioForm.name" maxlength="128" /></el-form-item>
        <el-form-item label="知识快照 ID"><el-input-number v-model="scenarioForm.knowledgeSnapshotId" :min="1" /></el-form-item>
        <el-form-item label="初始现象"><el-input v-model="scenarioForm.initialSymptom" type="textarea" /></el-form-item>
        <el-form-item label="Rubric JSON（维度满分合计 100）"><el-input v-model="scenarioForm.rubric" type="textarea" :rows="5" /></el-form-item>
        <el-form-item label="Rules JSON（规则和分值由数据库保存）"><el-input v-model="scenarioForm.rules" type="textarea" :rows="8" /></el-form-item>
        <el-form-item label="Evidence JSON"><el-input v-model="scenarioForm.evidence" type="textarea" :rows="5" /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="busy" @click="scenarioDialog = false">取消</el-button><el-button type="primary" :loading="busy" :disabled="!scenarioForm.name || !scenarioForm.knowledgeSnapshotId || !scenarioForm.initialSymptom" @click="createScenario">创建草稿</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.assessment-page{max-width:1280px;margin:0 auto}.page-heading,.toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap;margin-bottom:18px}.page-heading h2,.toolbar h3{margin:0 0 6px}.page-heading p,.symptom{margin:0;color:#606266}.project-select{width:260px}.form-band{padding:16px 0;border-bottom:1px solid #ebeef5;margin-bottom:14px}.form-band h3{font-size:16px}.work-panel{margin-top:20px;border-radius:4px}.work-panel h3{display:flex;align-items:center;gap:10px}.work-panel ul{padding-left:20px}.work-panel li{margin:8px 0;overflow-wrap:anywhere}.options{white-space:pre-wrap;margin-bottom:8px;color:#606266}.evidence-list{display:flex;flex-direction:column;align-items:flex-start;gap:8px;margin:12px 0}.evidence-list .el-checkbox{height:auto;white-space:normal;line-height:1.5;align-items:flex-start}.conclusion-form{margin-top:22px}.el-dialog .el-form{max-height:65vh;overflow:auto;padding-right:12px}@media(max-width:700px){.page-heading{align-items:stretch;flex-direction:column}.project-select{width:100%}}
</style>

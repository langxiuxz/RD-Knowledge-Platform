<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as api from '../api/insight'
import { getQuizAttempt, getDrillRun } from '../api/assessment'
import { getAssignment } from '../api/onboarding'
import { listProjects } from '../api/project'

const projects = ref([])
const projectId = ref(null)
const overview = ref(null)
const assignments = ref([])
const gaps = ref([])
const impacts = ref([])
const pending = ref([])
const loading = ref(false)
const busyKey = ref('')
const reviewTarget = ref(null)
const reviewVisible = ref(false)
const reviewEvidence = ref(null)
const evidenceLoading = ref(false)
const decision = ref('PASS')
const comment = ref('')
const scoreOverride = ref(null)
const report = ref(null)
const reportVisible = ref(false)
const reportLookupId = ref(null)
const contentStatus = ref('NEEDS_REVIEW')
const manager = computed(() => projects.value.some(p => p.id === projectId.value && p.status === 'ACTIVE' && ['OWNER', 'MAINTAINER'].includes(p.myRole)))
let generation = 0
let projectEpoch = 0
let reviewEpoch = 0
let reportEpoch = 0
let pollTimer = null
let disposed = false
const data = response => response.data.data
const showError = error => ElMessage.error(error.response?.data?.msg || error.message || '操作失败')

function stopReport() {
  reportEpoch++
  if (pollTimer) clearTimeout(pollTimer)
  pollTimer = null
}

function closeReview() {
  reviewEpoch++
  reviewVisible.value = false
  reviewTarget.value = null
  reviewEvidence.value = null
  evidenceLoading.value = false
}

function changeProject() {
  projectEpoch++
  generation++
  busyKey.value = ''
  loading.value = false
  overview.value = null
  assignments.value = []; gaps.value = []; impacts.value = []; pending.value = []
  closeReview()
  stopReport()
  reportVisible.value = false
  report.value = null
  if (manager.value) return loadProject()
}

async function loadProject(id = projectId.value) {
  if (!id || !manager.value) return
  const requestId = ++generation
  loading.value = true
  try {
    const [overviewResponse, assignmentResponse, gapResponse, impactResponse, reviewResponse] = await Promise.all([
      api.getInsightOverview(id), api.getInsightAssignments(id), api.getKnowledgeGaps(id),
      api.getContentImpact(id, { status: contentStatus.value }), api.getPendingReviews(id)
    ])
    if (disposed || requestId !== generation) return
    overview.value = data(overviewResponse)
    assignments.value = data(assignmentResponse)
    gaps.value = data(gapResponse).rows || []
    impacts.value = data(impactResponse)
    pending.value = data(reviewResponse)
  } catch (error) {
    if (!disposed && requestId === generation) showError(error)
  } finally {
    if (!disposed && requestId === generation) loading.value = false
  }
}

async function loadProjects() {
  try {
    const response = await listProjects({ page: 1, pageSize: 100, status: 'ACTIVE' })
    if (disposed) return
    projects.value = data(response).rows
    projectId.value = projects.value.find(p => ['OWNER', 'MAINTAINER'].includes(p.myRole))?.id || projects.value[0]?.id || null
  } catch (error) { if (!disposed) showError(error) }
}

async function openReview(row) {
  if (!manager.value || busyKey.value) return
  closeReview()
  const epoch = reviewEpoch
  reviewTarget.value = row
  reviewVisible.value = true
  evidenceLoading.value = true
  decision.value = 'PASS'
  comment.value = ''
  scoreOverride.value = null
  const loaders = { QUIZ_ATTEMPT: getQuizAttempt, DRILL_RUN: getDrillRun, ASSIGNMENT: getAssignment }
  try {
    const result = await loaders[row.targetType](row.targetId)
    if (!disposed && epoch === reviewEpoch) reviewEvidence.value = data(result)
  } catch (error) { if (!disposed && epoch === reviewEpoch) showError(error) }
  finally { if (!disposed && epoch === reviewEpoch) evidenceLoading.value = false }
}

async function submitReview() {
  const target = reviewTarget.value
  if (!manager.value || !target || !reviewEvidence.value || evidenceLoading.value || busyKey.value) return
  const epoch = projectEpoch
  const reviewAtStart = reviewEpoch
  busyKey.value = `${target.targetType}:${target.targetId}`
  const payload = { decision: decision.value, comment: comment.value.trim() || null }
  if (target.targetType !== 'ASSIGNMENT' && scoreOverride.value !== null) payload.scoreOverride = scoreOverride.value
  const handlers = { QUIZ_ATTEMPT: api.reviewQuizAttempt, DRILL_RUN: api.reviewDrillRun, ASSIGNMENT: api.reviewAssignment }
  try {
    await handlers[target.targetType](target.targetId, payload)
    if (disposed || epoch !== projectEpoch || reviewAtStart !== reviewEpoch) return
    closeReview()
    ElMessage.success('复核已保存')
    await loadProject()
  } catch (error) { if (!disposed && epoch === projectEpoch && reviewAtStart === reviewEpoch) showError(error) }
  finally { if (!disposed && epoch === projectEpoch) busyKey.value = '' }
}

async function generateReport(row) {
  if (!manager.value || busyKey.value) return
  stopReport()
  const epoch = reportEpoch
  const projectAtStart = projectEpoch
  busyKey.value = `report:${row.assignmentId}`
  try {
    const response = await api.generateCapabilityReport(row.assignmentId)
    if (disposed || epoch !== reportEpoch) return
    const created = data(response)
    reportLookupId.value = created.reportId
    report.value = null
    reportVisible.value = true
    await pollReport(created.reportId, epoch)
  } catch (error) { if (!disposed && epoch === reportEpoch) showError(error) }
  finally { if (!disposed && projectAtStart === projectEpoch) busyKey.value = '' }
}

async function openReport() {
  if (!Number.isSafeInteger(reportLookupId.value) || reportLookupId.value <= 0) return
  stopReport()
  report.value = null
  reportVisible.value = true
  await pollReport(reportLookupId.value, reportEpoch)
}

async function pollReport(id, epoch, startedAt = Date.now()) {
  try {
    const response = await api.getCapabilityReport(id)
    if (disposed || epoch !== reportEpoch || !reportVisible.value) return
    report.value = data(response)
    if (report.value.status === 'PROCESSING' && Date.now() - startedAt < 120000) {
      pollTimer = setTimeout(() => pollReport(id, epoch, startedAt), 1500)
    } else if (report.value.status === 'PROCESSING') {
      ElMessage.warning('报告仍在生成，可稍后重新打开报告查看')
    }
  } catch (error) { if (!disposed && epoch === reportEpoch && reportVisible.value) showError(error) }
}

const display = value => typeof value === 'string' ? value : JSON.stringify(value)
watch(projectId, changeProject)
watch(reportVisible, visible => { if (!visible) stopReport() })
watch(reviewVisible, visible => { if (!visible) closeReview() })
watch(contentStatus, () => { if (projectId.value) loadProject(projectId.value) })
onMounted(loadProjects)
onBeforeUnmount(() => { disposed = true; generation++; closeReview(); stopReport() })
</script>

<template>
  <section class="insight-page">
    <header class="page-heading">
      <div><h2>项目 Insight</h2></div>
      <el-select v-model="projectId" placeholder="选择项目" class="project-select">
        <el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" />
      </el-select>
    </header>
    <div class="filter-row"><el-input-number v-model="reportLookupId" :min="1" :precision="0" placeholder="报告 ID" aria-label="报告 ID" /><el-button @click="openReport">查看报告</el-button></div>
    <el-empty v-if="!projectId || !manager" :description="!projectId ? '暂无项目' : '团队 Insight 仅负责人和维护者可见'" />
    <template v-else>
      <div v-loading="loading" class="insight-content">
        <div v-if="overview" class="metrics" aria-label="项目总览">
          <div><span>活跃成员</span><strong>{{ overview.activeLearners }}</strong></div>
          <div><span>进行中 Assignment</span><strong>{{ overview.activeAssignments }}</strong></div>
          <div><span>Assignment 完成率</span><strong>{{ Math.round((overview.assignmentCompletionRate || 0) * 100) }}%</strong></div>
          <div><span>Assessment 通过率</span><strong>{{ Math.round((overview.assessmentPassRate || 0) * 100) }}%</strong></div>
          <div><span>待复核</span><strong>{{ overview.pendingAssessmentReviews }}</strong></div>
          <div><span>Knowledge Gap</span><strong>{{ overview.knowledgeGapCount }}</strong></div>
        </div>
        <el-tabs>
          <el-tab-pane label="Assignment 进度">
            <el-table :data="assignments" empty-text="暂无 Assignment">
              <el-table-column prop="assigneeName" label="成员" min-width="130" />
              <el-table-column prop="pathName" label="接手路径" min-width="160" />
              <el-table-column label="完成度" width="180"><template #default="{ row }"><el-progress :percentage="row.requiredTasks ? Math.round(row.completedRequiredTasks * 100 / row.requiredTasks) : 0" :stroke-width="8" /></template></el-table-column>
              <el-table-column prop="status" label="状态" width="140" />
              <el-table-column label="操作" width="150"><template #default="{ row }">
                <el-button v-if="row.status === 'REVIEW_PENDING'" link type="primary" @click="openReview({ targetType: 'ASSIGNMENT', targetId: row.assignmentId, title: row.pathName })">复核</el-button>
                <el-button v-if="['REVIEW_PENDING','PASSED','NEEDS_WORK'].includes(row.status)" link type="primary" :loading="busyKey === `report:${row.assignmentId}`" :disabled="!!busyKey" @click="generateReport(row)">生成报告</el-button>
              </template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="Knowledge Gap">
            <el-table :data="gaps" empty-text="暂无知识薄弱点">
              <el-table-column prop="knowledgeKey" label="Knowledge Key" min-width="200" />
              <el-table-column prop="label" label="主题" min-width="160" />
              <el-table-column prop="participantCount" label="成员数" width="100" />
              <el-table-column label="Quiz 错误率" width="130"><template #default="{ row }">{{ Math.round((row.quizWrongRate || 0) * 100) }}%</template></el-table-column>
              <el-table-column label="Drill 漏项率" width="130"><template #default="{ row }">{{ Math.round((row.drillMissRate || 0) * 100) }}%</template></el-table-column>
              <el-table-column prop="severity" label="风险" width="100" />
              <el-table-column label="关联文档" width="110"><template #default="{ row }">{{ (row.relatedDocumentIds || []).length }}</template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="待复核">
            <el-table :data="pending" empty-text="没有待复核结果">
              <el-table-column prop="userName" label="成员" width="140" />
              <el-table-column prop="targetType" label="类型" width="150" />
              <el-table-column prop="title" label="项目" min-width="180" />
              <el-table-column prop="automaticScore" label="自动/程序分" width="130" />
              <el-table-column prop="submittedAt" label="提交时间" min-width="170" />
              <el-table-column label="操作" width="100"><template #default="{ row }"><el-button link type="primary" @click="openReview(row)">复核</el-button></template></el-table-column>
            </el-table>
          </el-tab-pane>
          <el-tab-pane label="内容风险">
            <div class="filter-row"><span>依赖状态</span><el-select v-model="contentStatus" style="width: 190px"><el-option label="待复查" value="NEEDS_REVIEW"/><el-option label="已确认" value="CONFIRMED"/><el-option label="已解决" value="RESOLVED"/></el-select></div>
            <el-table :data="impacts" empty-text="暂无内容影响记录">
              <el-table-column prop="documentTitle" label="来源文档" min-width="180" />
              <el-table-column prop="documentVersionId" label="版本" width="100" />
              <el-table-column prop="targetType" label="受影响内容" width="150" />
              <el-table-column prop="targetId" label="内容 ID" width="100" />
              <el-table-column prop="status" label="状态" width="140" />
              <el-table-column prop="detectedAt" label="发现时间" min-width="170" />
            </el-table>
          </el-tab-pane>
        </el-tabs>
      </div>
    </template>
    <el-dialog v-model="reviewVisible" title="人工复核" width="min(900px, 94vw)">
      <p>{{ reviewTarget?.title || `${reviewTarget?.targetType} #${reviewTarget?.targetId}` }}</p>
      <el-skeleton v-if="evidenceLoading" :rows="3" animated />
      <template v-else-if="reviewEvidence">
        <div class="report-meta"><span>状态：{{ reviewEvidence.status || reviewEvidence.assignment?.status }}</span><span>Snapshot #{{ reviewEvidence.knowledgeSnapshotId || reviewEvidence.assignment?.knowledgeSnapshotId }}</span><span>自动/程序分：{{ reviewEvidence.autoScore ?? reviewEvidence.programScore ?? '—' }}</span><span>最终分：{{ reviewEvidence.finalScore ?? '—' }}</span></div>
        <el-table v-if="reviewTarget?.targetType === 'QUIZ_ATTEMPT'" :data="reviewEvidence.answers">
          <el-table-column label="题目" min-width="180"><template #default="{ row }">{{ reviewEvidence.questions?.find(q => q.id === row.questionId)?.content || row.questionId }}</template></el-table-column>
          <el-table-column label="作答" min-width="130"><template #default="{ row }">{{ display(row.userAnswer) }}</template></el-table-column>
          <el-table-column prop="ruleScore" label="程序分" width="80"/><el-table-column prop="aiSuggestedScore" label="AI 建议分" width="100"/><el-table-column prop="finalScore" label="最终分" width="80"/><el-table-column prop="feedback" label="反馈" min-width="160"/>
        </el-table>
        <template v-else-if="reviewTarget?.targetType === 'DRILL_RUN'">
          <p>现象：{{ reviewEvidence.initialSymptom }}</p>
          <el-table :data="reviewEvidence.actions"><el-table-column prop="seqNo" label="顺序" width="70"/><el-table-column prop="actionText" label="动作" min-width="180"/><el-table-column prop="normalizedAction" label="规则" min-width="140"/><el-table-column prop="pointsAwarded" label="程序分" width="80"/><el-table-column prop="riskFlag" label="风险动作" width="90"/></el-table>
          <p>结论：{{ display(reviewEvidence.conclusion) }}</p><p>缺失项：{{ (reviewEvidence.missingRequiredActions || []).join('、') || '无' }}</p><p>AI 解释：{{ reviewEvidence.aiExplanation || '无' }}</p>
        </template>
        <el-table v-else :data="reviewEvidence.tasks"><el-table-column prop="title" label="任务" min-width="180"/><el-table-column prop="type" label="类型" width="110"/><el-table-column prop="status" label="状态" width="130"/><el-table-column prop="quizAttemptId" label="QuizAttempt" width="115"/><el-table-column prop="drillRunId" label="DrillRun" width="100"/></el-table>
      </template>
      <el-form label-position="top">
        <el-form-item label="最终决定"><el-radio-group v-model="decision"><el-radio-button value="PASS">PASS</el-radio-button><el-radio-button value="NEEDS_WORK">NEEDS_WORK</el-radio-button></el-radio-group></el-form-item>
        <el-form-item v-if="reviewTarget?.targetType !== 'ASSIGNMENT'" label="分数修正（可选）"><el-input-number v-model="scoreOverride" :min="0" :max="100" :precision="2" controls-position="right" /></el-form-item>
        <el-form-item label="复核意见"><el-input v-model="comment" type="textarea" :rows="4" maxlength="2000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button @click="closeReview">取消</el-button><el-button type="primary" :loading="!!busyKey" :disabled="!reviewEvidence || evidenceLoading" @click="submitReview">提交复核</el-button></template>
    </el-dialog>
    <el-dialog v-model="reportVisible" title="Capability Report" width="min(760px, 96vw)">
      <template v-if="report">
        <div class="report-meta"><el-tag>{{ report.status }}</el-tag><span>决定：{{ report.overallDecision || '待最终复核' }}</span><span>Knowledge Snapshot #{{ report.knowledgeSnapshotId }}</span><span>Path v{{ report.pathVersion }}</span></div>
        <h3>固定评估事实</h3>
        <el-table :data="report.dimensions"><el-table-column prop="code" label="类型" min-width="150"/><el-table-column label="证据" min-width="150"><template #default="{ row }">{{ (row.evidence || []).join(', ') }}</template></el-table-column><el-table-column label="自动/程序分" width="120"><template #default="{ row }">{{ row.automaticScore ?? row.programScore }}</template></el-table-column><el-table-column prop="score" label="最终分" width="90"/></el-table>
        <p>缺失任务：{{ (report.facts?.missingItems || []).join('、') || '无' }}</p>
        <p>事实风险：{{ (report.facts?.riskItems || []).map(item => `${item.type} #${item.attemptId || item.runId}`).join('；') || '无' }}</p>
        <el-table :data="report.facts?.assessmentReviews || []"><el-table-column prop="targetType" label="复核对象" min-width="150"/><el-table-column prop="targetId" label="ID" width="80"/><el-table-column prop="reviewerId" label="复核人" width="90"/><el-table-column prop="decision" label="决定" width="140"/><el-table-column prop="comment" label="意见" min-width="180"/></el-table>
        <el-alert v-if="report.status === 'FAILED'" type="error" :closable="false" :title="report.errorMessage || '报告生成失败'" />
        <el-skeleton v-else-if="report.status === 'PROCESSING'" :rows="3" animated />
        <template v-else><h3>摘要</h3><p>{{ report.summary }}</p><p v-if="report.modelName" class="muted">模型：{{ report.modelName }}</p>
          <h3>优势</h3><ul><li v-for="(item,index) in report.strengths" :key="index">{{ item }}</li></ul>
          <h3>风险</h3><ul><li v-for="(item,index) in report.risks" :key="index">{{ typeof item === 'string' ? item : `${item.type} (${item.count || 1})` }}</li></ul>
          <h3>推荐资料</h3><ul><li v-for="item in report.recommendedSources" :key="item.documentVersionId">{{ item.title }} · Version #{{ item.documentVersionId }}</li></ul>
        </template>
      </template>
    </el-dialog>
  </section>
</template>

<style scoped>
.insight-page{max-width:1320px;margin:0 auto}.page-heading{display:flex;align-items:center;justify-content:space-between;gap:16px;flex-wrap:wrap;margin-bottom:18px}.page-heading h2{margin:0 0 5px;font-size:22px}.page-heading p,.muted{margin:0;color:#777}.project-select{width:260px}.metrics{display:grid;grid-template-columns:repeat(6,minmax(120px,1fr));gap:1px;background:#e7e9ed;border:1px solid #e7e9ed;margin-bottom:18px}.metrics>div{min-height:84px;background:#fff;padding:14px 16px;display:flex;flex-direction:column;justify-content:space-between}.metrics span{color:#73777f;font-size:13px}.metrics strong{font-size:22px;font-weight:600;color:#24282f}.filter-row,.report-meta{display:flex;align-items:center;gap:14px;flex-wrap:wrap;margin-bottom:14px}.report-meta{color:#606266}.report-meta>span{overflow-wrap:anywhere}.el-dialog h3{font-size:16px;margin:20px 0 8px}.el-dialog p,.el-dialog li{overflow-wrap:anywhere;line-height:1.6}.el-dialog ul{padding-left:20px}@media(max-width:900px){.metrics{grid-template-columns:repeat(3,minmax(0,1fr))}}@media(max-width:560px){.metrics{grid-template-columns:repeat(2,minmax(0,1fr))}.project-select{width:100%}}
</style>

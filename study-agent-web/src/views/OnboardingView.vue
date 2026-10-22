<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as api from '../api/onboarding'
import { listProjects, listProjectMembers } from '../api/project'
import { useAuthStore } from '../stores/auth'
import { isPathManager, canCompleteTask, canSubmitAssignment, taskResource } from '../utils/onboarding'

const auth = useAuthStore()
const projects = ref([]), projectId = ref(null), paths = ref([]), assignments = ref([])
const pathTotal = ref(0), assignmentTotal = ref(0), pathPage = ref(1), assignmentPage = ref(1)
const selected = ref(null), execution = ref(null), busy = ref(false), loading = ref(false)
const project = computed(() => projects.value.find(p => p.id === projectId.value))
const manager = computed(() => isPathManager(project.value))
const editable = computed(() => manager.value && selected.value?.path.status === 'DRAFT')
const pathDialog = ref(false), pathEditingId = ref(null)
const pathForm = reactive({ name: '', targetRole: '', description: '', knowledgeSnapshotId: null })
const taskDialog = ref(false), taskEditingId = ref(null)
const taskForm = reactive({ type: 'READ', title: '', description: '', resourceId: null, estimatedMinutes: 30, required: true, sortOrder: 1, prerequisiteTaskIds: [] })
const assignDialog = ref(false), learners = ref([]), learnerIds = ref([]), dueAt = ref(null)
let epoch = 0, pathEpoch = 0, assignmentEpoch = 0, projectScope = 0, disposed = false
const sameProject = (id, scope) => !disposed && id === projectId.value && scope === projectScope
const samePath = (id, scope, token) => sameProject(projectId.value, scope) && token === pathEpoch && id === selected.value?.path.id
const error = (e) => ElMessage.error(e.response?.data?.msg || e.message || '操作失败')
const unwrap = r => r.data.data

async function loadAll(fetch) {
  const rows = []
  for (let page = 1; ; page++) {
    const data = unwrap(await fetch(page))
    rows.push(...data.rows)
    if (rows.length >= data.total || !data.rows.length) return rows
  }
}
async function refresh() {
  const token = ++epoch, id = projectId.value
  if (disposed || !id) { loading.value = false; return }
  loading.value = true
  try {
    const [p, a] = await Promise.all([
      api.listOnboardingPaths(id, { page: pathPage.value, pageSize: 10 }),
      api.listAssignments(id, { page: assignmentPage.value, pageSize: 10 })
    ])
    if (token !== epoch) return
    paths.value = unwrap(p).rows; pathTotal.value = unwrap(p).total
    assignments.value = unwrap(a).rows; assignmentTotal.value = unwrap(a).total
  } catch (e) { if (token === epoch) error(e) }
  finally { if (token === epoch) loading.value = false }
}
function changeProject() {
  ++projectScope; ++pathEpoch; ++assignmentEpoch
  selected.value = null; execution.value = null; paths.value = []; assignments.value = []
  pathPage.value = 1; assignmentPage.value = 1
  pathDialog.value = false; taskDialog.value = false; assignDialog.value = false
  pathEditingId.value = null; taskEditingId.value = null
  learners.value = []; learnerIds.value = []; dueAt.value = null
  pathTotal.value = 0; assignmentTotal.value = 0
  return refresh()
}
async function selectPath(id) {
  if (disposed) return
  const token = ++pathEpoch
  pathDialog.value = false; taskDialog.value = false; assignDialog.value = false
  pathEditingId.value = null; taskEditingId.value = null
  selected.value = null
  try { const data = unwrap(await api.getOnboardingPath(id)); if (token === pathEpoch) selected.value = data } catch (e) { if (token === pathEpoch) error(e) }
}
async function selectAssignment(id) {
  if (disposed) return
  const token = ++assignmentEpoch
  execution.value = null
  try { const data = unwrap(await api.getAssignment(id)); if (token === assignmentEpoch) execution.value = data } catch (e) { if (token === assignmentEpoch) error(e) }
}
async function command(action) {
  if (busy.value || disposed) return
  busy.value = true
  try { await action() } catch (e) { if (!disposed && e !== 'cancel' && e !== 'close') error(e) }
  finally { busy.value = false }
}
function openPath(edit = false) {
  if (disposed || busy.value || !manager.value || (edit && !editable.value)) return
  pathEditingId.value = edit ? selected.value.path.id : null
  Object.assign(pathForm, edit ? selected.value.path : { name: '', targetRole: '', description: '', knowledgeSnapshotId: null })
  pathDialog.value = true
}
function savePath() { return command(async () => {
  if (!pathDialog.value || !manager.value || (pathEditingId.value && !editable.value)) return
  if (!pathForm.name.trim() || !pathForm.knowledgeSnapshotId) return ElMessage.warning('请填写名称及正式知识快照 ID')
  const id = pathEditingId.value, target = projectId.value, scope = projectScope, token = pathEpoch
  const payload = { name: pathForm.name.trim(), targetRole: pathForm.targetRole, description: pathForm.description, knowledgeSnapshotId: pathForm.knowledgeSnapshotId }
  const savedId = id || unwrap(await api.createOnboardingPath(target, payload)).id
  if (id) await api.updateOnboardingPath(id, payload)
  if (!sameProject(target, scope) || token !== pathEpoch) return
  pathDialog.value = false
  await Promise.all([refresh(), selectPath(savedId)])
}) }
function openTask(task = null) {
  if (disposed || busy.value || !editable.value) return
  taskEditingId.value = task?.id || null
  Object.assign(taskForm, task ? { ...task, prerequisiteTaskIds: [...task.prerequisiteTaskIds] } : { type: 'READ', title: '', description: '', resourceId: null, estimatedMinutes: 30, required: true, sortOrder: (selected.value?.tasks.length || 0) + 1, prerequisiteTaskIds: [] })
  taskDialog.value = true
}
function saveTask() { return command(async () => {
  if (!taskDialog.value || !editable.value) return
  if (!taskForm.title.trim() || (taskForm.type !== 'CHECKPOINT' && !taskForm.resourceId)) return ElMessage.warning('请填写标题和关联资源 ID')
  const pathId = selected.value.path.id, scope = projectScope, token = pathEpoch
  const [resourceType, completionRule] = taskResource(taskForm.type)
  const payload = { type: taskForm.type, description: taskForm.description, estimatedMinutes: taskForm.estimatedMinutes, required: taskForm.required, sortOrder: taskForm.sortOrder, prerequisiteTaskIds: [...taskForm.prerequisiteTaskIds], title: taskForm.title.trim(), resourceId: taskForm.type === 'CHECKPOINT' ? null : taskForm.resourceId, resourceType, completionRule }
  if (taskEditingId.value) await api.updatePathTask(taskEditingId.value, payload)
  else await api.addPathTask(pathId, payload)
  if (!samePath(pathId, scope, token)) return
  taskDialog.value = false
  await selectPath(pathId)
}) }
function removeTask(task) { return command(async () => {
  if (!editable.value || !selected.value.tasks.some(t => t.id === task?.id)) return
  const id = selected.value.path.id, target = projectId.value, scope = projectScope, token = pathEpoch
  await ElMessageBox.confirm(`删除任务“${task.title}”及依赖边？`, '删除任务')
  if (!samePath(id, scope, token) || !editable.value) return
  await api.deletePathTask(task.id)
  if (sameProject(target, scope) && samePath(id, scope, token)) await selectPath(id)
}) }
function moveTask(index, direction) { return command(async () => {
  if (!editable.value || !Number.isInteger(index) || ![-1, 1].includes(direction)) return
  const id = selected.value.path.id, target = projectId.value, scope = projectScope, token = pathEpoch
  const ids = selected.value.tasks.map(t => t.id), next = index + direction
  if (index < 0 || index >= ids.length || next < 0 || next >= ids.length) return
  ;[ids[index], ids[next]] = [ids[next], ids[index]]
  await api.reorderPathTasks(id, ids)
  if (sameProject(target, scope) && samePath(id, scope, token)) await selectPath(id)
}) }
function publish() { return command(async () => {
  if (!editable.value) return
  const id = selected.value.path.id, target = projectId.value, scope = projectScope, token = pathEpoch
  await ElMessageBox.confirm('发布后不可直接编辑，本版本可用于分配。确认发布？', '发布路径')
  if (!samePath(id, scope, token) || !editable.value) return
  await api.publishOnboardingPath(id)
  if (sameProject(target, scope) && samePath(id, scope, token)) await Promise.all([refresh(), selectPath(id)])
}) }
function openAssign() { return command(async () => {
  if (!manager.value || selected.value?.path.status !== 'PUBLISHED') return
  const id = projectId.value, pathId = selected.value.path.id, scope = projectScope, token = pathEpoch
  const data = await loadAll(page => listProjectMembers(id, { role: 'LEARNER', page, pageSize: 100 }))
  if (!sameProject(id, scope) || !samePath(pathId, scope, token)) return
  learners.value = data.filter(m => m.role === 'LEARNER' && m.status === 'ACTIVE'); learnerIds.value = []; dueAt.value = null; assignDialog.value = true
}) }
function assign() { return command(async () => {
  if (!assignDialog.value || !manager.value || selected.value?.path.status !== 'PUBLISHED') return
  if (!learnerIds.value.length) return ElMessage.warning('请选择至少一个 LEARNER')
  const target = projectId.value, id = selected.value.path.id, scope = projectScope, token = pathEpoch
  await api.assignOnboardingPath(id, { learnerUserIds: [...learnerIds.value], dueAt: dueAt.value })
  if (!sameProject(target, scope) || !samePath(id, scope, token)) return
  assignDialog.value = false
  await refresh()
}) }
function execute(kind, task = null) { return command(async () => {
  if (project.value?.status !== 'ACTIVE' || !execution.value) return
  const assignment = execution.value.assignment
  if (assignment.assigneeUserId !== auth.user?.id) return
  if (kind === 'start' && assignment.status !== 'ASSIGNED') return
  if (kind === 'submit' && !canSubmitAssignment(assignment, execution.value.tasks, auth.user?.id)) return
  if (kind === 'complete' && (!execution.value.tasks.some(t => t.id === task?.id) || !canCompleteTask(assignment, task, auth.user?.id))) return
  if (!['start', 'submit', 'complete'].includes(kind)) return
  const id = assignment.id, target = projectId.value, scope = projectScope, token = assignmentEpoch
  if (kind === 'start') await api.startAssignment(id)
  if (kind === 'submit') await api.submitAssignment(id)
  if (kind === 'complete') {
    const { value } = await ElMessageBox.prompt('填写阅读记录或检查点说明（可选）', '完成任务', { inputType: 'textarea', inputValidator: value => !value || value.length <= 1000 || '最多1000字' })
    if (!sameProject(target, scope) || token !== assignmentEpoch || id !== execution.value?.assignment.id) return
    await api.completeAssignmentTask(task.id, value || '')
  }
  if (sameProject(target, scope) && token === assignmentEpoch && id === execution.value?.assignment.id) await Promise.all([refresh(), selectAssignment(id)])
}) }
onMounted(async () => {
  const token = epoch
  try {
    const data = await loadAll(page => listProjects({ page, pageSize: 100 }))
    if (token !== epoch) return
    projects.value = data; projectId.value = data[0]?.id || null
    if (projectId.value) await refresh()
  } catch (e) { if (!disposed) error(e) }
})
onBeforeUnmount(() => { disposed = true; ++projectScope; ++epoch; ++pathEpoch; ++assignmentEpoch })
</script>

<template>
  <div class="onboarding">
    <div class="toolbar"><div><h2>Onboarding 接手路径</h2><p>以固定知识版本训练，以真实任务过程验证接手准备度。</p></div>
      <el-select v-model="projectId" placeholder="选择项目" :disabled="busy" @change="changeProject"><el-option v-for="p in projects" :key="p.id" :label="`${p.name} · ${p.myRole}`" :value="p.id" /></el-select>
    </div>
    <el-empty v-if="!projectId" description="请先创建或加入 Project 团队空间" />
    <template v-else>
      <el-alert v-if="project?.status === 'ARCHIVED'" title="项目已归档，仅可查看" type="warning" :closable="false" />
      <el-tabs>
        <el-tab-pane label="接手路径">
          <el-button v-if="manager" type="primary" :disabled="busy" @click="openPath()">新建路径</el-button>
          <el-table v-loading="loading" :data="paths" @row-click="p => !busy && selectPath(p.id)"><el-table-column prop="name" label="路径" /><el-table-column prop="targetRole" label="目标职责" /><el-table-column prop="status" label="状态" /><el-table-column prop="versionNo" label="版本" /><el-table-column prop="knowledgeSnapshotId" label="知识快照" /></el-table>
          <el-pagination v-model:current-page="pathPage" :page-size="10" :total="pathTotal" layout="prev, pager, next" @current-change="refresh" />
          <el-card v-if="selected" class="detail">
            <div class="toolbar"><h3>{{ selected.path.name }} · v{{ selected.path.versionNo }} · {{ selected.path.status }}</h3><div>
              <el-button v-if="editable" :disabled="busy" @click="openPath(true)">编辑路径</el-button><el-button v-if="editable" :disabled="busy" @click="openTask()">添加任务</el-button><el-button v-if="editable" type="primary" :disabled="busy" @click="publish">发布</el-button><el-button v-if="manager && selected.path.status === 'PUBLISHED'" type="primary" :disabled="busy" @click="openAssign">分配 LEARNER</el-button>
            </div></div>
            <p>{{ selected.path.description }}</p><p>KnowledgeSnapshot #{{ selected.path.knowledgeSnapshotId }}；已发布路径不可直接编辑。</p>
            <el-alert v-if="editable" title="QUIZ / DRILL 可配置草稿，待后续 Assessment 提供已发布资源后才能发布。READ 必须引用绑定快照内的 documentVersionId。" type="info" :closable="false" />
            <el-table :data="selected.tasks"><el-table-column prop="id" label="ID" width="80" /><el-table-column prop="type" label="类型" width="120" /><el-table-column prop="title" label="任务" /><el-table-column prop="description" label="说明" /><el-table-column prop="resourceId" label="资源 ID" /><el-table-column prop="prerequisiteTaskIds" label="前置任务 ID" /><el-table-column label="必需" width="70"><template #default="{ row }">{{ row.required ? '是' : '否' }}</template></el-table-column>
              <el-table-column v-if="editable" label="操作" width="240"><template #default="{ row, $index }"><el-button link :disabled="busy" @click="openTask(row)">编辑</el-button><el-button link :disabled="busy" @click="removeTask(row)">删除</el-button><el-button link :disabled="busy || $index === 0" @click="moveTask($index, -1)">上移</el-button><el-button link :disabled="busy || $index === selected.tasks.length - 1" @click="moveTask($index, 1)">下移</el-button></template></el-table-column>
            </el-table>
          </el-card>
        </el-tab-pane>
        <el-tab-pane :label="manager ? '团队 Assignment' : '我的 Assignment'">
          <el-table v-loading="loading" :data="assignments" @row-click="a => !busy && selectAssignment(a.id)"><el-table-column prop="id" label="Assignment" /><el-table-column prop="pathId" label="路径 ID" /><el-table-column prop="pathVersion" label="固定版本" /><el-table-column prop="assigneeUserId" label="LEARNER" /><el-table-column prop="status" label="状态" /><el-table-column label="进度"><template #default="{ row }">{{ row.progress }}%</template></el-table-column><el-table-column prop="dueAt" label="截止" /></el-table>
          <el-pagination v-model:current-page="assignmentPage" :page-size="10" :total="assignmentTotal" layout="prev, pager, next" @current-change="refresh" />
          <el-card v-if="execution" class="detail">
            <h3>Assignment #{{ execution.assignment.id }} · {{ execution.assignment.status }}</h3>
            <p>Path #{{ execution.assignment.pathId }} v{{ execution.assignment.pathVersion }} / KnowledgeSnapshot #{{ execution.assignment.knowledgeSnapshotId }}（分配后固定）</p>
            <el-button v-if="project?.status === 'ACTIVE' && execution.assignment.assigneeUserId === auth.user?.id && execution.assignment.status === 'ASSIGNED'" :disabled="busy" @click="execute('start')">开始接手</el-button>
            <el-button v-if="project?.status === 'ACTIVE' && canSubmitAssignment(execution.assignment, execution.tasks, auth.user?.id)" type="primary" :disabled="busy" @click="execute('submit')">提交负责人复核</el-button>
            <el-table :data="execution.tasks"><el-table-column prop="id" label="ID" /><el-table-column prop="type" label="类型" /><el-table-column prop="title" label="固定任务" /><el-table-column prop="resourceId" label="固定资源 ID" /><el-table-column prop="prerequisiteTaskIds" label="前置执行任务 ID" /><el-table-column prop="status" label="状态" /><el-table-column prop="completionNote" label="完成记录" /><el-table-column label="操作"><template #default="{ row }"><el-button v-if="project?.status === 'ACTIVE' && canCompleteTask(execution.assignment, row, auth.user?.id)" link type="primary" :disabled="busy" @click="execute('complete', row)">完成</el-button><span v-else-if="['QUIZ', 'DRILL'].includes(row.type)">等待 Assessment</span></template></el-table-column></el-table>
            <p>进度不等于最终 PASS。必需任务完成后提交复核，最终判定将在后续阶段接入。</p>
          </el-card>
        </el-tab-pane>
      </el-tabs>
    </template>
    <el-dialog v-model="pathDialog" :title="pathEditingId ? '编辑草稿路径' : '新建接手路径'" width="560px" :close-on-click-modal="!busy" :show-close="!busy">
      <el-form label-width="140px"><el-form-item label="名称"><el-input v-model="pathForm.name" maxlength="128" /></el-form-item><el-form-item label="目标职责"><el-input v-model="pathForm.targetRole" maxlength="128" /></el-form-item><el-form-item label="描述"><el-input v-model="pathForm.description" type="textarea" maxlength="1000" /></el-form-item><el-form-item label="KnowledgeSnapshot ID"><el-input-number v-model="pathForm.knowledgeSnapshotId" :min="1" :precision="0" /></el-form-item></el-form><template #footer><el-button type="primary" :loading="busy" @click="savePath">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="taskDialog" :title="taskEditingId ? '编辑任务' : '添加任务'" width="580px" :close-on-click-modal="!busy" :show-close="!busy">
      <el-form label-width="120px"><el-form-item label="类型"><el-select v-model="taskForm.type"><el-option v-for="t in ['READ', 'QUIZ', 'DRILL', 'CHECKPOINT']" :key="t" :label="t" :value="t" /></el-select></el-form-item><el-form-item label="标题"><el-input v-model="taskForm.title" maxlength="128" /></el-form-item><el-form-item label="描述"><el-input v-model="taskForm.description" type="textarea" maxlength="1000" /></el-form-item><el-form-item v-if="taskForm.type !== 'CHECKPOINT'" :label="taskForm.type === 'READ' ? '文档版本 ID' : '评估资源 ID'"><el-input-number v-model="taskForm.resourceId" :min="1" :precision="0" /></el-form-item><el-form-item label="前置任务"><el-select v-model="taskForm.prerequisiteTaskIds" multiple><el-option v-for="t in selected?.tasks.filter(t => t.id !== taskEditingId) || []" :key="t.id" :label="`${t.id} · ${t.title}`" :value="t.id" /></el-select></el-form-item><el-form-item label="预计分钟"><el-input-number v-model="taskForm.estimatedMinutes" :min="0" :precision="0" /></el-form-item><el-form-item label="必需任务"><el-switch v-model="taskForm.required" /></el-form-item></el-form><template #footer><el-button type="primary" :loading="busy" @click="saveTask">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="assignDialog" title="分配接手路径" width="560px" :close-on-click-modal="!busy" :show-close="!busy"><el-alert title="再次分配将创建新批次，不会覆盖历史 Assignment。" type="info" :closable="false" /><el-form label-width="100px"><el-form-item label="LEARNER"><el-select v-model="learnerIds" multiple><el-option v-for="m in learners" :key="m.userId" :label="`${m.nickname || m.username} #${m.userId}`" :value="m.userId" /></el-select></el-form-item><el-form-item label="截止时间"><el-date-picker v-model="dueAt" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" /></el-form-item></el-form><template #footer><el-button type="primary" :loading="busy" @click="assign">确认分配</el-button></template></el-dialog>
  </div>
</template>
<style scoped>
.toolbar{display:flex;justify-content:space-between;align-items:center;gap:16px;flex-wrap:wrap}.toolbar h2{margin:0}.toolbar p,p{color:#606266}.detail{margin-top:20px}.el-pagination{margin-top:16px}.el-select{min-width:230px}.el-form{margin-top:16px}.el-alert{margin:12px 0}
</style>

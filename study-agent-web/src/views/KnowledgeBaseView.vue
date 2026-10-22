<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as projectApi from '../api/project'
import * as kbApi from '../api/knowledgeBase'
import { usePolling } from '../composables/usePolling'

const projects = ref([])
const projectId = ref(null)
const bases = ref([])
const selected = ref(null)
const documents = ref([])
const snapshots = ref([])
const snapshotDetail = ref(null)
const loading = ref(false)
const busy = ref(false)
const uploadInput = ref(null)
const uploadTarget = ref(null)
const dialog = ref(false)
const editingId = ref(null)
const form = reactive({ name: '', description: '' })
const uploadForm = reactive({ title: '', changeNote: '', file: null })
const uploadDialog = ref(false)
const reviewDialog = ref(false)
const reviewTarget = ref(null)
const reviewForm = reactive({ decision: 'APPROVE', comment: '' })
const pollingVersionId = ref(null)
const canEdit = computed(() => ['OWNER', 'MAINTAINER'].includes(projects.value.find(item => item.id === projectId.value)?.myRole))

const { start: startPolling, stop: stopPolling } = usePolling(async () => {
  if (!pollingVersionId.value) return 'READY'
  const response = await kbApi.getVersionStatus(pollingVersionId.value)
  const state = response.data.data
  if (selected.value) await loadKnowledge(selected.value.id)
  if (state.processStatus === 'FAILED') ElMessage.error(state.errorMessage || '文档处理失败')
  return ['READY', 'FAILED'].includes(state.processStatus) ? state.processStatus : null
}, { interval: 1800, timeout: 240000, onError: () => ElMessage.error('查询处理状态失败') })

function data(response) { return response.data.data }
function showError(error, fallback) { ElMessage.error(error.response?.data?.msg || fallback) }
async function loadProjects() {
  try {
    const response = await projectApi.listProjects({ page: 1, pageSize: 100, status: 'ACTIVE' })
    projects.value = data(response).rows || []
    if (!projectId.value && projects.value.length) projectId.value = projects.value[0].id
    await loadBases()
  } catch (error) { showError(error, '项目加载失败') }
}
async function loadBases() {
  if (!projectId.value) { bases.value = []; selected.value = null; return }
  loading.value = true
  try {
    const response = await kbApi.listKnowledgeBases(projectId.value, { page: 1, pageSize: 100, status: 'ACTIVE' })
    bases.value = data(response).rows || []
    if (selected.value) selected.value = bases.value.find(item => item.id === selected.value.id) || null
    if (!selected.value && bases.value.length) selected.value = bases.value[0]
    if (selected.value) await loadKnowledge(selected.value.id)
    else { documents.value = []; snapshots.value = [] }
  } catch (error) { showError(error, '知识库加载失败') }
  finally { loading.value = false }
}
async function loadKnowledge(id) {
  const [detail, docs, history] = await Promise.all([
    kbApi.getKnowledgeBase(id), kbApi.listDocuments(id, { page: 1, pageSize: 100 }),
    kbApi.listSnapshots(id, { page: 1, pageSize: 50 })
  ])
  selected.value = { ...data(detail), ...(bases.value.find(item => item.id === id) || {}) }
  documents.value = data(docs).rows || []
  snapshots.value = data(history).rows || []
  await Promise.all(documents.value.map(async doc => {
    const result = await kbApi.getDocument(doc.id)
    doc.versions = data(result).versions || []
  }))
}
async function selectProject(id) { projectId.value = id; selected.value = null; await loadBases() }
async function selectBase(base) { selected.value = base; await loadKnowledge(base.id) }
function openCreate() { editingId.value = null; Object.assign(form, { name: '', description: '' }); dialog.value = true }
function openEdit() { editingId.value = selected.value.id; Object.assign(form, { name: selected.value.name, description: selected.value.description || '' }); dialog.value = true }
async function saveBase() {
  if (busy.value) return
  busy.value = true
  try {
    if (editingId.value) await kbApi.updateKnowledgeBase(editingId.value, form)
    else await kbApi.createKnowledgeBase(projectId.value, form)
    dialog.value = false; await loadBases(); ElMessage.success('知识库已保存')
  } catch (error) { showError(error, '保存失败') } finally { busy.value = false }
}
async function archiveBase() {
  try {
    await ElMessageBox.confirm(`归档知识库“${selected.value.name}”？历史快照仍会保留。`, '归档知识库', { type: 'warning' })
    await kbApi.archiveKnowledgeBase(selected.value.id); selected.value = null; await loadBases()
  } catch (error) { if (error !== 'cancel' && error !== 'close') showError(error, '归档失败') }
}
function openUpload(document = null) {
  uploadTarget.value = document
  Object.assign(uploadForm, { title: document?.title || '', changeNote: '', file: null })
  uploadDialog.value = true
}
async function submitUpload() {
  if (!uploadForm.file) return ElMessage.warning('请选择文件')
  busy.value = true
  try {
    const response = uploadTarget.value
      ? await kbApi.uploadVersion(uploadTarget.value.id, uploadForm)
      : await kbApi.uploadDocument(selected.value.id, uploadForm)
    pollingVersionId.value = data(response).versionId
    uploadDialog.value = false
    await loadKnowledge(selected.value.id)
    await startPolling()
  } catch (error) { showError(error, '上传失败') } finally { busy.value = false }
}
async function reprocess(version) {
  try {
    await kbApi.reprocessVersion(version.id); pollingVersionId.value = version.id; await startPolling()
  } catch (error) { showError(error, '重新处理失败') }
}
async function submitReview(version) {
  try { await kbApi.submitReview(version.id); await loadKnowledge(selected.value.id); ElMessage.success('已提交审核') }
  catch (error) { showError(error, '提交审核失败') }
}
function openReview(version) { reviewTarget.value = version; Object.assign(reviewForm, { decision: 'APPROVE', comment: '' }); reviewDialog.value = true }
async function review() {
  try { await kbApi.reviewVersion(reviewTarget.value.id, reviewForm); reviewDialog.value = false; await loadKnowledge(selected.value.id) }
  catch (error) { showError(error, '审核失败') }
}
async function publish(version) {
  try {
    await ElMessageBox.confirm('发布后会生成新的不可变知识快照。', '发布知识版本', { type: 'warning' })
    await kbApi.publishVersion(version.id); await loadKnowledge(selected.value.id); ElMessage.success('已发布并生成知识快照')
  } catch (error) { if (error !== 'cancel' && error !== 'close') showError(error, '发布失败') }
}
async function showSnapshot(snapshot) {
  try { snapshotDetail.value = data(await kbApi.getSnapshot(snapshot.id)) }
  catch (error) { showError(error, '快照详情加载失败') }
}
function processType(status) { return ({ READY: 'success', FAILED: 'danger', UPLOADED: 'info', PARSING: 'warning', CHUNKING: 'warning', EMBEDDING: 'warning' })[status] || 'info' }
function publishType(status) { return ({ PUBLISHED: 'success', APPROVED: 'success', IN_REVIEW: 'warning', REJECTED: 'danger', SUPERSEDED: 'info' })[status] || 'info' }
onMounted(loadProjects)
onBeforeUnmount(stopPolling)
</script>

<template>
  <main class="knowledge-page">
    <header class="page-heading"><div><h2>项目知识</h2><p>READY 表示已完成索引；只有发布到快照后才进入正式知识。</p></div><el-button type="primary" :disabled="!projectId || !canEdit" @click="openCreate">新建知识库</el-button></header>
    <div class="toolbar"><el-select :model-value="projectId" placeholder="选择项目" @change="selectProject"><el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" /></el-select><el-button v-if="selected && canEdit" @click="openEdit">编辑知识库</el-button><el-button v-if="selected && canEdit" type="danger" plain @click="archiveBase">归档</el-button></div>
    <div class="workspace">
      <aside class="base-list"><el-skeleton v-if="loading" :rows="5" animated /><el-empty v-else-if="!bases.length" description="项目暂无知识库" /><button v-for="base in bases" v-else :key="base.id" class="base-item" :class="{ active: selected?.id === base.id }" @click="selectBase(base)"><strong>{{ base.name }}</strong><span>{{ base.description || '暂无描述' }}</span><small>{{ base.documentCount || 0 }} 个逻辑文档</small></button></aside>
      <section v-if="selected" class="knowledge-content">
        <header class="section-heading"><div><h3>{{ selected.name }}</h3><p>{{ selected.description || '暂无描述' }}</p></div><el-button type="primary" :disabled="!canEdit" @click="openUpload()">上传文档</el-button></header>
        <el-table :data="documents" row-key="id" empty-text="暂无文档">
          <el-table-column type="expand"><template #default="{ row }"><div class="versions"><div v-for="version in row.versions" :key="version.id" class="version-row"><div class="version-meta"><strong>v{{ version.versionNo }} · {{ version.fileName }}</strong><span>{{ version.changeNote || '无变更说明' }}</span></div><div class="states"><el-tag :type="processType(version.processStatus)">处理：{{ version.processStatus }}</el-tag><el-progress v-if="!['READY','FAILED'].includes(version.processStatus)" :percentage="version.progress || 0" :show-text="false" :stroke-width="5" /><el-tag :type="publishType(version.publishStatus)">发布：{{ version.publishStatus }}</el-tag><span v-if="version.errorMessage" class="error-text">{{ version.errorMessage }}</span></div><div class="version-actions"><el-button v-if="version.processStatus === 'FAILED'" link type="warning" :disabled="!canEdit" @click="reprocess(version)">重新处理</el-button><el-button v-if="version.processStatus === 'READY' && version.publishStatus === 'DRAFT'" link type="primary" :disabled="!canEdit" @click="submitReview(version)">提交审核</el-button><el-button v-if="version.publishStatus === 'IN_REVIEW'" link type="primary" :disabled="!canEdit" @click="openReview(version)">审核</el-button><el-button v-if="version.publishStatus === 'APPROVED' && version.processStatus === 'READY'" link type="success" :disabled="!canEdit" @click="publish(version)">发布</el-button><el-button v-if="canEdit" link @click="openUpload(row)">上传新版本</el-button></div></div></div></template></el-table-column>
          <el-table-column prop="title" label="逻辑文档" min-width="220" /><el-table-column label="当前正式版本" min-width="160"><template #default="{ row }">{{ row.currentPublishedVersionId ? `v${row.currentVersionNo}` : '未发布' }}</template></el-table-column><el-table-column label="最新处理状态" width="150"><template #default="{ row }"><el-tag :type="processType(row.latestVersionProcessStatus)">{{ row.latestVersionProcessStatus || '无版本' }}</el-tag></template></el-table-column><el-table-column label="最新发布状态" width="150"><template #default="{ row }"><el-tag :type="publishType(row.latestVersionPublishStatus)">{{ row.latestVersionPublishStatus || 'DRAFT' }}</el-tag></template></el-table-column>
        </el-table>
        <section class="snapshot-section"><h3>知识快照</h3><el-table :data="snapshots" empty-text="尚无正式快照"><el-table-column prop="snapshotNo" label="版本" width="100"><template #default="{ row }">Snapshot {{ row.snapshotNo }}</template></el-table-column><el-table-column prop="itemCount" label="文档数" width="100" /><el-table-column prop="createTime" label="发布时间" min-width="180" /><el-table-column label="内容" width="110"><template #default="{ row }"><el-button link type="primary" @click="showSnapshot(row)">查看内容</el-button></template></el-table-column></el-table></section>
      </section><el-empty v-else description="请选择项目知识库" />
    </div>
    <el-dialog v-model="dialog" :title="editingId ? '编辑知识库' : '新建知识库'" width="min(480px, 94vw)"><el-form label-position="top"><el-form-item label="名称" required><el-input v-model="form.name" maxlength="128" /></el-form-item><el-form-item label="描述"><el-input v-model="form.description" type="textarea" maxlength="1000" /></el-form-item></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" :loading="busy" @click="saveBase">保存</el-button></template></el-dialog>
    <el-dialog v-model="uploadDialog" :title="uploadTarget ? '上传文档新版本' : '上传逻辑文档'" width="min(480px, 94vw)"><el-form label-position="top"><el-form-item v-if="!uploadTarget" label="文档标题" required><el-input v-model="uploadForm.title" maxlength="255" /></el-form-item><el-form-item label="变更说明"><el-input v-model="uploadForm.changeNote" type="textarea" maxlength="1000" /></el-form-item><el-form-item label="文件" required><input type="file" accept=".pdf,.docx,.txt,.md" @change="uploadForm.file = $event.target.files?.[0] || null" /></el-form-item></el-form><template #footer><el-button @click="uploadDialog = false">取消</el-button><el-button type="primary" :loading="busy" @click="submitUpload">上传并处理</el-button></template></el-dialog>
    <el-dialog v-model="reviewDialog" title="审核文档版本" width="min(440px, 94vw)"><el-form label-position="top"><el-form-item label="审核决定"><el-radio-group v-model="reviewForm.decision"><el-radio-button label="APPROVE">通过</el-radio-button><el-radio-button label="REJECT">退回</el-radio-button></el-radio-group></el-form-item><el-form-item label="审核意见"><el-input v-model="reviewForm.comment" type="textarea" maxlength="1000" /></el-form-item></el-form><template #footer><el-button @click="reviewDialog = false">取消</el-button><el-button type="primary" @click="review">提交审核</el-button></template></el-dialog>
    <el-dialog v-model="snapshotDetail" :title="`Knowledge Snapshot ${snapshotDetail?.snapshotNo || ''}`" width="min(700px, 94vw)"><el-table :data="snapshotDetail?.items || []"><el-table-column prop="title" label="逻辑文档" min-width="220" /><el-table-column prop="versionNo" label="文档版本" width="120"><template #default="{ row }">v{{ row.versionNo }}</template></el-table-column><el-table-column prop="documentVersionId" label="DocumentVersion ID" min-width="170" /></el-table></el-dialog>
  </main>
</template>

<style scoped>
.knowledge-page{max-width:1400px;margin:0 auto}.page-heading,.toolbar,.section-heading{display:flex;align-items:center;justify-content:space-between;gap:12px}.page-heading{margin-bottom:16px}h2,h3,p{margin:0}h2{margin-bottom:5px}.page-heading p,.section-heading p{color:#7b818a;font-size:13px}.toolbar{justify-content:flex-start;margin-bottom:14px}.toolbar .el-select{width:min(360px,100%)}.workspace{display:grid;grid-template-columns:minmax(230px,300px) minmax(0,1fr);gap:16px;align-items:start}.base-list{border-right:1px solid #e7e9ed;padding-right:12px;min-height:360px}.base-item{display:flex;width:100%;flex-direction:column;align-items:flex-start;gap:6px;padding:14px 10px;border:0;border-bottom:1px solid #e7e9ed;background:transparent;text-align:left;cursor:pointer}.base-item.active,.base-item:hover{background:#f1f7f4}.base-item span,.base-item small{color:#7b818a}.knowledge-content{min-width:0}.section-heading{margin-bottom:12px}.section-heading h3{margin-bottom:5px}.versions{display:grid;gap:10px;padding:4px 10px}.version-row{display:grid;grid-template-columns:minmax(180px,1fr) minmax(180px,1fr) auto;gap:12px;align-items:center;padding:10px 0;border-bottom:1px solid #eceef1}.version-meta{display:grid;gap:5px;min-width:0}.version-meta strong,.version-meta span{overflow-wrap:anywhere}.version-meta span,.error-text{font-size:12px;color:#7b818a}.states{display:grid;grid-template-columns:max-content minmax(60px,100px);gap:6px;align-items:center}.states .error-text{grid-column:1/-1}.version-actions{display:flex;flex-wrap:wrap;justify-content:flex-end}.snapshot-section{margin-top:24px}.snapshot-section h3{margin-bottom:10px}@media(max-width:950px){.workspace{grid-template-columns:1fr}.base-list{border-right:0;border-bottom:1px solid #e7e9ed;padding:0 0 12px;min-height:0}.version-row{grid-template-columns:1fr}.version-actions{justify-content:flex-start}.page-heading,.section-heading{align-items:flex-start;flex-wrap:wrap}}
</style>

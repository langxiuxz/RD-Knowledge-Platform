<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { usePolling } from '../composables/usePolling'
import * as kbApi from '../api/knowledgeBase'

const rows = ref([]); const total = ref(0); const loading = ref(false); const selected = ref(null); const documents = ref([]); const docLoading = ref(false)
const query = reactive({ name: '', page: 1, pageSize: 20 })
const dialog = ref(false); const editingId = ref(null); const form = reactive({ name: '', description: '', embeddingModel: '' })
const uploadInput = ref(null); const pollingDocumentId = ref(null)
const { start: startPolling, stop: stopPolling } = usePolling(async () => {
  if (!pollingDocumentId.value || !selected.value) return 'READY'
  const response = await kbApi.getDocumentStatus(pollingDocumentId.value)
  const status = response.data.data
  await loadDocuments(selected.value.id)
  if (status.status === 'FAILED') ElMessage.error(status.errorMessage || '文档处理失败')
  return status.status
}, { interval: 1800, timeout: 180000, onError: () => ElMessage.error('查询文档状态失败') })

async function load() { loading.value = true; try { const r = await kbApi.listKnowledgeBases({ ...query }); rows.value = r.data.data?.rows || []; total.value = r.data.data?.total || 0; if (selected.value) { selected.value = rows.value.find(x => x.id === selected.value.id) || null; if (selected.value) await loadDocuments(selected.value.id) } } catch (e) { ElMessage.error(e.response?.data?.msg || '知识库加载失败') } finally { loading.value = false } }
async function select(item) { selected.value = item; await loadDocuments(item.id) }
async function loadDocuments(id) { docLoading.value = true; try { const r = await kbApi.listDocuments(id, { page: 1, pageSize: 100 }); documents.value = r.data.data?.rows || [] } catch (e) { ElMessage.error(e.response?.data?.msg || '文档加载失败') } finally { docLoading.value = false } }
function openCreate() { editingId.value = null; Object.assign(form, { name: '', description: '', embeddingModel: '' }); dialog.value = true }
function openEdit(item) { editingId.value = item.id; Object.assign(form, { name: item.name, description: item.description || '', embeddingModel: item.embeddingModel || '' }); dialog.value = true }
async function save() { try { if (editingId.value) await kbApi.updateKnowledgeBase(editingId.value, form); else await kbApi.createKnowledgeBase(form); dialog.value = false; ElMessage.success('保存成功'); await load() } catch (e) { ElMessage.error(e.response?.data?.msg || '保存失败') } }
async function remove(item) { try { await ElMessageBox.confirm(`确定删除知识库“${item.name}”及其文档吗？`, '确认删除', { type: 'warning' }); await kbApi.deleteKnowledgeBase(item.id); if (selected.value?.id === item.id) { selected.value = null; documents.value = [] } ElMessage.success('已删除'); await load() } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.response?.data?.msg || '删除失败') } }
function chooseFile() { uploadInput.value?.click() }
async function upload(event) { const file = event.target.files?.[0]; event.target.value = ''; if (!file || !selected.value) return; try { const r = await kbApi.uploadDocument(selected.value.id, file); const data = r.data.data; pollingDocumentId.value = data.documentId; ElMessage.success('文档已上传，开始处理'); await loadDocuments(selected.value.id); await startPolling() } catch (e) { ElMessage.error(e.response?.data?.msg || '上传失败') } }
async function reprocess(doc) { try { const r = await kbApi.reprocessDocument(doc.id); pollingDocumentId.value = doc.id; await loadDocuments(selected.value.id); ElMessage.success('已重新处理'); await startPolling() } catch (e) { ElMessage.error(e.response?.data?.msg || '重新处理失败') } }
async function removeDoc(doc) { try { await ElMessageBox.confirm(`确定删除“${doc.fileName}”？`, '确认删除', { type: 'warning' }); await kbApi.deleteDocument(doc.id); ElMessage.success('已删除'); await loadDocuments(selected.value.id) } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.response?.data?.msg || '删除失败') } }
function statusType(status) { return ({ READY: 'success', FAILED: 'danger', PROCESSING: 'warning', UPLOADED: 'info' })[status] || 'info' }
onMounted(load)
</script>
<template>
  <div class="page"><div class="page-heading"><div><h2>知识库</h2><p>上传资料后，系统会自动解析并建立可检索索引。</p></div><el-button type="primary" @click="openCreate">新建知识库</el-button></div>
    <el-row :gutter="16"><el-col :xs="24" :md="9"><el-card class="list-card"><div class="search"><el-input v-model="query.name" placeholder="搜索知识库" clearable @keyup.enter="load" /><el-button @click="load">搜索</el-button></div><el-skeleton v-if="loading" :rows="5" animated /><el-empty v-else-if="!rows.length" description="还没有知识库" /><div v-else class="kb-list"><div v-for="item in rows" :key="item.id" class="kb-item" :class="{ active: selected?.id === item.id }" @click="select(item)"><div><strong>{{ item.name }}</strong><div class="muted">{{ item.description || '暂无描述' }}</div></div><el-tag size="small">{{ item.documentCount || 0 }} 个文档</el-tag></div></div><el-pagination v-if="total > query.pageSize" v-model:current-page="query.page" :page-size="query.pageSize" :total="total" layout="prev, pager, next" @current-change="load" /></el-card></el-col>
      <el-col :xs="24" :md="15"><el-card v-if="selected"><template #header><div class="card-header"><div><strong>{{ selected.name }}</strong><div class="muted">{{ selected.description || '暂无描述' }}</div></div><div><el-button size="small" @click="openEdit(selected)">编辑</el-button><el-button size="small" type="danger" plain @click="remove(selected)">删除</el-button><el-button size="small" type="primary" @click="chooseFile">上传文档</el-button><input ref="uploadInput" type="file" hidden accept=".pdf,.txt,.md,.docx" @change="upload" /></div></div></template><el-table v-loading="docLoading" :data="documents" empty-text="暂无文档"><el-table-column prop="fileName" label="文件名" min-width="200" /><el-table-column prop="fileType" label="类型" width="100" /><el-table-column prop="fileSize" label="大小" width="100"><template #default="scope">{{ Math.max(1, Math.round((scope.row.fileSize || 0) / 1024)) }} KB</template></el-table-column><el-table-column label="状态" width="120"><template #default="scope"><el-tag :type="statusType(scope.row.status)">{{ scope.row.status }}</el-tag><el-progress v-if="scope.row.status !== 'READY' && scope.row.status !== 'FAILED'" :percentage="scope.row.progress || 0" :stroke-width="5" /></template></el-table-column><el-table-column label="操作" width="150"><template #default="scope"><el-button v-if="scope.row.status === 'FAILED'" link type="warning" @click="reprocess(scope.row)">重新处理</el-button><el-button link type="danger" @click="removeDoc(scope.row)">删除</el-button></template></el-table-column></el-table></el-card><el-empty v-else description="请选择一个知识库" /></el-col></el-row>
    <el-dialog v-model="dialog" :title="editingId ? '编辑知识库' : '新建知识库'" width="460px"><el-form label-position="top"><el-form-item label="名称"><el-input v-model="form.name" maxlength="64" /></el-form-item><el-form-item label="描述"><el-input v-model="form.description" type="textarea" maxlength="500" /></el-form-item><el-form-item label="Embedding 模型（可选）"><el-input v-model="form.embeddingModel" /></el-form-item></el-form><template #footer><el-button @click="dialog = false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template></el-dialog>
  </div>
</template>
<style scoped>.page { max-width: 1300px; margin: 0 auto; } .page-heading,.card-header { display:flex; justify-content:space-between; align-items:center; gap:12px; } h2 { margin:0 0 6px; } p,.muted { color:#909399; margin:0; font-size:13px; } .search { display:flex; gap:8px; margin-bottom:12px; } .kb-item { padding:13px 10px; border-bottom:1px solid #f0f2f5; cursor:pointer; display:flex; justify-content:space-between; gap:10px; align-items:center; } .kb-item:hover,.kb-item.active { background:#f0f7ff; } .list-card { min-height:500px; } .el-pagination { margin-top:16px; justify-content:center; } @media(max-width:768px){.page-heading,.card-header{align-items:flex-start; flex-direction:column}.card-header .el-button{margin-top:5px}}
</style>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as conversationApi from '../api/conversation'
import * as projectApi from '../api/project'
import * as kbApi from '../api/knowledgeBase'
import { feedbackMessage } from '../api/message'
import { useSseChat } from '../composables/useSseChat'

const feedbackSent = ref(new Set()); const conversations = ref([]); const selected = ref(null); const messages = ref([]); const loading = ref(false); const sending = ref(false); const text = ref(''); const mode = ref('CHAT'); const kbId = ref(null); const newDialog = ref(false); const newTitle = ref(''); const newMode = ref('CHAT'); const newProjectId = ref(null); const currentProjectId = ref(null); const newKbId = ref(null); const newSnapshotId = ref(null); const projects = ref([]); const knowledgeBases = ref([]); const snapshots = ref([])
const stream = useSseChat()
const { streaming, content: streamContent, citations: streamCitations, toolCalls: streamToolCalls, nodes: streamNodes, error: streamError } = stream
const streamConversationId = ref(null)
// Transfer the buffered answer synchronously before the streaming bubble is hidden.
watch(streaming, (active, wasActive) => {
  if (active || !wasActive || !streamContent.value || selected.value?.id !== streamConversationId.value) return
  const id = stream.messageId.value ?? `local-assistant-${Date.now()}`
  const assistant = {
    id, role: 'assistant', content: streamContent.value, citations: [...streamCitations.value],
    pendingSync: true, localOnly: stream.messageId.value == null
  }
  const index = messages.value.findIndex(item => String(item.id) === String(id))
  if (index < 0) messages.value.push(assistant)
  else messages.value[index] = assistant
}, { flush: 'sync' })

function mergeHistory(rows, buffered = []) {
  // The history API pages newest-first; the chat renders oldest-first.
  const ordered = [...rows].reverse()
  for (const assistant of buffered) {
    const index = ordered.findIndex(item => String(item.id) === String(assistant.id))
    if (index < 0) {
      const next = ordered.findIndex(item => Number(item.id) > Number(assistant.id))
      ordered.splice(next < 0 ? ordered.length : next, 0, assistant)
    } else if (!ordered[index].content) ordered[index] = assistant
  }
  return ordered
}
const modeLabel = computed(() => ({ CHAT: '普通聊天', RAG: '知识库问答', AGENT: '学习助手' })[selected.value?.mode || mode.value] || '普通聊天')
async function loadConversations() { if (!currentProjectId.value) return; loading.value = true; try { const r = await conversationApi.listConversations(currentProjectId.value, { page: 1, pageSize: 50 }); conversations.value = r.data.data?.rows || []; if (!selected.value && conversations.value[0]) await selectConversation(conversations.value[0]) } catch (e) { ElMessage.error(e.response?.data?.msg || '会话加载失败') } finally { loading.value = false } }
async function selectConversation(item) {
  if (!sending.value) stream.reset()
  selected.value = item
  mode.value = item.mode || 'CHAT'
  kbId.value = item.knowledgeBaseId || null
  messages.value = []
  try {
    const r = await conversationApi.listMessages(item.id, { pageSize: 100 })
    if (selected.value?.id === item.id) messages.value = mergeHistory(r.data.data?.rows || [])
  } catch (e) { ElMessage.error(e.response?.data?.msg || '消息加载失败') }
}
async function openNewConversation() { newDialog.value = true; try { const r = await projectApi.listProjects({ page: 1, pageSize: 100, status: 'ACTIVE' }); projects.value = r.data.data?.rows || []; newProjectId.value ||= currentProjectId.value || projects.value[0]?.id; await loadKnowledgeChoices() } catch (e) { ElMessage.error(e.response?.data?.msg || '项目加载失败') } }
async function loadKnowledgeChoices() { knowledgeBases.value = []; snapshots.value = []; newKbId.value = null; newSnapshotId.value = null; if (!newProjectId.value) return; try { const r = await kbApi.listKnowledgeBases(newProjectId.value, { page: 1, pageSize: 100, status: 'ACTIVE' }); knowledgeBases.value = r.data.data?.rows || [] } catch (e) { ElMessage.error(e.response?.data?.msg || '知识库加载失败') } }
async function loadSnapshotChoices() { snapshots.value = []; newSnapshotId.value = null; if (!newKbId.value) return; try { const r = await kbApi.listSnapshots(newKbId.value, { page: 1, pageSize: 100 }); snapshots.value = r.data.data?.rows || []; newSnapshotId.value = snapshots.value[0]?.id || null } catch (e) { ElMessage.error(e.response?.data?.msg || '快照加载失败') } }
async function create() { if (!newTitle.value.trim()) return ElMessage.warning('请输入会话标题'); if (!newProjectId.value) return ElMessage.warning('请选择项目'); if (newMode.value === 'RAG' && (!newKbId.value || !newSnapshotId.value)) return ElMessage.warning('RAG 会话必须绑定正式知识快照'); try { const r = await conversationApi.createConversation({ projectId: newProjectId.value, title: newTitle.value.trim(), mode: newMode.value, knowledgeBaseId: newKbId.value || null, knowledgeSnapshotId: newSnapshotId.value || null }); newDialog.value = false; newTitle.value = ''; currentProjectId.value = newProjectId.value; selected.value = null; const id = r.data.data.id; await loadConversations(); const item = conversations.value.find(x => x.id === id); if (item) await selectConversation(item) } catch (e) { ElMessage.error(e.response?.data?.msg || '创建会话失败') } }
async function selectProject(id) { currentProjectId.value = id; selected.value = null; messages.value = []; conversations.value = []; await loadConversations() }
async function removeConversation(item) { try { await ElMessageBox.confirm('删除会话及历史消息？', '确认删除', { type: 'warning' }); await conversationApi.deleteConversation(item.id); if (selected.value?.id === item.id) { selected.value = null; messages.value = [] } await loadConversations() } catch (e) { if (e !== 'cancel' && e !== 'close') ElMessage.error(e.response?.data?.msg || '删除失败') } }
async function send() {
  if (!selected.value) return ElMessage.warning('请先创建或选择会话')
  if (!text.value.trim() || streaming.value || sending.value) return
  const conversation = selected.value
  const content = text.value.trim()
  text.value = ''
  messages.value.push({ id: `local-${Date.now()}`, role: 'user', content, citations: [] })
  streamConversationId.value = conversation.id
  sending.value = true
  try {
    await stream.start(conversation.id, {
      content,
      useKnowledgeBase: conversation.mode === 'RAG' || (conversation.mode === 'AGENT' && !!conversation.knowledgeBaseId),
      enableTools: conversation.mode === 'AGENT'
    })
    if (streamError.value) throw new Error(streamError.value.message || 'AI生成失败')
    try { await reloadMessages(conversation.id) }
    catch { ElMessage.warning('回答已保留，历史消息同步失败，请稍后重试') }
  } catch (e) {
    if (e.name !== 'AbortError') ElMessage.error(e.message || '发送失败')
  } finally { sending.value = false }
}
async function submitFeedback(item, rating) { try { await feedbackMessage(item.id, { rating }); feedbackSent.value = new Set(feedbackSent.value).add(item.id); ElMessage.success("感谢反馈") } catch (e) { ElMessage.error(e.response?.data?.msg || "提交反馈失败") } }
async function reloadMessages(conversationId = selected.value?.id) {
  if (!conversationId || selected.value?.id !== conversationId) return
  const r = await conversationApi.listMessages(conversationId, { pageSize: 100 })
  if (selected.value?.id !== conversationId) return
  messages.value = mergeHistory(r.data.data?.rows || [], messages.value.filter(item => item.pendingSync))
}
async function stop() {
  if (selected.value?.id === streamConversationId.value) {
    const conversationId = selected.value.id
    await stream.stop(conversationId)
    await reloadMessages(conversationId)
    ElMessage.info('已请求停止生成')
  }
}
onMounted(async () => { try { const r = await projectApi.listProjects({ page: 1, pageSize: 100, status: 'ACTIVE' }); projects.value = r.data.data?.rows || []; currentProjectId.value = projects.value[0]?.id || null; await loadConversations() } catch (e) { ElMessage.error(e.response?.data?.msg || '项目加载失败') } })
</script>
<template>
  <div class="chat-page"><el-card class="conversation-panel"><template #header><div class="card-header"><strong>会话</strong><el-button type="primary" size="small" @click="openNewConversation">新建</el-button></div></template><el-select :model-value="currentProjectId" placeholder="选择项目" class="project-select" @change="selectProject"><el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" /></el-select><el-skeleton v-if="loading" :rows="5" animated /><el-empty v-else-if="!conversations.length" description="暂无会话" /><div v-else class="conversation-list"><div v-for="item in conversations" :key="item.id" class="conversation-item" :class="{active:selected?.id===item.id}" @click="selectConversation(item)"><div class="title">{{ item.title }}</div><div class="meta">{{ item.mode }} · {{ item.messageCount || 0 }} 条</div><el-button link type="danger" @click.stop="removeConversation(item)">删</el-button></div></div></el-card>
    <el-card class="chat-panel"><template #header><div class="chat-header"><div><strong>{{ selected?.title || 'AI Chat' }}</strong><div class="muted">{{ selected ? modeLabel : '选择一个会话开始学习' }}</div></div><el-tag v-if="selected" :type="selected.mode === 'AGENT' ? 'warning' : selected.mode === 'RAG' ? 'success' : 'info'">{{ selected.mode }}</el-tag></div></template><div v-if="!selected" class="empty-chat"><el-empty description="请从左侧选择会话，或新建一个会话" /></div><template v-else><div class="messages"><div v-for="item in messages" :key="item.id" class="message" :class="item.role"><div class="bubble"><div class="role">{{ item.role === 'user' ? '我' : 'StudyAgent' }}</div><div v-if="item.role === 'assistant' && item.id && !item.localOnly" class="feedback"><el-button link size="small" :disabled="feedbackSent.has(item.id)" @click="submitFeedback(item, 1)">👍 有帮助</el-button><el-button link size="small" :disabled="feedbackSent.has(item.id)" @click="submitFeedback(item, -1)">👎</el-button></div><div class="content">{{ item.content }}</div><div v-if="item.citations?.length" class="citations"><div class="citation-title">来源</div><div v-for="(c,index) in item.citations" :key="`${c.chunkId}-${index}`" class="citation">{{ c.fileName || '文档' }}<span v-if="c.page">，第{{ c.page }}页</span><span v-if="c.score">（{{ Number(c.score).toFixed(2) }}）</span><span v-if="c.documentVersionId" class="citation-trace"> · 版本 {{ c.documentVersionId }}</span><span v-if="c.knowledgeSnapshotId || c.snapshotId" class="citation-trace"> · 快照 {{ c.knowledgeSnapshotId || c.snapshotId }}</span></div></div></div></div><div v-if="streaming && selected?.id === streamConversationId" class="message assistant"><div class="bubble streaming"><div class="role">StudyAgent · 生成中</div><div class="content">{{ streamContent || '正在思考…' }}<span class="cursor">▋</span></div><div v-if="streamNodes.length" class="events">节点：{{ streamNodes.map(n => n.node || n.name || '处理中').join(' → ') }}</div><div v-if="streamToolCalls.length" class="events">工具：{{ streamToolCalls.map(t => t.name || t.tool || 'tool').join('、') }}</div><div v-if="streamCitations.length" class="citations"><div class="citation-title">来源</div><div v-for="(c,index) in streamCitations" :key="index" class="citation">{{ c.fileName || '文档' }}<span v-if="c.page">，第{{ c.page }}页</span><span v-if="c.documentVersionId" class="citation-trace"> · 版本 {{ c.documentVersionId }}</span><span v-if="c.knowledgeSnapshotId || c.snapshotId" class="citation-trace"> · 快照 {{ c.knowledgeSnapshotId || c.snapshotId }}</span></div></div></div></div><div v-if="streamError && selected?.id === streamConversationId" class="stream-error"><el-alert type="error" :title="streamError.message || '生成失败'" :closable="false" /></div></div><div class="composer"><el-input v-model="text" type="textarea" :rows="3" resize="none" placeholder="输入你的问题…" @keydown.ctrl.enter="send" /><div class="composer-actions"><span class="muted">Ctrl + Enter 发送</span><div><el-button v-if="streaming && selected?.id === streamConversationId" type="danger" @click="stop">停止生成</el-button><el-button v-else type="primary" :loading="sending" :disabled="!text.trim()" @click="send">发送</el-button></div></div></div></template></el-card></div>
  <el-dialog v-model="newDialog" title="新建 AI 会话" width="440px"><el-form label-position="top"><el-form-item label="标题"><el-input v-model="newTitle" maxlength="128" /></el-form-item><el-form-item label="项目"><el-select v-model="newProjectId" placeholder="选择项目" @change="loadKnowledgeChoices"><el-option v-for="project in projects" :key="project.id" :label="project.name" :value="project.id" /></el-select></el-form-item><el-form-item label="模式"><el-radio-group v-model="newMode"><el-radio-button label="CHAT">CHAT</el-radio-button><el-radio-button label="RAG">RAG</el-radio-button><el-radio-button label="AGENT">AGENT</el-radio-button></el-radio-group></el-form-item><el-form-item label="知识库" :required="newMode === 'RAG'"><el-select v-model="newKbId" clearable placeholder="选择项目知识库" @change="loadSnapshotChoices"><el-option v-for="base in knowledgeBases" :key="base.id" :label="base.name" :value="base.id" /></el-select></el-form-item><el-form-item v-if="newKbId" label="正式知识快照" :required="newMode === 'RAG'"><el-select v-model="newSnapshotId" clearable placeholder="选择已发布快照"><el-option v-for="snapshot in snapshots" :key="snapshot.id" :label="`Snapshot ${snapshot.snapshotNo} · ${snapshot.itemCount} 个文档`" :value="snapshot.id" /></el-select></el-form-item></el-form><template #footer><el-button @click="newDialog = false">取消</el-button><el-button type="primary" @click="create">创建</el-button></template></el-dialog>
</template>
<style scoped>.chat-page{height:calc(100vh - 110px);min-height:600px;display:grid;grid-template-columns:280px 1fr;gap:16px;max-width:1400px;margin:0 auto}.conversation-panel,.chat-panel{min-height:0}.conversation-panel :deep(.el-card__body),.chat-panel :deep(.el-card__body){height:calc(100% - 57px);box-sizing:border-box}.card-header,.chat-header,.composer-actions{display:flex;justify-content:space-between;align-items:center;gap:10px}.conversation-list{overflow:auto;height:100%}.conversation-item{display:flex;align-items:center;gap:6px;padding:12px 8px;border-bottom:1px solid #f0f2f5;cursor:pointer}.conversation-item.active{background:#ecf5ff}.conversation-item .title{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.meta,.muted,.events{color:#909399;font-size:12px}.chat-panel :deep(.el-card__body){display:flex;flex-direction:column}.messages{flex:1;overflow:auto;padding:4px 12px}.message{display:flex;margin:12px 0}.message.user{justify-content:flex-end}.bubble{max-width:min(82%,720px);padding:12px 15px;border-radius:12px;background:#f4f6f8;line-height:1.7;white-space:pre-wrap}.message.user .bubble{background:#409eff;color:white}.role{font-size:12px;opacity:.75;margin-bottom:4px}.feedback{display:flex;gap:4px;margin-top:4px}.content{word-break:break-word}.streaming{border:1px dashed #409eff}.cursor{animation:blink 1s infinite}.events{margin-top:8px}.citations{margin-top:10px;padding-top:8px;border-top:1px solid #dcdfe6;font-size:12px}.citation-title{font-weight:600;margin-bottom:3px}.citation{margin:3px 0}.composer{border-top:1px solid #ebeef5;padding-top:12px}.composer-actions{margin-top:8px}.empty-chat{display:grid;place-items:center;height:100%}@keyframes blink{50%{opacity:0}}@media(max-width:900px){.chat-page{grid-template-columns:1fr;height:auto}.conversation-panel{min-height:220px}.chat-panel{min-height:650px}}
</style>

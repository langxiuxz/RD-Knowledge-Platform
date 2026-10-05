<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { usePolling } from '../composables/usePolling'
import * as quizApi from '../api/quiz'

const quizzes = ref([]); const total = ref(0); const loading = ref(false); const dialog = ref(false); const detail = ref(null); const result = ref(null); const submitting = ref(false); const answers = reactive({}); const form = reactive({ title: '我的练习', topic: '', knowledgeBaseId: null, questionCount: 5, difficulty: 'MEDIUM', types: ['SINGLE'] }); const pollQuizId = ref(null)
const { start: startPolling, stop: stopPolling } = usePolling(async () => { if (!pollQuizId.value) return 'READY'; const r = await quizApi.getQuiz(pollQuizId.value); const data = r.data.data; const row = quizzes.value.find(x => x.id === pollQuizId.value); if (row) row.status = data.status; if (data.status === 'READY') await openQuiz(pollQuizId.value); if (data.status === 'FAILED') ElMessage.error(data.errorMessage || '测验生成失败'); return data.status }, { interval: 1500, timeout: 180000, onError: () => ElMessage.error('测验状态查询失败') })
async function load() { loading.value = true; try { const r = await quizApi.listQuizzes({ page: 1, pageSize: 50 }); quizzes.value = r.data.data?.rows || []; total.value = r.data.data?.total || 0 } catch (e) { ElMessage.error(e.response?.data?.msg || '测验加载失败') } finally { loading.value = false } }
function openGenerate() { Object.assign(form, { title: '我的练习', topic: '', knowledgeBaseId: null, questionCount: 5, difficulty: 'MEDIUM', types: ['SINGLE'] }); dialog.value = true }
async function generate() { try { const r = await quizApi.generateQuiz(form); dialog.value = false; pollQuizId.value = r.data.data.quizId; ElMessage.success('测验生成中'); await load(); await startPolling() } catch (e) { ElMessage.error(e.response?.data?.msg || '生成测验失败') } }
function normalizeOptions(options) { return Array.isArray(options) ? options : [] }
function answerValue(q) { return answers[q.id] ?? (q.type === 'MULTIPLE' ? [] : '') }
async function openQuiz(id) { try { const r = await quizApi.getQuiz(id); detail.value = r.data.data; result.value = null; for (const q of detail.value.questions || []) answers[q.id] = q.type === 'MULTIPLE' ? [] : ''; } catch (e) { ElMessage.error(e.response?.data?.msg || '测验详情加载失败') } }
async function submit() {
  if (!detail.value?.questions?.length) return
  submitting.value = true
  try {
    const body = { answers: detail.value.questions.map(q => ({ questionId: q.id, answer: answers[q.id] ?? (q.type === 'MULTIPLE' ? [] : '') })) }
    const submitted = await quizApi.submitQuiz(detail.value.id, body)
    ElMessage.success(`提交完成，得分 ${submitted.data.data.score}`)
    detail.value.status = 'COMPLETED'
    await load()
    try {
      const response = await quizApi.getQuizResult(detail.value.id)
      result.value = response.data.data
    } catch {
      ElMessage.warning('测验已提交成功，但结果详情暂时无法加载，请稍后从已完成测验中查看')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.msg || '提交失败')
  } finally {
    submitting.value = false
  }
}
function typeText(t) { return ({ SINGLE: '单选题', MULTIPLE: '多选题', JUDGE: '判断题', SHORT: '简答题' })[t] || t }
onMounted(load)
</script>
<template>
  <div class="page"><div class="page-heading"><div><h2>AI 测验</h2><p>用生成式题目检验学习效果，客观题由后端确定性评分。</p></div><el-button type="primary" @click="openGenerate">生成测验</el-button></div><el-card><el-table v-loading="loading" :data="quizzes" empty-text="暂无测验"><el-table-column prop="title" label="标题" min-width="180" /><el-table-column prop="difficulty" label="难度" width="100" /><el-table-column prop="questionCount" label="题数" width="80" /><el-table-column label="状态" width="110"><template #default="scope"><el-tag :type="scope.row.status === 'READY' || scope.row.status === 'COMPLETED' ? 'success' : scope.row.status === 'FAILED' ? 'danger' : 'warning'">{{ scope.row.status }}</el-tag></template></el-table-column><el-table-column label="操作" width="110"><template #default="scope"><el-button v-if="scope.row.status === 'READY'" link type="primary" @click="openQuiz(scope.row.id)">开始</el-button><el-button v-else-if="scope.row.status === 'COMPLETED'" link type="primary" @click="openQuiz(scope.row.id)">查看</el-button></template></el-table-column></el-table></el-card>
    <el-drawer v-model="detail" title="测验" size="min(720px, 95vw)" destroy-on-close><template v-if="detail"><el-alert v-if="detail.status === 'FAILED'" type="error" :title="detail.errorMessage || '生成失败'" show-icon /><div v-for="(q,index) in detail.questions" :key="q.id" class="question"><div class="question-title">{{ index + 1 }}. {{ q.content }} <el-tag size="small">{{ typeText(q.type) }}</el-tag></div><el-radio-group v-if="q.type === 'SINGLE' || q.type === 'JUDGE'" v-model="answers[q.id]" class="options"><el-radio v-for="option in (q.type === 'JUDGE' ? [{key:'true',text:'正确'},{key:'false',text:'错误'}] : normalizeOptions(q.options))" :key="option.key" :value="option.key">{{ option.key }}. {{ option.text }}</el-radio></el-radio-group><el-checkbox-group v-else-if="q.type === 'MULTIPLE'" v-model="answers[q.id]" class="options"><el-checkbox v-for="option in normalizeOptions(q.options)" :key="option.key" :value="option.key">{{ option.key }}. {{ option.text }}</el-checkbox></el-checkbox-group><el-input v-else v-model="answers[q.id]" type="textarea" :rows="3" placeholder="请输入答案" /></div><el-divider /><div class="drawer-footer"><el-button v-if="detail.status === 'READY'" type="primary" :loading="submitting" @click="submit">提交测验</el-button><template v-if="result"><el-alert type="success" :title="`得分：${result.score}`" :closable="false" /><div v-for="item in result.questions" :key="item.questionId" class="result-row"><el-tag :type="item.correct ? 'success' : 'danger'">{{ item.correct ? '正确' : '错误' }}</el-tag><span>{{ item.explanation || '暂无解析' }}</span></div></template></div></template></el-drawer>
    <el-dialog v-model="dialog" title="生成 AI 测验" width="500px"><el-form label-position="top"><el-form-item label="标题"><el-input v-model="form.title" maxlength="128" /></el-form-item><el-form-item label="主题"><el-input v-model="form.topic" placeholder="例如：Spring、MyBatis" /></el-form-item><el-form-item label="知识库 ID（可选）"><el-input-number v-model="form.knowledgeBaseId" :min="1" controls-position="right" /></el-form-item><el-form-item label="题目数量"><el-input-number v-model="form.questionCount" :min="1" :max="50" /></el-form-item><el-form-item label="难度"><el-select v-model="form.difficulty"><el-option label="简单" value="EASY" /><el-option label="中等" value="MEDIUM" /><el-option label="困难" value="HARD" /></el-select></el-form-item><el-form-item label="题型"><el-checkbox-group v-model="form.types"><el-checkbox label="SINGLE">单选</el-checkbox><el-checkbox label="MULTIPLE">多选</el-checkbox><el-checkbox label="JUDGE">判断</el-checkbox><el-checkbox label="SHORT">简答</el-checkbox></el-checkbox-group></el-form-item></el-form><template #footer><el-button @click="dialog=false">取消</el-button><el-button type="primary" :disabled="!form.types.length" @click="generate">开始生成</el-button></template></el-dialog>
  </div>
</template>
<style scoped>.page{max-width:1200px;margin:0 auto}.page-heading{display:flex;justify-content:space-between;align-items:center;margin-bottom:16px}h2{margin:0 0 6px}p{color:#909399;margin:0}.question{padding:18px 4px;border-bottom:1px solid #ebeef5}.question-title{font-size:16px;font-weight:600;line-height:1.6}.options{display:flex;flex-direction:column;align-items:flex-start;gap:9px;margin-top:14px}.drawer-footer{display:flex;flex-direction:column;gap:12px}.result-row{display:flex;gap:10px;align-items:center;margin-top:8px;font-size:13px}@media(max-width:600px){.page-heading{align-items:flex-start;flex-direction:column;gap:12px}}
</style>

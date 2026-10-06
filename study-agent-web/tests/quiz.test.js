import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import { computed, reactive, ref } from 'vue'
import { parse } from '@vue/compiler-sfc'

// Execute the SFC's actual setup logic with mocked network/UI boundaries.
function view(file, bindings) {
  const { descriptor } = parse(readFileSync(new URL(`../src/views/${file}`, import.meta.url), 'utf8'))
  const script = descriptor.scriptSetup.content.replace(/^import .*$/gm, '')
  const context = vm.createContext({ computed, reactive, ref, onMounted: () => {},
    usePolling: () => ({ start() {}, stop() {} }), ...bindings })
  vm.runInContext(script, context)
  return { context, template: descriptor.template.content, run: (code) => vm.runInContext(code, context) }
}
const response = (data) => ({ data: { data } })
const questions = [{ id: 11, type: 'SINGLE' }, { id: 12, type: 'MULTIPLE' }]
const result = { score: 50, questions: [
  { questionId: 11, userAnswer: 'B', correctAnswer: 'A', correct: false, explanation: 'A is correct' },
  { questionId: 12, userAnswer: ['A', 'C'], correctAnswer: ['C', 'A'], correct: true }
] }
function setup(overrides = {}) {
  const calls = []
  const quizApi = {
    getQuiz: async (id) => response({ id, status: 'READY', questions }),
    listQuizzes: async () => response({ rows: [], total: 0 }),
    submitQuiz: async (id, body) => { calls.push({ id, body }); return response({ score: 50 }) },
    getQuizResult: async () => response(result), ...overrides
  }
  const messages = []
  const ElMessage = Object.fromEntries(['success', 'error', 'warning'].map(type => [type, text => messages.push({ type, text })]))
  return { ...view('QuizView.vue', { quizApi, ElMessage }), calls, messages }
}

test('submit sends scalar/array answers and displays incorrect answers with references', async () => {
  const page = setup()
  await page.run('openQuiz(50)')
  page.run("answers[11] = 'B'; answers[12] = ['A', 'C']")
  await page.run('submit()')
  assert.equal(page.calls.length, 1)
  assert.deepEqual(JSON.parse(JSON.stringify(page.calls[0])), { id: 50, body: { answers: [
    { questionId: 11, answer: 'B' }, { questionId: 12, answer: ['A', 'C'] }
  ] } })
  assert.equal(page.run('detail.value.status'), 'COMPLETED')
  assert.equal(page.run('wrongCount.value'), 1)
  assert.equal(page.run('resultByQuestion.value.get(11).correctAnswer'), 'A')
  assert.equal(page.run('drawerOpen.value'), true)
  assert.match(page.template, /v-model="drawerOpen"/)
  assert.match(page.template, /你的答案/)
  assert.match(page.template, /正确答案/)
  await page.run('submit()')
  assert.equal(page.calls.length, 1, 'completed quiz cannot be submitted again')
})

test('opening a completed quiz loads its result and restores submitted answers', async () => {
  const page = setup({ getQuiz: async id => response({ id, status: 'COMPLETED', questions }) })
  await page.run('openQuiz(50)')
  assert.equal(page.run('result.value.score'), 50)
  assert.equal(page.run('answers[11]'), 'B')
  assert.equal(page.run("formatAnswer(answers[12])"), 'A、C')
  page.run('drawerOpen.value = false')
  assert.equal(page.run('detail.value.id'), 50, 'closing drawer does not replace detail with a boolean')
})

test('result loading failure preserves successful submission and supports retry', async () => {
  let fails = true
  const page = setup({ getQuizResult: async () => { if (fails) throw new Error('temporary failure'); return response(result) } })
  await page.run('openQuiz(50)')
  await page.run('submit()')
  assert.equal(page.run('detail.value.status'), 'COMPLETED')
  assert.equal(page.run('resultError.value'), true)
  assert.equal(page.run('submitting.value'), false)
  assert.equal(page.messages.filter(m => m.type === 'error').length, 0)
  fails = false
  await page.run('loadResult()')
  assert.equal(page.run('resultError.value'), false)
  assert.equal(page.run('wrongCount.value'), 1)
})

test('failed submission preserves answers and READY state for retry', async () => {
  const page = setup({ submitQuiz: async () => { throw new Error('AI timeout') } })
  await page.run('openQuiz(50)')
  page.run("answers[11] = 'B'")
  await page.run('submit()')
  assert.equal(page.run('detail.value.status'), 'READY')
  assert.equal(page.run('answers[11]'), 'B')
  assert.equal(page.run('submitting.value'), false)
  assert.equal(page.run('result.value'), null)
})

test('wrong-question mastery toggles both directions without mutating failed requests', async () => {
  const calls = []
  const page = view('WrongQuestionsView.vue', {
    quizApi: { updateWrongQuestionMastered: async (...args) => calls.push(args),
      listWrongQuestions: async () => response({ rows: [] }) },
    ElMessage: { success() {}, error() {} }, ElMessageBox: {}
  })
  await page.run('mastered({ id: 801, mastered: false })')
  await page.run('mastered({ id: 801, mastered: true })')
  assert.deepEqual(calls, [[801, true], [801, false]])
  assert.doesNotMatch(page.template, /scope.row.mastered=false/)
  assert.doesNotMatch(page.template, /scope.row.questionId/)
})

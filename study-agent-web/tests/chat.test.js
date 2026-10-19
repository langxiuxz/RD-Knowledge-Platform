import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import * as Vue from 'vue'
import { compile } from '@vue/compiler-dom'
import { compileScript, parse } from '@vue/compiler-sfc'
import { renderToString } from '@vue/server-renderer'

const { descriptor } = parse(readFileSync(new URL('../src/views/ChatView.vue', import.meta.url), 'utf8'))
const script = descriptor.scriptSetup.content.replace(/^import .*$/gm, '')
const bindingNames = Object.keys(compileScript(descriptor, { id: 'chat-test' }).bindings)
const render = new Function('Vue', compile(descriptor.template.content, {
  mode: 'function', prefixIdentifiers: true
}).code)(Vue)
const composable = readFileSync(new URL('../src/composables/useSseChat.js', import.meta.url), 'utf8')
  .replace(/^import .*$/gm, '').replace('export function useSseChat', 'function useSseChat')
const response = data => ({ data: { data } })

// Execute the real view/composable and render the actual template: state-only tests
// would miss the nested-ref unwrapping bug. Mock only network and UI boundaries.
async function setup() {
  const requests = [], stops = [], notices = []
  let history = []
  let loadHistory = async () => response({ rows: history })
  const context = vm.createContext({
    ...Vue, AbortController, onMounted: () => {},
    streamConversation: (id, payload, options) => new Promise((resolve, reject) => {
      requests.push({ id, payload, ...options, resolve, reject })
      options.signal.addEventListener('abort', () => {
        const error = new Error('Aborted')
        error.name = 'AbortError'
        reject(error)
      }, { once: true })
    }),
    stopGeneration: async (id, traceId) => { stops.push({ id, traceId }) },
    conversationApi: { listMessages: (...args) => loadHistory(...args) },
    feedbackMessage: async () => {},
    ElMessage: Object.fromEntries(['error', 'warning', 'success', 'info'].map(type => [
      type, message => notices.push({ type, message })
    ])),
    ElMessageBox: { confirm: async () => {} }
  })
  vm.runInContext(composable, context)
  vm.runInContext(script, context)
  const run = code => vm.runInContext(code, context)
  const bindings = run(`({ ${bindingNames.join(', ')} })`)
  const stream = run('stream')
  await run("selectConversation({ id: 7, title: '测试会话', mode: 'CHAT' })")
  const stub = Vue.defineComponent({
    props: ['title', 'modelValue', 'disabled'],
    setup(props, { slots, attrs }) {
      return () => Vue.h('div', attrs, [props.title,
        slots.header?.(), slots.default?.(), slots.footer?.()])
    }
  })
  async function html() {
    const app = Vue.createSSRApp({ setup: () => bindings, render })
    for (const name of new Set(descriptor.template.content.match(/el-[\w-]+/g))) {
      app.component(name, name === 'el-dialog'
        ? Vue.defineComponent({ props: ['modelValue'], setup: (props, { slots }) =>
          () => props.modelValue ? Vue.h('div', slots.default?.()) : null })
        : stub)
    }
    return renderToString(app)
  }
  return { run, stream, requests, stops, notices, html, setHistory: rows => { history = rows }, setHistoryLoader: loader => { loadHistory = loader } }
}

function assertIdle(html) {
  assert.doesNotMatch(html, /StudyAgent · 生成中|正在思考…|class="cursor"|停止生成|class="stream-error"|生成失败/)
  assert.match(html, />发送</)
}

async function begin(page) {
  page.run("text.value = '你好'")
  const sending = page.run('send()')
  assert.equal(page.requests.length, 1)
  return { sending, request: page.requests[0] }
}

test('an empty selected conversation shows neither generation nor failure placeholders', async () => {
  const page = await setup()
  assert.equal(page.stream.streaming.value, false)
  assert.equal(page.stream.error.value, null)
  assertIdle(await page.html())
})

test('empty or whitespace-only input never starts generation', async () => {
  const page = await setup()
  for (const input of ['', '  \n\t  ']) {
    page.run(`text.value = ${JSON.stringify(input)}`)
    await page.run('send()')
    assertIdle(await page.html())
  }
  assert.equal(page.requests.length, 0)
  assert.equal(page.run('messages.value.length'), 0)
})

test('an active generation renders thinking, tokens, nodes, tools and citations', async () => {
  const page = await setup()
  const { sending, request } = await begin(page)
  let html = await page.html()
  assert.match(html, /StudyAgent · 生成中/)
  assert.match(html, /正在思考…/)
  assert.match(html, /class="cursor"/)
  assert.match(html, /停止生成/)
  assert.doesNotMatch(html, /class="stream-error"|生成失败/)
  request.onEvent('node', { node: 'retrieve_context' })
  request.onEvent('tool', { name: 'get_wrong_questions' })
  request.onEvent('citation', { fileName: '学习资料.pdf', page: 2 })
  request.onEvent('token', { content: '你好，开始学习吧！' })
  html = await page.html()
  assert.match(html, /你好，开始学习吧！/)
  assert.doesNotMatch(html, /正在思考…/)
  assert.match(html, /节点：retrieve_context/)
  assert.match(html, /工具：get_wrong_questions/)
  assert.match(html, /学习资料.pdf.*第2页/)
  page.run("text.value = '重复发送'")
  await page.run('send()')
  assert.equal(page.requests.length, 1)
  request.onEvent('done', { messageId: 9 })
  request.resolve()
  await sending
})

test('completion hides the streaming placeholder and displays saved history', async () => {
  const page = await setup()
  const { sending, request } = await begin(page)
  request.onEvent('token', { content: '完成的回答' })
  page.setHistory([{ id: 9, role: 'assistant', content: '完成的回答', citations: [] }])
  request.onEvent('done', { messageId: 9 })
  request.resolve()
  await sending
  const html = await page.html()
  assertIdle(html)
  assert.match(html, /完成的回答/)
})

test('only a real SSE error shows its message; retry clears the error', async () => {
  const page = await setup()
  const { sending, request } = await begin(page)
  request.onEvent('error', { message: 'AI 服务暂时不可用' })
  request.resolve()
  await sending
  let html = await page.html()
  assert.match(html, /class="stream-error"/)
  assert.match(html, /AI 服务暂时不可用/)
  assert.doesNotMatch(html, /StudyAgent · 生成中|class="cursor"|停止生成/)
  page.run("text.value = '重试'")
  const retry = page.run('send()')
  html = await page.html()
  assert.match(html, /StudyAgent · 生成中/)
  assert.doesNotMatch(html, /class="stream-error"|AI 服务暂时不可用/)
  page.requests[1].onEvent('done', {})
  page.requests[1].resolve()
  await retry
  assertIdle(await page.html())
})

test('a failed stream request shows the actual failure instead of a phantom generation', async () => {
  const page = await setup()
  const { sending, request } = await begin(page)
  request.reject(new Error('网络连接失败'))
  await sending
  const html = await page.html()
  assert.match(html, /class="stream-error"/)
  assert.match(html, /网络连接失败/)
  assert.doesNotMatch(html, /StudyAgent · 生成中|class="cursor"|停止生成/)
})

test('stopping generation hides the placeholder without showing a failure', async () => {
  const page = await setup()
  const { sending, request } = await begin(page)
  request.onEvent('start', { traceId: 'trace-7', messageId: 9 })
  await page.run('stop()')
  await sending
  assert.deepEqual(page.stops, [{ id: 7, traceId: 'trace-7' }])
  assert.equal(request.signal.aborted, true)
  assertIdle(await page.html())
})


test('done immediately hands the answer to the message list before EOF or history arrives', async () => {
  const page = await setup()
  const { sending, request } = await begin(page)
  request.onEvent('token', { content: '不会消失的回答' })
  request.onEvent('citation', { fileName: '资料.pdf', page: 3 })
  request.onEvent('done', { messageId: 9 })
  let html = await page.html()
  assert.match(html, /不会消失的回答/)
  assert.match(html, /资料.pdf/)
  assertIdle(html)

  let resolveHistory
  page.setHistoryLoader(() => new Promise(resolve => { resolveHistory = resolve }))
  request.resolve()
  await new Promise(resolve => setImmediate(resolve))
  html = await page.html()
  assert.match(html, /不会消失的回答/)
  // The done event has arrived, but the prior send still owns history synchronization.
  page.run("text.value = '不能重叠发送'")
  await page.run('send()')
  assert.equal(page.requests.length, 1)
  resolveHistory(response({ rows: [
    { id: 9, role: 'assistant', content: '不会消失的回答', citations: [{ fileName: '资料.pdf', page: 3 }] },
    { id: 8, role: 'user', content: '你好', citations: [] }
  ] }))
  await sending
  html = await page.html()
  assert.equal((html.match(/不会消失的回答/g) || []).length, 1)
  assert.equal(page.run('messages.value.length'), 2)
  assert.ok(html.indexOf('你好') < html.indexOf('不会消失的回答'))
})

test('history synchronization failure does not erase a completed answer', async () => {
  const page = await setup()
  const { sending, request } = await begin(page)
  page.setHistoryLoader(async () => { throw new Error('历史加载失败') })
  request.onEvent('token', { content: '同步失败也能看到' })
  request.onEvent('done', { messageId: 9 })
  request.resolve()
  await sending
  assert.match(await page.html(), /同步失败也能看到/)
  assertIdle(await page.html())
})

test('stale missing or empty history rows cannot overwrite the streamed answer', async () => {
  for (const rows of [[], [{ id: 9, role: 'assistant', content: '', citations: [] }]]) {
    const page = await setup()
    const { sending, request } = await begin(page)
    page.setHistory(rows)
    request.onEvent('token', { content: '暂未同步的回答' })
    request.onEvent('citation', { fileName: '参考资料.pdf' })
    request.onEvent('done', { messageId: 9 })
    request.resolve()
    await sending
    const html = await page.html()
    assert.match(html, /暂未同步的回答/)
    assert.match(html, /参考资料.pdf/)
    assert.equal(page.run("messages.value.filter(item => item.role === 'assistant').length"), 1)
  }
})

test('history in descending ID order renders chronologically on entry and refresh', async () => {
  const page = await setup()
  page.setHistory([
    { id: 4, role: 'assistant', content: '第二个回答', citations: [] },
    { id: 3, role: 'user', content: '第二个问题', citations: [] },
    { id: 2, role: 'assistant', content: '第一个回答', citations: [] },
    { id: 1, role: 'user', content: '第一个问题', citations: [] }
  ])
  await page.run("selectConversation({ id: 7, title: '测试会话', mode: 'CHAT' })")
  assert.deepEqual(Array.from(page.run('messages.value.map(item => item.id)')), [1, 2, 3, 4])
  await page.run('reloadMessages()')
  assert.deepEqual(Array.from(page.run('messages.value.map(item => item.id)')), [1, 2, 3, 4])
})

test('an interrupted stream preserves partial output as an ordinary message', async () => {
  for (const terminal of ['error', 'stop']) {
    const page = await setup()
    const { sending, request } = await begin(page)
    request.onEvent('start', { traceId: 'trace-7', messageId: 9 })
    request.onEvent('token', { content: '已经输出的部分回答' })
    if (terminal === 'error') {
      request.onEvent('error', { message: 'AI 服务中断' })
      request.resolve()
    } else {
      await page.run('stop()')
    }
    await sending
    const html = await page.html()
    assert.match(html, /已经输出的部分回答/)
    assert.doesNotMatch(html, /StudyAgent · 生成中|class="cursor"|停止生成/)
    if (terminal === 'error') assert.match(html, /AI 服务中断/)
  }
})

test('history refresh replaces the local snapshot once and the next turn keeps prior answers', async () => {
  const page = await setup()
  const first = await begin(page)
  first.request.onEvent('token', { content: '第一轮回答' })
  first.request.onEvent('done', { messageId: 9 })
  first.request.resolve()
  await first.sending
  page.setHistory([
    { id: 9, role: 'assistant', content: '第一轮回答', citations: [] },
    { id: 8, role: 'user', content: '你好', citations: [] }
  ])
  await page.run('reloadMessages()')
  assert.equal(page.run('messages.value.some(item => item.pendingSync)'), false)
  page.run("text.value = '第二轮问题'")
  const secondSending = page.run('send()')
  const second = page.requests[1]
  second.onEvent('start', { traceId: 'trace-2', messageId: 10 })
  second.onEvent('token', { content: '第二轮回答' })
  second.onEvent('done', { messageId: 11 })
  let html = await page.html()
  assert.equal((html.match(/第一轮回答/g) || []).length, 1)
  assert.equal((html.match(/第二轮回答/g) || []).length, 1)
  assert.equal(page.run('messages.value.at(-1).id'), 11)
  page.setHistory([
    { id: 11, role: 'assistant', content: '第二轮回答', citations: [] },
    { id: 10, role: 'user', content: '第二轮问题', citations: [] },
    { id: 9, role: 'assistant', content: '第一轮回答', citations: [] },
    { id: 8, role: 'user', content: '你好', citations: [] }
  ])
  second.resolve()
  await secondSending
  html = await page.html()
  assert.deepEqual(Array.from(page.run('messages.value.map(item => item.id)')), [8, 9, 10, 11])
  assert.equal((html.match(/第二轮回答/g) || []).length, 1)
})

test('a late history response from the previous conversation cannot replace the current messages', async () => {
  const page = await setup()
  const { sending, request } = await begin(page)
  request.onEvent('token', { content: '旧会话回答' })
  request.onEvent('done', { messageId: 9 })
  let resolveOldHistory
  page.setHistoryLoader(id => id === 7
    ? new Promise(resolve => { resolveOldHistory = resolve })
    : Promise.resolve(response({ rows: [{ id: 20, role: 'assistant', content: '新会话历史', citations: [] }] })))
  request.resolve()
  await new Promise(resolve => setImmediate(resolve))
  await page.run("selectConversation({ id: 12, title: '另一会话', mode: 'CHAT' })")
  resolveOldHistory(response({ rows: [{ id: 9, role: 'assistant', content: '旧会话回答', citations: [] }] }))
  await sending
  const html = await page.html()
  assert.match(html, /新会话历史/)
  assert.doesNotMatch(html, /旧会话回答/)
})

test('generation events stay in their originating conversation when the user switches chats', async () => {
  const page = await setup()
  const { sending, request } = await begin(page)
  request.onEvent('token', { content: '原会话的回答' })
  page.setHistory([{ id: 20, role: 'assistant', content: '另一个会话', citations: [] }])
  await page.run("selectConversation({ id: 12, title: '另一会话', mode: 'CHAT' })")
  let html = await page.html()
  assert.doesNotMatch(html, /原会话的回答|StudyAgent · 生成中/)
  request.onEvent('done', { messageId: 9 })
  request.resolve()
  await sending
  html = await page.html()
  assert.match(html, /另一个会话/)
  assert.doesNotMatch(html, /原会话的回答/)
})

test('a buffered earlier answer retains its chronological position when a later turn synchronizes', async () => {
  const page = await setup()
  const first = await begin(page)
  first.request.onEvent('token', { content: '较早的未同步回答' })
  first.request.onEvent('done', { messageId: 9 })
  first.request.resolve()
  await first.sending
  page.run("text.value = '后续问题'")
  const secondSending = page.run('send()')
  const second = page.requests[1]
  second.onEvent('token', { content: '后续回答' })
  second.onEvent('done', { messageId: 11 })
  page.setHistory([
    { id: 11, role: 'assistant', content: '后续回答', citations: [] },
    { id: 10, role: 'user', content: '后续问题', citations: [] },
    { id: 8, role: 'user', content: '你好', citations: [] }
  ])
  second.resolve()
  await secondSending
  assert.deepEqual(Array.from(page.run('messages.value.map(item => item.id)')), [8, 9, 10, 11])
})

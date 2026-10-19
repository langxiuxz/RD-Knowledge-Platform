import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import { computed, ref, watch, nextTick } from 'vue'
import * as logic from '../src/utils/pomodoro.js'

const source = readFileSync(new URL('../src/composables/usePomodoro.js', import.meta.url), 'utf8')
  .replace(/^import[\s\S]*?from '[^']+'\r?\n/gm, '').replace('export function usePomodoro', 'function usePomodoro')
const flush = async () => { for (let i = 0; i < 20; i++) await Promise.resolve() }

function mount({ storage = new Map(), auth = { user: { id: 7 } }, upload = async () => ({ data: { code: 1 } }), locks = true } = {}) {
  let clock = 1000
  let interval
  let cleanup
  let lockQueue = Promise.resolve()
  const events = new Map()
  const messages = []
  const navigator = { onLine: true }
  if (locks) navigator.locks = { request: (_, callback) => {
    const pending = lockQueue.then(callback)
    lockQueue = pending.catch(() => {})
    return pending
  } }
  const context = vm.createContext({ ...logic, computed, ref, watch,
    onBeforeUnmount: callback => { cleanup = callback },
    useAuthStore: () => auth, createLearningRecord: upload, structuredClone,
    Date: class extends Date { static now() { return clock } },
    localStorage: { getItem: key => storage.get(key) || null, setItem: (key, value) => storage.set(key, value) },
    navigator, document: { hidden: false, addEventListener() {}, removeEventListener() {} },
    window: { setInterval: callback => { interval = callback; return 1 },
      addEventListener: (name, cb) => events.set(name, cb), removeEventListener: name => events.delete(name) },
    clearInterval() {}, ElMessage: Object.fromEntries(['success', 'warning', 'info'].map(k => [k, message => messages.push(message)]))
  })
  const timer = vm.runInContext(`${source}\nusePomodoro()`, context)
  return { timer, storage, navigator, auth, messages, events, cleanup: () => cleanup(),
    tick: async time => { clock = time; interval(); await flush() } }
}

test('failed upload stays durable, retry uses the same ID, and refresh restores today', async () => {
  const calls = []
  let failing = true
  const app = mount({ upload: async payload => { calls.push(payload); if (failing) throw Error('offline'); return { data: { code: 1 } } } })
  await flush()
  await app.timer.act('start')
  const deadline = app.timer.state.value.deadline
  await app.tick(deadline)
  await app.timer.sync()
  assert.equal(app.timer.pending.value, 1)
  assert.equal(app.timer.today.value.count, 1)
  const key = logic.pomodoroKey(7)
  assert.equal(JSON.parse(app.storage.get(key)).rounds[0].synced, false)
  assert.match(app.timer.syncError.value, /offline/)
  failing = false
  await app.timer.sync()
  assert.equal(app.timer.pending.value, 0)
  assert.equal(new Set(calls.map(r => r.clientRecordId)).size, 1)
  const refresh = mount({ storage: app.storage })
  await refresh.tick(deadline)
  assert.equal(refresh.timer.today.value.minutes, 50)
  assert.equal(refresh.timer.state.value.mode, 'rest')
  app.cleanup(); refresh.cleanup()
})

test('user changes isolate saved state and stop reading the previous users rounds', async () => {
  const auth = { user: ref({ id: 7 }) }
  const actualAuth = { get user() { return auth.user.value } }
  const app = mount({ auth: actualAuth })
  await flush()
  await app.timer.act('settings', { topic: 'Private topic' })
  await app.timer.act('start')
  auth.user.value = { id: 8 }
  await nextTick(); await flush()
  assert.equal(app.timer.state.value.status, 'idle')
  assert.equal(app.timer.state.value.topic, '')
  assert.equal(JSON.parse(app.storage.get(logic.pomodoroKey(7))).topic, 'Private topic')
  app.cleanup()
})

test('corrupt local storage degrades to memory with an explicit warning', async () => {
  const app = mount({ storage: new Map([[logic.pomodoroKey(7), '{bad']]), locks: false })
  await flush()
  assert.ok(app.timer.storageError.value)
  await app.timer.act('start')
  await app.timer.act('distraction', 'Later')
  assert.equal(app.timer.state.value.status, 'running')
  assert.equal(app.timer.state.value.distractions.length, 1)
  app.cleanup()
})

test('offline completion is persisted before any upload and online event retries it', async () => {
  const calls = []
  const app = mount({ upload: async payload => { calls.push(payload); return { data: { code: 1 } } } })
  await flush()
  app.navigator.onLine = false
  await app.timer.act('start')
  await app.tick(app.timer.state.value.deadline)
  assert.equal(calls.length, 0)
  assert.equal(app.timer.pending.value, 1)
  app.navigator.onLine = true
  app.events.get('online')()
  await flush()
  assert.equal(calls.length, 1)
  assert.equal(app.timer.pending.value, 0)
  app.cleanup()
})

import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import * as Vue from 'vue'
import { compile } from '@vue/compiler-dom'
import { compileScript, parse } from '@vue/compiler-sfc'
import { renderToString } from '@vue/server-renderer'
import * as rules from '../src/utils/onboarding.js'

const { descriptor } = parse(readFileSync(new URL('../src/views/OnboardingView.vue', import.meta.url), 'utf8'))
const script = descriptor.scriptSetup.content.replace(/^import .*$/gm, '')
const bindingNames = Object.keys(compileScript(descriptor, { id: 'onboarding-test' }).bindings)
const render = new Function('Vue', compile(descriptor.template.content, {
  mode: 'function', prefixIdentifiers: true
}).code)(Vue)
const response = data => ({ data: { data } })
const makeProject = (id, myRole = 'OWNER', status = 'ACTIVE') => ({ id, name: `Project ${id}`, myRole, status })
const makeTask = (id, type = 'READ', status = 'PENDING') => ({
  id, type, status, title: `Task ${id}`, description: 'Read the service overview',
  resourceType: type === 'READ' ? 'DOCUMENT_VERSION' : type === 'CHECKPOINT' ? 'NONE' : type,
  resourceId: type === 'CHECKPOINT' ? null : 300, completionRule: ['READ', 'CHECKPOINT'].includes(type) ? 'MANUAL' : 'AUTO',
  estimatedMinutes: 30, required: true, sortOrder: 1, prerequisiteTaskIds: []
})
const makePath = (id, status = 'DRAFT') => ({
  path: { id, projectId: 1, name: `Path ${id}`, status, versionNo: status === 'DRAFT' ? 0 : 1, knowledgeSnapshotId: 55 },
  tasks: []
})
const makeAssignment = (id, assigneeUserId = 7, status = 'ASSIGNED') => ({
  assignment: { id, projectId: 1, pathId: 10, pathVersion: 1, knowledgeSnapshotId: 55, assigneeUserId, status }, tasks: []
})
const plain = value => JSON.parse(JSON.stringify(value))
function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}
function setup(overrides = {}) {
  const calls = [], notices = []
  let unmount, mount
  const record = name => async (...args) => { calls.push({ name, args: plain(args) }); return response({ id: 90 }) }
  const api = {
    listOnboardingPaths: async () => response({ rows: [], total: 0 }),
    listAssignments: async () => response({ rows: [], total: 0 }),
    getOnboardingPath: async id => response(makePath(id)),
    getAssignment: async id => response(makeAssignment(id)),
    createOnboardingPath: record('createPath'), updateOnboardingPath: record('updatePath'),
    addPathTask: record('addTask'), updatePathTask: record('updateTask'), deletePathTask: record('deleteTask'),
    reorderPathTasks: record('reorder'), publishOnboardingPath: record('publish'),
    assignOnboardingPath: record('assign'), startAssignment: record('start'),
    completeAssignmentTask: record('complete'), submitAssignment: record('submit'),
    ...overrides.api
  }
  const context = vm.createContext({
    ...Vue, ...rules, api, makeProject, makePath, makeTask, makeAssignment,
    listProjects: overrides.listProjects || (async () => response({ rows: [], total: 0 })),
    listProjectMembers: overrides.listProjectMembers || (async () => response({ rows: [], total: 0 })),
    useAuthStore: () => ({ user: { id: 7 } }),
    onMounted: callback => { mount = callback }, onBeforeUnmount: callback => { unmount = callback },
    ElMessage: Object.fromEntries(['error', 'warning', 'success'].map(type => [type, text => notices.push({ type, text })])),
    ElMessageBox: { confirm: async () => {}, prompt: async () => ({ value: '' }) }
  })
  vm.runInContext(script, context)
  const run = code => vm.runInContext(code, context)
  const bindings = run(`({ ${bindingNames.join(', ')} })`)
  async function html() {
    const app = Vue.createSSRApp({ setup: () => bindings, render })
    app.directive('loading', {})
    for (const name of new Set(descriptor.template.content.match(/el-[\w-]+/g))) {
      app.component(name, Vue.defineComponent({
        props: ['title', 'label', 'modelValue'],
        setup: (props, { slots }) => () => {
          if (name === 'el-dialog' && !props.modelValue) return null
          return Vue.h('div', [props.title, props.label,
            slots.header?.(), name === 'el-table-column' ? null : slots.default?.(), slots.footer?.()])
        }
      }))
    }
    return renderToString(app)
  }
  run('projects.value = [makeProject(1), makeProject(2)]; projectId.value = 1')
  return { run, html, calls, notices, context, mount: () => mount(), unmount: () => unmount() }
}

test('Onboarding permissions require active manager or the identified assignee', () => {
  assert.equal(rules.isPathManager(makeProject(1, 'MAINTAINER')), true)
  assert.equal(rules.isPathManager(makeProject(1, 'OWNER', 'ARCHIVED')), false)
  assert.equal(rules.isPathManager(makeProject(1, 'LEARNER')), false)
  assert.equal(rules.isExecutable({ assigneeUserId: 7, status: 'ASSIGNED' }, 7), true)
  assert.equal(rules.isExecutable({ assigneeUserId: 7, status: 'IN_PROGRESS' }, 8), false)
  assert.equal(rules.isExecutable({ assigneeUserId: 7, status: 'REVIEW_PENDING' }, 7), false)
  assert.equal(rules.isExecutable({ status: 'ASSIGNED' }, undefined), false)
})

test('only pending manual READ and CHECKPOINT tasks can be completed directly', () => {
  const active = makeAssignment(10, 7, 'IN_PROGRESS').assignment
  for (const type of ['READ', 'CHECKPOINT']) assert.equal(rules.canCompleteTask(active, makeTask(1, type), 7), true)
  for (const type of ['QUIZ', 'DRILL']) assert.equal(rules.canCompleteTask(active, makeTask(1, type), 7), false)
  for (const status of ['LOCKED', 'FAILED', 'COMPLETED', 'SKIPPED']) assert.equal(rules.canCompleteTask(active, makeTask(1, 'READ', status), 7), false)
  assert.equal(rules.canCompleteTask(active, { ...makeTask(1), completionRule: 'AUTO' }, 7), false)
  assert.equal(rules.canCompleteTask(active, null, 7), false)
  assert.deepEqual(rules.taskResource('DRILL'), ['DRILL', 'AUTO'])
  assert.deepEqual(rules.taskResource('CHECKPOINT'), ['NONE', 'MANUAL'])
})

test('submitting requires started work and all required tasks, not optional tasks', () => {
  const active = makeAssignment(10, 7, 'IN_PROGRESS').assignment
  assert.equal(rules.canSubmitAssignment(active, [], 7), false)
  assert.equal(rules.canSubmitAssignment(active, [makeTask(1)], 7), false)
  assert.equal(rules.canSubmitAssignment(active, [makeTask(1, 'READ', 'COMPLETED'), { ...makeTask(2), required: false }], 7), true)
  assert.equal(rules.canSubmitAssignment(makeAssignment(10).assignment, [makeTask(1, 'READ', 'COMPLETED')], 7), false)
})

test('only active managers see and can invoke path editing controls', async () => {
  for (const [role, status] of [['OWNER', 'ACTIVE'], ['MAINTAINER', 'ACTIVE'], ['LEARNER', 'ACTIVE'], ['OWNER', 'ARCHIVED']]) {
    const page = setup()
    page.context.candidate = makeProject(1, role, status)
    page.run('projects.value = [candidate]; selected.value = makePath(10)')
    const allowed = role !== 'LEARNER' && status === 'ACTIVE'
    assert.equal((await page.html()).includes('新建路径'), allowed)
    page.run('openPath(true); openTask()')
    assert.equal(page.run('pathDialog.value'), allowed)
    assert.equal(page.run('taskDialog.value'), allowed)
    if (!allowed) {
      await page.run('savePath()'); await page.run('saveTask()'); await page.run('publish()')
      assert.deepEqual(page.calls, [])
    }
  }
})

test('path creation submits only business fields and suppresses duplicate pending saves', async () => {
  const pending = deferred(), requests = []
  const page = setup({ api: { createOnboardingPath: (...args) => { requests.push(plain(args)); return pending.promise } } })
  page.run("openPath(); Object.assign(pathForm, { name: ' Orders ', targetRole: 'on-call', description: 'handover', knowledgeSnapshotId: 55, userId: 999, createdBy: 999 })")
  const first = page.run('savePath()')
  await page.run('savePath()')
  assert.deepEqual(requests, [[1, { name: 'Orders', targetRole: 'on-call', description: 'handover', knowledgeSnapshotId: 55 }]])
  assert.equal(page.run('busy.value'), true)
  pending.resolve(response({ id: 90 }))
  await first
  assert.equal(page.run('busy.value'), false)
  assert.equal(page.run('selected.value.path.id'), 90)
})

test('task edits submit explicit business fields and copy dependency ids', async () => {
  const page = setup()
  page.run("selected.value = makePath(10); selected.value.tasks = [makeTask(11)]; openTask(selected.value.tasks[0]); Object.assign(taskForm, { title: ' Architecture ', prerequisiteTaskIds: [12], userId: 999 })")
  await page.run('saveTask()')
  assert.deepEqual(page.calls, [{ name: 'updateTask', args: [11, {
    type: 'READ', description: 'Read the service overview', estimatedMinutes: 30, required: true,
    sortOrder: 1, prerequisiteTaskIds: [12], title: 'Architecture', resourceId: 300,
    resourceType: 'DOCUMENT_VERSION', completionRule: 'MANUAL'
  }] }])
})

test('switching projects clears old dialogs, form targets, member lists and totals', async () => {
  const page = setup()
  page.run('selected.value = makePath(10); openPath(true); openTask(); assignDialog.value = true; learners.value = [{ userId: 7 }]; learnerIds.value = [7]; pathTotal.value = 99; assignmentTotal.value = 99')
  await page.run('projectId.value = 2; changeProject()')
  for (const name of ['selected', 'execution', 'pathEditingId', 'taskEditingId']) assert.equal(page.run(`${name}.value`), null)
  for (const name of ['pathDialog', 'taskDialog', 'assignDialog']) assert.equal(page.run(`${name}.value`), false)
  for (const name of ['learners', 'learnerIds']) assert.equal(page.run(`${name}.value.length`), 0)
  await page.run('savePath()'); await page.run('saveTask()'); await page.run('assign()')
  assert.deepEqual(page.calls, [])
})

test('late list, path and assignment responses cannot overwrite a newer project', async () => {
  const oldList = deferred(), oldPath = deferred(), oldAssignment = deferred()
  const page = setup({ api: {
    listOnboardingPaths: id => id === 1 ? oldList.promise : Promise.resolve(response({ rows: [{ id: 20 }], total: 1 })),
    getOnboardingPath: () => oldPath.promise, getAssignment: () => oldAssignment.promise
  } })
  const requests = [page.run('refresh()'), page.run('selectPath(10)'), page.run('selectAssignment(30)')]
  await page.run('projectId.value = 2; changeProject()')
  oldList.resolve(response({ rows: [{ id: 10 }], total: 1 }))
  oldPath.resolve(response(makePath(10))); oldAssignment.resolve(response(makeAssignment(30)))
  await Promise.all(requests)
  assert.equal(page.run('paths.value[0].id'), 20)
  assert.equal(page.run('selected.value'), null)
  assert.equal(page.run('execution.value'), null)
})

test('a completed save from project A is stale even after switching A to B to A', async () => {
  const pending = deferred()
  const page = setup({ api: { createOnboardingPath: () => pending.promise } })
  page.run("openPath(); pathForm.name = 'Orders'; pathForm.knowledgeSnapshotId = 55")
  const save = page.run('savePath()')
  await page.run('projectId.value = 2; changeProject()')
  await page.run('projectId.value = 1; changeProject()')
  pending.resolve(response({ id: 90 }))
  await save
  assert.equal(page.run('selected.value'), null)
  assert.equal(page.run('busy.value'), false)
})

test('cancelled task removal and path publication make no requests', async () => {
  const page = setup()
  page.run('selected.value = makePath(10); selected.value.tasks = [makeTask(11)]')
  page.context.ElMessageBox.confirm = async () => { throw 'cancel' }
  await page.run('removeTask(selected.value.tasks[0])'); await page.run('publish()')
  assert.deepEqual(page.calls, []); assert.deepEqual(page.notices, [])
  assert.equal(page.run('busy.value'), false)
})

test('confirmation from a previous path cannot delete its task or publish after selection changes', async () => {
  for (const code of ['removeTask(selected.value.tasks[0])', 'publish()']) {
    const confirm = deferred(), page = setup()
    page.context.ElMessageBox.confirm = () => confirm.promise
    page.run('selected.value = makePath(10); selected.value.tasks = [makeTask(11)]')
    const action = page.run(code)
    await page.run('selectPath(20)')
    confirm.resolve()
    await action
    assert.deepEqual(page.calls, [])
  }
})

test('reordering sends the full ordered task list and ignores out-of-range moves', async () => {
  const page = setup()
  page.run('selected.value = makePath(10); selected.value.tasks = [makeTask(11), makeTask(12), makeTask(13)]')
  await page.run('moveTask(0, -1)'); await page.run('moveTask(-1, 1)')
  assert.deepEqual(page.calls, [])
  await page.run('moveTask(1, -1)')
  assert.deepEqual(page.calls, [{ name: 'reorder', args: [10, [12, 11, 13]] }])
})

test('assignment dialog loads all member pages and offers active learners only', async () => {
  const pages = []
  const page = setup({ listProjectMembers: async (id, query) => {
    pages.push([id, plain(query)])
    return response(query.page === 1 ? { rows: [{ userId: 7, role: 'LEARNER', status: 'ACTIVE' }], total: 3 }
      : { rows: [{ userId: 8, role: 'LEARNER', status: 'REMOVED' }, { userId: 9, role: 'OWNER', status: 'ACTIVE' }], total: 3 })
  } })
  page.run("selected.value = makePath(10, 'PUBLISHED')")
  await page.run('openAssign()')
  assert.deepEqual(plain(page.run('learners.value.map(m => m.userId)')), [7])
  page.run("learnerIds.value = [7]; dueAt.value = '2026-10-27T18:00:00'")
  await page.run('assign()')
  assert.deepEqual(pages.map(p => p[1].page), [1, 2])
  assert.deepEqual(page.calls, [{ name: 'assign', args: [10, { learnerUserIds: [7], dueAt: '2026-10-27T18:00:00' }] }])
})

test('late member loading does not reopen a dialog after project switching', async () => {
  const pending = deferred(), page = setup({ listProjectMembers: () => pending.promise })
  page.run("selected.value = makePath(10, 'PUBLISHED')")
  const load = page.run('openAssign()')
  await page.run('projectId.value = 2; changeProject()')
  pending.resolve(response({ rows: [{ userId: 7, role: 'LEARNER', status: 'ACTIVE' }], total: 1 }))
  await load
  assert.equal(page.run('assignDialog.value'), false)
  assert.equal(page.run('learners.value.length'), 0)
})

test('only the current assignee can start and no client identity is sent', async () => {
  const page = setup()
  page.run("projects.value = [makeProject(1, 'LEARNER')]; execution.value = makeAssignment(10, 8)")
  await page.run("execute('start')")
  assert.deepEqual(page.calls, [])
  page.run('execution.value = makeAssignment(10, 7)')
  await page.run("execute('start')")
  assert.deepEqual(page.calls, [{ name: 'start', args: [10] }])
})

test('assessment and locked tasks cannot invoke manual completion, but READ saves its note', async () => {
  const page = setup()
  page.run("execution.value = makeAssignment(10, 7, 'IN_PROGRESS'); execution.value.tasks = [makeTask(11, 'QUIZ'), makeTask(12, 'DRILL'), makeTask(13, 'READ', 'LOCKED'), makeTask(14)]")
  for (const index of [0, 1, 2]) await page.run(`execute('complete', execution.value.tasks[${index}])`)
  assert.deepEqual(page.calls, [])
  page.context.ElMessageBox.prompt = async () => ({ value: 'Verified the state machine' })
  await page.run("execute('complete', execution.value.tasks[3])")
  assert.deepEqual(page.calls, [{ name: 'complete', args: [14, 'Verified the state machine'] }])
})

test('cancelled and stale completion prompts do not mutate task state', async () => {
  const page = setup()
  page.run("execution.value = makeAssignment(10, 7, 'IN_PROGRESS'); execution.value.tasks = [makeTask(11)]")
  page.context.ElMessageBox.prompt = async () => { throw 'cancel' }
  await page.run("execute('complete', execution.value.tasks[0])")
  assert.deepEqual(page.calls, [])
  const prompt = deferred()
  page.context.ElMessageBox.prompt = () => prompt.promise
  const action = page.run("execute('complete', execution.value.tasks[0])")
  await page.run('selectAssignment(20)')
  prompt.resolve({ value: 'Old note' })
  await action
  assert.deepEqual(page.calls, [])
})

test('submit enters review pending instead of awarding a final pass', async () => {
  const reviewed = makeAssignment(10, 7, 'REVIEW_PENDING')
  reviewed.tasks = [makeTask(11, 'READ', 'COMPLETED')]
  const page = setup({ api: { getAssignment: async () => response(reviewed) } })
  page.run("execution.value = makeAssignment(10, 7, 'IN_PROGRESS'); execution.value.tasks = [makeTask(11, 'READ', 'COMPLETED')]")
  await page.run("execute('submit')")
  assert.deepEqual(page.calls, [{ name: 'submit', args: [10] }])
  assert.equal(page.run('execution.value.assignment.status'), 'REVIEW_PENDING')
  await page.run("execute('submit')")
  assert.equal(page.calls.length, 1)
})

test('unmount ignores late detail responses and prevents later commands', async () => {
  const pending = deferred(), page = setup({ api: { getOnboardingPath: () => pending.promise } })
  const request = page.run('selectPath(10)')
  page.unmount()
  pending.resolve(response(makePath(10)))
  await request
  page.run('openPath()')
  await page.run('savePath()')
  assert.equal(page.run('selected.value'), null)
  assert.equal(page.run('pathDialog.value'), false)
  assert.deepEqual(page.calls, [])
})

test('Onboarding REST routes stay behind the existing request client', async () => {
  const calls = []
  const context = vm.createContext({ request: Object.fromEntries(['get', 'post', 'put', 'delete'].map(method => [method,
    (...args) => { calls.push([method, ...plain(args)]); return Promise.resolve() }
  ])) })
  const source = readFileSync(new URL('../src/api/onboarding.js', import.meta.url), 'utf8')
    .replace(/^import .*$/gm, '').replace(/^export /gm, '')
  vm.runInContext(source, context)
  await vm.runInContext('reorderPathTasks(10, [12, 11]); completeAssignmentTask(20, "read"); startAssignment(30); submitAssignment(30)', context)
  assert.deepEqual(calls, [
    ['put', '/onboarding-paths/10/task-order', { taskIds: [12, 11] }],
    ['post', '/assignment-tasks/20/complete', { note: 'read' }],
    ['post', '/assignments/30/start'], ['post', '/assignments/30/submit']
  ])
})

import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import * as Vue from 'vue'
import { compile } from '@vue/compiler-dom'
import { compileScript, parse } from '@vue/compiler-sfc'
import { renderToString } from '@vue/server-renderer'

const { descriptor } = parse(readFileSync(new URL('../src/views/ProjectView.vue', import.meta.url), 'utf8'))
const script = descriptor.scriptSetup.content.replace(/^import .*$/gm, '')
const bindingNames = Object.keys(compileScript(descriptor, { id: 'project-test' }).bindings)
const render = new Function('Vue', compile(descriptor.template.content, {
  mode: 'function', prefixIdentifiers: true
}).code)(Vue)
const response = data => ({ data: { data } })
const project = (id, role = 'OWNER', status = 'ACTIVE') => ({
  id, name: `Project ${id}`, code: `service-${id}`, myRole: role, status, memberCount: 1
})
const member = { userId: 7, username: 'owner', nickname: 'Owner', role: 'OWNER', status: 'ACTIVE' }
const plain = value => JSON.parse(JSON.stringify(value))
function deferred() {
  let resolve
  const promise = new Promise(done => { resolve = done })
  return { promise, resolve }
}

function setup(overrides = {}) {
  const calls = [], notices = []
  let unmount
  const record = (name, result) => async (...args) => { calls.push({ name, args: plain(args) }); return result }
  const projectApi = {
    listProjects: async () => response({ rows: [], total: 0 }),
    getProject: async id => response(project(id)),
    listProjectMembers: async () => response({ rows: [member], total: 1 }),
    createProject: record('create', response({ id: 10 })),
    updateProject: record('update'),
    addProjectMember: record('add'),
    changeProjectMemberRole: record('role'),
    removeProjectMember: record('remove'),
    archiveProject: record('archive'),
    ...overrides
  }
  const context = vm.createContext({
    ...Vue, onMounted: () => {}, onBeforeUnmount: callback => { unmount = callback }, projectApi,
    ElMessage: Object.fromEntries(['error', 'warning', 'success'].map(type => [
      type, text => notices.push({ type, text })
    ])),
    ElMessageBox: { confirm: async () => {} }
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
  return { run, html, calls, notices, unmount: () => unmount(), context }
}

test('creating a project submits no identity or role and prevents duplicate pending submissions', async () => {
  const pending = deferred()
  const requests = []
  const page = setup({ createProject: body => { requests.push(plain(body)); return pending.promise } })
  page.run("openCreate(); Object.assign(form, { name: ' Orders ', code: ' orders ', description: 'handover' })")
  const first = page.run('saveProject()')
  await page.run('saveProject()')
  assert.deepEqual(requests, [{ name: 'Orders', code: 'orders', description: 'handover' }])
  assert.equal(page.run('busy.value'), true)
  pending.resolve(response({ id: 10 }))
  await first
  assert.equal(page.run('selected.value.id'), 10)
  assert.equal(page.run('dialog.value'), false)
  assert.equal(page.run('busy.value'), false)
})

test('only an active OWNER sees project settings and member management controls', async () => {
  for (const [role, status] of [['OWNER', 'ACTIVE'], ['MAINTAINER', 'ACTIVE'], ['LEARNER', 'ACTIVE'], ['OWNER', 'ARCHIVED']]) {
    const page = setup({ getProject: async id => response(project(id, role, status)) })
    await page.run('selectProject(10)')
    const html = await page.html()
    const manages = role === 'OWNER' && status === 'ACTIVE'
    assert.equal(html.includes('项目设置'), manages, `${role}/${status}`)
    assert.equal(html.includes('添加成员'), manages, `${role}/${status}`)
    assert.equal(html.includes('class="actions"'), manages, `${role}/${status}`)
    page.run('openAddMember(); openRole(members.value[0])')
    assert.equal(page.run('memberDialog.value'), manages)
    assert.equal(page.run('roleDialog.value'), manages)
  }
})

test('adding a registered member sends only the selected project, username and allowed role', async () => {
  const page = setup()
  await page.run('selectProject(10)')
  page.run("openAddMember(); memberForm.username = ' lin '; memberForm.role = 'MAINTAINER'")
  await page.run('addMember()')
  assert.deepEqual(page.calls, [{ name: 'add', args: [10, { username: 'lin', role: 'MAINTAINER' }] }])
  assert.equal(page.run('memberDialog.value'), false)
})

test('a last-OWNER conflict preserves the role and dialog for correction without showing success', async () => {
  const page = setup({ changeProjectMemberRole: async () => {
    throw { response: { status: 409, data: { code: 1104, msg: '不能降级最后一个 OWNER' } } }
  } })
  await page.run('selectProject(10)')
  page.run("openRole(members.value[0]); newRole.value = 'LEARNER'")
  await page.run('changeRole()')
  assert.equal(page.run('members.value[0].role'), 'OWNER')
  assert.equal(page.run('selected.value.myRole'), 'OWNER')
  assert.equal(page.run('roleDialog.value'), true)
  assert.equal(page.run('busy.value'), false)
  assert.deepEqual(page.notices, [{ type: 'error', text: '不能降级最后一个 OWNER' }])
})

test('a late project detail and member page cannot replace the newly selected project', async () => {
  const oldDetail = deferred(), oldMembers = deferred()
  const page = setup({
    getProject: id => id === 10 ? oldDetail.promise : Promise.resolve(response(project(id))),
    listProjectMembers: id => id === 10 ? oldMembers.promise : Promise.resolve(response({ rows: [], total: 0 }))
  })
  const oldSelection = page.run('selectProject(10)')
  await page.run('selectProject(20)')
  oldDetail.resolve(response(project(10)))
  oldMembers.resolve(response({ rows: [member], total: 1 }))
  await oldSelection
  assert.equal(page.run('selected.value.id'), 20)
  assert.equal(page.run('members.value.length'), 0)
  assert.equal(page.run('memberTotal.value'), 0)
  assert.equal(page.run('detailLoading.value'), false)
})

test('switching projects closes old member dialogs and cannot submit them against another project', async () => {
  const page = setup()
  await page.run('selectProject(10)')
  page.run("openAddMember(); memberForm.username = 'lin'; openRole(members.value[0]); newRole.value = 'LEARNER'")
  await page.run('selectProject(20)')
  assert.equal(page.run('memberDialog.value'), false)
  assert.equal(page.run('roleDialog.value'), false)
  assert.equal(page.run('roleTarget.value'), null)
  await page.run('addMember()')
  await page.run('changeRole()')
  assert.deepEqual(page.calls, [])
})

test('a late project list cannot replace newer filters and responses after unmount are ignored', async () => {
  const first = deferred(), second = deferred(), third = deferred()
  const pending = [first, second, third]
  const page = setup({ listProjects: () => pending.shift().promise })
  const oldList = page.run('load()')
  const newList = page.run("query.role = 'LEARNER'; load()")
  second.resolve(response({ rows: [project(20, 'LEARNER')], total: 1 }))
  await newList
  first.resolve(response({ rows: [project(10)], total: 1 }))
  await oldList
  assert.equal(page.run('rows.value[0].id'), 20)
  const afterUnmount = page.run('load()')
  page.unmount()
  third.resolve(response({ rows: [project(30)], total: 1 }))
  await afterUnmount
  assert.equal(page.run('rows.value[0].id'), 20)
})

test('cancelling member removal performs no request and releases the pending state', async () => {
  const page = setup()
  await page.run('selectProject(10)')
  page.context.ElMessageBox.confirm = async () => { throw 'cancel' }
  await page.run('removeMember(members.value[0])')
  assert.deepEqual(page.calls, [])
  assert.deepEqual(page.notices, [])
  assert.equal(page.run('busy.value'), false)
  assert.equal(page.run('members.value.length'), 1)
})
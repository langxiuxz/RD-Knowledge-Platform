<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as projectApi from '../api/project'

const roles = ['OWNER', 'MAINTAINER', 'LEARNER']
const rows = ref([])
const total = ref(0)
const loading = ref(false)
const selected = ref(null)
const members = ref([])
const memberTotal = ref(0)
const detailLoading = ref(false)
const busy = ref(false)
const query = reactive({ keyword: '', status: '', role: '', page: 1, pageSize: 10 })
const memberPage = ref(1)
const dialog = ref(false)
const editingId = ref(null)
const form = reactive({ name: '', code: '', description: '' })
const memberDialog = ref(false)
const memberProjectId = ref(null)
const memberForm = reactive({ username: '', role: 'LEARNER' })
const roleDialog = ref(false)
const roleTarget = ref(null)
const roleProjectId = ref(null)
const newRole = ref('LEARNER')
const canManage = computed(() => selected.value?.myRole === 'OWNER' && selected.value?.status === 'ACTIVE')
let selectionVersion = 0
let listVersion = 0

function showError(error, fallback) {
  ElMessage.error(error.response?.data?.msg || fallback)
}

async function load() {
  const version = ++listVersion
  loading.value = true
  try {
    const response = await projectApi.listProjects({ ...query, status: query.status || undefined, role: query.role || undefined })
    if (version !== listVersion) return
    rows.value = response.data.data.rows
    total.value = response.data.data.total
  } catch (error) {
    if (version === listVersion) showError(error, '项目加载失败')
  } finally {
    if (version === listVersion) loading.value = false
  }
}

function search() {
  query.page = 1
  load()
}

async function selectProject(id, resetPage = true) {
  const version = ++selectionVersion
  if (selected.value?.id !== id) {
    memberDialog.value = false
    memberProjectId.value = null
    roleDialog.value = false
    roleTarget.value = null
    roleProjectId.value = null
  }
  if (resetPage) memberPage.value = 1
  selected.value = null
  members.value = []
  memberTotal.value = 0
  detailLoading.value = true
  try {
    const [detail, page] = await Promise.all([
      projectApi.getProject(id),
      projectApi.listProjectMembers(id, { page: memberPage.value, pageSize: 10 })
    ])
    if (version !== selectionVersion) return
    selected.value = detail.data.data
    members.value = page.data.data.rows
    memberTotal.value = page.data.data.total
  } catch (error) {
    if (version === selectionVersion) showError(error, '项目或成员加载失败')
  } finally {
    if (version === selectionVersion) detailLoading.value = false
  }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', code: '', description: '' })
  dialog.value = true
}

function openEdit() {
  editingId.value = selected.value.id
  Object.assign(form, { name: selected.value.name, code: selected.value.code, description: selected.value.description || '' })
  dialog.value = true
}

async function saveProject() {
  if (busy.value) return
  const payload = { name: form.name.trim(), code: form.code.trim(), description: form.description }
  if (!payload.name || !/^[a-zA-Z0-9][a-zA-Z0-9._-]*$/.test(payload.code)) {
    ElMessage.warning('请填写项目名称及有效编码（字母、数字、点、下划线或连字符）')
    return
  }
  busy.value = true
  try {
    let id = editingId.value
    if (id) await projectApi.updateProject(id, payload)
    else id = (await projectApi.createProject(payload)).data.data.id
    dialog.value = false
    ElMessage.success('项目已保存')
    await Promise.all([load(), selectProject(id)])
  } catch (error) {
    showError(error, '项目保存失败')
  } finally {
    busy.value = false
  }
}

function openAddMember() {
  if (!canManage.value) return
  Object.assign(memberForm, { username: '', role: 'LEARNER' })
  memberProjectId.value = selected.value.id
  memberDialog.value = true
}

async function addMember() {
  if (busy.value || !memberDialog.value || !selected.value || !canManage.value
      || memberProjectId.value !== selected.value.id) return
  if (!memberForm.username.trim()) {
    ElMessage.warning('请填写已注册用户的用户名')
    return
  }
  const id = memberProjectId.value
  busy.value = true
  try {
    await projectApi.addProjectMember(id, { username: memberForm.username.trim(), role: memberForm.role })
    memberDialog.value = false
    ElMessage.success('成员已添加')
    await Promise.all([load(), selectProject(id)])
  } catch (error) {
    showError(error, '添加成员失败')
  } finally {
    busy.value = false
  }
}

function openRole(member) {
  if (!canManage.value) return
  roleTarget.value = member
  roleProjectId.value = selected.value.id
  newRole.value = member.role
  roleDialog.value = true
}

async function changeRole() {
  if (busy.value || !roleDialog.value || !selected.value || !canManage.value || !roleTarget.value
      || roleProjectId.value !== selected.value.id) return
  const id = roleProjectId.value
  busy.value = true
  try {
    await projectApi.changeProjectMemberRole(id, roleTarget.value.userId, { role: newRole.value })
    roleDialog.value = false
    ElMessage.success('成员角色已更新')
    await Promise.all([load(), selectProject(id, false)])
  } catch (error) {
    showError(error, '修改角色失败')
  } finally {
    busy.value = false
  }
}

async function removeMember(member) {
  if (busy.value || !selected.value) return
  const id = selected.value.id
  busy.value = true
  try {
    await ElMessageBox.confirm(`确认移除成员“${member.nickname || member.username}”？其历史记录会保留。`, '移除成员', { type: 'warning' })
    await projectApi.removeProjectMember(id, member.userId)
    ElMessage.success('成员已移除')
    await Promise.all([load(), selectProject(id)])
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') showError(error, '移除成员失败')
  } finally {
    busy.value = false
  }
}

async function archive() {
  if (busy.value || !selected.value) return
  const id = selected.value.id
  busy.value = true
  try {
    await ElMessageBox.confirm('归档后项目将只读，历史记录会保留。确认归档？', '归档项目', { type: 'warning' })
    await projectApi.archiveProject(id)
    ElMessage.success('项目已归档')
    await Promise.all([load(), selectProject(id)])
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') showError(error, '归档失败')
  } finally {
    busy.value = false
  }
}

onMounted(load)
onBeforeUnmount(() => { selectionVersion++; listVersion++ })
</script>

<template>
  <div class="project-page">
    <div class="heading">
      <div><h2>Project 团队空间</h2><p>为服务建立协作空间，明确成员与交接职责。</p></div>
      <el-button type="primary" :disabled="busy" @click="openCreate">创建项目</el-button>
    </div>
    <div class="workspace">
      <el-card class="project-list">
        <el-form class="filters" @submit.prevent="search">
          <el-input v-model="query.keyword" placeholder="搜索项目名称 / 编码" clearable @keyup.enter="search" />
          <div class="filter-row">
            <el-select v-model="query.status" placeholder="全部状态" clearable @change="search">
              <el-option label="进行中" value="ACTIVE" /><el-option label="已归档" value="ARCHIVED" />
            </el-select>
            <el-select v-model="query.role" placeholder="我的角色" clearable @change="search">
              <el-option v-for="role in roles" :key="role" :label="role" :value="role" />
            </el-select>
            <el-button @click="search">查询</el-button>
          </div>
        </el-form>
        <div v-loading="loading" class="list-content">
          <el-empty v-if="!rows.length && !loading" description="暂无参与的项目，可先创建一个项目" />
          <button v-for="project in rows" :key="project.id" class="project-item"
                  :class="{ active: selected?.id === project.id }" :disabled="busy" @click="selectProject(project.id)">
            <strong>{{ project.name }}</strong><span class="muted">{{ project.code }}</span>
            <div class="project-meta"><el-tag size="small">{{ project.role }}</el-tag><span>{{ project.memberCount }} 位成员</span>
              <el-tag v-if="project.status === 'ARCHIVED'" size="small" type="info">已归档</el-tag></div>
          </button>
        </div>
        <el-pagination v-if="total > query.pageSize" v-model:current-page="query.page" :page-size="query.pageSize"
                       :total="total" layout="prev, pager, next" @current-change="load" />
      </el-card>
      <el-card v-loading="detailLoading" class="project-detail">
        <template v-if="selected">
          <div class="detail-heading">
            <div><h3>{{ selected.name }}</h3><p>{{ selected.code }} · {{ selected.memberCount }} 位成员</p></div>
            <div class="actions" v-if="canManage"><el-button :disabled="busy" @click="openEdit">项目设置</el-button>
              <el-button type="warning" plain :disabled="busy" @click="archive">归档</el-button></div>
          </div>
          <p class="description">{{ selected.description || '暂无项目描述' }}</p>
          <div class="project-meta"><el-tag>我的角色：{{ selected.myRole }}</el-tag>
            <el-tag :type="selected.status === 'ACTIVE' ? 'success' : 'info'">{{ selected.status === 'ACTIVE' ? '进行中' : '已归档 · 只读' }}</el-tag></div>
          <div class="member-heading"><h3>项目成员</h3><el-button v-if="canManage" type="primary" :disabled="busy" @click="openAddMember">添加成员</el-button></div>
          <el-table :data="members" empty-text="暂无成员">
            <el-table-column prop="username" label="用户名" min-width="110" />
            <el-table-column prop="nickname" label="昵称" min-width="100" />
            <el-table-column prop="role" label="角色" width="140" />
            <el-table-column prop="joinedAt" label="加入时间" min-width="170" />
            <el-table-column v-if="canManage" label="操作" width="150"><template #default="{ row }">
              <el-button link type="primary" :disabled="busy" @click="openRole(row)">更改角色</el-button>
              <el-button link type="danger" :disabled="busy" @click="removeMember(row)">移除</el-button>
            </template></el-table-column>
          </el-table>
          <el-pagination v-if="memberTotal > 10" v-model:current-page="memberPage" :total="memberTotal" :page-size="10"
                         :disabled="busy" layout="prev, pager, next" @current-change="selectProject(selected.id, false)" />
        </template>
        <el-empty v-else description="选择一个项目查看详情与成员" />
      </el-card>
    </div>
    <el-dialog v-model="dialog" :title="editingId ? '项目设置' : '创建项目'" width="min(480px, 94vw)" :close-on-click-modal="!busy" :show-close="!busy">
      <el-form label-position="top" :disabled="busy" @submit.prevent="saveProject">
        <el-form-item label="项目名称" required><el-input v-model="form.name" maxlength="128" /></el-form-item>
        <el-form-item label="项目编码（全局唯一）" required><el-input v-model="form.code" maxlength="64" placeholder="order-service" /></el-form-item>
        <el-form-item label="描述"><el-input v-model="form.description" type="textarea" maxlength="1000" show-word-limit /></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="busy" @click="dialog = false">取消</el-button><el-button type="primary" :loading="busy" @click="saveProject">保存</el-button></template>
    </el-dialog>
    <el-dialog v-model="memberDialog" title="添加已注册用户" width="min(420px, 94vw)" :close-on-click-modal="!busy" :show-close="!busy">
      <el-form label-position="top" :disabled="busy" @submit.prevent="addMember">
        <el-form-item label="用户名" required><el-input v-model="memberForm.username" maxlength="32" /></el-form-item>
        <el-form-item label="项目角色"><el-select v-model="memberForm.role"><el-option v-for="role in roles" :key="role" :label="role" :value="role" /></el-select></el-form-item>
      </el-form>
      <template #footer><el-button :disabled="busy" @click="memberDialog = false">取消</el-button><el-button type="primary" :loading="busy" @click="addMember">添加</el-button></template>
    </el-dialog>
    <el-dialog v-model="roleDialog" title="更改成员角色" width="min(420px, 94vw)" :close-on-click-modal="!busy" :show-close="!busy">
      <p>{{ roleTarget?.nickname || roleTarget?.username }}</p>
      <el-select v-model="newRole" :disabled="busy"><el-option v-for="role in roles" :key="role" :label="role" :value="role" /></el-select>
      <p class="muted">项目至少保留一位 OWNER。</p>
      <template #footer><el-button :disabled="busy" @click="roleDialog = false">取消</el-button><el-button type="primary" :loading="busy" @click="changeRole">确认</el-button></template>
    </el-dialog>
  </div>
</template>

<style scoped>
.project-page{max-width:1400px;margin:0 auto}.heading,.detail-heading,.member-heading,.filter-row,.project-meta,.actions{display:flex;align-items:center;justify-content:space-between;gap:10px}.heading{margin-bottom:20px}.workspace{display:grid;grid-template-columns:minmax(290px,360px) minmax(0,1fr);gap:18px}h2,h3{margin:0 0 8px}p{margin:0;color:#606266}.filters{display:grid;gap:10px}.filter-row .el-select{min-width:0}.list-content{min-height:260px}.project-item{display:flex;flex-direction:column;gap:8px;text-align:left;width:100%;border:0;border-bottom:1px solid #ebeef5;background:white;padding:18px 10px;cursor:pointer}.project-item.active,.project-item:hover{background:#ecf5ff}.project-item:disabled{cursor:wait}.muted,.project-meta{font-size:13px;color:#909399}.project-meta{justify-content:flex-start;flex-wrap:wrap}.description{margin:18px 0;white-space:pre-wrap;overflow-wrap:anywhere}.member-heading{margin:28px 0 10px}.el-pagination{margin-top:18px;justify-content:center}.project-detail{min-height:440px}.el-dialog .muted{margin-top:12px}@media(max-width:1000px){.workspace{grid-template-columns:1fr}.heading,.detail-heading{align-items:flex-start;flex-direction:column}}
</style>

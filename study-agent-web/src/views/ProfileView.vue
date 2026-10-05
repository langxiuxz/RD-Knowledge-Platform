<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { uploadFile } from '../api/file'
const auth = useAuthStore()
const profileLoading = ref(false); const passwordLoading = ref(false); const avatarLoading = ref(false)
const avatarInput = ref(null)
const profile = reactive({ nickname: '', email: '', avatar: '' })
const password = reactive({ oldPassword: '', newPassword: '' })
async function uploadAvatar(event) { const file = event.target.files?.[0]; if (!file) return; avatarLoading.value = true; try { const response = await uploadFile(file, "AVATAR"); profile.avatar = response.data.data.url; ElMessage.success("头像上传成功，请保存资料") } catch (e) { ElMessage.error(e.response?.data?.msg || "头像上传失败") } finally { avatarLoading.value = false; event.target.value = "" } }
function syncProfile() { Object.assign(profile, { nickname: auth.user?.nickname || '', email: auth.user?.email || '', avatar: auth.user?.avatar || '' }) }
syncProfile()
async function saveProfile() { profileLoading.value = true; try { await auth.updateProfile(profile); syncProfile(); ElMessage.success('资料已更新') } catch (e) { ElMessage.error(e.response?.data?.msg || '更新失败') } finally { profileLoading.value = false } }
async function changePassword() { passwordLoading.value = true; try { await auth.changePassword(password); password.oldPassword = ''; password.newPassword = ''; ElMessage.success('密码已修改，请重新登录') } catch (e) { ElMessage.error(e.response?.data?.msg || '修改密码失败') } finally { passwordLoading.value = false } }
</script>
<template>
  <div class="page"><div class="page-heading"><div><h2>个人资料</h2><p>管理账号信息和登录安全</p></div></div>
    <el-row :gutter="16"><el-col :xs="24" :md="12"><el-card><template #header>基本资料</template><el-form label-position="top" @submit.prevent="saveProfile"><el-form-item label="用户名"><el-input :model-value="auth.user?.username" disabled /></el-form-item><el-form-item label="昵称"><el-input v-model="profile.nickname" /></el-form-item><el-form-item label="邮箱"><el-input v-model="profile.email" /></el-form-item><el-form-item label="头像"><el-input v-model="profile.avatar" placeholder="上传图片或粘贴URL" /><input ref="avatarInput" class="avatar-picker" type="file" accept="image/png,image/jpeg,image/gif,image/webp" @change="uploadAvatar" /><el-button size="small" :loading="avatarLoading" @click="avatarInput?.click()">上传头像</el-button></el-form-item><el-button type="primary" :loading="profileLoading" native-type="submit">保存资料</el-button></el-form></el-card></el-col>
      <el-col :xs="24" :md="12"><el-card><template #header>修改密码</template><el-form label-position="top" @submit.prevent="changePassword"><el-form-item label="当前密码"><el-input v-model="password.oldPassword" type="password" show-password /></el-form-item><el-form-item label="新密码"><el-input v-model="password.newPassword" type="password" show-password /></el-form-item><el-button type="warning" :loading="passwordLoading" native-type="submit">修改密码</el-button></el-form></el-card></el-col></el-row>
  </div>
</template>
<style scoped>.avatar-picker { display: none; } .page { max-width: 1200px; margin: 0 auto; } .page-heading { display:flex; justify-content:space-between; align-items:center; margin-bottom:16px; } h2 { margin:0 0 6px; } p { color:#909399; margin:0; }</style>



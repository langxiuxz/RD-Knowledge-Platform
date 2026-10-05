<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: '', password: '', nickname: '', email: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }, { min: 4, max: 32, message: '用户名长度为4~32位', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }, { min: 8, max: 64, message: '密码长度为8~64位', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }]
}
async function submit() {
  loading.value = true
  try {
    await auth.register(form)
    ElMessage.success('注册成功，请登录')
    router.push('/login')
  } catch (error) { ElMessage.error(error.response?.data?.msg || '注册失败') }
  finally { loading.value = false }
}
</script>
<template>
  <main class="auth-page">
    <el-card class="auth-card">
      <div class="auth-title"><h1>创建 StudyAgent 账号</h1><p>开始建立你的个人学习空间</p></div>
      <el-form :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username"><el-input v-model="form.username" autocomplete="username" /></el-form-item>
        <el-form-item label="密码" prop="password"><el-input v-model="form.password" type="password" show-password autocomplete="new-password" /></el-form-item>
        <el-form-item label="昵称" prop="nickname"><el-input v-model="form.nickname" /></el-form-item>
        <el-form-item label="邮箱（可选）" prop="email"><el-input v-model="form.email" type="email" /></el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" class="wide">注册</el-button>
      </el-form>
      <el-button link class="wide" @click="router.push('/login')">已有账号？返回登录</el-button>
    </el-card>
  </main>
</template>
<style scoped>
.auth-page { min-height: 100vh; display: grid; place-items: center; padding: 24px; background: linear-gradient(135deg, #eef5ff, #f8fafc); }
.auth-card { width: min(460px, 100%); } .auth-title { text-align: center; margin-bottom: 18px; } .auth-title h1 { margin: 0 0 8px; } .auth-title p { color: #909399; margin: 0; } .wide { width: 100%; margin-top: 8px; }
</style>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const loading = ref(false)
const form = reactive({ username: '', password: '' })

async function submit() {
  loading.value = true
  try {
    await auth.login(form)
    router.push('/dashboard')
  } catch (error) {
    ElMessage.error(error.response?.data?.msg || '登录失败')
  } finally { loading.value = false }
}
</script>

<template>
  <main class="login-page">
    <el-card class="login-card">
      <h1>StudyAgent</h1>
      <el-form :model="form" @submit.prevent="submit">
        <el-form-item label="用户名"><el-input v-model="form.username" autocomplete="username" /></el-form-item>
        <el-form-item label="密码"><el-input v-model="form.password" type="password" show-password autocomplete="current-password" /></el-form-item>
        <el-button type="primary" :loading="loading" native-type="submit" class="submit">登录</el-button>
      </el-form>
    </el-card>
  </main>
</template>

<style scoped>
.login-page { min-height: 100vh; display: grid; place-items: center; background: #f5f7fa; }
.login-card { width: min(420px, 90vw); }
.submit { width: 100%; }
</style>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
const router=useRouter();const auth=useAuthStore();const loading=ref(false);const form=reactive({username:'',password:''})
async function submit(){loading.value=true;try{await auth.login(form);router.push('/dashboard')}catch(e){ElMessage.error(e.response?.data?.msg||'登录失败')}finally{loading.value=false}}
</script>
<template><main class="login-page"><el-card class="login-card"><div class="auth-title"><h1>StudyAgent</h1><p>你的 AI 学习空间</p></div><el-form :model="form" @submit.prevent="submit"><el-form-item label="用户名"><el-input v-model="form.username" autocomplete="username"/></el-form-item><el-form-item label="密码"><el-input v-model="form.password" type="password" show-password autocomplete="current-password"/></el-form-item><el-button type="primary" :loading="loading" native-type="submit" class="submit">登录</el-button><el-button link class="submit" @click="router.push('/register')">没有账号？去注册</el-button></el-form></el-card></main></template>
<style scoped>.login-page{min-height:100vh;display:grid;place-items:center;background:linear-gradient(135deg,#eef5ff,#f8fafc);padding:24px}.login-card{width:min(420px,100%)}.auth-title{text-align:center;margin-bottom:18px}.auth-title h1{margin:0 0 8px}.auth-title p{margin:0;color:#909399}.submit{width:100%;margin-top:8px}</style>

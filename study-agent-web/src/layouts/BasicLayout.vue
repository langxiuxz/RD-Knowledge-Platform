<script setup>
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { provide } from 'vue'
import { usePomodoro } from '../composables/usePomodoro'
const pomodoro = usePomodoro()
provide('pomodoro', pomodoro)
const router=useRouter();const route=useRoute();const auth=useAuthStore()
async function signOut(){try{await auth.logout()}catch(e){ElMessage.warning('退出请求失败，已清理本地登录状态')}finally{router.push('/login')}}
const menus=[['/dashboard','学习总览','📊'],['/knowledge-bases','知识库','📚'],['/chat','AI Chat','💬'],['/quizzes','AI 测验','📝'],['/wrong-questions','错题本','⚠️'],['/learning','学习记录','📈'],['/pomodoro','学习番茄钟','🍅'],['/study-plans','学习计划','🗓️'],['/profile','个人资料','👤']]
</script>
<template><el-container class="layout"><el-header class="header"><div class="brand" @click="router.push('/dashboard')"><span class="brand-mark">S</span> StudyAgent</div><div class="header-actions"><el-button v-if="pomodoro.state.value.status !== 'idle'" link type="primary" @click="router.push('/pomodoro')">{{pomodoro.statusText.value}} {{pomodoro.displayTime.value}}</el-button><span class="welcome">{{auth.user?.nickname||auth.user?.username||'学习者'}}</span><el-button link @click="signOut">退出</el-button></div></el-header><el-container class="body"><el-aside width="220px" class="aside"><el-menu :default-active="route.path" router><el-menu-item v-for="item in menus" :key="item[0]" :index="item[0]"><span class="menu-icon">{{item[2]}}</span>{{item[1]}}</el-menu-item></el-menu></el-aside><el-main class="main"><RouterView /></el-main></el-container></el-container></template>
<style scoped>.layout{min-height:100vh;background:#f5f7fa}.header{height:64px;display:flex;align-items:center;justify-content:space-between;background:#fff;border-bottom:1px solid #ebeef5;padding:0 26px}.brand{font-size:21px;font-weight:700;color:#303133;cursor:pointer}.brand-mark{display:inline-grid;place-items:center;width:30px;height:30px;border-radius:9px;color:#fff;background:#409eff;margin-right:8px}.header-actions{display:flex;align-items:center;gap:16px}.welcome{color:#606266}.body{min-height:calc(100vh - 64px)}.aside{background:#fff;border-right:1px solid #ebeef5}.aside :deep(.el-menu){border-right:none}.menu-icon{width:25px;margin-right:4px}.main{padding:24px}@media(max-width:700px){.aside{width:58px!important}.aside :deep(.el-menu-item){font-size:0;justify-content:center;padding:0!important}.menu-icon{margin:0;font-size:18px}.main{padding:14px}.welcome{display:none}}
</style>

<script setup>
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
function signOut() { auth.clear(); router.push('/login') }
</script>

<template>
  <el-container class="layout">
    <el-header class="header">
      <span class="brand">StudyAgent</span>
      <div class="header-actions">
        <span>{{ auth.user?.nickname || auth.user?.username || '学习者' }}</span>
        <el-button link @click="signOut">退出</el-button>
      </div>
    </el-header>
    <el-container>
      <el-aside width="220px" class="aside">
        <el-menu router default-active="/dashboard">
          <el-menu-item index="/dashboard">学习总览</el-menu-item>
        </el-menu>
      </el-aside>
      <el-main><RouterView /></el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.layout { min-height: 100vh; }
.header { display: flex; align-items: center; justify-content: space-between; border-bottom: 1px solid var(--el-border-color); }
.brand { font-size: 20px; font-weight: 700; }
.header-actions { display: flex; align-items: center; gap: 16px; }
.aside { border-right: 1px solid var(--el-border-color); }
</style>

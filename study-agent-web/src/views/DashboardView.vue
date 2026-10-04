<script setup>
import { onMounted, ref } from 'vue'
import { getHealth } from '../api/health'

const health = ref(null)
onMounted(async () => {
  try { health.value = (await getHealth()).data.data } catch { health.value = { status: 'DOWN' } }
})
</script>

<template>
  <section>
    <h2>学习总览</h2>
    <el-alert v-if="health" :title="`服务状态：${health.status}`" :type="health.status === 'UP' ? 'success' : 'warning'" :closable="false" />
    <el-empty v-else description="正在检查服务状态" />
  </section>
</template>

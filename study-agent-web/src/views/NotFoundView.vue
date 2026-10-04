<template>
  <main class="not-found">
    <el-result icon="warning" title="404" sub-title="页面不存在">
      <template #extra><el-button type="primary" @click="$router.push('/')">返回首页</el-button></template>
    </el-result>
  </main>
</template>

<style scoped>
.not-found { min-height: 100vh; display: grid; place-items: center; }
</style>

<script setup>
import { computed, inject, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'

const route = useRoute()
const router = useRouter()
const { state, displayTime, today, percent, statusText, pending, saving,
  syncError, storageError, act, sync, enableNotifications } = inject('pomodoro')
const distraction = ref('')
const active = computed(() => state.value.status !== 'idle')
const openDistractions = computed(() => state.value.distractions.filter(item => !item.done).length)
function configure(patch) { return act('settings', patch) }
async function addDistraction() {
  if (!distraction.value.trim()) return
  if (await act('distraction', distraction.value)) distraction.value = ''
}
async function reset() {
  if (active.value) {
    try { await ElMessageBox.confirm('重置会放弃当前轮次，已完成的学习记录会保留。', '重置计时', { type: 'warning' }) }
    catch { return }
  }
  await act('reset')
}
watch(() => route.query.topic, async topic => {
  if (typeof topic === 'string' && !active.value) {
    await configure({ topic: topic.slice(0, 128), goal: `学习：${topic}`.slice(0, 400) })
    const query = { ...route.query }
    delete query.topic
    await router.replace({ path: route.path, query })
  }
}, { immediate: true })
</script>

<template>
  <div class="page">
    <div class="page-heading">
      <div><h2>学习番茄钟</h2><p>一次只专注一件事，让每段学习都留下记录。</p></div>
      <div class="heading-actions">
        <el-button @click="enableNotifications">开启完成提醒</el-button>
        <el-button @click="router.push('/learning')">查看学习记录</el-button>
      </div>
    </div>
    <el-alert v-if="storageError" :title="storageError" type="warning" :closable="false" show-icon class="notice" />
    <el-row :gutter="16">
      <el-col :xs="24" :lg="15">
        <el-card class="timer-card" shadow="never">
          <div class="mode-switch">
            <el-button :type="state.mode === 'focus' ? 'primary' : 'default'" :disabled="active" @click="act('mode', 'focus')">专注学习</el-button>
            <el-button :type="state.mode === 'rest' ? 'success' : 'default'" :disabled="active" @click="act('mode', 'rest')">短休息</el-button>
          </div>
          <div class="clock" :class="{ resting: state.mode === 'rest' }" role="timer" aria-label="剩余时间">{{ displayTime }}</div>
          <div class="status" role="status">{{ statusText }}</div>
          <el-progress :percentage="percent" :show-text="false" :stroke-width="8" :color="state.mode === 'rest' ? '#67c23a' : '#409eff'" />
          <div class="timer-actions">
            <el-button v-if="state.status === 'running'" type="primary" size="large" @click="act('pause')">暂停</el-button>
            <el-button v-else type="primary" size="large" @click="act('start')">{{ state.status === 'paused' ? '继续' : '开始' }}</el-button>
            <el-button size="large" @click="reset">重置</el-button>
            <el-button v-if="state.mode === 'focus' && active" size="large" @click="act('finish')">提前完成</el-button>
          </div>
          <p class="hint">{{ state.mode === 'focus' ? '完成后自动记录学习时长；满 1 分钟可提前完成。' : '休息不计入学习时长，结束后手动开始下一轮。' }}</p>
          <el-divider />
          <el-form label-position="top">
            <div class="duration-fields">
              <el-form-item label="专注时长（分钟）">
                <el-input-number :model-value="state.focusMinutes" :min="1" :max="180" :precision="0" :disabled="active" @change="value => configure({ focusMinutes: value })" />
              </el-form-item>
              <el-form-item label="短休息（分钟）">
                <el-input-number :model-value="state.restMinutes" :min="1" :max="60" :precision="0" :disabled="active" @change="value => configure({ restMinutes: value })" />
              </el-form-item>
            </div>
            <el-form-item label="学习主题">
              <el-input :model-value="state.topic" :disabled="active" maxlength="128" placeholder="例如：MyBatis 动态 SQL；留空记为自主学习" @update:model-value="value => configure({ topic: value })" />
            </el-form-item>
            <el-form-item label="本轮学习目标">
              <el-input :model-value="state.goal" :disabled="active" type="textarea" :rows="2" maxlength="400" show-word-limit placeholder="这轮结束时，我希望完成……" @update:model-value="value => configure({ goal: value })" />
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
      <el-col :xs="24" :lg="9">
        <el-card shadow="never" class="side-card">
          <template #header>今日专注</template>
          <div class="today-stats">
            <div><strong>{{ today.count }}</strong><span>完成番茄数</span></div>
            <div><strong>{{ today.minutes }}<small> 分钟</small></strong><span>累计专注</span></div>
          </div>
          <p class="hint">含提前完成的轮次；按本地完成日期统计，时长取完整分钟。</p>
          <div class="sync-status" role="status">
            <el-tag :type="pending ? 'warning' : 'success'">{{ pending ? `${pending} 轮待同步` : '学习记录已同步' }}</el-tag>
            <el-button v-if="pending" link type="primary" :loading="saving" @click="sync">立即重试</el-button>
          </div>
          <p v-if="syncError" class="sync-error">{{ syncError }}</p>
          <p class="hint">完成记录会用于学习总览、主题进度和 Agent 学习分析。离线记录会保留并在联网后重试。</p>
          <el-button link type="primary" @click="router.push('/chat')">与 Agent 分析学习情况 →</el-button>
        </el-card>
        <el-card shadow="never" class="side-card">
          <template #header><div class="card-heading"><span>稍后处理 · {{ openDistractions }}</span><el-button link :disabled="!state.distractions.some(item => item.done)" @click="act('clearDistractions')">清理已处理</el-button></div></template>
          <el-form @submit.prevent="addDistraction">
            <el-input v-model="distraction" maxlength="300" placeholder="记下分心的事，稍后再处理" aria-label="分心记录">
              <template #append><el-button native-type="submit" aria-label="添加分心记录">添加</el-button></template>
            </el-input>
          </el-form>
          <el-empty v-if="!state.distractions.length" description="想到什么，先记在这里" :image-size="70" />
          <ul v-else class="distractions">
            <li v-for="item in state.distractions" :key="item.id" :class="{ done: item.done }">
              <el-checkbox :model-value="item.done" :aria-label="`标记处理：${item.content}`" @change="act('toggleDistraction', item.id)" />
              <span>{{ item.content }}</span>
            </li>
          </ul>
          <p class="hint">这份清单仅保存在当前浏览器，不会发给 Agent。</p>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<style scoped>
.page{max-width:1200px;margin:0 auto}.page-heading,.heading-actions,.card-heading{display:flex;justify-content:space-between;align-items:center;gap:12px}.page-heading{margin-bottom:20px}h2{margin:0 0 6px}p{color:#909399;margin:0;font-size:13px;line-height:1.7}.notice{margin-bottom:16px}.timer-card{border-radius:12px;margin-bottom:16px}.mode-switch{text-align:center;margin-top:6px}.clock{text-align:center;color:#409eff;font-size:clamp(64px,10vw,112px);font-weight:700;font-variant-numeric:tabular-nums;letter-spacing:2px;line-height:1.4;margin-top:20px}.clock.resting{color:#67c23a}.status{text-align:center;color:#606266;margin-bottom:24px}.timer-actions{display:flex;justify-content:center;flex-wrap:wrap;gap:10px;margin:24px 0 12px}.timer-actions .el-button{margin:0}.hint{text-align:left}.duration-fields{display:flex;gap:24px;flex-wrap:wrap}.side-card{margin-bottom:16px;border-radius:12px}.today-stats{display:grid;grid-template-columns:1fr 1fr;gap:16px;margin-bottom:14px}.today-stats>div{display:flex;flex-direction:column;gap:8px}.today-stats strong{font-size:32px;color:#303133}.today-stats small{font-size:13px;font-weight:400}.today-stats span{font-size:13px;color:#909399}.sync-status{display:flex;align-items:center;gap:8px;margin:16px 0 8px}.sync-error{color:#e6a23c}.distractions{list-style:none;padding:0;max-height:350px;overflow:auto}.distractions li{display:flex;align-items:flex-start;gap:10px;border-bottom:1px solid #ebeef5;padding:8px 0}.distractions li span{font-size:14px;line-height:32px;overflow-wrap:anywhere}.distractions li.done span{text-decoration:line-through;color:#909399}.card-heading{font-size:15px}@media(max-width:600px){.page-heading{align-items:flex-start;flex-direction:column}.heading-actions{flex-wrap:wrap}.duration-fields{gap:10px}.clock{margin-top:14px}}
</style>

import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { createLearningRecord } from '../api/learning'
import { changePomodoro, focusPayload, newPomodoro, parsePomodoro, pomodoroId, pomodoroKey,
  prunePomodoros, remainingTime, settlePomodoro, todayPomodoros } from '../utils/pomodoro'

// Mounted by the authenticated layout so changing pages does not stop a round.
export function usePomodoro() {
  const auth = useAuthStore()
  const state = ref(newPomodoro())
  const now = ref(Date.now())
  const saving = ref(false)
  const syncError = ref('')
  const storageError = ref('')
  let userId = null
  let audio = null
  let disposed = false
  let lastSync = 0
  let memory = newPomodoro()
  let queue = Promise.resolve()
  let memoryOnly = false

  function read(id) {
    if (memoryOnly) return structuredClone(memory)
    try {
      const saved = parsePomodoro(localStorage.getItem(pomodoroKey(id)))
      memory = structuredClone(saved)
      return saved
    }
    catch (error) {
      memoryOnly = true
      storageError.value = '本地存储不可用或存档损坏；当前状态可能无法在刷新后恢复。'
      return structuredClone(memory)
    }
  }

  function write(id, next) {
    memory = structuredClone(next)
    try { localStorage.setItem(pomodoroKey(id), JSON.stringify(next)) }
    catch { memoryOnly = true; storageError.value = '本地存储不可用，请先不要刷新页面，学习记录仍会尝试同步。' }
    state.value = next
  }

  async function transaction(id, update) {
    const execute = () => {
      if (disposed || userId !== id) return
      const next = read(id)
      const before = JSON.stringify(next)
      const result = update(next)
      if (JSON.stringify(next) !== before) write(id, next)
      else state.value = next
      return result
    }
    // Locks coordinate timer transitions across tabs; the DB key also deduplicates uploads.
    const run = () => navigator.locks ? navigator.locks.request(pomodoroKey(id), execute) : execute()
    queue = queue.then(run, run)
    return queue
  }

  function notify(mode) {
    const title = mode === 'focus' ? '本轮专注已完成，休息一下吧' : '休息结束，可以开始下一轮学习了'
    ElMessage.success(title)
    if (audio?.state === 'running') {
      const oscillator = audio.createOscillator()
      const gain = audio.createGain()
      oscillator.connect(gain).connect(audio.destination)
      oscillator.frequency.value = 740
      gain.gain.setValueAtTime(0.12, audio.currentTime)
      gain.gain.exponentialRampToValueAtTime(0.001, audio.currentTime + 0.6)
      oscillator.start()
      oscillator.stop(audio.currentTime + 0.6)
    }
    if ('Notification' in window && Notification.permission === 'granted') {
      try { new Notification('StudyAgent 学习番茄钟', { body: title, tag: 'study-agent-pomodoro' }) } catch { /* Toast remains available. */ }
    }
  }

  // Must run in the button gesture, before awaiting locks/network.
  function enableSound() {
    try {
      const Audio = window.AudioContext || window.webkitAudioContext
      if (Audio && !audio) audio = new Audio()
      audio?.resume().catch(() => {})
    } catch { /* Browsers without audio still have the in-app reminder. */ }
  }

  async function enableNotifications() {
    enableSound()
    if (!('Notification' in window)) return ElMessage.info('当前浏览器不支持系统通知，将使用页面提示')
    try {
      const permission = await Notification.requestPermission()
      ElMessage.info(permission === 'granted' ? '系统提醒已开启' : '未开启系统提醒，仍会使用声音和页面提示')
    } catch { ElMessage.info('无法开启系统提醒，仍会使用页面提示') }
  }

  async function sync() {
    const id = userId
    if (!id || saving.value || disposed || !navigator.onLine) return
    saving.value = true
    lastSync = Date.now()
    try {
      await transaction(id, () => {})
      const pending = state.value.rounds.filter(r => !r.synced)
      for (const round of pending) {
        if (userId !== id || disposed) break
        const response = await createLearningRecord(focusPayload(round))
        if (response.data?.code !== 1) throw new Error(response.data?.msg || '学习记录保存失败')
        await transaction(id, next => {
          const saved = next.rounds.find(r => r.id === round.id)
          if (saved) saved.synced = true
          prunePomodoros(next, Date.now())
        })
      }
      if (id === userId) syncError.value = ''
    } catch (error) {
      if (id === userId) syncError.value = error.response?.data?.msg || error.message || '暂时无法同步，联网后将重试'
    } finally { saving.value = false }
  }

  async function tick() {
    now.value = Date.now()
    const id = userId
    if (!id || disposed) return
    const ended = await transaction(id, next => settlePomodoro(next, now.value))
    if (ended) notify(ended)
    if (state.value.rounds.some(r => !r.synced) && Date.now() - lastSync > 30000) void sync()
  }

  async function act(action, value) {
    if (!userId) return
    enableSound()
    try {
      const ended = await transaction(userId, next => changePomodoro(next, action, value, Date.now(), pomodoroId))
      if (ended) notify(ended)
      now.value = Date.now()
      if (state.value.rounds.some(r => !r.synced)) void sync()
      return true
    } catch (error) { ElMessage.warning(error.message); return false }
  }

  const stopWatch = watch(() => auth.user?.id, id => {
    userId = id || null
    memory = newPomodoro()
    memoryOnly = false
    syncError.value = ''
    storageError.value = ''
    state.value = id ? read(id) : memory
    lastSync = 0
    void tick()
  }, { immediate: true })
  const interval = window.setInterval(() => void tick(), 500)
  const visibility = () => { if (!document.hidden) { void tick(); void sync() } }
  const storage = event => { if (userId && event.key === pomodoroKey(userId)) void tick() }
  const online = () => void sync()
  window.addEventListener('storage', storage)
  window.addEventListener('online', online)
  window.addEventListener('focus', visibility)
  document.addEventListener('visibilitychange', visibility)
  onBeforeUnmount(() => {
    disposed = true
    userId = null
    stopWatch()
    clearInterval(interval)
    window.removeEventListener('storage', storage)
    window.removeEventListener('online', online)
    window.removeEventListener('focus', visibility)
    document.removeEventListener('visibilitychange', visibility)
    audio?.close().catch(() => {})
  })

  const remaining = computed(() => remainingTime(state.value, now.value))
  const displayTime = computed(() => {
    const seconds = Math.ceil(remaining.value / 1000)
    return `${String(Math.floor(seconds / 60)).padStart(2, '0')}:${String(seconds % 60).padStart(2, '0')}`
  })
  return { state, now, displayTime, saving, syncError, storageError, act, sync, enableNotifications,
    today: computed(() => todayPomodoros(state.value, now.value)),
    pending: computed(() => state.value.rounds.filter(r => !r.synced).length),
    percent: computed(() => Math.min(100, Math.max(0, (1 - remaining.value / state.value.planned) * 100))),
    statusText: computed(() => `${state.value.mode === 'focus' ? '专注' : '短休息'} · ${{ idle: '待开始', running: '进行中', paused: '已暂停' }[state.value.status]}`) }
}

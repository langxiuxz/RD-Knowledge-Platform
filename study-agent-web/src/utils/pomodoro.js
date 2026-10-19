// Pure clock/state logic: rendering intervals never act as the time source.
const MINUTE = 60000
// getRandomValues also works when randomUUID is unavailable (for example on a LAN URL).
export function pomodoroId() {
  if (globalThis.crypto.randomUUID) return globalThis.crypto.randomUUID()
  const bytes = globalThis.crypto.getRandomValues(new Uint8Array(16))
  bytes[6] = (bytes[6] & 15) | 64
  bytes[8] = (bytes[8] & 63) | 128
  const hex = Array.from(bytes, n => n.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export const pomodoroKey = userId => `study-agent.pomodoro.v1.${userId}`
export const localDay = value => {
  const date = new Date(value)
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

export function newPomodoro() {
  return {
    version: 1, focusMinutes: 50, restMinutes: 10, mode: 'focus', status: 'idle',
    remaining: 50 * MINUTE, planned: 50 * MINUTE, deadline: null,
    topic: '', goal: '', roundId: null, roundTopic: '', roundGoal: '', rounds: [], distractions: []
  }
}

export function parsePomodoro(raw) {
  if (!raw) return newPomodoro()
  const state = JSON.parse(raw)
  const finite = n => Number.isFinite(n) && n >= 0
  if (state.version !== 1 || !['focus', 'rest'].includes(state.mode)
      || !['idle', 'running', 'paused'].includes(state.status)
      || !Number.isInteger(state.focusMinutes) || state.focusMinutes < 1 || state.focusMinutes > 180
      || !Number.isInteger(state.restMinutes) || state.restMinutes < 1 || state.restMinutes > 60
      || !finite(state.remaining) || !finite(state.planned) || state.planned < MINUTE
      || state.planned > 180 * MINUTE || state.remaining > state.planned
      || (state.status === 'running' && !finite(state.deadline))
      || (state.mode === 'focus' && state.status !== 'idle' && !state.roundId)
      || !['topic', 'goal', 'roundTopic', 'roundGoal'].every(key => typeof state[key] === 'string')
      || !Array.isArray(state.rounds) || !Array.isArray(state.distractions)
      || !state.rounds.every(r => typeof r.id === 'string' && typeof r.topic === 'string'
        && typeof r.goal === 'string' && Number.isInteger(r.durationMinutes) && r.durationMinutes > 0
        && Number.isFinite(Date.parse(r.completedAt)) && typeof r.synced === 'boolean')
      || !state.distractions.every(d => typeof d.id === 'string' && typeof d.content === 'string'
        && typeof d.done === 'boolean')) throw new Error('计时存档无法读取')
  return state
}

export function remainingTime(state, now) {
  return state.status === 'running' ? Math.max(0, Math.min(state.remaining, state.deadline - now)) : state.remaining
}

function resetMode(state, mode) {
  state.mode = mode
  state.status = 'idle'
  state.remaining = state.planned = (mode === 'focus' ? state.focusMinutes : state.restMinutes) * MINUTE
  state.deadline = null
  state.roundId = null
}

function complete(state, completedAt, early = false) {
  const mode = state.mode
  if (mode === 'focus') {
    const durationMinutes = Math.floor((state.planned - remainingTime(state, completedAt)) / MINUTE)
    if (durationMinutes < 1) throw new Error('专注满 1 分钟后才可提前完成')
    // Keep pending records until the server acknowledges them, even after midnight.
    state.rounds.push({ id: state.roundId, topic: state.roundTopic, goal: state.roundGoal,
      durationMinutes, completedAt: new Date(completedAt).toISOString(), early, synced: false })
  }
  resetMode(state, mode === 'focus' ? 'rest' : 'focus')
  return mode
}

export function settlePomodoro(state, now) {
  // Settle only the current round. Never invent more rounds after sleep/closing the browser.
  if (state.status === 'running' && now >= state.deadline) return complete(state, state.deadline)
  return null
}

export function changePomodoro(state, action, value, now, uuid) {
  const ended = settlePomodoro(state, now)
  // A click after the deadline must not pause/reset the next round by accident.
  if (ended && ['pause', 'finish'].includes(action)) return ended
  if (action === 'start') {
    if (state.status === 'running') return ended
    if (state.status === 'idle' && state.mode === 'focus') {
      state.roundId = uuid()
      state.roundTopic = state.topic.trim() || '自主学习'
      state.roundGoal = state.goal.trim()
    }
    state.deadline = now + state.remaining
    state.status = 'running'
  } else if (action === 'pause' && state.status === 'running') {
    state.remaining = remainingTime(state, now)
    state.deadline = null
    state.status = 'paused'
  } else if (action === 'reset') {
    resetMode(state, state.mode)
  } else if (action === 'finish' && state.mode === 'focus' && state.status !== 'idle') {
    return complete(state, now, true)
  } else if (action === 'mode' && state.status === 'idle' && ['focus', 'rest'].includes(value)) {
    resetMode(state, value)
  } else if (action === 'settings' && state.status === 'idle') {
    // Apply patches to the latest state inside the transaction, including rapid input.
    value = { focusMinutes: state.focusMinutes, restMinutes: state.restMinutes,
      topic: state.topic, goal: state.goal, ...value }
    if (!Number.isInteger(value.focusMinutes) || value.focusMinutes < 1 || value.focusMinutes > 180
        || !Number.isInteger(value.restMinutes) || value.restMinutes < 1 || value.restMinutes > 60) {
      throw new Error('专注时长为 1～180 分钟，休息时长为 1～60 分钟')
    }
    state.focusMinutes = value.focusMinutes
    state.restMinutes = value.restMinutes
    state.topic = String(value.topic || '').slice(0, 128)
    state.goal = String(value.goal || '').slice(0, 400)
    resetMode(state, state.mode)
  } else if (action === 'distraction') {
    const content = String(value).trim().slice(0, 300)
    if (!content) return ended
    if (state.distractions.length >= 100) throw new Error('稍后处理最多保留 100 条，请先清理已处理内容')
    state.distractions.unshift({ id: uuid(), content, done: false })
  } else if (action === 'toggleDistraction') {
    const item = state.distractions.find(d => d.id === value)
    if (item) item.done = !item.done
  } else if (action === 'clearDistractions') {
    state.distractions = state.distractions.filter(d => !d.done)
  }
  return ended
}

export function todayPomodoros(state, now) {
  const rounds = state.rounds.filter(r => localDay(r.completedAt) === localDay(now))
  return { count: rounds.length, minutes: rounds.reduce((total, r) => total + r.durationMinutes, 0) }
}

export function focusPayload(round) {
  return { type: 'FOCUS', topic: round.topic, durationMinutes: round.durationMinutes,
    remark: round.goal || '番茄钟专注学习', clientRecordId: round.id, completedAt: round.completedAt }
}

export function prunePomodoros(state, now) {
  state.rounds = state.rounds.filter(r => !r.synced || Date.parse(r.completedAt) >= now - 31 * 86400000)
}

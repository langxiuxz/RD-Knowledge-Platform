import assert from 'node:assert/strict'
import test from 'node:test'
import { changePomodoro, focusPayload, newPomodoro, parsePomodoro, pomodoroId, pomodoroKey,
  prunePomodoros, remainingTime, settlePomodoro, todayPomodoros } from '../src/utils/pomodoro.js'

const M = 60000
const uuid = () => 'd86efc94-2975-4b88-a756-0b4c35c850a6'
const act = (s, action, now, value) => changePomodoro(s, action, value, now, uuid)

test('defaults and custom settings preserve quick text patches and spaces', () => {
  const s = newPomodoro()
  assert.equal(s.focusMinutes, 50)
  assert.equal(s.restMinutes, 10)
  act(s, 'settings', 0, { topic: 'Read Java ' })
  act(s, 'settings', 0, { goal: 'Understand streams ' })
  act(s, 'settings', 0, { focusMinutes: 25, restMinutes: 5 })
  assert.equal(s.topic, 'Read Java ')
  assert.equal(s.goal, 'Understand streams ')
  assert.equal(s.remaining, 25 * M)
  assert.throws(() => act(s, 'settings', 0, { focusMinutes: 0 }))
  assert.throws(() => act(s, 'settings', 0, { restMinutes: 61 }))
  assert.match(pomodoroId(), /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/)
  assert.notEqual(pomodoroKey(1), pomodoroKey(2))
})

test('background time, refresh, pause and resume use timestamps and exclude paused time', () => {
  let s = newPomodoro()
  act(s, 'start', 1000)
  assert.equal(remainingTime(s, 1000 + 12 * M), 38 * M)
  s = parsePomodoro(JSON.stringify(s))
  act(s, 'pause', 1000 + 12 * M)
  assert.equal(remainingTime(s, 1000 + 100 * M), 38 * M)
  act(s, 'start', 1000 + 100 * M)
  assert.equal(s.roundId, uuid())
  assert.equal(settlePomodoro(s, 1000 + 138 * M), 'focus')
  assert.equal(s.rounds[0].durationMinutes, 50)
  assert.equal(s.rounds[0].topic, '自主学习')
  assert.equal(s.mode, 'rest')
  assert.equal(s.status, 'idle')
})

test('sleep settles just one round at its original deadline and never invents cycles', () => {
  const s = newPomodoro()
  act(s, 'start', 1000)
  assert.equal(settlePomodoro(s, 1000 + 300 * M), 'focus')
  assert.equal(Date.parse(s.rounds[0].completedAt), 1000 + 50 * M)
  assert.equal(settlePomodoro(s, 1000 + 400 * M), null)
  assert.equal(s.rounds.length, 1)
  act(s, 'start', 1000 + 400 * M)
  assert.equal(settlePomodoro(s, 1000 + 410 * M), 'rest')
  assert.equal(s.rounds.length, 1)
  assert.equal(s.mode, 'focus')
})

test('early finish counts actual whole minutes only, including while paused', () => {
  const s = newPomodoro()
  act(s, 'settings', 0, { topic: 'Java', goal: 'Finish exercises' })
  act(s, 'start', 0)
  assert.throws(() => act(s, 'finish', M - 1), /1 分钟/)
  act(s, 'pause', 5 * M + 59000)
  act(s, 'finish', 100 * M)
  assert.equal(s.rounds[0].durationMinutes, 5)
  assert.deepEqual(focusPayload(s.rounds[0]), { type: 'FOCUS', topic: 'Java', durationMinutes: 5,
    remark: 'Finish exercises', clientRecordId: uuid(), completedAt: new Date(100 * M).toISOString() })
  assert.equal(s.rounds[0].early, true)
})

test('reset discards an unfinished round and rest never creates a learning record', () => {
  const s = newPomodoro()
  act(s, 'start', 0)
  act(s, 'reset', 4 * M)
  assert.equal(s.rounds.length, 0)
  assert.equal(s.remaining, 50 * M)
  act(s, 'mode', 5 * M, 'rest')
  act(s, 'start', 5 * M)
  settlePomodoro(s, 15 * M)
  assert.equal(s.rounds.length, 0)
  assert.equal(s.mode, 'focus')
})

test('a pause click after the deadline settles the old round without pausing rest', () => {
  const s = newPomodoro()
  act(s, 'start', 0)
  assert.equal(act(s, 'pause', 51 * M), 'focus')
  assert.equal(s.status, 'idle')
  assert.equal(s.mode, 'rest')
  assert.equal(s.rounds.length, 1)
})

test('today uses the completion day, rolls over at midnight, and keeps old pending records', () => {
  const s = newPomodoro()
  const before = new Date(2026, 9, 19, 23, 59).getTime()
  act(s, 'start', before - 2 * M)
  act(s, 'finish', before)
  assert.deepEqual(todayPomodoros(s, before), { count: 1, minutes: 2 })
  assert.deepEqual(todayPomodoros(s, before + 2 * M), { count: 0, minutes: 0 })
  prunePomodoros(s, before + 40 * 86400000)
  assert.equal(s.rounds.length, 1)
  s.rounds[0].synced = true
  prunePomodoros(s, before + 40 * 86400000)
  assert.equal(s.rounds.length, 0)
})

test('distraction notes survive refresh and can be marked and cleared', () => {
  let s = newPomodoro()
  act(s, 'distraction', 0, '  check email  ')
  act(s, 'distraction', 0, '  ')
  s = parsePomodoro(JSON.stringify(s))
  assert.equal(s.distractions.length, 1)
  assert.equal(s.distractions[0].content, 'check email')
  act(s, 'toggleDistraction', 0, uuid())
  act(s, 'clearDistractions', 0)
  assert.equal(s.distractions.length, 0)
})

test('invalid stored states fail validation instead of fabricating learning time', () => {
  assert.throws(() => parsePomodoro('{broken'))
  for (const patch of [{ version: 2 }, { planned: 0 }, { status: 'running', deadline: null },
    { status: 'paused', roundId: null }, { rounds: [{}] }, { remaining: -10 }]) {
    assert.throws(() => parsePomodoro(JSON.stringify({ ...newPomodoro(), ...patch })))
  }
})

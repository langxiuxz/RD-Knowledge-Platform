import { useAuthStore } from '../stores/auth'
import { refreshAccessToken } from './request'

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL || '/api').replace(/\/$/, '')

export function createSseParser(onEvent) {
  let buffer = ''

  function dispatch(block) {
    if (!block.trim()) return
    let event = 'message'
    const data = []
    for (const line of block.split(/\r?\n/)) {
      if (line.startsWith('event:')) event = line.slice(6).trim()
      if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
    }
    const raw = data.join('\n')
    if (!raw) return
    try {
      onEvent(event, JSON.parse(raw))
    } catch {
      onEvent(event, raw)
    }
  }

  return {
    push(chunk) {
      buffer += chunk
      let separator = buffer.match(/\r?\n\r?\n/)
      while (separator) {
        const index = separator.index
        dispatch(buffer.slice(0, index))
        buffer = buffer.slice(index + separator[0].length)
        separator = buffer.match(/\r?\n\r?\n/)
      }
    },
    finish() {
      dispatch(buffer)
      buffer = ''
    }
  }
}

async function openStream(conversationId, payload, token, signal) {
  return fetch(`${apiBaseUrl}/conversations/${conversationId}/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(payload),
    signal
  })
}

export async function streamConversation(conversationId, payload, { signal, onEvent }) {
  const authStore = useAuthStore()
  let response = await openStream(conversationId, payload, authStore.token, signal)
  if (response.status === 401 && authStore.refreshToken) {
    await refreshAccessToken()
    response = await openStream(conversationId, payload, authStore.token, signal)
  }
  if (!response.ok) {
    const body = await response.json().catch(() => null)
    throw new Error(body?.msg || `SSE request failed (${response.status})`)
  }
  if (!response.body) throw new Error('Streaming response body is unavailable')

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  const parser = createSseParser(onEvent)
  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      parser.push(decoder.decode(value, { stream: true }))
    }
    parser.push(decoder.decode())
    parser.finish()
  } finally {
    reader.releaseLock()
  }
}

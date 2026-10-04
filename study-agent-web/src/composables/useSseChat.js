import { ref } from 'vue'
import { stopGeneration } from '../api/conversation'
import { streamConversation } from '../api/sse'

export function useSseChat() {
  const streaming = ref(false)
  const traceId = ref('')
  const messageId = ref(null)
  const content = ref('')
  const citations = ref([])
  const toolCalls = ref([])
  const nodes = ref([])
  const error = ref(null)
  let controller = null

  function reset() {
    traceId.value = ''
    messageId.value = null
    content.value = ''
    citations.value = []
    toolCalls.value = []
    nodes.value = []
    error.value = null
  }

  function handleEvent(event, data) {
    if (event === 'start') {
      traceId.value = data.traceId || ''
      messageId.value = data.messageId ?? null
    } else if (event === 'node') {
      nodes.value.push(data)
    } else if (event === 'tool') {
      toolCalls.value.push(data)
    } else if (event === 'token') {
      content.value += data.content || ''
    } else if (event === 'citation') {
      citations.value.push(data)
    } else if (event === 'done') {
      messageId.value = data.messageId ?? messageId.value
      streaming.value = false
    } else if (event === 'error') {
      error.value = data
      streaming.value = false
    }
  }

  async function start(conversationId, payload) {
    if (streaming.value) throw new Error('A generation is already running')
    reset()
    controller = new AbortController()
    streaming.value = true
    try {
      await streamConversation(conversationId, payload, {
        signal: controller.signal,
        onEvent: handleEvent
      })
    } catch (requestError) {
      if (requestError.name !== 'AbortError') {
        error.value = { message: requestError.message }
        throw requestError
      }
    } finally {
      streaming.value = false
      controller = null
    }
  }

  async function stop(conversationId) {
    try {
      if (traceId.value) await stopGeneration(conversationId, traceId.value)
    } finally {
      controller?.abort()
      streaming.value = false
    }
  }

  return {
    streaming,
    traceId,
    messageId,
    content,
    citations,
    toolCalls,
    nodes,
    error,
    start,
    stop,
    reset
  }
}

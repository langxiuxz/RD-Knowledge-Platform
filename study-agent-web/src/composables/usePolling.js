import { onBeforeUnmount } from 'vue'

export function usePolling(task, { interval = 2000, timeout = 120000, onError } = {}) {
  let timer
  let startedAt = 0
  let stopped = false
  function stop() { stopped = true; if (timer) clearTimeout(timer); timer = undefined }
  async function start() {
    stop(); stopped = false; startedAt = Date.now()
    const tick = async () => {
      if (stopped) return
      try {
        const result = await task()
        const status = typeof result === 'string' ? result : result?.status || result?.generationStatus
        if (status === 'READY' || status === 'FAILED' || Date.now() - startedAt >= timeout) return stop()
      } catch (error) {
        onError?.(error)
        return stop()
      }
      if (!stopped) timer = setTimeout(tick, interval)
    }
    await tick()
  }
  onBeforeUnmount(stop)
  return { start, stop }
}

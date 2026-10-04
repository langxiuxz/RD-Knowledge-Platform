import { onBeforeUnmount } from 'vue'

export function usePolling(task, { interval = 2000, timeout = 60000 } = {}) {
  let timer
  let startedAt
  function stop() { clearTimeout(timer); timer = undefined }
  async function start() {
    stop(); startedAt = Date.now()
    const tick = async () => {
      const result = await task()
      if (result === 'READY' || result === 'FAILED' || Date.now() - startedAt >= timeout) return stop()
      timer = setTimeout(tick, interval)
    }
    await tick()
  }
  onBeforeUnmount(stop)
  return { start, stop }
}

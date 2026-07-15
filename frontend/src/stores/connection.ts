import { computed, ref } from 'vue'
import { defineStore } from 'pinia'

import { fetchHealth } from '@/api/healthApi'
import {
  SystemHealthClient,
  type RealtimeConnectionState,
} from '@/realtime/systemHealthClient'

export type ProbeState = 'IDLE' | 'CHECKING' | 'UP' | 'DOWN'

export const useConnectionStore = defineStore('connection', () => {
  const backendState = ref<ProbeState>('IDLE')
  const databaseState = ref<ProbeState>('IDLE')
  const websocketState = ref<RealtimeConnectionState>('DISCONNECTED')
  const websocketOperationInProgress = ref(false)
  const lastCheckedAt = ref<string | null>(null)
  const errorMessage = ref<string | null>(null)

  const realtimeClient = new SystemHealthClient({
    onStateChange: (state) => {
      websocketState.value = state
    },
    onMessage: (message) => {
      websocketState.value = message.status === 'UP' ? 'CONNECTED' : 'FAILED'
      lastCheckedAt.value = message.timestamp
    },
    onError: (message) => {
      errorMessage.value = message
    },
  })

  const allConnected = computed(
    () =>
      backendState.value === 'UP' &&
      databaseState.value === 'UP' &&
      websocketState.value === 'CONNECTED',
  )

  const websocketActionDisabled = computed(
    () => websocketOperationInProgress.value || websocketState.value === 'CONNECTING',
  )

  async function checkRestHealth(): Promise<void> {
    backendState.value = 'CHECKING'
    databaseState.value = 'CHECKING'
    errorMessage.value = null

    try {
      const health = await fetchHealth()
      backendState.value = health.status === 'UP' ? 'UP' : 'DOWN'
      databaseState.value = health.database === 'UP' ? 'UP' : 'DOWN'
      lastCheckedAt.value = new Date().toISOString()
    } catch (error) {
      backendState.value = 'DOWN'
      databaseState.value = 'DOWN'
      errorMessage.value = error instanceof Error ? error.message : 'REST 상태 확인에 실패했습니다.'
    }
  }

  function connectWebSocket(): void {
    if (websocketActionDisabled.value) {
      return
    }

    errorMessage.value = null
    realtimeClient.connect()
  }

  async function reconnectWebSocket(): Promise<void> {
    if (websocketActionDisabled.value) {
      return
    }

    websocketOperationInProgress.value = true
    errorMessage.value = null

    try {
      await realtimeClient.reconnect()
    } catch (error) {
      websocketState.value = 'FAILED'
      errorMessage.value =
        error instanceof Error ? error.message : 'WebSocket 재연결에 실패했습니다.'
    } finally {
      websocketOperationInProgress.value = false
    }
  }

  async function disconnectWebSocket(): Promise<void> {
    await realtimeClient.disconnect()
    websocketState.value = 'DISCONNECTED'
  }

  async function initialize(): Promise<void> {
    await checkRestHealth()
    connectWebSocket()
  }

  return {
    backendState,
    databaseState,
    websocketState,
    websocketOperationInProgress,
    lastCheckedAt,
    errorMessage,
    allConnected,
    websocketActionDisabled,
    checkRestHealth,
    connectWebSocket,
    reconnectWebSocket,
    disconnectWebSocket,
    initialize,
  }
})

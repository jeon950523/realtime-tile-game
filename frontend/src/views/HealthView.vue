<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'

import type { RealtimeConnectionState } from '@/realtime/systemHealthClient'
import { useConnectionStore, type ProbeState } from '@/stores/connection'

const connectionStore = useConnectionStore()
const apiBaseUrl = import.meta.env.VITE_API_BASE_URL
const webSocketUrl = import.meta.env.VITE_WS_URL

const probeLabel: Record<ProbeState, string> = {
  IDLE: '확인 전',
  CHECKING: '확인 중',
  UP: '정상',
  DOWN: '연결 실패',
}

const realtimeLabel: Record<RealtimeConnectionState, string> = {
  DISCONNECTED: '연결 끊김',
  CONNECTING: '연결 중',
  CONNECTED: '정상',
  FAILED: '연결 실패',
}

function statusClass(state: ProbeState | RealtimeConnectionState): string {
  if (state === 'UP' || state === 'CONNECTED') {
    return 'status-card--up'
  }
  if (state === 'DOWN' || state === 'FAILED') {
    return 'status-card--down'
  }
  return 'status-card--pending'
}

onMounted(() => {
  void connectionStore.initialize()
})

onBeforeUnmount(() => {
  void connectionStore.disconnectWebSocket()
})
</script>

<template>
  <main class="health-page">
    <section class="hero-panel">
      <p class="eyebrow">PHASE 0 · FOUNDATION</p>
      <h1>실시간 타일 보드게임</h1>
      <p class="hero-copy">
        REST, MySQL, WebSocket/STOMP 연결 상태를 확인하는 개발 환경 점검 화면이다.
      </p>
      <div class="overall-status" :class="{ 'overall-status--up': connectionStore.allConnected }">
        {{ connectionStore.allConnected ? '모든 연결 정상' : '연결 상태 확인 필요' }}
      </div>
    </section>

    <section class="status-grid" aria-label="연결 상태">
      <article class="status-card" :class="statusClass(connectionStore.backendState)">
        <span class="status-card__label">Backend REST</span>
        <strong>{{ probeLabel[connectionStore.backendState] }}</strong>
        <code>{{ apiBaseUrl }}/api/health</code>
      </article>

      <article class="status-card" :class="statusClass(connectionStore.databaseState)">
        <span class="status-card__label">MySQL</span>
        <strong>{{ probeLabel[connectionStore.databaseState] }}</strong>
        <code>SELECT 1</code>
      </article>

      <article class="status-card" :class="statusClass(connectionStore.websocketState)">
        <span class="status-card__label">WebSocket / STOMP</span>
        <strong>{{ realtimeLabel[connectionStore.websocketState] }}</strong>
        <code>{{ webSocketUrl }}</code>
      </article>
    </section>

    <section class="action-panel">
      <button type="button" @click="connectionStore.checkRestHealth">REST 다시 확인</button>
      <button
        type="button"
        class="button-secondary"
        :disabled="connectionStore.websocketActionDisabled"
        @click="connectionStore.reconnectWebSocket"
      >
        {{ connectionStore.websocketState === 'CONNECTING' ? 'WebSocket 연결 중' : 'WebSocket 다시 연결' }}
      </button>
      <p v-if="connectionStore.lastCheckedAt" class="timestamp">
        마지막 응답: {{ connectionStore.lastCheckedAt }}
      </p>
      <p v-if="connectionStore.errorMessage" class="error-message" role="alert">
        {{ connectionStore.errorMessage }}
      </p>
    </section>
  </main>
</template>

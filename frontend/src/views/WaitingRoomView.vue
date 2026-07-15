<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useGameStore } from '@/stores/game'
import { useRoomStore } from '@/stores/room'

const authStore = useAuthStore()
const roomStore = useRoomStore()
const gameStore = useGameStore()
const route = useRoute()
const router = useRouter()
const roomId = Number(route.params.roomId)
const launchingGame = ref(false)

const room = computed(() => roomStore.activeRoom)
const currentUserId = computed(() => authStore.user?.userId ?? null)
const currentParticipant = computed(() => room.value?.participants.find((participant) => participant.userId === currentUserId.value) ?? null)
const isOwner = computed(() => currentParticipant.value?.owner === true)
const emptySeats = computed(() => Math.max(0, (room.value?.maxPlayers ?? 0) - (room.value?.participants.length ?? 0)))

onMounted(async () => {
  try {
    await roomStore.loadRoomDetail(roomId)
    await roomStore.connectRoom(roomId)
  } catch {
    await router.replace('/lobby')
  }
})

watch(
  () => roomStore.launchedGameId,
  async (value) => {
    if (value === null || route.name !== 'waiting-room') return
    const gameId = roomStore.consumeLaunchedGameId()
    if (gameId === null) return
    launchingGame.value = true
    await roomStore.disconnectRealtime()
    gameStore.clearGameState()
    await router.replace(`/games/${gameId}`)
    roomStore.clearRoomState()
  },
)

watch(
  () => roomStore.activeRoomId,
  async (value, previous) => {
    if (!launchingGame.value && previous !== null && value === null && route.name === 'waiting-room') {
      await router.replace('/lobby')
    }
  },
)

async function leave(): Promise<void> {
  try {
    await roomStore.leaveRoom()
    await router.replace('/lobby')
  } catch {
    // Store exposes safe error.
  }
}

async function logout(): Promise<void> {
  try {
    await roomStore.leaveRoom()
    await authStore.logout()
    roomStore.clearRoomState()
    await router.replace('/login')
  } catch {
    // Leaving or logout failure must keep the current state.
  }
}
</script>

<template>
  <main class="waiting-page">
    <section v-if="room" class="waiting-card">
      <header class="waiting-header">
        <div>
          <p class="eyebrow">WAITING ROOM #{{ room.roomId }}</p>
          <h1>{{ room.roomName }}</h1>
          <p>{{ room.gameMode }} · 턴 {{ room.turnTimeLimitSeconds }}초 · {{ room.currentPlayers }}/{{ room.maxPlayers }}명</p>
        </div>
        <div class="toolbar-actions">
          <button class="secondary-button" type="button" :disabled="roomStore.leaving" @click="leave">방 나가기</button>
          <button class="secondary-button" type="button" :disabled="roomStore.leaving || authStore.logoutInProgress" @click="logout">로그아웃</button>
        </div>
      </header>

      <div class="connection-banner" :data-state="roomStore.roomConnectionState">대기방 연결: {{ roomStore.roomConnectionState }}</div>
      <section class="seat-grid" aria-label="참가자 좌석">
        <article v-for="participant in room.participants" :key="participant.userId" class="seat-card" :class="{ 'seat-card--me': participant.userId === currentUserId }">
          <span class="seat-number">SEAT {{ participant.seatOrder }}</span>
          <strong>{{ participant.nickname }}</strong>
          <small>{{ participant.avatarType }}</small>
          <div class="seat-badges"><span v-if="participant.owner" class="owner-badge">방장</span><span :class="participant.readyStatus === 'READY' ? 'ready-badge' : 'not-ready-badge'">{{ participant.readyStatus }}</span><span v-if="participant.userId === currentUserId">나</span></div>
        </article>
        <article v-for="index in emptySeats" :key="`empty-${index}`" class="seat-card seat-card--empty"><span>빈 자리</span></article>
      </section>

      <p v-if="roomStore.lastError" class="form-error" role="alert">{{ roomStore.lastError }}</p>
      <p v-if="roomStore.lastMessage" class="form-success" role="status">{{ roomStore.lastMessage }}</p>
      <div class="waiting-actions">
        <button class="primary-button" type="button" :disabled="roomStore.commandInProgress || roomStore.roomConnectionState !== 'CONNECTED'" @click="roomStore.setReady(currentParticipant?.readyStatus !== 'READY')">
          {{ currentParticipant?.readyStatus === 'READY' ? '준비 취소' : '준비하기' }}
        </button>
        <div v-if="isOwner" class="start-control">
          <button class="primary-button" type="button" :disabled="!room.startable || roomStore.commandInProgress || roomStore.roomConnectionState !== 'CONNECTED'" @click="roomStore.requestStart">게임 시작</button>
          <small v-if="!room.startable">{{ room.startBlockReason }}</small>
          <small v-else>모든 참가자가 준비되었습니다.</small>
        </div>
      </div>
    </section>
    <section v-else class="waiting-card"><p>대기방 정보를 불러오는 중입니다.</p></section>
  </main>
</template>

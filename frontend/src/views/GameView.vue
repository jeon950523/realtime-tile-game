<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useGameStore } from '@/stores/game'
import { useRoomStore } from '@/stores/room'

const authStore = useAuthStore()
const gameStore = useGameStore()
const roomStore = useRoomStore()
const route = useRoute()
const router = useRouter()
const gameId = Number(route.params.gameId)

const state = computed(() => gameStore.privateState)
const publicState = computed(() => state.value?.publicState ?? null)
const currentTurnPlayer = computed(() => publicState.value?.players.find((player) => player.currentTurn) ?? null)

onMounted(async () => {
  try {
    await gameStore.initialize(gameId)
  } catch {
    const active = await gameStore.loadActiveGame().catch(() => null)
    if (active?.active && active.gameId !== null) {
      await router.replace(`/games/${active.gameId}`)
      return
    }
    await router.replace('/lobby')
  }
})

async function logout(): Promise<void> {
  try {
    await gameStore.disconnectRealtime()
    await roomStore.disconnectRealtime()
    await authStore.logout()
    gameStore.clearGameState()
    roomStore.clearRoomState()
    await router.replace('/login')
  } catch {
    // Stores expose safe errors and preserve the current state for retry.
  }
}
</script>

<template>
  <main class="game-page">
    <section v-if="state && publicState" class="game-shell">
      <header class="game-header">
        <div>
          <p class="eyebrow">GAME #{{ publicState.gameId }}</p>
          <h1>{{ publicState.gameMode }} 게임</h1>
          <p>상태 {{ publicState.status }} · 턴 {{ publicState.turnNumber }} · Pool {{ publicState.tilePoolCount }}</p>
        </div>
        <button class="secondary-button" type="button" :disabled="authStore.logoutInProgress" @click="logout">로그아웃</button>
      </header>

      <div class="connection-banner" :data-state="gameStore.connectionState">
        게임 연결: {{ gameStore.connectionState }}
      </div>
      <p v-if="gameStore.lastError" class="form-error" role="alert">{{ gameStore.lastError }}</p>

      <section class="game-status-grid" aria-label="게임 공개 상태">
        <article>
          <span>현재 턴</span>
          <strong>{{ currentTurnPlayer?.nickname ?? `SEAT ${publicState.currentTurnSeatOrder}` }}</strong>
        </article>
        <article>
          <span>남은 타일</span>
          <strong>{{ publicState.tilePoolCount }}</strong>
        </article>
        <article>
          <span>내 좌석</span>
          <strong>SEAT {{ state.mySeatOrder }}</strong>
        </article>
        <article>
          <span>테이블 조합</span>
          <strong>{{ publicState.tableMelds.length }}</strong>
        </article>
      </section>

      <section class="game-players" aria-label="게임 참가자">
        <article
          v-for="player in publicState.players"
          :key="player.userId"
          class="game-player-card"
          :class="{ 'game-player-card--turn': player.currentTurn, 'game-player-card--me': player.userId === state.myUserId }"
        >
          <span>SEAT {{ player.seatOrder }}</span>
          <strong>{{ player.nickname }}</strong>
          <small>{{ player.avatarType }}</small>
          <p>Rack {{ player.rackTileCount }}개</p>
          <em v-if="player.currentTurn">현재 턴</em>
          <em v-if="player.userId === state.myUserId">나</em>
        </article>
      </section>

      <section class="table-zone" aria-label="게임 테이블">
        <h2>Table</h2>
        <p>Phase 4에서는 아직 제출된 Meld가 없습니다.</p>
      </section>

      <section class="rack-zone" aria-label="내 Rack">
        <div class="rack-zone__header">
          <h2>내 Rack</h2>
          <span>{{ state.myRack.length }}개</span>
        </div>
        <div class="tile-rack">
          <article
            v-for="tile in state.myRack"
            :key="tile.tileId"
            class="game-tile"
            :data-color="tile.color ?? 'JOKER'"
          >
            <strong>{{ tile.joker ? 'JOKER' : tile.number }}</strong>
            <span>{{ tile.color ?? 'WILD' }}</span>
            <small>{{ tile.tileId }}</small>
          </article>
        </div>
      </section>
    </section>

    <section v-else class="game-shell">
      <p>게임 상태를 복구하는 중입니다.</p>
    </section>
  </main>
</template>

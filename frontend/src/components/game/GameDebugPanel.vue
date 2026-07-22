<script setup lang="ts">
import type { GameConnectionState, GamePublicState } from '@/types/game'

defineProps<{
  publicState: GamePublicState
  mySeatOrder: number
  connectionState: GameConnectionState
}>()
</script>

<template>
  <details class="game-debug-panel">
    <summary>게임 정보</summary>
    <dl>
      <div><dt>Game</dt><dd>#{{ publicState.gameId }}</dd></div>
      <div><dt>Status</dt><dd>{{ publicState.status }}</dd></div>
      <div><dt>Version</dt><dd>{{ publicState.gameVersion }}</dd></div>
      <div><dt>Turn</dt><dd>{{ publicState.turnNumber }}</dd></div>
      <div><dt>내 좌석</dt><dd>{{ mySeatOrder }}</dd></div>
      <div><dt>연속 PASS</dt><dd>{{ publicState.consecutivePassCount }}</dd></div>
      <div><dt>Meld</dt><dd>{{ publicState.tableMelds.length }}</dd></div>
      <div><dt>연결</dt><dd>{{ connectionState }}</dd></div>
    </dl>
  </details>
</template>


<script setup lang="ts">
import { computed } from 'vue'

import type { GamePlayerPublicState } from '@/types/game'

const props = defineProps<{
  player: GamePlayerPublicState
  position: 'left' | 'center' | 'right'
  remainingSeconds: number
  avatarAsset?: string
}>()

const initials = computed(() => props.player.nickname.trim().slice(0, 2).toUpperCase() || `S${props.player.seatOrder}`)
const avatarStyle = computed(() => props.avatarAsset
  ? { backgroundImage: `url(${props.avatarAsset})` }
  : undefined)
</script>

<template>
  <article
    class="player-seat"
    :class="[`player-seat--${position}`, { 'player-seat--turn': player.currentTurn }]"
    :aria-label="`${player.nickname}, Rack ${player.rackTileCount}개${player.currentTurn ? ', 현재 턴' : ''}`"
  >
    <div class="player-seat__avatar-ring">
      <div class="player-seat__avatar" :style="avatarStyle" aria-hidden="true">{{ initials }}</div>
      <span v-if="player.currentTurn" class="player-seat__timer">{{ remainingSeconds }}</span>
    </div>
    <div class="player-seat__nameplate">
      <strong>{{ player.nickname }}</strong>
      <span aria-label="상대 Rack 타일 개수">▣ {{ player.rackTileCount }}</span>
    </div>
  </article>
</template>


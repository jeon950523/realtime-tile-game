<script setup lang="ts">
import { computed } from 'vue'

import type { GameAssetSet } from '@/config/gameAssets'
import type { GameConnectionState, GameMode, GameStatus } from '@/types/game'

const props = withDefaults(defineProps<{
  gameId: number
  gameMode: GameMode
  gameStatus: GameStatus
  connectionState: GameConnectionState
  isMyTurn: boolean
  exitPending?: boolean
  assets: Readonly<GameAssetSet>
}>(), {
  exitPending: false,
})

defineEmits<{
  exit: []
  logout: []
}>()

const boardStyle = computed(() => props.assets.boardTexture
  ? { backgroundImage: `url(${props.assets.boardTexture})` }
  : undefined)
const menuStyle = computed(() => props.assets.menuIcon
  ? { backgroundImage: `url(${props.assets.menuIcon})` }
  : undefined)
</script>

<template>
  <section
    class="game-board"
    :class="{ 'game-board--my-turn': isMyTurn }"
    :style="boardStyle"
    aria-label="실시간 타일 게임 보드"
  >
    <header class="game-board__topbar">
      <div class="game-board__identity">
        <span class="game-board__logo" aria-hidden="true">RT</span>
        <p><strong>REALTIME TILE</strong><small>#{{ gameId }} · {{ gameMode }} · {{ gameStatus }}</small></p>
      </div>
      <div class="game-board__connection" :data-state="connectionState" role="status">
        <span aria-hidden="true" /> {{ connectionState }}
      </div>
      <button
        v-if="gameStatus === 'IN_PROGRESS'"
        class="game-board__menu"
        type="button"
        aria-label="게임 포기 및 나가기"
        :disabled="exitPending"
        @click="$emit('exit')"
      >
        <span :style="menuStyle" aria-hidden="true">☰</span>
        {{ exitPending ? '처리 중…' : '게임 포기 및 나가기' }}
      </button>
      <button class="game-board__menu" type="button" aria-label="로그아웃" @click="$emit('logout')">
        로그아웃
      </button>
    </header>

    <div v-if="isMyTurn" class="game-board__turn-indicator" role="status" aria-live="polite">
      <span aria-hidden="true" />
      내 턴 · 테이블과 Rack을 조작할 수 있습니다
    </div>

    <div class="game-board__alerts">
      <slot name="alerts" />
    </div>

    <div class="game-board__main">
      <div class="game-board__opponents">
        <slot name="opponents" />
      </div>

      <section class="game-board__table" aria-label="중앙 타일 Meld 영역">
        <div class="game-board__watermark" aria-hidden="true"><span>REALTIME</span><strong>TILE TABLE</strong></div>
        <slot name="table" />
      </section>

      <div class="game-board__action-panel">
        <slot name="action" />
      </div>
    </div>

    <div class="game-board__bottom">
      <slot name="toolbar" />
      <section
        class="game-board__rack-area"
        :class="{ 'game-board__rack-area--my-turn': isMyTurn }"
        aria-label="내 Rack 영역"
      >
        <slot name="rack-heading" />
        <slot name="rack" />
      </section>
      <div class="game-board__bottom-spacer" aria-hidden="true" />
    </div>

    <div class="game-board__debug">
      <slot name="debug" />
    </div>
  </section>
</template>

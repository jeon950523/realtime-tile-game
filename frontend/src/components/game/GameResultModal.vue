<script setup lang="ts">
import { computed } from 'vue'

import { meldModifierStyle } from '@/domain/game/meldModifier'
import type { GameResultSnapshot } from '@/types/game'

const props = defineProps<{
  result: GameResultSnapshot
  leaving: boolean
}>()

const emit = defineEmits<{
  leaveLobby: []
}>()

const winnerLabel = computed(() => props.result.winnerNickname?.trim() || null)
const winnerSeatStyle = computed(() => meldModifierStyle(props.result.winnerSeatOrder))
</script>

<template>
  <div
    class="game-result-modal"
    role="dialog"
    aria-modal="true"
    aria-labelledby="game-result-title"
    aria-describedby="game-result-description"
  >
    <section class="game-result-modal__panel" data-testid="game-result-modal">
      <p class="game-result-modal__eyebrow">GAME RESULT</p>
      <h2 id="game-result-title">{{ result.didIWin ? '승리했습니다!' : '게임 종료' }}</h2>

      <div
        class="game-result-modal__winner"
        :style="winnerSeatStyle"
        :data-winner-seat="result.winnerSeatOrder ?? 'unknown'"
      >
        <span class="game-result-modal__seat">
          {{ result.winnerSeatOrder === null ? 'SEAT ?' : `SEAT ${result.winnerSeatOrder}` }}
        </span>
        <strong>{{ winnerLabel ?? '승자 정보 확인 불가' }}</strong>
        <small>종료 사유 · Rack 소진</small>
      </div>

      <div id="game-result-description" class="game-result-modal__description">
        <template v-if="winnerLabel">
          <p v-if="result.didIWin">{{ winnerLabel }}님이 모든 Rack 타일을 소진했습니다.</p>
          <template v-else>
            <p>{{ winnerLabel }}님이 승리했습니다.</p>
            <p>모든 Rack 타일을 먼저 소진했습니다.</p>
          </template>
        </template>
        <p v-else>승자가 결정되었습니다.</p>
      </div>

      <button
        type="button"
        class="game-result-modal__leave"
        :disabled="leaving"
        @click="emit('leaveLobby')"
      >
        {{ leaving ? '로비로 이동 중…' : '로비로 나가기' }}
      </button>
    </section>
  </div>
</template>

<style scoped>
.game-result-modal {
  position: fixed;
  z-index: 100;
  display: grid;
  place-items: center;
  inset: 0;
  padding: 24px;
  background:
    radial-gradient(circle at 50% 35%, rgb(34 95 164 / 30%), transparent 42%),
    rgb(2 10 27 / 82%);
  backdrop-filter: blur(10px);
}

.game-result-modal__panel {
  display: grid;
  width: min(100%, 470px);
  gap: 20px;
  padding: clamp(28px, 5vw, 44px);
  border: 1px solid rgb(126 178 235 / 36%);
  border-radius: 28px;
  color: var(--game-text, #f4f8ff);
  background: linear-gradient(155deg, rgb(13 35 67 / 98%), rgb(5 19 43 / 98%));
  box-shadow: 0 28px 90px rgb(0 0 0 / 58%), inset 0 1px 0 rgb(255 255 255 / 8%);
  text-align: center;
}

.game-result-modal__eyebrow {
  margin: 0;
  color: #83bfff;
  font-size: 0.72rem;
  font-weight: 800;
  letter-spacing: 0.22em;
}

.game-result-modal h2 {
  margin: -8px 0 0;
  font-size: clamp(2rem, 7vw, 3rem);
  line-height: 1.05;
}

.game-result-modal__winner {
  display: grid;
  justify-items: center;
  gap: 6px;
  padding: 18px;
  border: 2px solid var(--meld-modifier-color, #7f9bb9);
  border-radius: 20px;
  background: var(--meld-modifier-soft, rgb(127 155 185 / 14%));
  box-shadow: 0 0 24px var(--meld-modifier-soft, transparent);
}

.game-result-modal__winner strong {
  color: var(--meld-modifier-text, #eef7ff);
  font-size: 1.35rem;
}

.game-result-modal__winner small {
  color: rgb(218 231 247 / 72%);
}

.game-result-modal__seat {
  color: var(--meld-modifier-color, #a7bed8);
  font-size: 0.75rem;
  font-weight: 900;
  letter-spacing: 0.16em;
}

.game-result-modal__description {
  min-height: 52px;
  color: rgb(231 240 252 / 88%);
  line-height: 1.65;
}

.game-result-modal__description p {
  margin: 0;
}

.game-result-modal__leave {
  min-height: 52px;
  border: 1px solid rgb(121 190 255 / 62%);
  border-radius: 14px;
  color: #041326;
  background: linear-gradient(135deg, #8dcbff, #5fa6ff);
  box-shadow: 0 12px 26px rgb(35 121 220 / 32%);
  font: inherit;
  font-weight: 900;
  cursor: pointer;
}

.game-result-modal__leave:disabled {
  cursor: wait;
  opacity: 0.66;
}
</style>

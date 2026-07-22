<script setup lang="ts">
import { computed } from 'vue'

import type { GameRackTile } from '@/types/game'

const props = defineProps<{
  tile: GameRackTile
  index: number
  dragging?: boolean
  entering?: boolean
  ghost?: boolean
  interactionLocked?: boolean
}>()

const emit = defineEmits<{
  pointerdown: [tileId: string, event: PointerEvent]
}>()

const colorNames: Record<Exclude<GameRackTile['color'], null>, string> = {
  RED: '빨간색',
  BLUE: '파란색',
  YELLOW: '노란색',
  BLACK: '검은색',
}

const accessibleName = computed(() => props.tile.joker
  ? '조커 타일'
  : `${colorNames[props.tile.color!]} ${props.tile.number} 타일`)

function handlePointerDown(event: PointerEvent): void {
  if (
    props.ghost
    || props.entering
    || props.interactionLocked
    || (event.pointerType === 'mouse' && event.button !== 0)
  ) return
  emit('pointerdown', props.tile.tileId, event)
}
</script>

<template>
  <button
    class="game-tile"
    :class="{
      'game-tile--placeholder': dragging,
      'game-tile--entering': entering,
      'game-tile--ghost': ghost,
    }"
    type="button"
    :aria-label="ghost ? undefined : `${accessibleName}, 표시 순서 ${index + 1}`"
    :aria-hidden="ghost ? 'true' : undefined"
    :aria-grabbed="dragging"
    :tabindex="ghost ? -1 : undefined"
    :data-color="tile.color ?? 'JOKER'"
    :data-tile-id="ghost ? undefined : tile.tileId"
    :data-ghost-tile-id="ghost ? tile.tileId : undefined"
    :data-entering="!ghost && entering ? 'true' : undefined"
    @pointerdown="handlePointerDown"
  >
    <span class="game-tile__corner" aria-hidden="true">{{ tile.joker ? '★' : tile.number }}</span>
    <strong class="game-tile__value" aria-hidden="true">{{ tile.joker ? '★' : tile.number }}</strong>
    <span class="game-tile__mark" aria-hidden="true">{{ tile.joker ? 'JOKER' : 'TILE' }}</span>
  </button>
</template>

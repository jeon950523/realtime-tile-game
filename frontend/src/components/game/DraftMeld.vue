<script setup lang="ts">
import { computed, ref } from 'vue'

import GameTile from '@/components/game/GameTile.vue'
import type { GameRackTile, GameTableTile } from '@/types/game'
import type { DraftMeldValidation, ReadonlyDraftMeld } from '@/types/turnDraft'

type WorkingTile = GameRackTile | GameTableTile

const props = defineProps<{
  meld: ReadonlyDraftMeld
  validation: DraftMeldValidation
  tileById?: ReadonlyMap<string, WorkingTile>
  rackById?: ReadonlyMap<string, GameRackTile>
  editable?: boolean
  lockedReason?: string | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  tableDragStart: [drag: { kind: 'TILE'; tileId: string } | { kind: 'MELD'; meldId: string }]
  tableDragEnd: []
}>()

const tiles = computed(() => props.tileById ?? props.rackById ?? new Map<string, WorkingTile>())
const canEdit = computed(() => Boolean(props.editable && !props.disabled && !props.lockedReason))
const activeNativeDragTileId = ref<string | null>(null)

function startTableDrag(tileId: string, event: DragEvent): void {
  if (!canEdit.value || !event.dataTransfer) {
    event.preventDefault()
    return
  }
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('application/x-working-tile-id', tileId)
  event.dataTransfer.setData('text/plain', tileId)
  activeNativeDragTileId.value = tileId
  emit('tableDragStart', { kind: 'TILE', tileId })
}

function startCandidateDrag(event: DragEvent): void {
  if (!canEdit.value || !event.dataTransfer) {
    event.preventDefault()
    return
  }
  event.dataTransfer.effectAllowed = 'move'
  event.dataTransfer.setData('application/x-working-meld-id', props.meld.clientMeldId)
  event.dataTransfer.setData('text/plain', props.meld.clientMeldId)
  emit('tableDragStart', { kind: 'MELD', meldId: props.meld.clientMeldId })
}

function finishTableDrag(): void {
  activeNativeDragTileId.value = null
  emit('tableDragEnd')
}
</script>

<template>
  <article
    class="draft-meld working-meld"
    :class="[
      `draft-meld--${validation.kind.toLowerCase()}`,
      { 'working-meld--locked': !canEdit, 'working-meld--dragging': activeNativeDragTileId !== null },
    ]"
    :data-draft-meld-id="meld.clientMeldId"
    :data-working-meld-id="meld.clientMeldId"
    :title="lockedReason ?? validation.reason ?? validation.kind"
  >
    <header
      class="draft-meld__chrome"
      :draggable="canEdit"
      aria-label="Candidate 전체 이동"
      @dragstart.stop="startCandidateDrag"
      @dragend="finishTableDrag"
    >
      <strong class="draft-meld__kind">{{ validation.kind }}</strong>
    </header>
    <div class="draft-meld__tiles">
      <div
        v-for="(tileId, index) in meld.tileIds"
        :key="tileId"
        class="draft-tile-slot"
        :class="{ 'draft-tile-slot--drag-source': activeNativeDragTileId === tileId }"
        :draggable="canEdit"
        :data-working-tile-id="tileId"
        @dragstart="startTableDrag(tileId, $event)"
        @dragend="finishTableDrag"
      >
        <GameTile v-if="tiles.get(tileId)" :tile="tiles.get(tileId)!" :index="index" interaction-locked />
      </div>
    </div>
  </article>
</template>

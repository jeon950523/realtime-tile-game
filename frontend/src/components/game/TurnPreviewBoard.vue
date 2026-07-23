<script setup lang="ts">
import { computed } from 'vue'

import GameTile from '@/components/game/GameTile.vue'
import { meldModifierStyle, normalizeMeldModifierSeat } from '@/domain/game/meldModifier'
import { deriveTableCandidates } from '@/domain/game/tableCandidateDerivation'
import { TABLE_GRID_COLUMNS, TABLE_GRID_ROWS, TABLE_GRID_VISIBLE_ROWS } from '@/domain/game/tableGrid'
import { tableContentRows } from '@/domain/game/tableFlow'
import { publicPreviewTileFromId } from '@/domain/game/turnPreview'
import type { GameTableMeld, TurnPreviewSnapshot } from '@/types/game'
import type { DerivedTableCandidate, WorkingTilePlacement } from '@/types/turnDraft'

const props = defineProps<{
  preview: TurnPreviewSnapshot
  committedMelds: readonly GameTableMeld[]
  turnPlayerNickname: string
  turnPlayerSeatOrder?: number
}>()

const committedMelds = computed(() => [...props.committedMelds]
  .sort((left, right) => left.positionOrder - right.positionOrder
    || left.meldId.localeCompare(right.meldId)))
const committedTileIds = computed(() => new Set(
  committedMelds.value.flatMap((meld) => meld.tiles.map((tile) => tile.tileId)),
))
const committedCoordinates = computed(() => new Map(
  committedMelds.value.flatMap((meld) => meld.tiles.map((tile) => [
    tile.tileId,
    `${meld.gridRow}:${meld.gridColumn + tile.positionOrder}`,
  ] as const)),
))
const previewPlacements = computed<WorkingTilePlacement[]>(() => props.preview.tilePlacements
  .map((placement): WorkingTilePlacement => ({
    ...placement,
    source: committedTileIds.value.has(placement.tileId)
      ? 'COMMITTED_TABLE'
      : 'CURRENT_PLAYER_RACK',
    sourceMeldId: null,
    originalPositionOrder: null,
  }))
  .sort((left, right) => left.gridRow - right.gridRow
    || left.gridColumn - right.gridColumn
    || left.tileId.localeCompare(right.tileId)))
const previewCandidates = computed(() => deriveTableCandidates(previewPlacements.value))
const contentRows = computed(() => tableContentRows(previewPlacements.value))

function tileStyle(placement: { gridRow: number; gridColumn: number }): Record<string, string> {
  return {
    '--table-grid-column': String(placement.gridColumn),
    '--table-grid-row': String(placement.gridRow),
  }
}

function placementChanged(placement: { tileId: string; gridRow: number; gridColumn: number }): boolean {
  return committedCoordinates.value.get(placement.tileId)
    !== `${placement.gridRow}:${placement.gridColumn}`
}

function matchingCommittedMeld(candidate: DerivedTableCandidate): GameTableMeld | null {
  return committedMelds.value.find((meld) => {
    const tileIds = [...meld.tiles]
      .sort((left, right) => left.positionOrder - right.positionOrder)
      .map((tile) => tile.tileId)
    return meld.gridRow === candidate.gridRow
      && meld.gridColumn === candidate.gridColumn
      && tileIds.length === candidate.tileIds.length
      && tileIds.every((tileId, index) => tileId === candidate.tileIds[index])
  }) ?? null
}

function candidateModifierSeat(candidate: DerivedTableCandidate): number | null {
  const committed = matchingCommittedMeld(candidate)
  return normalizeMeldModifierSeat(
    committed?.lastModifiedBySeatOrder ?? props.turnPlayerSeatOrder,
  )
}

function candidateStyle(candidate: DerivedTableCandidate): Record<string, string> {
  return {
    '--table-grid-column': String(candidate.gridColumn),
    '--table-grid-row': String(candidate.gridRow),
    '--table-grid-span': String(Math.max(1, candidate.tileIds.length)),
    ...meldModifierStyle(candidateModifierSeat(candidate)),
  }
}
</script>

<template>
  <section class="turn-preview-board" aria-label="상대의 확정 전 Table 미리보기">
    <header class="turn-preview-board__status">
      <strong>{{ turnPlayerNickname }}님이 테이블을 편집 중입니다</strong>
      <span>확정 전 미리보기</span>
    </header>
    <div
      class="turn-preview-grid table-scroll-viewport"
      :data-visible-rows="TABLE_GRID_VISIBLE_ROWS"
    >
      <div
        class="turn-preview-grid__canvas table-scroll-canvas"
        :data-grid-rows="TABLE_GRID_ROWS"
        :data-content-rows="contentRows"
        :data-grid-columns="TABLE_GRID_COLUMNS"
        :style="{ '--table-content-rows': String(contentRows) }"
      >
        <div
          v-for="candidate in previewCandidates"
          :key="`preview-outline-${candidate.clientMeldId}`"
          class="turn-preview-meld-outline"
          :style="candidateStyle(candidate)"
          :data-preview-meld-outline="candidate.clientMeldId"
          :data-last-modified-seat="candidateModifierSeat(candidate) ?? 'unknown'"
        >
          <span v-if="candidateModifierSeat(candidate)" aria-hidden="true">
            S{{ candidateModifierSeat(candidate) }}
          </span>
        </div>

        <div
          v-for="(placement, index) in previewPlacements"
          :key="placement.tileId"
          class="turn-preview-table-tile"
          :class="{
            'turn-preview-table-tile--new': !committedTileIds.has(placement.tileId),
            'turn-preview-table-tile--changed': placementChanged(placement),
          }"
          :style="tileStyle(placement)"
          :data-preview-tile-id="placement.tileId"
          data-table-tile="true"
          :data-grid-row="placement.gridRow"
          :data-grid-column="placement.gridColumn"
        >
          <GameTile
            v-if="publicPreviewTileFromId(placement.tileId, index)"
            :tile="publicPreviewTileFromId(placement.tileId, index)!"
            :index="index"
            interaction-locked
          />
        </div>
      </div>
    </div>
  </section>
</template>

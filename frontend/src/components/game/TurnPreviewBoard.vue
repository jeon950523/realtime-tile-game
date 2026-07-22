<script setup lang="ts">
import { computed } from 'vue'

import GameTile from '@/components/game/GameTile.vue'
import { TABLE_GRID_COLUMNS, TABLE_GRID_ROWS, TABLE_GRID_VISIBLE_ROWS } from '@/domain/game/tableGrid'
import { flowCommittedTableMelds, flowWorkingPlacements, tableContentRows } from '@/domain/game/tableFlow'
import { publicPreviewTileFromId } from '@/domain/game/turnPreview'
import type { GameTableMeld, TurnPreviewSnapshot } from '@/types/game'
import type { WorkingTilePlacement } from '@/types/turnDraft'

const props = defineProps<{
  preview: TurnPreviewSnapshot
  committedMelds: readonly GameTableMeld[]
  turnPlayerNickname: string
}>()

const committedFlow = computed(() => flowCommittedTableMelds(props.committedMelds))
const committedTileIds = computed(() => new Set(
  committedFlow.value.flatMap((meld) => meld.tiles.map((tile) => tile.tileId)),
))
const committedCoordinates = computed(() => new Map(
  committedFlow.value.flatMap((meld) => meld.tiles.map((tile) => [
    tile.tileId,
    `${meld.gridRow}:${meld.gridColumn + tile.positionOrder}`,
  ] as const)),
))
const flowedPreviewPlacements = computed(() => flowWorkingPlacements(
  props.preview.tilePlacements.map((placement) => ({
    ...placement,
    source: committedTileIds.value.has(placement.tileId)
      ? 'COMMITTED_TABLE'
      : 'CURRENT_PLAYER_RACK',
    sourceMeldId: null,
    originalPositionOrder: null,
  } satisfies WorkingTilePlacement)),
))
const contentRows = computed(() => tableContentRows(flowedPreviewPlacements.value))
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
          v-for="(placement, index) in flowedPreviewPlacements"
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

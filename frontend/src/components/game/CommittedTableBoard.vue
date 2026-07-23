<script setup lang="ts">
import { computed } from 'vue'

import GameTile from '@/components/game/GameTile.vue'
import { meldModifierStyle, normalizeMeldModifierSeat } from '@/domain/game/meldModifier'
import { TABLE_GRID_COLUMNS, TABLE_GRID_ROWS, TABLE_GRID_VISIBLE_ROWS } from '@/domain/game/tableGrid'
import { tableContentRows } from '@/domain/game/tableFlow'
import type { GamePlayerPublicState, GameTableMeld, GameTableTile } from '@/types/game'

const props = withDefaults(defineProps<{
  melds: readonly GameTableMeld[]
  players?: readonly GamePlayerPublicState[]
}>(), {
  players: () => [],
})

const displayMelds = computed(() => [...props.melds]
  .sort((left, right) => left.positionOrder - right.positionOrder
    || left.meldId.localeCompare(right.meldId)))
const displayPlayers = computed(() => [...props.players]
  .sort((left, right) => left.seatOrder - right.seatOrder))
const placements = computed(() => displayMelds.value.flatMap((meld) => [...meld.tiles]
  .sort((left, right) => left.positionOrder - right.positionOrder)
  .map((tile) => ({
    meldId: meld.meldId,
    tileId: tile.tileId,
    tile,
    gridRow: meld.gridRow,
    gridColumn: meld.gridColumn + tile.positionOrder,
  }))))
const contentRows = computed(() => tableContentRows(placements.value))

function tileStyle(placement: { gridRow: number; gridColumn: number }): Record<string, string> {
  return {
    '--table-grid-column': String(placement.gridColumn),
    '--table-grid-row': String(placement.gridRow),
  }
}

function meldStyle(meld: GameTableMeld): Record<string, string> {
  return {
    '--table-grid-column': String(meld.gridColumn),
    '--table-grid-row': String(meld.gridRow),
    '--table-grid-span': String(Math.max(1, meld.tiles.length)),
    ...meldModifierStyle(meld.lastModifiedBySeatOrder),
  }
}

function modifierLabel(meld: GameTableMeld): string {
  const seat = normalizeMeldModifierSeat(meld.lastModifiedBySeatOrder)
  if (!seat) return '마지막 수정자 정보 없음'
  const player = props.players.find((candidate) => candidate.seatOrder === seat)
  return `마지막 수정: ${player?.nickname ?? `Seat ${seat}`}`
}
</script>

<template>
  <section
    class="committed-table committed-table-grid"
    aria-label="확정된 Table Meld"
  >
    <header class="committed-table-grid__status">
      <div>
        <strong>확정된 Table</strong>
        <span>{{ displayMelds.length }}개 조합</span>
      </div>
      <div v-if="displayPlayers.length" class="meld-modifier-legend" aria-label="플레이어별 Meld 외곽선 색상">
        <span
          v-for="player in displayPlayers"
          :key="player.userId"
          class="meld-modifier-legend__item"
          :style="meldModifierStyle(player.seatOrder)"
          :data-modifier-seat="player.seatOrder"
        >
          <i aria-hidden="true" />{{ player.nickname }}
        </span>
      </div>
    </header>
    <div
      class="committed-table-grid__viewport table-scroll-viewport"
      :data-visible-rows="TABLE_GRID_VISIBLE_ROWS"
    >
      <div
        class="committed-table-grid__canvas table-scroll-canvas"
        :data-grid-rows="TABLE_GRID_ROWS"
        :data-content-rows="contentRows"
        :data-grid-columns="TABLE_GRID_COLUMNS"
        :style="{ '--table-content-rows': String(contentRows) }"
      >
        <div
          v-for="meld in displayMelds"
          :key="`outline-${meld.meldId}`"
          class="committed-table-meld-outline"
          :style="meldStyle(meld)"
          :title="modifierLabel(meld)"
          :data-committed-meld-outline="meld.meldId"
          :data-last-modified-seat="normalizeMeldModifierSeat(meld.lastModifiedBySeatOrder) ?? 'unknown'"
          :data-last-modified-user-id="meld.lastModifiedByUserId ?? 'unknown'"
        >
          <span v-if="normalizeMeldModifierSeat(meld.lastModifiedBySeatOrder)" aria-hidden="true">
            S{{ normalizeMeldModifierSeat(meld.lastModifiedBySeatOrder) }}
          </span>
        </div>

        <div
          v-for="(placement, index) in placements"
          :key="placement.tile.tileId"
          class="committed-table-tile"
          :data-committed-meld-id="placement.meldId"
          :data-committed-table-tile-id="placement.tile.tileId"
          data-table-tile="true"
          :data-grid-row="placement.gridRow"
          :data-grid-column="placement.gridColumn"
          :style="tileStyle(placement)"
        >
          <GameTile :tile="placement.tile as GameTableTile" :index="index" interaction-locked />
        </div>
      </div>
    </div>
  </section>
</template>

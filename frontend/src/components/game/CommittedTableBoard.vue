<script setup lang="ts">
import { computed } from 'vue'

import GameTile from '@/components/game/GameTile.vue'
import { TABLE_GRID_COLUMNS, TABLE_GRID_ROWS, TABLE_GRID_VISIBLE_ROWS } from '@/domain/game/tableGrid'
import { tableContentRows } from '@/domain/game/tableFlow'
import type { GameTableMeld, GameTableTile } from '@/types/game'

const props = defineProps<{ melds: readonly GameTableMeld[] }>()

const displayMelds = computed(() => [...props.melds]
  .sort((left, right) => left.positionOrder - right.positionOrder
    || left.meldId.localeCompare(right.meldId)))
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
</script>

<template>
  <section
    class="committed-table committed-table-grid"
    aria-label="확정된 Table Meld"
  >
    <header class="committed-table-grid__status">
      <strong>확정된 Table</strong>
      <span>{{ displayMelds.length }}개 조합</span>
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

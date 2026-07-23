import { mount } from '@vue/test-utils'
import { effectScope, ref } from 'vue'
import { describe, expect, it } from 'vitest'

import CommittedTableBoard from '@/components/game/CommittedTableBoard.vue'
import TurnPreviewBoard from '@/components/game/TurnPreviewBoard.vue'
import { committedMeldsToPlacements, useWorkingTable } from '@/composables/game/useWorkingTable'
import type { GameRackTile, GameTableMeld, TurnPreviewSnapshot } from '@/types/game'

function tableMeld(
  meldId: string,
  gridRow: number,
  gridColumn: number,
  positionOrder = 0,
): GameTableMeld {
  return {
    meldId,
    meldType: 'RUN',
    score: 24,
    positionOrder,
    gridRow,
    gridColumn,
    tiles: [7, 8, 9].map((number, index) => ({
      tileId: `RED-${String(number).padStart(2, '0')}-A`,
      tileType: 'NUMBER',
      color: 'RED',
      number,
      joker: false,
      positionOrder: index,
    })),
  }
}

describe('Phase 7 committed table coordinate preservation fix', () => {
  it('P7-POS-001 converts committed melds without reflowing authoritative coordinates', () => {
    const placements = committedMeldsToPlacements([tableMeld('meld-a', 5, 7)])

    expect(placements.map((placement) => [
      placement.tileId,
      placement.gridRow,
      placement.gridColumn,
    ])).toEqual([
      ['RED-07-A', 5, 7],
      ['RED-08-A', 5, 8],
      ['RED-09-A', 5, 9],
    ])
  })

  it('P7-POS-002 creates the working baseline at the server-authoritative position', () => {
    const scope = effectScope()
    const table = ref<GameTableMeld[]>([tableMeld('meld-a', 4, 6)])
    const rack = ref<GameRackTile[]>([])
    const working = scope.run(() => useWorkingTable({
      authoritativeRack: rack,
      authoritativeVersion: ref(10),
      authoritativeSyncRevision: ref(1),
      tableMelds: table,
      initialMeldCompleted: ref(true),
      isMyTurn: ref(true),
      gameId: ref(1),
      currentTurnPlayerId: ref(1),
    }))!

    expect(working.placements.value.map((placement) => [
      placement.gridRow,
      placement.gridColumn,
    ])).toEqual([[4, 6], [4, 7], [4, 8]])
    expect(working.isMeldEditable('meld-a')).toBe(true)
    scope.stop()
  })

  it('P7-POS-003 renders committed table tiles at their persisted position', () => {
    const wrapper = mount(CommittedTableBoard, {
      props: { melds: [tableMeld('meld-a', 6, 10)] },
    })
    const tiles = wrapper.findAll('[data-committed-table-tile-id]')

    expect(tiles.map((tile) => [
      tile.attributes('data-grid-row'),
      tile.attributes('data-grid-column'),
    ])).toEqual([['6', '10'], ['6', '11'], ['6', '12']])
  })

  it('P7-POS-004 renders live turn preview placements without top-left reflow', () => {
    const preview: TurnPreviewSnapshot = {
      gameId: 1,
      baseGameVersion: 10,
      previewRevision: 2,
      turnPlayerId: 2,
      updatedAt: '2026-07-23T09:00:00+09:00',
      tilePlacements: [
        { tileId: 'RED-07-A', gridRow: 7, gridColumn: 11, source: 'COMMITTED_TABLE' },
        { tileId: 'RED-08-A', gridRow: 7, gridColumn: 12, source: 'COMMITTED_TABLE' },
        { tileId: 'RED-09-A', gridRow: 7, gridColumn: 13, source: 'COMMITTED_TABLE' },
      ],
    }
    const wrapper = mount(TurnPreviewBoard, {
      props: {
        preview,
        committedMelds: [tableMeld('meld-a', 2, 3)],
        turnPlayerNickname: 'asdf',
      },
    })
    const tiles = wrapper.findAll('[data-preview-tile-id]')

    expect(tiles.map((tile) => [
      tile.attributes('data-grid-row'),
      tile.attributes('data-grid-column'),
    ])).toEqual([['7', '11'], ['7', '12'], ['7', '13']])
  })

  it('P7-POS-005 keeps committed melds locked before the current player completes the initial meld', () => {
    const scope = effectScope()
    const table = ref<GameTableMeld[]>([tableMeld('meld-a', 4, 6)])
    const working = scope.run(() => useWorkingTable({
      authoritativeRack: ref<GameRackTile[]>([]),
      authoritativeVersion: ref(10),
      authoritativeSyncRevision: ref(1),
      tableMelds: table,
      initialMeldCompleted: ref(false),
      isMyTurn: ref(true),
      gameId: ref(1),
      currentTurnPlayerId: ref(1),
    }))!

    expect(working.isMeldEditable('meld-a')).toBe(false)
    scope.stop()
  })
})

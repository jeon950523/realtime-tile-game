import { mount } from '@vue/test-utils'
import { effectScope, ref } from 'vue'
import { describe, expect, it } from 'vitest'

import WorkingTableBoard from '@/components/game/WorkingTableBoard.vue'
import { useWorkingTable } from '@/composables/game/useWorkingTable'
import {
  TABLE_GRID_CANVAS_WIDTH_PX,
  TABLE_GRID_VIEWPORT_HEIGHT_PX,
} from '@/domain/game/tableGrid'
import {
  resolveInteractiveTableCoordinate,
  resolveNearestTableCoordinate,
} from '@/domain/game/tableFlow'
import { deriveTableCandidates } from '@/domain/game/tableCandidateDerivation'
import { validateTurnDraft } from '@/domain/game/turnDraftValidation'
import type { GameRackTile, GameTableMeld } from '@/types/game'
import type { RackDropTarget, WorkingTilePlacement } from '@/types/turnDraft'

function tile(number: number): GameRackTile {
  return {
    tileId: `RED-${String(number).padStart(2, '0')}-A`,
    tileType: 'NUMBER',
    color: 'RED',
    number,
    joker: false,
    positionOrder: number,
  }
}

function placement(item: GameRackTile, gridRow: number, gridColumn: number): WorkingTilePlacement {
  return {
    tileId: item.tileId,
    gridRow,
    gridColumn,
    source: 'CURRENT_PLAYER_RACK',
    sourceMeldId: null,
    originalPositionOrder: null,
  }
}

const red6 = tile(6)
const red7 = tile(7)
const red8 = tile(8)
const red9 = tile(9)
const red10 = tile(10)

function workingFixture() {
  const scope = effectScope()
  const rack = ref<GameRackTile[]>([red6, red7, red8, red9, red10])
  const working = scope.run(() => useWorkingTable({
    authoritativeRack: rack,
    authoritativeVersion: ref(7),
    authoritativeSyncRevision: ref(1),
    tableMelds: ref<GameTableMeld[]>([]),
    initialMeldCompleted: ref(true),
    isMyTurn: ref(true),
    gameId: ref(33),
    currentTurnPlayerId: ref(1),
  }))!
  return { scope, rack, working }
}

describe('Phase 7 direct placement Candidate merge fix', () => {
  it('keeps a free adjacent user coordinate instead of forcing the automatic gutter', () => {
    const existing = [
      placement(red7, 0, 0),
      placement(red8, 0, 1),
      placement(red9, 0, 2),
    ]

    expect(resolveNearestTableCoordinate(existing, ['NEW'], 0, 3))
      .toEqual({ gridRow: 0, gridColumn: 4 })
    expect(resolveInteractiveTableCoordinate(existing, ['NEW'], 0, 3))
      .toEqual({ gridRow: 0, gridColumn: 3 })
    expect(resolveInteractiveTableCoordinate(existing, ['NEW'], 0, 2))
      .toEqual({ gridRow: 0, gridColumn: 4 })
  })

  it('places three Rack tiles one by one in consecutive cells and derives one Candidate', () => {
    const { scope, rack, working } = workingFixture()
    const displayOrder = rack.value.map((item) => item.tileId)

    expect(working.addAsNewMeld([red7.tileId], displayOrder, 0, 0)).toBe(true)
    expect(working.addAsNewMeld([red8.tileId], displayOrder, 0, 1)).toBe(true)
    expect(working.addAsNewMeld([red9.tileId], displayOrder, 0, 2)).toBe(true)

    expect(working.placements.value.map((item) => [item.tileId, item.gridRow, item.gridColumn]))
      .toEqual([
        [red7.tileId, 0, 0],
        [red8.tileId, 0, 1],
        [red9.tileId, 0, 2],
      ])
    expect(working.candidates.value).toHaveLength(1)
    expect(working.candidates.value[0]?.tileIds)
      .toEqual([red7.tileId, red8.tileId, red9.tileId])
    expect(working.validation.value).toMatchObject({ invalidCount: 0, validCount: 1 })

    scope.stop()
  })


  it('prepends 6 to an existing 789 Candidate without moving the original tiles', () => {
    const { scope, rack, working } = workingFixture()
    const displayOrder = rack.value.map((item) => item.tileId)

    expect(working.addAsNewMeld([red7.tileId], displayOrder, 2, 5)).toBe(true)
    expect(working.addAsNewMeld([red8.tileId], displayOrder, 2, 6)).toBe(true)
    expect(working.addAsNewMeld([red9.tileId], displayOrder, 2, 7)).toBe(true)
    expect(working.addAsNewMeld([red6.tileId], displayOrder, 2, 4)).toBe(true)

    expect(working.placements.value.map((item) => [item.tileId, item.gridRow, item.gridColumn]))
      .toEqual([
        [red6.tileId, 2, 4],
        [red7.tileId, 2, 5],
        [red8.tileId, 2, 6],
        [red9.tileId, 2, 7],
      ])
    expect(working.candidates.value).toHaveLength(1)
    expect(working.candidates.value[0]?.tileIds)
      .toEqual([red6.tileId, red7.tileId, red8.tileId, red9.tileId])

    scope.stop()
  })

  it('appends 10 to an existing 789 Candidate without moving the original tiles', () => {
    const { scope, rack, working } = workingFixture()
    const displayOrder = rack.value.map((item) => item.tileId)

    expect(working.addAsNewMeld([red7.tileId], displayOrder, 3, 5)).toBe(true)
    expect(working.addAsNewMeld([red8.tileId], displayOrder, 3, 6)).toBe(true)
    expect(working.addAsNewMeld([red9.tileId], displayOrder, 3, 7)).toBe(true)
    expect(working.addAsNewMeld([red10.tileId], displayOrder, 3, 8)).toBe(true)

    expect(working.placements.value.map((item) => [item.tileId, item.gridRow, item.gridColumn]))
      .toEqual([
        [red7.tileId, 3, 5],
        [red8.tileId, 3, 6],
        [red9.tileId, 3, 7],
        [red10.tileId, 3, 8],
      ])
    expect(working.candidates.value).toHaveLength(1)
    expect(working.candidates.value[0]?.tileIds)
      .toEqual([red7.tileId, red8.tileId, red9.tileId, red10.tileId])

    scope.stop()
  })

  it('returns the exact adjacent cell from the Rack-to-Table runtime drop resolver', () => {
    const rack = [red7, red8, red9]
    const placements = [placement(red7, 0, 0)]
    const candidates = deriveTableCandidates(placements)
    const wrapper = mount(WorkingTableBoard, {
      props: {
        placements,
        rack,
        baselineMelds: [],
        validation: validateTurnDraft(candidates, rack, true, [], placements),
        initialMeldCompleted: true,
        isMeldEditable: () => true,
      },
    })
    const canvas = wrapper.get<HTMLElement>('.working-table-grid__canvas').element
    canvas.getBoundingClientRect = () => ({
      left: 0,
      top: 0,
      right: TABLE_GRID_CANVAS_WIDTH_PX,
      bottom: TABLE_GRID_VIEWPORT_HEIGHT_PX,
      width: TABLE_GRID_CANVAS_WIDTH_PX,
      height: TABLE_GRID_VIEWPORT_HEIGHT_PX,
      x: 0,
      y: 0,
      toJSON: () => ({}),
    } as DOMRect)

    const exposed = wrapper.vm as unknown as {
      resolveRackDropTarget: (clientX: number, clientY: number, tileCount?: number) => RackDropTarget
      finishExternalRackDrag: () => void
    }
    const target = exposed.resolveRackDropTarget(87, 34, 1)

    expect(target).toMatchObject({
      kind: 'WORKING_NEW_MELD',
      gridRow: 0,
      gridColumn: 1,
    })

    exposed.finishExternalRackDrag()
    wrapper.unmount()
  })
})

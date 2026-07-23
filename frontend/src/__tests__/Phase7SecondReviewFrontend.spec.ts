import { mount } from '@vue/test-utils'
import { effectScope, nextTick, ref } from 'vue'
import { describe, expect, it } from 'vitest'

import GameBoard from '@/components/game/GameBoard.vue'
import TileRack from '@/components/game/TileRack.vue'
import WorkingTableBoard from '@/components/game/WorkingTableBoard.vue'
import { useWorkingTable } from '@/composables/game/useWorkingTable'
import { computeAdaptiveRackLayout } from '@/domain/game/rackLayout'
import { deriveTableCandidates } from '@/domain/game/tableCandidateDerivation'
import { validateTurnDraft } from '@/domain/game/turnDraftValidation'
import motionCss from '@/styles/game/game-motion.css?raw'
import type { GameRackTile, GameTableMeld } from '@/types/game'
import type { WorkingTilePlacement } from '@/types/turnDraft'


interface CandidateSpec {
  clientMeldId: string
  sourceMeldId: string | null
  tileIds: readonly string[]
  gridRow?: number
  gridColumn?: number
}

function candidateSpecsToPlacements(
  specs: readonly CandidateSpec[],
  rack: readonly GameRackTile[],
): WorkingTilePlacement[] {
  const rackIds = new Set(rack.map((tile) => tile.tileId))
  return specs.flatMap((spec) => spec.tileIds.map((tileId, offset) => {
    const fromRack = rackIds.has(tileId)
    return {
      tileId,
      gridRow: spec.gridRow ?? 0,
      gridColumn: (spec.gridColumn ?? 0) + offset,
      source: fromRack ? 'CURRENT_PLAYER_RACK' as const : 'COMMITTED_TABLE' as const,
      sourceMeldId: fromRack ? null : spec.sourceMeldId,
      originalPositionOrder: fromRack ? null : offset,
    }
  }))
}

function tile(color: GameRackTile['color'], number: number, copy = 'A', positionOrder = number): GameRackTile {
  return {
    tileId: `${color}-${String(number).padStart(2, '0')}-${copy}`,
    tileType: 'NUMBER', color, number, joker: false, positionOrder,
  }
}

function tableMeld(meldId: string, color: GameRackTile['color'], numbers: number[]): GameTableMeld {
  return {
    meldId, meldType: 'RUN', score: numbers.reduce((sum, number) => sum + number, 0), positionOrder: 0,
    gridRow: 0, gridColumn: 0,
    tiles: numbers.map((number, index) => ({ ...tile(color, number, 'A', index), positionOrder: index })),
  }
}

const firstId = '11111111-1111-4111-8111-111111111111'
const secondId = '22222222-2222-4222-8222-222222222222'

function fixture(initialCompleted = true) {
  const scope = effectScope()
  const rack = ref<GameRackTile[]>([
    tile('RED', 4), tile('BLUE', 7), tile('YELLOW', 7), tile('YELLOW', 8), tile('YELLOW', 9),
  ])
  const table = ref<GameTableMeld[]>([
    tableMeld(firstId, 'RED', [1, 2, 3]),
    { ...tableMeld(secondId, 'BLUE', [4, 5, 6]), positionOrder: 1, gridColumn: 8 },
  ])
  const version = ref(3)
  const syncRevision = ref(0)
  const initial = ref(initialCompleted)
  const isMyTurn = ref(true)
  const working = scope.run(() => useWorkingTable({
    authoritativeRack: rack,
    authoritativeVersion: version,
    authoritativeSyncRevision: syncRevision,
    tableMelds: table,
    initialMeldCompleted: initial,
    isMyTurn,
  }))!
  return { scope, rack, table, version, syncRevision, initial, isMyTurn, working }
}

describe('Phase 7 second review adaptive rack layout', () => {
  it('LAYOUT-001 keeps 21 rack tiles in two rows', () => {
    expect(computeAdaptiveRackLayout(21, 870)).toMatchObject({ columns: 11, rows: 2 })
  })

  it('LAYOUT-002 keeps 30 rack tiles in two rows', () => {
    expect(computeAdaptiveRackLayout(30, 870)).toMatchObject({ columns: 15, rows: 2 })
  })

  it('LAYOUT-003 shrinks tiles when the available viewport width narrows', () => {
    expect(computeAdaptiveRackLayout(30, 650).tileWidth)
      .toBeLessThan(computeAdaptiveRackLayout(30, 1100).tileWidth)
  })

  it('LAYOUT-004 renders one working table in the board table slot without a second draft dock', () => {
    const wrapper = mount(GameBoard, {
      props: { gameId: 1, gameMode: 'CLASSIC', gameStatus: 'IN_PROGRESS', connectionState: 'CONNECTED', isMyTurn: true, assets: {} },
      slots: { table: '<section class="working-table-board" />' },
    })
    expect(wrapper.findAll('.game-board__table .working-table-board')).toHaveLength(1)
    expect(wrapper.find('.turn-draft-board').exists()).toBe(false)
  })

  it('LAYOUT-005 uses a bounded rack height derived from rows instead of a third overflowing row', () => {
    const layout = computeAdaptiveRackLayout(30, 870)
    expect(layout.rackHeight).toBe(layout.tileHeight * 2 + layout.gap + 26)
  })

  it('LAYOUT-006 exposes the same adaptive column count used by drag slot geometry', () => {
    const tiles = Array.from({ length: 30 }, (_, index) => tile('RED', index % 13 + 1, `${index}`))
    const wrapper = mount(TileRack, { props: {
      tiles, activeDragTileIds: [], enteringTileIds: [], isMyTurn: true,
    } })
    expect(wrapper.find('.tile-rack').attributes('data-rack-columns')).toBe('15')
    expect(wrapper.findAll('.rack-tile-slot')).toHaveLength(30)
  })
})

describe('Phase 7 second review current-turn UX', () => {
  it('TURN-UX-001 adds the strong current-turn rack class', () => {
    const wrapper = mount(TileRack, { props: { tiles: [], activeDragTileIds: [], enteringTileIds: [], isMyTurn: true } })
    expect(wrapper.find('.tile-rack--my-turn').exists()).toBe(true)
  })

  it('TURN-UX-002 removes the green rack class on an opponent turn', () => {
    const wrapper = mount(TileRack, { props: { tiles: [], activeDragTileIds: [], enteringTileIds: [], isMyTurn: false } })
    expect(wrapper.find('.tile-rack--my-turn').exists()).toBe(false)
  })

  it('TURN-UX-003 marks both rack area and action ownership from one isMyTurn authority', () => {
    const wrapper = mount(GameBoard, {
      props: { gameId: 1, gameMode: 'CLASSIC', gameStatus: 'IN_PROGRESS', connectionState: 'CONNECTED', isMyTurn: true, assets: {} },
      slots: { rack: '<div />', action: '<aside class="turn-action turn-action--my-turn" />' },
    })
    expect(wrapper.find('.game-board__rack-area--my-turn').exists()).toBe(true)
    expect(wrapper.find('.turn-action--my-turn').exists()).toBe(true)
  })

  it('TURN-UX-004 reduced motion contains no turn pulse animation', () => {
    const reduced = motionCss.slice(motionCss.indexOf('@media (prefers-reduced-motion: reduce)'))
    expect(reduced).not.toContain('pulse')
  })
})

describe('Phase 7 second review status bar', () => {
  function statusWrapper(specs: CandidateSpec[], rack: GameRackTile[], baseline: GameTableMeld[], completed: boolean) {
    const placements = candidateSpecsToPlacements(specs, rack)
    const candidates = deriveTableCandidates(placements)
    const validation = validateTurnDraft(candidates, rack, completed, baseline, placements)
    return mount(WorkingTableBoard, { props: {
      placements, rack, baselineMelds: baseline, validation, initialMeldCompleted: completed,
      isMeldEditable: () => true,
    } })
  }

  it('STATUS-001 keeps first-registration score out of meld and table headers', () => {
    const rack = [tile('RED', 7), tile('RED', 8), tile('RED', 9)]
    const wrapper = statusWrapper([{ clientMeldId: 'new', sourceMeldId: null, tileIds: rack.map((item) => item.tileId), gridRow: 0, gridColumn: 0 }], rack, [], false)
    expect(wrapper.text()).not.toContain('24점')
    expect(wrapper.text()).not.toContain('24/30')
  })

  it('STATUS-002 keeps the exact-thirty score in validation without duplicating it in the table', () => {
    const rack = [tile('RED', 7), tile('RED', 8), tile('RED', 9), tile('BLUE', 1), tile('BLUE', 2), tile('BLUE', 3)]
    const melds: CandidateSpec[] = [
      { clientMeldId: 'a', sourceMeldId: null, tileIds: rack.slice(0, 3).map((item) => item.tileId), gridRow: 0, gridColumn: 0 },
      { clientMeldId: 'b', sourceMeldId: null, tileIds: rack.slice(3).map((item) => item.tileId), gridRow: 0, gridColumn: 13 },
    ]
    const placements = candidateSpecsToPlacements(melds, rack)
    const validation = validateTurnDraft(deriveTableCandidates(placements), rack, false, [], placements)
    expect(validation.submissionScore).toBe(30)
    expect(statusWrapper(melds, rack, [], false).text()).not.toContain('30점')
  })

  it('STATUS-003 keeps completion score copy out of the working-table header', () => {
    const wrapper = statusWrapper([], [], [], true)
    expect(wrapper.text()).not.toContain('첫 등록 완료')
    expect(wrapper.text()).not.toContain('/30')
  })

  it('STATUS-004 keeps submitted score in validation but not in a meld card', () => {
    const rack = [tile('RED', 4)]
    const baseline = [tableMeld(firstId, 'RED', [1, 2, 3])]
    const melds = [{ clientMeldId: firstId, sourceMeldId: firstId, tileIds: [...baseline[0]!.tiles.map((item) => item.tileId), rack[0]!.tileId], gridRow: 0, gridColumn: 0 }]
    const placements = candidateSpecsToPlacements(melds, rack)
    const validation = validateTurnDraft(deriveTableCandidates(placements), rack, true, baseline, placements)
    expect(validation.submissionScore).toBe(4)
    expect(statusWrapper(melds, rack, baseline, true).text()).not.toContain('이번 제출 4점')
  })

  it('STATUS-005 displays the invalid meld count', () => {
    const rack = [tile('YELLOW', 7)]
    const melds = [{ clientMeldId: 'new', sourceMeldId: null, tileIds: [rack[0]!.tileId], gridRow: 0, gridColumn: 0 }]
    expect(statusWrapper(melds, rack, [], false).text()).toContain('유효하지 않은 조합 1개')
  })
})

describe('Phase 7 second review unified working table operations', () => {
  it('WORK-001 clones the committed table into an immutable baseline and editable working copy', () => {
    const { working, table } = fixture()
    expect(working.candidates.value.map((meld) => meld.clientMeldId)).toEqual([firstId, secondId])
    expect(working.workingTable.value?.baseline.committedMelds).not.toBe(table.value)
  })

  it('WORK-002 adds a rack tile directly to an existing meld after initial completion', () => {
    const { working, rack } = fixture()
    expect(working.addToMeld(firstId, [rack.value[0]!.tileId], rack.value.map((item) => item.tileId))).toBe(true)
    expect(working.candidates.value[0]!.tileIds).toContain('RED-04-A')
  })

  it('WORK-003 reorders tiles inside an existing meld', () => {
    const { working } = fixture()
    expect(working.reorderTile(firstId, 0, 2)).toBe(true)
    expect(working.candidates.value[0]!.tileIds[2]).toBe('RED-01-A')
  })

  it('WORK-004 moves a baseline table tile to another meld without losing it', () => {
    const { working } = fixture()
    expect(working.moveTile('RED-03-A', secondId)).toBe(true)
    expect(working.candidates.value[1]!.tileIds).toContain('RED-03-A')
  })

  it('WORK-005 splits one existing meld into two working melds', () => {
    const { working } = fixture()
    expect(working.splitMeld(firstId, 1)).toBe(true)
    expect(working.candidates.value).toHaveLength(3)
  })

  it('WORK-006 merges two melds into one candidate meld', () => {
    const { working } = fixture()
    expect(working.mergeMelds(firstId, secondId)).toBe(true)
    expect(working.candidates.value).toHaveLength(1)
    expect(working.candidates.value[0]!.tileIds).toHaveLength(6)
  })

  it('WORK-007 removes an empty source meld after a merge', () => {
    const { working } = fixture()
    working.mergeMelds(firstId, secondId)
    expect(working.candidates.value.some((meld) => meld.clientMeldId === firstId)).toBe(false)
  })

  it('WORK-008 permits an invalid intermediate table locally', () => {
    const { working } = fixture()
    working.splitMeld(firstId, 1)
    expect(working.validation.value.invalidCount).toBeGreaterThan(0)
  })

  it('WORK-009 disables commit while an intermediate meld is invalid', () => {
    const { working } = fixture()
    working.splitMeld(firstId, 1)
    expect(working.validation.value.canCommit).toBe(false)
  })

  it('WORK-010 re-enables a valid candidate after undo repairs the invalid operation', () => {
    const { working, rack } = fixture()
    expect(working.addToMeld(
      firstId,
      [rack.value[0]!.tileId],
      rack.value.map((item) => item.tileId),
    )).toBe(true)
    expect(working.candidates.value[0]!.clientMeldId).toBe(firstId)
    expect(working.splitMeld(firstId, 1)).toBe(true)
    expect(working.undo()).toBe(true)
    expect(working.validation.value.invalidCount).toBe(0)
    expect(working.validation.value.canCommit).toBe(true)
  })

  it('WORK-011 locks baseline table melds before initial completion', () => {
    const { working, rack } = fixture(false)
    expect(working.isMeldEditable(firstId)).toBe(false)
    expect(working.addToMeld(firstId, [rack.value[0]!.tileId], rack.value.map((item) => item.tileId))).toBe(false)
  })

  it('WORK-012 unlocks number-only baseline melds after initial completion', () => {
    const { working } = fixture(true)
    expect(working.isMeldEditable(firstId)).toBe(true)
  })

  it('WORK-013 preserves every baseline table tile exactly once through recomposition', () => {
    const { working } = fixture()
    working.moveTile('RED-03-A', secondId)
    const ids = working.candidates.value.flatMap((meld) => meld.tileIds)
    expect(ids.filter((id) => id === 'RED-03-A')).toHaveLength(1)
    expect(working.validation.value.baselinePreserved).toBe(true)
  })

  it('WORK-014 preserves the visible-rack plus contributed-rack partition', () => {
    const { working, rack } = fixture()
    working.addToMeld(firstId, [rack.value[0]!.tileId], rack.value.map((item) => item.tileId))
    expect(working.partitionPreserved.value).toBe(true)
  })
})

describe('Phase 7 second review working table recovery', () => {
  it('RECOVER-001 undoes rack to table placement', () => {
    const { working, rack } = fixture()
    working.addToMeld(firstId, [rack.value[0]!.tileId], rack.value.map((item) => item.tileId))
    working.undo()
    expect(working.draftTileIds.value).not.toContain('RED-04-A')
  })

  it('RECOVER-002 undoes a table to table move', () => {
    const { working } = fixture()
    working.moveTile('RED-03-A', secondId)
    working.undo()
    expect(working.candidates.value[0]!.tileIds).toContain('RED-03-A')
  })

  it('RECOVER-003 cancel restores the entire baseline table and source rack order', () => {
    const { working, rack } = fixture()
    const order = rack.value.map((item) => item.tileId).reverse()
    working.addToMeld(firstId, [rack.value[0]!.tileId], order)
    expect(working.cancel()).toEqual(order)
    expect(working.hasChanges.value).toBe(false)
  })

  it('RECOVER-004 discards local work when the turn times out', async () => {
    const { working, rack, isMyTurn } = fixture()
    working.addToMeld(firstId, [rack.value[0]!.tileId], [])
    isMyTurn.value = false
    await nextTick()
    expect(working.workingTable.value).toBeNull()
  })

  it('RECOVER-005 discards local work on a stale authoritative version', async () => {
    const { working, rack, version } = fixture()
    working.addToMeld(firstId, [rack.value[0]!.tileId], [])
    version.value += 1
    await nextTick()
    expect(working.hasChanges.value).toBe(false)
    expect(working.lastResolution.value).toBe('STALE')
  })

  it('RECOVER-006 discards local work on reconnect private synchronization', async () => {
    const { working, rack, syncRevision } = fixture()
    working.addToMeld(firstId, [rack.value[0]!.tileId], [])
    syncRevision.value += 1
    await nextTick()
    expect(working.hasChanges.value).toBe(false)
  })

  it('RECOVER-007 keeps local work after a validation rejection', () => {
    const { working, rack } = fixture()
    working.addToMeld(firstId, [rack.value[0]!.tileId], [])
    working.markCommitPending('action')
    working.rejectCommit(true)
    expect(working.hasChanges.value).toBe(true)
    expect(working.workingTable.value?.pendingCommitActionId).toBeNull()
  })

  it('RECOVER-008 leaves state unchanged after an invalid drop target', () => {
    const { working } = fixture()
    const before = JSON.parse(JSON.stringify(working.candidates.value))
    expect(working.moveTile('NOT-A-TILE', firstId)).toBe(false)
    expect(working.candidates.value).toEqual(before)
  })
})

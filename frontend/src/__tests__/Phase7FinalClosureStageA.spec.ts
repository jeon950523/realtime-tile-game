import { mount } from '@vue/test-utils'
import { effectScope, ref } from 'vue'
import { describe, expect, it } from 'vitest'

import CommittedTableBoard from '@/components/game/CommittedTableBoard.vue'
import DraftMeld from '@/components/game/DraftMeld.vue'
import { useRackPresentation } from '@/composables/game/useRackPresentation'
import { useWorkingTable } from '@/composables/game/useWorkingTable'
import { isRackToolbarDisabled } from '@/domain/game/rackInteractionPolicy'
import { buildRackVisualGroups } from '@/domain/game/rackVisualGroups'
import { validateDraftMeld, validateTurnDraft } from '@/domain/game/turnDraftValidation'
import type { GameRackTile, GameTableMeld } from '@/types/game'
import type { RackSyncSource } from '@/types/rackPresentation'

function numberTile(color: GameRackTile['color'], number: number, copy = 'A', positionOrder = 0): GameRackTile {
  return {
    tileId: `${color}-${String(number).padStart(2, '0')}-${copy}`,
    tileType: 'NUMBER', color, number, joker: false, positionOrder,
  }
}

function jokerTile(positionOrder = 0): GameRackTile {
  return {
    tileId: `JOKER-${positionOrder}`,
    tileType: 'JOKER', color: null, number: null, joker: true, positionOrder,
  }
}

function rackHarness(rack: GameRackTile[], excludedTileIds = ref<string[]>([])) {
  const authoritativeRack = ref(rack)
  const rackSyncRevision = ref(1)
  const rackSyncSource = ref<RackSyncSource>('SNAPSHOT')
  const drawMotionRevision = ref(0)
  const drawMotionTileIds = ref<string[]>([])
  const scope = effectScope()
  const presentation = scope.run(() => useRackPresentation({
    authoritativeRack,
    rackSyncRevision,
    rackSyncSource,
    drawMotionRevision,
    drawMotionTileIds,
    excludedTileIds,
    sortMotionDurationMs: 0,
    drawMotionDurationMs: 0,
  }))!
  return { scope, authoritativeRack, excludedTileIds, presentation }
}

const rack = [
  numberTile('RED', 7, 'A', 0),
  numberTile('RED', 8, 'A', 1),
  numberTile('RED', 9, 'A', 2),
  numberTile('BLUE', 7, 'A', 3),
  numberTile('YELLOW', 7, 'A', 4),
]

const policy = {
  gameStatus: 'IN_PROGRESS' as const,
  commandInProgress: false,
  reconnectRecoveryInProgress: false,
  privateStateLoaded: true,
  motionActive: false,
  enteringTileCount: 0,
}

describe('Phase 7 final closure Stage A rack workspace', () => {
  it('FE-P7A-001 permits 789 sorting without a current-turn dependency', () => {
    const harness = rackHarness(rack)
    expect(harness.presentation.applySort('RUN_789')).toBe(true)
    expect(harness.presentation.sortMode.value).toBe('RUN_789')
    harness.scope.stop()
  })

  it('FE-P7A-002 permits 777 sorting without a current-turn dependency', () => {
    const harness = rackHarness(rack)
    expect(harness.presentation.applySort('GROUP_777')).toBe(true)
    expect(harness.presentation.sortMode.value).toBe('GROUP_777')
    harness.scope.stop()
  })

  it('FE-P7A-003 sorts the remaining rack while the working table is dirty', () => {
    const authoritativeRack = ref([...rack])
    const version = ref(0)
    const syncRevision = ref(1)
    const table = ref<GameTableMeld[]>([])
    const completed = ref(false)
    const isMyTurn = ref(true)
    const rackSyncSource = ref<RackSyncSource>('SNAPSHOT')
    const drawMotionRevision = ref(0)
    const drawMotionTileIds = ref<string[]>([])
    const scope = effectScope()
    const result = scope.run(() => {
      const working = useWorkingTable({
        authoritativeRack, authoritativeVersion: version, authoritativeSyncRevision: syncRevision,
        tableMelds: table, initialMeldCompleted: completed, isMyTurn,
      })
      const presentation = useRackPresentation({
        authoritativeRack, rackSyncRevision: syncRevision, rackSyncSource,
        drawMotionRevision, drawMotionTileIds, excludedTileIds: working.draftTileIds,
        sortMotionDurationMs: 0,
      })
      return { working, presentation }
    })!
    expect(result.working.addAsNewMeld(rack.slice(0, 3).map((tile) => tile.tileId), result.presentation.displayOrderIds.value)).toBe(true)
    expect(result.working.hasChanges.value).toBe(true)
    expect(result.presentation.applySort('GROUP_777')).toBe(true)
    expect(result.presentation.displayOrderIds.value).toEqual(rack.slice(3).map((tile) => tile.tileId))
    scope.stop()
  })

  it('FE-P7A-004 moves a tile into an empty logical slot', () => {
    const harness = rackHarness(rack.slice(0, 3))
    const tileId = rack[0]!.tileId
    expect(harness.presentation.beginDrag([tileId])).toBe(true)
    harness.presentation.previewDrag(7)
    harness.presentation.finishDrag(true)
    expect(harness.presentation.rackSlots.value[0]!.tileId).toBeNull()
    expect(harness.presentation.rackSlots.value[7]!.tileId).toBe(tileId)
    harness.scope.stop()
  })

  it('FE-P7A-005 preserves empty slots after a manual drop', () => {
    const harness = rackHarness(rack.slice(0, 3))
    harness.presentation.beginDrag([rack[1]!.tileId])
    harness.presentation.previewDrag(9)
    harness.presentation.finishDrag(true)
    expect(harness.presentation.rackSlots.value.filter((slot) => slot.tileId === null)).toHaveLength(17)
    expect(harness.presentation.sortMode.value).toBe('MANUAL')
    harness.scope.stop()
  })

  it('FE-P7A-006 consistently swaps two occupied slots', () => {
    const harness = rackHarness(rack.slice(0, 3))
    harness.presentation.beginDrag([rack[0]!.tileId])
    harness.presentation.previewDrag(2)
    harness.presentation.finishDrag(true)
    expect(harness.presentation.rackSlots.value.slice(0, 3).map((slot) => slot.tileId)).toEqual([
      rack[2]!.tileId, rack[1]!.tileId, rack[0]!.tileId,
    ])
    harness.scope.stop()
  })

  it('FE-P7A-007 preserves visual group selection by tile ID after sorting', () => {
    const harness = rackHarness(rack)
    harness.presentation.applySort('GROUP_777')
    const group = buildRackVisualGroups(
      harness.presentation.displayedTiles.value,
      harness.presentation.sortMode.value,
      harness.presentation.serverOrderSnapshot.value,
    ).find((candidate) => candidate.kind === 'GROUP')
    expect(group?.tileIds).toEqual([rack[0]!.tileId, rack[3]!.tileId, rack[4]!.tileId])
    expect(harness.presentation.beginDrag(group!.tileIds.slice(0, 1))).toBe(true)
    expect(harness.presentation.upgradeDrag(group!.tileIds)).toBe(true)
    expect(harness.presentation.dragState.value?.activeDragTileIds).toEqual(group!.tileIds)
    harness.scope.stop()
  })

  it('FE-P7A-008 leaves the submitted tile slot empty', () => {
    const excluded = ref<string[]>([])
    const harness = rackHarness(rack.slice(0, 3), excluded)
    excluded.value = [rack[1]!.tileId]
    expect(harness.presentation.rackSlots.value[1]!.tileId).toBeNull()
    expect(harness.presentation.invariantPreserved.value).toBe(true)
    harness.scope.stop()
  })

  it('FE-P7A-009 restores an undone tile to its original slot first', () => {
    const excluded = ref<string[]>([])
    const harness = rackHarness(rack.slice(0, 3), excluded)
    excluded.value = [rack[1]!.tileId]
    excluded.value = []
    expect(harness.presentation.rackSlots.value[1]!.tileId).toBe(rack[1]!.tileId)
    harness.scope.stop()
  })

  it('FE-P7A-010 keeps the current manual layout and restores a cancelled tile near its parked slot', () => {
    const excluded = ref<string[]>([])
    const harness = rackHarness(rack.slice(0, 3), excluded)
    harness.presentation.beginDrag([rack[0]!.tileId])
    harness.presentation.previewDrag(8)
    harness.presentation.finishDrag(true)
    excluded.value = [rack[0]!.tileId]
    excluded.value = []
    expect(harness.presentation.rackSlots.value[8]!.tileId).toBe(rack[0]!.tileId)
    expect(harness.presentation.rackSlots.value[1]!.tileId).toBe(rack[1]!.tileId)
    expect(harness.presentation.sortMode.value).toBe('MANUAL')
    harness.scope.stop()
  })

  it('FE-P7A-011 blocks sorting while a game command is processing', () => {
    expect(isRackToolbarDisabled({ ...policy, commandInProgress: true }, false)).toBe(true)
  })

  it('FE-P7A-012 blocks sorting during reconnect authoritative recovery', () => {
    expect(isRackToolbarDisabled({ ...policy, reconnectRecoveryInProgress: true }, false)).toBe(true)
  })

  it('FE-P7A-013 removes point labels from committed and draft meld cards', () => {
    const runTiles = rack.slice(0, 3)
    const meld = { clientMeldId: 'draft', sourceMeldId: null, tileIds: runTiles.map((tile) => tile.tileId) }
    const validation = validateDraftMeld(meld, new Map(runTiles.map((tile) => [tile.tileId, tile])))
    const draft = mount(DraftMeld, {
      props: { meld, validation, rackById: new Map(runTiles.map((tile) => [tile.tileId, tile])) },
    })
    const committed = mount(CommittedTableBoard, { props: { melds: [{
      meldId: 'committed', meldType: 'RUN', score: 24, positionOrder: 0,
      gridRow: 0, gridColumn: 0,
      tiles: runTiles.map((tile) => ({ ...tile })),
    }] } })
    expect(draft.find('header').text()).not.toContain('점')
    expect(committed.findAll('.committed-table-tile')).toHaveLength(3)
    expect(committed.text()).not.toContain('점')
    expect(committed.text()).not.toContain('RUN')
  })

  it('FE-P7A-014 keeps RUN and GROUP kind labels on meld cards', () => {
    const runTiles = rack.slice(0, 3)
    const groupTiles = [rack[0]!, rack[3]!, rack[4]!]
    const runMeld = { clientMeldId: 'run', sourceMeldId: null, tileIds: runTiles.map((tile) => tile.tileId) }
    const groupMeld = { clientMeldId: 'group', sourceMeldId: null, tileIds: groupTiles.map((tile) => tile.tileId) }
    expect(validateDraftMeld(runMeld, new Map(runTiles.map((tile) => [tile.tileId, tile]))).kind).toBe('RUN')
    expect(validateDraftMeld(groupMeld, new Map(groupTiles.map((tile) => [tile.tileId, tile]))).kind).toBe('GROUP')
  })

  it('FE-P7A-015 calculates the exact first-registration score from contributed rack tiles including Joker resolution', () => {
    const submitted = [
      numberTile('RED', 7, 'B'), jokerTile(1), numberTile('RED', 9, 'B'),
      numberTile('BLUE', 1, 'B'), numberTile('BLUE', 2, 'B'), numberTile('BLUE', 3, 'B'),
    ]
    const melds = [
      { clientMeldId: '789', sourceMeldId: null, tileIds: submitted.slice(0, 3).map((tile) => tile.tileId) },
      { clientMeldId: '123', sourceMeldId: null, tileIds: submitted.slice(3).map((tile) => tile.tileId) },
    ]
    const validation = validateTurnDraft(melds, submitted, false)
    expect(validation.melds['789']?.resolvedTileScores[submitted[1]!.tileId]).toBe(8)
    expect(validation.submissionScore).toBe(30)
    expect(validation.canCommit).toBe(true)
  })
})

import { effectScope, nextTick, ref, type Ref } from 'vue'
import { describe, expect, it } from 'vitest'

import { useRackPresentation } from '@/composables/game/useRackPresentation'
import { useWorkingTable } from '@/composables/game/useWorkingTable'
import { computeAdaptiveRackLayout } from '@/domain/game/rackLayout'
import { visibleRackSlots } from '@/domain/game/rackSlots'
import type { GameRackTile, GameTableMeld } from '@/types/game'
import type { RackSyncSource } from '@/types/rackPresentation'

const COLORS = ['RED', 'BLUE', 'YELLOW', 'BLACK'] as const

function rackOf(size: number): GameRackTile[] {
  return Array.from({ length: size }, (_, positionOrder) => ({
    tileId: `RACK-${String(positionOrder).padStart(2, '0')}`,
    tileType: 'NUMBER' as const,
    color: COLORS[positionOrder % COLORS.length]!,
    number: positionOrder % 13 + 1,
    joker: false,
    positionOrder,
  }))
}

function createPresentation(
  initialRack: GameRackTile[],
  excludedTileIds: Ref<string[]> = ref([]),
) {
  const authoritativeRack = ref(structuredClone(initialRack))
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
  return {
    authoritativeRack,
    rackSyncRevision,
    rackSyncSource,
    excludedTileIds,
    presentation,
    scope,
  }
}

function createWorkingTableHarness(initialRack: GameRackTile[]) {
  const authoritativeRack = ref(structuredClone(initialRack))
  const version = ref(0)
  const syncRevision = ref(1)
  const tableMelds = ref<GameTableMeld[]>([])
  const initialMeldCompleted = ref(false)
  const isMyTurn = ref(true)
  const rackSyncSource = ref<RackSyncSource>('SNAPSHOT')
  const drawMotionRevision = ref(0)
  const drawMotionTileIds = ref<string[]>([])
  const scope = effectScope()
  const result = scope.run(() => {
    const working = useWorkingTable({
      authoritativeRack,
      authoritativeVersion: version,
      authoritativeSyncRevision: syncRevision,
      tableMelds,
      initialMeldCompleted,
      isMyTurn,
    })
    const presentation = useRackPresentation({
      authoritativeRack,
      rackSyncRevision: syncRevision,
      rackSyncSource,
      drawMotionRevision,
      drawMotionTileIds,
      excludedTileIds: working.draftTileIds,
      sortMotionDurationMs: 0,
      drawMotionDurationMs: 0,
    })
    return { working, presentation }
  })!
  return { ...result, scope }
}

describe('rack slot capacity compaction', () => {
  it('keeps 30 tiles in a 15-column, two-row rack', () => {
    const harness = createPresentation(rackOf(30))

    expect(harness.presentation.displayedSlots.value).toHaveLength(30)
    expect(computeAdaptiveRackLayout(harness.presentation.displayedSlots.value.length, 870))
      .toMatchObject({ columns: 15, rows: 2 })
    harness.scope.stop()
  })

  it('removes only a trailing column when its upper and lower slots are empty', () => {
    const rack = rackOf(30)
    const harness = createPresentation(rack)
    harness.excludedTileIds.value = [rack[14]!.tileId, rack[29]!.tileId]

    expect(harness.presentation.rackSlots.value).toHaveLength(30)
    expect(harness.presentation.displayedSlots.value).toHaveLength(28)
    expect(computeAdaptiveRackLayout(harness.presentation.displayedSlots.value.length, 870).columns).toBe(14)
    harness.scope.stop()
  })

  it('preserves an interior empty slot, its index, and the existing capacity', () => {
    const rack = rackOf(30)
    const harness = createPresentation(rack)
    harness.excludedTileIds.value = [rack[7]!.tileId]

    expect(harness.presentation.displayedSlots.value).toHaveLength(30)
    expect(harness.presentation.displayedSlots.value[7]).toMatchObject({ slotIndex: 7, tileId: null })
    expect(visibleRackSlots(harness.presentation.rackSlots.value)[7]?.slotIndex).toBe(7)
    harness.scope.stop()
  })

  it('grows by one column when Draw exceeds the current full capacity', async () => {
    const rack = rackOf(28)
    const harness = createPresentation(rack)
    harness.authoritativeRack.value = [...rack, rackOf(29)[28]!]
    harness.rackSyncSource.value = 'PRIVATE_EVENT'
    harness.rackSyncRevision.value += 1
    await nextTick()

    expect(harness.presentation.displayedSlots.value).toHaveLength(30)
    expect(computeAdaptiveRackLayout(harness.presentation.displayedSlots.value.length, 870).columns).toBe(15)
    expect(harness.presentation.invariantPreserved.value).toBe(true)
    harness.scope.stop()
  })

  it('shrinks a trailing empty column immediately after tiles move to the Table', () => {
    const rack = rackOf(30)
    const harness = createPresentation(rack)
    harness.excludedTileIds.value = [rack[14]!.tileId, rack[29]!.tileId]

    expect(harness.presentation.displayedSlots.value.map((slot) => slot.slotIndex)).not.toContain(14)
    expect(harness.presentation.displayedSlots.value.map((slot) => slot.slotIndex)).not.toContain(29)
    harness.scope.stop()
  })

  it('restores the exact baseline slot structure after Working Table Undo', () => {
    const rack = rackOf(30)
    const harness = createWorkingTableHarness(rack)
    const baseline = harness.presentation.rackSlots.value.map((slot) => ({ ...slot }))

    expect(harness.working.addAsNewMeld(
      [rack[14]!.tileId, rack[29]!.tileId],
      harness.presentation.displayOrderIds.value,
      0,
      0,
    )).toBe(true)
    expect(harness.presentation.displayedSlots.value).toHaveLength(28)
    expect(harness.working.undo()).toBe(true)
    expect(harness.presentation.rackSlots.value).toEqual(baseline)
    expect(harness.presentation.displayedSlots.value).toHaveLength(30)
    harness.scope.stop()
  })

  it('restores the exact baseline slot structure after Working Table Cancel', () => {
    const rack = rackOf(30)
    const harness = createWorkingTableHarness(rack)
    const baseline = harness.presentation.rackSlots.value.map((slot) => ({ ...slot }))

    expect(harness.working.addAsNewMeld(
      [rack[14]!.tileId, rack[29]!.tileId],
      harness.presentation.displayOrderIds.value,
      0,
      0,
    )).toBe(true)
    expect(harness.working.cancel()).not.toBeNull()
    expect(harness.presentation.rackSlots.value).toEqual(baseline)
    expect(harness.presentation.displayedSlots.value).toHaveLength(30)
    harness.scope.stop()
  })

  it('recalculates persistent capacity from the authoritative Rack after Commit sync', async () => {
    const rack = rackOf(30)
    const submittedIds = [rack[14]!.tileId, rack[29]!.tileId]
    const harness = createPresentation(rack)
    harness.excludedTileIds.value = submittedIds
    harness.authoritativeRack.value = rack.filter((tile) => !submittedIds.includes(tile.tileId))
    harness.rackSyncSource.value = 'PRIVATE_EVENT'
    harness.rackSyncRevision.value += 1
    await nextTick()

    expect(harness.presentation.rackSlots.value).toHaveLength(28)
    expect(harness.presentation.rackSlots.value.map((slot) => slot.slotIndex))
      .toEqual(Array.from({ length: 28 }, (_, index) => index))
    expect(harness.presentation.invariantPreserved.value).toBe(true)
    harness.scope.stop()
  })

  it('keeps 789, 777, server order, and physical drag selection available', () => {
    const rack = rackOf(30)
    const harness = createPresentation(rack)

    expect(harness.presentation.applySort('RUN_789')).toBe(true)
    expect(harness.presentation.applySort('GROUP_777')).toBe(true)
    expect(harness.presentation.restoreServerOrder()).toBe(true)
    const firstId = harness.presentation.displayOrderIds.value[0]!
    expect(harness.presentation.beginDrag([firstId])).toBe(true)
    expect(harness.presentation.upgradeDrag(harness.presentation.displayOrderIds.value.slice(0, 3))).toBe(true)
    expect(harness.presentation.invariantPreserved.value).toBe(true)
    harness.scope.stop()
  })
})

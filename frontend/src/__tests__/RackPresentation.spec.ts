import { effectScope, nextTick, ref } from 'vue'
import { afterEach, describe, expect, it, vi } from 'vitest'

import { useRackPresentation } from '@/composables/game/useRackPresentation'
import type { GameRackTile } from '@/types/game'
import type { RackSyncSource } from '@/types/rackPresentation'

function tile(tileId: string, color: GameRackTile['color'], number: number): GameRackTile {
  return { tileId, tileType: 'NUMBER', color, number, joker: false, positionOrder: 0 }
}

function createHarness(initialRack: GameRackTile[], sortMotionDurationMs = 0) {
  const authoritativeRack = ref<GameRackTile[]>(structuredClone(initialRack))
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
    sortMotionDurationMs,
    drawMotionDurationMs: 340,
  }))!
  return {
    authoritativeRack,
    rackSyncRevision,
    rackSyncSource,
    drawMotionRevision,
    drawMotionTileIds,
    presentation,
    scope,
  }
}

const initialRack = [
  tile('BLUE-07-A', 'BLUE', 7),
  tile('RED-02-A', 'RED', 2),
  tile('RED-01-A', 'RED', 1),
]

afterEach(() => {
  vi.useRealTimers()
})

describe('rack presentation', () => {
  it('SORT-005, SORT-009, and SORT-010 sort locally without mutating the rack or game version', () => {
    const harness = createHarness(initialRack)
    const authoritativeBefore = harness.authoritativeRack.value.map((item) => ({ ...item }))
    const publicState = { gameVersion: 7 }

    expect(harness.presentation.applySort('GROUP_777')).toBe(true)

    expect(harness.presentation.displayOrderIds.value).toEqual(['RED-01-A', 'RED-02-A', 'BLUE-07-A'])
    expect(harness.authoritativeRack.value).toEqual(authoritativeBefore)
    expect(publicState.gameVersion).toBe(7)
    expect(harness.presentation.invariantPreserved.value).toBe(true)
    harness.scope.stop()
  })

  it('SORT-006 allows local sorting without regard to the current player', () => {
    const harness = createHarness(initialRack)
    const isMyTurn = false

    expect(isMyTurn).toBe(false)
    expect(harness.presentation.applySort('RUN_789')).toBe(true)
    expect(harness.presentation.sortMode.value).toBe('RUN_789')
    harness.scope.stop()
  })

  it('SORT-007 and SORT-015 use the latest snapshot as the original order', async () => {
    const harness = createHarness(initialRack)
    harness.presentation.applySort('GROUP_777')
    harness.authoritativeRack.value = [initialRack[1]!, initialRack[0]!, initialRack[2]!]
    harness.rackSyncSource.value = 'SNAPSHOT'
    harness.rackSyncRevision.value += 1
    await nextTick()

    expect(harness.presentation.sortMode.value).toBe('SERVER')
    expect(harness.presentation.displayOrderIds.value).toEqual(['RED-02-A', 'BLUE-07-A', 'RED-01-A'])
    expect(harness.presentation.restoreServerOrder()).toBe(true)
    expect(harness.presentation.displayOrderIds.value).toEqual(['RED-02-A', 'BLUE-07-A', 'RED-01-A'])
    harness.scope.stop()
  })

  it('SORT-011 does not reset display order when no private rack revision arrives', async () => {
    const harness = createHarness(initialRack)
    harness.presentation.applySort('GROUP_777')
    const sorted = [...harness.presentation.displayOrderIds.value]

    harness.authoritativeRack.value = harness.authoritativeRack.value.map((item) => ({ ...item }))
    await nextTick()

    expect(harness.presentation.displayOrderIds.value).toEqual(sorted)
    expect(harness.presentation.sortMode.value).toBe('GROUP_777')
    harness.scope.stop()
  })

  it('SORT-012 appends a received tile while preserving manual order', async () => {
    const harness = createHarness(initialRack)
    harness.presentation.beginDrag(['BLUE-07-A'])
    harness.presentation.previewDrag(2)
    harness.presentation.finishDrag(true)
    expect(harness.presentation.sortMode.value).toBe('MANUAL')

    harness.authoritativeRack.value = [...harness.authoritativeRack.value, tile('YELLOW-03-A', 'YELLOW', 3)]
    harness.rackSyncSource.value = 'PRIVATE_EVENT'
    harness.rackSyncRevision.value += 1
    await nextTick()

    expect(harness.presentation.displayOrderIds.value).toEqual([
      'RED-01-A',
      'RED-02-A',
      'BLUE-07-A',
      'YELLOW-03-A',
    ])
    expect(harness.presentation.invariantPreserved.value).toBe(true)
    harness.scope.stop()
  })

  it('SORT-013 and SORT-014 reapply the active auto-sort after Draw', async () => {
    for (const mode of ['GROUP_777', 'RUN_789'] as const) {
      const harness = createHarness(initialRack)
      harness.presentation.applySort(mode)
      harness.authoritativeRack.value = [
        ...harness.authoritativeRack.value,
        tile('BLUE-01-A', 'BLUE', 1),
      ]
      harness.rackSyncSource.value = 'PRIVATE_EVENT'
      harness.rackSyncRevision.value += 1
      await nextTick()

      const expected = mode === 'GROUP_777'
        ? ['RED-01-A', 'BLUE-01-A', 'RED-02-A', 'BLUE-07-A']
        : ['RED-01-A', 'RED-02-A', 'BLUE-01-A', 'BLUE-07-A']
      expect(harness.presentation.displayOrderIds.value).toEqual(expected)
      harness.scope.stop()
    }
  })

  it('DRAG-004 restores the prior order for an invalid outside drop', () => {
    const harness = createHarness(initialRack)
    const original = [...harness.presentation.displayOrderIds.value]
    harness.presentation.beginDrag(['BLUE-07-A'])
    harness.presentation.previewDrag(2)
    harness.presentation.finishDrag(false)

    expect(harness.presentation.displayOrderIds.value).toEqual(original)
    expect(harness.presentation.sortMode.value).toBe('SERVER')
    harness.scope.stop()
  })

  it('DRAG-005 sets MANUAL mode after a valid drop, including the same slot', () => {
    const harness = createHarness(initialRack)
    harness.presentation.beginDrag(['RED-02-A'])
    harness.presentation.previewDrag(1)
    harness.presentation.finishDrag(true)

    expect(harness.presentation.sortMode.value).toBe('MANUAL')
    expect(harness.presentation.displayOrderIds.value).toEqual(initialRack.map((item) => item.tileId))
    harness.scope.stop()
  })

  it('MOTION-POLISH-001 keeps the same array reference for repeated same-index previews', () => {
    const harness = createHarness(initialRack)
    const originalReference = harness.presentation.displayOrderIds.value
    harness.presentation.beginDrag(['RED-02-A'])

    harness.presentation.previewDrag(1)
    harness.presentation.previewDrag(1)

    expect(harness.presentation.displayOrderIds.value).toBe(originalReference)
    expect(harness.presentation.displayOrderIds.value).toEqual(initialRack.map((item) => item.tileId))
    harness.scope.stop()
  })

  it('MOTION-POLISH-002 assigns one new order only when the target index changes', () => {
    const harness = createHarness(initialRack)
    harness.presentation.beginDrag(['BLUE-07-A'])

    harness.presentation.previewDrag(2)
    const changedReference = harness.presentation.displayOrderIds.value
    harness.presentation.previewDrag(2)

    expect(harness.presentation.displayOrderIds.value).toBe(changedReference)
    expect(harness.presentation.displayOrderIds.value).toEqual(['RED-01-A', 'RED-02-A', 'BLUE-07-A'])
    harness.scope.stop()
  })

  it('MOTION-POLISH-010 keeps the authoritative rack and game version unchanged', () => {
    const harness = createHarness(initialRack)
    const authoritativeBefore = harness.authoritativeRack.value.map((item) => ({ ...item }))
    const publicState = { gameVersion: 12 }

    harness.presentation.applySort('RUN_789')
    harness.presentation.beginDrag(['RED-02-A'])
    harness.presentation.previewDrag(2)
    harness.presentation.finishDrag(true)

    expect(harness.authoritativeRack.value).toEqual(authoritativeBefore)
    expect(publicState.gameVersion).toBe(12)
    expect(harness.presentation.invariantPreserved.value).toBe(true)
    harness.scope.stop()
  })

  it('MOTION-001 locks a second sort while the move transition is active', () => {
    vi.useFakeTimers()
    const harness = createHarness(initialRack, 270)

    expect(harness.presentation.applySort('GROUP_777')).toBe(true)
    expect(harness.presentation.motionActive.value).toBe(true)
    expect(harness.presentation.applySort('RUN_789')).toBe(false)
    vi.advanceTimersByTime(270)
    expect(harness.presentation.motionActive.value).toBe(false)
    harness.scope.stop()
  })

  it('MOTION-002 reaches the same final order with zero-duration motion', () => {
    const harness = createHarness(initialRack, 0)
    harness.presentation.applySort('GROUP_777')

    expect(harness.presentation.motionActive.value).toBe(false)
    expect(harness.presentation.displayOrderIds.value).toEqual(['RED-01-A', 'RED-02-A', 'BLUE-07-A'])
    harness.scope.stop()
  })

  it('DRAW-MOTION-001~003 animates only a new confirmed revision once', async () => {
    vi.useFakeTimers()
    const harness = createHarness(initialRack)

    harness.authoritativeRack.value = [...harness.authoritativeRack.value, tile('BLACK-09-A', 'BLACK', 9)]
    harness.rackSyncSource.value = 'PRIVATE_EVENT'
    harness.rackSyncRevision.value += 1
    await nextTick()
    expect(harness.presentation.enteringTileIds.value).toEqual([])

    harness.drawMotionTileIds.value = ['BLACK-09-A']
    harness.drawMotionRevision.value += 1
    await nextTick()
    expect(harness.presentation.enteringTileIds.value).toEqual(['BLACK-09-A'])

    harness.drawMotionRevision.value = 1
    await nextTick()
    expect(harness.presentation.enteringTileIds.value).toEqual(['BLACK-09-A'])
    vi.advanceTimersByTime(340)
    expect(harness.presentation.enteringTileIds.value).toEqual([])
    harness.scope.stop()
  })
})

import {
  computed,
  onScopeDispose,
  readonly,
  ref,
  watch,
  type Ref,
} from 'vue'

import {
  buildCompactSortedRackSlots,
  compactRackSlots,
  compactRackSlotCapacity,
  hasRackSlotInvariant,
  moveRackTileToSlot,
  rackSlotCount,
  rackSlotTileIds,
  removeRackTileFromSlots,
  restoreRackTileToSlots,
  visibleRackSlots,
} from '@/domain/game/rackSlots'
import { sortRackIds } from '@/domain/game/rackSorting'
import type { GameRackTile } from '@/types/game'
import type { RackDragState, RackSlot, RackSortMode, RackSyncSource } from '@/types/rackPresentation'

interface UseRackPresentationOptions {
  authoritativeRack: Readonly<Ref<readonly GameRackTile[]>>
  rackSyncRevision: Readonly<Ref<number>>
  rackSyncSource: Readonly<Ref<RackSyncSource>>
  drawMotionRevision: Readonly<Ref<number>>
  drawMotionTileIds: Readonly<Ref<readonly string[]>>
  excludedTileIds?: Readonly<Ref<readonly string[]>>
  sortMotionDurationMs?: number
  drawMotionDurationMs?: number
}

export function useRackPresentation(options: UseRackPresentationOptions) {
  const initialOrderIds = options.authoritativeRack.value.map((tile) => tile.tileId)
  const initialExcluded = new Set(options.excludedTileIds?.value ?? [])
  const initialVisibleIds = initialOrderIds.filter((tileId) => !initialExcluded.has(tileId))
  const serverOrderSnapshot = ref<string[]>(initialOrderIds)
  const rackSlots = ref<RackSlot[]>(compactRackSlots(
    initialVisibleIds,
    rackSlotCount(initialOrderIds.length),
  ))
  const sortMode = ref<RackSortMode>('SERVER')
  const dragState = ref<RackDragState | null>(null)
  const motionActive = ref(false)
  const enteringTileIds = ref<string[]>([])
  const parkedSlotByTileId = new Map<string, number>()
  const sortMotionDurationMs = options.sortMotionDurationMs ?? 220
  const drawMotionDurationMs = options.drawMotionDurationMs ?? 520
  let motionTimer: number | null = null
  let drawMotionTimer: number | null = null
  let lastDrawMotionRevision = 0

  const displayOrderIds = computed(() => rackSlotTileIds(rackSlots.value))

  const displayedTiles = computed(() => {
    const byId = new Map(options.authoritativeRack.value.map((tile) => [tile.tileId, tile]))
    return displayOrderIds.value
      .map((tileId) => byId.get(tileId))
      .filter((tile): tile is GameRackTile => tile !== undefined)
  })

  const displayedSlots = computed(() => {
    const byId = new Map(options.authoritativeRack.value.map((tile) => [tile.tileId, tile]))
    return visibleRackSlots(rackSlots.value).map((slot) => ({
      ...slot,
      tile: slot.tileId === null ? null : byId.get(slot.tileId) ?? null,
    }))
  })

  const invariantPreserved = computed(() => hasRackSlotInvariant(
    options.authoritativeRack.value,
    new Set(options.excludedTileIds?.value ?? []),
    rackSlots.value,
  ))

  function clearMotionTimer(): void {
    if (motionTimer !== null) window.clearTimeout(motionTimer)
    motionTimer = null
  }

  function startMotionLock(): void {
    clearMotionTimer()
    if (sortMotionDurationMs <= 0) {
      motionActive.value = false
      return
    }
    motionActive.value = true
    motionTimer = window.setTimeout(() => {
      motionActive.value = false
      motionTimer = null
    }, sortMotionDurationMs)
  }

  function remainingRack(): GameRackTile[] {
    const excluded = new Set(options.excludedTileIds?.value ?? [])
    return options.authoritativeRack.value.filter((tile) => !excluded.has(tile.tileId))
  }

  function compactForMode(mode: RackSortMode): void {
    const rack = remainingRack()
    let orderedIds: string[]
    if (mode === 'GROUP_777' || mode === 'RUN_789') {
      orderedIds = sortRackIds(rack, serverOrderSnapshot.value, mode)
    } else if (mode === 'SERVER') {
      const remainingIds = new Set(rack.map((tile) => tile.tileId))
      orderedIds = serverOrderSnapshot.value.filter((tileId) => remainingIds.has(tileId))
    } else {
      orderedIds = displayOrderIds.value
    }
    rackSlots.value = buildCompactSortedRackSlots(orderedIds)
  }

  function applySort(mode: Extract<RackSortMode, 'GROUP_777' | 'RUN_789'>): boolean {
    if (motionActive.value || dragState.value) return false
    compactForMode(mode)
    sortMode.value = mode
    startMotionLock()
    return true
  }

  function restoreServerOrder(): boolean {
    if (motionActive.value || dragState.value) return false
    compactForMode('SERVER')
    sortMode.value = 'SERVER'
    startMotionLock()
    return true
  }

  function beginDrag(activeDragTileIds: readonly string[]): boolean {
    const uniqueIds = [...new Set(activeDragTileIds)]
    const anchorTileId = uniqueIds[0]
    const currentTargetIndex = anchorTileId
      ? rackSlots.value.findIndex((slot) => slot.tileId === anchorTileId)
      : -1
    if (
      motionActive.value
      || dragState.value
      || uniqueIds.length === 0
      || uniqueIds.some((tileId) => !displayOrderIds.value.includes(tileId))
      || currentTargetIndex < 0
    ) return false
    dragState.value = {
      activeDragTileIds: uniqueIds,
      originalOrderIds: [...displayOrderIds.value],
      originalSlots: rackSlots.value.map((slot) => ({ ...slot })),
      currentTargetIndex,
    }
    return true
  }

  function previewDrag(targetIndex: number): void {
    const current = dragState.value
    const anchorTileId = current?.activeDragTileIds[0]
    if (!current || current.activeDragTileIds.length !== 1 || !anchorTileId
      || current.currentTargetIndex === targetIndex) return
    const nextSlots = moveRackTileToSlot(rackSlots.value, anchorTileId, targetIndex)
    const nextIndex = nextSlots.findIndex((slot) => slot.tileId === anchorTileId)
    if (nextIndex < 0 || nextIndex === current.currentTargetIndex) return
    rackSlots.value = nextSlots
    current.currentTargetIndex = nextIndex
  }

  function upgradeDrag(activeDragTileIds: readonly string[]): boolean {
    const current = dragState.value
    const uniqueIds = [...new Set(activeDragTileIds)]
    if (!current || uniqueIds.length < 2
      || uniqueIds.some((tileId) => !displayOrderIds.value.includes(tileId))) return false
    current.activeDragTileIds = uniqueIds
    return true
  }

  function replaceDisplayOrder(orderIds: readonly string[]): boolean {
    if (dragState.value || motionActive.value) return false
    const expected = displayOrderIds.value
    if (orderIds.length !== expected.length || new Set(orderIds).size !== orderIds.length
      || orderIds.some((tileId) => !expected.includes(tileId))) return false
    rackSlots.value = compactRackSlots(orderIds, rackSlots.value.length)
    sortMode.value = 'MANUAL'
    return true
  }

  function finishDrag(validDrop: boolean): void {
    const current = dragState.value
    if (!current) return
    if (!validDrop) {
      rackSlots.value = current.originalSlots.map((slot) => ({ ...slot }))
      dragState.value = null
      return
    }
    sortMode.value = 'MANUAL'
    dragState.value = null
    startMotionLock()
  }

  function reconcileExcludedTiles(): void {
    const excluded = new Set(options.excludedTileIds?.value ?? [])
    const authoritativeIds = options.authoritativeRack.value.map((tile) => tile.tileId)
    const authoritativeSet = new Set(authoritativeIds)
    const excludedFromSlots = rackSlots.value
      .filter((slot) => slot.tileId !== null && excluded.has(slot.tileId))
      .map((slot) => ({ tileId: slot.tileId!, slotIndex: slot.slotIndex }))

    excludedFromSlots.forEach(({ tileId, slotIndex }) => {
      parkedSlotByTileId.set(tileId, slotIndex)
      rackSlots.value = removeRackTileFromSlots(rackSlots.value, tileId)
    })

    const visibleExpected = authoritativeIds.filter((tileId) => !excluded.has(tileId))
    let restoredAny = false
    visibleExpected.forEach((tileId) => {
      if (rackSlots.value.some((slot) => slot.tileId === tileId)) return
      const preferred = parkedSlotByTileId.get(tileId) ?? authoritativeIds.indexOf(tileId)
      rackSlots.value = restoreRackTileToSlots(rackSlots.value, tileId, preferred)
      parkedSlotByTileId.delete(tileId)
      restoredAny = true
    })

    for (const tileId of [...parkedSlotByTileId.keys()]) {
      if (!authoritativeSet.has(tileId)) parkedSlotByTileId.delete(tileId)
    }

    if (restoredAny && (sortMode.value === 'GROUP_777' || sortMode.value === 'RUN_789')) {
      compactForMode(sortMode.value)
    }
    if (dragState.value) dragState.value = null
  }

  function syncRack(source: RackSyncSource): void {
    const rack = options.authoritativeRack.value
    const receivedIds = rack.map((tile) => tile.tileId)
    serverOrderSnapshot.value = [...receivedIds]

    if (source === 'SNAPSHOT' || rackSlots.value.length === 0) {
      parkedSlotByTileId.clear()
      sortMode.value = 'SERVER'
      rackSlots.value = compactRackSlots(
        receivedIds.filter((tileId) => !(options.excludedTileIds?.value ?? []).includes(tileId)),
        rackSlotCount(receivedIds.length),
      )
      dragState.value = null
      return
    }

    const authoritativeSet = new Set(receivedIds)
    rackSlots.value = rackSlots.value.map((slot) => (
      slot.tileId && !authoritativeSet.has(slot.tileId) ? { ...slot, tileId: null } : { ...slot }
    ))
    reconcileExcludedTiles()
    rackSlots.value = compactRackSlotCapacity(rackSlots.value)
  }

  watch(options.rackSyncRevision, () => {
    syncRack(options.rackSyncSource.value)
  })

  if (options.excludedTileIds) {
    watch(options.excludedTileIds, reconcileExcludedTiles, { flush: 'sync' })
  }

  watch(options.drawMotionRevision, (revision) => {
    if (revision <= lastDrawMotionRevision) return
    lastDrawMotionRevision = revision
    const visibleIds = new Set(displayOrderIds.value)
    const entering = options.drawMotionTileIds.value.filter((tileId) => visibleIds.has(tileId))
    if (entering.length === 0) return
    enteringTileIds.value = [...new Set(entering)]
    if (drawMotionTimer !== null) window.clearTimeout(drawMotionTimer)
    drawMotionTimer = window.setTimeout(() => {
      enteringTileIds.value = []
      drawMotionTimer = null
    }, drawMotionDurationMs)
  })

  onScopeDispose(() => {
    clearMotionTimer()
    if (drawMotionTimer !== null) window.clearTimeout(drawMotionTimer)
  })

  return {
    serverOrderSnapshot: readonly(serverOrderSnapshot),
    rackSlots: readonly(rackSlots),
    displayedSlots,
    displayOrderIds,
    displayedTiles,
    sortMode: readonly(sortMode),
    dragState: readonly(dragState),
    motionActive: readonly(motionActive),
    enteringTileIds: readonly(enteringTileIds),
    invariantPreserved,
    applySort,
    restoreServerOrder,
    beginDrag,
    upgradeDrag,
    previewDrag,
    finishDrag,
    replaceDisplayOrder,
  }
}

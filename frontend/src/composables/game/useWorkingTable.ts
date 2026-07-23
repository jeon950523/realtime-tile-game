import { computed, readonly, ref, watch, type Ref } from 'vue'

import { isTableTilePlacementLayoutValid } from '@/domain/game/tableGrid'
import {
  firstAvailableTableCoordinateWithGutter,
  flowCommittedTableMelds,
  resolveInteractiveTableCoordinate,
  resolveNearestTableCoordinate,
} from '@/domain/game/tableFlow'
import {
  committedMeldsFingerprint,
  deriveTableCandidates,
} from '@/domain/game/tableCandidateDerivation'
import { hasDraftPartitionInvariant, validateTurnDraft } from '@/domain/game/turnDraftValidation'
import type { GameRackTile, GameTableMeld } from '@/types/game'
import type {
  DerivedTableCandidate,
  WorkingTableBaseline,
  WorkingTableState,
  WorkingTilePlacement,
} from '@/types/turnDraft'

interface UseWorkingTableOptions {
  authoritativeRack: Readonly<Ref<readonly GameRackTile[]>>
  authoritativeVersion: Readonly<Ref<number>>
  authoritativeSyncRevision: Readonly<Ref<number>>
  tableMelds: Readonly<Ref<readonly GameTableMeld[]>>
  initialMeldCompleted: Readonly<Ref<boolean>>
  isMyTurn?: Readonly<Ref<boolean>>
  gameId?: Readonly<Ref<number | null>>
  currentTurnPlayerId?: Readonly<Ref<number | null>>
}

function cloneTableMelds(melds: readonly GameTableMeld[]): GameTableMeld[] {
  return melds.map((meld) => ({ ...meld, tiles: meld.tiles.map((tile) => ({ ...tile })) }))
}

function clonePlacements(placements: readonly WorkingTilePlacement[]): WorkingTilePlacement[] {
  return placements.map((placement) => ({ ...placement }))
}

function orderedPlacements(placements: readonly WorkingTilePlacement[]): WorkingTilePlacement[] {
  return [...placements].sort((left, right) => left.gridRow - right.gridRow
    || left.gridColumn - right.gridColumn
    || left.tileId.localeCompare(right.tileId))
}

function firstDetachedCoordinate(
  placements: readonly WorkingTilePlacement[],
  movingTileIds: readonly string[],
  sourceRow?: number,
): { gridRow: number; gridColumn: number } | null {
  return firstAvailableTableCoordinateWithGutter(placements, movingTileIds, sourceRow)
}

function samePlacements(left: readonly WorkingTilePlacement[], right: readonly WorkingTilePlacement[]): boolean {
  const leftOrdered = orderedPlacements(left)
  const rightOrdered = orderedPlacements(right)
  return leftOrdered.length === rightOrdered.length && leftOrdered.every((placement, index) => {
    const candidate = rightOrdered[index]
    return candidate?.tileId === placement.tileId
      && candidate.gridRow === placement.gridRow
      && candidate.gridColumn === placement.gridColumn
      && candidate.source === placement.source
      && candidate.sourceMeldId === placement.sourceMeldId
      && candidate.originalPositionOrder === placement.originalPositionOrder
  })
}

function sameGridPlacements(left: readonly WorkingTilePlacement[], right: readonly WorkingTilePlacement[]): boolean {
  const leftOrdered = orderedPlacements(left)
  const rightOrdered = orderedPlacements(right)
  return leftOrdered.length === rightOrdered.length && leftOrdered.every((placement, index) => {
    const candidate = rightOrdered[index]
    return candidate?.tileId === placement.tileId
      && candidate.gridRow === placement.gridRow
      && candidate.gridColumn === placement.gridColumn
  })
}

export function committedMeldsToPlacements(melds: readonly GameTableMeld[]): WorkingTilePlacement[] {
  return flowCommittedTableMelds(melds).flatMap((meld) => [...meld.tiles]
    .sort((left, right) => left.positionOrder - right.positionOrder)
    .map((tile) => ({
      tileId: tile.tileId,
      gridRow: meld.gridRow,
      gridColumn: meld.gridColumn + tile.positionOrder,
      source: 'COMMITTED_TABLE' as const,
      sourceMeldId: meld.meldId,
      originalPositionOrder: tile.positionOrder,
    })))
}

function candidateById(
  placements: readonly WorkingTilePlacement[],
  candidateId: string,
): DerivedTableCandidate | null {
  return deriveTableCandidates(placements)
    .find((candidate) => candidate.clientCandidateId === candidateId || candidate.clientMeldId === candidateId) ?? null
}

export function useWorkingTable(options: UseWorkingTableOptions) {
  const workingTable = ref<WorkingTableState | null>(null)
  const lastResolution = ref<'NONE' | 'COMMITTED' | 'STALE' | 'CANCELLED'>('NONE')
  const ownTurn = computed(() => options.isMyTurn?.value ?? true)
  const authoritativeFingerprint = computed(() => committedMeldsFingerprint(flowCommittedTableMelds(options.tableMelds.value)))
  const candidates = computed(() => deriveTableCandidates(workingTable.value?.placements ?? []))
  const rackTileIdSet = computed(() => new Set(options.authoritativeRack.value.map((tile) => tile.tileId)))
  const draftTileIds = computed(() => workingTable.value?.placements
    .filter((placement) => placement.source === 'CURRENT_PLAYER_RACK')
    .map((placement) => placement.tileId) ?? [])
  const draftTileIdSet = computed(() => new Set(draftTileIds.value))
  const visibleRackTiles = computed(() => options.authoritativeRack.value
    .filter((tile) => !draftTileIdSet.value.has(tile.tileId)))
  const validation = computed(() => validateTurnDraft(
    candidates.value,
    options.authoritativeRack.value,
    options.initialMeldCompleted.value,
    workingTable.value?.baseline.committedMelds ?? options.tableMelds.value,
    workingTable.value?.placements ?? [],
  ))
  const partitionPreserved = computed(() => hasDraftPartitionInvariant(
    options.authoritativeRack.value.map((tile) => tile.tileId),
    visibleRackTiles.value.map((tile) => tile.tileId),
    candidates.value,
  ))
  const hasChanges = computed(() => {
    const current = workingTable.value
    return Boolean(current && !samePlacements(current.placements, current.baseline.placements))
  })
  const authoritativeBaselineCurrent = computed(() => {
    const current = workingTable.value
    return Boolean(current
      && current.baseline.baseVersion === options.authoritativeVersion.value
      && current.baseline.tableMeldsFingerprint === authoritativeFingerprint.value)
  })

  function createWorkingTable(sourceDisplayOrderIds?: readonly string[]): WorkingTableState {
    const committedMelds = cloneTableMelds(flowCommittedTableMelds(options.tableMelds.value))
    const baselinePlacements = committedMeldsToPlacements(committedMelds)
    const baseline: WorkingTableBaseline = {
      gameId: options.gameId?.value ?? null,
      baseVersion: options.authoritativeVersion.value,
      currentTurnPlayerId: options.currentTurnPlayerId?.value ?? null,
      committedMelds,
      placements: clonePlacements(baselinePlacements),
      tableMeldsFingerprint: committedMeldsFingerprint(committedMelds),
      authoritativeRackIds: options.authoritativeRack.value.map((tile) => tile.tileId),
      sourceDisplayOrderIds: [...(sourceDisplayOrderIds ?? options.authoritativeRack.value.map((tile) => tile.tileId))],
    }
    return {
      baseline,
      placements: baselinePlacements,
      get melds() { return deriveTableCandidates(this.placements) },
      history: [],
      pendingCommitActionId: null,
    }
  }

  function ensureWorkingTable(sourceDisplayOrderIds: readonly string[]): WorkingTableState {
    if (!workingTable.value) {
      workingTable.value = createWorkingTable(sourceDisplayOrderIds)
      lastResolution.value = 'NONE'
    }
    return workingTable.value
  }

  function isMeldEditable(candidateId: string): boolean {
    const current = workingTable.value
    const candidate = current ? candidateById(current.placements, candidateId) : null
    if (!current || !candidate || current.pendingCommitActionId) return false
    return options.initialMeldCompleted.value
      || candidate.placements.every((placement) => placement.source === 'CURRENT_PLAYER_RACK')
  }

  function mutate(
    sourceDisplayOrderIds: readonly string[],
    operation: (placements: WorkingTilePlacement[]) => void,
  ): boolean {
    const current = ensureWorkingTable(sourceDisplayOrderIds)
    if (current.pendingCommitActionId) return false
    if (current.history.length === 0 && !hasChanges.value) {
      current.baseline.sourceDisplayOrderIds = [...sourceDisplayOrderIds]
    }
    const before = clonePlacements(current.placements)
    const next = clonePlacements(current.placements)
    operation(next)
    const allowed = new Set([
      ...current.baseline.authoritativeRackIds,
      ...current.baseline.placements.map((placement) => placement.tileId),
    ])
    const allIds = next.map((placement) => placement.tileId)
    if (allIds.some((tileId) => !allowed.has(tileId)) || new Set(allIds).size !== allIds.length) return false
    if (current.baseline.placements.some((baseline) => !allIds.includes(baseline.tileId))) return false
    if (!isTableTilePlacementLayoutValid(next) || samePlacements(before, next)) return false
    current.history.push({
      placements: before,
      sourceDisplayOrderIds: [...current.baseline.sourceDisplayOrderIds],
    })
    if (current.history.length > 100) current.history.splice(0, current.history.length - 100)
    current.placements = orderedPlacements(next)
    return true
  }

  function rackPlacement(tileId: string, gridRow: number, gridColumn: number): WorkingTilePlacement {
    return {
      tileId,
      gridRow,
      gridColumn,
      source: 'CURRENT_PLAYER_RACK',
      sourceMeldId: null,
      originalPositionOrder: null,
    }
  }

  function addAsNewMeld(
    tileIds: readonly string[],
    sourceDisplayOrderIds: readonly string[],
    requestedGridRow?: number,
    requestedGridColumn?: number,
  ): boolean {
    const unique = [...new Set(tileIds)]
    if (unique.length !== tileIds.length || unique.length === 0
      || unique.some((tileId) => !rackTileIdSet.value.has(tileId))) return false
    return mutate(sourceDisplayOrderIds, (placements) => {
      if (unique.some((tileId) => placements.some((placement) => placement.tileId === tileId))) return
      const requested = requestedGridRow !== undefined && requestedGridColumn !== undefined
        ? resolveInteractiveTableCoordinate(placements, unique, requestedGridRow, requestedGridColumn)
        : firstDetachedCoordinate(placements, unique)
      if (!requested) return
      unique.forEach((tileId, offset) => placements.push(
        rackPlacement(tileId, requested.gridRow, requested.gridColumn + offset),
      ))
    })
  }

  function reflowCandidate(
    placements: WorkingTilePlacement[],
    candidate: DerivedTableCandidate,
    orderedTileIds: readonly string[],
    metadataOverrides: ReadonlyMap<string, WorkingTilePlacement> = new Map(),
  ): void {
    const candidatesBeforeChange = deriveTableCandidates(placements)
    const targetIndex = candidatesBeforeChange.findIndex((item) => (
      item.clientCandidateId === candidate.clientCandidateId
      || item.clientMeldId === candidate.clientMeldId
    ))
    if (targetIndex < 0) return

    const movingIds = new Set([...candidate.tileIds, ...orderedTileIds])
    const metadata = new Map(placements
      .filter((placement) => movingIds.has(placement.tileId))
      .map((placement) => [placement.tileId, { ...placement }]))
    metadataOverrides.forEach((placement, tileId) => {
      metadata.set(tileId, { ...placement })
    })

    // Candidates before the edited block stay fixed. The edited block keeps its
    // local position, and only later blocks are nudged right / wrapped downward
    // when the edited width grows into their gutter. This is the production
    // Working Local Reflow contract.
    const upstreamCandidates = candidatesBeforeChange.slice(0, targetIndex)
      .filter((item) => item.tileIds.every((tileId) => !movingIds.has(tileId)))
    const downstreamCandidates = candidatesBeforeChange.slice(targetIndex + 1)
      .filter((item) => item.tileIds.every((tileId) => !movingIds.has(tileId)))
    const upstreamIds = new Set(upstreamCandidates.flatMap((item) => item.tileIds))
    const rebuilt = placements
      .filter((placement) => upstreamIds.has(placement.tileId))
      .map((placement) => ({ ...placement }))

    if (orderedTileIds.length > 0) {
      const targetCoordinate = resolveNearestTableCoordinate(
        rebuilt,
        orderedTileIds,
        candidate.gridRow,
        candidate.gridColumn,
      )
      if (!targetCoordinate) return
      orderedTileIds.forEach((tileId, offset) => {
        const existing = metadata.get(tileId)
        rebuilt.push(existing
          ? {
              ...existing,
              gridRow: targetCoordinate.gridRow,
              gridColumn: targetCoordinate.gridColumn + offset,
            }
          : rackPlacement(
              tileId,
              targetCoordinate.gridRow,
              targetCoordinate.gridColumn + offset,
            ))
      })
    }

    for (const downstream of downstreamCandidates) {
      const coordinate = resolveNearestTableCoordinate(
        rebuilt,
        downstream.tileIds,
        downstream.gridRow,
        downstream.gridColumn,
      )
      if (!coordinate) return
      downstream.tileIds.forEach((tileId, offset) => {
        const placement = downstream.placements.find((item) => item.tileId === tileId)
        if (placement) rebuilt.push({
          ...placement,
          gridRow: coordinate.gridRow,
          gridColumn: coordinate.gridColumn + offset,
        })
      })
    }

    placements.splice(0, placements.length, ...rebuilt)
  }

  function addToMeld(
    candidateId: string,
    tileIds: readonly string[],
    sourceDisplayOrderIds: readonly string[],
    targetIndex?: number,
  ): boolean {
    const unique = [...new Set(tileIds)]
    const current = workingTable.value
    const target = current ? candidateById(current.placements, candidateId) : null
    if (!target || !isMeldEditable(candidateId) || unique.length !== tileIds.length || unique.length === 0
      || unique.some((tileId) => !rackTileIdSet.value.has(tileId))) return false
    return mutate(sourceDisplayOrderIds, (placements) => {
      const candidate = candidateById(placements, candidateId)
      if (!candidate) return
      const index = Math.max(0, Math.min(targetIndex ?? candidate.tileIds.length, candidate.tileIds.length))
      const ordered = [...candidate.tileIds]
      ordered.splice(index, 0, ...unique)
      reflowCandidate(placements, candidate, ordered)
    })
  }

  function reorderTile(candidateId: string, fromIndex: number, toIndex: number): boolean {
    const current = workingTable.value
    const candidate = current ? candidateById(current.placements, candidateId) : null
    if (!current || !candidate || !isMeldEditable(candidateId)
      || fromIndex < 0 || fromIndex >= candidate.tileIds.length) return false
    return mutate(current.baseline.sourceDisplayOrderIds, (placements) => {
      const nextCandidate = candidateById(placements, candidateId)
      if (!nextCandidate) return
      const ordered = [...nextCandidate.tileIds]
      const bounded = Math.max(0, Math.min(toIndex, ordered.length - 1))
      const [tileId] = ordered.splice(fromIndex, 1)
      ordered.splice(bounded, 0, tileId!)
      reflowCandidate(placements, nextCandidate, ordered)
    })
  }

  function moveTile(tileId: string, targetCandidateId: string, targetIndex?: number): boolean {
    const current = workingTable.value
    const source = current?.placements.find((placement) => placement.tileId === tileId)
    const sourceCandidate = current ? deriveTableCandidates(current.placements)
      .find((candidate) => candidate.tileIds.includes(tileId)) : null
    const target = current ? candidateById(current.placements, targetCandidateId) : null
    if (!current || !source || !sourceCandidate || !target
      || !isMeldEditable(sourceCandidate.clientCandidateId) || !isMeldEditable(targetCandidateId)) return false
    return mutate(current.baseline.sourceDisplayOrderIds, (placements) => {
      const nextSource = deriveTableCandidates(placements)
        .find((candidate) => candidate.tileIds.includes(tileId))
      const nextTarget = candidateById(placements, targetCandidateId)
      if (!nextSource || !nextTarget) return
      if (nextSource.clientCandidateId === nextTarget.clientCandidateId) {
        const ordered = nextTarget.tileIds.filter((candidate) => candidate !== tileId)
        const index = Math.max(0, Math.min(targetIndex ?? ordered.length, ordered.length))
        ordered.splice(index, 0, tileId)
        reflowCandidate(placements, nextTarget, ordered)
        return
      }
      const sourceOrdered = nextSource.tileIds.filter((candidate) => candidate !== tileId)
      const targetOrdered = [...nextTarget.tileIds]
      const index = Math.max(0, Math.min(targetIndex ?? targetOrdered.length, targetOrdered.length))
      targetOrdered.splice(index, 0, tileId)
      const movedPlacement = placements.find((placement) => placement.tileId === tileId)
      if (!movedPlacement) return
      reflowCandidate(placements, nextSource, sourceOrdered)
      const refreshedTarget = candidateById(placements, targetCandidateId)
        ?? deriveTableCandidates(placements).find((candidate) => (
          nextTarget.tileIds.every((targetTileId) => candidate.tileIds.includes(targetTileId))
        ))
      if (!refreshedTarget) return
      reflowCandidate(
        placements,
        refreshedTarget,
        targetOrdered,
        new Map([[tileId, { ...movedPlacement }]]),
      )
    })
  }

  function moveAsNewMeld(tileId: string, requestedGridRow?: number, requestedGridColumn?: number): boolean {
    const current = workingTable.value
    const placement = current?.placements.find((candidate) => candidate.tileId === tileId)
    const sourceCandidate = current ? deriveTableCandidates(current.placements)
      .find((candidate) => candidate.tileIds.includes(tileId)) : null
    if (!current || !placement || !sourceCandidate || !isMeldEditable(sourceCandidate.clientCandidateId)) return false
    return mutate(current.baseline.sourceDisplayOrderIds, (placements) => {
      const moving = placements.find((candidate) => candidate.tileId === tileId)
      if (!moving) return
      const coordinate = requestedGridRow !== undefined && requestedGridColumn !== undefined
        ? resolveInteractiveTableCoordinate(placements, [tileId], requestedGridRow, requestedGridColumn)
        : firstDetachedCoordinate(placements, [tileId], sourceCandidate.gridRow)
      if (!coordinate) return
      moving.gridRow = coordinate.gridRow
      moving.gridColumn = coordinate.gridColumn
    })
  }

  function moveMeld(candidateId: string, gridRow: number, gridColumn: number): boolean {
    const current = workingTable.value
    const candidate = current ? candidateById(current.placements, candidateId) : null
    if (!current || !candidate || !isMeldEditable(candidateId)) return false
    const coordinate = resolveInteractiveTableCoordinate(
      current.placements,
      candidate.tileIds,
      gridRow,
      gridColumn,
    )
    if (!coordinate) return false
    return mutate(current.baseline.sourceDisplayOrderIds, (placements) => {
      candidate.tileIds.forEach((tileId, offset) => {
        const placement = placements.find((item) => item.tileId === tileId)
        if (placement) {
          placement.gridRow = coordinate.gridRow
          placement.gridColumn = coordinate.gridColumn + offset
        }
      })
    })
  }

  function splitMeld(candidateId: string, splitIndex: number): boolean {
    const current = workingTable.value
    const candidate = current ? candidateById(current.placements, candidateId) : null
    if (!current || !candidate || !isMeldEditable(candidateId)
      || splitIndex <= 0 || splitIndex >= candidate.tileIds.length) return false
    const moving = candidate.tileIds.slice(splitIndex)
    const coordinate = firstDetachedCoordinate(current.placements, moving, candidate.gridRow)
    if (!coordinate) return false
    return mutate(current.baseline.sourceDisplayOrderIds, (placements) => {
      moving.forEach((tileId, offset) => {
        const placement = placements.find((item) => item.tileId === tileId)
        if (placement) {
          placement.gridRow = coordinate.gridRow
          placement.gridColumn = coordinate.gridColumn + offset
        }
      })
    })
  }

  function mergeMelds(sourceCandidateId: string, targetCandidateId: string): boolean {
    const current = workingTable.value
    const source = current ? candidateById(current.placements, sourceCandidateId) : null
    const target = current ? candidateById(current.placements, targetCandidateId) : null
    if (!current || !source || !target || sourceCandidateId === targetCandidateId
      || !isMeldEditable(sourceCandidateId) || !isMeldEditable(targetCandidateId)) return false
    return mutate(current.baseline.sourceDisplayOrderIds, (placements) => {
      const nextSource = candidateById(placements, sourceCandidateId)
      const nextTarget = candidateById(placements, targetCandidateId)
      if (!nextSource || !nextTarget) return
      reflowCandidate(placements, nextTarget, [...nextTarget.tileIds, ...nextSource.tileIds])
    })
  }

  function returnTile(tileId: string): boolean {
    const current = workingTable.value
    const placement = current?.placements.find((candidate) => candidate.tileId === tileId)
    if (!current || !placement || placement.source !== 'CURRENT_PLAYER_RACK') return false
    return mutate(current.baseline.sourceDisplayOrderIds, (placements) => {
      const index = placements.findIndex((candidate) => candidate.tileId === tileId)
      if (index >= 0) placements.splice(index, 1)
    })
  }

  function returnMeld(candidateId: string): boolean {
    const current = workingTable.value
    const candidate = current ? candidateById(current.placements, candidateId) : null
    if (!current || !candidate
      || candidate.placements.some((placement) => placement.source !== 'CURRENT_PLAYER_RACK')) return false
    return mutate(current.baseline.sourceDisplayOrderIds, (placements) => {
      const ids = new Set(candidate.tileIds)
      placements.splice(0, placements.length, ...placements.filter((placement) => !ids.has(placement.tileId)))
    })
  }

  function undo(): boolean {
    const current = workingTable.value
    if (!current || current.pendingCommitActionId || current.history.length === 0) return false
    const snapshot = current.history.pop()!
    current.placements = clonePlacements(snapshot.placements)
    current.baseline.sourceDisplayOrderIds = [...snapshot.sourceDisplayOrderIds]
    return true
  }

  function cancel(): string[] | null {
    const current = workingTable.value
    if (!current || current.pendingCommitActionId) return null
    current.placements = clonePlacements(current.baseline.placements)
    current.history = []
    lastResolution.value = 'CANCELLED'
    return [...current.baseline.sourceDisplayOrderIds]
  }

  function markCommitPending(actionId: string): void {
    if (workingTable.value) workingTable.value.pendingCommitActionId = actionId
  }

  function rejectCommit(keepWorkingTable: boolean): void {
    const current = workingTable.value
    if (!current) return
    if (keepWorkingTable) current.pendingCommitActionId = null
    else {
      workingTable.value = ownTurn.value ? createWorkingTable() : null
      lastResolution.value = 'STALE'
    }
  }

  function authoritativeMatches(current: WorkingTableState): boolean {
    return sameGridPlacements(committedMeldsToPlacements(options.tableMelds.value), current.placements)
  }

  function resolveAuthoritativeSync(): void {
    const current = workingTable.value
    if (!current) {
      if (ownTurn.value) workingTable.value = createWorkingTable()
      return
    }
    const contributed = current.placements
      .filter((placement) => placement.source === 'CURRENT_PLAYER_RACK')
      .map((placement) => placement.tileId)
    const rackIds = new Set(options.authoritativeRack.value.map((tile) => tile.tileId))
    const contributionGone = contributed.every((tileId) => !rackIds.has(tileId))
    const committed = Boolean(current.pendingCommitActionId
      && options.authoritativeVersion.value > current.baseline.baseVersion
      && contributionGone
      && authoritativeMatches(current))
    lastResolution.value = committed ? 'COMMITTED' : 'STALE'
    workingTable.value = committed ? null : ownTurn.value ? createWorkingTable() : null
  }

  watch(ownTurn, (isOwnTurn) => {
    workingTable.value = isOwnTurn ? createWorkingTable() : null
    if (isOwnTurn) lastResolution.value = 'NONE'
  }, { flush: 'sync', immediate: true })

  watch(options.authoritativeVersion, (version) => {
    const current = workingTable.value
    if (!current || version === current.baseline.baseVersion || current.pendingCommitActionId) return
    lastResolution.value = 'STALE'
    workingTable.value = ownTurn.value ? createWorkingTable() : null
  }, { flush: 'sync' })

  watch(authoritativeFingerprint, (fingerprint) => {
    const current = workingTable.value
    if (!ownTurn.value || !current || fingerprint === current.baseline.tableMeldsFingerprint
      || current.pendingCommitActionId) return
    if (!hasChanges.value) workingTable.value = createWorkingTable(current.baseline.sourceDisplayOrderIds)
    else lastResolution.value = 'STALE'
  }, { flush: 'sync' })

  watch([
    () => options.gameId?.value ?? null,
    () => options.currentTurnPlayerId?.value ?? null,
  ], ([gameId, turnPlayerId]) => {
    const current = workingTable.value
    if (!ownTurn.value || !current || (current.baseline.gameId === gameId
      && current.baseline.currentTurnPlayerId === turnPlayerId)) return
    lastResolution.value = 'STALE'
    workingTable.value = createWorkingTable()
  }, { flush: 'sync' })

  watch(options.authoritativeSyncRevision, () => {
    resolveAuthoritativeSync()
  }, { flush: 'sync' })

  return {
    workingTable: readonly(workingTable),
    draft: readonly(workingTable),
    candidates,
    placements: computed(() => workingTable.value?.placements ?? []),
    draftTileIds,
    visibleRackTiles,
    validation,
    partitionPreserved,
    hasChanges,
    authoritativeBaselineCurrent,
    lastResolution: readonly(lastResolution),
    isMeldEditable,
    addAsNewMeld,
    placeTiles: addAsNewMeld,
    addToMeld,
    reorderTile,
    moveTile,
    moveAsNewMeld,
    movePlacement: moveAsNewMeld,
    moveMeld,
    splitMeld,
    mergeMelds,
    returnTile,
    returnMeld,
    undo,
    cancel,
    markCommitPending,
    rejectCommit,
  }
}

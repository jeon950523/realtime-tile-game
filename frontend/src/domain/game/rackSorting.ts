import type { GameRackTile } from '@/types/game'
import type { RackSortMode } from '@/types/rackPresentation'

export const RACK_COLOR_PRIORITY = ['RED', 'BLUE', 'YELLOW', 'BLACK'] as const

const colorRank = new Map<string, number>(RACK_COLOR_PRIORITY.map((color, index) => [color, index]))

function receiveOrderIndex(serverOrderIds: readonly string[]): Map<string, number> {
  return new Map(serverOrderIds.map((tileId, index) => [tileId, index]))
}

function compareJokers(left: GameRackTile, right: GameRackTile): number | null {
  if (left.joker === right.joker) return null
  return left.joker ? 1 : -1
}

function compareReceiveOrder(
  left: GameRackTile,
  right: GameRackTile,
  orderIndex: ReadonlyMap<string, number>,
): number {
  return (orderIndex.get(left.tileId) ?? Number.MAX_SAFE_INTEGER)
    - (orderIndex.get(right.tileId) ?? Number.MAX_SAFE_INTEGER)
}

export function sortRackForGroup777(
  rack: readonly GameRackTile[],
  serverOrderIds: readonly string[],
): GameRackTile[] {
  const orderIndex = receiveOrderIndex(serverOrderIds)
  return [...rack].sort((left, right) => {
    const jokerOrder = compareJokers(left, right)
    if (jokerOrder !== null) return jokerOrder
    if (left.joker && right.joker) return compareReceiveOrder(left, right, orderIndex)

    const numberOrder = (left.number ?? Number.MAX_SAFE_INTEGER) - (right.number ?? Number.MAX_SAFE_INTEGER)
    if (numberOrder !== 0) return numberOrder

    const colorOrder = (colorRank.get(left.color ?? '') ?? Number.MAX_SAFE_INTEGER)
      - (colorRank.get(right.color ?? '') ?? Number.MAX_SAFE_INTEGER)
    if (colorOrder !== 0) return colorOrder
    return compareReceiveOrder(left, right, orderIndex)
  })
}

export function sortRackForRun789(
  rack: readonly GameRackTile[],
  serverOrderIds: readonly string[],
): GameRackTile[] {
  const orderIndex = receiveOrderIndex(serverOrderIds)
  return [...rack].sort((left, right) => {
    const jokerOrder = compareJokers(left, right)
    if (jokerOrder !== null) return jokerOrder
    if (left.joker && right.joker) return compareReceiveOrder(left, right, orderIndex)

    const colorOrder = (colorRank.get(left.color ?? '') ?? Number.MAX_SAFE_INTEGER)
      - (colorRank.get(right.color ?? '') ?? Number.MAX_SAFE_INTEGER)
    if (colorOrder !== 0) return colorOrder

    const numberOrder = (left.number ?? Number.MAX_SAFE_INTEGER) - (right.number ?? Number.MAX_SAFE_INTEGER)
    if (numberOrder !== 0) return numberOrder
    return compareReceiveOrder(left, right, orderIndex)
  })
}

export function sortRackIds(
  rack: readonly GameRackTile[],
  serverOrderIds: readonly string[],
  mode: Extract<RackSortMode, 'GROUP_777' | 'RUN_789'>,
): string[] {
  const sorted = mode === 'GROUP_777'
    ? sortRackForGroup777(rack, serverOrderIds)
    : sortRackForRun789(rack, serverOrderIds)
  return sorted.map((tile) => tile.tileId)
}

export function moveTileId(
  orderIds: readonly string[],
  tileId: string,
  targetIndex: number,
): string[] {
  const currentIndex = orderIds.indexOf(tileId)
  if (currentIndex < 0 || orderIds.length === 0) return [...orderIds]
  const boundedTarget = Math.max(0, Math.min(targetIndex, orderIds.length - 1))
  if (currentIndex === boundedTarget) return [...orderIds]

  const moved = [...orderIds]
  moved.splice(currentIndex, 1)
  moved.splice(boundedTarget, 0, tileId)
  return moved
}

export function mergeRackDisplayOrder(
  rack: readonly GameRackTile[],
  previousDisplayOrderIds: readonly string[],
  serverOrderIds: readonly string[],
  mode: RackSortMode,
): string[] {
  if (mode === 'GROUP_777' || mode === 'RUN_789') {
    return sortRackIds(rack, serverOrderIds, mode)
  }

  const authoritativeIds = rack.map((tile) => tile.tileId)
  const authoritativeSet = new Set(authoritativeIds)
  const retained = previousDisplayOrderIds.filter((tileId) => authoritativeSet.has(tileId))
  const retainedSet = new Set(retained)
  const additions = authoritativeIds.filter((tileId) => !retainedSet.has(tileId))
  return [...retained, ...additions]
}

export function hasRackOrderInvariant(
  rack: readonly GameRackTile[],
  displayOrderIds: readonly string[],
): boolean {
  const authoritativeIds = rack.map((tile) => tile.tileId)
  if (authoritativeIds.length !== displayOrderIds.length) return false
  if (new Set(authoritativeIds).size !== authoritativeIds.length) return false
  if (new Set(displayOrderIds).size !== displayOrderIds.length) return false
  const authoritativeSet = new Set(authoritativeIds)
  return displayOrderIds.every((tileId) => authoritativeSet.has(tileId))
}


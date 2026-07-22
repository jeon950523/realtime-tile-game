import type { GameRackTile } from '@/types/game'
import type { RackSlot } from '@/types/rackPresentation'

const MIN_RACK_COLUMNS = 10

function rackColumnCount(slots: readonly RackSlot[]): number | null {
  if (slots.length < MIN_RACK_COLUMNS * 2 || slots.length % 2 !== 0) return null
  return slots.length / 2
}

export function rackSlotCount(tileCount: number): number {
  const columns = Math.max(MIN_RACK_COLUMNS, Math.ceil(Math.max(1, tileCount) / 2))
  return columns * 2
}

export function compactRackSlots(tileIds: readonly string[], slotCountHint = 0): RackSlot[] {
  const count = Math.max(rackSlotCount(tileIds.length), slotCountHint, tileIds.length)
  return Array.from({ length: count }, (_, slotIndex) => ({
    slotIndex,
    tileId: tileIds[slotIndex] ?? null,
  }))
}

/**
 * Rebuilds an explicitly sorted Rack from its current visible tiles only.
 * Historical workspace capacity and manual interior gaps intentionally do not
 * participate in 789 / 777 / server-order sorting.
 */
export function buildCompactSortedRackSlots(tileIds: readonly string[]): RackSlot[] {
  return compactRackSlots(tileIds, rackSlotCount(tileIds.length))
}

export function rackSlotTileIds(slots: readonly RackSlot[]): string[] {
  return slots.flatMap((slot) => slot.tileId === null ? [] : [slot.tileId])
}

/**
 * Returns the visible two-row workspace without trailing columns whose upper and
 * lower slots are both empty. Interior empty slots are intentionally preserved.
 * Original slotIndex values are retained so drag targets still address the
 * persistent workspace used for Undo and Cancel restoration.
 */
export function visibleRackSlots(slots: readonly RackSlot[]): RackSlot[] {
  const initialColumns = rackColumnCount(slots)
  if (initialColumns === null) return slots.map((slot) => ({ ...slot }))

  const visible = slots.map((slot) => ({ ...slot }))
  let columns = initialColumns
  while (columns > MIN_RACK_COLUMNS) {
    const upperRightIndex = columns - 1
    const lowerRightIndex = columns * 2 - 1
    if (visible[upperRightIndex]?.tileId !== null || visible[lowerRightIndex]?.tileId !== null) break
    visible.splice(lowerRightIndex, 1)
    visible.splice(upperRightIndex, 1)
    columns -= 1
  }
  return visible
}

/**
 * Applies visible capacity to the authoritative workspace. This is reserved for
 * server rack synchronization, after draft Undo/Cancel restoration is no longer
 * needed. Rows and columns are preserved; only removed trailing columns cause
 * slotIndex values to be normalized.
 */
export function compactRackSlotCapacity(slots: readonly RackSlot[]): RackSlot[] {
  return visibleRackSlots(slots).map((slot, slotIndex) => ({
    slotIndex,
    tileId: slot.tileId,
  }))
}

export function moveRackTileToSlot(
  slots: readonly RackSlot[],
  tileId: string,
  targetSlotIndex: number,
): RackSlot[] {
  const sourceSlotIndex = slots.findIndex((slot) => slot.tileId === tileId)
  if (sourceSlotIndex < 0 || targetSlotIndex < 0 || targetSlotIndex >= slots.length) return [...slots]
  if (sourceSlotIndex === targetSlotIndex) return slots.map((slot) => ({ ...slot }))
  const moved = slots.map((slot) => ({ ...slot }))
  const targetTileId = moved[targetSlotIndex]!.tileId
  moved[targetSlotIndex]!.tileId = tileId
  moved[sourceSlotIndex]!.tileId = targetTileId
  return moved
}

export function removeRackTileFromSlots(slots: readonly RackSlot[], tileId: string): RackSlot[] {
  return slots.map((slot) => slot.tileId === tileId ? { ...slot, tileId: null } : { ...slot })
}

export function restoreRackTileToSlots(
  slots: readonly RackSlot[],
  tileId: string,
  preferredSlotIndex: number,
): RackSlot[] {
  if (slots.some((slot) => slot.tileId === tileId)) return slots.map((slot) => ({ ...slot }))
  const restored = slots.map((slot) => ({ ...slot }))
  const emptySlots = restored.filter((slot) => slot.tileId === null)
  if (emptySlots.length === 0) {
    const expandedCount = rackSlotCount(restored.length + 1)
    for (let slotIndex = restored.length; slotIndex < expandedCount; slotIndex++) {
      restored.push({ slotIndex, tileId: null })
    }
  }
  const destination = restored
    .filter((slot) => slot.tileId === null)
    .sort((left, right) => {
      const distance = Math.abs(left.slotIndex - preferredSlotIndex) - Math.abs(right.slotIndex - preferredSlotIndex)
      return distance || left.slotIndex - right.slotIndex
    })[0]
  if (destination) destination.tileId = tileId
  return restored
}

export function hasRackSlotInvariant(
  rack: readonly GameRackTile[],
  excludedTileIds: ReadonlySet<string>,
  slots: readonly RackSlot[],
): boolean {
  const expected = rack.map((tile) => tile.tileId).filter((tileId) => !excludedTileIds.has(tileId))
  const actual = rackSlotTileIds(slots)
  return expected.length === actual.length
    && new Set(actual).size === actual.length
    && expected.every((tileId) => actual.includes(tileId))
}

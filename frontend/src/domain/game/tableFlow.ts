import {
  TABLE_GRID_BOTTOM_DROP_ROWS,
  TABLE_GRID_COLUMNS,
  TABLE_GRID_GUTTER_COLUMNS,
  TABLE_GRID_ROWS,
  TABLE_GRID_VISIBLE_ROWS,
  isTableGridCoordinateInBounds,
  type TableGridCoordinate,
  type TableGridTilePlacement,
} from '@/domain/game/tableGrid'
import { deriveTableCandidates } from '@/domain/game/tableCandidateDerivation'
import type { GameTableMeld } from '@/types/game'
import type { WorkingTilePlacement } from '@/types/turnDraft'

export interface TableFlowBlock {
  blockId: string
  tileIds: readonly string[]
  gridRow?: number
  gridColumn?: number
}

export interface FlowedTableBlock extends TableFlowBlock {
  gridRow: number
  gridColumn: number
}

export function flowTableBlocks(blocks: readonly TableFlowBlock[]): FlowedTableBlock[] {
  let row = 0
  let column = 0
  const flowed: FlowedTableBlock[] = []

  blocks.forEach((block) => {
    const tileCount = block.tileIds.length
    if (tileCount === 0 || tileCount > TABLE_GRID_COLUMNS || row >= TABLE_GRID_ROWS) return
    if (column > 0 && column + tileCount > TABLE_GRID_COLUMNS) {
      row += 1
      column = 0
    }
    if (row >= TABLE_GRID_ROWS) return
    flowed.push({ ...block, gridRow: row, gridColumn: column })
    column += tileCount + TABLE_GRID_GUTTER_COLUMNS
    if (column >= TABLE_GRID_COLUMNS) {
      row += 1
      column = 0
    }
  })
  return flowed
}

export function flowCommittedTableMelds(melds: readonly GameTableMeld[]): GameTableMeld[] {
  const ordered = [...melds].sort((left, right) => left.gridRow - right.gridRow
    || left.gridColumn - right.gridColumn
    || left.meldId.localeCompare(right.meldId))
  const flowed = flowTableBlocks(ordered.map((meld) => ({
    blockId: meld.meldId,
    tileIds: [...meld.tiles]
      .sort((left, right) => left.positionOrder - right.positionOrder)
      .map((tile) => tile.tileId),
  })))
  const positionById = new Map(flowed.map((block) => [block.blockId, block]))
  return ordered.map((meld) => {
    const position = positionById.get(meld.meldId)
    return {
      ...meld,
      gridRow: position?.gridRow ?? meld.gridRow,
      gridColumn: position?.gridColumn ?? meld.gridColumn,
      tiles: [...meld.tiles]
        .sort((left, right) => left.positionOrder - right.positionOrder)
        .map((tile, positionOrder) => ({ ...tile, positionOrder })),
    }
  })
}

export function flowWorkingPlacements(placements: readonly WorkingTilePlacement[]): WorkingTilePlacement[] {
  const candidates = deriveTableCandidates(placements)
  const flowed = flowTableBlocks(candidates.map((candidate) => ({
    blockId: candidate.clientCandidateId,
    tileIds: candidate.tileIds,
  })))
  const metadata = new Map(placements.map((placement) => [placement.tileId, placement]))
  return flowed.flatMap((block) => block.tileIds.map((tileId, offset) => ({
    ...metadata.get(tileId)!,
    gridRow: block.gridRow,
    gridColumn: block.gridColumn + offset,
  })))
}

export function tableContentRows(
  placements: readonly Pick<TableGridTilePlacement, 'gridRow'>[],
  includeBottomDropRow = true,
): number {
  const occupiedRows = placements.length === 0
    ? 1
    : Math.max(...placements.map((placement) => placement.gridRow)) + 1
  const requested = occupiedRows + (includeBottomDropRow ? TABLE_GRID_BOTTOM_DROP_ROWS : 0)
  return Math.min(TABLE_GRID_ROWS, Math.max(TABLE_GRID_VISIBLE_ROWS, requested))
}

export function canPlaceTableBlockWithGutter(
  placements: readonly TableGridTilePlacement[],
  movingTileIds: readonly string[],
  gridRow: number,
  gridColumn: number,
): boolean {
  if (!isTableGridCoordinateInBounds(movingTileIds.length, gridRow, gridColumn)) return false
  const moving = new Set(movingTileIds)
  if (moving.size !== movingTileIds.length) return false
  const occupied = new Set(placements
    .filter((placement) => !moving.has(placement.tileId))
    .map((placement) => `${placement.gridRow}:${placement.gridColumn}`))
  for (let offset = -TABLE_GRID_GUTTER_COLUMNS;
    offset < movingTileIds.length + TABLE_GRID_GUTTER_COLUMNS;
    offset += 1) {
    const column = gridColumn + offset
    if (column < 0 || column >= TABLE_GRID_COLUMNS) continue
    if (occupied.has(`${gridRow}:${column}`)) return false
  }
  return true
}

/**
 * Resolves a drop deterministically without moving content upward or to the left.
 * The requested row is scanned from the requested column to the right, then the
 * following rows are scanned from column zero. This matches the visible
 * "nudge right, then wrap" table behaviour and avoids oscillating layouts.
 */
export function resolveNearestTableCoordinate(
  placements: readonly TableGridTilePlacement[],
  movingTileIds: readonly string[],
  requestedRow: number,
  requestedColumn: number,
): TableGridCoordinate | null {
  if (movingTileIds.length === 0
    || !Number.isInteger(requestedRow)
    || !Number.isInteger(requestedColumn)
    || requestedRow < 0
    || requestedRow >= TABLE_GRID_ROWS
    || requestedColumn < 0
    || requestedColumn >= TABLE_GRID_COLUMNS) return null

  for (let gridRow = requestedRow; gridRow < TABLE_GRID_ROWS; gridRow += 1) {
    const firstColumn = gridRow === requestedRow ? requestedColumn : 0
    for (let gridColumn = firstColumn;
      gridColumn + movingTileIds.length <= TABLE_GRID_COLUMNS;
      gridColumn += 1) {
      if (canPlaceTableBlockWithGutter(placements, movingTileIds, gridRow, gridColumn)) {
        return { gridRow, gridColumn }
      }
    }
  }
  return null
}

export function firstAvailableTableCoordinateWithGutter(
  placements: readonly TableGridTilePlacement[],
  movingTileIds: readonly string[],
  preferredRow?: number,
): TableGridCoordinate | null {
  if (preferredRow !== undefined
    && Number.isInteger(preferredRow)
    && preferredRow >= 0
    && preferredRow < TABLE_GRID_ROWS) {
    const preferred = resolveNearestTableCoordinate(placements, movingTileIds, preferredRow, 0)
    if (preferred) return preferred
  }
  return resolveNearestTableCoordinate(placements, movingTileIds, 0, 0)
}

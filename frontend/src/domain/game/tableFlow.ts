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

function tableCellKey(gridRow: number, gridColumn: number): string {
  return `${gridRow}:${gridColumn}`
}

function createMovingTileSet(movingTileIds: readonly string[]): Set<string> | null {
  if (movingTileIds.length === 0) return null
  const moving = new Set(movingTileIds)
  return moving.size === movingTileIds.length ? moving : null
}

function occupiedTableCells(
  placements: readonly TableGridTilePlacement[],
  movingTileIds: readonly string[],
): ReadonlySet<string> | null {
  const moving = createMovingTileSet(movingTileIds)
  if (!moving) return null
  return new Set(placements
    .filter((placement) => !moving.has(placement.tileId))
    .map((placement) => tableCellKey(placement.gridRow, placement.gridColumn)))
}

function canPlaceAgainstOccupied(
  occupied: ReadonlySet<string>,
  tileCount: number,
  gridRow: number,
  gridColumn: number,
): boolean {
  if (!isTableGridCoordinateInBounds(tileCount, gridRow, gridColumn)) return false
  for (let offset = 0; offset < tileCount; offset += 1) {
    if (occupied.has(tableCellKey(gridRow, gridColumn + offset))) return false
  }
  return true
}

function canPlaceWithGutterAgainstOccupied(
  occupied: ReadonlySet<string>,
  tileCount: number,
  gridRow: number,
  gridColumn: number,
): boolean {
  if (!isTableGridCoordinateInBounds(tileCount, gridRow, gridColumn)) return false
  for (let offset = -TABLE_GRID_GUTTER_COLUMNS;
    offset < tileCount + TABLE_GRID_GUTTER_COLUMNS;
    offset += 1) {
    const column = gridColumn + offset
    if (column < 0 || column >= TABLE_GRID_COLUMNS) continue
    if (occupied.has(tableCellKey(gridRow, column))) return false
  }
  return true
}

export interface TableCoordinateResolver {
  readonly occupiedCellCount: number
  canPlaceExact(gridRow: number, gridColumn: number): boolean
  canPlaceWithGutter(gridRow: number, gridColumn: number): boolean
  resolveNearest(requestedRow: number, requestedColumn: number): TableGridCoordinate | null
  resolveInteractive(requestedRow: number, requestedColumn: number): TableGridCoordinate | null
}

/**
 * Builds one immutable occupancy snapshot for a drag or mutation.
 *
 * Reusing this resolver avoids rebuilding the same occupied-cell Set for every
 * scanned coordinate and every pointer-move frame.
 */
export function createTableCoordinateResolver(
  placements: readonly TableGridTilePlacement[],
  movingTileIds: readonly string[],
): TableCoordinateResolver {
  const occupied = occupiedTableCells(placements, movingTileIds)
  const tileCount = movingTileIds.length

  function canPlaceExact(gridRow: number, gridColumn: number): boolean {
    return occupied !== null && canPlaceAgainstOccupied(
      occupied,
      tileCount,
      gridRow,
      gridColumn,
    )
  }

  function canPlaceWithGutter(gridRow: number, gridColumn: number): boolean {
    return occupied !== null && canPlaceWithGutterAgainstOccupied(
      occupied,
      tileCount,
      gridRow,
      gridColumn,
    )
  }

  function resolveNearest(
    requestedRow: number,
    requestedColumn: number,
  ): TableGridCoordinate | null {
    if (occupied === null
      || !Number.isInteger(requestedRow)
      || !Number.isInteger(requestedColumn)
      || requestedRow < 0
      || requestedRow >= TABLE_GRID_ROWS
      || requestedColumn < 0
      || requestedColumn >= TABLE_GRID_COLUMNS) return null

    for (let gridRow = requestedRow; gridRow < TABLE_GRID_ROWS; gridRow += 1) {
      const firstColumn = gridRow === requestedRow ? requestedColumn : 0
      for (let gridColumn = firstColumn;
        gridColumn + tileCount <= TABLE_GRID_COLUMNS;
        gridColumn += 1) {
        if (canPlaceWithGutter(gridRow, gridColumn)) {
          return { gridRow, gridColumn }
        }
      }
    }
    return null
  }

  function resolveInteractive(
    requestedRow: number,
    requestedColumn: number,
  ): TableGridCoordinate | null {
    if (canPlaceExact(requestedRow, requestedColumn)) {
      return { gridRow: requestedRow, gridColumn: requestedColumn }
    }
    return resolveNearest(requestedRow, requestedColumn)
  }

  return {
    occupiedCellCount: occupied?.size ?? 0,
    canPlaceExact,
    canPlaceWithGutter,
    resolveNearest,
    resolveInteractive,
  }
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
  return createTableCoordinateResolver(placements, movingTileIds)
    .canPlaceWithGutter(gridRow, gridColumn)
}

/**
 * Resolves a drop deterministically without moving content upward or to the left.
 * The requested row is scanned from the requested column to the right, then the
 * following rows are scanned from column zero.
 */
export function resolveNearestTableCoordinate(
  placements: readonly TableGridTilePlacement[],
  movingTileIds: readonly string[],
  requestedRow: number,
  requestedColumn: number,
): TableGridCoordinate | null {
  return createTableCoordinateResolver(placements, movingTileIds)
    .resolveNearest(requestedRow, requestedColumn)
}

/**
 * Keeps an explicit free user coordinate exactly so adjacent tiles derive one
 * Candidate. Only an occupied coordinate falls back to gutter-aware nudge/wrap.
 */
export function resolveInteractiveTableCoordinate(
  placements: readonly TableGridTilePlacement[],
  movingTileIds: readonly string[],
  requestedRow: number,
  requestedColumn: number,
): TableGridCoordinate | null {
  return createTableCoordinateResolver(placements, movingTileIds)
    .resolveInteractive(requestedRow, requestedColumn)
}

export function firstAvailableTableCoordinateWithGutter(
  placements: readonly TableGridTilePlacement[],
  movingTileIds: readonly string[],
  preferredRow?: number,
): TableGridCoordinate | null {
  const resolver = createTableCoordinateResolver(placements, movingTileIds)
  if (preferredRow !== undefined
    && Number.isInteger(preferredRow)
    && preferredRow >= 0
    && preferredRow < TABLE_GRID_ROWS) {
    const preferred = resolver.resolveNearest(preferredRow, 0)
    if (preferred) return preferred
  }
  return resolver.resolveNearest(0, 0)
}

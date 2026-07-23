export const TABLE_GRID_VISIBLE_ROWS = 8
export const TABLE_GRID_ROWS = 18
export const TABLE_GRID_COLUMNS = 18
export const TABLE_GRID_GUTTER_COLUMNS = 1
export const TABLE_GRID_BOTTOM_DROP_ROWS = 1
export const TABLE_GRID_CELL_WIDTH_PX = 58
export const TABLE_GRID_ROW_HEIGHT_PX = 68
export const TABLE_TILE_WIDTH_PX = 54
export const TABLE_TILE_HEIGHT_PX = 66
export const TABLE_GRID_COMPACT_CELL_WIDTH_PX = 52
export const TABLE_GRID_COMPACT_ROW_HEIGHT_PX = 60
export const TABLE_TILE_COMPACT_WIDTH_PX = 48
export const TABLE_TILE_COMPACT_HEIGHT_PX = 59
export const TABLE_GRID_CANVAS_WIDTH_PX = TABLE_GRID_COLUMNS * TABLE_GRID_CELL_WIDTH_PX
export const TABLE_GRID_CANVAS_HEIGHT_PX = TABLE_GRID_ROWS * TABLE_GRID_ROW_HEIGHT_PX
export const TABLE_GRID_VIEWPORT_HEIGHT_PX = TABLE_GRID_VISIBLE_ROWS * TABLE_GRID_ROW_HEIGHT_PX
export const TABLE_GRID_COMPACT_CANVAS_WIDTH_PX = TABLE_GRID_COLUMNS * TABLE_GRID_COMPACT_CELL_WIDTH_PX
export const TABLE_GRID_COMPACT_CANVAS_HEIGHT_PX = TABLE_GRID_ROWS * TABLE_GRID_COMPACT_ROW_HEIGHT_PX


export interface TableGridCoordinate {
  gridRow: number
  gridColumn: number
}

export interface TableGridTilePlacement {
  tileId: string
  gridRow: number
  gridColumn: number
}

export interface TableGridClientRect {
  left: number
  top: number
  width: number
  height: number
}

export interface TableGridCellClientRect {
  left: number
  top: number
  width: number
  height: number
}

export function clientPointToTableGridCoordinate(
  boardRect: TableGridClientRect,
  clientX: number,
  clientY: number,
  contentRows = TABLE_GRID_ROWS,
): TableGridCoordinate | null {
  if (boardRect.width <= 0 || boardRect.height <= 0 || contentRows <= 0) return null
  const x = clientX - boardRect.left
  const y = clientY - boardRect.top
  if (x < 0 || y < 0 || x >= boardRect.width || y >= boardRect.height) return null
  return {
    gridRow: Math.floor(y / (boardRect.height / contentRows)),
    gridColumn: Math.floor(x / (boardRect.width / TABLE_GRID_COLUMNS)),
  }
}

export function tableGridCellClientRect(
  boardRect: TableGridClientRect,
  gridRow: number,
  gridColumn: number,
  tileCount = 1,
  contentRows = TABLE_GRID_ROWS,
): TableGridCellClientRect | undefined {
  if (!isTableGridCoordinateInBounds(tileCount, gridRow, gridColumn) || contentRows <= 0) return undefined
  const cellWidth = boardRect.width / TABLE_GRID_COLUMNS
  const rowHeight = boardRect.height / contentRows
  return {
    left: boardRect.left + gridColumn * cellWidth,
    top: boardRect.top + gridRow * rowHeight,
    width: tileCount * cellWidth,
    height: rowHeight,
  }
}

export function isTableGridCellInBounds(gridRow: number, gridColumn: number): boolean {
  return Number.isInteger(gridRow)
    && Number.isInteger(gridColumn)
    && gridRow >= 0
    && gridRow < TABLE_GRID_ROWS
    && gridColumn >= 0
    && gridColumn < TABLE_GRID_COLUMNS
}

export function canPlaceTableTiles(
  placements: readonly TableGridTilePlacement[],
  movingTileIds: readonly string[],
  gridRow: number,
  gridColumn: number,
): boolean {
  if (movingTileIds.length === 0 || gridColumn + movingTileIds.length > TABLE_GRID_COLUMNS) return false
  const moving = new Set(movingTileIds)
  if (moving.size !== movingTileIds.length) return false
  const occupied = new Set(placements
    .filter((placement) => !moving.has(placement.tileId))
    .map((placement) => `${placement.gridRow}:${placement.gridColumn}`))
  return movingTileIds.every((_, offset) => isTableGridCellInBounds(gridRow, gridColumn + offset)
    && !occupied.has(`${gridRow}:${gridColumn + offset}`))
}

export function isTableTilePlacementLayoutValid(
  placements: readonly TableGridTilePlacement[],
): boolean {
  const tileIds = placements.map((placement) => placement.tileId)
  const cells = placements.map((placement) => `${placement.gridRow}:${placement.gridColumn}`)
  return new Set(tileIds).size === tileIds.length
    && new Set(cells).size === cells.length
    && placements.every((placement) => isTableGridCellInBounds(placement.gridRow, placement.gridColumn))
}

export function isTableGridCoordinateInBounds(
  tileCount: number,
  gridRow: number,
  gridColumn: number,
): boolean {
  return Number.isInteger(gridRow)
    && Number.isInteger(gridColumn)
    && tileCount > 0
    && gridRow >= 0
    && gridRow < TABLE_GRID_ROWS
    && gridColumn >= 0
    && gridColumn + tileCount <= TABLE_GRID_COLUMNS
}


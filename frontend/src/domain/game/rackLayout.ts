export interface AdaptiveRackLayout {
  columns: number
  rows: number
  tileWidth: number
  tileHeight: number
  gap: number
  rackHeight: number
}

export function computeAdaptiveRackLayout(tileCount: number, availableWidth: number): AdaptiveRackLayout {
  const safeCount = Math.max(1, tileCount)
  const columns = safeCount > 20 ? Math.ceil(safeCount / 2) : Math.min(10, safeCount)
  const rows = Math.ceil(safeCount / columns)
  const gap = availableWidth < 720 ? 3 : 5
  const widthBySpace = Math.floor((Math.max(320, availableWidth) - gap * (columns - 1)) / columns)
  const tileWidth = Math.max(42, Math.min(82, widthBySpace))
  const tileHeight = Math.round(tileWidth * (96 / 82))
  const rackHeight = rows * tileHeight + Math.max(0, rows - 1) * gap + 26
  return { columns, rows, tileWidth, tileHeight, gap, rackHeight }
}

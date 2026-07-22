import type { GameRackTile } from '@/types/game'
import type { RackSortMode } from '@/types/rackPresentation'
import type { RackVisualGroup } from '@/types/turnDraft'
import { validateDraftMeld } from '@/domain/game/turnDraftValidation'

const COLORS = ['RED', 'BLUE', 'YELLOW', 'BLACK'] as const

function receiveRanks(serverOrderIds: readonly string[]): Map<string, number> {
  return new Map(serverOrderIds.map((tileId, index) => [tileId, index]))
}

function byReceiveOrder(tiles: readonly GameRackTile[], ranks: ReadonlyMap<string, number>): GameRackTile[] {
  return [...tiles].sort((left, right) =>
    (ranks.get(left.tileId) ?? Number.MAX_SAFE_INTEGER)
      - (ranks.get(right.tileId) ?? Number.MAX_SAFE_INTEGER))
}

function runGroups(tiles: readonly GameRackTile[], serverOrderIds: readonly string[]): RackVisualGroup[] {
  const ranks = receiveRanks(serverOrderIds)
  const result: RackVisualGroup[] = []
  for (const color of COLORS) {
    const byNumber = new Map<number, GameRackTile[]>()
    tiles.filter((tile) => !tile.joker && tile.color === color).forEach((tile) => {
      const bucket = byNumber.get(tile.number!) ?? []
      bucket.push(tile)
      byNumber.set(tile.number!, bucket)
    })
    byNumber.forEach((bucket, number) => byNumber.set(number, byReceiveOrder(bucket, ranks)))
    const laneCount = Math.max(0, ...[...byNumber.values()].map((bucket) => bucket.length))
    for (let lane = 0; lane < laneCount; lane++) {
      let segment: GameRackTile[] = []
      const flush = () => {
        if (segment.length >= 3) {
          result.push({
            groupId: `RUN:${color}:${lane}:${segment[0]!.number}`,
            kind: 'RUN',
            tileIds: segment.map((tile) => tile.tileId),
          })
        }
        segment = []
      }
      for (let number = 1; number <= 13; number++) {
        const tile = byNumber.get(number)?.[lane]
        if (tile) segment.push(tile)
        else flush()
      }
      flush()
    }
  }
  return result
}

function groupGroups(tiles: readonly GameRackTile[], serverOrderIds: readonly string[]): RackVisualGroup[] {
  const ranks = receiveRanks(serverOrderIds)
  const result: RackVisualGroup[] = []
  for (let number = 1; number <= 13; number++) {
    const byColor = new Map<string, GameRackTile[]>()
    COLORS.forEach((color) => {
      byColor.set(color, byReceiveOrder(
        tiles.filter((tile) => !tile.joker && tile.number === number && tile.color === color),
        ranks,
      ))
    })
    const laneCount = Math.max(0, ...COLORS.map((color) => byColor.get(color)!.length))
    for (let lane = 0; lane < laneCount; lane++) {
      const laneTiles = COLORS
        .map((color) => byColor.get(color)?.[lane])
        .filter((tile): tile is GameRackTile => tile !== undefined)
      if (laneTiles.length >= 3) {
        result.push({
          groupId: `GROUP:${number}:${lane}`,
          kind: 'GROUP',
          tileIds: laneTiles.map((tile) => tile.tileId),
        })
      }
    }
  }
  return result
}

export function buildRackVisualGroups(
  tiles: readonly GameRackTile[],
  sortMode: RackSortMode,
  serverOrderIds: readonly string[],
): RackVisualGroup[] {
  if (sortMode === 'RUN_789') return runGroups(tiles, serverOrderIds)
  if (sortMode === 'GROUP_777') return groupGroups(tiles, serverOrderIds)
  return []
}

interface PhysicalRackSlot {
  slotIndex: number
  tileId: string | null
}

export function buildRackAdjacentGroups(
  tiles: readonly GameRackTile[],
  slots: readonly PhysicalRackSlot[],
  sortMode: RackSortMode,
  _serverOrderIds: readonly string[],
): RackVisualGroup[] {
  const tileById = new Map(tiles.map((tile) => [tile.tileId, tile]))
  const columns = Math.max(1, Math.ceil(slots.length / 2))
  const candidates: RackVisualGroup[] = []

  for (let row = 0; row < 2; row++) {
    const rowSlots = slots
      .filter((slot) => Math.floor(slot.slotIndex / columns) === row)
      .sort((left, right) => left.slotIndex - right.slotIndex)
    let block: GameRackTile[] = []
    const flush = () => {
      for (let start = 0; start < block.length; start++) {
        for (let end = start + 3; end <= block.length; end++) {
          const segment = block.slice(start, end)
          if (segment.some((tile) => tile.joker)) continue
          const validation = validateDraftMeld({
            clientMeldId: 'rack-adjacency',
            tileIds: segment.map((tile) => tile.tileId),
          }, tileById)
          if (!validation.valid || validation.kind === 'INVALID') continue
          candidates.push({
            groupId: `PHYSICAL:${row}:${start}:${end}:${segment.map((tile) => tile.tileId).join('|')}`,
            kind: validation.kind,
            tileIds: segment.map((tile) => tile.tileId),
          })
        }
      }
      block = []
    }
    rowSlots.forEach((slot) => {
      const tile = slot.tileId ? tileById.get(slot.tileId) : undefined
      if (!tile) {
        flush()
        return
      }
      block.push(tile)
    })
    flush()
  }

  const hint = sortMode === 'RUN_789' ? 'RUN' : sortMode === 'GROUP_777' ? 'GROUP' : null
  return candidates.sort((left, right) => right.tileIds.length - left.tileIds.length
    || (left.kind === hint ? -1 : right.kind === hint ? 1 : 0)
    || left.groupId.localeCompare(right.groupId))
}

export function visualGroupForTile(
  groups: readonly RackVisualGroup[],
  tileId: string,
): RackVisualGroup | null {
  return groups.find((group) => group.tileIds.includes(tileId)) ?? null
}

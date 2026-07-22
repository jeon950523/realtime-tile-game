import type { GameRackTile, GameTileColor } from '@/types/game'

const NUMBER_TILE_ID = /^(RED|BLUE|YELLOW|BLACK)-(0[1-9]|1[0-3])-[AB]$/

export function publicPreviewTileFromId(tileId: string, positionOrder: number): GameRackTile | null {
  if (/^JOKER-[AB]$/.test(tileId)) {
    return {
      tileId,
      tileType: 'JOKER',
      color: null,
      number: null,
      joker: true,
      positionOrder,
    }
  }
  const match = NUMBER_TILE_ID.exec(tileId)
  if (!match) return null
  return {
    tileId,
    tileType: 'NUMBER',
    color: match[1] as GameTileColor,
    number: Number(match[2]),
    joker: false,
    positionOrder,
  }
}

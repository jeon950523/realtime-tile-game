import { describe, expect, it } from 'vitest'

import {
  hasRackOrderInvariant,
  moveTileId,
  sortRackForGroup777,
  sortRackForRun789,
} from '@/domain/game/rackSorting'
import type { GameRackTile } from '@/types/game'

function numberTile(tileId: string, color: GameRackTile['color'], number: number): GameRackTile {
  return { tileId, tileType: 'NUMBER', color, number, joker: false, positionOrder: 0 }
}

const joker: GameRackTile = {
  tileId: 'JOKER-A',
  tileType: 'JOKER',
  color: null,
  number: null,
  joker: true,
  positionOrder: 99,
}

describe('rack sorting', () => {
  const rack = [
    numberTile('BLUE-07-B', 'BLUE', 7),
    numberTile('RED-02-A', 'RED', 2),
    numberTile('BLUE-01-B', 'BLUE', 1),
    joker,
    numberTile('RED-01-B', 'RED', 1),
    numberTile('YELLOW-01-A', 'YELLOW', 1),
    numberTile('BLUE-01-A', 'BLUE', 1),
    numberTile('RED-01-A', 'RED', 1),
  ]
  const serverOrderIds = rack.map((tile) => tile.tileId)

  it('SORT-001 orders 777 by number and then color', () => {
    expect(sortRackForGroup777(rack, serverOrderIds).map((tile) => tile.tileId)).toEqual([
      'RED-01-B',
      'RED-01-A',
      'BLUE-01-B',
      'BLUE-01-A',
      'YELLOW-01-A',
      'RED-02-A',
      'BLUE-07-B',
      'JOKER-A',
    ])
  })

  it('SORT-002 orders 789 by color and then number', () => {
    expect(sortRackForRun789(rack, serverOrderIds).map((tile) => tile.tileId)).toEqual([
      'RED-01-B',
      'RED-01-A',
      'RED-02-A',
      'BLUE-01-B',
      'BLUE-01-A',
      'BLUE-07-B',
      'YELLOW-01-A',
      'JOKER-A',
    ])
  })

  it('SORT-003 keeps duplicate tiles in the last server receive order', () => {
    const sorted = sortRackForRun789(rack, serverOrderIds).map((tile) => tile.tileId)
    expect(sorted.indexOf('RED-01-B')).toBeLessThan(sorted.indexOf('RED-01-A'))
    expect(sorted.indexOf('BLUE-01-B')).toBeLessThan(sorted.indexOf('BLUE-01-A'))
  })

  it('SORT-004 places jokers at the right for both modes', () => {
    expect(sortRackForGroup777(rack, serverOrderIds).at(-1)?.joker).toBe(true)
    expect(sortRackForRun789(rack, serverOrderIds).at(-1)?.joker).toBe(true)
  })

  it('DRAG-001~003 moves first, last, and same-position tiles without loss', () => {
    expect(moveTileId(['a', 'b', 'c'], 'a', 2)).toEqual(['b', 'c', 'a'])
    expect(moveTileId(['a', 'b', 'c'], 'c', 0)).toEqual(['c', 'a', 'b'])
    expect(moveTileId(['a', 'b', 'c'], 'b', 1)).toEqual(['a', 'b', 'c'])
  })

  it('SORT-008 preserves count, uniqueness, and the authoritative tileId set', () => {
    const sortedIds = sortRackForGroup777(rack, serverOrderIds).map((tile) => tile.tileId)
    expect(hasRackOrderInvariant(rack, sortedIds)).toBe(true)
    expect(hasRackOrderInvariant(rack, [...sortedIds, sortedIds[0]!])).toBe(false)
  })
})


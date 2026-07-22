import { describe, expect, it } from 'vitest'

import { buildRackVisualGroups } from '@/domain/game/rackVisualGroups'
import type { GameRackTile, GameTileColor } from '@/types/game'

function tile(color: GameTileColor, number: number, copy = 'A'): GameRackTile {
  return { tileId: `${color}-${String(number).padStart(2, '0')}-${copy}`, tileType: 'NUMBER', color, number, joker: false, positionOrder: 0 }
}
const joker: GameRackTile = { tileId: 'JOKER-A', tileType: 'JOKER', color: null, number: null, joker: true, positionOrder: 99 }

describe('Phase 7 rack visual groups', () => {
  it('GROUP-HOLD-001 RUN_789 resolves 7 hold to 7-8-9', () => {
    const rack = [tile('RED', 7), tile('RED', 8), tile('RED', 9)]
    expect(buildRackVisualGroups(rack, 'RUN_789', rack.map((item) => item.tileId))[0]?.tileIds)
      .toEqual(rack.map((item) => item.tileId))
  })

  it('GROUP-HOLD-002 GROUP_777 uses three different colors', () => {
    const rack = [tile('RED', 7), tile('BLUE', 7), tile('YELLOW', 7)]
    expect(buildRackVisualGroups(rack, 'GROUP_777', rack.map((item) => item.tileId))[0]?.tileIds)
      .toEqual(rack.map((item) => item.tileId))
  })

  it('GROUP-HOLD-003 keeps a long run as one maximal group', () => {
    const rack = [6, 7, 8, 9, 10].map((number) => tile('BLACK', number))
    expect(buildRackVisualGroups(rack, 'RUN_789', rack.map((item) => item.tileId))[0]?.tileIds).toHaveLength(5)
  })

  it('GROUP-HOLD-004 assigns duplicate copies to deterministic receive-order lanes', () => {
    const rack = [7, 8, 9].flatMap((number) => [tile('BLUE', number, 'B'), tile('BLUE', number, 'A')])
    const order = rack.map((item) => item.tileId)
    const groups = buildRackVisualGroups([...rack].reverse(), 'RUN_789', order)
    expect(groups.map((group) => group.tileIds)).toEqual([
      ['BLUE-07-B', 'BLUE-08-B', 'BLUE-09-B'],
      ['BLUE-07-A', 'BLUE-08-A', 'BLUE-09-A'],
    ])
  })

  it('GROUP-HOLD-005 excludes jokers from automatic visual groups', () => {
    const rack = [tile('RED', 7), tile('RED', 8), tile('RED', 9), joker]
    expect(buildRackVisualGroups(rack, 'RUN_789', rack.map((item) => item.tileId))
      .flatMap((group) => group.tileIds)).not.toContain('JOKER-A')
  })
})

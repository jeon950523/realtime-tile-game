import { mount } from '@vue/test-utils'
import { effectScope, ref } from 'vue'
import { describe, expect, it, vi } from 'vitest'

import GameBoard from '@/components/game/GameBoard.vue'
import WorkingTableBoard from '@/components/game/WorkingTableBoard.vue'
import { useWorkingTable } from '@/composables/game/useWorkingTable'
import {
  canPlaceTableBlockWithGutter,
  flowCommittedTableMelds,
  flowTableBlocks,
  resolveNearestTableCoordinate,
  tableContentRows,
} from '@/domain/game/tableFlow'
import { deriveTableCandidates } from '@/domain/game/tableCandidateDerivation'
import { validateTurnDraft } from '@/domain/game/turnDraftValidation'
import type { GameRackTile, GameTableMeld, GameTableTile } from '@/types/game'
import type { WorkingTilePlacement } from '@/types/turnDraft'

function rackTile(number: number, copy = 'A'): GameRackTile {
  return {
    tileId: `RED-${String(number).padStart(2, '0')}-${copy}`,
    tileType: 'NUMBER', color: 'RED', number, joker: false, positionOrder: number,
  }
}

function tableMeld(
  meldId: string,
  numbers: number[],
  gridRow: number,
  gridColumn: number,
  positionOrder: number,
): GameTableMeld {
  return {
    meldId,
    meldType: 'RUN',
    score: numbers.reduce((sum, number) => sum + number, 0),
    positionOrder,
    gridRow,
    gridColumn,
    tiles: numbers.map((number, index): GameTableTile => ({
      ...rackTile(number, meldId.slice(0, 1)),
      positionOrder: index,
    })),
  }
}

function placement(tileId: string, gridRow: number, gridColumn: number): WorkingTilePlacement {
  return {
    tileId, gridRow, gridColumn,
    source: 'CURRENT_PLAYER_RACK', sourceMeldId: null, originalPositionOrder: null,
  }
}

describe('Phase 7 production closeout table flow', () => {
  it('P7-CLOSE-001 fully flows committed melds with a deterministic one-cell gutter', () => {
    const flowed = flowCommittedTableMelds([
      tableMeld('b-meld', [4, 5, 6], 7, 12, 1),
      tableMeld('a-meld', [1, 2, 3], 3, 9, 0),
      tableMeld('c-meld', [7, 8, 9], 9, 2, 2),
    ])

    expect(flowed.map((meld) => [meld.meldId, meld.gridRow, meld.gridColumn])).toEqual([
      ['a-meld', 0, 0],
      ['b-meld', 0, 4],
      ['c-meld', 0, 8],
    ])
  })

  it('P7-CLOSE-002 flows 100 singleton blocks into finite extra rows without overlap or gutter violations', () => {
    const flowed = flowTableBlocks(Array.from({ length: 100 }, (_, index) => ({
      blockId: `block-${index}`,
      tileIds: [`tile-${index}`],
    })))

    expect(flowed).toHaveLength(100)
    expect(Math.max(...flowed.map((block) => block.gridRow))).toBe(11)

    const occupied = new Set<string>()
    for (const block of flowed) {
      const cell = `${block.gridRow}:${block.gridColumn}`
      expect(occupied.has(cell)).toBe(false)
      occupied.add(cell)
    }
    const byRow = new Map<number, typeof flowed>()
    for (const block of flowed) {
      const rowBlocks = byRow.get(block.gridRow) ?? []
      rowBlocks.push(block)
      byRow.set(block.gridRow, rowBlocks)
    }
    for (const rowBlocks of byRow.values()) {
      const columns = rowBlocks.map((block) => block.gridColumn).sort((left, right) => left - right)
      for (let index = 1; index < columns.length; index += 1) {
        expect(columns[index]! - columns[index - 1]!).toBeGreaterThanOrEqual(2)
      }
    }
  })

  it('P7-CLOSE-003 nudges right then wraps to the next row and rejects coordinates outside the logical board', () => {
    const stationary = [
      placement('a', 0, 0), placement('b', 0, 1), placement('c', 0, 2),
    ]

    expect(canPlaceTableBlockWithGutter(stationary, ['x', 'y', 'z'], 0, 3)).toBe(false)
    expect(resolveNearestTableCoordinate(stationary, ['x', 'y', 'z'], 0, 2))
      .toEqual({ gridRow: 0, gridColumn: 4 })
    expect(resolveNearestTableCoordinate(stationary, ['x', 'y', 'z'], 0, 17))
      .toEqual({ gridRow: 1, gridColumn: 0 })
    expect(resolveNearestTableCoordinate(stationary, ['x'], 18, 0)).toBeNull()
    expect(resolveNearestTableCoordinate(stationary, ['x'], 0, 18)).toBeNull()
  })

  it('P7-CLOSE-004 keeps an eight-row viewport while content and the bottom drop row expand', () => {
    const rack = [rackTile(1), rackTile(2), rackTile(3)]
    const placements = rack.map((tile, index) => placement(tile.tileId, 10, index))
    const candidates = deriveTableCandidates(placements)
    const wrapper = mount(WorkingTableBoard, { props: {
      placements,
      rack,
      baselineMelds: [],
      validation: validateTurnDraft(candidates, rack, true, [], placements),
      initialMeldCompleted: true,
      isMeldEditable: () => true,
    } })

    expect(tableContentRows(placements)).toBe(12)
    expect(wrapper.get('.working-table-grid').attributes('data-visible-rows')).toBe('8')
    expect(wrapper.get('.working-table-grid__canvas').attributes('data-content-rows')).toBe('12')
    expect(wrapper.get<HTMLElement>('.working-table-bottom-drop-row').element.style
      .getPropertyValue('--table-grid-row')).toBe('11')
  })

  it('P7-CLOSE-005 edge auto-scroll moves the fixed viewport during an external rack drag', () => {
    const rack = [rackTile(1), rackTile(2), rackTile(3)]
    const placements = rack.map((tile, index) => placement(tile.tileId, 10, index))
    const candidates = deriveTableCandidates(placements)
    const wrapper = mount(WorkingTableBoard, { props: {
      placements,
      rack,
      baselineMelds: [],
      validation: validateTurnDraft(candidates, rack, true, [], placements),
      initialMeldCompleted: true,
      isMeldEditable: () => true,
    } })
    const viewport = wrapper.get<HTMLElement>('.working-table-grid').element
    const canvas = wrapper.get<HTMLElement>('.working-table-grid__canvas').element
    viewport.getBoundingClientRect = () => ({
      left: 0, top: 0, right: 1044, bottom: 100, width: 1044, height: 100,
      x: 0, y: 0, toJSON: () => ({}),
    } as DOMRect)
    canvas.getBoundingClientRect = () => ({
      left: 0, top: 0, right: 1044, bottom: 816, width: 1044, height: 816,
      x: 0, y: 0, toJSON: () => ({}),
    } as DOMRect)
    Object.defineProperty(viewport, 'clientHeight', { configurable: true, value: 100 })
    Object.defineProperty(viewport, 'scrollHeight', { configurable: true, value: 816 })
    let frame: FrameRequestCallback | null = null
    const request = vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      frame = callback
      return 1
    })
    const cancel = vi.spyOn(window, 'cancelAnimationFrame').mockImplementation(() => {})

    try {
      const exposed = wrapper.vm as unknown as {
        resolveRackDropTarget: (clientX: number, clientY: number, tileCount: number) => unknown
        finishExternalRackDrag: () => void
      }
      exposed.resolveRackDropTarget(900, 96, 1)
      expect(request).toHaveBeenCalledTimes(1)
      expect(frame).not.toBeNull()
      const callback = frame as FrameRequestCallback | null
      if (callback) callback(performance.now())
      expect(viewport.scrollTop).toBe(12)
      exposed.finishExternalRackDrag()
      expect(cancel).toHaveBeenCalled()
    } finally {
      wrapper.unmount()
      request.mockRestore()
      cancel.mockRestore()
    }
  })

  it('P7-CLOSE-006 preserves committed-tile provenance and nudges the following meld after a table-to-meld move', () => {
    const scope = effectScope()
    const table = ref<GameTableMeld[]>([
      tableMeld('a-meld', [1, 2, 3], 0, 0, 0),
      tableMeld('b-meld', [4, 5, 6], 0, 4, 1),
      tableMeld('c-meld', [7, 8, 9], 0, 8, 2),
    ])
    const working = scope.run(() => useWorkingTable({
      authoritativeRack: ref<GameRackTile[]>([]),
      authoritativeVersion: ref(1),
      authoritativeSyncRevision: ref(1),
      tableMelds: table,
      initialMeldCompleted: ref(true),
      isMyTurn: ref(true),
    }))!

    expect(working.moveTile('RED-03-a', 'b-meld', 3)).toBe(true)
    const moved = working.workingTable.value!.placements.find((item) => item.tileId === 'RED-03-a')
    const target = working.workingTable.value!.melds.find((meld) => (
      ['RED-04-b', 'RED-05-b', 'RED-06-b'].every((tileId) => meld.tileIds.includes(tileId))
    ))
    const following = working.workingTable.value!.melds.find((meld) => (
      ['RED-07-c', 'RED-08-c', 'RED-09-c'].every((tileId) => meld.tileIds.includes(tileId))
    ))
    expect(moved?.source).toBe('COMMITTED_TABLE')
    expect(moved?.sourceMeldId).toBe('a-meld')
    expect(target?.gridColumn).toBe(4)
    expect(target?.tileIds).toContain('RED-03-a')
    expect(following?.gridColumn).toBe(9)
    scope.stop()
  })

  it('P7-CLOSE-007 bounds Undo history to the latest 100 local transactions', () => {
    const scope = effectScope()
    const table = ref<GameTableMeld[]>([
      tableMeld('11111111-1111-4111-8111-111111111111', [1, 2, 3], 0, 0, 0),
    ])
    const working = scope.run(() => useWorkingTable({
      authoritativeRack: ref<GameRackTile[]>([]),
      authoritativeVersion: ref(1),
      authoritativeSyncRevision: ref(1),
      tableMelds: table,
      initialMeldCompleted: ref(true),
      isMyTurn: ref(true),
    }))!

    const meldId = working.workingTable.value!.melds[0]!.clientMeldId
    for (let index = 0; index < 105; index += 1) {
      const row = index % 2 === 0 ? 2 : 3
      expect(working.moveMeld(meldId, row, 0)).toBe(true)
    }
    expect(working.workingTable.value!.history).toHaveLength(100)
    scope.stop()
  })

  it('P7-CLOSE-008 renders a strong current-turn indicator only for the active player', async () => {
    const active = mount(GameBoard, { props: {
      gameId: 1,
      gameMode: 'CLASSIC',
      gameStatus: 'IN_PROGRESS',
      connectionState: 'CONNECTED',
      isMyTurn: true,
      assets: {},
    } })
    expect(active.find('.game-board__turn-indicator').text()).toContain('내 턴')
    expect(active.classes()).toContain('game-board--my-turn')

    await active.setProps({ isMyTurn: false })
    expect(active.find('.game-board__turn-indicator').exists()).toBe(false)
    expect(active.classes()).not.toContain('game-board--my-turn')
  })
})

import { mount } from '@vue/test-utils'
import { effectScope, nextTick, ref } from 'vue'
import { describe, expect, it } from 'vitest'

import WorkingTableBoard from '@/components/game/WorkingTableBoard.vue'
import { useWorkingTable } from '@/composables/game/useWorkingTable'
import { TABLE_GRID_COLUMNS, TABLE_GRID_ROWS } from '@/domain/game/tableGrid'
import { validateTurnDraft } from '@/domain/game/turnDraftValidation'
import type { GameRackTile, GameTableMeld, GameTableTile } from '@/types/game'

function rackTile(color: GameRackTile['color'], number: number, copy = 'B'): GameRackTile {
  return {
    tileId: `${color}-${String(number).padStart(2, '0')}-${copy}`,
    tileType: 'NUMBER', color, number, joker: false, positionOrder: number,
  }
}

function tableMeld(
  meldId: string,
  color: GameRackTile['color'],
  numbers: number[],
  gridRow: number,
  gridColumn: number,
  positionOrder: number,
): GameTableMeld {
  return {
    meldId, meldType: 'RUN', score: numbers.reduce((sum, number) => sum + number, 0),
    positionOrder, gridRow, gridColumn,
    tiles: numbers.map((number, index): GameTableTile => ({
      ...rackTile(color, number, 'A'), positionOrder: index,
    })),
  }
}

const firstId = '11111111-1111-4111-8111-111111111111'
const secondId = '22222222-2222-4222-8222-222222222222'

function fixture() {
  const scope = effectScope()
  const rack = ref<GameRackTile[]>([
    rackTile('RED', 7), rackTile('RED', 8), rackTile('BLUE', 7), rackTile('YELLOW', 7),
  ])
  const table = ref<GameTableMeld[]>([
    tableMeld(firstId, 'RED', [1, 2, 3], 0, 0, 0),
    tableMeld(secondId, 'BLUE', [4, 5, 6], 0, 13, 1),
  ])
  const working = scope.run(() => useWorkingTable({
    authoritativeRack: rack,
    authoritativeVersion: ref(4),
    authoritativeSyncRevision: ref(1),
    tableMelds: table,
    initialMeldCompleted: ref(true),
    isMyTurn: ref(true),
  }))!
  return { scope, rack, table, working }
}

describe('Phase 7 final closure Stage B grid working table', () => {
  it('FE-P7B-001 moves a whole meld to a free logical coordinate', () => {
    const { scope, working } = fixture()
    expect(working.moveMeld(firstId, 3, 6)).toBe(true)
    expect(working.workingTable.value?.melds.find((meld) => meld.tileIds.includes('RED-01-A')))
      .toMatchObject({ gridRow: 3, gridColumn: 6 })
    scope.stop()
  })

  it('FE-P7B-002 allows an individual rack tile as an INVALID draft meld', () => {
    const { scope, rack, working } = fixture()
    expect(working.addAsNewMeld([rack.value[0]!.tileId], rack.value.map((tile) => tile.tileId), 2, 4)).toBe(true)
    const added = working.workingTable.value!.melds.at(-1)!
    expect(added).toMatchObject({ tileIds: [rack.value[0]!.tileId], gridRow: 2, gridColumn: 4 })
    expect(working.validation.value.melds[added.clientMeldId]?.kind).toBe('INVALID')
    scope.stop()
  })

  it('FE-P7B-003 allows a two-tile incomplete draft meld', () => {
    const { scope, rack, working } = fixture()
    expect(working.addAsNewMeld(rack.value.slice(0, 2).map((tile) => tile.tileId), rack.value.map((tile) => tile.tileId), 2, 7)).toBe(true)
    const added = working.workingTable.value!.melds.at(-1)!
    expect(added.tileIds).toHaveLength(2)
    expect(working.validation.value.melds[added.clientMeldId]?.valid).toBe(false)
    scope.stop()
  })

  it('FE-P7B-004 nudges an overlapping meld right to the nearest gutter-safe slot', () => {
    const { scope, working } = fixture()
    expect(working.moveMeld(secondId, 3, 10)).toBe(true)
    expect(working.moveMeld(secondId, 0, 2)).toBe(true)
    expect(working.workingTable.value?.melds[1]).toMatchObject({ gridRow: 0, gridColumn: 4 })
    scope.stop()
  })

  it('FE-P7B-005 blocks a row outside the logical grid and wraps an edge drop', () => {
    const { scope, working } = fixture()
    expect(working.moveMeld(firstId, TABLE_GRID_ROWS, 0)).toBe(false)
    expect(working.moveMeld(firstId, 0, TABLE_GRID_COLUMNS)).toBe(false)
    expect(working.moveMeld(firstId, 0, TABLE_GRID_COLUMNS - 2)).toBe(true)
    expect(working.workingTable.value?.melds.find((meld) => meld.tileIds.includes('RED-01-A')))
      .toMatchObject({ gridRow: 1, gridColumn: 0 })
    scope.stop()
  })

  it('FE-P7B-006 separates one table tile into its own positioned draft meld', () => {
    const { scope, working } = fixture()
    const tileId = working.workingTable.value!.melds[0]!.tileIds[2]!
    expect(working.moveAsNewMeld(tileId, 2, 5)).toBe(true)
    expect(working.workingTable.value!.melds).toContainEqual(expect.objectContaining({
      tileIds: [tileId], gridRow: 2, gridColumn: 5,
    }))
    scope.stop()
  })

  it('FE-P7B-007 merges two melds while retaining the target coordinate', () => {
    const { scope, working } = fixture()
    expect(working.mergeMelds(secondId, firstId)).toBe(true)
    expect(working.workingTable.value!.melds).toHaveLength(1)
    expect(working.workingTable.value!.melds[0]).toMatchObject({
      gridRow: 0, gridColumn: 0,
    })
    expect(working.workingTable.value!.melds[0]!.tileIds).toHaveLength(6)
    scope.stop()
  })

  it('FE-P7B-008 restores coordinates in an undo snapshot', () => {
    const { scope, working } = fixture()
    working.moveMeld(firstId, 4, 8)
    expect(working.undo()).toBe(true)
    expect(working.workingTable.value?.melds[0]).toMatchObject({ gridRow: 0, gridColumn: 0 })
    scope.stop()
  })

  it('FE-P7B-009 restores committed coordinates on cancel', () => {
    const { scope, working } = fixture()
    working.moveMeld(secondId, 5, 9)
    working.cancel()
    expect(working.workingTable.value?.melds[1]).toMatchObject({ gridRow: 0, gridColumn: 4 })
    scope.stop()
  })

  it('shows no persistent grid cells and renders a weak preview only during drag', async () => {
    const { scope, rack, table, working } = fixture()
    const melds = working.workingTable.value!.melds
    const wrapper = mount(WorkingTableBoard, { props: {
      melds,
      rack: rack.value,
      baselineMelds: table.value,
      validation: validateTurnDraft(melds, rack.value, true, table.value),
      initialMeldCompleted: true,
      isMeldEditable: () => true,
    } })
    expect(wrapper.findAll('.working-table-grid-cell')).toHaveLength(0)
    expect(wrapper.find('.working-table-drop-preview').exists()).toBe(false)
    const grid = wrapper.find<HTMLElement>('.working-table-grid__canvas')
    grid.element.getBoundingClientRect = () => ({
      left: 0, top: 0, width: 1508, height: 400, right: 1508, bottom: 400,
      x: 0, y: 0, toJSON: () => ({}),
    } as DOMRect)
    const data = new Map<string, string>()
    const dataTransfer = {
      types: ['application/x-working-meld-id'],
      effectAllowed: 'move',
      setData: (type: string, value: string) => data.set(type, value),
      getData: (type: string) => data.get(type) ?? '',
    }
    const tile = wrapper.find('.working-table-tile')
    await tile.trigger('dragstart', { dataTransfer, shiftKey: true })
    await grid.trigger('dragover', { dataTransfer, clientX: 420, clientY: 180 })
    await new Promise<void>((resolve) => window.requestAnimationFrame(() => resolve()))
    await nextTick()
    expect(wrapper.find('.working-table-drop-preview').exists()).toBe(true)
    await tile.trigger('dragend', { dataTransfer })
    expect(wrapper.find('.working-table-drop-preview').exists()).toBe(false)
    scope.stop()
  })

  it('FE-P7B-010 shows and immediately clears Rack-to-Table drop guidance', async () => {
    const { scope, rack, table, working } = fixture()
    const melds = working.workingTable.value!.melds
    const wrapper = mount(WorkingTableBoard, { props: {
      melds,
      rack: rack.value,
      baselineMelds: table.value,
      validation: validateTurnDraft(melds, rack.value, true, table.value),
      initialMeldCompleted: true,
      isMeldEditable: () => true,
      rackDropPreview: {
        target: { kind: 'WORKING_NEW_MELD', gridRow: 3, gridColumn: 4 },
        tileCount: 2,
      },
    } })

    expect(wrapper.find('.working-table-drop-preview').exists()).toBe(true)
    await wrapper.setProps({ rackDropPreview: null })
    expect(wrapper.find('.working-table-drop-preview').exists()).toBe(false)

    await wrapper.setProps({
      rackDropPreview: {
        target: { kind: 'WORKING_EXISTING_MELD', meldId: firstId },
        tileCount: 1,
      },
    })
    expect(wrapper.findAll('.working-table-grid__meld--drop-preview')).toHaveLength(1)
    await wrapper.setProps({ rackDropPreview: null })
    expect(wrapper.findAll('.working-table-grid__meld--drop-preview')).toHaveLength(0)
    scope.stop()
  })
})

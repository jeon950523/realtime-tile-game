import { mount } from '@vue/test-utils'
import { effectScope, ref } from 'vue'
import { describe, expect, it } from 'vitest'

import WorkingTableBoard from '@/components/game/WorkingTableBoard.vue'
import { useWorkingTable } from '@/composables/game/useWorkingTable'
import { validateTurnDraft } from '@/domain/game/turnDraftValidation'
import type { GameRackTile, GameTableMeld, GameTableTile } from '@/types/game'

const originalMeldId = '11111111-1111-4111-8111-111111111111'

function numberTile(color: GameRackTile['color'], number: number, copy = 'A'): GameRackTile {
  return {
    tileId: `${color}-${String(number).padStart(2, '0')}-${copy}`,
    tileType: 'NUMBER', color, number, joker: false, positionOrder: number,
  }
}

function jokerTile(): GameTableTile {
  return {
    tileId: 'JOKER-A', tileType: 'JOKER', color: null, number: null,
    joker: true, positionOrder: 1,
  }
}

function fixture() {
  const scope = effectScope()
  const rack = ref<GameRackTile[]>([
    numberTile('RED', 4), numberTile('BLUE', 8), numberTile('BLUE', 9),
  ])
  const baseline = ref<GameTableMeld[]>([{
    meldId: originalMeldId,
    meldType: 'RUN',
    score: 12,
    positionOrder: 0,
    gridRow: 0,
    gridColumn: 0,
    tiles: [
      { ...numberTile('RED', 3), positionOrder: 0 },
      jokerTile(),
      { ...numberTile('RED', 5), positionOrder: 2 },
    ],
  }])
  const working = scope.run(() => useWorkingTable({
    authoritativeRack: rack,
    authoritativeVersion: ref(7),
    authoritativeSyncRevision: ref(1),
    tableMelds: baseline,
    initialMeldCompleted: ref(true),
    isMyTurn: ref(true),
  }))!
  const displayOrder = rack.value.map((tile) => tile.tileId)
  return { scope, rack, baseline, working, displayOrder }
}

describe('Phase 7 final closure Stage C Joker recomposition', () => {
  it('FE-P7C-001 removes the temporary Joker meld lock', () => {
    const { scope, rack, baseline, working } = fixture()
    const placements = working.placements.value
    const wrapper = mount(WorkingTableBoard, { props: {
      placements,
      rack: rack.value,
      baselineMelds: baseline.value,
      validation: validateTurnDraft(working.candidates.value, rack.value, true, baseline.value, placements),
      initialMeldCompleted: true,
      isMeldEditable: working.isMeldEditable,
    } })

    expect(working.isMeldEditable(originalMeldId)).toBe(true)
    expect(wrapper.text()).not.toContain('Joker Meld 편집 잠금')
    expect(wrapper.find('.working-table-tile').attributes('draggable')).toBe('true')
    scope.stop()
  })

  it('FE-P7C-002 drags a Joker from its committed meld into another draft meld', () => {
    const { scope, working, displayOrder } = fixture()
    expect(working.addAsNewMeld(['BLUE-08-A', 'BLUE-09-A'], displayOrder, 2, 0)).toBe(true)
    const target = working.candidates.value.at(-1)!

    expect(working.moveTile('JOKER-A', target.clientMeldId, 0)).toBe(true)
    expect(working.candidates.value.find((meld) => meld.tileIds.includes('BLUE-08-A'))?.tileIds)
      .toEqual(['JOKER-A', 'BLUE-08-A', 'BLUE-09-A'])
    scope.stop()
  })

  it('FE-P7C-003 separates a Joker into an individual positioned draft', () => {
    const { scope, working } = fixture()
    expect(working.moveAsNewMeld('JOKER-A', 3, 5)).toBe(true)
    expect(working.candidates.value).toContainEqual(expect.objectContaining({
      tileIds: ['JOKER-A'], gridRow: 3, gridColumn: 5,
    }))
    scope.stop()
  })

  it('FE-P7C-004 merges a separated Joker draft into another meld', () => {
    const { scope, working, displayOrder } = fixture()
    expect(working.moveAsNewMeld('JOKER-A', 3, 5)).toBe(true)
    const jokerMeld = working.candidates.value.find((meld) => meld.tileIds.includes('JOKER-A'))!
    expect(working.addAsNewMeld(['BLUE-08-A', 'BLUE-09-A'], displayOrder, 2, 0)).toBe(true)
    const target = working.candidates.value.find((meld) => meld.tileIds.includes('BLUE-08-A'))!

    expect(working.mergeMelds(jokerMeld.clientMeldId, target.clientMeldId)).toBe(true)
    expect(working.candidates.value.find((meld) => meld.tileIds.includes('BLUE-08-A'))?.tileIds)
      .toEqual(['BLUE-08-A', 'BLUE-09-A', 'JOKER-A'])
    scope.stop()
  })

  it('FE-P7C-005 allows an intermediate individual Joker and marks it INVALID', () => {
    const { scope, working } = fixture()
    expect(working.moveAsNewMeld('JOKER-A', 3, 5)).toBe(true)
    const jokerMeld = working.candidates.value.find((meld) => meld.tileIds.includes('JOKER-A'))!
    expect(working.validation.value.melds[jokerMeld.clientMeldId]).toMatchObject({
      kind: 'INVALID', valid: false,
    })
    expect(working.validation.value.canCommit).toBe(false)
    scope.stop()
  })

  it('FE-P7C-006 enables Commit preview after exact replacement and same-turn reuse', () => {
    const { scope, working, displayOrder } = fixture()
    expect(working.addToMeld(originalMeldId, ['RED-04-A'], displayOrder, 1)).toBe(true)
    expect(working.addAsNewMeld(['BLUE-08-A', 'BLUE-09-A'], displayOrder, 2, 0)).toBe(true)
    const reused = working.candidates.value.at(-1)!
    expect(working.moveTile('JOKER-A', reused.clientMeldId, 0)).toBe(true)

    expect(working.candidates.value[0]!.tileIds)
      .toEqual(['RED-03-A', 'RED-04-A', 'RED-05-A'])
    expect(working.candidates.value.find((meld) => meld.tileIds.includes('BLUE-08-A'))?.tileIds)
      .toEqual(['JOKER-A', 'BLUE-08-A', 'BLUE-09-A'])
    expect(working.validation.value.invalidCount).toBe(0)
    expect(working.validation.value.rackContributionCount).toBe(3)
    expect(working.validation.value.canCommit).toBe(true)
    scope.stop()
  })
})

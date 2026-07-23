import { effectScope, nextTick, ref, type EffectScope, type Ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'

import { useTurnDraft } from '@/composables/game/useTurnDraft'
import { hasDraftPartitionInvariant, validateDraftMeld, validateTurnDraft } from '@/domain/game/turnDraftValidation'
import type { GameRackTile, GameTableMeld, GameTileColor } from '@/types/game'

function tile(color: GameTileColor, number: number, copy = 'A'): GameRackTile {
  return { tileId: `${color}-${String(number).padStart(2, '0')}-${copy}`, tileType: 'NUMBER', color, number, joker: false, positionOrder: 0 }
}

const rack = [
  tile('RED', 7), tile('RED', 8), tile('RED', 9),
  tile('BLUE', 1), tile('BLUE', 2), tile('BLUE', 3),
  tile('RED', 1), tile('YELLOW', 1),
]

describe('Phase 7 TurnDraft domain', () => {
  let scope: EffectScope
  let version: Ref<number>
  let syncRevision: Ref<number>
  let table: Ref<GameTableMeld[]>
  let initialCompleted: Ref<boolean>
  let draft: ReturnType<typeof useTurnDraft>
  const order = rack.map((item) => item.tileId)

  beforeEach(() => {
    scope = effectScope()
    version = ref(0)
    syncRevision = ref(0)
    table = ref<GameTableMeld[]>([])
    initialCompleted = ref(false)
    draft = scope.run(() => useTurnDraft({
      authoritativeRack: ref(rack),
      authoritativeVersion: version,
      authoritativeSyncRevision: syncRevision,
      tableMelds: table,
      initialMeldCompleted: initialCompleted,
    }))!
  })
  afterEach(() => scope.stop())

  it('DRAFT-001 group drop creates one new meld', () => {
    expect(draft.addAsNewMeld(order.slice(0, 3), order)).toBe(true)
    expect(draft.candidates.value).toHaveLength(1)
    expect(draft.candidates.value[0]?.tileIds).toEqual(order.slice(0, 3))
  })

  it('DRAFT-002 single drop creates an invalid one-tile meld', () => {
    draft.addAsNewMeld([order[0]!], order)
    expect(draft.validation.value.melds[draft.candidates.value[0]!.clientMeldId]?.valid).toBe(false)
  })

  it('DRAFT-003 adds a rack tile to an existing meld', () => {
    draft.addAsNewMeld(order.slice(0, 2), order)
    const id = draft.candidates.value[0]!.clientMeldId
    draft.addToMeld(id, [order[2]!], order)
    expect(draft.candidates.value[0]!.tileIds).toEqual(order.slice(0, 3))
  })

  it('DRAFT-004 changes tile order inside a draft meld', () => {
    draft.addAsNewMeld(order.slice(0, 3), order)
    const id = draft.candidates.value[0]!.clientMeldId
    draft.reorderTile(id, 2, 0)
    expect(draft.candidates.value[0]!.tileIds[0]).toBe(order[2])
  })

  it('DRAFT-005 moves a tile between draft melds', () => {
    draft.addAsNewMeld(order.slice(0, 3), order)
    draft.addAsNewMeld(order.slice(3, 6), order)
    const [left, right] = draft.candidates.value
    draft.moveTile(left!.tileIds[0]!, right!.clientMeldId)
    expect(draft.candidates.value[1]!.tileIds).toContain(order[0])
  })

  it('DRAFT-006 returns a draft tile to the visible rack', () => {
    draft.addAsNewMeld(order.slice(0, 3), order)
    draft.returnTile(order[0]!)
    expect(draft.visibleRackTiles.value.map((item) => item.tileId)).toContain(order[0])
  })

  it('DRAFT-007 undoes the last operation', () => {
    draft.addAsNewMeld(order.slice(0, 3), order)
    draft.returnTile(order[0]!)
    expect(draft.undo()).toBe(true)
    expect(draft.candidates.value[0]!.tileIds).toEqual(order.slice(0, 3))
  })

  it('DRAFT-008 full cancel returns the exact source display order', () => {
    draft.addAsNewMeld(order.slice(0, 3), [...order].reverse())
    expect(draft.cancel()).toEqual([...order].reverse())
    expect(draft.candidates.value).toEqual([])
  })

  it('DRAFT-009 preserves the authoritative rack partition', () => {
    draft.addAsNewMeld(order.slice(0, 3), order)
    expect(draft.partitionPreserved.value).toBe(true)
    expect(hasDraftPartitionInvariant(order, order.slice(3), draft.candidates.value)).toBe(true)
  })

  it('DRAFT-010 rejects duplicate tiles across melds', () => {
    draft.addAsNewMeld(order.slice(0, 3), order)
    expect(draft.addAsNewMeld([order[0]!], order)).toBe(false)
  })

  it('DRAFT-011 removes an empty meld automatically', () => {
    draft.addAsNewMeld([order[0]!], order)
    draft.returnTile(order[0]!)
    expect(draft.candidates.value).toEqual([])
  })

  it('VALIDATION-001 recognizes an ordered RUN and score', () => {
    const result = validateDraftMeld({ clientMeldId: 'm', tileIds: order.slice(0, 3) }, new Map(rack.map((item) => [item.tileId, item])))
    expect(result).toMatchObject({ kind: 'RUN', valid: true, score: 24 })
  })

  it('VALIDATION-002 recognizes a same-number different-color GROUP', () => {
    const groupIds = [order[3]!, order[6]!, order[7]!]
    const result = validateDraftMeld({ clientMeldId: 'm', tileIds: groupIds }, new Map(rack.map((item) => [item.tileId, item])))
    expect(result).toMatchObject({ kind: 'GROUP', valid: true, score: 3 })
  })

  it('VALIDATION-003 rejects a non-consecutive meld', () => {
    const result = validateDraftMeld({ clientMeldId: 'm', tileIds: [order[0]!, order[1]!, order[5]!] }, new Map(rack.map((item) => [item.tileId, item])))
    expect(result.kind).toBe('INVALID')
  })

  it('VALIDATION-004 adds 789 and 123 to exactly thirty', () => {
    const result = validateTurnDraft([
      { clientMeldId: 'a', tileIds: order.slice(0, 3) },
      { clientMeldId: 'b', tileIds: order.slice(3, 6) },
    ], rack, false)
    expect(result).toMatchObject({ totalScore: 30, canCommit: true })
  })

  it('VALIDATION-005 disables commit below thirty before initial meld', () => {
    const result = validateTurnDraft([{ clientMeldId: 'a', tileIds: order.slice(0, 3) }], rack, false)
    expect(result).toMatchObject({ totalScore: 24, canCommit: false })
  })

  it('VALIDATION-006 removes the thirty point gate after initial meld', () => {
    const result = validateTurnDraft([{ clientMeldId: 'a', tileIds: order.slice(0, 3) }], rack, true)
    expect(result.canCommit).toBe(true)
  })

  it('VALIDATION-007 requires authoritative Tile Placement coordinates to lock an existing table before first registration', () => {
    const baselineTiles = [tile('BLACK', 1, 'B'), tile('BLACK', 2, 'B'), tile('BLACK', 3, 'B')]
    const baseline: GameTableMeld[] = [{
      meldId: 'baseline', meldType: 'RUN', score: 6, positionOrder: 0,
      gridRow: 4, gridColumn: 6,
      tiles: baselineTiles.map((item, positionOrder) => ({ ...item, positionOrder })),
    }]
    const candidates = [
      { clientMeldId: 'baseline', sourceMeldId: 'baseline', tileIds: baselineTiles.map((item) => item.tileId) },
      { clientMeldId: 'new', sourceMeldId: null, tileIds: order.slice(0, 3) },
    ]

    const result = validateTurnDraft(candidates, rack, false, baseline, [])

    expect(result.baselinePreserved).toBe(true)
    expect(result.canCommit).toBe(false)
    expect(result.reason).toBe('첫 등록 전에는 기존 Table을 변경할 수 없습니다.')
  })

  it('does not clear a pending draft on an early public event, then resolves on private sync', async () => {
    draft.addAsNewMeld(order.slice(0, 3), order)
    const meld = draft.candidates.value[0]!
    draft.markCommitPending('action')
    table.value = [{ meldId: meld.clientMeldId, meldType: 'RUN', score: 24, positionOrder: 0, gridRow: 0, gridColumn: 0, tiles: [] }]
    version.value = 1
    await nextTick()
    expect(draft.draft.value).not.toBeNull()
    syncRevision.value += 1
    await nextTick()
    expect(draft.lastResolution.value).toBe('STALE')
  })
})

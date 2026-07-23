import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { effectScope, ref } from 'vue'
import { beforeEach, describe, expect, it } from 'vitest'

import CommittedTableBoard from '@/components/game/CommittedTableBoard.vue'
import TurnPreviewBoard from '@/components/game/TurnPreviewBoard.vue'
import WorkingTableBoard from '@/components/game/WorkingTableBoard.vue'
import { committedMeldsToPlacements, useWorkingTable } from '@/composables/game/useWorkingTable'
import { buildRackAdjacentGroups, buildRackVisualGroups } from '@/domain/game/rackVisualGroups'
import {
  clientPointToTableGridCoordinate,
  TABLE_GRID_CANVAS_HEIGHT_PX,
  TABLE_GRID_CANVAS_WIDTH_PX,
  TABLE_GRID_COLUMNS,
  TABLE_GRID_COMPACT_CANVAS_HEIGHT_PX,
  TABLE_GRID_COMPACT_CANVAS_WIDTH_PX,
  TABLE_GRID_ROWS,
  TABLE_GRID_VISIBLE_ROWS,
  TABLE_TILE_COMPACT_HEIGHT_PX,
  TABLE_TILE_COMPACT_WIDTH_PX,
  TABLE_TILE_HEIGHT_PX,
  TABLE_TILE_WIDTH_PX,
  isTableGridCellInBounds,
  tableGridCellClientRect,
} from '@/domain/game/tableGrid'
import { deriveTableCandidates } from '@/domain/game/tableCandidateDerivation'
import { validateTurnDraft } from '@/domain/game/turnDraftValidation'
import { useGameStore } from '@/stores/game'
import type { GamePrivateState, GameRackTile, GameTableMeld, TurnPreviewSnapshot } from '@/types/game'
import type { WorkingTilePlacement } from '@/types/turnDraft'

function tile(color: GameRackTile['color'], number: number, copy = 'A'): GameRackTile {
  return {
    tileId: `${color}-${String(number).padStart(2, '0')}-${copy}`,
    tileType: 'NUMBER', color, number, joker: false, positionOrder: number,
  }
}

function placement(item: GameRackTile, row: number, column: number): WorkingTilePlacement {
  return {
    tileId: item.tileId, gridRow: row, gridColumn: column,
    source: 'CURRENT_PLAYER_RACK', sourceMeldId: null, originalPositionOrder: null,
  }
}

const red10 = tile('RED', 10)
const blue10 = tile('BLUE', 10)
const black10 = tile('BLACK', 10)
const red7 = tile('RED', 7)
const red8 = tile('RED', 8)
const red9 = tile('RED', 9)

function tableMeld(): GameTableMeld {
  return {
    meldId: '11111111-1111-4111-8111-111111111111', meldType: 'RUN', score: 24,
    positionOrder: 0, gridRow: 2, gridColumn: 4,
    tiles: [red7, red8, red9].map((item, index) => ({ ...item, positionOrder: index })),
  }
}

function fixture(table = ref<GameTableMeld[]>([])) {
  const scope = effectScope()
  const rack = ref<GameRackTile[]>([red10, blue10, black10])
  const sync = ref(0)
  const working = scope.run(() => useWorkingTable({
    authoritativeRack: rack,
    authoritativeVersion: ref(7),
    authoritativeSyncRevision: sync,
    tableMelds: table,
    initialMeldCompleted: ref(true),
    isMyTurn: ref(true),
    gameId: ref(33),
    currentTurnPlayerId: ref(1),
  }))!
  return { scope, rack, sync, working }
}

function slots(tileIds: Array<string | null>) {
  return tileIds.map((tileId, slotIndex) => ({ slotIndex, tileId }))
}

function privateState(myUserId = 2, currentTurnUserId = 1): GamePrivateState {
  return {
    publicState: {
      gameId: 33, roomId: 10, gameMode: 'CLASSIC', status: 'IN_PROGRESS', gameVersion: 7,
      currentTurnUserId, currentTurnSeatOrder: 1, turnNumber: 4,
      currentTurnId: 'turn', currentTurnStartedAt: '2026-07-18T08:00:00Z',
      turnDeadlineAt: '2026-07-18T08:02:00Z', consecutivePassCount: 0,
      startedAt: '2026-07-18T07:50:00Z', tilePoolCount: 70, tableMelds: [tableMeld()],
      players: [
        { userId: 1, nickname: 'qwer', avatarType: 'DEFAULT_01', seatOrder: 1, rackTileCount: 13, initialMeldCompleted: true, currentTurn: true },
        { userId: 2, nickname: 'asdf', avatarType: 'DEFAULT_02', seatOrder: 2, rackTileCount: 13, initialMeldCompleted: true, currentTurn: false },
      ],
    },
    myPlayerId: 102, myUserId, mySeatOrder: 2, myRack: [],
  }
}

function preview(revision = 1, turnPlayerId = 1): TurnPreviewSnapshot {
  return {
    gameId: 33, turnPlayerId, baseGameVersion: 7, previewRevision: revision,
    tilePlacements: [red10, blue10, black10].map((item, index) => ({
      tileId: item.tileId, gridRow: 5, gridColumn: 7 + index, source: 'CURRENT_PLAYER_RACK',
    })),
    updatedAt: '2026-07-18T08:00:00Z',
  }
}

describe('Phase 7 tile placement automatic Candidate grid fix', () => {
  beforeEach(() => setActivePinia(createPinia()))

  it('FE-P7-FIX-001 연속 10-10-10 → Candidate 1개', () => {
    expect(deriveTableCandidates([red10, blue10, black10].map((item, index) => placement(item, 0, index))))
      .toHaveLength(1)
  })

  it('FE-P7-FIX-002 중간 빈칸 → Candidate 2개', () => {
    expect(deriveTableCandidates([placement(red10, 0, 0), placement(blue10, 0, 2)]))
      .toHaveLength(2)
  })

  it('FE-P7-FIX-003 옆 Tile 추가 → 자동 병합', () => {
    const source = [placement(red10, 0, 0), placement(black10, 0, 2)]
    expect(deriveTableCandidates([...source, placement(blue10, 0, 1)])).toHaveLength(1)
  })

  it('FE-P7-FIX-004 중간 Tile 제거 → 자동 분리', () => {
    const source = [red10, blue10, black10].map((item, index) => placement(item, 0, index))
    expect(deriveTableCandidates(source.filter((item) => item.tileId !== blue10.tileId))).toHaveLength(2)
  })

  it('FE-P7-FIX-005 Row가 다르면 별도 Candidate', () => {
    expect(deriveTableCandidates([placement(red10, 0, 0), placement(blue10, 1, 1)]))
      .toHaveLength(2)
  })

  it('FE-P7-FIX-006 Candidate Tile 순서 Column 기준', () => {
    const result = deriveTableCandidates([
      placement(black10, 0, 2), placement(red10, 0, 0), placement(blue10, 0, 1),
    ])
    expect(result[0]!.tileIds).toEqual([red10.tileId, blue10.tileId, black10.tileId])
  })

  it('FE-P7-FIX-007 INVALID 1장/2장', () => {
    const rack = [red10, blue10]
    for (const count of [1, 2]) {
      const placements = rack.slice(0, count).map((item, index) => placement(item, 0, index))
      const candidates = deriveTableCandidates(placements)
      expect(validateTurnDraft(candidates, rack, true, [], placements).invalidCount).toBe(1)
    }
  })

  it('FE-P7-FIX-008 Candidate당 테두리 1개', () => {
    const rack = [red10, blue10, black10]
    const placements = rack.map((item, index) => placement(item, 0, index))
    const candidates = deriveTableCandidates(placements)
    const wrapper = mount(WorkingTableBoard, { props: {
      placements, rack, baselineMelds: [],
      validation: validateTurnDraft(candidates, rack, true, [], placements),
      initialMeldCompleted: true, isMeldEditable: () => true,
    } })
    expect(wrapper.findAll('[data-derived-candidate]')).toHaveLength(1)
    expect(wrapper.findAll('.working-table-tile')).toHaveLength(3)
    expect(wrapper.findAll('.draft-meld')).toHaveLength(0)
    expect(wrapper.find('.working-table-candidate-overlay--invalid').exists()).toBe(false)
  })

  it('FE-P7-FIX-008A INVALID uses a text-free overlay outside the Tile layout', () => {
    const rack = [red10, blue10]
    const placements = rack.map((item, index) => placement(item, 2, 4 + index))
    const candidates = deriveTableCandidates(placements)
    const wrapper = mount(WorkingTableBoard, { props: {
      placements, rack, baselineMelds: [],
      validation: validateTurnDraft(candidates, rack, true, [], placements),
      initialMeldCompleted: true, isMeldEditable: () => true,
    } })

    const overlay = wrapper.find('.working-table-candidate-overlay--invalid')
    expect(overlay.exists()).toBe(true)
    expect(overlay.text()).toBe('')
    expect(wrapper.findAll('.working-table-tile')).toHaveLength(2)
    expect(wrapper.findAll('.draft-meld')).toHaveLength(0)
    expect(wrapper.find('button[aria-label*="Rack"]').exists()).toBe(false)
  })

  it('FE-P7-FIX-008B keeps identical Tile dimensions for 1, 2, 3, and 4 Tile Candidates', () => {
    const red1 = tile('RED', 1)
    const red2 = tile('RED', 2)
    const red3 = tile('RED', 3)
    const red4 = tile('RED', 4)
    const rack = [red7, red8, red9, red10, blue10, black10, red1, red2, red3, red4]
    const placements = [
      placement(red7, 0, 0),
      placement(red8, 1, 0), placement(red9, 1, 1),
      placement(red10, 2, 0), placement(blue10, 2, 1), placement(black10, 2, 2),
      placement(red1, 3, 0), placement(red2, 3, 1), placement(red3, 3, 2), placement(red4, 3, 3),
    ]
    const candidates = deriveTableCandidates(placements)
    const wrapper = mount(WorkingTableBoard, { props: {
      placements, rack, baselineMelds: [],
      validation: validateTurnDraft(candidates, rack, true, [], placements),
      initialMeldCompleted: true, isMeldEditable: () => true,
    } })

    const tiles = wrapper.findAll<HTMLElement>('.working-table-tile')
    expect(tiles).toHaveLength(10)
    expect(new Set(tiles.map((entry) => entry.element.style.width))).toEqual(new Set(['']))
    expect(new Set(tiles.map((entry) => entry.element.style.height))).toEqual(new Set(['']))
    expect([TABLE_TILE_WIDTH_PX, TABLE_TILE_HEIGHT_PX]).toEqual([54, 66])
    expect(wrapper.text()).not.toContain('RUN')
    expect(wrapper.text()).not.toContain('GROUP')
  })

  it('FE-P7-FIX-009 빈 tableMelds 후 같은 version 실제 Meld 도착 시 Baseline 생성', () => {
    const table = ref<GameTableMeld[]>([])
    const { scope, working } = fixture(table)
    table.value = [tableMeld()]
    expect(working.placements.value.map((item) => item.tileId)).toEqual([red7.tileId, red8.tileId, red9.tileId])
    scope.stop()
  })

  it('FE-P7-FIX-010 local change가 있으면 늦은 동일 version 응답이 편집을 덮지 않음', () => {
    const table = ref<GameTableMeld[]>([])
    const { scope, rack, working } = fixture(table)
    working.addAsNewMeld([red10.tileId], rack.value.map((item) => item.tileId), 5, 5)
    table.value = [tableMeld()]
    expect(working.placements.value.some((item) => item.tileId === red10.tileId)).toBe(true)
    expect(working.lastResolution.value).toBe('STALE')
    scope.stop()
  })

  it('FE-P7-FIX-011 Committed Meld → 서버 확정 Tile Placement 좌표 보존', () => {
    expect(committedMeldsToPlacements([tableMeld()]).map((item) => [item.gridRow, item.gridColumn]))
      .toEqual([[2, 4], [2, 5], [2, 6]])
  })

  it('FE-P7-FIX-012 Cancel → Committed Placement 복구', () => {
    const { scope, rack, working } = fixture(ref([tableMeld()]))
    working.addAsNewMeld([red10.tileId], rack.value.map((item) => item.tileId), 5, 5)
    working.cancel()
    expect(working.placements.value).toEqual(committedMeldsToPlacements([tableMeld()]))
    scope.stop()
  })

  it('FE-P7-FIX-013 Undo → Placement 복구', () => {
    const { scope, rack, working } = fixture()
    working.addAsNewMeld([red10.tileId], rack.value.map((item) => item.tileId), 5, 5)
    expect(working.undo()).toBe(true)
    expect(working.placements.value).toHaveLength(0)
    scope.stop()
  })

  it('FE-P7-FIX-014 MANUAL 모드 인접 GROUP Hold', () => {
    const rack = [red10, blue10, black10]
    expect(buildRackAdjacentGroups(rack, slots([...rack.map((item) => item.tileId), null, null, null]), 'MANUAL', []))
      .toContainEqual(expect.objectContaining({ kind: 'GROUP', tileIds: rack.map((item) => item.tileId) }))
  })

  it('FE-P7-FIX-015 MANUAL 모드 인접 RUN Hold', () => {
    const rack = [red7, red8, red9]
    expect(buildRackAdjacentGroups(rack, slots([...rack.map((item) => item.tileId), null, null, null]), 'MANUAL', []))
      .toContainEqual(expect.objectContaining({ kind: 'RUN', tileIds: rack.map((item) => item.tileId) }))
  })

  it('FE-P7-FIX-016 빈 Slot이 묶음을 분리', () => {
    const rack = [red7, red8, red9]
    expect(buildRackAdjacentGroups(rack, slots([red7.tileId, null, red8.tileId, red9.tileId]), 'MANUAL', []))
      .toHaveLength(0)
  })

  it('FE-P7-FIX-017 invalid 인접 Tile은 단일 Hold', () => {
    const rack = [red7, blue10, red9]
    expect(buildRackAdjacentGroups(rack, slots([...rack.map((item) => item.tileId), null, null, null]), 'MANUAL', []))
      .toHaveLength(0)
  })

  it('FE-P7-FIX-018 789/777 기존 기능 회귀 없음', () => {
    expect(buildRackVisualGroups([red7, red8, red9], 'RUN_789', [red7.tileId, red8.tileId, red9.tileId]))
      .toContainEqual(expect.objectContaining({ kind: 'RUN' }))
    expect(buildRackVisualGroups([red10, blue10, black10], 'GROUP_777', [red10.tileId, blue10.tileId, black10.tileId]))
      .toContainEqual(expect.objectContaining({ kind: 'GROUP' }))
  })

  it('FE-P7-FIX-019 상대 턴 Rack 정리 가능', () => {
    const rack = [red7, red8, red9]
    expect(buildRackAdjacentGroups(rack, slots([...rack.map((item) => item.tileId), null, null, null]), 'MANUAL', []))
      .toContainEqual(expect.objectContaining({ tileIds: rack.map((item) => item.tileId) }))
  })

  it('FE-P7-FIX-020 Placement Preview 수신 후 Candidate 자동 파생', () => {
    const wrapper = mount(TurnPreviewBoard, { props: {
      preview: preview(), committedMelds: [tableMeld()], turnPlayerNickname: 'qwer',
    } })
    expect(wrapper.findAll('.turn-preview-meld')).toHaveLength(0)
    expect(wrapper.findAll('.turn-preview-table-tile')).toHaveLength(3)
    expect(wrapper.findAll('.game-tile')).toHaveLength(3)
  })

  it('FE-P7-FIX-021 낮은 revision 무시', () => {
    const store = useGameStore()
    store.privateState = privateState()
    store.applyTurnPreviewSnapshot(preview(5))
    store.applyTurnPreviewSnapshot(preview(4))
    expect(store.turnPreview?.previewRevision).toBe(5)
  })

  it('FE-P7-FIX-022 자신의 Preview가 로컬 편집을 덮지 않음', () => {
    const store = useGameStore()
    store.privateState = privateState(1, 1)
    store.applyTurnPreviewSnapshot(preview(1, 1))
    expect(store.turnPreview).toBeNull()
  })

  it('FE-P7-FIX-023 Cancel/Commit 후 Preview 제거', () => {
    const store = useGameStore()
    store.privateState = privateState()
    store.applyTurnPreviewSnapshot(preview(1))
    store.applyTurnPreviewCleared({
      gameId: 33, turnPlayerId: 1, baseGameVersion: 7, previewRevision: 2, reason: 'CANCEL',
    })
    expect(store.turnPreview).toBeNull()
    store.applyTurnPreviewSnapshot(preview(3))
    store.applyGameEvent({
      eventType: 'MELDS_COMMITTED', occurredAt: '2026-07-18T08:01:00Z', payload: {
        gameId: 33, gameVersion: 8, committedByUserId: 1, committedByRackCount: 10,
        initialMeldCompleted: true, initialMeldScore: 0, changedMeldIds: [],
        rackContributionCount: 3, tableRecomposed: true, nextTurnUserId: 2,
        nextTurnSeatOrder: 2, turnNumber: 5, currentTurnId: 'next',
        currentTurnStartedAt: '2026-07-18T08:01:00Z', turnDeadlineAt: '2026-07-18T08:03:00Z',
        consecutivePassCount: 0,
      },
    })
    expect(store.turnPreview).toBeNull()
  })

  it('FE-P7-FIX-024 Board는 18열×18행 논리 경계와 8행 Viewport를 사용한다', () => {
    expect([TABLE_GRID_COLUMNS, TABLE_GRID_ROWS, TABLE_GRID_VISIBLE_ROWS]).toEqual([18, 18, 8])
    expect(isTableGridCellInBounds(0, 0)).toBe(true)
    expect(isTableGridCellInBounds(17, 17)).toBe(true)
    expect(isTableGridCellInBounds(-1, 0)).toBe(false)
    expect(isTableGridCellInBounds(18, 0)).toBe(false)
    expect(isTableGridCellInBounds(0, -1)).toBe(false)
    expect(isTableGridCellInBounds(0, 18)).toBe(false)
  })

  it('FE-P7-FIX-025 Desktop Content Rect를 18×18 좌표로 변환한다', () => {
    const rect = { left: 100, top: 50, width: TABLE_GRID_CANVAS_WIDTH_PX, height: TABLE_GRID_CANVAS_HEIGHT_PX }
    expect(clientPointToTableGridCoordinate(rect, 101, 51)).toEqual({ gridRow: 0, gridColumn: 0 })
    expect(clientPointToTableGridCoordinate(
      rect,
      rect.left + rect.width - 1,
      rect.top + rect.height - 1,
    )).toEqual({ gridRow: 17, gridColumn: 17 })
    expect(clientPointToTableGridCoordinate(rect, rect.left - 1, rect.top)).toBeNull()
    expect(clientPointToTableGridCoordinate(rect, rect.left + rect.width, rect.top)).toBeNull()
  })

  it('FE-P7-FIX-026 Compact Board도 같은 18×8 좌표를 반환한다', () => {
    const rect = { left: 20, top: 30, width: TABLE_GRID_COMPACT_CANVAS_WIDTH_PX, height: TABLE_GRID_COMPACT_CANVAS_HEIGHT_PX }
    expect(clientPointToTableGridCoordinate(
      rect,
      rect.left + (10 * rect.width / TABLE_GRID_COLUMNS) + 1,
      rect.top + (5 * rect.height / TABLE_GRID_ROWS) + 1,
    )).toEqual({ gridRow: 5, gridColumn: 10 })
  })

  it('FE-P7-FIX-027 Rack Ghost Target Rect도 Board Cell과 동일한 좌표를 사용한다', () => {
    const rect = { left: 100, top: 50, width: TABLE_GRID_CANVAS_WIDTH_PX, height: TABLE_GRID_CANVAS_HEIGHT_PX }
    expect(tableGridCellClientRect(rect, 7, 15, 3)).toEqual({
      left: 100 + 15 * 58,
      top: 50 + 7 * 68,
      width: 3 * 58,
      height: 68,
    })
    expect(tableGridCellClientRect(rect, 7, 16, 3)).toBeUndefined()
  })

  it('FE-P7-FIX-028 일반/Compact는 두 개의 고정 Tile 규격만 사용한다', () => {
    expect([TABLE_TILE_WIDTH_PX, TABLE_TILE_HEIGHT_PX]).toEqual([54, 66])
    expect([TABLE_TILE_COMPACT_WIDTH_PX, TABLE_TILE_COMPACT_HEIGHT_PX]).toEqual([48, 59])
  })

  it('FE-P7-FIX-029 Working/Committed/Preview는 18×18 Content와 8행 Viewport 계약을 공유한다', () => {
    const placements = [red10, blue10, black10].map((item, index) => placement(item, 6, 14 + index))
    const candidates = deriveTableCandidates(placements)
    const working = mount(WorkingTableBoard, { props: {
      placements, rack: [red10, blue10, black10], baselineMelds: [],
      validation: validateTurnDraft(candidates, [red10, blue10, black10], true, [], placements),
      initialMeldCompleted: true, isMeldEditable: () => true,
    } })
    const committed = mount(CommittedTableBoard, { props: { melds: [tableMeld()] } })
    const previewBoard = mount(TurnPreviewBoard, { props: {
      preview: preview(), committedMelds: [tableMeld()], turnPlayerNickname: 'qwer',
    } })
    for (const canvas of [
      working.find<HTMLElement>('.working-table-grid__canvas'),
      committed.find<HTMLElement>('.committed-table-grid__canvas'),
      previewBoard.find<HTMLElement>('.turn-preview-grid__canvas'),
    ]) {
      expect(canvas.element.style.transform).toBe('')
      expect(canvas.attributes('data-grid-columns')).toBe('18')
      expect(canvas.attributes('data-grid-rows')).toBe('18')
    }
    for (const viewport of [
      working.find<HTMLElement>('.working-table-grid'),
      committed.find<HTMLElement>('.committed-table-grid__viewport'),
      previewBoard.find<HTMLElement>('.turn-preview-grid'),
    ]) {
      expect(viewport.attributes('data-visible-rows')).toBe('8')
    }
    expect(working.find('.table-camera-viewport').exists()).toBe(false)
    expect(committed.find('.table-camera-viewport').exists()).toBe(false)
    expect(previewBoard.find('.table-camera-viewport').exists()).toBe(false)
  })

  it('FE-P7-FIX-030 Board 끝 Tile도 좌표 변수만 바뀌고 Tile 크기 규격은 고정된다', () => {
    const rack = [red10, blue10]
    const placements = [placement(red10, 0, 0), placement(blue10, 17, 17)]
    const wrapper = mount(WorkingTableBoard, { props: {
      placements, rack, baselineMelds: [],
      validation: validateTurnDraft(deriveTableCandidates(placements), rack, true, [], placements),
      initialMeldCompleted: true, isMeldEditable: () => true,
    } })
    const tiles = wrapper.findAll<HTMLElement>('.working-table-tile')
    expect(tiles.map((entry) => entry.element.style.getPropertyValue('--table-grid-column'))).toEqual(['0', '17'])
    expect(tiles.map((entry) => entry.element.style.getPropertyValue('--table-grid-row'))).toEqual(['0', '17'])
    expect(wrapper.find<HTMLElement>('.working-table-grid__canvas').element.style.transform).toBe('')
  })
})

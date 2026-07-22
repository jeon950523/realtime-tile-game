import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import TurnPreviewBoard from '@/components/game/TurnPreviewBoard.vue'
import { resetGameStoreClientForTests, useGameStore } from '@/stores/game'
import type {
  GamePrivateState,
  GameTableMeld,
  MeldsCommittedPayload,
  TurnPreviewSnapshot,
} from '@/types/game'

const realtime = vi.hoisted(() => ({
  publishTurnPreview: vi.fn(),
  publishTurnPreviewCancel: vi.fn(),
  options: null as null | { onStateChange: (state: 'CONNECTING' | 'CONNECTED') => void },
}))

vi.mock('@/realtime/authenticatedStompClient', () => ({
  AuthenticatedStompClient: class {
    constructor(options: { onStateChange: (state: 'CONNECTING' | 'CONNECTED') => void }) {
      realtime.options = options
    }
    publishTurnPreview = realtime.publishTurnPreview
    publishTurnPreviewCancel = realtime.publishTurnPreviewCancel
    isGameCommandReady = vi.fn(() => true)
  },
}))

const committedMeld: GameTableMeld = {
  meldId: '11111111-1111-4111-8111-111111111111',
  meldType: 'RUN',
  score: 6,
  positionOrder: 0,
  gridRow: 0,
  gridColumn: 0,
  tiles: [1, 2, 3].map((number, index) => ({
    tileId: `RED-0${number}-A`, tileType: 'NUMBER', color: 'RED', number,
    joker: false, positionOrder: index,
  })),
}

function privateState(myUserId = 2, currentTurnUserId = 1): GamePrivateState {
  return {
    publicState: {
      gameId: 33, roomId: 10, gameMode: 'CLASSIC', status: 'IN_PROGRESS', gameVersion: 7,
      currentTurnUserId, currentTurnSeatOrder: currentTurnUserId, turnNumber: 4,
      currentTurnId: '11111111-1111-4111-8111-111111111111',
      currentTurnStartedAt: '2026-07-18T08:00:00Z', turnDeadlineAt: '2026-07-18T08:02:00Z',
      consecutivePassCount: 0, startedAt: '2026-07-18T07:50:00Z', tilePoolCount: 70,
      tableMelds: [structuredClone(committedMeld)],
      players: [
        { userId: 1, nickname: 'qwer', avatarType: 'DEFAULT_01', seatOrder: 1, rackTileCount: 13, initialMeldCompleted: true, currentTurn: currentTurnUserId === 1 },
        { userId: 2, nickname: 'asdf', avatarType: 'DEFAULT_02', seatOrder: 2, rackTileCount: 14, initialMeldCompleted: true, currentTurn: currentTurnUserId === 2 },
      ],
    },
    myPlayerId: myUserId === 1 ? 101 : 102,
    myUserId,
    mySeatOrder: myUserId,
    myRack: [],
  }
}

function preview(revision = 1, turnPlayerId = 1): TurnPreviewSnapshot {
  const tileIds = ['RED-01-A', 'RED-02-A', 'RED-03-A', 'BLUE-04-A']
  return {
    gameId: 33,
    turnPlayerId,
    baseGameVersion: 7,
    previewRevision: revision,
    tilePlacements: tileIds.map((tileId, index) => ({
      tileId,
      gridRow: 2,
      gridColumn: 5 + index,
      source: index < 3 ? 'COMMITTED_TABLE' as const : 'CURRENT_PLAYER_RACK' as const,
    })),
    updatedAt: '2026-07-18T08:00:00Z',
  }
}

function readyStore(myUserId = 1, currentTurnUserId = 1) {
  const store = useGameStore()
  store.privateState = privateState(myUserId, currentTurnUserId)
  store.activeGameId = 33
  store.connectionState = 'CONNECTED'
  store.gameSubscriptionsReady = true
  store.privateStateLoaded = true
  store.privateStateVersion = 7
  return store
}

describe('Phase 7 final closure Stage D live turn preview', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    resetGameStoreClientForTests()
    vi.clearAllMocks()
    realtime.options = null
  })

  it('FE-P7D-001 publishes one Snapshot after a meaningful Drop result', () => {
    const store = readyStore()
    expect(store.publishTurnPreview(
      ['RED-01-A', 'RED-02-A', 'RED-03-A', 'BLUE-04-A'].map((tileId, index) => ({
        tileId, gridRow: 2, gridColumn: 5 + index,
        source: index < 3 ? 'COMMITTED_TABLE' as const : 'CURRENT_PLAYER_RACK' as const,
      })),
    )).toBe(true)
    expect(realtime.publishTurnPreview).toHaveBeenCalledTimes(1)
  })

  it('FE-P7D-002 does not transmit merely because a drag frame exists', () => {
    readyStore()
    expect(realtime.publishTurnPreview).not.toHaveBeenCalled()
    expect(realtime.publishTurnPreviewCancel).not.toHaveBeenCalled()
  })

  it('FE-P7D-003 includes base version, revision, tile IDs, and logical coordinates', () => {
    const store = readyStore()
    store.publishTurnPreview(
      ['RED-01-A', 'RED-02-A', 'RED-03-A', 'BLUE-04-A'].map((tileId, index) => ({
        tileId, gridRow: 6, gridColumn: 9 + index,
        source: index < 3 ? 'COMMITTED_TABLE' as const : 'CURRENT_PLAYER_RACK' as const,
      })),
    )

    expect(realtime.publishTurnPreview).toHaveBeenCalledWith(33, expect.objectContaining({
      gameId: 33,
      baseGameVersion: 7,
      previewRevision: 1,
      tilePlacements: expect.arrayContaining([
        expect.objectContaining({ tileId: 'RED-01-A', gridRow: 6, gridColumn: 9 }),
        expect.objectContaining({ tileId: 'BLUE-04-A', gridRow: 6, gridColumn: 12 }),
      ]),
    }))
  })

  it('FE-P7D-004 renders the opponent Snapshot as a read-only board', () => {
    const wrapper = mount(TurnPreviewBoard, { props: {
      preview: preview(),
      committedMelds: [committedMeld],
      turnPlayerNickname: 'qwer',
    } })
    expect(wrapper.text()).toContain('qwer님이 테이블을 편집 중입니다')
    expect(wrapper.text()).toContain('확정 전 미리보기')
    expect(wrapper.findAll('.game-tile')).toHaveLength(4)
    expect(wrapper.findAll('[draggable="true"]')).toHaveLength(0)
    expect(wrapper.findAll('[data-draft-drop-target]')).toHaveLength(0)
  })

  it('FE-P7D-005 ignores a repeated or lower Preview revision', () => {
    const store = readyStore(2, 1)
    store.applyTurnPreviewSnapshot(preview(5))
    store.applyTurnPreviewSnapshot(preview(4))
    expect(store.turnPreview?.previewRevision).toBe(5)
  })

  it('FE-P7D-006 ignores the current players own broadcast', () => {
    const store = readyStore(1, 1)
    store.applyTurnPreviewSnapshot(preview(1, 1))
    expect(store.turnPreview).toBeNull()
  })

  it('FE-P7D-007 clears Preview when Commit advances the authoritative turn', () => {
    const store = readyStore(2, 1)
    store.applyTurnPreviewSnapshot(preview(3))
    const payload: MeldsCommittedPayload = {
      gameId: 33, gameVersion: 8, committedByUserId: 1, committedByRackCount: 12,
      initialMeldCompleted: true, initialMeldScore: 0, changedMeldIds: [committedMeld.meldId],
      rackContributionCount: 1, tableRecomposed: true, nextTurnUserId: 2, nextTurnSeatOrder: 2,
      turnNumber: 5, currentTurnId: '22222222-2222-4222-8222-222222222222',
      currentTurnStartedAt: '2026-07-18T08:01:00Z', turnDeadlineAt: '2026-07-18T08:03:00Z',
      consecutivePassCount: 0,
    }
    store.applyGameEvent({ eventType: 'MELDS_COMMITTED', occurredAt: payload.currentTurnStartedAt, payload })
    expect(store.turnPreview).toBeNull()
    expect(store.privateState?.publicState.gameVersion).toBe(8)
  })

  it('FE-P7D-008 applies Cancel clear and returns to the committed table', () => {
    const store = readyStore(2, 1)
    store.applyTurnPreviewSnapshot(preview(3))
    store.applyGameEvent({
      eventType: 'TURN_PREVIEW_CLEARED', occurredAt: '2026-07-18T08:01:00Z',
      payload: { gameId: 33, turnPlayerId: 1, baseGameVersion: 7, previewRevision: 4, reason: 'CANCEL' },
    })
    expect(store.turnPreview).toBeNull()
    expect(store.privateState?.publicState.tableMelds).toEqual([committedMeld])
  })

  it('FE-P7D-009 drops Preview on disconnect so reconnect recovery can restore it', () => {
    const store = readyStore(2, 1)
    store.client()
    store.applyTurnPreviewSnapshot(preview(3))
    realtime.options?.onStateChange('CONNECTING')
    expect(store.turnPreview).toBeNull()
  })
})

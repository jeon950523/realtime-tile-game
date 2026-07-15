import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as gameApi from '@/api/gameApi'
import { resetGameStoreClientForTests, useGameStore } from '@/stores/game'
import type { GamePrivateState } from '@/types/game'

vi.mock('@/api/gameApi')

const fixture: GamePrivateState = {
  publicState: {
    gameId: 33,
    roomId: 10,
    gameMode: 'CLASSIC',
    status: 'IN_PROGRESS',
    currentTurnUserId: 1,
    currentTurnSeatOrder: 1,
    turnNumber: 1,
    startedAt: '2026-07-15T07:00:00Z',
    tilePoolCount: 78,
    tableMelds: [],
    players: [
      { userId: 1, nickname: 'owner', avatarType: 'DEFAULT_01', seatOrder: 1, rackTileCount: 14, initialMeldCompleted: false, currentTurn: true },
      { userId: 2, nickname: 'guest', avatarType: 'DEFAULT_02', seatOrder: 2, rackTileCount: 14, initialMeldCompleted: false, currentTurn: false },
    ],
  },
  myPlayerId: 100,
  myUserId: 1,
  mySeatOrder: 1,
  myRack: Array.from({ length: 14 }, (_, index) => ({
    tileId: `RED-${String(index + 1).padStart(2, '0')}-A`,
    tileType: 'NUMBER' as const,
    color: 'RED' as const,
    number: (index % 13) + 1,
    joker: false,
    positionOrder: index,
  })),
}

beforeEach(() => {
  setActivePinia(createPinia())
  resetGameStoreClientForTests()
  vi.resetAllMocks()
})

describe('game store', () => {
  it('loads and keeps the private rack separate from public player counts', async () => {
    vi.mocked(gameApi.getGameState).mockResolvedValue(structuredClone(fixture))
    const store = useGameStore()

    await store.loadGame(33)

    expect(store.myRack).toHaveLength(14)
    expect(store.publicState?.players.map((player) => player.rackTileCount)).toEqual([14, 14])
    expect(store.publicState).not.toHaveProperty('myRack')
  })

  it('merges a public event without replacing the authenticated rack', async () => {
    vi.mocked(gameApi.getGameState).mockResolvedValue(structuredClone(fixture))
    const store = useGameStore()
    await store.loadGame(33)
    const rackBefore = store.myRack.map((tile) => ({ ...tile }))

    store.applyGameEvent({
      eventType: 'GAME_STATE_UPDATED',
      occurredAt: '',
      payload: { ...fixture.publicState, tilePoolCount: 77, currentTurnUserId: 2, currentTurnSeatOrder: 2 },
    })

    expect(store.publicState?.tilePoolCount).toBe(77)
    expect(store.myRack).toEqual(rackBefore)
  })

  it('restores an active game before room recovery', async () => {
    vi.mocked(gameApi.getActiveGame).mockResolvedValue({ active: true, gameId: 33, roomId: 10, status: 'IN_PROGRESS' })
    const store = useGameStore()

    const result = await store.loadActiveGame()

    expect(result.gameId).toBe(33)
    expect(store.activeGameId).toBe(33)
  })

  it('ignores a private queue event for another active game', () => {
    const store = useGameStore()
    store.activeGameId = 33

    store.applyPrivateStateEvent({
      eventType: 'GAME_STATE_UPDATED',
      occurredAt: '',
      payload: { ...fixture, publicState: { ...fixture.publicState, gameId: 44 } },
    })

    expect(store.privateState).toBeNull()
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'

const http = vi.hoisted(() => ({ get: vi.fn() }))
vi.mock('@/api/httpClient', () => ({ httpClient: http }))

import { getActiveGame, getGameState } from '@/api/gameApi'
import type { GamePrivateState } from '@/types/game'

const state: GamePrivateState = {
  publicState: {
    gameId: 33,
    roomId: 10,
    gameMode: 'CLASSIC',
    status: 'IN_PROGRESS',
    gameVersion: 0,
    currentTurnUserId: 1,
    currentTurnSeatOrder: 1,
    turnNumber: 1,
    currentTurnId: '11111111-1111-4111-8111-111111111111',
    currentTurnStartedAt: '2026-07-15T07:00:00Z',
    turnDeadlineAt: '2026-07-15T07:02:00Z',
    consecutivePassCount: 0,
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
  myRack: [],
}

beforeEach(() => vi.resetAllMocks())

describe('game api', () => {
  it('loads the authenticated private state for a game', async () => {
    http.get.mockResolvedValue({ data: { data: state } })

    await expect(getGameState(33)).resolves.toEqual(state)
    expect(http.get).toHaveBeenCalledWith('/api/games/33')
  })

  it('loads active game recovery metadata', async () => {
    const active = { active: true, gameId: 33, roomId: 10, status: 'IN_PROGRESS' as const }
    http.get.mockResolvedValue({ data: { data: active } })

    await expect(getActiveGame()).resolves.toEqual(active)
    expect(http.get).toHaveBeenCalledWith('/api/me/active-game')
  })
})

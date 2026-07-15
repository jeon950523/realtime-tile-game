import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as gameApi from '@/api/gameApi'
import { resetGameStoreClientForTests } from '@/stores/game'
import GameView from '@/views/GameView.vue'
import { useAuthStore } from '@/stores/auth'
import type { GamePrivateState } from '@/types/game'

const realtime = vi.hoisted(() => ({ connectGame: vi.fn(async () => undefined), disconnect: vi.fn(async () => undefined) }))
vi.mock('@/api/gameApi')
vi.mock('@/realtime/authenticatedStompClient', () => ({
  AuthenticatedStompClient: class {
    connectGame = realtime.connectGame
    disconnect = realtime.disconnect
  },
}))

const state: GamePrivateState = {
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
    tileId: index === 13 ? 'JOKER-A' : `BLUE-${String(index + 1).padStart(2, '0')}-A`,
    tileType: index === 13 ? 'JOKER' as const : 'NUMBER' as const,
    color: index === 13 ? null : 'BLUE' as const,
    number: index === 13 ? null : index + 1,
    joker: index === 13,
    positionOrder: index,
  })),
}

beforeEach(() => {
  setActivePinia(createPinia())
  resetGameStoreClientForTests()
  vi.resetAllMocks()
  vi.mocked(gameApi.getGameState).mockResolvedValue(structuredClone(state))
  vi.mocked(gameApi.getActiveGame).mockResolvedValue({ active: true, gameId: 33, roomId: 10, status: 'IN_PROGRESS' })
  const auth = useAuthStore()
  auth.authStatus = 'AUTHENTICATED'
  auth.accessToken = 'token'
  auth.user = { userId: 1, nickname: 'owner', avatarType: 'DEFAULT_01', ratingScore: 1000 }
})

async function mountGame() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/games/:gameId', name: 'game', component: GameView },
      { path: '/lobby', name: 'lobby', component: { template: '<div />' } },
    ],
  })
  await router.push('/games/33')
  await router.isReady()
  const wrapper = mount(GameView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('game view', () => {
  it('restores the private game state and one game subscription on mount', async () => {
    const wrapper = await mountGame()

    expect(gameApi.getGameState).toHaveBeenCalledWith(33)
    expect(realtime.connectGame).toHaveBeenCalledTimes(1)
    expect(realtime.connectGame).toHaveBeenCalledWith(33)
    expect(wrapper.findAll('.game-tile')).toHaveLength(14)
  })

  it('shows pool, current turn and opponent rack count without opponent tile details', async () => {
    const wrapper = await mountGame()

    expect(wrapper.text()).toContain('Pool 78')
    expect(wrapper.text()).toContain('owner')
    expect(wrapper.text()).toContain('guest')
    expect(wrapper.text()).toContain('Rack 14개')
    expect(wrapper.text()).not.toContain('RED-13-B')
  })
})

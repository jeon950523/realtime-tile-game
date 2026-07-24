import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as gameApi from '@/api/gameApi'
import * as roomApi from '@/api/roomApi'
import { resetGameStoreClientForTests, useGameStore } from '@/stores/game'
import GameView from '@/views/GameView.vue'
import { useAuthStore } from '@/stores/auth'
import { useRoomStore } from '@/stores/room'
import type { GamePrivateState } from '@/types/game'

const realtime = vi.hoisted(() => ({
  connectGame: vi.fn(async (_gameId: number) => undefined),
  connectLobby: vi.fn(async () => undefined),
  disconnect: vi.fn(async () => undefined),
  publishDraw: vi.fn(),
  publishPass: vi.fn(),
  publishExitActiveGame: vi.fn(),
}))
vi.mock('@/api/gameApi')
vi.mock('@/api/roomApi')
vi.mock('@/realtime/authenticatedStompClient', () => ({
  AuthenticatedStompClient: class {
    constructor(private readonly options: { onStateChange: (state: string) => void }) {}

    async connectGame(gameId: number): Promise<void> {
      await realtime.connectGame(gameId)
      this.options.onStateChange('CONNECTED')
    }

    connectLobby = realtime.connectLobby
    disconnect = realtime.disconnect
    isGameCommandReady = vi.fn(() => true)
    publishDraw = realtime.publishDraw
    publishPass = realtime.publishPass
    publishExitActiveGame = realtime.publishExitActiveGame
  },
}))

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
  vi.mocked(roomApi.getRooms).mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0 })
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

function rackExhaustedEvent(winnerUserId = 1) {
  return {
    eventType: 'GAME_TERMINATED',
    occurredAt: '2026-07-23T06:00:00Z',
    payload: {
      roomId: 10,
      gameId: 33,
      gameVersion: 1,
      roomStatus: 'CLOSED' as const,
      gameStatus: 'FINISHED' as const,
      terminationReason: 'RACK_EXHAUSTED' as const,
      exitedParticipantId: null,
      exitedUserId: null,
      winnerParticipantId: winnerUserId === 1 ? 100 : 101,
      winnerUserId,
      serverTime: '2026-07-23T06:00:00Z',
    },
  }
}

describe('game view', () => {
  it('does not render the loading state while the normal game state is present', async () => {
    const wrapper = await mountGame()

    expect(wrapper.find('.game-loading').exists()).toBe(false)
  })

  it('renders the loading state only while the game state is absent', async () => {
    vi.mocked(gameApi.getGameState).mockImplementation(() => new Promise<GamePrivateState>(() => undefined))

    const wrapper = await mountGame()

    expect(wrapper.find('.game-loading').exists()).toBe(true)
    expect(wrapper.find('.game-board').exists()).toBe(false)
  })

  it('does not render loading merely because the exit confirmation is closed', async () => {
    const wrapper = await mountGame()

    expect(wrapper.find('[role="dialog"]').exists()).toBe(false)
    expect(wrapper.find('.game-loading').exists()).toBe(false)
  })

  it('restores the private game state and one game subscription on mount', async () => {
    const wrapper = await mountGame()

    expect(gameApi.getGameState).toHaveBeenCalledWith(33)
    expect(realtime.connectGame).toHaveBeenCalledTimes(1)
    expect(realtime.connectGame).toHaveBeenCalledWith(33)
    expect(wrapper.findAll('.game-tile')).toHaveLength(14)
  })

  it('shows pool, current turn and opponent rack count without opponent tile details', async () => {
    const wrapper = await mountGame()

    expect(wrapper.find('[data-action="draw"]').text()).toContain('78')
    expect(wrapper.text()).toContain('owner')
    expect(wrapper.text()).toContain('guest')
    expect(wrapper.find('.player-seat').attributes('aria-label')).toContain('Rack 14개')
    expect(wrapper.text()).not.toContain('RED-13-B')
  })

  it('shows completed initial registration without leaving a zero-over-thirty label', async () => {
    const completed = structuredClone(state)
    completed.publicState.players[0]!.initialMeldCompleted = true
    vi.mocked(gameApi.getGameState).mockResolvedValue(completed)

    const wrapper = await mountGame()

    expect(wrapper.text()).toContain('첫 등록 완료')
    expect(wrapper.text()).not.toContain('첫 등록 0 / 30')
    expect(wrapper.text()).not.toContain('이번 제출 0점')
  })

  it('enables Draw only for the current player while the pool has tiles', async () => {
    const wrapper = await mountGame()
    const drawButton = wrapper.find('[data-action="draw"]')

    expect(drawButton.attributes('disabled')).toBeUndefined()
    await drawButton.trigger('click')
    expect(realtime.publishDraw).toHaveBeenCalledTimes(1)
    expect(realtime.publishPass).not.toHaveBeenCalled()
  })

  it('confirms active-game exit, cancels without a request, and blocks duplicate confirmation', async () => {
    const wrapper = await mountGame()
    const exitButton = wrapper.find('[aria-label="게임 포기 및 나가기"]')

    await exitButton.trigger('click')
    expect(wrapper.get('[role="dialog"]').text()).toContain('상대방의 승리')
    await wrapper.get('[role="dialog"] button').trigger('click')
    expect(realtime.publishExitActiveGame).not.toHaveBeenCalled()

    await exitButton.trigger('click')
    const buttons = wrapper.get('[role="dialog"]').findAll('button')
    await buttons[1]!.trigger('click')

    expect(realtime.publishExitActiveGame).toHaveBeenCalledTimes(1)
    expect(realtime.publishExitActiveGame).toHaveBeenCalledWith(33, expect.objectContaining({
      roomId: 10,
      gameVersion: 0,
      actionId: expect.any(String),
    }))
    expect(wrapper.find('[aria-label="게임 포기 및 나가기"]').attributes('disabled')).toBeDefined()
    await wrapper.find('[aria-label="게임 포기 및 나가기"]').trigger('click')
    expect(realtime.publishExitActiveGame).toHaveBeenCalledTimes(1)
  })

  it('disables both turn actions for a non-current participant', async () => {
    const opponentTurn = structuredClone(state)
    opponentTurn.publicState.currentTurnUserId = 2
    opponentTurn.publicState.currentTurnSeatOrder = 2
    opponentTurn.publicState.players[0]!.currentTurn = false
    opponentTurn.publicState.players[1]!.currentTurn = true
    vi.mocked(gameApi.getGameState).mockResolvedValue(opponentTurn)

    const wrapper = await mountGame()
    const drawButton = wrapper.find('[data-action="draw"]')
    const sortButtons = wrapper.findAll('.rack-toolbar button')

    expect(drawButton.attributes('disabled')).toBeDefined()
    expect(sortButtons.every((button) => button.attributes('disabled') === undefined)).toBe(true)
    await sortButtons[1]!.trigger('click')
    expect(realtime.publishDraw).not.toHaveBeenCalled()
    expect(realtime.publishPass).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('상대 턴에도 Rack 정렬은 가능합니다.')
  })

  it('enables PASS only when the authoritative pool count is zero', async () => {
    const emptyPool = structuredClone(state)
    emptyPool.publicState.tilePoolCount = 0
    vi.mocked(gameApi.getGameState).mockResolvedValue(emptyPool)

    const wrapper = await mountGame()
    const passButton = wrapper.find('[data-action="pass"]')

    expect(passButton.attributes('disabled')).toBeUndefined()
    await passButton.trigger('click')
    expect(realtime.publishPass).toHaveBeenCalledTimes(1)
    expect(realtime.publishDraw).not.toHaveBeenCalled()
  })

  it('restores turn version and deadline from REST and clamps an expired countdown to zero', async () => {
    const expired = structuredClone(state)
    expired.publicState.gameVersion = 9
    expired.publicState.turnNumber = 10
    expired.publicState.currentTurnId = '99999999-9999-4999-8999-999999999999'
    expired.publicState.turnDeadlineAt = '2020-01-01T00:00:00Z'
    vi.mocked(gameApi.getGameState).mockResolvedValue(expired)

    const wrapper = await mountGame()

    expect(wrapper.find('.game-debug-panel').text()).toContain('Turn10')
    expect(wrapper.find('.game-debug-panel').text()).toContain('Version9')
    expect(wrapper.text()).toContain('0초')
  })

  it('sorts 777, 789, and server order without publishing a server command', async () => {
    const unsorted = structuredClone(state)
    unsorted.myRack = [
      { tileId: 'BLUE-07-A', tileType: 'NUMBER', color: 'BLUE', number: 7, joker: false, positionOrder: 0 },
      { tileId: 'RED-02-A', tileType: 'NUMBER', color: 'RED', number: 2, joker: false, positionOrder: 1 },
      { tileId: 'RED-01-A', tileType: 'NUMBER', color: 'RED', number: 1, joker: false, positionOrder: 2 },
    ]
    vi.mocked(gameApi.getGameState).mockResolvedValue(unsorted)
    const wrapper = await mountGame()
    const ids = () => wrapper.findAll('.game-tile').map((tile) => tile.attributes('data-tile-id'))

    await wrapper.find('[aria-label^="777"]').trigger('click')
    expect(ids()).toEqual(['RED-01-A', 'RED-02-A', 'BLUE-07-A'])
    await new Promise((resolve) => window.setTimeout(resolve, 280))
    await wrapper.find('[aria-label^="789"]').trigger('click')
    expect(ids()).toEqual(['RED-01-A', 'RED-02-A', 'BLUE-07-A'])
    await new Promise((resolve) => window.setTimeout(resolve, 280))
    await wrapper.find('[aria-label^="마지막 서버"]').trigger('click')
    expect(ids()).toEqual(['BLUE-07-A', 'RED-02-A', 'RED-01-A'])
    expect(realtime.publishDraw).not.toHaveBeenCalled()
    expect(realtime.publishPass).not.toHaveBeenCalled()
  })

  it('marks only the confirmed TILE_DRAWN tile for entry motion', async () => {
    const wrapper = await mountGame()
    const store = useGameStore()
    expect(wrapper.find('[data-entering="true"]').exists()).toBe(false)

    store.applyGameEvent({
      eventType: 'TILE_DRAWN',
      occurredAt: '2026-07-15T07:00:01Z',
      payload: {
        gameId: 33,
        gameVersion: 1,
        drawnByUserId: 1,
        drawnByRackCount: 15,
        tilePoolCount: 77,
        nextTurnUserId: 2,
        nextTurnSeatOrder: 2,
        turnNumber: 2,
        currentTurnId: '22222222-2222-4222-8222-222222222222',
        currentTurnStartedAt: '2026-07-15T07:00:01Z',
        turnDeadlineAt: '2026-07-15T07:02:01Z',
        consecutivePassCount: 0,
      },
    })
    expect(wrapper.find('[data-entering="true"]').exists()).toBe(false)

    const current = store.privateState!
    const drawn: GamePrivateState = {
      ...current,
      publicState: {
        ...current.publicState,
        players: current.publicState.players.map((player) => ({ ...player })),
        tableMelds: [...current.publicState.tableMelds],
      },
      myRack: current.myRack.map((tile) => ({ ...tile })),
    }
    drawn.myRack.push({
      tileId: 'BLACK-09-B',
      tileType: 'NUMBER',
      color: 'BLACK',
      number: 9,
      joker: false,
      positionOrder: 14,
    })
    store.applyPrivateStateEvent({ eventType: 'GAME_STATE_UPDATED', occurredAt: '', payload: drawn })
    await flushPromises()

    expect(wrapper.find('[data-tile-id="BLACK-09-B"]').attributes('data-entering')).toBe('true')
  })

  it('places three opponents across left, center, and right relative seats', async () => {
    const fourPlayer = structuredClone(state)
    fourPlayer.publicState.players.push(
      { userId: 3, nickname: 'third', avatarType: 'DEFAULT_03', seatOrder: 3, rackTileCount: 14, initialMeldCompleted: false, currentTurn: false },
      { userId: 4, nickname: 'fourth', avatarType: 'DEFAULT_04', seatOrder: 4, rackTileCount: 14, initialMeldCompleted: false, currentTurn: false },
    )
    vi.mocked(gameApi.getGameState).mockResolvedValue(fourPlayer)

    const wrapper = await mountGame()

    expect(wrapper.findAll('.player-seat')).toHaveLength(3)
    expect(wrapper.find('.player-seat--left').exists()).toBe(true)
    expect(wrapper.find('.player-seat--center').exists()).toBe(true)
    expect(wrapper.find('.player-seat--right').exists()).toBe(true)
  })

  it('keeps the game route and shows a blocking Rack result modal without a loading state', async () => {
    const wrapper = await mountGame()
    const store = useGameStore()
    store.applyGameEvent(rackExhaustedEvent())
    await flushPromises()

    expect(store.activeGameId).toBeNull()
    expect(store.privateState).toBeNull()
    expect(store.terminalRevision).toBe(1)
    expect(wrapper.vm.$route.fullPath).toBe('/games/33')
    expect(wrapper.get('[data-testid="game-result-modal"]').text()).toContain('승리했습니다!')
    expect(wrapper.get('[data-testid="game-result-modal"]').text()).toContain('owner님이 모든 Rack 타일을 소진했습니다.')
    expect(wrapper.get('[data-testid="game-result-modal"]').text()).toContain('SEAT 1')
    expect(wrapper.get('[data-testid="game-result-modal"]').text()).toContain('종료 사유 · Rack 소진')
    expect(wrapper.find('.game-loading').exists()).toBe(false)
    expect(wrapper.find('[data-action="draw"]').exists()).toBe(false)
    expect(wrapper.find('.rack-toolbar').exists()).toBe(false)
    expect(wrapper.find('[aria-label="게임 포기 및 나가기"]').exists()).toBe(false)
    expect(wrapper.get('[role="dialog"]').findAll('button')).toHaveLength(1)
    expect(roomApi.getRooms).not.toHaveBeenCalled()
    expect(realtime.connectLobby).not.toHaveBeenCalled()

    await wrapper.get('.game-result-modal').trigger('click')
    await wrapper.trigger('keydown', { key: 'Escape' })
    expect(wrapper.find('[data-testid="game-result-modal"]').exists()).toBe(true)
    expect(store.terminalResult).not.toBeNull()
  })

  it('shows the winner nickname to a losing player', async () => {
    const wrapper = await mountGame()
    const store = useGameStore()
    store.applyGameEvent(rackExhaustedEvent(2))
    await flushPromises()

    const modal = wrapper.get('[data-testid="game-result-modal"]')
    expect(modal.text()).toContain('게임 종료')
    expect(modal.text()).toContain('guest님이 승리했습니다.')
    expect(modal.text()).toContain('모든 Rack 타일을 먼저 소진했습니다.')
    expect(modal.text()).toContain('SEAT 2')
  })

  it('moves to the lobby once only after the result button is clicked twice quickly', async () => {
    const wrapper = await mountGame()
    const store = useGameStore()
    const roomStore = useRoomStore()
    store.applyGameEvent(rackExhaustedEvent())
    await flushPromises()

    const leaveButton = wrapper.get('.game-result-modal__leave')
    void leaveButton.trigger('click')
    void leaveButton.trigger('click')
    await flushPromises()

    expect(roomApi.getRooms).toHaveBeenCalledTimes(1)
    expect(realtime.connectLobby).toHaveBeenCalledTimes(1)
    expect(wrapper.vm.$route.fullPath).toBe('/lobby')
    expect(roomStore.lastMessage).toBe('모든 Rack 타일을 소진하여 승리했습니다.')
    expect(store.terminalResult).toBeNull()
  })

  it('keeps PLAYER_FORFEIT on the existing immediate lobby flow', async () => {
    const wrapper = await mountGame()
    const store = useGameStore()
    store.applyGameEvent({
      eventType: 'GAME_TERMINATED',
      occurredAt: '',
      payload: {
        roomId: 10, gameId: 33, gameVersion: 1, roomStatus: 'CLOSED', gameStatus: 'FINISHED',
        terminationReason: 'PLAYER_FORFEIT', exitedParticipantId: 100, exitedUserId: 1,
        winnerParticipantId: 101, winnerUserId: 2, serverTime: '',
      },
    })
    await flushPromises()

    expect(store.terminalResult).toBeNull()
    expect(wrapper.vm.$route.fullPath).toBe('/lobby')
    expect(roomApi.getRooms).toHaveBeenCalledTimes(1)
    expect(realtime.connectLobby).toHaveBeenCalledTimes(1)
  })

  it('moves to the lobby after refresh when the terminated game is no longer active', async () => {
    vi.mocked(gameApi.getGameState).mockRejectedValueOnce(new Error('GAME_NOT_IN_PROGRESS'))
    vi.mocked(gameApi.getActiveGame).mockResolvedValueOnce({
      active: false,
      gameId: null,
      roomId: null,
      status: null,
    })

    const wrapper = await mountGame()

    expect(wrapper.vm.$route.fullPath).toBe('/lobby')
    expect(useGameStore().terminalResult).toBeNull()
  })

})

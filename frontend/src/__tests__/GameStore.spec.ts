import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as gameApi from '@/api/gameApi'
import { resetGameStoreClientForTests, useGameStore } from '@/stores/game'
import type { GamePrivateState } from '@/types/game'

vi.mock('@/api/gameApi')

const realtime = vi.hoisted(() => ({
  publishDraw: vi.fn(),
  publishPass: vi.fn(),
  publishExitActiveGame: vi.fn(),
  connectGame: vi.fn(async () => undefined),
  isGameCommandReady: vi.fn(() => true),
  disconnect: vi.fn(async () => undefined),
}))

vi.mock('@/realtime/authenticatedStompClient', () => ({
  AuthenticatedStompClient: class {
    publishDraw = realtime.publishDraw
    publishPass = realtime.publishPass
    publishExitActiveGame = realtime.publishExitActiveGame
    connectGame = realtime.connectGame
    isGameCommandReady = realtime.isGameCommandReady
    disconnect = realtime.disconnect
  },
}))


const fixture: GamePrivateState = {
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

function markGameCommandReady(store: ReturnType<typeof useGameStore>): void {
  store.connectionState = 'CONNECTED'
  store.gameSubscriptionsReady = true
  store.privateStateLoaded = true
  store.privateStateVersion = store.privateState?.publicState.gameVersion ?? -1
}

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

  it('publishes one draw command with a UUID and the current game version while a reply is pending', () => {
    const store = useGameStore()
    store.privateState = structuredClone(fixture)
    store.activeGameId = 33
    markGameCommandReady(store)

    store.drawTile()
    store.drawTile()

    expect(realtime.publishDraw).toHaveBeenCalledTimes(1)
    expect(realtime.publishDraw).toHaveBeenCalledWith(33, {
      actionId: expect.stringMatching(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i),
      gameVersion: 0,
    })
    expect(store.commandInProgress).toBe(true)
    expect(store.pendingActionId).toBe(realtime.publishDraw.mock.calls[0]?.[1].actionId)
    expect(store.pendingBaseVersion).toBe(0)
  })

  it('publishes one active-game exit and keeps all game state until an authoritative success arrives', () => {
    const store = useGameStore()
    store.privateState = structuredClone(fixture)
    store.activeGameId = 33
    markGameCommandReady(store)

    const actionId = store.exitActiveGame()
    store.exitActiveGame()

    expect(actionId).toBeTruthy()
    expect(realtime.publishExitActiveGame).toHaveBeenCalledTimes(1)
    expect(realtime.publishExitActiveGame).toHaveBeenCalledWith(33, {
      actionId,
      gameVersion: 0,
      roomId: 10,
    })
    expect(store.privateState).not.toBeNull()
    expect(store.activeGameId).toBe(33)
    expect(store.pendingActionType).toBe('EXIT_ACTIVE_GAME')
  })

  it('preserves the game after a rejected exit and clears only the pending request', () => {
    const store = useGameStore()
    store.privateState = structuredClone(fixture)
    store.activeGameId = 33
    markGameCommandReady(store)
    const actionId = store.exitActiveGame()!

    store.applyGameCommandReply({
      eventType: 'GAME_COMMAND_REJECTED',
      actionId,
      accepted: false,
      duplicate: false,
      code: 'STALE_GAME_VERSION',
      message: '최신 상태가 필요합니다.',
      gameId: 33,
      actionType: 'EXIT_ACTIVE_GAME',
      gameVersion: 0,
    })

    expect(store.privateState?.publicState.gameId).toBe(33)
    expect(store.activeGameId).toBe(33)
    expect(store.commandInProgress).toBe(false)
    expect(store.lastCommandError).toBe('최신 상태가 필요합니다.')
  })

  it('cleans the complete game context once when a terminal broadcast arrives before the reply', () => {
    const store = useGameStore()
    store.privateState = structuredClone(fixture)
    store.activeGameId = 33
    store.turnPreview = {
      gameId: 33,
      baseGameVersion: 0,
      previewRevision: 1,
      turnPlayerId: 2,
      updatedAt: '',
      tilePlacements: [],
    }
    store.pendingRackAddition = { gameVersion: 1, tileIds: ['RED-01-A'] }
    markGameCommandReady(store)
    const actionId = store.exitActiveGame()!

    store.applyGameEvent({
      eventType: 'GAME_TERMINATED',
      occurredAt: '2026-07-19T10:00:00Z',
      payload: {
        roomId: 10,
        gameId: 33,
        gameVersion: 1,
        roomStatus: 'CLOSED',
        gameStatus: 'FINISHED',
        terminationReason: 'PLAYER_FORFEIT',
        exitedParticipantId: 100,
        exitedUserId: 1,
        winnerParticipantId: 101,
        winnerUserId: 2,
        serverTime: '2026-07-19T10:00:00Z',
      },
    })
    store.applyGameCommandReply({
      eventType: 'GAME_COMMAND_ACCEPTED', actionId, accepted: true, duplicate: false,
      code: null, message: 'ok', gameId: 33, actionType: 'EXIT_ACTIVE_GAME', gameVersion: 1,
    })

    expect(store.activeGameId).toBeNull()
    expect(store.privateState).toBeNull()
    expect(store.turnPreview).toBeNull()
    expect(store.pendingRackAddition).toBeNull()
    expect(store.pendingActionId).toBeNull()
    expect(store.reconnectRecoveryInProgress).toBe(false)
    expect(store.terminalRevision).toBe(1)
    expect(store.terminalRoomId).toBe(10)
    expect(store.terminationNotice).toContain('포기')
    expect(realtime.disconnect).toHaveBeenCalledTimes(1)
  })

  it('shows a victory notice when my final valid commit exhausts my Rack', () => {
    const store = useGameStore()
    store.privateState = structuredClone(fixture)
    store.activeGameId = 33

    store.applyGameEvent({
      eventType: 'GAME_TERMINATED',
      occurredAt: '2026-07-23T06:00:00Z',
      payload: {
        roomId: 10,
        gameId: 33,
        gameVersion: 1,
        roomStatus: 'CLOSED',
        gameStatus: 'FINISHED',
        terminationReason: 'RACK_EXHAUSTED',
        exitedParticipantId: null,
        exitedUserId: null,
        winnerParticipantId: 100,
        winnerUserId: 1,
        serverTime: '2026-07-23T06:00:00Z',
      },
    })

    expect(store.terminationNotice).toContain('승리')
    expect(store.lastMessage).toContain('승리')
    expect(store.activeGameId).toBeNull()
    expect(store.privateState).toBeNull()
    expect(store.terminalRevision).toBe(1)
  })

  it('shows an opponent Rack exhaustion notice to a losing player', () => {
    const store = useGameStore()
    store.privateState = structuredClone(fixture)
    store.activeGameId = 33

    store.applyGameEvent({
      eventType: 'GAME_TERMINATED',
      occurredAt: '2026-07-23T06:00:00Z',
      payload: {
        roomId: 10,
        gameId: 33,
        gameVersion: 1,
        roomStatus: 'CLOSED',
        gameStatus: 'FINISHED',
        terminationReason: 'RACK_EXHAUSTED',
        exitedParticipantId: null,
        exitedUserId: null,
        winnerParticipantId: 101,
        winnerUserId: 2,
        serverTime: '2026-07-23T06:00:00Z',
      },
    })

    expect(store.terminationNotice).toContain('상대 플레이어')
    expect(store.terminationNotice).toContain('소진')
    expect(store.terminationNotice).not.toContain('승리했습니다')
    expect(store.terminalRevision).toBe(1)
  })

  it('also cleans once when the successful reply arrives before the terminal broadcast', () => {
    const store = useGameStore()
    store.privateState = structuredClone(fixture)
    store.activeGameId = 33
    markGameCommandReady(store)
    const actionId = store.exitActiveGame()!

    store.applyGameCommandReply({
      eventType: 'GAME_COMMAND_ACCEPTED', actionId, accepted: true, duplicate: false,
      code: null, message: 'ok', gameId: 33, actionType: 'EXIT_ACTIVE_GAME', gameVersion: 1,
    })
    store.applyGameEvent({
      eventType: 'GAME_TERMINATED', occurredAt: '',
      payload: {
        roomId: 10, gameId: 33, gameVersion: 1, roomStatus: 'CLOSED', gameStatus: 'FINISHED',
        terminationReason: 'PLAYER_FORFEIT', exitedParticipantId: 100, exitedUserId: 1,
        winnerParticipantId: 101, winnerUserId: 2, serverTime: '',
      },
    })

    expect(store.activeGameId).toBeNull()
    expect(store.terminalRevision).toBe(1)
    expect(store.terminalRoomId).toBe(10)
    expect(realtime.disconnect).toHaveBeenCalledTimes(1)
  })

  it('applies TILE_DRAWN publicly without inventing the private tile and accepts same-version private rack state', () => {
    const store = useGameStore()
    store.privateState = structuredClone(fixture)
    store.activeGameId = 33

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

    expect(store.publicState?.gameVersion).toBe(1)
    expect(store.publicState?.tilePoolCount).toBe(77)
    expect(store.publicState?.players[0]?.rackTileCount).toBe(15)
    expect(store.myRack).toHaveLength(14)
    expect(store.drawMotionRevision).toBe(0)

    const privateUpdate = structuredClone(fixture)
    privateUpdate.publicState = {
      ...store.publicState!,
      players: store.publicState!.players.map((player) => ({ ...player })),
      tableMelds: [...store.publicState!.tableMelds],
    }
    privateUpdate.myRack.push({
      tileId: 'BLUE-13-B',
      tileType: 'NUMBER',
      color: 'BLUE',
      number: 13,
      joker: false,
      positionOrder: 14,
    })
    store.applyPrivateStateEvent({
      eventType: 'GAME_STATE_UPDATED',
      occurredAt: '2026-07-15T07:00:01Z',
      payload: privateUpdate,
    })

    expect(store.myRack).toHaveLength(15)
    expect(store.myRack.at(-1)?.tileId).toBe('BLUE-13-B')
    expect(store.drawMotionRevision).toBe(1)
    expect(store.drawMotionTileIds).toEqual(['BLUE-13-B'])

    store.applyGameEvent({
      eventType: 'TILE_DRAWN',
      occurredAt: '2026-07-15T07:00:02Z',
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
    expect(store.drawMotionRevision).toBe(1)
  })

  it('confirms Draw motion when the private rack event arrives before TILE_DRAWN', () => {
    const store = useGameStore()
    store.privateState = structuredClone(fixture)
    store.activeGameId = 33
    const privateUpdate = structuredClone(fixture)
    privateUpdate.publicState.gameVersion = 1
    privateUpdate.myRack.push({
      tileId: 'BLACK-09-B',
      tileType: 'NUMBER',
      color: 'BLACK',
      number: 9,
      joker: false,
      positionOrder: 14,
    })

    store.applyPrivateStateEvent({ eventType: 'GAME_STATE_UPDATED', occurredAt: '', payload: privateUpdate })
    expect(store.drawMotionRevision).toBe(0)

    store.applyGameEvent({
      eventType: 'TILE_DRAWN',
      occurredAt: '',
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

    expect(store.drawMotionRevision).toBe(1)
    expect(store.drawMotionTileIds).toEqual(['BLACK-09-B'])
  })

  it('keeps a pending draw locked when a same-base-version private state arrives late', () => {
    const store = useGameStore()
    const current = structuredClone(fixture)
    current.publicState.gameVersion = 3
    store.privateState = current
    store.activeGameId = 33
    markGameCommandReady(store)

    store.drawTile()
    const pendingActionId = realtime.publishDraw.mock.calls[0]?.[1].actionId

    store.applyPrivateStateEvent({
      eventType: 'GAME_STATE_UPDATED',
      occurredAt: '2026-07-15T07:00:01Z',
      payload: structuredClone(current),
    })

    expect(store.commandInProgress).toBe(true)
    expect(store.pendingActionId).toBe(pendingActionId)
    expect(store.pendingBaseVersion).toBe(3)
  })

  it('does not let a late reply for an older action release the current pending command', () => {
    const store = useGameStore()
    store.privateState = structuredClone(fixture)
    store.activeGameId = 33
    markGameCommandReady(store)

    store.drawTile()
    const pendingActionId = realtime.publishDraw.mock.calls[0]?.[1].actionId

    store.applyGameCommandReply({
      eventType: 'GAME_COMMAND_REJECTED',
      actionId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      accepted: false,
      duplicate: false,
      code: 'STALE_GAME_VERSION',
      message: '과거 요청 응답입니다.',
      gameId: 33,
      actionType: 'DRAW',
      gameVersion: 0,
    })

    expect(store.commandInProgress).toBe(true)
    expect(store.pendingActionId).toBe(pendingActionId)
    expect(store.pendingBaseVersion).toBe(0)
    expect(store.lastCommandReply).toBeNull()
    expect(gameApi.getGameState).not.toHaveBeenCalled()
  })

  it('releases a matching accepted reply only after a committed newer private state is observed', () => {
    const store = useGameStore()
    store.privateState = structuredClone(fixture)
    store.activeGameId = 33
    markGameCommandReady(store)

    store.drawTile()
    const pendingActionId = realtime.publishDraw.mock.calls[0]?.[1].actionId

    store.applyGameCommandReply({
      eventType: 'GAME_COMMAND_ACCEPTED',
      actionId: pendingActionId!,
      accepted: true,
      duplicate: false,
      code: null,
      message: '게임 행동을 처리했습니다.',
      gameId: 33,
      actionType: 'DRAW',
      gameVersion: 1,
    })

    expect(store.commandInProgress).toBe(true)
    expect(store.pendingActionId).toBe(pendingActionId)

    const committed = structuredClone(fixture)
    committed.publicState.gameVersion = 1
    committed.publicState.currentTurnUserId = 2
    committed.publicState.currentTurnSeatOrder = 2
    committed.myRack.push({
      tileId: 'BLUE-13-B',
      tileType: 'NUMBER',
      color: 'BLUE',
      number: 13,
      joker: false,
      positionOrder: 14,
    })
    store.applyPrivateStateEvent({
      eventType: 'GAME_STATE_UPDATED',
      occurredAt: '2026-07-15T07:00:02Z',
      payload: committed,
    })

    expect(store.commandInProgress).toBe(false)
    expect(store.pendingActionId).toBeNull()
    expect(store.pendingBaseVersion).toBeNull()
    expect(store.myRack).toHaveLength(15)
  })

  it('releases a matching rejected reply immediately', () => {
    const store = useGameStore()
    store.privateState = structuredClone(fixture)
    store.activeGameId = 33
    markGameCommandReady(store)

    store.drawTile()
    const pendingActionId = realtime.publishDraw.mock.calls[0]?.[1].actionId

    store.applyGameCommandReply({
      eventType: 'GAME_COMMAND_REJECTED',
      actionId: pendingActionId!,
      accepted: false,
      duplicate: false,
      code: 'DRAW_POOL_EMPTY',
      message: 'Pool이 비어 있습니다.',
      gameId: 33,
      actionType: 'DRAW',
      gameVersion: 0,
    })

    expect(store.commandInProgress).toBe(false)
    expect(store.pendingActionId).toBeNull()
    expect(store.pendingBaseVersion).toBeNull()
    expect(store.lastCommandError).toContain('Pool')
  })

  it('ignores lower versions and reloads REST state when an event skips a version', async () => {
    const store = useGameStore()
    const current = structuredClone(fixture)
    current.publicState.gameVersion = 3
    store.privateState = current
    store.activeGameId = 33
    const restored = structuredClone(fixture)
    restored.publicState.gameVersion = 5
    restored.publicState.turnNumber = 6
    vi.mocked(gameApi.getGameState).mockResolvedValue(restored)

    store.applyGameEvent({
      eventType: 'TURN_PASSED',
      occurredAt: '',
      payload: {
        gameId: 33,
        gameVersion: 2,
        passedByUserId: 2,
        tilePoolCount: 0,
        nextTurnUserId: 1,
        nextTurnSeatOrder: 1,
        turnNumber: 3,
        currentTurnId: '22222222-2222-4222-8222-222222222222',
        currentTurnStartedAt: '2026-07-15T07:00:01Z',
        turnDeadlineAt: '2026-07-15T07:02:01Z',
        consecutivePassCount: 1,
      },
    })
    expect(store.publicState?.gameVersion).toBe(3)

    store.applyGameEvent({
      eventType: 'TURN_PASSED',
      occurredAt: '',
      payload: {
        gameId: 33,
        gameVersion: 5,
        passedByUserId: 2,
        tilePoolCount: 0,
        nextTurnUserId: 1,
        nextTurnSeatOrder: 1,
        turnNumber: 6,
        currentTurnId: '55555555-5555-4555-8555-555555555555',
        currentTurnStartedAt: '2026-07-15T07:00:03Z',
        turnDeadlineAt: '2026-07-15T07:02:03Z',
        consecutivePassCount: 3,
      },
    })

    await vi.waitFor(() => expect(gameApi.getGameState).toHaveBeenCalledWith(33))
    await vi.waitFor(() => expect(store.publicState?.gameVersion).toBe(5))
    expect(store.publicState?.turnNumber).toBe(6)
  })

  it('reloads the authoritative REST state after a stale-version reply', async () => {
    const store = useGameStore()
    store.privateState = structuredClone(fixture)
    store.activeGameId = 33
    markGameCommandReady(store)
    store.drawTile()
    const pendingActionId = realtime.publishDraw.mock.calls[0]?.[1].actionId
    const restored = structuredClone(fixture)
    restored.publicState.gameVersion = 1
    restored.publicState.currentTurnUserId = 2
    restored.publicState.currentTurnSeatOrder = 2
    vi.mocked(gameApi.getGameState).mockResolvedValue(restored)

    store.applyGameCommandReply({
      eventType: 'GAME_COMMAND_REJECTED',
      actionId: pendingActionId!,
      accepted: false,
      duplicate: false,
      code: 'STALE_GAME_VERSION',
      message: '게임 상태가 변경되었습니다.',
      gameId: 33,
      actionType: 'DRAW',
      gameVersion: 0,
    })

    await vi.waitFor(() => expect(store.publicState?.gameVersion).toBe(1))
    expect(store.commandInProgress).toBe(false)
    expect(store.lastCommandError).toContain('게임 상태')
  })

})

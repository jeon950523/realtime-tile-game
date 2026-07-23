import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { effectScope, nextTick, ref } from 'vue'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as gameApi from '@/api/gameApi'
import CommittedTableBoard from '@/components/game/CommittedTableBoard.vue'
import TurnActionControl from '@/components/game/TurnActionControl.vue'
import { useTurnDraft } from '@/composables/game/useTurnDraft'
import { resetGameStoreClientForTests, useGameStore } from '@/stores/game'
import type { CommitTilePlacementCommand, GamePrivateState, GameRackTile, GameTableMeld } from '@/types/game'

const realtime = vi.hoisted(() => ({
  publishCommit: vi.fn(), publishDraw: vi.fn(), publishPass: vi.fn(), isGameCommandReady: vi.fn(() => true),
}))
vi.mock('@/api/gameApi')
vi.mock('@/realtime/authenticatedStompClient', () => ({
  AuthenticatedStompClient: class {
    publishCommit = realtime.publishCommit
    publishDraw = realtime.publishDraw
    publishPass = realtime.publishPass
    connectGame = vi.fn(async () => undefined)
    isGameCommandReady = realtime.isGameCommandReady
    disconnect = vi.fn(async () => undefined)
  },
}))

const rack: GameRackTile[] = [7, 8, 9].map((number, index) => ({
  tileId: `RED-${String(number).padStart(2, '0')}-A`, tileType: 'NUMBER', color: 'RED', number, joker: false, positionOrder: index,
}))


function commitPlacements(
  tileIds: readonly string[] = rack.map((tile) => tile.tileId),
  gridRow = 0,
  gridColumn = 0,
): CommitTilePlacementCommand[] {
  return tileIds.map((tileId, offset) => ({
    tileId,
    gridRow,
    gridColumn: gridColumn + offset,
  }))
}

function state(version = 0): GamePrivateState {
  return {
    publicState: {
      gameId: 33, roomId: 10, gameMode: 'CLASSIC', status: 'IN_PROGRESS', gameVersion: version,
      currentTurnUserId: 1, currentTurnSeatOrder: 1, turnNumber: 1,
      currentTurnId: '11111111-1111-4111-8111-111111111111', currentTurnStartedAt: '2026-01-01T00:00:00Z',
      turnDeadlineAt: '2026-01-01T00:02:00Z', consecutivePassCount: 0, startedAt: '2026-01-01T00:00:00Z',
      tilePoolCount: 78, tableMelds: [],
      players: [
        { userId: 1, nickname: 'one', avatarType: 'DEFAULT_01', seatOrder: 1, rackTileCount: rack.length, initialMeldCompleted: false, currentTurn: true },
        { userId: 2, nickname: 'two', avatarType: 'DEFAULT_02', seatOrder: 2, rackTileCount: 14, initialMeldCompleted: false, currentTurn: false },
      ],
    },
    myPlayerId: 11, myUserId: 1, mySeatOrder: 1, myRack: structuredClone(rack),
  }
}

describe('Phase 7 commit frontend synchronization', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    resetGameStoreClientForTests()
    vi.resetAllMocks()
  })

  function storeWithState() {
    const store = useGameStore()
    store.privateState = state()
    store.activeGameId = 33
    store.connectionState = 'CONNECTED'
    store.gameSubscriptionsReady = true
    store.privateStateLoaded = true
    store.privateStateVersion = store.privateState.publicState.gameVersion
    return store
  }

  it('COMMIT-FE-001 shows commit controls instead of Draw or PASS when a draft exists', () => {
    const wrapper = mount(TurnActionControl, { props: {
      poolCount: 78, canDraw: false, canPass: false, commandInProgress: false, isMyTurn: true,
      hasDraft: true, canCommit: true, canUndo: true,
    } })
    expect(wrapper.find('[data-action="commit"]').exists()).toBe(true)
    expect(wrapper.find('[data-action="draw"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('Undo')
    expect(wrapper.text()).toContain('Cancel')
  })

  it('COMMIT-FE-002 publishes only action/version and tile placements', () => {
    const store = storeWithState()
    const actionId = store.commitTurn(commitPlacements())
    expect(actionId).toBeTruthy()
    const command = realtime.publishCommit.mock.calls[0]![1]
    expect(Object.keys(command).sort()).toEqual(['actionId', 'gameVersion', 'tilePlacements'])
    expect(Object.keys(command.tilePlacements[0]).sort()).toEqual(['gridColumn', 'gridRow', 'tileId'])
    expect(JSON.stringify(command)).not.toContain('score')
    expect(JSON.stringify(command)).not.toContain('meldType')
  })

  it('COMMIT-FE-003 blocks duplicate commit clicks while one command is pending', () => {
    const store = storeWithState()
    const placements = commitPlacements()
    expect(store.commitTurn(placements)).toBeTruthy()
    expect(store.commitTurn(placements)).toBeNull()
    expect(realtime.publishCommit).toHaveBeenCalledTimes(1)
  })

  it('COMMIT-FE-004 keeps pending state when accepted reply arrives before private state', () => {
    const store = storeWithState()
    const actionId = store.commitTurn(commitPlacements())!
    store.applyGameCommandReply({ eventType: 'GAME_COMMAND_ACCEPTED', actionId, accepted: true, duplicate: false,
      code: null, message: 'ok', gameId: 33, actionType: 'COMMIT', gameVersion: 1 })
    expect(store.commandInProgress).toBe(true)
  })

  it('COMMIT-FE-005 accepts private state before a late reply without applying twice', () => {
    const store = storeWithState()
    const actionId = store.commitTurn(commitPlacements())!
    const committed = state(1)
    committed.myRack = []
    store.applyPrivateStateEvent({ eventType: 'GAME_STATE_UPDATED', occurredAt: '', payload: committed })
    expect(store.commandInProgress).toBe(false)
    store.applyGameCommandReply({ eventType: 'GAME_COMMAND_ACCEPTED', actionId, accepted: true, duplicate: false,
      code: null, message: 'ok', gameId: 33, actionType: 'COMMIT', gameVersion: 1 })
    expect(store.privateState?.publicState.gameVersion).toBe(1)
  })

  it('COMMIT-FE-006 validation rejection clears pending without reloading authority', () => {
    const store = storeWithState()
    const actionId = store.commitTurn(commitPlacements())!
    store.applyGameCommandReply({ eventType: 'GAME_COMMAND_REJECTED', actionId, accepted: false, duplicate: false,
      code: 'INVALID_MELD', message: 'invalid', gameId: 33, actionType: 'COMMIT', gameVersion: 0 })
    expect(store.commandInProgress).toBe(false)
    expect(store.privateState?.myRack).toHaveLength(3)
    expect(gameApi.getGameState).not.toHaveBeenCalled()
  })

  it('COMMIT-FE-007 stale rejection discards pending command and reloads latest state', async () => {
    const store = storeWithState()
    vi.mocked(gameApi.getGameState).mockResolvedValue(state(1))
    const actionId = store.commitTurn(commitPlacements())!
    store.applyGameCommandReply({ eventType: 'GAME_COMMAND_REJECTED', actionId, accepted: false, duplicate: false,
      code: 'STALE_GAME_VERSION', message: 'stale', gameId: 33, actionType: 'COMMIT', gameVersion: 0 })
    await nextTick()
    expect(gameApi.getGameState).toHaveBeenCalledWith(33)
  })

  it('COMMIT-FE-008 clears draft after authoritative rack and table confirm the commit', async () => {
    const scope = effectScope()
    const authoritativeRack = ref<GameRackTile[]>(structuredClone(rack))
    const version = ref(0)
    const syncRevision = ref(0)
    const table = ref<GameTableMeld[]>([])
    const draft = scope.run(() => useTurnDraft({ authoritativeRack, authoritativeVersion: version, authoritativeSyncRevision: syncRevision, tableMelds: table, initialMeldCompleted: ref(false) }))!
    draft.addAsNewMeld(rack.map((tile) => tile.tileId), rack.map((tile) => tile.tileId))
    const meldId = draft.candidates.value[0]!.clientMeldId
    draft.markCommitPending('action')
    authoritativeRack.value = []
    table.value = [{ meldId, meldType: 'RUN', score: 24, positionOrder: 0, gridRow: 0, gridColumn: 0,
      tiles: rack.map((tile, index) => ({ ...tile, positionOrder: index })) }]
    version.value = 1
    syncRevision.value += 1
    await nextTick()
    expect(draft.draft.value).toBeNull()
    expect(draft.lastResolution.value).toBe('COMMITTED')
    scope.stop()
  })

  it('COMMIT-FE-009 keeps local draft data outside public and private authority contracts', () => {
    const authoritative = state()
    expect(authoritative.publicState).not.toHaveProperty('turnDraft')
    expect(authoritative).not.toHaveProperty('turnDraft')
  })

  it('COMMIT-FE-010 renders typed committed table melds after refresh', () => {
    const meld: GameTableMeld = {
      meldId: 'a', meldType: 'RUN', score: 24, positionOrder: 0, gridRow: 0, gridColumn: 0,
      tiles: rack.map((tile, index) => ({ ...tile, positionOrder: index })),
    }
    const wrapper = mount(CommittedTableBoard, { props: { melds: [meld] }, attachTo: document.body })
    expect(wrapper.find('[data-committed-meld-id="a"]').exists()).toBe(true)
    expect(wrapper.findAll('.game-tile')).toHaveLength(3)
    expect(wrapper.text()).not.toContain('RUN')
    expect(wrapper.text()).not.toContain('24점')
    wrapper.unmount()
  })

  it('COMMIT-FE-011 sends the entire candidate table including retained existing melds', () => {
    const store = storeWithState()
    const candidate: CommitTilePlacementCommand[] = [
      ...commitPlacements(['TABLE-01', 'TABLE-02', 'RED-07-A'], 0, 0),
      ...commitPlacements(['RED-08-A', 'RED-09-A'], 0, 13),
    ]
    store.commitTurn(candidate)
    expect(realtime.publishCommit.mock.calls[0]![1].tilePlacements).toEqual([
      { tileId: 'TABLE-01', gridRow: 0, gridColumn: 0 },
      { tileId: 'TABLE-02', gridRow: 0, gridColumn: 1 },
      { tileId: 'RED-07-A', gridRow: 0, gridColumn: 2 },
      { tileId: 'RED-08-A', gridRow: 0, gridColumn: 13 },
      { tileId: 'RED-09-A', gridRow: 0, gridColumn: 14 },
    ])
  })

  it('COMMIT-FE-012 never sends client validation, score, type, or creator authority', () => {
    const store = storeWithState()
    store.commitTurn(commitPlacements())
    const serialized = JSON.stringify(realtime.publishCommit.mock.calls[0]![1])
    expect(serialized).not.toMatch(/score|meldType|valid|createdBy/)
  })

  it('COMMIT-FE-013 does not grant meld identity authority to the client command', () => {
    const store = storeWithState()
    const existingId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa'
    store.commitTurn(commitPlacements())
    expect(JSON.stringify(realtime.publishCommit.mock.calls[0]![1])).not.toContain(existingId)
  })

  it('COMMIT-FE-014 derives a deterministic local Candidate ID from tile placement', () => {
    const scope = effectScope()
    const draft = scope.run(() => useTurnDraft({
      authoritativeRack: ref(rack), authoritativeVersion: ref(0), authoritativeSyncRevision: ref(0),
      tableMelds: ref<GameTableMeld[]>([]), initialMeldCompleted: ref(false),
    }))!
    draft.addAsNewMeld(rack.map((tile) => tile.tileId), rack.map((tile) => tile.tileId))
    expect(draft.candidates.value[0]!.clientMeldId).toBe('candidate:0:0:RED-07-A|RED-08-A|RED-09-A')
    expect(draft.candidates.value[0]!.sourceMeldId).toBeNull()
    scope.stop()
  })

  it('COMMIT-FE-015 treats private snapshot then late accepted reply as one authoritative commit', () => {
    const store = storeWithState()
    const actionId = store.commitTurn(commitPlacements())!
    const committed = state(1)
    committed.myRack = []
    store.applyPrivateStateEvent({ eventType: 'GAME_STATE_UPDATED', occurredAt: '', payload: committed })
    store.applyGameCommandReply({ eventType: 'GAME_COMMAND_ACCEPTED', actionId, accepted: true, duplicate: false,
      code: null, message: 'ok', gameId: 33, actionType: 'COMMIT', gameVersion: 1 })
    expect(store.privateState?.publicState.gameVersion).toBe(1)
    expect(store.commandInProgress).toBe(false)
  })
  it('COMMIT-FE-016 rejects duplicate tiles, overlapping cells, and out-of-range placements at the Store boundary', () => {
    const store = storeWithState()
    const duplicateTile = [
      { tileId: 'RED-07-A', gridRow: 0, gridColumn: 0 },
      { tileId: 'RED-07-A', gridRow: 0, gridColumn: 1 },
    ]
    const overlappingCells = [
      { tileId: 'RED-07-A', gridRow: 0, gridColumn: 0 },
      { tileId: 'RED-08-A', gridRow: 0, gridColumn: 0 },
    ]
    const outOfRange = [
      { tileId: 'RED-07-A', gridRow: 18, gridColumn: 0 },
    ]

    expect(store.commitTurn(duplicateTile)).toBeNull()
    expect(store.commitTurn(overlappingCells)).toBeNull()
    expect(store.commitTurn(outOfRange)).toBeNull()
    expect(realtime.publishCommit).not.toHaveBeenCalled()
  })

})

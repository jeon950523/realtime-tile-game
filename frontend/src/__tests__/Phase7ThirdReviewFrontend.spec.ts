import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { effectScope, nextTick, ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import * as gameApi from '@/api/gameApi'
import DraftMeld from '@/components/game/DraftMeld.vue'
import TileRack from '@/components/game/TileRack.vue'
import WorkingTableBoard from '@/components/game/WorkingTableBoard.vue'
import { useWorkingTable } from '@/composables/game/useWorkingTable'
import { deriveTableCandidates } from '@/domain/game/tableCandidateDerivation'
import { validateDraftMeld, validateTurnDraft } from '@/domain/game/turnDraftValidation'
import {
  GAME_ACCEPTED_STATE_GRACE_MS,
  GAME_COMMAND_RESPONSE_TIMEOUT_MS,
  resetGameStoreClientForTests,
  useGameStore,
} from '@/stores/game'
import type { CommitTilePlacementCommand, GamePrivateState, GameRackTile, GameTableMeld, GameTableTile } from '@/types/game'
import type { WorkingTilePlacement } from '@/types/turnDraft'

const realtime = vi.hoisted(() => ({
  options: null as Record<string, (...args: any[]) => any> | null,
  publishCommit: vi.fn(),
  publishDraw: vi.fn(),
  publishPass: vi.fn(),
  connectGame: vi.fn(async () => undefined),
  disconnect: vi.fn(async () => undefined),
}))

vi.mock('@/api/gameApi')
vi.mock('@/realtime/authenticatedStompClient', () => ({
  AuthenticatedStompClient: class {
    constructor(options: Record<string, (...args: any[]) => any>) { realtime.options = options }
    publishCommit = realtime.publishCommit
    publishDraw = realtime.publishDraw
    publishPass = realtime.publishPass
    connectGame = realtime.connectGame
    disconnect = realtime.disconnect
    isGameCommandReady = vi.fn(() => true)
  },
}))


interface CandidateSpec {
  clientMeldId: string
  sourceMeldId: string | null
  tileIds: readonly string[]
  gridRow?: number
  gridColumn?: number
}

function candidateSpecsToPlacements(
  specs: readonly CandidateSpec[],
  rack: readonly GameRackTile[],
): WorkingTilePlacement[] {
  const rackIds = new Set(rack.map((tile) => tile.tileId))
  return specs.flatMap((spec) => spec.tileIds.map((tileId, offset) => {
    const fromRack = rackIds.has(tileId)
    return {
      tileId,
      gridRow: spec.gridRow ?? 0,
      gridColumn: (spec.gridColumn ?? 0) + offset,
      source: fromRack ? 'CURRENT_PLAYER_RACK' as const : 'COMMITTED_TABLE' as const,
      sourceMeldId: fromRack ? null : spec.sourceMeldId,
      originalPositionOrder: fromRack ? null : offset,
    }
  }))
}

function numberTile(color: GameRackTile['color'], number: number, copy = 'A', order = number): GameRackTile {
  return {
    tileId: `${color}-${String(number).padStart(2, '0')}-${copy}`,
    tileType: 'NUMBER', color, number, joker: false, positionOrder: order,
  }
}

function jokerTile(order = 0): GameRackTile {
  return { tileId: `JOKER-${order}`, tileType: 'JOKER', color: null, number: null, joker: true, positionOrder: order }
}

function tableRun(meldId: string, numbers: number[]): GameTableMeld {
  return {
    meldId, meldType: 'RUN', score: numbers.reduce((sum, number) => sum + number, 0), positionOrder: 0,
    gridRow: 0, gridColumn: 0,
    tiles: numbers.map((number, index): GameTableTile => ({ ...numberTile('RED', number, 'A', index), positionOrder: index })),
  }
}

const existingMeldId = '11111111-1111-4111-8111-111111111111'
const rack12 = numberTile('RED', 12, 'B', 0)

function privateState(version = 0): GamePrivateState {
  return {
    publicState: {
      gameId: 33, roomId: 10, gameMode: 'CLASSIC', status: 'IN_PROGRESS', gameVersion: version,
      currentTurnUserId: 2, currentTurnSeatOrder: 2, turnNumber: 2,
      currentTurnId: '22222222-2222-4222-8222-222222222222', currentTurnStartedAt: '2026-07-18T00:00:00Z',
      turnDeadlineAt: '2026-07-18T00:02:00Z', consecutivePassCount: 0, startedAt: '2026-07-18T00:00:00Z',
      tilePoolCount: 70, tableMelds: [tableRun(existingMeldId, [9, 10, 11])],
      players: [
        { userId: 1, nickname: 'A', avatarType: 'DEFAULT_01', seatOrder: 1, rackTileCount: 13, initialMeldCompleted: true, currentTurn: false },
        { userId: 2, nickname: 'B', avatarType: 'DEFAULT_02', seatOrder: 2, rackTileCount: 1, initialMeldCompleted: true, currentTurn: true },
      ],
    },
    myPlayerId: 22, myUserId: 2, mySeatOrder: 2, myRack: [rack12],
  }
}

function readyStore() {
  const store = useGameStore()
  store.privateState = privateState()
  store.activeGameId = 33
  store.connectionState = 'CONNECTED'
  store.gameSubscriptionsReady = true
  store.privateStateLoaded = true
  store.privateStateVersion = 0
  return store
}

function candidate(): CommitTilePlacementCommand[] {
  const committed = privateState().publicState.tableMelds[0]!
  return [
    ...committed.tiles.map((tile) => ({
      tileId: tile.tileId,
      gridRow: committed.gridRow,
      gridColumn: committed.gridColumn + tile.positionOrder,
    })),
    { tileId: rack12.tileId, gridRow: committed.gridRow, gridColumn: committed.gridColumn + committed.tiles.length },
  ]
}

function committedState(): GamePrivateState {
  const state = privateState(1)
  state.myRack = []
  state.publicState.tableMelds = [{
    ...state.publicState.tableMelds[0]!, score: 42,
    tiles: [...state.publicState.tableMelds[0]!.tiles, { ...rack12, positionOrder: 3 }],
  }]
  state.publicState.currentTurnUserId = 1
  state.publicState.currentTurnSeatOrder = 1
  return state
}

beforeEach(() => {
  setActivePinia(createPinia())
  resetGameStoreClientForTests()
  realtime.options = null
  vi.resetAllMocks()
})

afterEach(() => {
  vi.useRealTimers()
  resetGameStoreClientForTests()
})

describe('Phase 7 third review command lifecycle', () => {
  it('FE-P7-001 blocks Commit until every game command subscription is ready', () => {
    const store = readyStore()
    store.gameSubscriptionsReady = false
    expect(store.commitTurn(candidate())).toBeNull()
    expect(realtime.publishCommit).not.toHaveBeenCalled()
  })

  it('FE-P7-002 clears Pending immediately when publish throws and keeps the candidate retryable', () => {
    const store = readyStore()
    realtime.publishCommit.mockImplementationOnce(() => { throw new Error('destination not ready') })
    expect(store.commitTurn(candidate())).toBeNull()
    expect(store.commandInProgress).toBe(false)
    expect(store.lastCommandError).toBe('TurnDraft를 확정하지 못했습니다.')
  })

  it('FE-P7-003 clears Pending on a validation rejection without replacing authority', () => {
    const store = readyStore()
    const before = JSON.stringify(store.privateState)
    const actionId = store.commitTurn(candidate())!
    store.applyGameCommandReply({ eventType: 'GAME_COMMAND_REJECTED', actionId, accepted: false, duplicate: false,
      code: 'INVALID_MELD', message: 'invalid', gameId: 33, actionType: 'COMMIT', gameVersion: 0 })
    expect(store.commandInProgress).toBe(false)
    expect(JSON.stringify(store.privateState)).toBe(before)
    expect(gameApi.getGameState).not.toHaveBeenCalled()
  })

  it('FE-P7-004 cancels the accepted-reply timer when the matching Private State arrives', async () => {
    vi.useFakeTimers()
    const store = readyStore()
    const actionId = store.commitTurn(candidate())!
    store.applyGameCommandReply({ eventType: 'GAME_COMMAND_ACCEPTED', actionId, accepted: true, duplicate: false,
      code: null, message: 'ok', gameId: 33, actionType: 'COMMIT', gameVersion: 1 })
    store.applyPrivateStateEvent({ eventType: 'GAME_STATE_UPDATED', occurredAt: '', payload: committedState() })
    await vi.advanceTimersByTimeAsync(GAME_ACCEPTED_STATE_GRACE_MS + 1)
    expect(store.commandInProgress).toBe(false)
    expect(gameApi.getGameState).not.toHaveBeenCalled()
  })

  it('FE-P7-005 recovers by REST when accepted Reply is not followed by Private State', async () => {
    vi.useFakeTimers()
    vi.mocked(gameApi.getGameState).mockResolvedValue(committedState())
    const store = readyStore()
    const actionId = store.commitTurn(candidate())!
    store.applyGameCommandReply({ eventType: 'GAME_COMMAND_ACCEPTED', actionId, accepted: true, duplicate: false,
      code: null, message: 'ok', gameId: 33, actionType: 'COMMIT', gameVersion: 1 })
    await vi.advanceTimersByTimeAsync(GAME_ACCEPTED_STATE_GRACE_MS)
    expect(gameApi.getGameState).toHaveBeenCalledWith(33)
    expect(store.pendingRecoveryOutcome).toBe('COMMITTED')
    expect(store.commandInProgress).toBe(false)
  })

  it('FE-P7-006 recovers by REST when neither Reply nor State arrives before timeout', async () => {
    vi.useFakeTimers()
    vi.mocked(gameApi.getGameState).mockResolvedValue(privateState())
    const store = readyStore()
    store.commitTurn(candidate())
    await vi.advanceTimersByTimeAsync(GAME_COMMAND_RESPONSE_TIMEOUT_MS)
    expect(gameApi.getGameState).toHaveBeenCalledWith(33)
    expect(store.pendingRecoveryOutcome).toBe('RETRYABLE')
    expect(store.commandInProgress).toBe(false)
  })

  it('FE-P7-007 performs authoritative recovery when the command transport disconnects', async () => {
    vi.mocked(gameApi.getGameState).mockResolvedValue(privateState())
    const store = readyStore()
    store.client()
    store.commitTurn(candidate())
    realtime.options?.onCommandTransportInterrupted?.('disconnect')
    await vi.waitFor(() => expect(store.commandInProgress).toBe(false))
    expect(store.pendingRecoveryOutcome).toBe('RETRYABLE')
  })

  it('FE-P7-008 performs the same recovery for a STOMP ERROR callback', async () => {
    vi.mocked(gameApi.getGameState).mockResolvedValue(privateState())
    const store = readyStore()
    store.client()
    store.commitTurn(candidate())
    realtime.options?.onCommandTransportInterrupted?.('STOMP ERROR')
    await vi.waitFor(() => expect(store.commandInProgress).toBe(false))
    expect(store.lastCommandError).toBe('STOMP ERROR')
  })

  it('FE-P7-009 route leave clears Pending and prevents the old timeout callback', async () => {
    vi.useFakeTimers()
    const store = readyStore()
    store.commitTurn(candidate())
    store.cancelPendingForRouteLeave()
    await vi.advanceTimersByTimeAsync(GAME_COMMAND_RESPONSE_TIMEOUT_MS + 1)
    expect(store.commandInProgress).toBe(false)
    expect(gameApi.getGameState).not.toHaveBeenCalled()
  })

  it('FE-P7-010 ignores a duplicate late Reply after the first action is resolved', () => {
    const store = readyStore()
    const actionId = store.commitTurn(candidate())!
    const rejection = { eventType: 'GAME_COMMAND_REJECTED' as const, actionId, accepted: false, duplicate: false,
      code: 'INVALID_MELD', message: 'invalid', gameId: 33, actionType: 'COMMIT' as const, gameVersion: 0 }
    store.applyGameCommandReply(rejection)
    store.lastCommandError = 'first'
    store.applyGameCommandReply({ ...rejection, duplicate: true, message: 'duplicate' })
    expect(store.lastCommandError).toBe('first')
  })

  it('FE-P7-011 recognizes a reflected Candidate as COMMITTED after timeout recovery', async () => {
    vi.mocked(gameApi.getGameState).mockResolvedValue(committedState())
    const store = readyStore()
    store.commitTurn(candidate())
    await store.recoverPendingCommand('timeout')
    expect(store.pendingRecoveryOutcome).toBe('COMMITTED')
    expect(store.privateState?.myRack).toHaveLength(0)
  })

  it('FE-P7-012 keeps an unreflected Candidate retryable with a fresh action ID', async () => {
    vi.mocked(gameApi.getGameState).mockResolvedValue(privateState())
    const store = readyStore()
    const firstAction = store.commitTurn(candidate())!
    await store.recoverPendingCommand('timeout')
    const secondAction = store.commitTurn(candidate())!
    expect(store.pendingRecoveryOutcome).toBe('NONE')
    expect(secondAction).not.toBe(firstAction)
    expect(realtime.publishCommit).toHaveBeenCalledTimes(2)
  })
})

describe('Phase 7 final reconnect Store command gate', () => {
  it('STORE-P7-FINAL-001 blocks Commit while authoritative recovery is in progress', () => {
    const store = readyStore()
    store.reconnectRecoveryInProgress = true

    expect(store.commitTurn(candidate())).toBeNull()
    expect(realtime.publishCommit).not.toHaveBeenCalled()
  })

  it('STORE-P7-FINAL-002 blocks Draw while authoritative recovery is in progress', () => {
    const store = readyStore()
    store.reconnectRecoveryInProgress = true

    store.drawTile()

    expect(realtime.publishDraw).not.toHaveBeenCalled()
    expect(store.commandInProgress).toBe(false)
  })

  it('STORE-P7-FINAL-003 blocks Pass while authoritative recovery is in progress', () => {
    const store = readyStore()
    store.reconnectRecoveryInProgress = true

    store.passTurn()

    expect(realtime.publishPass).not.toHaveBeenCalled()
    expect(store.commandInProgress).toBe(false)
  })

  it('STORE-P7-FINAL-004 publishes the first command exactly once after recovery completes', () => {
    const store = readyStore()
    store.reconnectRecoveryInProgress = true
    store.drawTile()
    store.reconnectRecoveryInProgress = false

    store.drawTile()
    store.drawTile()

    expect(realtime.publishDraw).toHaveBeenCalledTimes(1)
  })

  it('STORE-P7-FINAL-005 publishes nothing after recovery failure', () => {
    const store = readyStore()
    store.client()
    realtime.options?.onGameRecoveryStateChange?.(33, true)
    realtime.options?.onGameRecoveryStateChange?.(33, false)
    realtime.options?.onStateChange?.('FAILED', '최신 게임 상태를 복구하지 못했습니다.')

    store.commitTurn(candidate())
    store.drawTile()
    store.passTurn()

    expect(realtime.publishCommit).not.toHaveBeenCalled()
    expect(realtime.publishDraw).not.toHaveBeenCalled()
    expect(realtime.publishPass).not.toHaveBeenCalled()
    expect(store.gameCommandReady).toBe(false)
  })

  it('STORE-P7-FINAL-006 ignores a REST recovery result after route leave', async () => {
    let resolveRecovery!: (state: GamePrivateState) => void
    vi.mocked(gameApi.getGameState).mockImplementation(() => new Promise((resolve) => {
      resolveRecovery = resolve
    }))
    const store = readyStore()
    store.client()
    realtime.options?.onGameRecoveryStateChange?.(33, true)
    const recovery = realtime.options?.onGameReconnect?.(33) as Promise<boolean>

    store.cancelPendingForRouteLeave()
    store.clearGameState()
    resolveRecovery(committedState())

    expect(await recovery).toBe(false)
    expect(store.activeGameId).toBeNull()
    expect(store.privateState).toBeNull()
    expect(store.gameSubscriptionsReady).toBe(false)
  })
})

function workingFixture() {
  const scope = effectScope()
  const rack = ref<GameRackTile[]>([numberTile('RED', 4, 'B'), numberTile('BLUE', 7, 'B')])
  const table = ref<GameTableMeld[]>([
    tableRun(existingMeldId, [1, 2, 3]),
    { ...tableRun('22222222-2222-4222-8222-222222222222', [7]), positionOrder: 1, gridColumn: 13 },
  ])
  const working = scope.run(() => useWorkingTable({
    authoritativeRack: rack, authoritativeVersion: ref(1), authoritativeSyncRevision: ref(0),
    tableMelds: table, initialMeldCompleted: ref(true), isMyTurn: ref(true),
  }))!
  return { scope, rack, table, working }
}

describe('Phase 7 third review button-free Working Table UX', () => {
  function board(specs: CandidateSpec[], rack: GameRackTile[], baseline: GameTableMeld[]) {
    const placements = candidateSpecsToPlacements(specs, rack)
    const candidates = deriveTableCandidates(placements)
    return mount(WorkingTableBoard, { props: {
      placements, rack, baselineMelds: baseline,
      validation: validateTurnDraft(candidates, rack, true, baseline, placements),
      initialMeldCompleted: true, isMeldEditable: () => true,
    } })
  }

  it('FE-P7-UI-001 renders no per-tile manipulation buttons', () => {
    const meld = tableRun(existingMeldId, [1, 2, 3])
    const wrapper = board([{ clientMeldId: meld.meldId, sourceMeldId: meld.meldId, tileIds: meld.tiles.map((tile) => tile.tileId), gridRow: meld.gridRow, gridColumn: meld.gridColumn }], [], [meld])
    expect(wrapper.find('.draft-tile-tools').exists()).toBe(false)
    expect(wrapper.find('[aria-label="Candidate 전체 이동"]').exists()).toBe(false)
    expect(wrapper.findAll('.working-table-tile')).toHaveLength(3)
    expect(wrapper.find('[aria-label="Rack으로 반환"]').exists()).toBe(false)
  })

  it('FE-P7-UI-002 renders no next-meld merge button', () => {
    const meld = tableRun(existingMeldId, [1, 2, 3])
    const wrapper = board([{ clientMeldId: meld.meldId, sourceMeldId: meld.meldId, tileIds: meld.tiles.map((tile) => tile.tileId), gridRow: meld.gridRow, gridColumn: meld.gridColumn }], [], [meld])
    expect(wrapper.text()).not.toContain('다음 Meld와 병합')
    expect(wrapper.find('.working-meld__merge').exists()).toBe(false)
  })

  it('FE-P7-UI-003 exposes same-meld drag insertion at the target index', async () => {
    const meld = tableRun(existingMeldId, [1, 2, 3])
    const working = { clientMeldId: meld.meldId, sourceMeldId: meld.meldId, tileIds: meld.tiles.map((tile) => tile.tileId) }
    const wrapper = mount(DraftMeld, { props: {
      meld: working, validation: validateDraftMeld(working, new Map(meld.tiles.map((tile) => [tile.tileId, tile]))),
      tileById: new Map(meld.tiles.map((tile) => [tile.tileId, tile])), editable: true,
    } })
    const dataTransfer = { types: ['application/x-working-tile-id'], getData: () => meld.tiles[2]!.tileId }
    await wrapper.findAll('.draft-tile-slot')[0]!.trigger('drop', { dataTransfer })
    expect(wrapper.emitted('move')).toBeUndefined()
  })

  it('FE-P7-UI-004 exposes table tiles directly instead of Candidate card drop zones', () => {
    const first = tableRun(existingMeldId, [1, 2, 3])
    const second = tableRun('22222222-2222-4222-8222-222222222222', [4, 5, 6])
    const melds = [first, second].map((meld, index) => ({ clientMeldId: meld.meldId, sourceMeldId: meld.meldId, tileIds: meld.tiles.map((tile) => tile.tileId), gridRow: 0, gridColumn: index * 13 }))
    const wrapper = board(melds, [], [first, second])
    expect(wrapper.findAll('.working-table-tile')).toHaveLength(6)
    expect(wrapper.findAll('.draft-meld')).toHaveLength(0)
  })

  it('FE-P7-UI-005 exposes the empty grid as the new-meld drop contract', () => {
    const meld = tableRun(existingMeldId, [1, 2, 3])
    const wrapper = board([{ clientMeldId: meld.meldId, sourceMeldId: meld.meldId, tileIds: meld.tiles.map((tile) => tile.tileId), gridRow: meld.gridRow, gridColumn: meld.gridColumn }], [], [meld])
    expect(wrapper.find('[data-table-grid-root="true"]').exists()).toBe(true)
    expect(wrapper.find('.working-table-new-drop').exists()).toBe(false)
  })

  it('FE-P7-UI-006 returns a Rack-origin working tile through a Rack drop', async () => {
    const wrapper = mount(TileRack, { props: {
      tiles: [rack12], activeDragTileIds: [], enteringTileIds: [], canDraftDrop: true,
    } })
    const dataTransfer = { getData: () => rack12.tileId }
    await wrapper.find('.tile-rack').trigger('drop', { dataTransfer })
    expect(wrapper.emitted('workingTileReturn')?.[0]).toEqual([rack12.tileId])
  })

  it('FE-P7-UI-007 rejects returning a TABLE-origin tile to Rack', () => {
    const { scope, table, working } = workingFixture()
    expect(working.returnTile(table.value[0]!.tiles[0]!.tileId)).toBe(false)
    scope.stop()
  })

  it('FE-P7-UI-008 compacts an empty source meld after its last tile moves', () => {
    const { scope, working } = workingFixture()
    const source = working.candidates.value[1]!
    expect(working.moveTile(source.tileIds[0]!, existingMeldId)).toBe(true)
    expect(working.candidates.value.some((meld) => meld.clientMeldId === source.clientMeldId)).toBe(false)
    scope.stop()
  })

  it('FE-P7-UI-009 removes the 전체 Rack button from every Candidate', () => {
    const rackMeld = { clientMeldId: 'new', sourceMeldId: null, tileIds: [rack12.tileId] }
    const rackValidation = validateDraftMeld(rackMeld, new Map([[rack12.tileId, rack12]]))
    const rackWrapper = mount(DraftMeld, { props: {
      meld: rackMeld, validation: rackValidation, rackById: new Map([[rack12.tileId, rack12]]), editable: true,
    } })
    expect(rackWrapper.text()).not.toContain('전체 Rack')
    expect(rackWrapper.find('button[aria-label*="Rack"]').exists()).toBe(false)
    const tableTile = tableRun(existingMeldId, [9]).tiles[0]!
    const tableFragment = { clientMeldId: 'split', sourceMeldId: null, tileIds: [tableTile.tileId] }
    const tableWrapper = mount(DraftMeld, { props: {
      meld: tableFragment, validation: validateDraftMeld(tableFragment, new Map([[tableTile.tileId, tableTile]])),
      tileById: new Map([[tableTile.tileId, tableTile]]), rackById: new Map(), editable: true,
    } })
    expect(tableWrapper.text()).not.toContain('전체 Rack')
  })

  it('FE-P7-UI-010 keeps valid and invalid meld visual states', () => {
    const valid = tableRun(existingMeldId, [1, 2, 3])
    const validMeld = { clientMeldId: valid.meldId, sourceMeldId: valid.meldId, tileIds: valid.tiles.map((tile) => tile.tileId), gridRow: valid.gridRow, gridColumn: valid.gridColumn }
    const validWrapper = board([validMeld], [], [valid])
    expect(validWrapper.find('[data-candidate-kind="RUN"]').exists()).toBe(true)
    expect(validWrapper.find('.working-table-candidate-overlay--invalid').exists()).toBe(false)
    const invalidMeld = { clientMeldId: 'new', sourceMeldId: null, tileIds: [rack12.tileId], gridRow: 0, gridColumn: 0 }
    const invalidWrapper = board([invalidMeld], [rack12], [])
    expect(invalidWrapper.find('[data-candidate-kind="INVALID"]').exists()).toBe(true)
    expect(invalidWrapper.find('.working-table-candidate-overlay--invalid').exists()).toBe(true)
  })

  it('FE-P7-UI-011 keeps narrow meld markup free of button rows and merge controls', () => {
    const rack = Array.from({ length: 13 }, (_, index) => numberTile('RED', index + 1, 'B', index))
    const meld = { clientMeldId: 'long', sourceMeldId: null, tileIds: rack.map((tile) => tile.tileId), gridRow: 0, gridColumn: 0 }
    const wrapper = board([meld], rack, [])
    expect(wrapper.findAll('.game-tile')).toHaveLength(13)
    expect(wrapper.find('.draft-tile-tools').exists()).toBe(false)
    expect(wrapper.find('.working-meld__merge').exists()).toBe(false)
  })
})

describe('Phase 7 third review Joker contribution scoring', () => {
  it('FE-P7-JOKER-001 keeps ordinary RUN contribution scoring unchanged', () => {
    const rack = [numberTile('RED', 7), numberTile('RED', 8), numberTile('RED', 9)]
    const meld = { clientMeldId: 'run', sourceMeldId: null, tileIds: rack.map((tile) => tile.tileId) }
    expect(validateTurnDraft([meld], rack, true).submissionScore).toBe(24)
  })

  it('FE-P7-JOKER-002 resolves middle and end RUN Joker values from the validated positions', () => {
    const middle = [numberTile('RED', 9), jokerTile(1), numberTile('RED', 11)]
    const middleMeld = { clientMeldId: 'middle', sourceMeldId: null, tileIds: middle.map((tile) => tile.tileId) }
    expect(validateTurnDraft([middleMeld], middle, true).submissionScore).toBe(30)
    const end = [numberTile('RED', 11), numberTile('RED', 12), jokerTile(2)]
    const endMeld = { clientMeldId: 'end', sourceMeldId: null, tileIds: end.map((tile) => tile.tileId) }
    expect(validateTurnDraft([endMeld], end, true).submissionScore).toBe(36)
  })

  it('FE-P7-JOKER-003 resolves a GROUP Joker to the group number', () => {
    const rack = [numberTile('RED', 10), numberTile('BLUE', 10), jokerTile(2)]
    const meld = { clientMeldId: 'group', sourceMeldId: null, tileIds: rack.map((tile) => tile.tileId) }
    expect(validateTurnDraft([meld], rack, true).submissionScore).toBe(30)
  })

  it('FE-P7-JOKER-004 uses the same resolved score for the initial 30-point gate and display', () => {
    const rack = [numberTile('RED', 9), jokerTile(1), numberTile('RED', 11)]
    const meld = { clientMeldId: 'initial', sourceMeldId: null, tileIds: rack.map((tile) => tile.tileId) }
    const validation = validateTurnDraft([meld], rack, false)
    expect(validation.totalScore).toBe(30)
    expect(validation.submissionScore).toBe(30)
    expect(validation.canCommit).toBe(true)
  })
})

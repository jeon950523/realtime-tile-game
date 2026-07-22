import { defineStore } from 'pinia'

import { getApiErrorMessage } from '@/api/apiError'
import * as gameApi from '@/api/gameApi'
import { AuthenticatedStompClient } from '@/realtime/authenticatedStompClient'
import { useAuthStore } from '@/stores/auth'
import type {
  ActiveGame,
  CommitTilePlacementCommand,
  GameActionType,
  GameCommandReply,
  GameConnectionState,
  GamePrivateState,
  GamePublicState,
  GameTerminatedPayload,
  MeldsCommittedPayload,
  RealtimeGameEvent,
  TileDrawnPayload,
  TurnPreviewTilePlacement,
  TurnPreviewClearedPayload,
  TurnPreviewSnapshot,
  TurnPassedPayload,
} from '@/types/game'
import type { RackSyncSource } from '@/types/rackPresentation'

interface PendingRackAddition {
  gameVersion: number
  tileIds: string[]
}

interface GameState {
  activeGameId: number | null
  privateState: GamePrivateState | null
  connectionState: GameConnectionState
  gameSubscriptionsReady: boolean
  reconnectRecoveryInProgress: boolean
  reconnectRecoveryRevision: number
  privateStateLoaded: boolean
  privateStateVersion: number
  loading: boolean
  commandInProgress: boolean
  pendingActionId: string | null
  pendingBaseVersion: number | null
  pendingActionType: GameActionType | null
  pendingCommitTilePlacements: CommitTilePlacementCommand[] | null
  pendingRecoveryInProgress: boolean
  pendingRecoveryRevision: number
  pendingRecoveryOutcome: 'NONE' | 'COMMITTED' | 'RETRYABLE' | 'AUTHORITATIVE_RESET'
  lastCommandReply: GameCommandReply | null
  lastCommandError: string | null
  lastMessage: string | null
  lastError: string | null
  rackSyncRevision: number
  rackSyncSource: RackSyncSource
  drawMotionRevision: number
  drawMotionTileIds: string[]
  pendingOwnDrawVersion: number | null
  pendingRackAddition: PendingRackAddition | null
  lastDrawMotionVersion: number
  turnPreview: TurnPreviewSnapshot | null
  publishedPreviewBaseVersion: number
  publishedPreviewRevision: number
  publishedPreviewActive: boolean
  terminalGameId: number | null
  terminalRoomId: number | null
  terminalRevision: number
  terminationNotice: string | null
}

let realtimeClient: AuthenticatedStompClient | null = null
let pendingCommandTimer: ReturnType<typeof setTimeout> | null = null
let pendingRecoveryTask: { actionId: string; promise: Promise<boolean> } | null = null

export const GAME_COMMAND_RESPONSE_TIMEOUT_MS = 9_000
export const GAME_ACCEPTED_STATE_GRACE_MS = 1_500

function clearPendingCommandTimer(): void {
  if (pendingCommandTimer !== null) clearTimeout(pendingCommandTimer)
  pendingCommandTimer = null
}

function candidateMatchesTable(
  candidate: readonly CommitTilePlacementCommand[] | null,
  table: GamePublicState['tableMelds'],
): boolean {
  if (!candidate) return false
  const orderedCandidate = [...candidate].sort((left, right) => left.tileId.localeCompare(right.tileId))
  const authoritative = table.flatMap((meld) => meld.tiles.map((tile) => ({
    tileId: tile.tileId,
    gridRow: meld.gridRow,
    gridColumn: meld.gridColumn + tile.positionOrder,
  }))).sort((left, right) => left.tileId.localeCompare(right.tileId))
  return orderedCandidate.length === authoritative.length && orderedCandidate.every((placement, index) => {
    const actual = authoritative[index]
    return actual?.tileId === placement.tileId
      && actual.gridRow === placement.gridRow
      && actual.gridColumn === placement.gridColumn
  })
}

interface LegacyCommitMeldInput {
  clientMeldId?: string
  tileIds: readonly string[]
  gridRow?: number
  gridColumn?: number
}

type CommitPlacementInput = CommitTilePlacementCommand | LegacyCommitMeldInput

function normalizeCommitPlacements(inputs: readonly CommitPlacementInput[]): CommitTilePlacementCommand[] {
  if (inputs.every((input): input is CommitTilePlacementCommand => 'tileId' in input)) {
    return inputs.map((placement) => ({ ...placement }))
  }
  return inputs.flatMap((input, meldIndex) => {
    if ('tileId' in input) return [{ ...input }]
    const gridRow = input.gridRow ?? 0
    const gridColumn = input.gridColumn ?? (meldIndex * 13)
    return input.tileIds.map((tileId, tileIndex) => ({
      tileId,
      gridRow,
      gridColumn: gridColumn + tileIndex,
    }))
  })
}

function createActionId(): string {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const value = Math.floor(Math.random() * 16)
    const result = character === 'x' ? value : (value & 0x3) | 0x8
    return result.toString(16)
  })
}

function clonePublicState(state: GamePublicState): GamePublicState {
  return {
    ...state,
    players: state.players.map((player) => ({ ...player })),
    tableMelds: state.tableMelds.map((meld) => ({
      ...meld,
      tiles: meld.tiles.map((tile) => ({ ...tile })),
    })),
  }
}

export const useGameStore = defineStore('game', {
  state: (): GameState => ({
    activeGameId: null,
    privateState: null,
    connectionState: 'DISCONNECTED',
    gameSubscriptionsReady: false,
    reconnectRecoveryInProgress: false,
    reconnectRecoveryRevision: 0,
    privateStateLoaded: false,
    privateStateVersion: -1,
    loading: false,
    commandInProgress: false,
    pendingActionId: null,
    pendingBaseVersion: null,
    pendingActionType: null,
    pendingCommitTilePlacements: null,
    pendingRecoveryInProgress: false,
    pendingRecoveryRevision: 0,
    pendingRecoveryOutcome: 'NONE',
    lastCommandReply: null,
    lastCommandError: null,
    lastMessage: null,
    lastError: null,
    rackSyncRevision: 0,
    rackSyncSource: 'NONE',
    drawMotionRevision: 0,
    drawMotionTileIds: [],
    pendingOwnDrawVersion: null,
    pendingRackAddition: null,
    lastDrawMotionVersion: -1,
    turnPreview: null,
    publishedPreviewBaseVersion: -1,
    publishedPreviewRevision: 0,
    publishedPreviewActive: false,
    terminalGameId: null,
    terminalRoomId: null,
    terminalRevision: 0,
    terminationNotice: null,
  }),

  getters: {
    publicState: (state): GamePublicState | null => state.privateState?.publicState ?? null,
    myRack: (state) => state.privateState?.myRack ?? [],
    gameCommandReady: (state): boolean => Boolean(
      state.connectionState === 'CONNECTED'
      && state.gameSubscriptionsReady
      && !state.reconnectRecoveryInProgress
      && state.privateStateLoaded
      && state.activeGameId !== null
      && state.privateState?.publicState.gameId === state.activeGameId,
    ),
  },

  actions: {
    client(): AuthenticatedStompClient {
      if (realtimeClient) return realtimeClient
      const authStore = useAuthStore()
      realtimeClient = new AuthenticatedStompClient({
        getAccessToken: () => authStore.accessToken,
        onStateChange: (state, message) => {
          this.connectionState = state
          if (state !== 'CONNECTED') {
            this.gameSubscriptionsReady = false
            this.turnPreview = null
          }
          if (state === 'CONNECTED') this.lastError = null
          if (message) this.lastError = message
        },
        onGameSubscriptionsReady: (gameId, ready) => {
          if (this.activeGameId === gameId) {
            this.gameSubscriptionsReady = ready && !this.reconnectRecoveryInProgress
          }
        },
        onGameRecoveryStateChange: (gameId, recoveryInProgress) => {
          if (this.activeGameId !== gameId) return
          if (recoveryInProgress) {
            this.reconnectRecoveryRevision += 1
            this.gameSubscriptionsReady = false
          }
          this.reconnectRecoveryInProgress = recoveryInProgress
        },
        onCommandTransportInterrupted: (message) => {
          if (this.commandInProgress) void this.recoverPendingCommand(message)
        },
        onGameReply: (reply) => this.applyGameCommandReply(reply),
        onGameEvent: (event) => this.applyGameEvent(event),
        onGameState: (event) => this.applyPrivateStateEvent(event),
        onGameReconnect: async (gameId) => {
          const recoveryRevision = this.reconnectRecoveryRevision
          if (this.activeGameId !== gameId || !this.reconnectRecoveryInProgress) return false
          if (this.commandInProgress) {
            const recovered = await this.recoverPendingCommand(
              '실시간 연결이 다시 설정되어 최신 게임 상태를 확인했습니다.',
            )
            return recovered && this.isCurrentReconnectRecovery(gameId, recoveryRevision)
          }
          return this.recoverGameAfterReconnect(gameId, recoveryRevision)
        },
      })
      return realtimeClient
    },

    isCurrentReconnectRecovery(gameId: number, recoveryRevision: number): boolean {
      return this.activeGameId === gameId
        && this.reconnectRecoveryInProgress
        && this.reconnectRecoveryRevision === recoveryRevision
    },

    invalidateReconnectRecovery(): void {
      this.reconnectRecoveryRevision += 1
      this.reconnectRecoveryInProgress = false
      this.gameSubscriptionsReady = false
    },

    async loadActiveGame(): Promise<ActiveGame> {
      const active = await gameApi.getActiveGame()
      if (active.active && active.gameId !== this.activeGameId) {
        this.invalidateReconnectRecovery()
        this.clearPendingCommand()
        this.privateStateLoaded = false
        this.privateStateVersion = -1
        if (this.privateState?.publicState.gameId !== active.gameId) this.privateState = null
        this.turnPreview = null
      }
      this.activeGameId = active.active ? active.gameId : null
      if (!active.active) {
        this.invalidateReconnectRecovery()
        this.privateState = null
        this.privateStateLoaded = false
        this.privateStateVersion = -1
        this.gameSubscriptionsReady = false
        this.turnPreview = null
        this.clearPendingCommand()
      }
      return active
    },

    async loadGame(gameId: number): Promise<GamePrivateState> {
      if (this.loading) {
        if (this.privateState?.publicState.gameId === gameId) return this.privateState
        throw new Error('다른 게임 상태를 불러오는 중입니다.')
      }
      if (this.activeGameId !== null && this.activeGameId !== gameId) {
        this.invalidateReconnectRecovery()
        this.clearPendingCommand()
        this.privateStateLoaded = false
        this.privateStateVersion = -1
      }
      this.loading = true
      this.lastError = null
      try {
        const state = await gameApi.getGameState(gameId)
        if (state.publicState.gameId !== gameId) throw new Error('게임 상태 식별자가 일치하지 않습니다.')
        const normalizedState: GamePrivateState = {
          ...state,
          publicState: clonePublicState(state.publicState),
          myRack: state.myRack.map((tile) => ({ ...tile })),
        }
        this.privateState = normalizedState
        this.terminalGameId = null
        this.terminationNotice = null
        this.activeGameId = gameId
        this.privateStateLoaded = true
        this.privateStateVersion = normalizedState.publicState.gameVersion
        this.rackSyncSource = 'SNAPSHOT'
        this.rackSyncRevision += 1
        this.pendingOwnDrawVersion = null
        this.pendingRackAddition = null
        this.lastDrawMotionVersion = normalizedState.publicState.gameVersion
        this.drawMotionTileIds = []
        this.turnPreview = null
        this.clearPendingCommand()
        return normalizedState
      } catch (error: unknown) {
        this.lastError = getApiErrorMessage(error, '게임 상태를 불러오지 못했습니다.')
        throw error
      } finally {
        this.loading = false
      }
    },

    async recoverGameAfterReconnect(gameId: number, recoveryRevision: number): Promise<boolean> {
      if (!this.isCurrentReconnectRecovery(gameId, recoveryRevision)) return false
      try {
        const state = await gameApi.getGameState(gameId)
        if (!this.isCurrentReconnectRecovery(gameId, recoveryRevision)) return false
        if (state.publicState.gameId !== gameId) {
          throw new Error('게임 상태 식별자가 일치하지 않습니다.')
        }
        const currentVersion = this.privateState?.publicState.gameId === gameId
          ? this.privateState.publicState.gameVersion
          : -1
        if (state.publicState.gameVersion < currentVersion) {
          throw new Error('복구한 게임 상태가 현재 상태보다 오래되었습니다.')
        }
        const normalizedState: GamePrivateState = {
          ...state,
          publicState: clonePublicState(state.publicState),
          myRack: state.myRack.map((tile) => ({ ...tile })),
        }
        this.applyRecoveredPrivateState(normalizedState, true)
        await this.loadTurnPreview(gameId)
        if (!this.isCurrentReconnectRecovery(gameId, recoveryRevision)) return false
        return true
      } catch (error: unknown) {
        if (this.isCurrentReconnectRecovery(gameId, recoveryRevision)) {
          this.lastError = getApiErrorMessage(error, '최신 게임 상태를 복구하지 못했습니다.')
        }
        return false
      }
    },

    async connectGame(gameId: number): Promise<void> {
      this.gameSubscriptionsReady = false
      const client = this.client()
      await client.connectGame(gameId)
      this.gameSubscriptionsReady = client.isGameCommandReady(gameId)
      if (!this.gameSubscriptionsReady) throw new Error('게임 명령 구독을 준비하지 못했습니다.')
    },

    async initialize(gameId: number): Promise<GamePrivateState> {
      const state = await this.loadGame(gameId)
      await this.connectGame(gameId)
      await this.loadTurnPreview(gameId)
      return state
    },

    async loadTurnPreview(gameId: number): Promise<TurnPreviewSnapshot | null> {
      const observedPreview = this.turnPreview
      const observedRevision = observedPreview?.previewRevision ?? -1
      const preview = await gameApi.getTurnPreview(gameId)
      if (this.activeGameId !== gameId || this.privateState?.publicState.gameId !== gameId) return null
      if (preview) {
        this.applyTurnPreviewSnapshot(preview)
      } else if (this.turnPreview === observedPreview
        && (this.turnPreview?.previewRevision ?? -1) === observedRevision) {
        this.turnPreview = null
      }
      return preview ?? null
    },

    publishTurnPreview(placements: readonly ({
      tileId: string
      gridRow: number
      gridColumn: number
      source: 'COMMITTED_TABLE' | 'CURRENT_PLAYER_RACK'
    })[]): boolean {
      const state = this.privateState
      if (!state || !this.gameCommandReady
        || state.publicState.currentTurnUserId !== state.myUserId
        || placements.length === 0) return false
      const baseGameVersion = state.publicState.gameVersion
      if (this.publishedPreviewBaseVersion !== baseGameVersion) {
        this.publishedPreviewBaseVersion = baseGameVersion
        this.publishedPreviewRevision = 0
        this.publishedPreviewActive = false
      }
      const previewRevision = this.publishedPreviewRevision + 1
      const normalized: TurnPreviewTilePlacement[] = placements.map((placement) => ({
        tileId: placement.tileId,
        gridRow: placement.gridRow,
        gridColumn: placement.gridColumn,
        source: placement.source,
      }))
      try {
        this.client().publishTurnPreview(state.publicState.gameId, {
          gameId: state.publicState.gameId,
          baseGameVersion,
          previewRevision,
          tilePlacements: normalized,
        })
        this.publishedPreviewRevision = previewRevision
        this.publishedPreviewActive = true
        return true
      } catch {
        return false
      }
    },

    clearTurnPreview(): boolean {
      const state = this.privateState
      if (!state || !this.gameCommandReady
        || state.publicState.currentTurnUserId !== state.myUserId
        || this.publishedPreviewBaseVersion !== state.publicState.gameVersion
        || !this.publishedPreviewActive) return false
      const previewRevision = this.publishedPreviewRevision + 1
      try {
        this.client().publishTurnPreviewCancel(state.publicState.gameId, {
          gameId: state.publicState.gameId,
          baseGameVersion: state.publicState.gameVersion,
          previewRevision,
        })
        this.publishedPreviewRevision = previewRevision
        this.publishedPreviewActive = false
        return true
      } catch {
        return false
      }
    },

    startPendingCommand(
      actionId: string,
      baseVersion: number,
      actionType: GameActionType,
      candidate: readonly CommitTilePlacementCommand[] | null = null,
    ): void {
      clearPendingCommandTimer()
      this.commandInProgress = true
      this.pendingActionId = actionId
      this.pendingBaseVersion = baseVersion
      this.pendingActionType = actionType
      this.pendingCommitTilePlacements = candidate?.map((placement) => ({
        tileId: placement.tileId,
        gridRow: placement.gridRow,
        gridColumn: placement.gridColumn,
      })) ?? null
      this.pendingRecoveryOutcome = 'NONE'
      this.schedulePendingRecovery(actionId, GAME_COMMAND_RESPONSE_TIMEOUT_MS)
    },

    clearPendingCommand(): void {
      clearPendingCommandTimer()
      this.commandInProgress = false
      this.pendingActionId = null
      this.pendingBaseVersion = null
      this.pendingActionType = null
      this.pendingCommitTilePlacements = null
    },

    schedulePendingRecovery(actionId: string, delayMs: number): void {
      clearPendingCommandTimer()
      pendingCommandTimer = setTimeout(() => {
        pendingCommandTimer = null
        void this.recoverPendingCommand(
          '서버 응답을 확인하지 못했습니다. 최신 게임 상태를 다시 불러왔습니다.',
          actionId,
        )
      }, delayMs)
    },

    drawTile(): void {
      const state = this.privateState
      if (!state || this.commandInProgress || !this.gameCommandReady) return
      const actionId = createActionId()
      const baseVersion = state.publicState.gameVersion
      this.startPendingCommand(actionId, baseVersion, 'DRAW')
      this.lastCommandError = null
      this.lastMessage = null
      try {
        this.client().publishDraw(state.publicState.gameId, {
          actionId,
          gameVersion: baseVersion,
        })
      } catch (error: unknown) {
        this.clearPendingCommand()
        this.lastCommandError = getApiErrorMessage(error, '타일을 뽑지 못했습니다.')
      }
    },

    passTurn(): void {
      const state = this.privateState
      if (!state || this.commandInProgress || !this.gameCommandReady) return
      const actionId = createActionId()
      const baseVersion = state.publicState.gameVersion
      this.startPendingCommand(actionId, baseVersion, 'PASS')
      this.lastCommandError = null
      this.lastMessage = null
      try {
        this.client().publishPass(state.publicState.gameId, {
          actionId,
          gameVersion: baseVersion,
        })
      } catch (error: unknown) {
        this.clearPendingCommand()
        this.lastCommandError = getApiErrorMessage(error, 'PASS를 처리하지 못했습니다.')
      }
    },

    commitTurn(placements: readonly CommitPlacementInput[]): string | null {
      const state = this.privateState
      if (!state || this.commandInProgress || !this.gameCommandReady || placements.length === 0) return null
      const actionId = createActionId()
      const baseVersion = state.publicState.gameVersion
      const tilePlacements = normalizeCommitPlacements(placements)
      this.startPendingCommand(actionId, baseVersion, 'COMMIT', tilePlacements)
      this.lastCommandReply = null
      this.lastCommandError = null
      this.lastMessage = null
      try {
        this.client().publishCommit(state.publicState.gameId, {
          actionId,
          gameVersion: baseVersion,
          tilePlacements,
        })
        return actionId
      } catch (error: unknown) {
        this.clearPendingCommand()
        this.lastCommandError = getApiErrorMessage(error, 'TurnDraft를 확정하지 못했습니다.')
        return null
      }
    },

    exitActiveGame(): string | null {
      const state = this.privateState
      if (!state || this.commandInProgress || !this.gameCommandReady
        || state.publicState.status !== 'IN_PROGRESS') return null
      const actionId = createActionId()
      const baseVersion = state.publicState.gameVersion
      this.startPendingCommand(actionId, baseVersion, 'EXIT_ACTIVE_GAME')
      this.lastCommandReply = null
      this.lastCommandError = null
      this.lastMessage = null
      try {
        this.client().publishExitActiveGame(state.publicState.gameId, {
          actionId,
          gameVersion: baseVersion,
          roomId: state.publicState.roomId,
        })
        return actionId
      } catch (error: unknown) {
        this.clearPendingCommand()
        this.lastCommandError = getApiErrorMessage(error, '게임을 포기하고 나가지 못했습니다.')
        return null
      }
    },

    applyGameCommandReply(reply: GameCommandReply): void {
      if (this.activeGameId !== null && reply.gameId !== this.activeGameId) return
      if (this.pendingActionId === null || reply.actionId !== this.pendingActionId) return
      this.lastCommandReply = { ...reply }
      if (!reply.accepted || reply.code) {
        this.clearPendingCommand()
        this.lastCommandError = reply.message
        if ((reply.code === 'STALE_GAME_VERSION' || reply.code === 'NOT_CURRENT_TURN') && reply.gameId > 0) {
          void this.loadGame(reply.gameId).catch(() => undefined)
        }
        return
      }
      this.lastCommandError = null
      this.lastMessage = reply.duplicate ? '중복 요청의 기존 결과를 복구했습니다.' : reply.message
      if (reply.actionType === 'EXIT_ACTIVE_GAME') {
        this.completeGameTermination({
          gameId: reply.gameId,
          roomId: this.privateState?.publicState.roomId ?? null,
          terminationReason: null,
        })
        return
      }
      if (this.privateStateVersion >= reply.gameVersion) this.clearPendingCommand()
      else this.schedulePendingRecovery(reply.actionId, GAME_ACCEPTED_STATE_GRACE_MS)
    },

    async recoverPendingCommand(message: string, expectedActionId?: string): Promise<boolean> {
      const actionId = expectedActionId ?? this.pendingActionId
      if (!actionId || actionId !== this.pendingActionId) return false
      if (pendingRecoveryTask?.actionId === actionId) return pendingRecoveryTask.promise
      const gameId = this.activeGameId ?? this.privateState?.publicState.gameId ?? null
      const baseVersion = this.pendingBaseVersion
      const actionType = this.pendingActionType
      const candidate = this.pendingCommitTilePlacements?.map((placement) => ({
        tileId: placement.tileId,
        gridRow: placement.gridRow,
        gridColumn: placement.gridColumn,
      })) ?? null
      if (gameId === null || baseVersion === null || actionType === null) {
        this.clearPendingCommand()
        return false
      }
      clearPendingCommandTimer()
      this.pendingRecoveryInProgress = true
      const task = (async (): Promise<boolean> => {
        try {
          const state = await gameApi.getGameState(gameId)
          if (this.activeGameId !== gameId || this.pendingActionId !== actionId) return false
          const normalizedState: GamePrivateState = {
            ...state,
            publicState: clonePublicState(state.publicState),
            myRack: state.myRack.map((tile) => ({ ...tile })),
          }
          if (actionType === 'EXIT_ACTIVE_GAME' && normalizedState.publicState.status !== 'IN_PROGRESS') {
            this.completeGameTermination({
              gameId,
              roomId: normalizedState.publicState.roomId,
              terminationReason: null,
            })
            return true
          }
          const versionAdvanced = normalizedState.publicState.gameVersion > baseVersion
          const commitReflected = actionType === 'COMMIT'
            && versionAdvanced
            && candidateMatchesTable(candidate, normalizedState.publicState.tableMelds)
          const commandReflected = actionType === 'COMMIT' ? commitReflected : versionAdvanced
          const authoritativeReset = versionAdvanced && !commandReflected
          this.applyRecoveredPrivateState(normalizedState, commandReflected || authoritativeReset)
          this.clearPendingCommand()
          this.pendingRecoveryOutcome = commandReflected
            ? 'COMMITTED'
            : authoritativeReset ? 'AUTHORITATIVE_RESET' : 'RETRYABLE'
          this.pendingRecoveryRevision += 1
          if (commandReflected) {
            this.lastCommandError = null
            this.lastMessage = '최신 게임 상태에서 명령 반영을 확인했습니다.'
          } else if (authoritativeReset) {
            this.lastCommandError = '게임 상태가 변경되어 최신 상태로 복구했습니다.'
          } else {
            this.lastCommandError = message
          }
          return true
        } catch (error: unknown) {
          if (this.activeGameId !== gameId || this.pendingActionId !== actionId) return false
          this.clearPendingCommand()
          this.pendingRecoveryOutcome = 'RETRYABLE'
          this.pendingRecoveryRevision += 1
          this.lastCommandError = getApiErrorMessage(
            error,
            '서버 상태를 다시 확인하지 못했습니다. 연결을 확인한 뒤 다시 시도해주세요.',
          )
          return false
        }
      })()
      const taskEntry = { actionId, promise: task }
      pendingRecoveryTask = taskEntry
      try {
        return await task
      } finally {
        if (pendingRecoveryTask === taskEntry) {
          pendingRecoveryTask = null
          this.pendingRecoveryInProgress = false
        }
      }
    },

    applyRecoveredPrivateState(state: GamePrivateState, notifyAuthoritativeSync: boolean): void {
      const previousVersion = this.privateState?.publicState.gameVersion ?? -1
      const previousTurnUserId = this.privateState?.publicState.currentTurnUserId ?? null
      this.privateState = state
      this.activeGameId = state.publicState.gameId
      this.privateStateLoaded = true
      this.privateStateVersion = state.publicState.gameVersion
      this.rackSyncSource = 'SNAPSHOT'
      if (notifyAuthoritativeSync) this.rackSyncRevision += 1
      this.pendingOwnDrawVersion = null
      this.pendingRackAddition = null
      this.lastDrawMotionVersion = state.publicState.gameVersion
      this.drawMotionTileIds = []
      if (previousVersion !== state.publicState.gameVersion
        || previousTurnUserId !== state.publicState.currentTurnUserId) this.turnPreview = null
    },

    applyTurnPreviewSnapshot(preview: TurnPreviewSnapshot): void {
      const state = this.privateState
      if (!state || preview.gameId !== state.publicState.gameId
        || preview.baseGameVersion !== state.publicState.gameVersion
        || preview.turnPlayerId !== state.publicState.currentTurnUserId
        || preview.turnPlayerId === state.myUserId
        || (this.turnPreview?.previewRevision ?? -1) >= preview.previewRevision) return
      this.turnPreview = {
        ...preview,
        tilePlacements: preview.tilePlacements.map((placement) => ({
          ...placement,
        })),
      }
    },

    applyTurnPreviewCleared(payload: TurnPreviewClearedPayload): void {
      const current = this.turnPreview
      if (!current || payload.gameId !== current.gameId
        || payload.baseGameVersion !== current.baseGameVersion
        || payload.previewRevision < current.previewRevision) return
      this.turnPreview = null
    },

    applyGameEvent(event: RealtimeGameEvent): void {
      if (event.eventType === 'GAME_TERMINATED') {
        const payload = event.payload as GameTerminatedPayload
        if (payload.gameId !== this.activeGameId && payload.gameId !== this.privateState?.publicState.gameId) return
        this.completeGameTermination(payload)
        return
      }
      if (!this.privateState) return
      if (event.eventType === 'TURN_PREVIEW_UPDATED') {
        this.applyTurnPreviewSnapshot(event.payload as TurnPreviewSnapshot)
        return
      }
      if (event.eventType === 'TURN_PREVIEW_CLEARED') {
        this.applyTurnPreviewCleared(event.payload as TurnPreviewClearedPayload)
        return
      }
      if (event.eventType === 'GAME_STATE_UPDATED') {
        const publicState = event.payload as GamePublicState
        if (publicState.gameId !== this.privateState.publicState.gameId) return
        if (publicState.gameVersion < this.privateState.publicState.gameVersion) return
        if (this.turnPreview && (this.turnPreview.baseGameVersion !== publicState.gameVersion
          || this.turnPreview.turnPlayerId !== publicState.currentTurnUserId)) this.turnPreview = null
        this.privateState = {
          ...this.privateState,
          publicState: clonePublicState(publicState),
        }
        return
      }
      if (event.eventType === 'TILE_DRAWN') {
        this.turnPreview = null
        this.applyTileDrawn(event.payload as TileDrawnPayload)
        return
      }
      if (event.eventType === 'TURN_PASSED') {
        this.turnPreview = null
        this.applyTurnPassed(event.payload as TurnPassedPayload)
        return
      }
      if (event.eventType === 'MELDS_COMMITTED') {
        this.turnPreview = null
        this.applyMeldsCommitted(event.payload as MeldsCommittedPayload)
      }
    },

    applyTileDrawn(payload: TileDrawnPayload): void {
      const state = this.privateState
      if (!state || payload.gameId !== state.publicState.gameId) return
      const currentVersion = state.publicState.gameVersion
      if (payload.gameVersion < currentVersion) return
      if (payload.gameVersion > currentVersion + 1) {
        void this.loadGame(payload.gameId).catch(() => undefined)
        return
      }
      if (payload.drawnByUserId === state.myUserId && payload.gameVersion > this.lastDrawMotionVersion) {
        if (this.pendingRackAddition?.gameVersion === payload.gameVersion) {
          this.confirmDrawMotion(payload.gameVersion, this.pendingRackAddition.tileIds)
        } else {
          this.pendingOwnDrawVersion = payload.gameVersion
        }
      }
      const publicState = state.publicState
      this.privateState = {
        ...state,
        publicState: {
          ...publicState,
          gameVersion: payload.gameVersion,
          currentTurnUserId: payload.nextTurnUserId,
          currentTurnSeatOrder: payload.nextTurnSeatOrder,
          turnNumber: payload.turnNumber,
          currentTurnId: payload.currentTurnId,
          currentTurnStartedAt: payload.currentTurnStartedAt,
          turnDeadlineAt: payload.turnDeadlineAt,
          consecutivePassCount: payload.consecutivePassCount,
          tilePoolCount: payload.tilePoolCount,
          players: publicState.players.map((player) => ({
            ...player,
            rackTileCount: player.userId === payload.drawnByUserId
              ? payload.drawnByRackCount
              : player.rackTileCount,
            currentTurn: player.userId === payload.nextTurnUserId,
          })),
          tableMelds: [...publicState.tableMelds],
        },
      }
    },

    applyTurnPassed(payload: TurnPassedPayload): void {
      const state = this.privateState
      if (!state || payload.gameId !== state.publicState.gameId) return
      const currentVersion = state.publicState.gameVersion
      if (payload.gameVersion < currentVersion) return
      if (payload.gameVersion > currentVersion + 1) {
        void this.loadGame(payload.gameId).catch(() => undefined)
        return
      }
      const publicState = state.publicState
      this.privateState = {
        ...state,
        publicState: {
          ...publicState,
          gameVersion: payload.gameVersion,
          currentTurnUserId: payload.nextTurnUserId,
          currentTurnSeatOrder: payload.nextTurnSeatOrder,
          turnNumber: payload.turnNumber,
          currentTurnId: payload.currentTurnId,
          currentTurnStartedAt: payload.currentTurnStartedAt,
          turnDeadlineAt: payload.turnDeadlineAt,
          consecutivePassCount: payload.consecutivePassCount,
          tilePoolCount: payload.tilePoolCount,
          players: publicState.players.map((player) => ({
            ...player,
            currentTurn: player.userId === payload.nextTurnUserId,
          })),
          tableMelds: [...publicState.tableMelds],
        },
      }
    },

    applyMeldsCommitted(payload: MeldsCommittedPayload): void {
      const state = this.privateState
      if (!state || payload.gameId !== state.publicState.gameId) return
      const currentVersion = state.publicState.gameVersion
      if (payload.gameVersion < currentVersion) return
      if (payload.gameVersion > currentVersion + 1) {
        void this.loadGame(payload.gameId).catch(() => undefined)
        return
      }
      const publicState = state.publicState
      this.privateState = {
        ...state,
        publicState: {
          ...publicState,
          gameVersion: payload.gameVersion,
          currentTurnUserId: payload.nextTurnUserId,
          currentTurnSeatOrder: payload.nextTurnSeatOrder,
          turnNumber: payload.turnNumber,
          currentTurnId: payload.currentTurnId,
          currentTurnStartedAt: payload.currentTurnStartedAt,
          turnDeadlineAt: payload.turnDeadlineAt,
          consecutivePassCount: payload.consecutivePassCount,
          players: publicState.players.map((player) => ({
            ...player,
            rackTileCount: player.userId === payload.committedByUserId
              ? payload.committedByRackCount
              : player.rackTileCount,
            initialMeldCompleted: player.userId === payload.committedByUserId
              ? payload.initialMeldCompleted
              : player.initialMeldCompleted,
            currentTurn: player.userId === payload.nextTurnUserId,
          })),
          tableMelds: publicState.tableMelds.map((meld) => ({
            ...meld,
            tiles: meld.tiles.map((tile) => ({ ...tile })),
          })),
        },
      }
    },

    applyPrivateStateEvent(event: RealtimeGameEvent): void {
      if (event.eventType !== 'GAME_STATE_UPDATED') return
      const state = event.payload as GamePrivateState
      if (this.activeGameId !== null && state.publicState.gameId !== this.activeGameId) return
      const currentVersion = this.privateState?.publicState.gameVersion ?? -1
      if (state.publicState.gameVersion < currentVersion) return
      const shouldClearPending = this.pendingBaseVersion !== null
        && state.publicState.gameVersion > this.pendingBaseVersion
      const shouldClearPreview = this.turnPreview !== null
        && (state.publicState.gameVersion !== this.turnPreview.baseGameVersion
          || state.publicState.currentTurnUserId !== this.turnPreview.turnPlayerId)
      const previousRackIds = new Set(this.privateState?.myRack.map((tile) => tile.tileId) ?? [])
      const addedTileIds = state.myRack
        .map((tile) => tile.tileId)
        .filter((tileId) => !previousRackIds.has(tileId))
      this.privateState = {
        ...state,
        publicState: clonePublicState(state.publicState),
        myRack: state.myRack.map((tile) => ({ ...tile })),
      }
      this.activeGameId = state.publicState.gameId
      this.privateStateLoaded = true
      this.privateStateVersion = state.publicState.gameVersion
      this.rackSyncSource = 'PRIVATE_EVENT'
      this.rackSyncRevision += 1
      if (shouldClearPreview) this.turnPreview = null
      if (addedTileIds.length > 0 && state.publicState.gameVersion > this.lastDrawMotionVersion) {
        if (this.pendingOwnDrawVersion === state.publicState.gameVersion) {
          this.confirmDrawMotion(state.publicState.gameVersion, addedTileIds)
        } else {
          this.pendingRackAddition = {
            gameVersion: state.publicState.gameVersion,
            tileIds: addedTileIds,
          }
        }
      }
      if (this.pendingOwnDrawVersion !== null && state.publicState.gameVersion > this.pendingOwnDrawVersion) {
        this.pendingOwnDrawVersion = null
      }
      if (this.pendingRackAddition && state.publicState.gameVersion > this.pendingRackAddition.gameVersion) {
        this.pendingRackAddition = null
      }
      if (shouldClearPending) this.clearPendingCommand()
    },

    confirmDrawMotion(gameVersion: number, tileIds: readonly string[]): void {
      if (gameVersion <= this.lastDrawMotionVersion || tileIds.length === 0) return
      this.lastDrawMotionVersion = gameVersion
      this.drawMotionTileIds = [...new Set(tileIds)]
      this.drawMotionRevision += 1
      if (this.pendingOwnDrawVersion === gameVersion) this.pendingOwnDrawVersion = null
      if (this.pendingRackAddition?.gameVersion === gameVersion) this.pendingRackAddition = null
    },

    async disconnectRealtime(): Promise<void> {
      this.clearTurnPreview()
      this.clearPendingCommand()
      this.invalidateReconnectRecovery()
      if (realtimeClient) await realtimeClient.disconnect()
    },

    completeGameTermination(payload: {
      gameId: number
      roomId: number | null
      terminationReason: GameTerminatedPayload['terminationReason'] | null
    }): void {
      const notice = payload.terminationReason === 'PLAYER_LEFT'
        ? '플레이어 이탈로 게임이 중단되었습니다.'
        : payload.terminationReason === 'PLAYER_FORFEIT'
          ? '플레이어가 게임을 포기하여 게임이 종료되었습니다.'
          : '게임 포기 및 나가기가 처리되었습니다.'
      if (this.terminalGameId === payload.gameId) {
        if (payload.roomId !== null) this.terminalRoomId = payload.roomId
        this.terminationNotice = notice
        this.lastMessage = notice
        return
      }
      this.terminalGameId = payload.gameId
      this.terminalRoomId = payload.roomId
      this.terminationNotice = notice
      this.clearActiveGameContext()
      this.lastMessage = notice
      this.terminalRevision += 1
    },

    clearActiveGameContext(): void {
      const nextReconnectRecoveryRevision = this.reconnectRecoveryRevision + 1
      this.activeGameId = null
      this.privateState = null
      this.gameSubscriptionsReady = false
      this.reconnectRecoveryInProgress = false
      this.reconnectRecoveryRevision = nextReconnectRecoveryRevision
      this.privateStateLoaded = false
      this.privateStateVersion = -1
      this.loading = false
      this.clearPendingCommand()
      this.pendingRecoveryInProgress = false
      this.pendingRecoveryOutcome = 'NONE'
      this.lastCommandReply = null
      this.lastCommandError = null
      this.lastError = null
      this.rackSyncSource = 'NONE'
      this.drawMotionTileIds = []
      this.pendingOwnDrawVersion = null
      this.pendingRackAddition = null
      this.turnPreview = null
      this.publishedPreviewBaseVersion = -1
      this.publishedPreviewRevision = 0
      this.publishedPreviewActive = false
      if (realtimeClient) void realtimeClient.disconnect()
    },

    cancelPendingForRouteLeave(): void {
      this.clearTurnPreview()
      this.clearPendingCommand()
      this.invalidateReconnectRecovery()
      this.pendingRecoveryInProgress = false
      this.pendingRecoveryOutcome = 'NONE'
    },

    clearGameState(): void {
      const nextReconnectRecoveryRevision = this.reconnectRecoveryRevision + 1
      this.activeGameId = null
      this.privateState = null
      this.connectionState = 'DISCONNECTED'
      this.gameSubscriptionsReady = false
      this.reconnectRecoveryInProgress = false
      this.reconnectRecoveryRevision = nextReconnectRecoveryRevision
      this.privateStateLoaded = false
      this.privateStateVersion = -1
      this.loading = false
      this.clearPendingCommand()
      this.pendingRecoveryInProgress = false
      this.pendingRecoveryRevision = 0
      this.pendingRecoveryOutcome = 'NONE'
      this.lastCommandReply = null
      this.lastCommandError = null
      this.lastMessage = null
      this.lastError = null
      this.rackSyncSource = 'NONE'
      this.rackSyncRevision = 0
      this.drawMotionRevision = 0
      this.drawMotionTileIds = []
      this.pendingOwnDrawVersion = null
      this.pendingRackAddition = null
      this.lastDrawMotionVersion = -1
      this.turnPreview = null
      this.publishedPreviewBaseVersion = -1
      this.publishedPreviewRevision = 0
      this.publishedPreviewActive = false
      this.terminalGameId = null
      this.terminalRoomId = null
      this.terminalRevision = 0
      this.terminationNotice = null
    },
  },
})

export function resetGameStoreClientForTests(): void {
  clearPendingCommandTimer()
  pendingRecoveryTask = null
  realtimeClient = null
}

import type { RoomConnectionState } from '@/types/room'

export type GameMode = 'CLASSIC'
export type GameStatus = 'IN_PROGRESS' | 'FINISHED' | 'ABORTED'
export type GameActionType = 'DRAW' | 'PASS' | 'COMMIT' | 'EXIT_ACTIVE_GAME'

export type GameTileColor = 'RED' | 'BLUE' | 'YELLOW' | 'BLACK'

export interface GameTableTile {
  tileId: string
  tileType: 'NUMBER' | 'JOKER'
  color: GameTileColor | null
  number: number | null
  joker: boolean
  positionOrder: number
}

export interface GameTableMeld {
  meldId: string
  meldType: 'RUN' | 'GROUP'
  score: number
  positionOrder: number
  gridRow: number
  gridColumn: number
  tiles: GameTableTile[]
}

export interface GamePlayerPublicState {
  userId: number
  nickname: string
  avatarType: string
  seatOrder: number
  rackTileCount: number
  initialMeldCompleted: boolean
  currentTurn: boolean
}

export interface GamePublicState {
  gameId: number
  roomId: number
  gameMode: GameMode
  status: GameStatus
  gameVersion: number
  currentTurnUserId: number
  currentTurnSeatOrder: number
  turnNumber: number
  currentTurnId: string
  currentTurnStartedAt: string
  turnDeadlineAt: string
  consecutivePassCount: number
  startedAt: string
  tilePoolCount: number
  tableMelds: GameTableMeld[]
  players: GamePlayerPublicState[]
}

export interface GameRackTile {
  tileId: string
  tileType: 'NUMBER' | 'JOKER'
  color: GameTileColor | null
  number: number | null
  joker: boolean
  positionOrder: number
}

export interface GamePrivateState {
  publicState: GamePublicState
  myPlayerId: number
  myUserId: number
  mySeatOrder: number
  myRack: GameRackTile[]
}

export interface ActiveGame {
  active: boolean
  gameId: number | null
  roomId: number | null
  status: GameStatus | null
}

export interface GameStartedPayload {
  gameId: number
  roomId: number
  route: string
  publicState: GamePublicState
}

export interface GameTurnCommand {
  actionId: string
  gameVersion: number
}

export interface ExitActiveGameCommand extends GameTurnCommand {
  roomId: number
}

export interface GameTerminatedPayload {
  roomId: number
  gameId: number
  gameVersion: number
  roomStatus: 'CLOSED'
  gameStatus: 'FINISHED' | 'ABORTED'
  terminationReason: 'PLAYER_FORFEIT' | 'PLAYER_LEFT'
  exitedParticipantId: number
  exitedUserId: number
  winnerParticipantId: number | null
  winnerUserId: number | null
  serverTime: string
}

export interface CommitTableMeldCommand {
  meldId: string
  tileIds: string[]
  gridRow: number
  gridColumn: number
}

export interface CommitTilePlacementCommand {
  tileId: string
  gridRow: number
  gridColumn: number
}

export interface CommitTurnCommand extends GameTurnCommand {
  tilePlacements: CommitTilePlacementCommand[]
}

export interface TurnPreviewTilePlacement {
  tileId: string
  gridRow: number
  gridColumn: number
  source: 'COMMITTED_TABLE' | 'CURRENT_PLAYER_RACK'
}

export interface TurnPreviewCommand {
  gameId: number
  baseGameVersion: number
  previewRevision: number
  tilePlacements: TurnPreviewTilePlacement[]
}

export interface TurnPreviewCancelCommand {
  gameId: number
  baseGameVersion: number
  previewRevision: number
}

export interface TurnPreviewSnapshot extends TurnPreviewCommand {
  turnPlayerId: number
  updatedAt: string
}

export interface TurnPreviewClearedPayload {
  gameId: number
  turnPlayerId: number
  baseGameVersion: number
  previewRevision: number
  reason: string
}

export interface GameCommandReply {
  eventType: 'GAME_COMMAND_ACCEPTED' | 'GAME_COMMAND_REJECTED' | 'DUPLICATE_GAME_ACTION_REPLAYED'
  actionId: string
  accepted: boolean
  duplicate: boolean
  code: string | null
  message: string
  gameId: number
  actionType: GameActionType
  gameVersion: number
}

export interface TileDrawnPayload {
  gameId: number
  gameVersion: number
  drawnByUserId: number
  drawnByRackCount: number
  tilePoolCount: number
  nextTurnUserId: number
  nextTurnSeatOrder: number
  turnNumber: number
  currentTurnId: string
  currentTurnStartedAt: string
  turnDeadlineAt: string
  consecutivePassCount: number
}

export interface TurnPassedPayload {
  gameId: number
  gameVersion: number
  passedByUserId: number
  tilePoolCount: number
  nextTurnUserId: number
  nextTurnSeatOrder: number
  turnNumber: number
  currentTurnId: string
  currentTurnStartedAt: string
  turnDeadlineAt: string
  consecutivePassCount: number
}

export interface MeldsCommittedPayload {
  gameId: number
  gameVersion: number
  committedByUserId: number
  committedByRackCount: number
  initialMeldCompleted: boolean
  initialMeldScore: number
  changedMeldIds: string[]
  rackContributionCount: number
  tableRecomposed: boolean
  nextTurnUserId: number
  nextTurnSeatOrder: number
  turnNumber: number
  currentTurnId: string
  currentTurnStartedAt: string
  turnDeadlineAt: string
  consecutivePassCount: number
}

export interface RealtimeGameEvent<T = unknown> {
  eventType: string
  occurredAt: string
  payload: T
}

export type GameConnectionState = RoomConnectionState

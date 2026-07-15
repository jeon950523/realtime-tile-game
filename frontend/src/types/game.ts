import type { RoomConnectionState } from '@/types/room'

export type GameMode = 'CLASSIC'
export type GameStatus = 'IN_PROGRESS' | 'FINISHED'

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
  currentTurnUserId: number
  currentTurnSeatOrder: number
  turnNumber: number
  startedAt: string
  tilePoolCount: number
  tableMelds: unknown[]
  players: GamePlayerPublicState[]
}

export interface GameRackTile {
  tileId: string
  tileType: 'NUMBER' | 'JOKER'
  color: 'RED' | 'BLUE' | 'YELLOW' | 'BLACK' | null
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

export interface RealtimeGameEvent<T = unknown> {
  eventType: string
  occurredAt: string
  payload: T
}

export type GameConnectionState = RoomConnectionState

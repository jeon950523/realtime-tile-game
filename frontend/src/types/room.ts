export type RoomConnectionState = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'FAILED'

export interface RoomSummary {
  roomId: number
  roomName: string
  ownerNickname: string
  currentPlayers: number
  maxPlayers: 2 | 3 | 4
  gameMode: 'CLASSIC'
  turnTimeLimitSeconds: number
  status: 'WAITING' | 'PLAYING' | 'FINISHED' | 'CLOSED'
  joinable: boolean
}

export interface RoomPage {
  content: RoomSummary[]
  page: number
  size: number
  totalElements: number
}

export interface RoomParticipant {
  userId: number
  nickname: string
  avatarType: string
  seatOrder: number
  readyStatus: 'NOT_READY' | 'READY'
  owner: boolean
}

export interface RoomDetail {
  roomId: number
  roomName: string
  ownerUserId: number
  ownerNickname: string
  currentPlayers: number
  maxPlayers: 2 | 3 | 4
  gameMode: 'CLASSIC'
  turnTimeLimitSeconds: number
  status: 'WAITING' | 'PLAYING' | 'FINISHED' | 'CLOSED'
  startable: boolean
  startBlockReason: string | null
  participants: RoomParticipant[]
}

export interface ActiveRoom {
  active: boolean
  roomId: number | null
  status: string | null
}

export interface CreateRoomRequest {
  roomName: string
  maxPlayers: 2 | 3 | 4
  gameMode: 'CLASSIC'
  turnTimeLimitSeconds: number
  isPublic: true
}

export interface RealtimeRoomEvent<T = unknown> {
  eventType: string
  occurredAt: string
  payload: T
}

export interface RoomCommandReply {
  eventType: string
  actionId: string
  code: string | null
  message: string
  recoverable: boolean
  payload: unknown
}

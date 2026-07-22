import { defineStore } from 'pinia'

import { getApiErrorMessage } from '@/api/apiError'
import * as roomApi from '@/api/roomApi'
import { AuthenticatedStompClient } from '@/realtime/authenticatedStompClient'
import { useAuthStore } from '@/stores/auth'
import type {
  ActiveRoom,
  CreateRoomRequest,
  RealtimeRoomEvent,
  RoomCommandReply,
  RoomConnectionState,
  RoomDetail,
  RoomPage,
  RoomParticipant,
  RoomSummary,
} from '@/types/room'

interface RoomState {
  rooms: RoomSummary[]
  roomsPage: RoomPage | null
  activeRoom: RoomDetail | null
  activeRoomId: number | null
  lobbyConnectionState: RoomConnectionState
  roomConnectionState: RoomConnectionState
  loadingRooms: boolean
  creating: boolean
  joining: boolean
  quickMatching: boolean
  leaving: boolean
  commandInProgress: boolean
  launchedGameId: number | null
  lastError: string | null
  lastMessage: string | null
}

let realtimeClient: AuthenticatedStompClient | null = null
let roomContextRevision = 0

function createActionId(): string {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID()
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (character) => {
    const value = Math.floor(Math.random() * 16)
    const result = character === 'x' ? value : (value & 0x3) | 0x8
    return result.toString(16)
  })
}

export const useRoomStore = defineStore('room', {
  state: (): RoomState => ({
    rooms: [],
    roomsPage: null,
    activeRoom: null,
    activeRoomId: null,
    lobbyConnectionState: 'DISCONNECTED',
    roomConnectionState: 'DISCONNECTED',
    loadingRooms: false,
    creating: false,
    joining: false,
    quickMatching: false,
    leaving: false,
    commandInProgress: false,
    launchedGameId: null,
    lastError: null,
    lastMessage: null,
  }),

  actions: {
    beginRoomContext(roomId: number): number {
      roomContextRevision += 1
      if (this.activeRoom?.roomId !== roomId) this.activeRoom = null
      this.activeRoomId = roomId
      this.commandInProgress = false
      this.launchedGameId = null
      this.lastError = null
      this.lastMessage = null
      return roomContextRevision
    },

    clearCurrentRoomContext(roomId: number | null = null): void {
      if (roomId !== null) this.rooms = this.rooms.filter((room) => room.roomId !== roomId)
      roomContextRevision += 1
      this.activeRoom = null
      this.activeRoomId = null
      this.commandInProgress = false
      this.launchedGameId = null
    },

    client(): AuthenticatedStompClient {
      if (realtimeClient) return realtimeClient
      const authStore = useAuthStore()
      realtimeClient = new AuthenticatedStompClient({
        getAccessToken: () => authStore.accessToken,
        onStateChange: (state, message) => {
          this.lobbyConnectionState = state
          this.roomConnectionState = state
          if (message) this.lastError = message
        },
        onLobbyEvent: (event) => this.applyLobbyEvent(event),
        onRoomEvent: (event) => this.applyRoomEvent(event),
        onReply: (reply) => this.applyCommandReply(reply),
        onLobbyReconnect: () => this.loadRooms(),
        onRoomReconnect: async (roomId) => { await this.loadRoomDetail(roomId) },
      })
      return realtimeClient
    },

    async loadRooms(): Promise<void> {
      if (this.loadingRooms) return
      this.loadingRooms = true
      this.lastError = null
      try {
        const page = await roomApi.getRooms()
        this.roomsPage = page
        this.rooms = page.content
      } catch (error: unknown) {
        this.lastError = getApiErrorMessage(error, '방 목록을 불러오지 못했습니다.')
        throw error
      } finally {
        this.loadingRooms = false
      }
    },

    async loadActiveRoom(): Promise<ActiveRoom> {
      const active = await roomApi.getActiveRoom()
      this.activeRoomId = active.active ? active.roomId : null
      if (!active.active) this.activeRoom = null
      return active
    },

    async createRoom(request: CreateRoomRequest): Promise<RoomDetail> {
      if (this.creating) throw new Error('방 생성 요청이 이미 진행 중입니다.')
      this.creating = true
      roomContextRevision += 1
      this.lastError = null
      this.lastMessage = null
      try {
        const room = await roomApi.createRoom(request)
        this.activeRoom = room
        this.activeRoomId = room.roomId
        this.commandInProgress = false
        this.launchedGameId = null
        return room
      } catch (error: unknown) {
        this.lastError = getApiErrorMessage(error, '방을 만들지 못했습니다.')
        throw error
      } finally {
        this.creating = false
      }
    },

    async quickMatch(): Promise<RoomDetail | null> {
      if (this.quickMatching || this.joining) return null
      this.quickMatching = true
      this.lastError = null
      try {
        const candidate = await roomApi.getQuickMatch()
        if (!candidate) {
          this.lastMessage = '입장 가능한 방이 없습니다.'
          return null
        }
        return await this.joinRoom(candidate.roomId)
      } catch (error: unknown) {
        this.lastError = getApiErrorMessage(error, '빠른 입장을 처리하지 못했습니다.')
        throw error
      } finally {
        this.quickMatching = false
      }
    },

    async joinRoom(roomId: number): Promise<RoomDetail> {
      if (this.joining) throw new Error('방 입장 요청이 이미 진행 중입니다.')
      this.joining = true
      roomContextRevision += 1
      this.lastError = null
      this.lastMessage = null
      try {
        const room = await roomApi.joinRoom(roomId)
        this.activeRoom = room
        this.activeRoomId = room.roomId
        this.commandInProgress = false
        this.launchedGameId = null
        return room
      } catch (error: unknown) {
        this.lastError = getApiErrorMessage(error, '방에 입장하지 못했습니다.')
        throw error
      } finally {
        this.joining = false
      }
    },

    async loadRoomDetail(roomId: number): Promise<RoomDetail> {
      const revision = this.beginRoomContext(roomId)
      const room = await roomApi.getRoomDetail(roomId)
      if (revision !== roomContextRevision) return room
      this.activeRoom = room
      this.activeRoomId = room.roomId
      return room
    },

    async leaveRoom(expectedRoomId: number | null = null): Promise<boolean> {
      if (this.leaving) return false

      const roomId = expectedRoomId ?? this.activeRoom?.roomId ?? this.activeRoomId
      if (roomId === null) {
        const error = new Error('현재 방 정보를 확인할 수 없습니다.')
        this.lastError = error.message
        throw error
      }

      this.leaving = true
      this.lastError = null
      this.lastMessage = null
      try {
        await roomApi.leaveRoom(roomId)
        this.clearCurrentRoomContext(roomId)
        this.lastMessage = '방을 나갔습니다.'
        try {
          await this.client().disconnect()
        } catch {
          this.roomConnectionState = 'DISCONNECTED'
        }
        return true
      } catch (error: unknown) {
        this.lastError = getApiErrorMessage(error, '방을 나가지 못했습니다.')
        throw error
      } finally {
        this.leaving = false
      }
    },

    async connectLobby(): Promise<void> {
      await this.client().connectLobby()
    },

    async connectRoom(roomId: number): Promise<void> {
      await this.client().connectRoom(roomId)
    },

    async disconnectRealtime(): Promise<void> {
      if (realtimeClient) await realtimeClient.disconnect()
    },

    setReady(ready: boolean): void {
      if (this.commandInProgress || this.activeRoomId === null) return
      this.commandInProgress = true
      this.lastError = null
      try {
        this.client().publishReady(this.activeRoomId, createActionId(), ready)
      } catch (error: unknown) {
        this.commandInProgress = false
        this.lastError = getApiErrorMessage(error, '준비 상태를 변경하지 못했습니다.')
      }
    },

    requestStart(): void {
      if (this.commandInProgress || this.activeRoomId === null) return
      this.commandInProgress = true
      this.lastError = null
      try {
        this.client().publishStart(this.activeRoomId, createActionId())
      } catch (error: unknown) {
        this.commandInProgress = false
        this.lastError = getApiErrorMessage(error, '게임을 시작하지 못했습니다.')
      }
    },

    applyLobbyEvent(event: RealtimeRoomEvent): void {
      if (event.eventType === 'ROOM_REMOVED') {
        const roomId = (event.payload as { roomId: number }).roomId
        this.clearTerminatedRoom(roomId)
        return
      }
      if (event.eventType === 'ROOM_CREATED' || event.eventType === 'ROOM_UPDATED') {
        const room = event.payload as RoomSummary
        const index = this.rooms.findIndex((candidate) => candidate.roomId === room.roomId)
        if (index === -1) this.rooms = [room, ...this.rooms]
        else this.rooms.splice(index, 1, room)
      }
    },

    applyRoomEvent(event: RealtimeRoomEvent): void {
      if (event.eventType === 'ROOM_CLOSED') {
        const roomId = (event.payload as { roomId: number }).roomId
        this.clearTerminatedRoom(roomId)
        this.lastMessage = '방이 종료되어 로비로 이동합니다.'
        return
      }
      if (event.eventType === 'GAME_STARTED') {
        const payload = event.payload as { gameId: number; roomId: number; route: string }
        if (!this.activeRoom || payload.roomId !== this.activeRoom.roomId || !Number.isInteger(payload.gameId) || payload.gameId <= 0) return
        this.commandInProgress = false
        this.launchedGameId = payload.gameId
        this.lastMessage = '게임이 시작되었습니다.'
        return
      }
      if (!this.activeRoom) return
      if (event.eventType === 'ROOM_STATE_UPDATED') {
        const snapshot = event.payload as RoomDetail
        if (snapshot.roomId !== this.activeRoom.roomId) return
        this.activeRoom = {
          ...snapshot,
          participants: snapshot.participants.map((participant) => ({ ...participant })),
        }
        this.activeRoomId = snapshot.roomId
        return
      }
      if (event.eventType === 'ROOM_PLAYER_JOINED') {
        const participant = event.payload as RoomParticipant
        if (!this.activeRoom.participants.some((value) => value.userId === participant.userId)) {
          this.activeRoom.participants.push(participant)
          this.activeRoom.participants.sort((a, b) => a.seatOrder - b.seatOrder)
          this.activeRoom.currentPlayers = this.activeRoom.participants.length
        }
        return
      }
      if (event.eventType === 'ROOM_PLAYER_LEFT') {
        const userId = (event.payload as { userId: number }).userId
        this.activeRoom.participants = this.activeRoom.participants.filter((value) => value.userId !== userId)
        this.activeRoom.currentPlayers = this.activeRoom.participants.length
        return
      }
      if (event.eventType === 'ROOM_OWNER_CHANGED') {
        const owner = event.payload as RoomParticipant
        this.activeRoom.ownerUserId = owner.userId
        this.activeRoom.ownerNickname = owner.nickname
        this.activeRoom.participants = this.activeRoom.participants.map((participant) => ({
          ...participant,
          owner: participant.userId === owner.userId,
        }))
        return
      }
      if (event.eventType === 'ROOM_READY_CHANGED') {
        const payload = event.payload as {
          userId: number
          readyStatus: 'READY' | 'NOT_READY'
          startable: boolean
          startBlockReason: string | null
        }
        this.activeRoom.participants = this.activeRoom.participants.map((participant) =>
          participant.userId === payload.userId
            ? { ...participant, readyStatus: payload.readyStatus }
            : participant,
        )
        this.activeRoom.startable = payload.startable
        this.activeRoom.startBlockReason = payload.startBlockReason || null
      }
    },


    clearTerminatedRoom(roomId: number | null): void {
      if (roomId === null) return
      this.rooms = this.rooms.filter((room) => room.roomId !== roomId)
      if (this.activeRoomId === roomId || this.activeRoom?.roomId === roomId) {
        roomContextRevision += 1
        this.activeRoom = null
        this.activeRoomId = null
        this.commandInProgress = false
        this.launchedGameId = null
      }
    },

    consumeLaunchedGameId(): number | null {
      const gameId = this.launchedGameId
      this.launchedGameId = null
      return gameId
    },

    applyCommandReply(reply: RoomCommandReply): void {
      this.commandInProgress = false
      if (reply.eventType === 'ROOM_COMMAND_REJECTED' || reply.code) {
        this.lastError = reply.message
        return
      }
      this.lastMessage = reply.message
    },

    clearRoomState(): void {
      roomContextRevision += 1
      this.rooms = []
      this.roomsPage = null
      this.activeRoom = null
      this.activeRoomId = null
      this.lastError = null
      this.lastMessage = null
      this.commandInProgress = false
      this.launchedGameId = null
    },
  },
})

export function resetRoomStoreClientForTests(): void {
  realtimeClient = null
  roomContextRevision = 0
}

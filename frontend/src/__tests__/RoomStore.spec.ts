import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as roomApi from '@/api/roomApi'
import { resetRoomStoreClientForTests, useRoomStore } from '@/stores/room'
import type { RoomDetail, RoomSummary } from '@/types/room'

vi.mock('@/api/roomApi')

const summary: RoomSummary = {
  roomId: 10,
  roomName: '초보방',
  ownerNickname: 'owner',
  currentPlayers: 1,
  maxPlayers: 4,
  gameMode: 'CLASSIC',
  turnTimeLimitSeconds: 120,
  status: 'WAITING',
  joinable: true,
}

const detail: RoomDetail = {
  roomId: 10,
  roomName: '초보방',
  ownerUserId: 1,
  ownerNickname: 'owner',
  currentPlayers: 1,
  maxPlayers: 4,
  gameMode: 'CLASSIC',
  turnTimeLimitSeconds: 120,
  status: 'WAITING',
  startable: false,
  startBlockReason: 'ROOM_MIN_PLAYERS_NOT_MET',
  participants: [{ userId: 1, nickname: 'owner', avatarType: 'DEFAULT_01', seatOrder: 1, readyStatus: 'NOT_READY', owner: true }],
}

beforeEach(() => {
  setActivePinia(createPinia())
  resetRoomStoreClientForTests()
  vi.resetAllMocks()
})

describe('room store', () => {
  it('loads the initial room list through REST', async () => {
    vi.mocked(roomApi.getRooms).mockResolvedValue({ content: [summary], page: 0, size: 20, totalElements: 1 })
    const store = useRoomStore()

    await store.loadRooms()

    expect(store.rooms).toEqual([summary])
    expect(store.roomsPage?.totalElements).toBe(1)
  })

  it('upserts created and updated lobby events and removes closed rooms', () => {
    const store = useRoomStore()
    store.applyLobbyEvent({ eventType: 'ROOM_CREATED', occurredAt: '', payload: summary })
    store.applyLobbyEvent({ eventType: 'ROOM_UPDATED', occurredAt: '', payload: { ...summary, currentPlayers: 2 } })
    expect(store.rooms).toHaveLength(1)
    expect(store.rooms[0]?.currentPlayers).toBe(2)

    store.applyLobbyEvent({ eventType: 'ROOM_REMOVED', occurredAt: '', payload: { roomId: 10 } })
    expect(store.rooms).toHaveLength(0)
  })

  it('creates a room and records the active room id', async () => {
    vi.mocked(roomApi.createRoom).mockResolvedValue(detail)
    const store = useRoomStore()

    await store.createRoom({ roomName: '초보방', maxPlayers: 4, gameMode: 'CLASSIC', turnTimeLimitSeconds: 120, isPublic: true })

    expect(store.activeRoomId).toBe(10)
    expect(roomApi.createRoom).toHaveBeenCalledTimes(1)
  })

  it('prevents duplicate create requests while one is running', async () => {
    let resolve!: (value: RoomDetail) => void
    vi.mocked(roomApi.createRoom).mockImplementation(() => new Promise((next) => { resolve = next }))
    const store = useRoomStore()
    const first = store.createRoom({ roomName: '초보방', maxPlayers: 4, gameMode: 'CLASSIC', turnTimeLimitSeconds: 120, isPublic: true })

    await expect(store.createRoom({ roomName: '두번째방', maxPlayers: 4, gameMode: 'CLASSIC', turnTimeLimitSeconds: 120, isPublic: true })).rejects.toThrow()
    expect(roomApi.createRoom).toHaveBeenCalledTimes(1)
    resolve(detail)
    await first
  })

  it('reports no quick-match candidate and prevents duplicate lookup requests', async () => {
    let resolve!: (value: RoomSummary | null) => void
    vi.mocked(roomApi.getQuickMatch).mockImplementation(() => new Promise((next) => { resolve = next }))
    const store = useRoomStore()

    const first = store.quickMatch()
    const duplicate = await store.quickMatch()

    expect(duplicate).toBeNull()
    expect(roomApi.getQuickMatch).toHaveBeenCalledTimes(1)
    resolve(null)
    const result = await first
    expect(result).toBeNull()
    expect(store.lastMessage).toContain('없습니다')
  })

  it('applies participant join leave owner and ready events', () => {
    const store = useRoomStore()
    store.activeRoom = structuredClone(detail)
    store.activeRoomId = 10
    const second = { userId: 2, nickname: 'second', avatarType: 'DEFAULT_02', seatOrder: 2, readyStatus: 'NOT_READY' as const, owner: false }

    store.applyRoomEvent({ eventType: 'ROOM_PLAYER_JOINED', occurredAt: '', payload: second })
    store.applyRoomEvent({ eventType: 'ROOM_READY_CHANGED', occurredAt: '', payload: { userId: 2, readyStatus: 'READY', startable: true, startBlockReason: null } })
    store.applyRoomEvent({ eventType: 'ROOM_OWNER_CHANGED', occurredAt: '', payload: { ...second, owner: true } })

    expect(store.activeRoom.participants[1]?.readyStatus).toBe('READY')
    expect(store.activeRoom.ownerUserId).toBe(2)
    expect(store.activeRoom.startable).toBe(true)

    store.applyRoomEvent({ eventType: 'ROOM_PLAYER_LEFT', occurredAt: '', payload: { userId: 1 } })
    expect(store.activeRoom.participants.map((participant) => participant.userId)).toEqual([2])
  })


  it('joinEventOrSnapshotDisablesPreviouslyStartableRoom', () => {
    const store = useRoomStore()
    store.activeRoom = {
      ...structuredClone(detail),
      currentPlayers: 2,
      startable: true,
      startBlockReason: null,
      participants: [
        { ...detail.participants[0]!, readyStatus: 'READY' },
        { userId: 2, nickname: 'second', avatarType: 'DEFAULT_02', seatOrder: 2, readyStatus: 'READY', owner: false },
      ],
    }
    store.activeRoomId = 10

    const snapshot: RoomDetail = {
      ...store.activeRoom,

      currentPlayers: 3,
      startable: false,
      startBlockReason: 'ROOM_PLAYERS_NOT_READY',

      participants: [
        ...store.activeRoom.participants.map((participant) => ({
          ...participant,
        })),
        {
          userId: 3,
          nickname: 'third',
          avatarType: 'DEFAULT_03',
          seatOrder: 3,
          readyStatus: 'NOT_READY',
          owner: false,
        },
      ],
    }

    store.applyRoomEvent({ eventType: 'ROOM_STATE_UPDATED', occurredAt: '', payload: snapshot })

    expect(store.activeRoom?.currentPlayers).toBe(3)
    expect(store.activeRoom?.startable).toBe(false)
    expect(store.activeRoom?.startBlockReason).toBe('ROOM_PLAYERS_NOT_READY')
  })

  it('leaveEventOrSnapshotDisablesRoomBelowMinimumPlayers', () => {
    const store = useRoomStore()
    store.activeRoom = {
      ...structuredClone(detail),
      currentPlayers: 2,
      startable: true,
      startBlockReason: null,
      participants: [
        { ...detail.participants[0]!, readyStatus: 'READY' },
        { userId: 2, nickname: 'second', avatarType: 'DEFAULT_02', seatOrder: 2, readyStatus: 'READY', owner: false },
      ],
    }
    store.activeRoomId = 10
    const snapshot: RoomDetail = {
      ...structuredClone(detail),
      participants: [{ ...detail.participants[0]!, readyStatus: 'READY' }],
    }

    store.applyRoomEvent({ eventType: 'ROOM_STATE_UPDATED', occurredAt: '', payload: snapshot })

    expect(store.activeRoom?.currentPlayers).toBe(1)
    expect(store.activeRoom?.startable).toBe(false)
    expect(store.activeRoom?.startBlockReason).toBe('ROOM_MIN_PLAYERS_NOT_MET')
  })

  it('leavingNotReadyPlayerEnablesTwoReadyPlayers', () => {
    const store = useRoomStore()
    store.activeRoom = {
      ...structuredClone(detail),
      currentPlayers: 3,
      startable: false,
      startBlockReason: 'ROOM_PLAYERS_NOT_READY',
      participants: [
        { ...detail.participants[0]!, readyStatus: 'READY' },
        { userId: 2, nickname: 'second', avatarType: 'DEFAULT_02', seatOrder: 2, readyStatus: 'READY', owner: false },
        { userId: 3, nickname: 'third', avatarType: 'DEFAULT_03', seatOrder: 3, readyStatus: 'NOT_READY', owner: false },
      ],
    }
    store.activeRoomId = 10
    const snapshot: RoomDetail = {
      ...store.activeRoom,
      currentPlayers: 2,
      startable: true,
      startBlockReason: null,
      participants: store.activeRoom.participants
        .slice(0, 2)
        .map((participant) => ({
          ...participant,
        })),
    }

    store.applyRoomEvent({ eventType: 'ROOM_STATE_UPDATED', occurredAt: '', payload: snapshot })

    expect(store.activeRoom?.participants.map((participant) => participant.userId)).toEqual([1, 2])
    expect(store.activeRoom?.startable).toBe(true)
    expect(store.activeRoom?.startBlockReason).toBeNull()
  })

  it('roomSnapshotReplacesOwnerParticipantsAndEligibilityAtomically', () => {
    const store = useRoomStore()
    store.activeRoom = structuredClone(detail)
    store.activeRoomId = 10
    const previous = store.activeRoom
    const snapshot: RoomDetail = {
      ...structuredClone(detail),
      ownerUserId: 2,
      ownerNickname: 'second',
      currentPlayers: 2,
      startable: true,
      startBlockReason: null,
      participants: [
        { userId: 2, nickname: 'second', avatarType: 'DEFAULT_02', seatOrder: 1, readyStatus: 'READY', owner: true },
        { userId: 3, nickname: 'third', avatarType: 'DEFAULT_03', seatOrder: 2, readyStatus: 'READY', owner: false },
      ],
    }

    store.applyRoomEvent({ eventType: 'ROOM_STATE_UPDATED', occurredAt: '', payload: snapshot })

    expect(store.activeRoom).not.toBe(previous)
    expect(store.activeRoom).toEqual(snapshot)
    expect(store.activeRoom?.participants.filter((participant) => participant.owner)).toHaveLength(1)
  })

  it('records GAME_STARTED for every room participant without trusting the owner reply', () => {
    const store = useRoomStore()
    store.activeRoom = structuredClone(detail)
    store.activeRoomId = 10
    store.commandInProgress = false

    store.applyRoomEvent({
      eventType: 'GAME_STARTED',
      occurredAt: '',
      payload: { gameId: 33, roomId: 10, route: '/games/33' },
    })

    expect(store.launchedGameId).toBe(33)
    expect(store.consumeLaunchedGameId()).toBe(33)
    expect(store.launchedGameId).toBeNull()
  })

  it('clears active room and removes its lobby summary when ROOM_CLOSED arrives', () => {
    const store = useRoomStore()
    store.rooms = [structuredClone(summary)]
    store.activeRoom = structuredClone(detail)
    store.activeRoomId = 10

    store.applyRoomEvent({ eventType: 'ROOM_CLOSED', occurredAt: '', payload: { roomId: 10 } })

    expect(store.activeRoom).toBeNull()
    expect(store.activeRoomId).toBeNull()
    expect(store.rooms).toHaveLength(0)
  })

  it('treats a successful REST leave as authoritative before realtime disconnect cleanup', async () => {
    vi.mocked(roomApi.leaveRoom).mockResolvedValue(undefined)
    const store = useRoomStore()
    store.rooms = [structuredClone(summary)]
    store.activeRoom = structuredClone(detail)
    store.activeRoomId = 10

    await store.leaveRoom()

    expect(roomApi.leaveRoom).toHaveBeenCalledWith(10)
    expect(store.activeRoom).toBeNull()
    expect(store.activeRoomId).toBeNull()
    expect(store.rooms).toHaveLength(0)
    expect(store.lastMessage).toContain('나갔습니다')
  })

  it('uses the loaded room id when activeRoomId was cleared by a stale async update', async () => {
    vi.mocked(roomApi.leaveRoom).mockResolvedValue(undefined)
    const store = useRoomStore()
    store.rooms = [structuredClone(summary)]
    store.activeRoom = structuredClone(detail)
    store.activeRoomId = null

    await expect(store.leaveRoom()).resolves.toBe(true)

    expect(roomApi.leaveRoom).toHaveBeenCalledWith(10)
    expect(store.activeRoom).toBeNull()
    expect(store.activeRoomId).toBeNull()
  })


  it('uses an explicit route room id instead of stale active room state when leaving', async () => {
    vi.mocked(roomApi.leaveRoom).mockResolvedValue(undefined)
    const store = useRoomStore()
    store.activeRoom = structuredClone(detail)
    store.activeRoomId = 10

    await expect(store.leaveRoom(11)).resolves.toBe(true)

    expect(roomApi.leaveRoom).toHaveBeenCalledWith(11)
    expect(store.activeRoom).toBeNull()
    expect(store.activeRoomId).toBeNull()
  })

  it('clears stale termination feedback when a new room context is loaded', async () => {
    vi.mocked(roomApi.getRoomDetail).mockResolvedValue(detail)
    const store = useRoomStore()
    store.lastMessage = '플레이어가 게임을 포기하여 게임이 종료되었습니다.'
    store.lastError = 'old error'

    await store.loadRoomDetail(10)

    expect(store.lastMessage).toBeNull()
    expect(store.lastError).toBeNull()
    expect(store.activeRoom?.roomId).toBe(10)
  })

  it('does not report a successful leave when another leave is already running', async () => {
    const store = useRoomStore()
    store.activeRoom = structuredClone(detail)
    store.activeRoomId = 10
    store.leaving = true

    await expect(store.leaveRoom()).resolves.toBe(false)

    expect(roomApi.leaveRoom).not.toHaveBeenCalled()
  })

  it('shows command acceptance and rejection safely', () => {
    const store = useRoomStore()
    store.commandInProgress = true
    store.applyCommandReply({ eventType: 'ROOM_COMMAND_REJECTED', actionId: 'a', code: 'ROOM_FULL', message: '실패', recoverable: true, payload: null })
    expect(store.commandInProgress).toBe(false)
    expect(store.lastError).toBe('실패')

    store.applyCommandReply({ eventType: 'DUPLICATE_ACTION_REPLAYED', actionId: 'a', code: 'ROOM_FULL', message: '최초 실패', recoverable: true, payload: null })
    expect(store.lastError).toBe('최초 실패')

    store.applyCommandReply({ eventType: 'ROOM_START_REQUEST_ACCEPTED', actionId: 'b', code: null, message: '조건 충족', recoverable: false, payload: null })
    expect(store.lastMessage).toBe('조건 충족')
  })
})

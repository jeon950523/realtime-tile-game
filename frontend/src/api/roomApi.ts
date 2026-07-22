import { httpClient } from '@/api/httpClient'
import type { ApiResponse } from '@/types/api'
import type { ActiveRoom, CreateRoomRequest, RoomDetail, RoomPage, RoomSummary } from '@/types/room'

export async function getRooms(page = 0, size = 20): Promise<RoomPage> {
  const response = await httpClient.get<ApiResponse<RoomPage>>('/api/rooms', {
    params: { status: 'WAITING', gameMode: 'CLASSIC', page, size },
  })
  return response.data.data
}

export async function getQuickMatch(): Promise<RoomSummary | null> {
  const response = await httpClient.get<ApiResponse<RoomSummary | null>>('/api/rooms/quick-match', {
    params: { gameMode: 'CLASSIC' },
  })
  return response.data.data
}

export async function createRoom(request: CreateRoomRequest): Promise<RoomDetail> {
  const response = await httpClient.post<ApiResponse<RoomDetail>>('/api/rooms', request)
  return response.data.data
}

export async function joinRoom(roomId: number): Promise<RoomDetail> {
  const response = await httpClient.post<ApiResponse<RoomDetail>>(`/api/rooms/${roomId}/join`)
  return response.data.data
}

export async function getRoomDetail(roomId: number): Promise<RoomDetail> {
  const response = await httpClient.get<ApiResponse<RoomDetail>>(`/api/rooms/${roomId}`)
  return response.data.data
}

export async function leaveRoom(roomId: number): Promise<void> {
  await httpClient.delete(`/api/rooms/${roomId}/members/me`)
}

export async function getActiveRoom(): Promise<ActiveRoom> {
  const response = await httpClient.get<ApiResponse<ActiveRoom>>('/api/me/active-room')
  return response.data.data
}

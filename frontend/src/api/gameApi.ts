import { httpClient } from '@/api/httpClient'
import type { ApiResponse } from '@/types/api'
import type { ActiveGame, GamePrivateState } from '@/types/game'

export async function getGameState(gameId: number): Promise<GamePrivateState> {
  const response = await httpClient.get<ApiResponse<GamePrivateState>>(`/api/games/${gameId}`)
  return response.data.data
}

export async function getActiveGame(): Promise<ActiveGame> {
  const response = await httpClient.get<ApiResponse<ActiveGame>>('/api/me/active-game')
  return response.data.data
}

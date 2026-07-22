import { httpClient } from '@/api/httpClient'
import type { ApiResponse } from '@/types/api'
import type { HealthResponse } from '@/types/health'

export async function fetchHealth(): Promise<HealthResponse> {
  const response = await httpClient.get<ApiResponse<HealthResponse>>('/api/health')
  return response.data.data
}

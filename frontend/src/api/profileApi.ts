import { httpClient } from '@/api/httpClient'
import type { ApiResponse } from '@/types/api'
import type { MyProfile, ProfileUpdateRequest, ProfileUpdateResponse } from '@/types/auth'

export async function getMyProfile(): Promise<MyProfile> {
  const response = await httpClient.get<ApiResponse<MyProfile>>('/api/me')
  return response.data.data
}

export async function updateMyProfile(request: ProfileUpdateRequest): Promise<ProfileUpdateResponse> {
  const response = await httpClient.patch<ApiResponse<ProfileUpdateResponse>>(
    '/api/me/profile',
    request,
  )
  return response.data.data
}

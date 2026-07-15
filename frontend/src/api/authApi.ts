import { httpClient, refreshAccessToken } from '@/api/httpClient'
import type { ApiResponse } from '@/types/api'
import type {
  LoginRequest,
  LoginResponse,
  RegisterRequest,
  RegisterResponse,
  ReissueResponse,
} from '@/types/auth'

export async function register(request: RegisterRequest): Promise<RegisterResponse> {
  const response = await httpClient.post<ApiResponse<RegisterResponse>>('/api/auth/register', request)
  return response.data.data
}

export async function login(request: LoginRequest): Promise<LoginResponse> {
  const response = await httpClient.post<ApiResponse<LoginResponse>>('/api/auth/login', request)
  return response.data.data
}

export async function reissue(): Promise<ReissueResponse> {
  return refreshAccessToken()
}

export async function logout(): Promise<void> {
  await httpClient.post('/api/auth/logout')
}

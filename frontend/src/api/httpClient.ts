import axios, {
  AxiosError,
  AxiosHeaders,
  type AxiosInstance,
  type InternalAxiosRequestConfig,
} from 'axios'

import { resolveApiBaseUrl } from '@/config/runtimeEndpoints'
import type { ApiResponse } from '@/types/api'
import type { ReissueResponse } from '@/types/auth'

interface AuthenticationHooks {
  getAccessToken: () => string | null
  acceptRefreshedAccessToken: (accessToken: string) => void
  authenticationLost: () => void
}

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _authRetry?: boolean
}

const PUBLIC_AUTH_PATHS = new Set([
  '/api/auth/register',
  '/api/auth/login',
  '/api/auth/reissue',
  '/api/auth/logout',
])

const baseConfig = {
  baseURL: resolveApiBaseUrl(),
  timeout: 5_000,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
  },
}

export const httpClient = axios.create(baseConfig)
const refreshClient = axios.create(baseConfig)

let hooks: AuthenticationHooks = {
  getAccessToken: () => null,
  acceptRefreshedAccessToken: () => undefined,
  authenticationLost: () => undefined,
}
let refreshFlight: Promise<ReissueResponse> | null = null

export function configureHttpAuthentication(nextHooks: AuthenticationHooks): void {
  hooks = nextHooks
}

export function refreshAccessToken(): Promise<ReissueResponse> {
  if (refreshFlight) {
    return refreshFlight
  }

  refreshFlight = refreshClient
    .post<ApiResponse<ReissueResponse>>('/api/auth/reissue')
    .then((response) => {
      const result = response.data.data
      hooks.acceptRefreshedAccessToken(result.accessToken)
      return result
    })
    .catch((error: unknown) => {
      hooks.authenticationLost()
      throw error
    })
    .finally(() => {
      refreshFlight = null
    })

  return refreshFlight
}

function requestPath(config: InternalAxiosRequestConfig): string {
  const url = config.url ?? ''
  try {
    return new URL(url, 'http://client.local').pathname
  } catch {
    return url
  }
}

function shouldAttemptRefresh(config: RetriableRequestConfig | undefined): config is RetriableRequestConfig {
  if (!config || config._authRetry) {
    return false
  }
  return !PUBLIC_AUTH_PATHS.has(requestPath(config))
}

httpClient.interceptors.request.use((config) => {
  const accessToken = hooks.getAccessToken()
  if (accessToken && !PUBLIC_AUTH_PATHS.has(requestPath(config))) {
    config.headers = AxiosHeaders.from(config.headers)
    config.headers.set('Authorization', `Bearer ${accessToken}`)
  }
  return config
})

httpClient.interceptors.response.use(
  (response) => response,
  async (error: unknown) => {
    if (!axios.isAxiosError(error) || error.response?.status !== 401) {
      return Promise.reject(error)
    }

    const originalRequest = error.config as RetriableRequestConfig | undefined
    if (!shouldAttemptRefresh(originalRequest)) {
      return Promise.reject(error)
    }

    originalRequest._authRetry = true
    try {
      const refreshed = await refreshAccessToken()
      originalRequest.headers = AxiosHeaders.from(originalRequest.headers)
      originalRequest.headers.set('Authorization', `Bearer ${refreshed.accessToken}`)
      return await httpClient.request(originalRequest)
    } catch (refreshError: unknown) {
      return Promise.reject(refreshError)
    }
  },
)

export const httpClientTesting = {
  refreshClient(): AxiosInstance {
    return refreshClient
  },
  reset(): void {
    refreshFlight = null
    hooks = {
      getAccessToken: () => null,
      acceptRefreshedAccessToken: () => undefined,
      authenticationLost: () => undefined,
    }
  },
}

import {
  AxiosError,
  AxiosHeaders,
  type AxiosResponse,
  type InternalAxiosRequestConfig,
} from 'axios'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import {
  configureHttpAuthentication,
  httpClient,
  httpClientTesting,
} from '@/api/httpClient'

function response(config: InternalAxiosRequestConfig, data: unknown = {}): AxiosResponse {
  return { data, status: 200, statusText: 'OK', headers: {}, config }
}

function unauthorized(config: InternalAxiosRequestConfig): AxiosError {
  return new AxiosError(
    'unauthorized',
    'ERR_BAD_REQUEST',
    config,
    undefined,
    { data: {}, status: 401, statusText: 'Unauthorized', headers: {}, config },
  )
}

let accessToken: string | null
let lostCount: number

beforeEach(() => {
  accessToken = null
  lostCount = 0
  httpClientTesting.reset()
  configureHttpAuthentication({
    getAccessToken: () => accessToken,
    acceptRefreshedAccessToken: (token) => {
      accessToken = token
    },
    authenticationLost: () => {
      accessToken = null
      lostCount += 1
    },
  })
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('authenticated http client', () => {
  it('adds the in-memory access token to protected requests', async () => {
    accessToken = 'memory-token'
    let authorization: string | undefined
    httpClient.defaults.adapter = async (config) => {
      authorization = AxiosHeaders.from(config.headers).get('Authorization')?.toString()
      return response(config)
    }

    await httpClient.get('/api/me')

    expect(authorization).toBe('Bearer memory-token')
  })

  it('uses one refresh request for concurrent 401 responses and retries each request once', async () => {
    accessToken = 'expired-token'
    let refreshCalls = 0
    let requestCalls = 0
    httpClientTesting.refreshClient().defaults.adapter = async (config) => {
      refreshCalls += 1
      await Promise.resolve()
      return response(config, {
        success: true,
        data: { accessToken: 'new-token', expiresIn: 1800 },
        timestamp: '2026-07-15T00:00:00Z',
      })
    }
    httpClient.defaults.adapter = async (config) => {
      requestCalls += 1
      const header = AxiosHeaders.from(config.headers).get('Authorization')
      if (header !== 'Bearer new-token') {
        throw unauthorized(config)
      }
      return response(config, { ok: true })
    }

    const results = await Promise.all([httpClient.get('/api/me'), httpClient.get('/api/me')])

    expect(results).toHaveLength(2)
    expect(refreshCalls).toBe(1)
    expect(requestCalls).toBe(4)
    expect(accessToken).toBe('new-token')
  })

  it('clears authentication when refresh fails', async () => {
    accessToken = 'expired-token'
    httpClientTesting.refreshClient().defaults.adapter = async (config) => {
      throw unauthorized(config)
    }
    httpClient.defaults.adapter = async (config) => {
      throw unauthorized(config)
    }

    await expect(httpClient.get('/api/me')).rejects.toBeInstanceOf(AxiosError)

    expect(lostCount).toBe(1)
    expect(accessToken).toBeNull()
  })

  it('does not refresh public login failures', async () => {
    const refreshAdapter = vi.fn(async (config: InternalAxiosRequestConfig) => response(config))
    httpClientTesting.refreshClient().defaults.adapter = refreshAdapter
    httpClient.defaults.adapter = async (config) => {
      throw unauthorized(config)
    }

    await expect(httpClient.post('/api/auth/login', {})).rejects.toBeInstanceOf(AxiosError)

    expect(refreshAdapter).not.toHaveBeenCalled()
  })

  it('never retries the same protected request more than once', async () => {
    accessToken = 'expired-token'
    let requestCalls = 0
    let refreshCalls = 0
    httpClientTesting.refreshClient().defaults.adapter = async (config) => {
      refreshCalls += 1
      return response(config, {
        success: true,
        data: { accessToken: 'still-invalid', expiresIn: 1800 },
        timestamp: '2026-07-15T00:00:00Z',
      })
    }
    httpClient.defaults.adapter = async (config) => {
      requestCalls += 1
      throw unauthorized(config)
    }

    await expect(httpClient.get('/api/me')).rejects.toBeInstanceOf(AxiosError)

    expect(refreshCalls).toBe(1)
    expect(requestCalls).toBe(2)
  })
})

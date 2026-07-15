import { AxiosError, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as authApi from '@/api/authApi'
import * as profileApi from '@/api/profileApi'
import { resetAuthStoreFlightsForTests, useAuthStore } from '@/stores/auth'
import type { MyProfile } from '@/types/auth'

vi.mock('@/api/authApi')
vi.mock('@/api/profileApi')

const profile: MyProfile = {
  userId: 1,
  email: 'user@example.com',
  nickname: 'player1',
  avatarType: 'DEFAULT_01',
  ratingScore: 1000,
  classicRecord: { wins: 0, losses: 0, draws: 0, totalGames: 0 },
  speedRecord: { wins: 0, losses: 0, draws: 0, totalGames: 0 },
  activeSession: { roomId: null, gameId: null, status: null },
}

function refreshMissingError(): AxiosError {
  const config = { headers: {} } as InternalAxiosRequestConfig
  const response = {
    data: {
      success: false,
      error: { code: 'REFRESH_TOKEN_MISSING', message: 'Refresh Token이 없습니다.', fieldErrors: [] },
      timestamp: '2026-07-15T00:00:00Z',
    },
    status: 401,
    statusText: 'Unauthorized',
    headers: {},
    config,
  } as AxiosResponse
  return new AxiosError('missing', 'ERR_BAD_REQUEST', config, undefined, response)
}

beforeEach(() => {
  setActivePinia(createPinia())
  resetAuthStoreFlightsForTests()
  vi.resetAllMocks()
})

describe('auth store', () => {
  it('stores the login access token and user in memory state', async () => {
    vi.mocked(authApi.login).mockResolvedValue({
      accessToken: 'access-token',
      expiresIn: 1800,
      user: { userId: 1, nickname: 'player1', avatarType: 'DEFAULT_01', ratingScore: 1000 },
      redirect: { type: 'LOBBY', roomId: null, gameId: null },
    })
    const store = useAuthStore()

    await store.login({ email: 'user@example.com', password: 'qwer1234!' })

    expect(store.accessToken).toBe('access-token')
    expect(store.authStatus).toBe('AUTHENTICATED')
    expect(store.user?.nickname).toBe('player1')
  })

  it('restores the session once and loads the profile', async () => {
    vi.mocked(authApi.reissue).mockResolvedValue({ accessToken: 'restored-token', expiresIn: 1800 })
    vi.mocked(profileApi.getMyProfile).mockResolvedValue(profile)
    const store = useAuthStore()

    await Promise.all([store.restoreSession(), store.restoreSession()])

    expect(authApi.reissue).toHaveBeenCalledTimes(1)
    expect(profileApi.getMyProfile).toHaveBeenCalledTimes(1)
    expect(store.initializationState).toBe('READY')
    expect(store.authStatus).toBe('AUTHENTICATED')
    expect(store.profile?.email).toBe('user@example.com')
  })

  it('treats a missing refresh cookie as an ordinary anonymous session', async () => {
    vi.mocked(authApi.reissue).mockRejectedValue(refreshMissingError())
    const store = useAuthStore()

    await store.restoreSession()

    expect(store.authStatus).toBe('ANONYMOUS')
    expect(store.lastError).toBeNull()
    expect(store.initializationState).toBe('READY')
  })

  it('logoutSuccessClearsAuthentication', async () => {
    vi.mocked(authApi.logout).mockResolvedValue()
    const store = useAuthStore()
    store.accessToken = 'token'
    store.user = { userId: 1, nickname: 'player1', avatarType: 'DEFAULT_01', ratingScore: 1000 }
    store.profile = profile
    store.authStatus = 'AUTHENTICATED'

    await store.logout()

    expect(store.accessToken).toBeNull()
    expect(store.user).toBeNull()
    expect(store.profile).toBeNull()
    expect(store.authStatus).toBe('ANONYMOUS')
    expect(store.logoutInProgress).toBe(false)
  })

  it('logoutFailureKeepsAuthentication', async () => {
    vi.mocked(authApi.logout).mockRejectedValue(new Error('network'))
    const store = useAuthStore()
    store.accessToken = 'token'
    store.user = { userId: 1, nickname: 'player1', avatarType: 'DEFAULT_01', ratingScore: 1000 }
    store.profile = profile
    store.authStatus = 'AUTHENTICATED'

    await expect(store.logout()).rejects.toThrow('network')

    expect(store.accessToken).toBe('token')
    expect(store.user?.nickname).toBe('player1')
    expect(store.profile?.email).toBe('user@example.com')
    expect(store.authStatus).toBe('AUTHENTICATED')
    expect(store.lastError).toBe('로그아웃에 실패했습니다. 잠시 후 다시 시도해주세요.')
    expect(store.logoutInProgress).toBe(false)
  })

  it('repeatedLogoutClickDoesNotSendDuplicateRequest', async () => {
    let resolveLogout!: () => void
    vi.mocked(authApi.logout).mockImplementation(() => new Promise<void>((resolve) => {
      resolveLogout = resolve
    }))
    const store = useAuthStore()
    store.accessToken = 'token'
    store.user = { userId: 1, nickname: 'player1', avatarType: 'DEFAULT_01', ratingScore: 1000 }
    store.authStatus = 'AUTHENTICATED'

    const first = store.logout()
    const second = store.logout()

    expect(authApi.logout).toHaveBeenCalledTimes(1)
    expect(store.logoutInProgress).toBe(true)
    resolveLogout()
    await Promise.all([first, second])
    expect(store.logoutInProgress).toBe(false)
  })

  it('updates profile and user state together', async () => {
    vi.mocked(profileApi.updateMyProfile).mockResolvedValue({ nickname: 'newName', avatarType: 'DEFAULT_03' })
    const store = useAuthStore()
    store.profile = profile
    store.user = { userId: 1, nickname: 'player1', avatarType: 'DEFAULT_01', ratingScore: 1000 }

    await store.updateProfile({ nickname: 'newName', avatarType: 'DEFAULT_03' })

    expect(store.profile.nickname).toBe('newName')
    expect(store.user.nickname).toBe('newName')
    expect(store.user.avatarType).toBe('DEFAULT_03')
  })
})

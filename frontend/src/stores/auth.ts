import { defineStore } from 'pinia'

import * as authApi from '@/api/authApi'
import { getApiErrorCode, getApiErrorMessage } from '@/api/apiError'
import * as profileApi from '@/api/profileApi'
import type {
  AuthStatus,
  AuthUser,
  InitializationState,
  LoginRequest,
  MyProfile,
  ProfileUpdateRequest,
  RegisterRequest,
  RegisterResponse,
} from '@/types/auth'

interface AuthState {
  accessToken: string | null
  user: AuthUser | null
  profile: MyProfile | null
  initializationState: InitializationState
  authStatus: AuthStatus
  lastError: string | null
  logoutInProgress: boolean
}

let restoreFlight: Promise<void> | null = null
let logoutFlight: Promise<void> | null = null

function userFromProfile(profile: MyProfile): AuthUser {
  return {
    userId: profile.userId,
    nickname: profile.nickname,
    avatarType: profile.avatarType,
    ratingScore: profile.ratingScore,
  }
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => ({
    accessToken: null,
    user: null,
    profile: null,
    initializationState: 'UNINITIALIZED',
    authStatus: 'ANONYMOUS',
    lastError: null,
    logoutInProgress: false,
  }),

  actions: {
    acceptRefreshedAccessToken(accessToken: string): void {
      this.accessToken = accessToken
    },

    clearAuthentication(): void {
      this.accessToken = null
      this.user = null
      this.profile = null
      this.authStatus = 'ANONYMOUS'
    },

    async register(request: RegisterRequest): Promise<RegisterResponse> {
      this.lastError = null
      try {
        return await authApi.register(request)
      } catch (error: unknown) {
        this.lastError = getApiErrorMessage(error, '회원가입에 실패했습니다.')
        throw error
      }
    },

    async login(request: LoginRequest): Promise<void> {
      this.lastError = null
      try {
        const result = await authApi.login(request)
        this.accessToken = result.accessToken
        this.user = result.user
        this.profile = null
        this.authStatus = 'AUTHENTICATED'
        this.initializationState = 'READY'
      } catch (error: unknown) {
        this.clearAuthentication()
        this.lastError = getApiErrorMessage(error, '로그인에 실패했습니다.')
        throw error
      }
    },

    async restoreSession(): Promise<void> {
      if (this.initializationState === 'READY') {
        return
      }
      if (restoreFlight) {
        return restoreFlight
      }

      this.initializationState = 'RESTORING'
      this.lastError = null
      restoreFlight = (async () => {
        try {
          const result = await authApi.reissue()
          this.accessToken = result.accessToken
          await this.loadProfile()
          this.authStatus = 'AUTHENTICATED'
        } catch (error: unknown) {
          this.clearAuthentication()
          const code = getApiErrorCode(error)
          if (code === null || !code.startsWith('REFRESH_TOKEN_')) {
            this.lastError = getApiErrorMessage(error, '로그인 상태를 복구하지 못했습니다.')
          }
        } finally {
          this.initializationState = 'READY'
          restoreFlight = null
        }
      })()

      return restoreFlight
    },

    async logout(): Promise<void> {
      if (logoutFlight) {
        return logoutFlight
      }

      this.lastError = null
      this.logoutInProgress = true
      logoutFlight = (async () => {
        try {
          await authApi.logout()
          this.clearAuthentication()
          this.initializationState = 'READY'
        } catch (error: unknown) {
          this.lastError = '로그아웃에 실패했습니다. 잠시 후 다시 시도해주세요.'
          throw error
        } finally {
          this.logoutInProgress = false
          logoutFlight = null
        }
      })()

      return logoutFlight
    },

    async loadProfile(): Promise<MyProfile> {
      const profile = await profileApi.getMyProfile()
      this.profile = profile
      this.user = userFromProfile(profile)
      this.authStatus = 'AUTHENTICATED'
      return profile
    },

    async updateProfile(request: ProfileUpdateRequest): Promise<void> {
      this.lastError = null
      try {
        const updated = await profileApi.updateMyProfile(request)
        if (!this.profile) {
          await this.loadProfile()
          return
        }
        this.profile = {
          ...this.profile,
          nickname: updated.nickname,
          avatarType: updated.avatarType,
        }
        this.user = userFromProfile(this.profile)
      } catch (error: unknown) {
        this.lastError = getApiErrorMessage(error, '프로필 저장에 실패했습니다.')
        throw error
      }
    },
  },
})

export function resetAuthStoreFlightsForTests(): void {
  restoreFlight = null
  logoutFlight = null
}

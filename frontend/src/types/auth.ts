export type AvatarType = 'DEFAULT_01' | 'DEFAULT_02' | 'DEFAULT_03' | 'DEFAULT_04'

export type AuthStatus = 'ANONYMOUS' | 'AUTHENTICATED'
export type InitializationState = 'UNINITIALIZED' | 'RESTORING' | 'READY'

export interface AuthUser {
  userId: number
  nickname: string
  avatarType: AvatarType
  ratingScore: number
}

export interface LoginRequest {
  email: string
  password: string
}

export interface LoginResponse {
  accessToken: string
  expiresIn: number
  user: AuthUser
  redirect: {
    type: 'LOBBY'
    roomId: number | null
    gameId: number | null
  }
}

export interface RegisterRequest {
  email: string
  password: string
  passwordConfirm: string
  nickname: string
}

export interface RegisterResponse {
  userId: number
  email: string
  nickname: string
  profileSetupRequired: boolean
}

export interface ReissueResponse {
  accessToken: string
  expiresIn: number
}

export interface RecordSummary {
  wins: number
  losses: number
  draws: number
  totalGames: number
}

export interface MyProfile {
  userId: number
  email: string
  nickname: string
  avatarType: AvatarType
  ratingScore: number
  classicRecord: RecordSummary
  speedRecord: RecordSummary
  activeSession: {
    roomId: number | null
    gameId: number | null
    status: string | null
  }
}

export interface ProfileUpdateRequest {
  nickname: string
  avatarType: AvatarType
}

export interface ProfileUpdateResponse {
  nickname: string
  avatarType: AvatarType
}

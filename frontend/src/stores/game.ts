import { defineStore } from 'pinia'

import { getApiErrorMessage } from '@/api/apiError'
import * as gameApi from '@/api/gameApi'
import { AuthenticatedStompClient } from '@/realtime/authenticatedStompClient'
import { useAuthStore } from '@/stores/auth'
import type {
  ActiveGame,
  GameConnectionState,
  GamePrivateState,
  GamePublicState,
  RealtimeGameEvent,
} from '@/types/game'

interface GameState {
  activeGameId: number | null
  privateState: GamePrivateState | null
  connectionState: GameConnectionState
  loading: boolean
  lastError: string | null
}

let realtimeClient: AuthenticatedStompClient | null = null

export const useGameStore = defineStore('game', {
  state: (): GameState => ({
    activeGameId: null,
    privateState: null,
    connectionState: 'DISCONNECTED',
    loading: false,
    lastError: null,
  }),

  getters: {
    publicState: (state): GamePublicState | null => state.privateState?.publicState ?? null,
    myRack: (state) => state.privateState?.myRack ?? [],
  },

  actions: {
    client(): AuthenticatedStompClient {
      if (realtimeClient) return realtimeClient
      const authStore = useAuthStore()
      realtimeClient = new AuthenticatedStompClient({
        getAccessToken: () => authStore.accessToken,
        onStateChange: (state, message) => {
          this.connectionState = state
          if (message) this.lastError = message
        },
        onGameEvent: (event) => this.applyGameEvent(event),
        onGameState: (event) => this.applyPrivateStateEvent(event),
        onGameReconnect: async (gameId) => { await this.loadGame(gameId) },
      })
      return realtimeClient
    },

    async loadActiveGame(): Promise<ActiveGame> {
      const active = await gameApi.getActiveGame()
      this.activeGameId = active.active ? active.gameId : null
      if (!active.active) this.privateState = null
      return active
    },

    async loadGame(gameId: number): Promise<GamePrivateState> {
      if (this.loading) {
        if (this.privateState?.publicState.gameId === gameId) return this.privateState
        throw new Error('다른 게임 상태를 불러오는 중입니다.')
      }
      this.loading = true
      this.lastError = null
      try {
        const state = await gameApi.getGameState(gameId)
        if (state.publicState.gameId !== gameId) throw new Error('게임 상태 식별자가 일치하지 않습니다.')
        this.privateState = state
        this.activeGameId = gameId
        return state
      } catch (error: unknown) {
        this.lastError = getApiErrorMessage(error, '게임 상태를 불러오지 못했습니다.')
        throw error
      } finally {
        this.loading = false
      }
    },

    async connectGame(gameId: number): Promise<void> {
      await this.client().connectGame(gameId)
    },

    async initialize(gameId: number): Promise<GamePrivateState> {
      const state = await this.loadGame(gameId)
      await this.connectGame(gameId)
      return state
    },

    applyGameEvent(event: RealtimeGameEvent): void {
      if (event.eventType !== 'GAME_STATE_UPDATED' || !this.privateState) return
      const publicState = event.payload as GamePublicState
      if (publicState.gameId !== this.privateState.publicState.gameId) return
      this.privateState = {
        ...this.privateState,
        publicState: {
          ...publicState,
          players: publicState.players.map((player) => ({ ...player })),
          tableMelds: [...publicState.tableMelds],
        },
      }
    },

    applyPrivateStateEvent(event: RealtimeGameEvent): void {
      if (event.eventType !== 'GAME_STATE_UPDATED') return
      const state = event.payload as GamePrivateState
      if (this.activeGameId !== null && state.publicState.gameId !== this.activeGameId) return
      this.privateState = {
        ...state,
        publicState: {
          ...state.publicState,
          players: state.publicState.players.map((player) => ({ ...player })),
          tableMelds: [...state.publicState.tableMelds],
        },
        myRack: state.myRack.map((tile) => ({ ...tile })),
      }
      this.activeGameId = state.publicState.gameId
    },

    async disconnectRealtime(): Promise<void> {
      if (realtimeClient) await realtimeClient.disconnect()
    },

    clearGameState(): void {
      this.activeGameId = null
      this.privateState = null
      this.connectionState = 'DISCONNECTED'
      this.loading = false
      this.lastError = null
    },
  },
})

export function resetGameStoreClientForTests(): void {
  realtimeClient = null
}

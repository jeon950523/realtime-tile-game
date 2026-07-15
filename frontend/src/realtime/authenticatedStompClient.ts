import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'

import { refreshAccessToken } from '@/api/httpClient'
import type { RealtimeGameEvent } from '@/types/game'
import type { RealtimeRoomEvent, RoomCommandReply, RoomConnectionState } from '@/types/room'

interface AuthenticatedStompClientOptions {
  getAccessToken: () => string | null
  onStateChange: (state: RoomConnectionState, message?: string | null) => void
  onLobbyEvent?: (event: RealtimeRoomEvent) => void
  onRoomEvent?: (event: RealtimeRoomEvent) => void
  onReply?: (reply: RoomCommandReply) => void
  onGameEvent?: (event: RealtimeGameEvent) => void
  onGameState?: (event: RealtimeGameEvent) => void
  onLobbyReconnect?: () => Promise<void> | void
  onRoomReconnect?: (roomId: number) => Promise<void> | void
  onGameReconnect?: (gameId: number) => Promise<void> | void
}

export class AuthenticatedStompClient {
  private client: Client | null = null
  private lobbySubscription: StompSubscription | null = null
  private roomSubscription: StompSubscription | null = null
  private replySubscription: StompSubscription | null = null
  private gameSubscription: StompSubscription | null = null
  private gameStateSubscription: StompSubscription | null = null
  private roomId: number | null = null
  private gameId: number | null = null
  private authRecoveryUsed = false
  private connecting: Promise<void> | null = null
  private hasConnectedBefore = false

  constructor(private readonly options: AuthenticatedStompClientOptions) {}

  connectLobby(): Promise<void> {
    this.roomId = null
    this.gameId = null
    this.unsubscribeRoom()
    this.unsubscribeGame()
    return this.ensureConnected()
  }

  async connectRoom(roomId: number): Promise<void> {
    if (this.roomId !== roomId) this.unsubscribeRoom()
    this.roomId = roomId
    this.gameId = null
    this.unsubscribeGame()
    await this.ensureConnected()
    this.subscribeRoom()
  }

  async connectGame(gameId: number): Promise<void> {
    if (this.gameId !== gameId) this.unsubscribeGame()
    this.roomId = null
    this.gameId = gameId
    this.unsubscribeRoom()
    await this.ensureConnected()
    this.subscribeGame()
    this.subscribeGameState()
  }

  async disconnect(): Promise<void> {
    this.lobbySubscription?.unsubscribe()
    this.roomSubscription?.unsubscribe()
    this.replySubscription?.unsubscribe()
    this.gameSubscription?.unsubscribe()
    this.gameStateSubscription?.unsubscribe()
    this.lobbySubscription = null
    this.roomSubscription = null
    this.replySubscription = null
    this.gameSubscription = null
    this.gameStateSubscription = null
    this.roomId = null
    this.gameId = null
    const client = this.client
    this.client = null
    if (client?.active) await client.deactivate()
    this.options.onStateChange('DISCONNECTED')
  }

  publishReady(roomId: number, actionId: string, ready: boolean): void {
    this.publish(`/app/rooms/${roomId}/ready`, { actionId, ready })
  }

  publishStart(roomId: number, actionId: string): void {
    this.publish(`/app/rooms/${roomId}/start`, { actionId })
  }

  private ensureConnected(): Promise<void> {
    if (this.client?.connected) {
      this.subscribeConfiguredDestinations()
      return Promise.resolve()
    }
    if (this.connecting) return this.connecting

    this.connecting = new Promise<void>((resolve, reject) => {
      this.options.onStateChange('CONNECTING')
      const client = new Client({
        brokerURL: import.meta.env.VITE_WS_URL,
        reconnectDelay: 3_000,
        heartbeatIncoming: 10_000,
        heartbeatOutgoing: 10_000,
        debug: () => undefined,
        beforeConnect: async () => {
          let accessToken = this.options.getAccessToken()
          if (!accessToken) accessToken = (await refreshAccessToken()).accessToken
          client.connectHeaders = { Authorization: `Bearer ${accessToken}` }
        },
        onConnect: async () => {
          const reconnect = this.hasConnectedBefore
          this.hasConnectedBefore = true
          this.authRecoveryUsed = false
          this.subscribeConfiguredDestinations()
          this.options.onStateChange('CONNECTED')
          if (reconnect) await this.recoverCurrentContext()
          this.connecting = null
          resolve()
        },
        onStompError: async (frame) => {
          const message = frame.headers.message ?? '실시간 연결 인증에 실패했습니다.'
          if (!this.authRecoveryUsed && /AUTHENTICATION_REQUIRED|expired|token/i.test(message)) {
            this.authRecoveryUsed = true
            try {
              await refreshAccessToken()
              await client.deactivate()
              client.activate()
              return
            } catch {
              // Fall through to terminal failure.
            }
          }
          this.options.onStateChange('FAILED', '실시간 연결을 복구하지 못했습니다.')
          this.connecting = null
          reject(new Error(message))
        },
        onWebSocketError: () => {
          this.options.onStateChange('FAILED', 'WebSocket 연결에 실패했습니다.')
        },
        onWebSocketClose: () => {
          this.lobbySubscription = null
          this.roomSubscription = null
          this.replySubscription = null
          this.gameSubscription = null
          this.gameStateSubscription = null
          if (this.client?.active) this.options.onStateChange('CONNECTING')
        },
      })
      this.client = client
      client.activate()
    })

    return this.connecting
  }

  private subscribeConfiguredDestinations(): void {
    this.subscribeLobby()
    this.subscribeReply()
    this.subscribeRoom()
    this.subscribeGame()
    this.subscribeGameState()
  }

  private subscribeLobby(): void {
    if (!this.options.onLobbyEvent || !this.client?.connected || this.lobbySubscription) return
    this.lobbySubscription = this.client.subscribe('/topic/lobby/rooms', (message) => {
      this.options.onLobbyEvent?.(this.parse<RealtimeRoomEvent>(message))
    })
  }

  private subscribeRoom(): void {
    if (!this.options.onRoomEvent || !this.client?.connected || this.roomId === null || this.roomSubscription) return
    this.roomSubscription = this.client.subscribe(`/topic/rooms/${this.roomId}`, (message) => {
      this.options.onRoomEvent?.(this.parse<RealtimeRoomEvent>(message))
    })
  }

  private subscribeReply(): void {
    if (!this.options.onReply || !this.client?.connected || this.replySubscription) return
    this.replySubscription = this.client.subscribe('/user/queue/replies', (message) => {
      this.options.onReply?.(this.parse<RoomCommandReply>(message))
    })
  }

  private subscribeGame(): void {
    if (!this.options.onGameEvent || !this.client?.connected || this.gameId === null || this.gameSubscription) return
    this.gameSubscription = this.client.subscribe(`/topic/games/${this.gameId}`, (message) => {
      this.options.onGameEvent?.(this.parse<RealtimeGameEvent>(message))
    })
  }

  private subscribeGameState(): void {
    if (!this.options.onGameState || !this.client?.connected || this.gameId === null || this.gameStateSubscription) return
    this.gameStateSubscription = this.client.subscribe('/user/queue/game-state', (message) => {
      this.options.onGameState?.(this.parse<RealtimeGameEvent>(message))
    })
  }

  private unsubscribeRoom(): void {
    this.roomSubscription?.unsubscribe()
    this.roomSubscription = null
  }

  private unsubscribeGame(): void {
    this.gameSubscription?.unsubscribe()
    this.gameStateSubscription?.unsubscribe()
    this.gameSubscription = null
    this.gameStateSubscription = null
  }

  private async recoverCurrentContext(): Promise<void> {
    if (this.gameId !== null && this.options.onGameReconnect) {
      await this.options.onGameReconnect(this.gameId)
      return
    }
    if (this.roomId !== null && this.options.onRoomReconnect) {
      await this.options.onRoomReconnect(this.roomId)
    }
    if (this.options.onLobbyReconnect) await this.options.onLobbyReconnect()
  }

  private publish(destination: string, body: object): void {
    if (!this.client?.connected) throw new Error('실시간 연결이 필요합니다.')
    this.client.publish({ destination, body: JSON.stringify(body), headers: { 'content-type': 'application/json' } })
  }

  private parse<T>(message: IMessage): T {
    return JSON.parse(message.body) as T
  }
}

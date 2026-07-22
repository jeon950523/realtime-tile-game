import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'

import { refreshAccessToken } from '@/api/httpClient'
import { resolveWebSocketUrl } from '@/config/runtimeEndpoints'
import type {
  CommitTurnCommand,
  ExitActiveGameCommand,
  GameCommandReply,
  GameTurnCommand,
  RealtimeGameEvent,
  TurnPreviewCancelCommand,
  TurnPreviewCommand,
} from '@/types/game'
import type { RealtimeRoomEvent, RoomCommandReply, RoomConnectionState } from '@/types/room'

interface AuthenticatedStompClientOptions {
  getAccessToken: () => string | null
  onStateChange: (state: RoomConnectionState, message?: string | null) => void
  onLobbyEvent?: (event: RealtimeRoomEvent) => void
  onRoomEvent?: (event: RealtimeRoomEvent) => void
  onReply?: (reply: RoomCommandReply) => void
  onGameReply?: (reply: GameCommandReply) => void
  onGameEvent?: (event: RealtimeGameEvent) => void
  onGameState?: (event: RealtimeGameEvent) => void
  onGameSubscriptionsReady?: (gameId: number, ready: boolean) => void
  onGameRecoveryStateChange?: (gameId: number, recoveryInProgress: boolean) => void
  onCommandTransportInterrupted?: (message: string) => void
  onLobbyReconnect?: () => Promise<void> | void
  onRoomReconnect?: (roomId: number) => Promise<void> | void
  onGameReconnect?: (gameId: number) => Promise<boolean | void> | boolean | void
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
  private gameRecoveryInProgress = false
  private gameCommandsEnabled = false
  private contextRevision = 0
  private recoveryAttempt = 0

  constructor(private readonly options: AuthenticatedStompClientOptions) {}

  connectLobby(): Promise<void> {
    this.notifyGameSubscriptionsReady(false)
    if (this.roomId !== null || this.gameId !== null) this.invalidateRecoveryContext()
    this.roomId = null
    this.gameId = null
    this.unsubscribeRoom()
    this.unsubscribeGame()
    return this.ensureConnected()
  }

  async connectRoom(roomId: number): Promise<void> {
    if (this.roomId !== roomId || this.gameId !== null) {
      this.invalidateRecoveryContext()
      this.unsubscribeRoom()
    }
    this.notifyGameSubscriptionsReady(false)
    this.roomId = roomId
    this.gameId = null
    this.unsubscribeGame()
    await this.ensureConnected()
    this.subscribeRoom()
  }

  async connectGame(gameId: number): Promise<void> {
    if (this.gameId !== gameId || this.roomId !== null) {
      this.notifyGameSubscriptionsReady(false)
      this.invalidateRecoveryContext()
      this.unsubscribeGame()
    }
    this.roomId = null
    this.gameId = gameId
    this.unsubscribeRoom()
    await this.ensureConnected()
    this.subscribeGame()
    this.subscribeGameState()
    if (!this.gameRecoveryInProgress) this.gameCommandsEnabled = true
    this.notifyGameSubscriptionsReady(true)
    if (!this.isGameCommandReady(gameId)) {
      throw new Error('게임 명령 구독을 준비하지 못했습니다.')
    }
  }

  async disconnect(): Promise<void> {
    this.notifyGameSubscriptionsReady(false)
    this.invalidateRecoveryContext()
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

  publishDraw(gameId: number, command: GameTurnCommand): void {
    this.requireGameCommandReady(gameId)
    this.publish(`/app/games/${gameId}/turn/draw`, command)
  }

  publishPass(gameId: number, command: GameTurnCommand): void {
    this.requireGameCommandReady(gameId)
    this.publish(`/app/games/${gameId}/turn/pass`, command)
  }

  publishCommit(gameId: number, command: CommitTurnCommand): void {
    this.requireGameCommandReady(gameId)
    this.publish(`/app/games/${gameId}/turn/commit`, command)
  }

  publishExitActiveGame(gameId: number, command: ExitActiveGameCommand): void {
    this.requireGameCommandReady(gameId)
    this.publish(`/app/games/${gameId}/exit`, command)
  }

  publishTurnPreview(gameId: number, command: TurnPreviewCommand): void {
    this.requireGameCommandReady(gameId)
    this.publish(`/app/games/${gameId}/turn/preview`, command)
  }

  publishTurnPreviewCancel(gameId: number, command: TurnPreviewCancelCommand): void {
    this.requireGameCommandReady(gameId)
    this.publish(`/app/games/${gameId}/turn/preview/cancel`, command)
  }

  isGameCommandReady(gameId: number): boolean {
    return Boolean(
      this.client?.connected
      && !this.gameRecoveryInProgress
      && this.gameCommandsEnabled
      && this.gameId === gameId
      && this.replySubscription
      && this.gameSubscription
      && this.gameStateSubscription,
    )
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
        brokerURL: resolveWebSocketUrl(),
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
          const recoveryAttempt = ++this.recoveryAttempt
          const contextRevision = this.contextRevision
          const recoveryGameId = this.gameId
          const recoveryRoomId = this.roomId
          this.subscribeConfiguredDestinations()
          if (!reconnect) {
            this.setGameRecoveryInProgress(false)
            this.gameCommandsEnabled = true
            this.options.onStateChange('CONNECTED')
            this.notifyGameSubscriptionsReady(true)
            this.connecting = null
            resolve()
            return
          }

          this.setGameRecoveryInProgress(recoveryGameId !== null)
          this.options.onStateChange('CONNECTING')
          this.notifyGameSubscriptionsReady(false)
          try {
            const recovered = await this.recoverCurrentContext()
            if (!this.isCurrentRecovery(recoveryAttempt, contextRevision, recoveryGameId, recoveryRoomId)) return
            if (!recovered) throw new Error('최신 게임 상태를 복구하지 못했습니다.')
            this.setGameRecoveryInProgress(false)
            this.gameCommandsEnabled = true
            this.options.onStateChange('CONNECTED')
            this.notifyGameSubscriptionsReady(true)
          } catch {
            if (!this.isCurrentRecovery(recoveryAttempt, contextRevision, recoveryGameId, recoveryRoomId)) return
            this.setGameRecoveryInProgress(false)
            this.notifyGameSubscriptionsReady(false)
            this.options.onStateChange('FAILED', '최신 게임 상태를 복구하지 못했습니다.')
          } finally {
            if (this.recoveryAttempt === recoveryAttempt) {
              this.connecting = null
              resolve()
            }
          }
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
          this.gameCommandsEnabled = false
          this.setGameRecoveryInProgress(false)
          this.notifyGameSubscriptionsReady(false)
          this.options.onCommandTransportInterrupted?.('STOMP ERROR로 게임 명령 응답을 확인할 수 없습니다.')
          this.connecting = null
          reject(new Error(message))
        },
        onWebSocketError: () => {
          this.options.onStateChange('FAILED', 'WebSocket 연결에 실패했습니다.')
          this.gameCommandsEnabled = false
          this.setGameRecoveryInProgress(false)
          this.notifyGameSubscriptionsReady(false)
          this.options.onCommandTransportInterrupted?.('WebSocket 오류로 게임 명령 응답을 확인할 수 없습니다.')
        },
        onWebSocketClose: () => {
          this.lobbySubscription = null
          this.roomSubscription = null
          this.replySubscription = null
          this.gameSubscription = null
          this.gameStateSubscription = null
          this.gameCommandsEnabled = false
          this.setGameRecoveryInProgress(this.gameId !== null)
          this.notifyGameSubscriptionsReady(false)
          this.options.onCommandTransportInterrupted?.('WebSocket 연결이 종료되어 게임 상태를 다시 확인합니다.')
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
    if ((!this.options.onReply && !this.options.onGameReply) || !this.client?.connected || this.replySubscription) return
    this.replySubscription = this.client.subscribe('/user/queue/replies', (message) => {
      const reply = this.parse<RoomCommandReply | GameCommandReply>(message)
      if (this.isGameCommandReply(reply)) this.options.onGameReply?.(reply)
      else this.options.onReply?.(reply)
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

  private notifyGameSubscriptionsReady(ready: boolean): void {
    if (this.gameId === null) return
    this.options.onGameSubscriptionsReady?.(
      this.gameId,
      ready && !this.gameRecoveryInProgress && this.isGameCommandReady(this.gameId),
    )
  }

  private requireGameCommandReady(gameId: number): void {
    if (!this.isGameCommandReady(gameId)) {
      throw new Error('게임 명령 구독이 아직 준비되지 않았습니다.')
    }
  }

  private async recoverCurrentContext(): Promise<boolean> {
    if (this.gameId !== null && this.options.onGameReconnect) {
      return (await this.options.onGameReconnect(this.gameId)) !== false
    }
    if (this.roomId !== null && this.options.onRoomReconnect) {
      await this.options.onRoomReconnect(this.roomId)
      return true
    }
    if (this.options.onLobbyReconnect) await this.options.onLobbyReconnect()
    return true
  }

  private setGameRecoveryInProgress(recoveryInProgress: boolean): void {
    if (recoveryInProgress) this.gameCommandsEnabled = false
    if (this.gameRecoveryInProgress === recoveryInProgress) return
    this.gameRecoveryInProgress = recoveryInProgress
    if (this.gameId !== null) {
      this.options.onGameRecoveryStateChange?.(this.gameId, recoveryInProgress)
    }
  }

  private invalidateRecoveryContext(): void {
    this.contextRevision += 1
    this.recoveryAttempt += 1
    this.gameCommandsEnabled = false
    this.setGameRecoveryInProgress(false)
  }

  private isCurrentRecovery(
    recoveryAttempt: number,
    contextRevision: number,
    gameId: number | null,
    roomId: number | null,
  ): boolean {
    return this.recoveryAttempt === recoveryAttempt
      && this.contextRevision === contextRevision
      && this.gameId === gameId
      && this.roomId === roomId
      && Boolean(this.client?.connected)
  }

  private publish(destination: string, body: object): void {
    if (!this.client?.connected) throw new Error('실시간 연결이 필요합니다.')
    this.client.publish({ destination, body: JSON.stringify(body), headers: { 'content-type': 'application/json' } })
  }

  private isGameCommandReply(reply: RoomCommandReply | GameCommandReply): reply is GameCommandReply {
    return 'accepted' in reply && typeof reply.accepted === 'boolean' && 'actionType' in reply
  }

  private parse<T>(message: IMessage): T {
    return JSON.parse(message.body) as T
  }
}

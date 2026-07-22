import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'

import { resolveWebSocketUrl } from '@/config/runtimeEndpoints'
import type { RealtimeHealthMessage } from '@/types/health'

export type RealtimeConnectionState = 'DISCONNECTED' | 'CONNECTING' | 'CONNECTED' | 'FAILED'

interface SystemHealthClientCallbacks {
  onStateChange: (state: RealtimeConnectionState) => void
  onMessage: (message: RealtimeHealthMessage) => void
  onError: (message: string) => void
}

export class SystemHealthClient {
  private readonly client: Client
  private readonly callbacks: SystemHealthClientCallbacks
  private subscription: StompSubscription | null = null
  private reconnectPromise: Promise<void> | null = null
  private manualRestart = false
  private connectionFailed = false
  private connected = false

  constructor(callbacks: SystemHealthClientCallbacks) {
    this.callbacks = callbacks
    this.client = new Client({
      brokerURL: resolveWebSocketUrl(),
      reconnectDelay: 5_000,
      heartbeatIncoming: 10_000,
      heartbeatOutgoing: 10_000,
      connectionTimeout: 5_000,
      debug: import.meta.env.DEV ? (message) => console.debug('[STOMP]', message) : () => undefined,
      beforeConnect: () => {
        this.connectionFailed = false
        this.connected = false
        this.callbacks.onStateChange('CONNECTING')
      },
      onConnect: () => {
        this.connectionFailed = false
        this.connected = true
        this.replaceSubscription()
        this.callbacks.onStateChange('CONNECTED')
        this.client.publish({ destination: '/app/system.health.ping' })
      },
      onWebSocketClose: () => {
        this.clearSubscription()

        if (this.manualRestart || this.connectionFailed) {
          this.connected = false
          return
        }

        if (this.client.active && !this.connected) {
          this.failConnection('WebSocket 연결이 완료되기 전에 종료되었습니다.')
          return
        }

        this.connected = false
        this.callbacks.onStateChange(this.client.active ? 'CONNECTING' : 'DISCONNECTED')
      },
      onWebSocketError: () => {
        this.failConnection('WebSocket 연결에 실패했습니다.')
      },
      onStompError: (frame) => {
        this.failConnection(frame.headers.message ?? 'STOMP 처리 중 오류가 발생했습니다.')
      },
    })
  }

  connect(): void {
    if (this.client.active || this.reconnectPromise) {
      return
    }

    this.callbacks.onStateChange('CONNECTING')
    this.client.activate()
  }

  reconnect(): Promise<void> {
    if (this.reconnectPromise) {
      return this.reconnectPromise
    }

    const reconnectOperation = this.performReconnect()
    this.reconnectPromise = reconnectOperation.finally(() => {
      this.reconnectPromise = null
    })

    return this.reconnectPromise
  }

  async disconnect(): Promise<void> {
    if (this.reconnectPromise) {
      try {
        await this.reconnectPromise
      } catch {
        // 연결 실패는 이미 callback으로 전달됐으므로 종료 정리는 계속한다.
      }
    }

    this.manualRestart = true
    this.clearSubscription()

    try {
      await this.client.deactivate()
      this.callbacks.onStateChange('DISCONNECTED')
    } finally {
      this.manualRestart = false
    }
  }

  private async performReconnect(): Promise<void> {
    this.callbacks.onStateChange('CONNECTING')
    this.clearSubscription()
    this.manualRestart = true

    try {
      if (this.client.active) {
        await this.client.deactivate()
      }

      this.connectionFailed = false
      this.connected = false
      this.client.activate()
    } catch (error) {
      const message = error instanceof Error ? error.message : 'WebSocket 재연결에 실패했습니다.'
      this.failConnection(message)
      throw error
    } finally {
      this.manualRestart = false
    }
  }

  private replaceSubscription(): void {
    this.clearSubscription()
    this.subscription = this.client.subscribe('/topic/system.health', (frame) => {
      this.callbacks.onMessage(this.parseMessage(frame))
    })
  }

  private clearSubscription(): void {
    this.subscription?.unsubscribe()
    this.subscription = null
  }

  private failConnection(message: string): void {
    this.connectionFailed = true
    this.callbacks.onStateChange('FAILED')
    this.callbacks.onError(message)
  }

  private parseMessage(frame: IMessage): RealtimeHealthMessage {
    return JSON.parse(frame.body) as RealtimeHealthMessage
  }
}

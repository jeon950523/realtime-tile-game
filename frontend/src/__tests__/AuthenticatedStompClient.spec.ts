import { beforeEach, describe, expect, it, vi } from 'vitest'

const stompHarness = vi.hoisted(() => ({
  instances: [] as Array<{
    config: Record<string, any>
    active: boolean
    connected: boolean
    connectHeaders: Record<string, string>
    subscriptions: Array<{ destination: string; active: boolean; callback: (message: { body: string }) => void }>
    published: Array<{ destination: string; body: string; headers?: Record<string, string> }>
    activate: () => void
    deactivate: () => Promise<void>
    transportClose: () => void
    subscribe: (destination: string, callback: (message: { body: string }) => void) => { unsubscribe: () => void }
    publish: (frame: { destination: string; body: string; headers?: Record<string, string> }) => void
  }>,
}))

vi.mock('@stomp/stompjs', () => {
  class Client {
    config: Record<string, any>
    active = false
    connected = false
    connectHeaders: Record<string, string> = {}
    subscriptions: Array<{ destination: string; active: boolean; callback: (message: { body: string }) => void }> = []
    published: Array<{ destination: string; body: string; headers?: Record<string, string> }> = []

    constructor(config: Record<string, any>) {
      this.config = config
      stompHarness.instances.push(this as never)
    }

    activate(): void {
      this.active = true
      void Promise.resolve(this.config.beforeConnect?.()).then(() => {
        this.connected = true
        void this.config.onConnect?.()
      })
    }

    async deactivate(): Promise<void> {
      this.active = false
      this.transportClose()
    }

    transportClose(): void {
      this.connected = false
      this.subscriptions.forEach((subscription) => { subscription.active = false })
      this.config.onWebSocketClose?.()
    }

    subscribe(destination: string, callback: (message: { body: string }) => void): { unsubscribe: () => void } {
      const subscription = { destination, active: true, callback }
      this.subscriptions.push(subscription)
      return { unsubscribe: () => { subscription.active = false } }
    }

    publish(frame: { destination: string; body: string; headers?: Record<string, string> }): void {
      this.published.push(frame)
    }
  }
  return { Client }
})

const httpHarness = vi.hoisted(() => ({ refreshAccessToken: vi.fn() }))
vi.mock('@/api/httpClient', () => ({ refreshAccessToken: httpHarness.refreshAccessToken }))

import { AuthenticatedStompClient } from '@/realtime/authenticatedStompClient'

function createClient(token: string | null = 'access-token') {
  const states: string[] = []
  const onReconnect = vi.fn()
  const client = new AuthenticatedStompClient({
    getAccessToken: () => token,
    onStateChange: (state) => states.push(state),
    onLobbyEvent: vi.fn(),
    onRoomEvent: vi.fn(),
    onReply: vi.fn(),
    onLobbyReconnect: onReconnect,
  })
  return { client, states, onReconnect }
}

beforeEach(() => {
  stompHarness.instances.length = 0
  httpHarness.refreshAccessToken.mockReset()
  vi.stubEnv('VITE_WS_URL', 'ws://localhost:8080/ws')
})

describe('authenticated STOMP client', () => {
  it('sends the memory access token only in the CONNECT header', async () => {
    const { client, states } = createClient()

    await client.connectLobby()

    const stomp = stompHarness.instances[0]!
    expect(stomp.config.brokerURL).toBe('ws://localhost:8080/ws')
    expect(stomp.config.brokerURL).not.toContain('?')
    expect(stomp.connectHeaders).toEqual({ Authorization: 'Bearer access-token' })
    expect(stomp.subscriptions.filter((value) => value.active).map((value) => value.destination)).toEqual([
      '/topic/lobby/rooms',
      '/user/queue/replies',
    ])
    expect(states.at(-1)).toBe('CONNECTED')
  })

  it('reuses a single lobby and reply subscription while already connected', async () => {
    const { client } = createClient()

    await client.connectLobby()
    await client.connectLobby()

    const active = stompHarness.instances[0]!.subscriptions.filter((value) => value.active)
    expect(active.filter((value) => value.destination === '/topic/lobby/rooms')).toHaveLength(1)
    expect(active.filter((value) => value.destination === '/user/queue/replies')).toHaveLength(1)
  })

  it('subscribes to one room at a time and publishes commands without a client user id', async () => {
    const { client } = createClient()

    await client.connectRoom(10)
    client.publishReady(10, 'action-1', true)
    await client.connectRoom(20)

    const stomp = stompHarness.instances[0]!
    const activeRoomSubscriptions = stomp.subscriptions.filter((value) => value.active && value.destination.startsWith('/topic/rooms/'))
    expect(activeRoomSubscriptions.map((value) => value.destination)).toEqual(['/topic/rooms/20'])
    expect(JSON.parse(stomp.published[0]!.body)).toEqual({ actionId: 'action-1', ready: true })
    expect(stomp.published[0]!.body).not.toContain('userId')
  })

  it('refreshes once before connecting when no memory token exists', async () => {
    httpHarness.refreshAccessToken.mockResolvedValue({ accessToken: 'refreshed-token', expiresIn: 1800 })
    const { client } = createClient(null)

    await client.connectLobby()

    expect(httpHarness.refreshAccessToken).toHaveBeenCalledTimes(1)
    expect(stompHarness.instances[0]!.connectHeaders.Authorization).toBe('Bearer refreshed-token')
  })

  it('transportReconnectLeavesOneActiveLobbySubscription', async () => {
    const { client, onReconnect } = createClient()
    await client.connectLobby()
    const stomp = stompHarness.instances[0]!

    stomp.transportClose()
    stomp.connected = true
    await stomp.config.onConnect()

    expect(onReconnect).toHaveBeenCalledTimes(1)
    const active = stomp.subscriptions.filter((value) => value.active)
    expect(active.filter((value) => value.destination === '/topic/lobby/rooms')).toHaveLength(1)
  })

  it('transportReconnectLeavesOneActiveReplySubscription', async () => {
    const { client } = createClient()
    await client.connectLobby()
    const stomp = stompHarness.instances[0]!

    stomp.transportClose()
    stomp.connected = true
    await stomp.config.onConnect()

    const active = stomp.subscriptions.filter((value) => value.active)
    expect(active.filter((value) => value.destination === '/user/queue/replies')).toHaveLength(1)
  })

  it('subscribes to one game topic and one private game-state queue', async () => {
    const client = new AuthenticatedStompClient({
      getAccessToken: () => 'access-token',
      onStateChange: vi.fn(),
      onGameEvent: vi.fn(),
      onGameState: vi.fn(),
    })

    await client.connectGame(33)
    await client.connectGame(33)

    const active = stompHarness.instances[0]!.subscriptions.filter((value) => value.active)
    expect(active.filter((value) => value.destination === '/topic/games/33')).toHaveLength(1)
    expect(active.filter((value) => value.destination === '/user/queue/game-state')).toHaveLength(1)
  })

  it('gameReconnectLeavesOneActiveGameSubscription', async () => {
    const reconnect = vi.fn(async () => undefined)
    const client = new AuthenticatedStompClient({
      getAccessToken: () => 'access-token',
      onStateChange: vi.fn(),
      onGameEvent: vi.fn(),
      onGameState: vi.fn(),
      onGameReconnect: reconnect,
    })
    await client.connectGame(33)
    const stomp = stompHarness.instances[0]!

    stomp.transportClose()
    stomp.connected = true
    await stomp.config.onConnect()

    expect(reconnect).toHaveBeenCalledWith(33)
    const active = stomp.subscriptions.filter((value) => value.active)
    expect(active.filter((value) => value.destination === '/topic/games/33')).toHaveLength(1)
    expect(active.filter((value) => value.destination === '/user/queue/game-state')).toHaveLength(1)
  })

  it('roomReconnectLeavesOneActiveRoomSubscription', async () => {
    const { client } = createClient()
    await client.connectRoom(10)
    const stomp = stompHarness.instances[0]!

    stomp.transportClose()
    stomp.connected = true
    await stomp.config.onConnect()

    const active = stomp.subscriptions.filter((value) => value.active)
    expect(active.filter((value) => value.destination === '/topic/rooms/10')).toHaveLength(1)
  })
})

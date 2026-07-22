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

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void
  let reject!: (reason?: unknown) => void
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise
    reject = rejectPromise
  })
  return { promise, resolve, reject }
}

function createRecoveringGameClient(onGameReconnect: (gameId: number) => Promise<boolean | void>) {
  const states: string[] = []
  const readiness: Array<[number, boolean]> = []
  const recoveryStates: Array<[number, boolean]> = []
  const client = new AuthenticatedStompClient({
    getAccessToken: () => 'access-token',
    onStateChange: (state) => states.push(state),
    onGameReply: vi.fn(),
    onGameEvent: vi.fn(),
    onGameState: vi.fn(),
    onGameReconnect,
    onGameSubscriptionsReady: (gameId, ready) => readiness.push([gameId, ready]),
    onGameRecoveryStateChange: (gameId, inProgress) => recoveryStates.push([gameId, inProgress]),
  })
  return { client, states, readiness, recoveryStates }
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
      onGameReply: vi.fn(),
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
      onGameReply: vi.fn(),
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

  it('publishes draw, pass, and active-game exit through the existing game command transport', async () => {
    const client = new AuthenticatedStompClient({
      getAccessToken: () => 'access-token',
      onStateChange: vi.fn(),
      onGameReply: vi.fn(),
      onGameEvent: vi.fn(),
      onGameState: vi.fn(),
    })
    await client.connectGame(33)

    client.publishDraw(33, {
      actionId: '11111111-1111-4111-8111-111111111111',
      gameVersion: 4,
    })
    client.publishPass(33, {
      actionId: '22222222-2222-4222-8222-222222222222',
      gameVersion: 5,
    })
    client.publishExitActiveGame(33, {
      actionId: '33333333-3333-4333-8333-333333333333',
      gameVersion: 6,
      roomId: 10,
    })

    const published = stompHarness.instances[0]!.published
    expect(published.map((frame) => frame.destination)).toEqual([
      '/app/games/33/turn/draw',
      '/app/games/33/turn/pass',
      '/app/games/33/exit',
    ])
    expect(JSON.parse(published[0]!.body)).toEqual({
      actionId: '11111111-1111-4111-8111-111111111111',
      gameVersion: 4,
    })
    expect(JSON.parse(published[1]!.body)).toEqual({
      actionId: '22222222-2222-4222-8222-222222222222',
      gameVersion: 5,
    })
    expect(JSON.parse(published[2]!.body)).toEqual({
      actionId: '33333333-3333-4333-8333-333333333333',
      gameVersion: 6,
      roomId: 10,
    })
    expect(published[0]!.body).not.toContain('userId')
    expect(published[0]!.body).not.toContain('gameId')
  })

  it('routes a game command reply through the existing single reply subscription', async () => {
    const onRoomReply = vi.fn()
    const onGameReply = vi.fn()
    const client = new AuthenticatedStompClient({
      getAccessToken: () => 'access-token',
      onStateChange: vi.fn(),
      onReply: onRoomReply,
      onGameReply,
      onGameEvent: vi.fn(),
      onGameState: vi.fn(),
    })
    await client.connectGame(33)
    const stomp = stompHarness.instances[0]!
    const replySubscription = stomp.subscriptions.find(
      (subscription) => subscription.active && subscription.destination === '/user/queue/replies',
    )!

    replySubscription.callback({
      body: JSON.stringify({
        eventType: 'GAME_COMMAND_ACCEPTED',
        actionId: '11111111-1111-4111-8111-111111111111',
        accepted: true,
        duplicate: false,
        code: null,
        message: '게임 행동을 처리했습니다.',
        gameId: 33,
        actionType: 'DRAW',
        gameVersion: 1,
      }),
    })

    expect(onGameReply).toHaveBeenCalledTimes(1)
    expect(onRoomReply).not.toHaveBeenCalled()
    expect(stomp.subscriptions.filter(
      (subscription) => subscription.active && subscription.destination === '/user/queue/replies',
    )).toHaveLength(1)
  })

  it('reports game command readiness only after all required subscriptions exist', async () => {
    const readiness: Array<[number, boolean]> = []
    const client = new AuthenticatedStompClient({
      getAccessToken: () => 'access-token',
      onStateChange: vi.fn(),
      onGameReply: vi.fn(),
      onGameEvent: vi.fn(),
      onGameState: vi.fn(),
      onGameSubscriptionsReady: (gameId, ready) => readiness.push([gameId, ready]),
    })

    await client.connectGame(33)
    expect(client.isGameCommandReady(33)).toBe(true)
    expect(readiness.at(-1)).toEqual([33, true])

    await client.connectLobby()
    expect(client.isGameCommandReady(33)).toBe(false)
    expect(readiness.at(-1)).toEqual([33, false])
  })

  it('notifies Pending recovery when the game WebSocket closes', async () => {
    const interrupted = vi.fn()
    const client = new AuthenticatedStompClient({
      getAccessToken: () => 'access-token',
      onStateChange: vi.fn(),
      onGameReply: vi.fn(),
      onGameEvent: vi.fn(),
      onGameState: vi.fn(),
      onCommandTransportInterrupted: interrupted,
    })
    await client.connectGame(33)

    stompHarness.instances[0]!.transportClose()

    expect(interrupted).toHaveBeenCalledTimes(1)
    expect(client.isGameCommandReady(33)).toBe(false)
  })

})

describe('Phase 7 final reconnect readiness ordering', () => {
  it('RT-P7-FINAL-001 enables initial readiness only after required subscriptions exist', async () => {
    const context = createRecoveringGameClient(vi.fn(async () => true))

    await context.client.connectGame(33)

    const active = stompHarness.instances[0]!.subscriptions.filter((subscription) => subscription.active)
    expect(active.filter((subscription) => subscription.destination === '/user/queue/replies')).toHaveLength(1)
    expect(active.filter((subscription) => subscription.destination === '/topic/games/33')).toHaveLength(1)
    expect(active.filter((subscription) => subscription.destination === '/user/queue/game-state')).toHaveLength(1)
    expect(context.readiness.at(-1)).toEqual([33, true])
    expect(context.client.isGameCommandReady(33)).toBe(true)
  })

  it('RT-P7-FINAL-002 keeps readiness false until reconnect recovery resolves', async () => {
    const recovery = deferred<boolean>()
    const context = createRecoveringGameClient(vi.fn(() => recovery.promise))
    await context.client.connectGame(33)
    const stomp = stompHarness.instances[0]!

    stomp.transportClose()
    stomp.connected = true
    const reconnect = stomp.config.onConnect()
    await Promise.resolve()

    expect(context.recoveryStates.at(-1)).toEqual([33, true])
    expect(context.readiness.at(-1)).toEqual([33, false])
    expect(context.client.isGameCommandReady(33)).toBe(false)

    recovery.resolve(true)
    await reconnect
  })

  it('RT-P7-FINAL-003 exposes neither CONNECTED nor command publishing during recovery', async () => {
    const recovery = deferred<boolean>()
    const context = createRecoveringGameClient(vi.fn(() => recovery.promise))
    await context.client.connectGame(33)
    const stomp = stompHarness.instances[0]!

    stomp.transportClose()
    stomp.connected = true
    const reconnect = stomp.config.onConnect()
    await Promise.resolve()

    expect(context.states.at(-1)).toBe('CONNECTING')
    expect(() => context.client.publishDraw(33, { actionId: 'action-1', gameVersion: 1 })).toThrow(
      '게임 명령 구독이 아직 준비되지 않았습니다.',
    )
    expect(stomp.published).toHaveLength(0)

    recovery.resolve(true)
    await reconnect
  })

  it('RT-P7-FINAL-004 enables readiness and the first command only after recovery succeeds', async () => {
    const recovery = deferred<boolean>()
    const context = createRecoveringGameClient(vi.fn(() => recovery.promise))
    await context.client.connectGame(33)
    const stomp = stompHarness.instances[0]!
    stomp.transportClose()
    stomp.connected = true
    const reconnect = stomp.config.onConnect()
    await Promise.resolve()

    recovery.resolve(true)
    await reconnect
    context.client.publishDraw(33, { actionId: 'action-1', gameVersion: 2 })

    expect(context.recoveryStates.at(-1)).toEqual([33, false])
    expect(context.states.at(-1)).toBe('CONNECTED')
    expect(context.readiness.at(-1)).toEqual([33, true])
    expect(stomp.published).toHaveLength(1)
  })

  it('RT-P7-FINAL-005 keeps readiness false when recovery fails', async () => {
    const recovery = deferred<boolean>()
    const context = createRecoveringGameClient(vi.fn(() => recovery.promise))
    await context.client.connectGame(33)
    const stomp = stompHarness.instances[0]!
    stomp.transportClose()
    stomp.connected = true
    const reconnect = stomp.config.onConnect()
    await Promise.resolve()

    recovery.reject(new Error('REST unavailable'))
    await reconnect

    expect(context.recoveryStates.at(-1)).toEqual([33, false])
    expect(context.states.at(-1)).toBe('FAILED')
    expect(context.readiness.at(-1)).toEqual([33, false])
    expect(context.client.isGameCommandReady(33)).toBe(false)
  })

  it('RT-P7-FINAL-006 ignores an old recovery after the active game changes', async () => {
    const recovery = deferred<boolean>()
    const context = createRecoveringGameClient(vi.fn(() => recovery.promise))
    await context.client.connectGame(33)
    const stomp = stompHarness.instances[0]!
    stomp.transportClose()
    stomp.connected = true
    const reconnect = stomp.config.onConnect()
    await Promise.resolve()
    const eventsBeforeGameChange = context.readiness.length

    await context.client.connectGame(44)
    recovery.resolve(true)
    await reconnect

    expect(context.readiness.slice(eventsBeforeGameChange)).not.toContainEqual([33, true])
    expect(context.client.isGameCommandReady(33)).toBe(false)
    expect(context.client.isGameCommandReady(44)).toBe(true)
  })

  it('RT-P7-FINAL-007 leaves one active subscription for every required destination', async () => {
    const context = createRecoveringGameClient(vi.fn(async () => true))
    await context.client.connectGame(33)
    const stomp = stompHarness.instances[0]!
    stomp.transportClose()
    stomp.connected = true

    await stomp.config.onConnect()

    const active = stomp.subscriptions.filter((subscription) => subscription.active)
    expect(active.filter((subscription) => subscription.destination === '/user/queue/replies')).toHaveLength(1)
    expect(active.filter((subscription) => subscription.destination === '/topic/games/33')).toHaveLength(1)
    expect(active.filter((subscription) => subscription.destination === '/user/queue/game-state')).toHaveLength(1)
  })
})

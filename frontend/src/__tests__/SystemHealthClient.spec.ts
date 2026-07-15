import { beforeEach, describe, expect, it, vi } from 'vitest'

import { SystemHealthClient } from '@/realtime/systemHealthClient'

const stompMock = vi.hoisted(() => {
  type FakeConfig = {
    beforeConnect?: () => void | Promise<void>
    onConnect?: () => void
    onWebSocketClose?: () => void
    onWebSocketError?: () => void
    onStompError?: (frame: { headers: { message?: string } }) => void
  }

  let config: FakeConfig = {}
  const subscription = {
    unsubscribe: vi.fn(),
  }
  const client = {
    active: false,
    activate: vi.fn(() => {
      client.active = true
      void config.beforeConnect?.()
    }),
    deactivate: vi.fn(async () => {
      client.active = false
      config.onWebSocketClose?.()
    }),
    subscribe: vi.fn(() => subscription),
    publish: vi.fn(),
  }

  return {
    client,
    subscription,
    getConfig: () => config,
    setConfig: (value: FakeConfig) => {
      config = value
    },
  }
})

vi.mock('@stomp/stompjs', () => ({
  Client: class {
    constructor(config: object) {
      stompMock.setConfig(config)
      return stompMock.client
    }
  },
}))

function createCallbacks() {
  return {
    onStateChange: vi.fn(),
    onMessage: vi.fn(),
    onError: vi.fn(),
  }
}

describe('SystemHealthClient', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    stompMock.client.active = false
    stompMock.client.deactivate.mockImplementation(async () => {
      stompMock.client.active = false
      stompMock.getConfig().onWebSocketClose?.()
    })
  })

  it('marks the initial connection as connected only after STOMP onConnect', () => {
    const callbacks = createCallbacks()
    const client = new SystemHealthClient(callbacks)

    client.connect()

    expect(callbacks.onStateChange).toHaveBeenLastCalledWith('CONNECTING')
    expect(callbacks.onStateChange).not.toHaveBeenCalledWith('CONNECTED')

    stompMock.getConfig().onConnect?.()

    expect(callbacks.onStateChange).toHaveBeenLastCalledWith('CONNECTED')
    expect(stompMock.client.subscribe).toHaveBeenCalledTimes(1)
    expect(stompMock.client.publish).toHaveBeenCalledTimes(1)
  })

  it('deactivates the active client before activating a manual reconnect', async () => {
    const callbacks = createCallbacks()
    const client = new SystemHealthClient(callbacks)

    client.connect()
    stompMock.getConfig().onConnect?.()
    await client.reconnect()

    expect(stompMock.subscription.unsubscribe).toHaveBeenCalledTimes(1)
    expect(stompMock.client.deactivate).toHaveBeenCalledTimes(1)
    expect(stompMock.client.deactivate.mock.invocationCallOrder[0]).toBeLessThan(
      stompMock.client.activate.mock.invocationCallOrder.at(-1) ?? Number.MAX_SAFE_INTEGER,
    )
    expect(callbacks.onStateChange).toHaveBeenLastCalledWith('CONNECTING')
  })

  it('keeps exactly one active subscription across three reconnects', async () => {
    const callbacks = createCallbacks()
    const client = new SystemHealthClient(callbacks)

    client.connect()
    stompMock.getConfig().onConnect?.()

    for (let attempt = 0; attempt < 3; attempt += 1) {
      await client.reconnect()
      stompMock.getConfig().onConnect?.()
    }

    expect(stompMock.client.subscribe).toHaveBeenCalledTimes(4)
    expect(stompMock.subscription.unsubscribe).toHaveBeenCalledTimes(3)
    expect(stompMock.client.publish).toHaveBeenCalledTimes(4)
  })

  it('deduplicates overlapping manual reconnect requests', async () => {
    let finishDeactivate: (() => void) | undefined
    stompMock.client.active = true
    stompMock.client.deactivate.mockImplementationOnce(
      () =>
        new Promise<void>((resolve) => {
          finishDeactivate = () => {
            stompMock.client.active = false
            stompMock.getConfig().onWebSocketClose?.()
            resolve()
          }
        }),
    )

    const callbacks = createCallbacks()
    const client = new SystemHealthClient(callbacks)
    stompMock.client.active = true

    const firstReconnect = client.reconnect()
    const repeatedReconnect = client.reconnect()

    expect(repeatedReconnect).toBe(firstReconnect)
    expect(stompMock.client.deactivate).toHaveBeenCalledTimes(1)

    finishDeactivate?.()
    await Promise.all([firstReconnect, repeatedReconnect])

    expect(stompMock.client.activate).toHaveBeenCalledTimes(1)
  })

  it('reports a failed state and message on WebSocket failure', () => {
    const callbacks = createCallbacks()
    new SystemHealthClient(callbacks)

    stompMock.getConfig().onWebSocketError?.()

    expect(callbacks.onStateChange).toHaveBeenLastCalledWith('FAILED')
    expect(callbacks.onError).toHaveBeenCalledWith('WebSocket 연결에 실패했습니다.')
  })
})

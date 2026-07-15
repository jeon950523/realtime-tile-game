import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { fetchHealth } from '@/api/healthApi'
import { useConnectionStore } from '@/stores/connection'

const realtimeMocks = vi.hoisted(() => ({
  connect: vi.fn(),
  reconnect: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
  disconnect: vi.fn<() => Promise<void>>().mockResolvedValue(undefined),
}))

vi.mock('@/api/healthApi', () => ({
  fetchHealth: vi.fn(),
}))

vi.mock('@/realtime/systemHealthClient', () => ({
  SystemHealthClient: class {
    connect = realtimeMocks.connect
    reconnect = realtimeMocks.reconnect
    disconnect = realtimeMocks.disconnect
  },
}))

const mockedFetchHealth = vi.mocked(fetchHealth)

function successfulHealthResponse() {
  return {
    application: 'realtime-tile-game-backend',
    status: 'UP' as const,
    database: 'UP' as const,
  }
}

describe('connection store', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.clearAllMocks()
    realtimeMocks.reconnect.mockResolvedValue(undefined)
    realtimeMocks.disconnect.mockResolvedValue(undefined)
  })

  it('marks REST and database as available after a successful probe', async () => {
    mockedFetchHealth.mockResolvedValue(successfulHealthResponse())

    const store = useConnectionStore()
    await store.checkRestHealth()

    expect(store.backendState).toBe('UP')
    expect(store.databaseState).toBe('UP')
    expect(store.errorMessage).toBeNull()
  })

  it('marks REST and database as down after a failed probe', async () => {
    mockedFetchHealth.mockRejectedValue(new Error('connection refused'))

    const store = useConnectionStore()
    await store.checkRestHealth()

    expect(store.backendState).toBe('DOWN')
    expect(store.databaseState).toBe('DOWN')
    expect(store.errorMessage).toContain('connection refused')
  })

  it('uses the initial connect path during initialization', async () => {
    mockedFetchHealth.mockResolvedValue(successfulHealthResponse())

    const store = useConnectionStore()
    await store.initialize()

    expect(realtimeMocks.connect).toHaveBeenCalledTimes(1)
    expect(realtimeMocks.reconnect).not.toHaveBeenCalled()
  })

  it('uses the dedicated reconnect path for a manual reconnect', async () => {
    const store = useConnectionStore()

    await store.reconnectWebSocket()

    expect(realtimeMocks.reconnect).toHaveBeenCalledTimes(1)
    expect(realtimeMocks.connect).not.toHaveBeenCalled()
  })

  it('blocks repeated manual reconnect requests while one is in progress', async () => {
    let finishReconnect: (() => void) | undefined
    realtimeMocks.reconnect.mockImplementation(
      () =>
        new Promise<void>((resolve) => {
          finishReconnect = resolve
        }),
    )

    const store = useConnectionStore()
    const firstReconnect = store.reconnectWebSocket()
    const repeatedReconnect = store.reconnectWebSocket()

    expect(store.websocketActionDisabled).toBe(true)
    expect(realtimeMocks.reconnect).toHaveBeenCalledTimes(1)

    finishReconnect?.()
    await Promise.all([firstReconnect, repeatedReconnect])

    expect(store.websocketOperationInProgress).toBe(false)
  })
})

import { afterEach, describe, expect, it, vi } from 'vitest'

import { resolveApiBaseUrl, resolveWebSocketUrl } from '@/config/runtimeEndpoints'

afterEach(() => {
  vi.unstubAllEnvs()
})

describe('runtime endpoint resolver', () => {
  it('keeps an explicitly configured API URL', () => {
    expect(resolveApiBaseUrl('http://localhost:8080')).toBe('http://localhost:8080')
  })

  it('uses a relative API base when the configured value is empty or unset', () => {
    expect(resolveApiBaseUrl('')).toBe('')

    vi.stubEnv('VITE_API_BASE_URL', undefined)
    expect(resolveApiBaseUrl()).toBe('')
  })

  it('keeps an explicitly configured WebSocket URL', () => {
    expect(resolveWebSocketUrl('ws://localhost:8080/ws')).toBe('ws://localhost:8080/ws')
  })

  it('resolves an HTTP origin to a ws URL', () => {
    expect(resolveWebSocketUrl('', { protocol: 'http:', host: 'localhost' })).toBe(
      'ws://localhost/ws',
    )
  })

  it('resolves an HTTPS origin to a wss URL', () => {
    expect(resolveWebSocketUrl('', { protocol: 'https:', host: 'example.internal' })).toBe(
      'wss://example.internal/ws',
    )
  })

  it('preserves the browser port while resolving the WebSocket URL', () => {
    expect(resolveWebSocketUrl('', { protocol: 'http:', host: 'localhost:5173' })).toBe(
      'ws://localhost:5173/ws',
    )
  })
})

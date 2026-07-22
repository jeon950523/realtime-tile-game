import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import { installAuthenticationGuards } from '@/router'
import { resetAuthStoreFlightsForTests, useAuthStore } from '@/stores/auth'
import { pinia } from '@/stores/pinia'
import * as gameApi from '@/api/gameApi'
import * as roomApi from '@/api/roomApi'

vi.mock('@/api/gameApi')
vi.mock('@/api/roomApi')

function guardedRouter() {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', name: 'root', component: { template: '<div />' } },
      { path: '/health', name: 'health', component: { template: '<div />' } },
      { path: '/login', name: 'login', component: { template: '<div />' }, meta: { guestOnly: true } },
      { path: '/profile', name: 'profile', component: { template: '<div />' }, meta: { requiresAuth: true } },
      { path: '/lobby', name: 'lobby', component: { template: '<div />' }, meta: { requiresAuth: true } },
      { path: '/rooms/:roomId', name: 'waiting-room', component: { template: '<div />' }, meta: { requiresAuth: true } },
      { path: '/games/:gameId', name: 'game', component: { template: '<div />' }, meta: { requiresAuth: true } },
    ],
  })
  installAuthenticationGuards(router)
  return router
}

beforeEach(() => {
  vi.clearAllMocks()
  const store = useAuthStore(pinia)
  store.$reset()
  store.initializationState = 'READY'
  resetAuthStoreFlightsForTests()
  vi.mocked(gameApi.getActiveGame).mockResolvedValue({ active: false, gameId: null, roomId: null, status: null })
  vi.mocked(roomApi.getActiveRoom).mockResolvedValue({ active: false, roomId: null, status: null })
})

describe('authentication router guard', () => {
  it('does not attempt session restoration on the public health route', async () => {
    const store = useAuthStore(pinia)
    store.initializationState = 'UNINITIALIZED'
    const restoreSession = vi.spyOn(store, 'restoreSession')
    const router = guardedRouter()

    await router.push('/health')

    expect(router.currentRoute.value.name).toBe('health')
    expect(restoreSession).not.toHaveBeenCalled()
    expect(store.initializationState).toBe('UNINITIALIZED')
  })

  it('redirects an anonymous user away from the profile route', async () => {
    const router = guardedRouter()

    await router.push('/profile')

    expect(router.currentRoute.value.name).toBe('login')
    expect(router.currentRoute.value.query.redirect).toBe('/profile')
  })

  it('redirects an authenticated user away from guest-only routes', async () => {
    const store = useAuthStore(pinia)
    store.authStatus = 'AUTHENTICATED'
    const router = guardedRouter()

    await router.push('/login')

    expect(router.currentRoute.value.name).toBe('lobby')
  })

  it('restores an authenticated user to the active waiting room', async () => {
    const store = useAuthStore(pinia)
    store.authStatus = 'AUTHENTICATED'
    vi.mocked(roomApi.getActiveRoom).mockResolvedValue({ active: true, roomId: 17, status: 'WAITING' })
    const router = guardedRouter()

    await router.push('/')

    expect(router.currentRoute.value.name).toBe('waiting-room')
    expect(router.currentRoute.value.params.roomId).toBe('17')
  })

  it('restores an authenticated user to the active game before the waiting room', async () => {
    const store = useAuthStore(pinia)
    store.authStatus = 'AUTHENTICATED'
    vi.mocked(gameApi.getActiveGame).mockResolvedValue({ active: true, gameId: 33, roomId: 17, status: 'IN_PROGRESS' })
    vi.mocked(roomApi.getActiveRoom).mockResolvedValue({ active: true, roomId: 17, status: 'PLAYING' })
    const router = guardedRouter()

    await router.push('/')

    expect(router.currentRoute.value.name).toBe('game')
    expect(router.currentRoute.value.params.gameId).toBe('33')
    expect(roomApi.getActiveRoom).not.toHaveBeenCalled()
  })


  it('validates the active game before restoring a direct game route', async () => {
    const store = useAuthStore(pinia)
    store.authStatus = 'AUTHENTICATED'
    vi.mocked(gameApi.getActiveGame).mockResolvedValue({ active: true, gameId: 33, roomId: 17, status: 'IN_PROGRESS' })
    const router = guardedRouter()

    await router.push('/games/33')

    expect(router.currentRoute.value.name).toBe('game')
    expect(router.currentRoute.value.params.gameId).toBe('33')
    expect(gameApi.getActiveGame).toHaveBeenCalledTimes(1)
    expect(roomApi.getActiveRoom).not.toHaveBeenCalled()
  })

  it('redirects a stale game url to the current active game', async () => {
    const store = useAuthStore(pinia)
    store.authStatus = 'AUTHENTICATED'
    vi.mocked(gameApi.getActiveGame).mockResolvedValue({ active: true, gameId: 33, roomId: 17, status: 'IN_PROGRESS' })
    const router = guardedRouter()

    await router.push('/games/99')

    expect(router.currentRoute.value.name).toBe('game')
    expect(router.currentRoute.value.params.gameId).toBe('33')
  })

  it('rejects an invalid room id safely', async () => {
    const store = useAuthStore(pinia)
    store.authStatus = 'AUTHENTICATED'
    const router = guardedRouter()

    await router.push('/rooms/not-a-number')

    expect(router.currentRoute.value.name).toBe('lobby')
  })

})

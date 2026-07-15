import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as roomApi from '@/api/roomApi'
import RoomCreateModal from '@/components/room/RoomCreateModal.vue'
import LobbyView from '@/views/LobbyView.vue'
import WaitingRoomView from '@/views/WaitingRoomView.vue'
import { useAuthStore } from '@/stores/auth'
import { resetRoomStoreClientForTests, useRoomStore } from '@/stores/room'
import type { RoomDetail, RoomSummary } from '@/types/room'

const realtime = vi.hoisted(() => ({
  connectLobby: vi.fn(async () => undefined),
  connectRoom: vi.fn(async () => undefined),
  disconnect: vi.fn(async () => undefined),
  publishReady: vi.fn(),
  publishStart: vi.fn(),
}))

vi.mock('@/api/roomApi')
vi.mock('@/realtime/authenticatedStompClient', () => ({
  AuthenticatedStompClient: class {
    connectLobby = realtime.connectLobby
    connectRoom = realtime.connectRoom
    disconnect = realtime.disconnect
    publishReady = realtime.publishReady
    publishStart = realtime.publishStart
  },
}))

const summary: RoomSummary = {
  roomId: 10,
  roomName: '초보방',
  ownerNickname: 'owner',
  currentPlayers: 1,
  maxPlayers: 4,
  gameMode: 'CLASSIC',
  turnTimeLimitSeconds: 120,
  status: 'WAITING',
  joinable: true,
}

const detail: RoomDetail = {
  roomId: 10,
  roomName: '초보방',
  ownerUserId: 1,
  ownerNickname: 'owner',
  currentPlayers: 2,
  maxPlayers: 4,
  gameMode: 'CLASSIC',
  turnTimeLimitSeconds: 120,
  status: 'WAITING',
  startable: false,
  startBlockReason: 'ROOM_PLAYERS_NOT_READY',
  participants: [
    { userId: 1, nickname: 'owner', avatarType: 'DEFAULT_01', seatOrder: 1, readyStatus: 'READY', owner: true },
    { userId: 2, nickname: 'guest', avatarType: 'DEFAULT_02', seatOrder: 2, readyStatus: 'NOT_READY', owner: false },
  ],
}

async function routerAt(path: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/lobby', component: LobbyView },
      { path: '/rooms/:roomId', name: 'waiting-room', component: WaitingRoomView },
      { path: '/games/:gameId', name: 'game', component: { template: '<div>game</div>' } },
      { path: '/profile', component: { template: '<div>profile</div>' } },
      { path: '/login', component: { template: '<div>login</div>' } },
    ],
  })
  await router.push(path)
  await router.isReady()
  return router
}

beforeEach(() => {
  setActivePinia(createPinia())
  resetRoomStoreClientForTests()
  vi.resetAllMocks()
  vi.mocked(roomApi.getRooms).mockResolvedValue({ content: [summary], page: 0, size: 20, totalElements: 1 })
  vi.mocked(roomApi.getRoomDetail).mockResolvedValue(detail)
  const auth = useAuthStore()
  auth.accessToken = 'token'
  auth.authStatus = 'AUTHENTICATED'
  auth.user = { userId: 1, nickname: 'owner', avatarType: 'DEFAULT_01', ratingScore: 1000 }
})

describe('room views', () => {
  it('renders the initial lobby room list and joins through the server response', async () => {
    vi.mocked(roomApi.joinRoom).mockResolvedValue(detail)
    const router = await routerAt('/lobby')
    const wrapper = mount(LobbyView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('초보방')
    expect(wrapper.text()).toContain('1/4')
    await wrapper.get('article.room-card button').trigger('click')
    await flushPromises()
    expect(roomApi.joinRoom).toHaveBeenCalledWith(10)
    expect(router.currentRoute.value.fullPath).toBe('/rooms/10')
  })

  it('validates and emits a normalized CLASSIC public room request', async () => {
    const wrapper = mount(RoomCreateModal, { props: { submitting: false, error: null } })
    await wrapper.get('input').setValue('  새 방  ')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.emitted('submit')?.[0]?.[0]).toEqual({
      roomName: '새 방', maxPlayers: 4, gameMode: 'CLASSIC', turnTimeLimitSeconds: 120, isPublic: true,
    })
  })

  it('blocks an invalid room name without emitting a request', async () => {
    const wrapper = mount(RoomCreateModal, { props: { submitting: false, error: null } })
    await wrapper.get('input').setValue('a')
    await wrapper.get('form').trigger('submit')

    expect(wrapper.text()).toContain('2~50자')
    expect(wrapper.emitted('submit')).toBeUndefined()
  })

  it('renders participants, empty seats, owner badge and disabled start condition', async () => {
    const router = await routerAt('/rooms/10')
    const wrapper = mount(WaitingRoomView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).toContain('owner')
    expect(wrapper.text()).toContain('guest')
    expect(wrapper.findAll('.seat-card--empty')).toHaveLength(2)
    expect(wrapper.text()).toContain('방장')
    const startButton = wrapper.findAll('button').find((button) => button.text().includes('게임 시작'))
    expect(startButton?.attributes('disabled')).toBeDefined()
  })

  it('does not render the start button for a non-owner participant', async () => {
    useAuthStore().user = { userId: 2, nickname: 'guest', avatarType: 'DEFAULT_02', ratingScore: 1000 }
    const router = await routerAt('/rooms/10')
    const wrapper = mount(WaitingRoomView, { global: { plugins: [router] } })
    await flushPromises()

    expect(wrapper.text()).not.toContain('게임 시작')
  })

  it('moves every participant after GAME_STARTED instead of relying on the owner reply', async () => {
    const router = await routerAt('/rooms/10')
    mount(WaitingRoomView, { global: { plugins: [router] } })
    await flushPromises()
    const store = useRoomStore()

    store.applyRoomEvent({
      eventType: 'GAME_STARTED',
      occurredAt: '',
      payload: { gameId: 33, roomId: 10, route: '/games/33' },
    })
    await flushPromises()

    expect(realtime.disconnect).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.fullPath).toBe('/games/33')
  })

  it('leaves the room before moving back to the lobby', async () => {
    vi.mocked(roomApi.leaveRoom).mockResolvedValue(undefined)
    const router = await routerAt('/rooms/10')
    const wrapper = mount(WaitingRoomView, { global: { plugins: [router] } })
    await flushPromises()

    const leave = wrapper.findAll('button').find((button) => button.text() === '방 나가기')!
    await leave.trigger('click')
    await flushPromises()

    expect(roomApi.leaveRoom).toHaveBeenCalledWith(10)
    expect(router.currentRoute.value.fullPath).toBe('/lobby')
  })
})

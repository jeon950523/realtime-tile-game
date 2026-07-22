import { createPinia, setActivePinia } from 'pinia'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { beforeEach, describe, expect, it, vi } from 'vitest'

import * as authApi from '@/api/authApi'
import * as profileApi from '@/api/profileApi'
import * as roomApi from '@/api/roomApi'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'
import ProfileView from '@/views/ProfileView.vue'
import RegisterView from '@/views/RegisterView.vue'

vi.mock('@/api/authApi')
vi.mock('@/api/profileApi')
vi.mock('@/api/roomApi')

function testRouter() {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/login', name: 'login', component: LoginView },
      { path: '/register', name: 'register', component: RegisterView },
      { path: '/profile', name: 'profile', component: ProfileView },
      { path: '/lobby', name: 'lobby', component: { template: '<div />' } },
      { path: '/rooms/:roomId', name: 'waiting-room', component: { template: '<div />' } },
    ],
  })
}

beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(roomApi.getActiveRoom).mockResolvedValue({ active: false, roomId: null, status: null })
})

describe('authentication views', () => {

  it('shows a safe login failure message', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = testRouter()
    await router.push('/login')
    await router.isReady()
    vi.mocked(authApi.login).mockRejectedValue(new Error('invalid'))
    const wrapper = mount(LoginView, { global: { plugins: [pinia, router] } })

    await wrapper.get('input[name="email"]').setValue('user@example.com')
    await wrapper.get('input[name="password"]').setValue('wrong-password')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(wrapper.text()).toContain('로그인에 실패했습니다')
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('moves to login after successful registration', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = testRouter()
    await router.push('/register')
    await router.isReady()
    vi.mocked(authApi.register).mockResolvedValue({
      userId: 1,
      email: 'user@example.com',
      nickname: 'player1',
      profileSetupRequired: true,
    })
    const wrapper = mount(RegisterView, { global: { plugins: [pinia, router] } })

    await wrapper.get('input[name="email"]').setValue('user@example.com')
    await wrapper.get('input[name="nickname"]').setValue('player1')
    await wrapper.get('input[name="password"]').setValue('qwer1234!')
    await wrapper.get('input[name="passwordConfirm"]').setValue('qwer1234!')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(authApi.register).toHaveBeenCalledTimes(1)
    expect(router.currentRoute.value.path).toBe('/login')
  })

  it('loads a profile when the authenticated screen has no cached profile', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = testRouter()
    await router.push('/profile')
    await router.isReady()
    const store = useAuthStore()
    store.authStatus = 'AUTHENTICATED'
    store.initializationState = 'READY'
    vi.mocked(profileApi.getMyProfile).mockResolvedValue({
      userId: 1,
      email: 'user@example.com',
      nickname: 'player1',
      avatarType: 'DEFAULT_01',
      ratingScore: 1000,
      classicRecord: { wins: 0, losses: 0, draws: 0, totalGames: 0 },
      speedRecord: { wins: 0, losses: 0, draws: 0, totalGames: 0 },
      activeSession: { roomId: null, gameId: null, status: null },
    })

    const wrapper = mount(ProfileView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    expect(profileApi.getMyProfile).toHaveBeenCalledTimes(1)
    expect(wrapper.text()).toContain('player1')
    expect(wrapper.text()).toContain('1000')
  })
  it('logs in and moves to the lobby screen', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = testRouter()
    await router.push('/login')
    await router.isReady()
    vi.mocked(authApi.login).mockResolvedValue({
      accessToken: 'token',
      expiresIn: 1800,
      user: { userId: 1, nickname: 'player1', avatarType: 'DEFAULT_01', ratingScore: 1000 },
      redirect: { type: 'LOBBY', roomId: null, gameId: null },
    })
    const wrapper = mount(LoginView, { global: { plugins: [pinia, router] } })

    await wrapper.get('input[name="email"]').setValue('user@example.com')
    await wrapper.get('input[name="password"]').setValue('qwer1234!')
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(useAuthStore().authStatus).toBe('AUTHENTICATED')
    expect(router.currentRoute.value.path).toBe('/lobby')
  })

  it('blocks registration locally when password confirmation differs', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = testRouter()
    await router.push('/register')
    await router.isReady()
    const wrapper = mount(RegisterView, { global: { plugins: [pinia, router] } })

    await wrapper.get('input[name="email"]').setValue('user@example.com')
    await wrapper.get('input[name="nickname"]').setValue('player1')
    await wrapper.get('input[name="password"]').setValue('qwer1234!')
    await wrapper.get('input[name="passwordConfirm"]').setValue('different1!')
    await wrapper.get('form').trigger('submit')

    expect(authApi.register).not.toHaveBeenCalled()
    expect(wrapper.text()).toContain('일치하지 않습니다')
  })

  it('updates nickname and avatar from the profile screen', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = testRouter()
    await router.push('/profile')
    await router.isReady()
    const store = useAuthStore()
    store.authStatus = 'AUTHENTICATED'
    store.initializationState = 'READY'
    store.profile = {
      userId: 1,
      email: 'user@example.com',
      nickname: 'player1',
      avatarType: 'DEFAULT_01',
      ratingScore: 1000,
      classicRecord: { wins: 0, losses: 0, draws: 0, totalGames: 0 },
      speedRecord: { wins: 0, losses: 0, draws: 0, totalGames: 0 },
      activeSession: { roomId: null, gameId: null, status: null },
    }
    store.user = { userId: 1, nickname: 'player1', avatarType: 'DEFAULT_01', ratingScore: 1000 }
    vi.mocked(profileApi.updateMyProfile).mockResolvedValue({ nickname: 'newName', avatarType: 'DEFAULT_03' })
    const wrapper = mount(ProfileView, { global: { plugins: [pinia, router] } })

    await wrapper.get('input:not([readonly])').setValue('newName')
    await wrapper.get('input[value="DEFAULT_03"]').setValue(true)
    await wrapper.get('form').trigger('submit')
    await flushPromises()

    expect(profileApi.updateMyProfile).toHaveBeenCalledWith({ nickname: 'newName', avatarType: 'DEFAULT_03' })
    expect(store.profile?.nickname).toBe('newName')
    expect(wrapper.text()).toContain('프로필을 저장했습니다')
  })
  it('logoutFailureShowsSafeMessage', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = testRouter()
    await router.push('/profile')
    await router.isReady()
    const store = useAuthStore()
    store.authStatus = 'AUTHENTICATED'
    store.initializationState = 'READY'
    store.accessToken = 'token'
    store.profile = {
      userId: 1,
      email: 'user@example.com',
      nickname: 'player1',
      avatarType: 'DEFAULT_01',
      ratingScore: 1000,
      classicRecord: { wins: 0, losses: 0, draws: 0, totalGames: 0 },
      speedRecord: { wins: 0, losses: 0, draws: 0, totalGames: 0 },
      activeSession: { roomId: null, gameId: null, status: null },
    }
    store.user = { userId: 1, nickname: 'player1', avatarType: 'DEFAULT_01', ratingScore: 1000 }
    vi.mocked(authApi.logout).mockRejectedValue(new Error('server failed'))
    const wrapper = mount(ProfileView, { global: { plugins: [pinia, router] } })

    await wrapper.get('button.secondary-button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('로그아웃에 실패했습니다. 잠시 후 다시 시도해주세요.')
    expect(store.authStatus).toBe('AUTHENTICATED')
  })

  it('logoutFailureDoesNotRedirect', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = testRouter()
    await router.push('/profile')
    await router.isReady()
    const store = useAuthStore()
    store.authStatus = 'AUTHENTICATED'
    store.initializationState = 'READY'
    store.accessToken = 'token'
    store.profile = {
      userId: 1,
      email: 'user@example.com',
      nickname: 'player1',
      avatarType: 'DEFAULT_01',
      ratingScore: 1000,
      classicRecord: { wins: 0, losses: 0, draws: 0, totalGames: 0 },
      speedRecord: { wins: 0, losses: 0, draws: 0, totalGames: 0 },
      activeSession: { roomId: null, gameId: null, status: null },
    }
    store.user = { userId: 1, nickname: 'player1', avatarType: 'DEFAULT_01', ratingScore: 1000 }
    vi.mocked(authApi.logout).mockRejectedValue(new Error('server failed'))
    const wrapper = mount(ProfileView, { global: { plugins: [pinia, router] } })

    await wrapper.get('button.secondary-button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/profile')
  })

  it('successfulLogoutRedirectsToLogin', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = testRouter()
    await router.push('/profile')
    await router.isReady()
    const store = useAuthStore()
    store.authStatus = 'AUTHENTICATED'
    store.initializationState = 'READY'
    store.accessToken = 'token'
    store.profile = {
      userId: 1,
      email: 'user@example.com',
      nickname: 'player1',
      avatarType: 'DEFAULT_01',
      ratingScore: 1000,
      classicRecord: { wins: 0, losses: 0, draws: 0, totalGames: 0 },
      speedRecord: { wins: 0, losses: 0, draws: 0, totalGames: 0 },
      activeSession: { roomId: null, gameId: null, status: null },
    }
    store.user = { userId: 1, nickname: 'player1', avatarType: 'DEFAULT_01', ratingScore: 1000 }
    vi.mocked(authApi.logout).mockResolvedValue()
    const wrapper = mount(ProfileView, { global: { plugins: [pinia, router] } })

    await wrapper.get('button.secondary-button').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.path).toBe('/login')
    expect(store.authStatus).toBe('ANONYMOUS')
  })


  it('shows the required password placeholders', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = testRouter()
    await router.push('/register')
    await router.isReady()
    const wrapper = mount(RegisterView, { global: { plugins: [pinia, router] } })

    expect(wrapper.get('input[name="password"]').attributes('placeholder')).toBe('예: qwer1234!')
    expect(wrapper.get('input[name="passwordConfirm"]').attributes('placeholder')).toBe('비밀번호를 다시 입력하세요')
  })

})

import { createRouter, createWebHistory, type Router } from 'vue-router'

import { pinia } from '@/stores/pinia'
import { useAuthStore } from '@/stores/auth'
import { useGameStore } from '@/stores/game'
import { useRoomStore } from '@/stores/room'
import GameView from '@/views/GameView.vue'
import HealthView from '@/views/HealthView.vue'
import LoginView from '@/views/LoginView.vue'
import ProfileView from '@/views/ProfileView.vue'
import LobbyView from '@/views/LobbyView.vue'
import WaitingRoomView from '@/views/WaitingRoomView.vue'
import RegisterView from '@/views/RegisterView.vue'

export function installAuthenticationGuards(router: Router): void {
  router.beforeEach(async (to) => {
    const authStore = useAuthStore(pinia)
    if (authStore.initializationState === 'UNINITIALIZED') {
      await authStore.restoreSession()
    }

    const gameStore = useGameStore(pinia)
    const roomStore = useRoomStore(pinia)
    const waitingRoomOrLobby = async () => {
      const activeRoom = await roomStore.loadActiveRoom()
      return activeRoom.active && activeRoom.roomId !== null
        ? { name: 'waiting-room', params: { roomId: activeRoom.roomId } }
        : { name: 'lobby' }
    }
    const authenticatedHome = async () => {
      const activeGame = await gameStore.loadActiveGame()
      if (activeGame.active && activeGame.gameId !== null) {
        return { name: 'game', params: { gameId: activeGame.gameId } }
      }
      return waitingRoomOrLobby()
    }

    if (to.path === '/') {
      return authStore.authStatus === 'AUTHENTICATED' ? authenticatedHome() : { name: 'login' }
    }
    if (to.meta.requiresAuth && authStore.authStatus !== 'AUTHENTICATED') {
      return { name: 'login', query: { redirect: to.fullPath } }
    }
    if (to.meta.guestOnly && authStore.authStatus === 'AUTHENTICATED') {
      return authenticatedHome()
    }
    if (authStore.authStatus === 'AUTHENTICATED' && (to.name === 'lobby' || to.name === 'waiting-room')) {
      const activeGame = await gameStore.loadActiveGame()
      if (activeGame.active && activeGame.gameId !== null) {
        return { name: 'game', params: { gameId: activeGame.gameId } }
      }
    }
    if (to.name === 'waiting-room') {
      const roomId = Number(to.params.roomId)
      if (!Number.isInteger(roomId) || roomId <= 0) return { name: 'lobby' }
    }
    if (to.name === 'game') {
      const gameId = Number(to.params.gameId)
      if (!Number.isInteger(gameId) || gameId <= 0) return authenticatedHome()
      const activeGame = await gameStore.loadActiveGame()
      if (!activeGame.active || activeGame.gameId === null) return waitingRoomOrLobby()
      if (activeGame.gameId !== gameId) {
        return { name: 'game', params: { gameId: activeGame.gameId } }
      }
    }
    return true
  })
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'root',
      component: { template: '<div />' },
    },
    {
      path: '/health',
      name: 'health',
      component: HealthView,
    },
    {
      path: '/login',
      name: 'login',
      component: LoginView,
      meta: { guestOnly: true },
    },
    {
      path: '/register',
      name: 'register',
      component: RegisterView,
      meta: { guestOnly: true },
    },
    {
      path: '/lobby',
      name: 'lobby',
      component: LobbyView,
      meta: { requiresAuth: true },
    },
    {
      path: '/rooms/:roomId',
      name: 'waiting-room',
      component: WaitingRoomView,
      meta: { requiresAuth: true },
    },
    {
      path: '/games/:gameId',
      name: 'game',
      component: GameView,
      meta: { requiresAuth: true },
    },
    {
      path: '/profile',
      name: 'profile',
      component: ProfileView,
      meta: { requiresAuth: true },
    },
  ],
})

installAuthenticationGuards(router)

export default router

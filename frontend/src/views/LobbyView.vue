<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import RoomCreateModal from '@/components/room/RoomCreateModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useRoomStore } from '@/stores/room'
import type { CreateRoomRequest } from '@/types/room'

const authStore = useAuthStore()
const roomStore = useRoomStore()
const router = useRouter()
const showCreate = ref(false)
const user = computed(() => authStore.user)

onMounted(async () => {
  try {
    await Promise.all([roomStore.loadRooms(), roomStore.connectLobby()])
  } catch {
    // Store exposes a safe error and keeps the lobby usable.
  }
})

async function create(request: CreateRoomRequest): Promise<void> {
  try {
    const room = await roomStore.createRoom(request)
    showCreate.value = false
    await router.push(`/rooms/${room.roomId}`)
  } catch {
    // Store exposes safe error.
  }
}

async function join(roomId: number): Promise<void> {
  try {
    const room = await roomStore.joinRoom(roomId)
    await router.push(`/rooms/${room.roomId}`)
  } catch {
    // Store exposes safe error.
  }
}

async function quickMatch(): Promise<void> {
  try {
    const room = await roomStore.quickMatch()
    if (room) await router.push(`/rooms/${room.roomId}`)
  } catch {
    // Store exposes safe error.
  }
}

async function logout(): Promise<void> {
  try {
    await authStore.logout()
    await roomStore.disconnectRealtime()
    roomStore.clearRoomState()
    await router.replace('/login')
  } catch {
    // Authentication and realtime state remain available for a retry.
  }
}
</script>

<template>
  <main class="lobby-page">
    <section class="lobby-toolbar">
      <div>
        <p class="eyebrow">LOBBY</p>
        <h1>대기 중인 방</h1>
        <p>{{ user?.nickname }} · {{ user?.avatarType }} · Rating {{ user?.ratingScore }}</p>
      </div>
      <div class="toolbar-actions">
        <RouterLink class="secondary-button button-link" to="/profile">프로필</RouterLink>
        <button class="secondary-button" type="button" @click="logout">로그아웃</button>
        <button class="secondary-button" type="button" :disabled="roomStore.loadingRooms" @click="roomStore.loadRooms">새로고침</button>
        <button class="secondary-button" type="button" :disabled="roomStore.joining || roomStore.quickMatching" @click="quickMatch">빠른 입장</button>
        <button class="primary-button" type="button" @click="showCreate = true">방 만들기</button>
      </div>
    </section>

    <div class="connection-banner" :data-state="roomStore.lobbyConnectionState">
      실시간 로비: {{ roomStore.lobbyConnectionState }}
    </div>
    <p v-if="roomStore.lastError" class="form-error" role="alert">{{ roomStore.lastError }}</p>
    <p v-if="roomStore.lastMessage" class="form-success" role="status">{{ roomStore.lastMessage }}</p>

    <section v-if="roomStore.rooms.length" class="room-list" aria-label="방 목록">
      <article v-for="room in roomStore.rooms" :key="room.roomId" class="room-card">
        <div><span class="room-mode">{{ room.gameMode }}</span><h2>{{ room.roomName }}</h2><p>방장 {{ room.ownerNickname }}</p></div>
        <dl><div><dt>인원</dt><dd>{{ room.currentPlayers }}/{{ room.maxPlayers }}</dd></div><div><dt>턴</dt><dd>{{ room.turnTimeLimitSeconds }}초</dd></div><div><dt>상태</dt><dd>{{ room.status }}</dd></div></dl>
        <button class="primary-button" type="button" :disabled="!room.joinable || roomStore.joining" @click="join(room.roomId)">
          {{ room.joinable ? '입장' : '입장 불가' }}
        </button>
      </article>
    </section>
    <section v-else class="empty-panel"><h2>현재 참여 가능한 방이 없습니다.</h2><p>직접 방을 만들어보세요.</p><button class="primary-button" @click="showCreate = true">방 만들기</button></section>

    <RoomCreateModal v-if="showCreate" :submitting="roomStore.creating" :error="roomStore.lastError" @close="showCreate = false" @submit="create" />
  </main>
</template>

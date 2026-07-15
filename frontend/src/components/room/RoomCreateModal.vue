<script setup lang="ts">
import { ref } from 'vue'

import type { CreateRoomRequest } from '@/types/room'

const props = defineProps<{ submitting: boolean; error: string | null }>()
const emit = defineEmits<{ close: []; submit: [request: CreateRoomRequest] }>()

const roomName = ref('')
const maxPlayers = ref<2 | 3 | 4>(4)
const turnTimeLimitSeconds = ref(120)
const clientError = ref<string | null>(null)

function submit(): void {
  if (props.submitting) return
  const normalized = roomName.value.trim()
  if (normalized.length < 2 || normalized.length > 50) {
    clientError.value = '방 이름은 2~50자로 입력해주세요.'
    return
  }
  clientError.value = null
  emit('submit', {
    roomName: normalized,
    maxPlayers: maxPlayers.value,
    gameMode: 'CLASSIC',
    turnTimeLimitSeconds: turnTimeLimitSeconds.value,
    isPublic: true,
  })
}
</script>

<template>
  <div class="modal-backdrop" role="presentation" @click.self="emit('close')">
    <section class="room-modal" role="dialog" aria-modal="true" aria-labelledby="room-create-title">
      <div class="section-heading">
        <div>
          <p class="eyebrow">CREATE ROOM</p>
          <h2 id="room-create-title">방 만들기</h2>
        </div>
        <button class="icon-button" type="button" aria-label="닫기" :disabled="submitting" @click="emit('close')">×</button>
      </div>
      <form class="room-form" @submit.prevent="submit">
        <label>방 이름<input v-model="roomName" required minlength="2" maxlength="50" placeholder="예: 초보자 환영" /></label>
        <label>최대 인원
          <select v-model="maxPlayers">
            <option :value="2">2명</option><option :value="3">3명</option><option :value="4">4명</option>
          </select>
        </label>
        <label>턴 제한시간
          <select v-model="turnTimeLimitSeconds">
            <option :value="60">60초</option><option :value="90">90초</option><option :value="120">120초</option><option :value="180">180초</option>
          </select>
        </label>
        <div class="fixed-setting"><span>게임 모드</span><strong>CLASSIC</strong></div>
        <div class="fixed-setting"><span>공개 여부</span><strong>공개방</strong></div>
        <p v-if="clientError || error" class="form-error" role="alert">{{ clientError ?? error }}</p>
        <div class="modal-actions">
          <button class="secondary-button" type="button" :disabled="submitting" @click="emit('close')">취소</button>
          <button class="primary-button" type="submit" :disabled="submitting">{{ submitting ? '생성 중…' : '방 만들기' }}</button>
        </div>
      </form>
    </section>
  </div>
</template>

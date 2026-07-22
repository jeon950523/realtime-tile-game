<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import { useRoomStore } from '@/stores/room'

const authStore = useAuthStore()
const roomStore = useRoomStore()
const route = useRoute()
const router = useRouter()
const email = ref('')
const password = ref('')
const submitting = ref(false)

async function submit(): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  try {
    await authStore.login({ email: email.value, password: password.value })
    if (typeof route.query.redirect === 'string') {
      await router.push(route.query.redirect)
      return
    }
    const active = await roomStore.loadActiveRoom()
    await router.push(active.active && active.roomId !== null ? `/rooms/${active.roomId}` : '/lobby')
  } catch {
    // The store exposes the API-safe message.
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-card" aria-labelledby="login-title">
      <p class="eyebrow">AUTHENTICATION</p>
      <h1 id="login-title">로그인</h1>
      <p class="auth-copy">Access Token은 브라우저 메모리에만 유지하고, 새로고침 시 HttpOnly Cookie로 세션을 복구합니다.</p>

      <form class="auth-form" @submit.prevent="submit">
        <label>
          이메일
          <input v-model="email" name="email" type="email" autocomplete="email" required maxlength="255" />
        </label>
        <label>
          비밀번호
          <input v-model="password" name="password" type="password" autocomplete="current-password" required />
        </label>
        <p v-if="authStore.lastError" class="form-error" role="alert">{{ authStore.lastError }}</p>
        <button class="primary-button" type="submit" :disabled="submitting">
          {{ submitting ? '로그인 중…' : '로그인' }}
        </button>
      </form>

      <p class="auth-switch">계정이 없나요? <RouterLink to="/register">회원가입</RouterLink></p>
    </section>
  </main>
</template>

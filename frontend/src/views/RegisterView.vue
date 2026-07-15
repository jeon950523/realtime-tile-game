<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const router = useRouter()
const email = ref('')
const nickname = ref('')
const password = ref('')
const passwordConfirm = ref('')
const submitting = ref(false)
const clientError = ref<string | null>(null)

async function submit(): Promise<void> {
  if (submitting.value) return
  clientError.value = null
  if (password.value !== passwordConfirm.value) {
    clientError.value = '비밀번호와 비밀번호 확인이 일치하지 않습니다.'
    return
  }

  submitting.value = true
  try {
    await authStore.register({
      email: email.value,
      nickname: nickname.value,
      password: password.value,
      passwordConfirm: passwordConfirm.value,
    })
    await router.push('/login')
  } catch {
    // The store exposes the API-safe message.
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <section class="auth-card" aria-labelledby="register-title">
      <p class="eyebrow">CREATE ACCOUNT</p>
      <h1 id="register-title">회원가입</h1>
      <p class="auth-copy">비밀번호는 8~64자이며 영문, 숫자, 특수문자를 각각 하나 이상 포함해야 합니다.</p>

      <form class="auth-form" @submit.prevent="submit">
        <label>
          이메일
          <input v-model="email" name="email" type="email" autocomplete="email" required maxlength="255" />
        </label>
        <label>
          닉네임
          <input v-model="nickname" name="nickname" autocomplete="nickname" required minlength="2" maxlength="20" pattern="[가-힣A-Za-z0-9_]+" />
        </label>
        <label>
          비밀번호
          <input v-model="password" name="password" type="password" autocomplete="new-password" required minlength="8" maxlength="64" placeholder="예: qwer1234!" />
        </label>
        <label>
          비밀번호 확인
          <input v-model="passwordConfirm" name="passwordConfirm" type="password" autocomplete="new-password" required minlength="8" maxlength="64" placeholder="비밀번호를 다시 입력하세요" />
        </label>
        <p v-if="clientError || authStore.lastError" class="form-error" role="alert">
          {{ clientError ?? authStore.lastError }}
        </p>
        <button class="primary-button" type="submit" :disabled="submitting">
          {{ submitting ? '가입 중…' : '회원가입' }}
        </button>
      </form>

      <p class="auth-switch">이미 계정이 있나요? <RouterLink to="/login">로그인</RouterLink></p>
    </section>
  </main>
</template>

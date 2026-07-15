<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { useAuthStore } from '@/stores/auth'
import type { AvatarType } from '@/types/auth'

const AVATARS: AvatarType[] = ['DEFAULT_01', 'DEFAULT_02', 'DEFAULT_03', 'DEFAULT_04']
const authStore = useAuthStore()
const router = useRouter()
const nickname = ref('')
const avatarType = ref<AvatarType>('DEFAULT_01')
const submitting = ref(false)
const successMessage = ref<string | null>(null)

const profile = computed(() => authStore.profile)

watch(
  profile,
  (value) => {
    if (!value) return
    nickname.value = value.nickname
    avatarType.value = value.avatarType
  },
  { immediate: true },
)

onMounted(async () => {
  if (!authStore.profile) {
    try {
      await authStore.loadProfile()
    } catch {
      authStore.clearAuthentication()
      await router.replace('/login')
    }
  }
})

async function save(): Promise<void> {
  if (submitting.value) return
  submitting.value = true
  successMessage.value = null
  try {
    await authStore.updateProfile({ nickname: nickname.value, avatarType: avatarType.value })
    successMessage.value = '프로필을 저장했습니다.'
  } catch {
    // The store exposes the API-safe message.
  } finally {
    submitting.value = false
  }
}

async function logout(): Promise<void> {
  if (authStore.logoutInProgress) return
  try {
    await authStore.logout()
    await router.replace('/login')
  } catch {
    // The store keeps the current session and exposes a safe retry message.
  }
}
</script>

<template>
  <main class="profile-page">
    <section v-if="profile" class="profile-card" aria-labelledby="profile-title">
      <div class="profile-heading">
        <div>
          <p class="eyebrow">MY PROFILE</p>
          <h1 id="profile-title">{{ profile.nickname }}</h1>
          <p class="auth-copy">Phase 2에서는 인증 사용자 정보와 프로필 수정까지만 제공합니다.</p>
        </div>
        <div class="rating-badge">
          <span>CLASSIC RATING</span>
          <strong>{{ profile.ratingScore }}</strong>
        </div>
      </div>

      <form class="profile-form" @submit.prevent="save">
        <label>
          이메일
          <input :value="profile.email" type="email" readonly />
        </label>
        <label>
          닉네임
          <input v-model="nickname" required minlength="2" maxlength="20" pattern="[가-힣A-Za-z0-9_]+" />
        </label>

        <fieldset>
          <legend>아바타</legend>
          <div class="avatar-grid">
            <label v-for="avatar in AVATARS" :key="avatar" class="avatar-option">
              <input v-model="avatarType" type="radio" name="avatarType" :value="avatar" />
              <span>{{ avatar.replace('DEFAULT_', 'Avatar ') }}</span>
            </label>
          </div>
        </fieldset>

        <div class="record-grid">
          <article>
            <span>CLASSIC</span>
            <strong>{{ profile.classicRecord.wins }}승 {{ profile.classicRecord.losses }}패 {{ profile.classicRecord.draws }}무</strong>
            <small>{{ profile.classicRecord.totalGames }}전</small>
          </article>
          <article>
            <span>SPEED</span>
            <strong>{{ profile.speedRecord.wins }}승 {{ profile.speedRecord.losses }}패 {{ profile.speedRecord.draws }}무</strong>
            <small>{{ profile.speedRecord.totalGames }}전</small>
          </article>
        </div>

        <p v-if="authStore.lastError" class="form-error" role="alert">{{ authStore.lastError }}</p>
        <p v-if="successMessage" class="form-success" role="status">{{ successMessage }}</p>
        <div class="profile-actions">
          <button class="primary-button" type="submit" :disabled="submitting">
            {{ submitting ? '저장 중…' : '프로필 저장' }}
          </button>
          <button
            class="secondary-button"
            type="button"
            :disabled="authStore.logoutInProgress"
            @click="logout"
          >
            {{ authStore.logoutInProgress ? '로그아웃 중…' : '로그아웃' }}
          </button>
        </div>
      </form>
    </section>
    <section v-else class="profile-card">
      <p>프로필을 불러오는 중입니다.</p>
    </section>
  </main>
</template>

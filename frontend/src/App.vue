<script setup lang="ts">
import { computed } from 'vue'

import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()
const authenticated = computed(() => authStore.authStatus === 'AUTHENTICATED')
</script>

<template>
  <div class="app-shell">
    <header class="app-header">
      <RouterLink class="app-header__brand" to="/">
        <span class="app-header__mark" aria-hidden="true">RT</span>
        <span class="app-header__name">Realtime Tile Game</span>
      </RouterLink>
      <nav class="app-header__nav" aria-label="주요 메뉴">
        <RouterLink to="/health">상태 확인</RouterLink>
        <RouterLink v-if="authenticated" to="/lobby">로비</RouterLink>
        <RouterLink v-if="authenticated" to="/profile">내 프로필</RouterLink>
        <RouterLink v-else to="/login">로그인</RouterLink>
        <span class="app-header__phase">Phase 4</span>
      </nav>
    </header>

    <RouterView />
  </div>
</template>

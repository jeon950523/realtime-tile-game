<script setup lang="ts">
import type { RackSortMode } from '@/types/rackPresentation'

defineProps<{
  sortMode: RackSortMode
  disabled?: boolean
}>()

defineEmits<{
  sort777: []
  sort789: []
  restore: []
}>()

const modeLabel: Record<RackSortMode, string> = {
  SERVER: '원래 순서',
  MANUAL: '직접 정렬',
  GROUP_777: '777 정렬',
  RUN_789: '789 정렬',
}
</script>

<template>
  <aside class="rack-toolbar" aria-label="Rack 자동 정렬">
    <button
      type="button"
      aria-label="789 색상별 연속 숫자 순서로 정렬"
      :aria-pressed="sortMode === 'RUN_789'"
      :disabled="disabled"
      @click="$emit('sort789')"
    >
      <strong>789</strong>
      <span>색상 · 숫자</span>
    </button>
    <button
      type="button"
      aria-label="777 같은 숫자와 색상 순서로 정렬"
      :aria-pressed="sortMode === 'GROUP_777'"
      :disabled="disabled"
      @click="$emit('sort777')"
    >
      <strong>777</strong>
      <span>숫자 · 색상</span>
    </button>
    <button
      class="rack-toolbar__restore"
      type="button"
      aria-label="마지막 서버 수신 순서로 복원"
      :aria-pressed="sortMode === 'SERVER'"
      :disabled="disabled"
      @click="$emit('restore')"
    >
      <strong>↺</strong>
      <span>원래 순서</span>
    </button>
    <p role="status">현재: {{ modeLabel[sortMode] }}</p>
  </aside>
</template>


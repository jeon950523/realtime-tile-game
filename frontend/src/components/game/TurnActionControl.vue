<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  poolCount: number
  canDraw: boolean
  canPass: boolean
  commandInProgress: boolean
  isMyTurn: boolean
  drawIcon?: string
  passIcon?: string
  hasDraft?: boolean
  canCommit?: boolean
  canUndo?: boolean
  commitReason?: string | null
}>()

const emit = defineEmits<{
  draw: []
  pass: []
  commit: []
  undo: []
  cancel: []
}>()

const showsDraw = computed(() => props.poolCount > 0)
const actionDisabled = computed(() => props.hasDraft
  ? !props.canCommit
  : showsDraw.value ? !props.canDraw : !props.canPass)
const actionLabel = computed(() => {
  if (props.commandInProgress) return '처리 중'
  if (props.hasDraft) return '턴 확정'
  if (showsDraw.value) return 'Draw'
  return 'PASS'
})
const iconStyle = computed(() => {
  const icon = showsDraw.value ? props.drawIcon : props.passIcon
  return icon ? { backgroundImage: `url(${icon})` } : undefined
})

function act(): void {
  if (actionDisabled.value) return
  if (props.hasDraft) emit('commit')
  else if (showsDraw.value) emit('draw')
  else emit('pass')
}
</script>

<template>
  <aside class="turn-action" :class="{ 'turn-action--my-turn': isMyTurn }" aria-label="턴 행동">
    <button
      type="button"
      :data-action="hasDraft ? 'commit' : showsDraw ? 'draw' : 'pass'"
      :aria-label="hasDraft ? 'TurnDraft를 서버에 확정' : showsDraw ? `타일 한 장 Draw. Pool ${poolCount}개` : 'Pool이 비어 있어 PASS'"
      :disabled="actionDisabled"
      @click="act"
    >
      <span class="turn-action__icon" :style="hasDraft ? undefined : iconStyle" aria-hidden="true">{{ hasDraft ? '✓' : showsDraw ? '+' : '↷' }}</span>
      <strong>{{ actionLabel }}</strong>
      <span class="turn-action__pool">{{ showsDraw ? `▣ ${poolCount}` : 'Pool 0' }}</span>
    </button>
    <div v-if="hasDraft" class="turn-action__draft-tools">
      <button type="button" :disabled="commandInProgress || !canUndo" @click="emit('undo')">Undo</button>
      <button type="button" :disabled="commandInProgress" @click="emit('cancel')">Cancel</button>
    </div>
    <small v-if="hasDraft && commitReason">{{ commitReason }}</small>
    <small v-if="!isMyTurn">상대 턴에도 Rack 정렬은 가능합니다.</small>
    <small v-else-if="commandInProgress">서버 응답을 기다리는 중입니다.</small>
  </aside>
</template>

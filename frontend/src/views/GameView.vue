<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import GameBoard from '@/components/game/GameBoard.vue'
import CommittedTableBoard from '@/components/game/CommittedTableBoard.vue'
import GameDebugPanel from '@/components/game/GameDebugPanel.vue'
import PlayerSeat from '@/components/game/PlayerSeat.vue'
import RackToolbar from '@/components/game/RackToolbar.vue'
import TileRack from '@/components/game/TileRack.vue'
import TurnPreviewBoard from '@/components/game/TurnPreviewBoard.vue'
import TurnActionControl from '@/components/game/TurnActionControl.vue'
import WorkingTableBoard from '@/components/game/WorkingTableBoard.vue'
import { useRackPresentation } from '@/composables/game/useRackPresentation'
import { useWorkingTable } from '@/composables/game/useWorkingTable'
import { gameAssets } from '@/config/gameAssets'
import { buildRackAdjacentGroups } from '@/domain/game/rackVisualGroups'
import { isRackInteractionUnavailable, isRackToolbarDisabled } from '@/domain/game/rackInteractionPolicy'
import { useAuthStore } from '@/stores/auth'
import { useGameStore } from '@/stores/game'
import { useRoomStore } from '@/stores/room'
import type { RackDraftDrop, RackDropTarget } from '@/types/turnDraft'

const authStore = useAuthStore()
const gameStore = useGameStore()
const roomStore = useRoomStore()
const route = useRoute()
const router = useRouter()
const gameId = Number(route.params.gameId)
const nowMs = ref(Date.now())
const rackDropPreview = ref<{ target: RackDropTarget; tileCount: number } | null>(null)
const exitConfirmationOpen = ref(false)
let terminalNavigationInProgress = false
interface WorkingTableBoardApi {
  resolveRackDropTarget: (clientX: number, clientY: number, tileCount?: number) => RackDropTarget
  finishExternalRackDrag: () => void
}
const workingTableBoardRef = ref<WorkingTableBoardApi | null>(null)
let countdownTimer: number | null = null
let previewWasPublished = false

const state = computed(() => gameStore.privateState)
const publicState = computed(() => state.value?.publicState ?? null)
const authoritativeRack = computed(() => state.value?.myRack ?? [])
const rackSyncRevision = computed(() => gameStore.rackSyncRevision)
const rackSyncSource = computed(() => gameStore.rackSyncSource)
const drawMotionRevision = computed(() => gameStore.drawMotionRevision)
const drawMotionTileIds = computed(() => gameStore.drawMotionTileIds)
const authoritativeVersion = computed(() => publicState.value?.gameVersion ?? -1)
const tableMelds = computed(() => publicState.value?.tableMelds ?? [])
const initialMeldCompleted = computed(() => Boolean(
  publicState.value?.players.find((player) => player.userId === state.value?.myUserId)?.initialMeldCompleted,
))
const currentTurnPlayer = computed(() => publicState.value?.players.find((player) => player.currentTurn) ?? null)
const myPlayer = computed(() => publicState.value?.players.find((player) => player.userId === state.value?.myUserId) ?? null)
const isMyTurn = computed(() => Boolean(state.value && publicState.value?.currentTurnUserId === state.value.myUserId))
const activeGameId = computed(() => publicState.value?.gameId ?? null)
const currentTurnPlayerId = computed(() => publicState.value?.currentTurnUserId ?? null)
const playerCount = computed(() => publicState.value?.players.length ?? 0)

const turnDraft = useWorkingTable({
  authoritativeRack,
  authoritativeVersion,
  authoritativeSyncRevision: rackSyncRevision,
  tableMelds,
  initialMeldCompleted,
  isMyTurn,
  gameId: activeGameId,
  currentTurnPlayerId,
})

const {
  displayedTiles,
  displayedSlots,
  displayOrderIds,
  serverOrderSnapshot,
  sortMode,
  dragState,
  motionActive,
  enteringTileIds,
  invariantPreserved,
  applySort,
  restoreServerOrder,
  beginDrag,
  upgradeDrag,
  previewDrag,
  finishDrag,
} = useRackPresentation({
  authoritativeRack,
  rackSyncRevision,
  rackSyncSource,
  drawMotionRevision,
  drawMotionTileIds,
  excludedTileIds: turnDraft.draftTileIds,
})

const activeDragTileIds = computed(() => dragState.value?.activeDragTileIds ?? [])
const rackInteractionLocked = computed(() => motionActive.value || enteringTileIds.value.length > 0)
const visibleDisplayedTiles = displayedTiles
const visualGroups = computed(() => buildRackAdjacentGroups(
  visibleDisplayedTiles.value,
  displayedSlots.value,
  sortMode.value,
  serverOrderSnapshot.value,
))
const remainingSeconds = computed(() => {
  const deadline = publicState.value?.turnDeadlineAt
  if (!deadline) return 0
  const remainingMs = new Date(deadline).getTime() - nowMs.value
  return Math.max(0, Math.ceil(remainingMs / 1_000))
})
const canDraw = computed(() => Boolean(
  publicState.value?.status === 'IN_PROGRESS'
  && isMyTurn.value
  && publicState.value.tilePoolCount > 0
  && gameStore.gameCommandReady
  && !gameStore.commandInProgress,
))
const canPass = computed(() => Boolean(
  publicState.value?.status === 'IN_PROGRESS'
  && isMyTurn.value
  && publicState.value.tilePoolCount === 0
  && gameStore.gameCommandReady
  && !gameStore.commandInProgress,
))
const canEditDraft = computed(() => Boolean(
  publicState.value?.status === 'IN_PROGRESS'
  && isMyTurn.value
  && gameStore.gameCommandReady
  && !gameStore.commandInProgress,
))
const rackPolicyState = computed(() => ({
  gameStatus: publicState.value?.status ?? null,
  commandInProgress: gameStore.commandInProgress,
  reconnectRecoveryInProgress: gameStore.reconnectRecoveryInProgress,
  privateStateLoaded: gameStore.privateStateLoaded,
  motionActive: motionActive.value,
  enteringTileCount: enteringTileIds.value.length,
}))
const rackInteractionUnavailable = computed(() => isRackInteractionUnavailable(rackPolicyState.value))
const rackToolbarDisabled = computed(() => isRackToolbarDisabled(rackPolicyState.value, Boolean(dragState.value)))
const canCommit = computed(() => Boolean(
  canEditDraft.value
  && turnDraft.draft.value
  && turnDraft.hasChanges.value
  && turnDraft.validation.value.canCommit
  && turnDraft.draft.value.baseline.baseVersion === publicState.value?.gameVersion
  && turnDraft.authoritativeBaselineCurrent.value
  && !dragState.value
  && !rackInteractionLocked.value,
))

const opponentSeats = computed(() => {
  const currentState = state.value
  const players = publicState.value?.players ?? []
  if (!currentState) return []
  const playerCount = players.length
  const opponents = players
    .filter((player) => player.userId !== currentState.myUserId)
    .sort((left, right) => {
      const leftOffset = (left.seatOrder - currentState.mySeatOrder + playerCount) % playerCount
      const rightOffset = (right.seatOrder - currentState.mySeatOrder + playerCount) % playerCount
      return leftOffset - rightOffset
    })
  const positions: Array<'left' | 'center' | 'right'> = opponents.length === 1
    ? ['center']
    : opponents.length === 2
      ? ['left', 'right']
      : ['left', 'center', 'right']
  return opponents.map((player, index) => ({ player, position: positions[index]! }))
})

function resolveDropTarget(
  clientX: number,
  clientY: number,
  activeDragTileIds: readonly string[] = [],
): RackDropTarget {
  return workingTableBoardRef.value?.resolveRackDropTarget(
    clientX,
    clientY,
    Math.max(1, activeDragTileIds.length),
  ) ?? { kind: 'INVALID' }
}

function handleDraftDrop(drop: RackDraftDrop): void {
  workingTableBoardRef.value?.finishExternalRackDrag()
  if (!canEditDraft.value) return
  if (drop.target.kind === 'WORKING_NEW_MELD') {
    turnDraft.addAsNewMeld(
      drop.activeDragTileIds,
      displayOrderIds.value,
      drop.target.gridRow,
      drop.target.gridColumn,
    )
  } else if (drop.target.kind === 'WORKING_EXISTING_MELD') {
    turnDraft.addToMeld(
      drop.target.meldId,
      drop.activeDragTileIds,
      displayOrderIds.value,
      drop.target.targetIndex,
    )
  }
}

function handleRackDraftHover(target: RackDropTarget, tileIds: readonly string[]): void {
  if (target.kind === 'INVALID') workingTableBoardRef.value?.finishExternalRackDrag()
  rackDropPreview.value = canEditDraft.value && target.kind !== 'INVALID'
    ? { target, tileCount: tileIds.length }
    : null
}

const workingTablePreviewSignature = computed(() => {
  const draft = turnDraft.draft.value
  if (!draft) return ''
  return JSON.stringify(draft.placements.map((placement) => ({
    tileId: placement.tileId,
    gridRow: placement.gridRow,
    gridColumn: placement.gridColumn,
    source: placement.source,
  })))
})

function cancelDraft(): void {
  turnDraft.cancel()
}

function commitDraft(): void {
  if (!canCommit.value || !turnDraft.draft.value) return
  const actionId = gameStore.commitTurn(turnDraft.draft.value.placements)
  if (actionId) turnDraft.markCommitPending(actionId)
}

watch(() => gameStore.lastCommandReply, (reply) => {
  const current = turnDraft.draft.value
  if (!reply || reply.actionType !== 'COMMIT' || !current
    || reply.actionId !== current.pendingCommitActionId || reply.accepted) return
  const keepDraft = reply.code !== 'STALE_GAME_VERSION' && reply.code !== 'NOT_CURRENT_TURN'
  turnDraft.rejectCommit(keepDraft)
})

watch(() => gameStore.pendingRecoveryRevision, () => {
  const outcome = gameStore.pendingRecoveryOutcome
  if (outcome === 'RETRYABLE') turnDraft.rejectCommit(true)
  else if (outcome === 'AUTHORITATIVE_RESET' || outcome === 'COMMITTED') {
    turnDraft.rejectCommit(false)
  }
})

watch(() => gameStore.terminalRevision, async (revision) => {
  if (revision <= 0 || terminalNavigationInProgress) return
  terminalNavigationInProgress = true
  exitConfirmationOpen.value = false
  turnDraft.cancel()
  rackDropPreview.value = null
  roomStore.clearTerminatedRoom(gameStore.terminalRoomId)
  try {
    await roomStore.loadRooms()
    await roomStore.connectLobby()
    roomStore.lastMessage = gameStore.terminationNotice
    await router.replace('/lobby')
  } finally {
    terminalNavigationInProgress = false
  }
})

watch(workingTablePreviewSignature, () => {
  if (!isMyTurn.value || gameStore.commandInProgress || !turnDraft.draft.value) {
    previewWasPublished = false
    return
  }
  if (turnDraft.hasChanges.value) {
    if (gameStore.publishTurnPreview(turnDraft.draft.value.placements)) previewWasPublished = true
  } else if (previewWasPublished && gameStore.clearTurnPreview()) {
    previewWasPublished = false
  }
}, { flush: 'post' })

onMounted(async () => {
  document.body.classList.add('game-route-active')
  countdownTimer = window.setInterval(() => {
    nowMs.value = Date.now()
  }, 250)
  try {
    await gameStore.initialize(gameId)
  } catch {
    const active = await gameStore.loadActiveGame().catch(() => null)
    if (active?.active && active.gameId !== null) {
      await router.replace(`/games/${active.gameId}`)
      return
    }
    await router.replace('/lobby')
  }
})

onUnmounted(() => {
  if (previewWasPublished) gameStore.clearTurnPreview()
  gameStore.cancelPendingForRouteLeave()
  document.body.classList.remove('game-route-active')
  if (countdownTimer !== null) window.clearInterval(countdownTimer)
})

async function logout(): Promise<void> {
  try {
    await gameStore.disconnectRealtime()
    await roomStore.disconnectRealtime()
    await authStore.logout()
    gameStore.clearGameState()
    roomStore.clearRoomState()
    await router.replace('/login')
  } catch {
    // Stores expose safe errors and preserve the current state for retry.
  }
}

function openExitConfirmation(): void {
  if (publicState.value?.status !== 'IN_PROGRESS' || gameStore.commandInProgress) return
  exitConfirmationOpen.value = true
}

function cancelExitConfirmation(): void {
  if (gameStore.commandInProgress) return
  exitConfirmationOpen.value = false
}

function confirmExitActiveGame(): void {
  if (gameStore.commandInProgress) return
  if (gameStore.exitActiveGame()) exitConfirmationOpen.value = false
}
</script>

<template>
  <main class="game-page">
    <GameBoard
      v-if="state && publicState"
      :game-id="publicState.gameId"
      :game-mode="publicState.gameMode"
      :game-status="publicState.status"
      :connection-state="gameStore.connectionState"
      :is-my-turn="isMyTurn"
      :exit-pending="gameStore.pendingActionType === 'EXIT_ACTIVE_GAME'"
      :assets="gameAssets"
      @exit="openExitConfirmation"
      @logout="logout"
    >
      <template #opponents>
        <PlayerSeat
          v-for="seat in opponentSeats"
          :key="seat.player.userId"
          :player="seat.player"
          :position="seat.position"
          :remaining-seconds="remainingSeconds"
          :avatar-asset="gameAssets.defaultAvatar"
        />
      </template>

      <template #alerts>
        <p v-if="gameStore.lastError" class="game-toast game-toast--error" role="alert">{{ gameStore.lastError }}</p>
        <p v-if="gameStore.lastCommandError" class="game-toast game-toast--error" role="alert">{{ gameStore.lastCommandError }}</p>
        <p v-if="gameStore.lastMessage" class="game-toast game-toast--message" role="status">{{ gameStore.lastMessage }}</p>
        <p v-if="!invariantPreserved" class="game-toast game-toast--error" role="alert">
          Rack 표시 상태를 안전하게 복구하는 중입니다.
        </p>
        <p v-if="!turnDraft.partitionPreserved.value" class="game-toast game-toast--error" role="alert">
          Rack과 TurnDraft 파티션을 복구하는 중입니다.
        </p>
      </template>

      <template #table>
        <div class="game-table-content">
          <WorkingTableBoard
            v-if="isMyTurn && turnDraft.workingTable.value"
            ref="workingTableBoardRef"
            :placements="turnDraft.draft.value?.placements ?? []"
            :rack="state.myRack"
            :baseline-melds="turnDraft.workingTable.value.baseline.committedMelds"
            :validation="turnDraft.validation.value"
            :initial-meld-completed="initialMeldCompleted"
            :is-meld-editable="turnDraft.isMeldEditable"
            :disabled="gameStore.commandInProgress"
            :rack-drop-preview="rackDropPreview"
            @move-as-new="turnDraft.moveAsNewMeld"
            @move-meld="turnDraft.moveMeld"
            @move-to-meld="turnDraft.moveTile"
            @merge-meld="turnDraft.mergeMelds"
          />
          <TurnPreviewBoard
            v-else-if="gameStore.turnPreview"
            :preview="gameStore.turnPreview"
            :committed-melds="publicState.tableMelds"
            :turn-player-nickname="currentTurnPlayer?.nickname ?? '상대 플레이어'"
          />
          <CommittedTableBoard v-else :melds="publicState.tableMelds" />
        </div>
      </template>

      <template #toolbar>
        <RackToolbar
          :sort-mode="sortMode"
          :disabled="rackToolbarDisabled"
          @sort777="applySort('GROUP_777')"
          @sort789="applySort('RUN_789')"
          @restore="restoreServerOrder"
        />
      </template>

      <template #rack-heading>
        <div class="rack-heading">
          <p><span>나의 Rack</span><strong>{{ myPlayer?.nickname ?? `SEAT ${state.mySeatOrder}` }}</strong></p>
          <p v-if="isMyTurn" class="rack-heading__initial-score" :class="{ 'rack-heading__initial-score--complete': initialMeldCompleted }">
            <template v-if="initialMeldCompleted">
              첫 등록 완료<span v-if="turnDraft.hasChanges.value"> · 이번 제출 {{ turnDraft.validation.value.submissionScore }}점</span>
            </template>
            <template v-else>첫 등록 {{ turnDraft.validation.value.submissionScore }} / 30점</template>
          </p>
          <p class="rack-heading__turn" :class="{ 'rack-heading__turn--active': isMyTurn }">
            <b v-if="isMyTurn" class="rack-heading__badge">내 턴</b>
            <strong>{{ isMyTurn ? `${remainingSeconds}초` : currentTurnPlayer?.nickname }}</strong>
            <span>{{ isMyTurn ? '내 턴' : '현재 턴' }} · {{ visibleDisplayedTiles.length }} Rack<span v-if="turnDraft.draftTileIds.value.length"> + {{ turnDraft.draftTileIds.value.length }} Table</span></span>
          </p>
        </div>
      </template>

      <template #rack>
        <TileRack
          :tiles="visibleDisplayedTiles"
          :slots="displayedSlots"
          :active-drag-tile-ids="activeDragTileIds"
          :entering-tile-ids="enteringTileIds"
          :interaction-locked="rackInteractionUnavailable"
          :rack-texture="gameAssets.rackTexture"
          :visual-groups="visualGroups"
          :can-draft-drop="canEditDraft"
          :is-my-turn="isMyTurn"
          :resolve-drop-target="resolveDropTarget"
          @drag-start="beginDrag"
          @drag-upgrade="upgradeDrag"
          @drag-preview="previewDrag"
          @drag-finish="finishDrag"
          @draft-drop="handleDraftDrop"
          @draft-hover="handleRackDraftHover"
          @working-tile-return="turnDraft.returnTile"
        />
      </template>

      <template #action>
        <TurnActionControl
          :pool-count="publicState.tilePoolCount"
          :can-draw="canDraw"
          :can-pass="canPass"
          :command-in-progress="gameStore.commandInProgress"
          :is-my-turn="isMyTurn"
          :draw-icon="gameAssets.drawIcon"
          :pass-icon="gameAssets.passIcon"
          :has-draft="turnDraft.hasChanges.value"
          :can-commit="canCommit"
          :can-undo="Boolean(turnDraft.draft.value?.history.length)"
          :commit-reason="turnDraft.validation.value.reason"
          @draw="gameStore.drawTile"
          @pass="gameStore.passTurn"
          @commit="commitDraft"
          @undo="turnDraft.undo"
          @cancel="cancelDraft"
        />
      </template>

      <template #debug>
        <GameDebugPanel
          :public-state="publicState"
          :my-seat-order="state.mySeatOrder"
          :connection-state="gameStore.connectionState"
        />
      </template>
    </GameBoard>

    <div
      v-if="exitConfirmationOpen"
      class="game-exit-modal"
      role="dialog"
      aria-modal="true"
      aria-labelledby="game-exit-title"
    >
      <section class="game-exit-modal__panel">
        <h2 id="game-exit-title">게임 포기 및 나가기</h2>
        <p v-if="playerCount === 2">
          게임을 포기하고 나가면 상대방의 승리로 종료됩니다.<br>
          이 게임에는 다시 참여할 수 없습니다.
        </p>
        <p v-else>
          게임을 포기하고 나가면 현재 게임이 중단되고 방이 종료됩니다.<br>
          모든 참가자는 로비로 이동해 새 방을 만들 수 있습니다.
        </p>
        <div class="game-exit-modal__actions">
          <button type="button" :disabled="gameStore.commandInProgress" @click="cancelExitConfirmation">취소</button>
          <button
            type="button"
            class="game-exit-modal__confirm"
            :disabled="gameStore.commandInProgress"
            @click="confirmExitActiveGame"
          >
            {{ gameStore.commandInProgress ? '처리 중…' : '포기하고 나가기' }}
          </button>
        </div>
      </section>
    </div>

    <section v-if="!state || !publicState" class="game-loading" role="status">
      <span aria-hidden="true">RT</span>
      <p>게임 상태를 복구하는 중입니다.</p>
    </section>
  </main>
</template>

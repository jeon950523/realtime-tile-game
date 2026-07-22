<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

import GameTile from '@/components/game/GameTile.vue'
import { computeAdaptiveRackLayout } from '@/domain/game/rackLayout'
import type { GameRackTile } from '@/types/game'
import type { RackDraftDrop, RackDropTarget, RackVisualGroup } from '@/types/turnDraft'

interface DisplayedRackSlot {
  slotIndex: number
  tileId: string | null
  tile: GameRackTile | null
}

const props = defineProps<{
  tiles: readonly GameRackTile[]
  slots?: readonly DisplayedRackSlot[]
  activeDragTileIds: readonly string[]
  enteringTileIds: readonly string[]
  interactionLocked?: boolean
  rackTexture?: string
  visualGroups?: readonly RackVisualGroup[]
  canDraftDrop?: boolean
  isMyTurn?: boolean
  resolveDropTarget?: (
    clientX: number,
    clientY: number,
    activeDragTileIds: readonly string[],
  ) => RackDropTarget
}>()

const emit = defineEmits<{
  dragStart: [activeDragTileIds: readonly string[]]
  dragUpgrade: [activeDragTileIds: readonly string[]]
  dragPreview: [targetIndex: number]
  dragFinish: [validDrop: boolean]
  draftDrop: [drop: RackDraftDrop]
  draftHover: [target: RackDropTarget, activeDragTileIds: readonly string[]]
  workingTileReturn: [tileId: string]
}>()

const rackElement = ref<HTMLElement | null>(null)
const rackContentWidth = ref(870)
const overlayTiles = ref<GameRackTile[]>([])
const localActiveDragTileIds = ref<string[]>([])
const ghostPosition = ref({ x: 0, y: 0 })
const ghostSize = ref({ width: 0, height: 0 })
const ghostTileSize = ref({ width: 0, height: 0 })
const ghostSettling = ref(false)
const ghostReturning = ref(false)
let activePointerId: number | null = null
let lastPositionWasValid = true
let pointerFrame: number | null = null
let settleFrame: number | null = null
let settleTimer: number | null = null
let lastTargetIndex: number | null = null
let originalIndex: number | null = null
let holdTimer: number | null = null
let anchorTileId: string | null = null
let pointerDownPosition = { clientX: 0, clientY: 0 }
let lastPointerPosition = { clientX: 0, clientY: 0 }
let ghostPointerOffset = { x: 0, y: 0 }
let slotGeometry: SlotGeometry[] = []
let resizeObserver: ResizeObserver | null = null
let lastDraftHoverKey = 'INVALID'

interface SlotGeometry {
  index: number
  row: number
  column: number
  centerX: number
  centerY: number
  width: number
  height: number
}

const requestFrame = (callback: FrameRequestCallback): number => window.requestAnimationFrame(callback)
const cancelFrame = (handle: number): void => window.cancelAnimationFrame(handle)
const HOLD_DURATION_MS = 320
const HOLD_MOVEMENT_THRESHOLD_PX = 6
const GROUP_GAP_PX = 5

const renderSlots = computed<readonly DisplayedRackSlot[]>(() => (
  props.slots ?? props.tiles.map((tile, slotIndex) => ({
    slotIndex,
    tileId: tile.tileId,
    tile,
  }))
))

const overlayStyle = computed(() => ({
  width: `${ghostSize.value.width}px`,
  height: `${ghostSize.value.height}px`,
  transform: `translate3d(${ghostPosition.value.x}px, ${ghostPosition.value.y}px, 0)`,
}))

const adaptiveLayout = computed(() => computeAdaptiveRackLayout(renderSlots.value.length, rackContentWidth.value))
const rackStyle = computed(() => ({
  ...(props.rackTexture ? { backgroundImage: `url(${props.rackTexture})` } : {}),
  '--rack-columns': adaptiveLayout.value.columns,
  '--rack-tile-width': `${adaptiveLayout.value.tileWidth}px`,
  '--rack-tile-height': `${adaptiveLayout.value.tileHeight}px`,
  '--rack-tile-gap': `${adaptiveLayout.value.gap}px`,
  '--rack-height': `${adaptiveLayout.value.rackHeight}px`,
}))

function clearPointerFrame(): void {
  if (pointerFrame !== null) cancelFrame(pointerFrame)
  pointerFrame = null
}

function clearSettleWork(): void {
  if (settleFrame !== null) cancelFrame(settleFrame)
  if (settleTimer !== null) window.clearTimeout(settleTimer)
  settleFrame = null
  settleTimer = null
}

function clearHoldTimer(): void {
  if (holdTimer !== null) window.clearTimeout(holdTimer)
  holdTimer = null
}

function clearVisualDrag(): void {
  clearHoldTimer()
  clearSettleWork()
  overlayTiles.value = []
  localActiveDragTileIds.value = []
  ghostSettling.value = false
  ghostReturning.value = false
  slotGeometry = []
  lastTargetIndex = null
  originalIndex = null
  anchorTileId = null
}

function draftHoverKey(target: RackDropTarget): string {
  if (target.kind === 'WORKING_NEW_MELD') return `${target.kind}:${target.gridRow}:${target.gridColumn}`
  if (target.kind === 'WORKING_EXISTING_MELD') return `${target.kind}:${target.meldId}:${target.targetIndex ?? -1}`
  return target.kind
}

function emitDraftHover(target: RackDropTarget): void {
  const nextKey = draftHoverKey(target)
  if (nextKey === lastDraftHoverKey) return
  lastDraftHoverKey = nextKey
  emit('draftHover', target, [...localActiveDragTileIds.value])
}

function snapshotSlotGeometry(): SlotGeometry[] {
  const elements = [...(rackElement.value?.querySelectorAll<HTMLElement>('.rack-tile-slot') ?? [])]
  let row = -1
  let column = 0
  let lastTop: number | null = null

  return elements.map((element, fallbackIndex) => {
    const rect = element.getBoundingClientRect()
    if (lastTop === null || Math.abs(rect.top - lastTop) > rect.height / 2) {
      row += 1
      column = 0
      lastTop = rect.top
    }
    const geometry = {
      index: Number(element.dataset.rackIndex ?? fallbackIndex),
      row,
      column,
      centerX: rect.left + rect.width / 2,
      centerY: rect.top + rect.height / 2,
      width: rect.width,
      height: rect.height,
    }
    column += 1
    return geometry
  })
}

function startDrag(tileId: string, event: PointerEvent): void {
  if (props.interactionLocked || activePointerId !== null) return
  const source = event.currentTarget as HTMLElement | null
  const sourceRect = source?.getBoundingClientRect()
  const nextActiveDragTileIds = [tileId]
  const nextOverlayTiles = props.tiles.filter((candidate) => nextActiveDragTileIds.includes(candidate.tileId))
  if (!sourceRect || nextOverlayTiles.length === 0) return

  clearVisualDrag()
  lastDraftHoverKey = 'INVALID'
  slotGeometry = snapshotSlotGeometry()
  originalIndex = renderSlots.value.findIndex((slot) => slot.tileId === tileId)
  if (originalIndex < 0 || slotGeometry.length === 0) return

  activePointerId = event.pointerId
  lastPositionWasValid = true
  lastTargetIndex = originalIndex
  lastPointerPosition = { clientX: event.clientX, clientY: event.clientY }
  pointerDownPosition = lastPointerPosition
  anchorTileId = tileId
  ghostPointerOffset = {
    x: event.clientX - sourceRect.left,
    y: event.clientY - sourceRect.top,
  }
  ghostSize.value = { width: sourceRect.width, height: sourceRect.height }
  ghostTileSize.value = { width: sourceRect.width, height: sourceRect.height }
  ghostPosition.value = {
    x: event.clientX - ghostPointerOffset.x,
    y: event.clientY - ghostPointerOffset.y,
  }
  localActiveDragTileIds.value = nextActiveDragTileIds
  overlayTiles.value = nextOverlayTiles
  rackElement.value?.setPointerCapture?.(event.pointerId)
  emit('dragStart', nextActiveDragTileIds)
  holdTimer = window.setTimeout(() => {
    holdTimer = null
    const visualGroup = props.visualGroups?.find((group) => group.tileIds.includes(tileId))
    if (!visualGroup || visualGroup.tileIds.length < 2 || activePointerId !== event.pointerId) return
    const byId = new Map(props.tiles.map((tile) => [tile.tileId, tile]))
    const groupTiles = visualGroup.tileIds
      .map((groupTileId) => byId.get(groupTileId))
      .filter((tile): tile is GameRackTile => tile !== undefined)
    if (groupTiles.length !== visualGroup.tileIds.length) return
    const anchorIndex = visualGroup.tileIds.indexOf(tileId)
    localActiveDragTileIds.value = [...visualGroup.tileIds]
    overlayTiles.value = groupTiles
    ghostPointerOffset.x += anchorIndex * (sourceRect.width + GROUP_GAP_PX)
    ghostSize.value = {
      width: sourceRect.width * groupTiles.length + GROUP_GAP_PX * (groupTiles.length - 1),
      height: sourceRect.height,
    }
    ghostPosition.value = {
      x: lastPointerPosition.clientX - ghostPointerOffset.x,
      y: lastPointerPosition.clientY - ghostPointerOffset.y,
    }
    emit('dragUpgrade', visualGroup.tileIds)
  }, HOLD_DURATION_MS)
  event.preventDefault()
}

function isInsideRack(clientX: number, clientY: number): boolean {
  const rect = rackElement.value?.getBoundingClientRect()
  if (!rect) return false
  const tolerance = 12
  return clientX >= rect.left - tolerance
    && clientX <= rect.right + tolerance
    && clientY >= rect.top - tolerance
    && clientY <= rect.bottom + tolerance
}

function resolveTargetIndex(clientX: number, clientY: number): number | null {
  if (slotGeometry.length === 0 || lastTargetIndex === null) return null
  const current = slotGeometry.find((slot) => slot.index === lastTargetIndex) ?? slotGeometry[0]!
  const rowCenters = [...new Map(slotGeometry.map((slot) => [slot.row, slot.centerY])).entries()]
  let targetRow = rowCenters.reduce((closest, candidate) => (
    Math.abs(clientY - candidate[1]) < Math.abs(clientY - closest[1]) ? candidate : closest
  ))[0]

  if (targetRow !== current.row) {
    const targetCenterY = rowCenters.find(([row]) => row === targetRow)?.[1] ?? current.centerY
    const direction = targetCenterY > current.centerY ? 1 : -1
    const boundary = (current.centerY + targetCenterY) / 2
    const deadZone = current.height * 0.18
    const crossedBoundary = direction > 0
      ? clientY >= boundary + deadZone
      : clientY <= boundary - deadZone
    if (!crossedBoundary) targetRow = current.row
  }

  const rowSlots = slotGeometry.filter((slot) => slot.row === targetRow)
  const candidate = rowSlots.reduce((closest, slot) => (
    Math.abs(clientX - slot.centerX) < Math.abs(clientX - closest.centerX) ? slot : closest
  ))

  if (candidate.index === current.index) return current.index
  if (candidate.row === current.row && Math.abs(candidate.column - current.column) === 1) {
    const direction = candidate.centerX > current.centerX ? 1 : -1
    const boundary = (current.centerX + candidate.centerX) / 2
    const deadZone = Math.min(current.width, candidate.width) * 0.18
    const crossedBoundary = direction > 0
      ? clientX >= boundary + deadZone
      : clientX <= boundary - deadZone
    if (!crossedBoundary) return current.index
  }

  return candidate.index
}

function processPointerFrame(): void {
  pointerFrame = null
  if (activePointerId === null || overlayTiles.value.length === 0) return
  const { clientX, clientY } = lastPointerPosition
  ghostPosition.value = {
    x: clientX - ghostPointerOffset.x,
    y: clientY - ghostPointerOffset.y,
  }
  lastPositionWasValid = isInsideRack(clientX, clientY)
  if (!lastPositionWasValid) {
    const target: RackDropTarget = props.canDraftDrop && props.resolveDropTarget
      ? props.resolveDropTarget(clientX, clientY, localActiveDragTileIds.value)
      : { kind: 'INVALID' }
    emitDraftHover(target)
    return
  }
  emitDraftHover({ kind: 'INVALID' })
  if (localActiveDragTileIds.value.length !== 1) return
  const targetIndex = resolveTargetIndex(clientX, clientY)
  if (targetIndex === null || targetIndex === lastTargetIndex) return
  lastTargetIndex = targetIndex
  emit('dragPreview', targetIndex)
}

function handlePointerMove(event: PointerEvent): void {
  if (event.pointerId !== activePointerId) return
  event.preventDefault()
  lastPointerPosition = { clientX: event.clientX, clientY: event.clientY }
  if (holdTimer !== null && Math.hypot(
    event.clientX - pointerDownPosition.clientX,
    event.clientY - pointerDownPosition.clientY,
  ) > HOLD_MOVEMENT_THRESHOLD_PX) clearHoldTimer()
  if (pointerFrame === null) pointerFrame = requestFrame(processPointerFrame)
}

function settleGhost(validDrop: boolean, externalTarget?: RackDropTarget): void {
  const settleIndex = validDrop ? lastTargetIndex : originalIndex
  const target = slotGeometry.find((slot) => slot.index === settleIndex)
  const targetRect = externalTarget && externalTarget.kind !== 'INVALID' ? externalTarget.rect : undefined
  if ((!target && !targetRect) || overlayTiles.value.length === 0) {
    clearVisualDrag()
    return
  }
  ghostSettling.value = true
  ghostReturning.value = !validDrop && !targetRect
  settleFrame = requestFrame(() => {
    settleFrame = null
    ghostPosition.value = {
      x: targetRect
        ? targetRect.left + targetRect.width / 2 - ghostSize.value.width / 2
        : target!.centerX - ghostSize.value.width / 2,
      y: targetRect
        ? targetRect.top + targetRect.height / 2 - ghostSize.value.height / 2
        : target!.centerY - ghostSize.value.height / 2,
    }
    settleTimer = window.setTimeout(clearVisualDrag, validDrop || targetRect ? 130 : 170)
  })
}

function finishActiveDrag(cancelled: boolean, position = lastPointerPosition, releaseCapture = true): void {
  if (activePointerId === null) return
  const pointerId = activePointerId
  clearPointerFrame()
  clearHoldTimer()
  lastPointerPosition = position
  ghostPosition.value = {
    x: position.clientX - ghostPointerOffset.x,
    y: position.clientY - ghostPointerOffset.y,
  }
  const insideRack = isInsideRack(position.clientX, position.clientY)
  lastPositionWasValid = insideRack
  if (!cancelled && insideRack && localActiveDragTileIds.value.length === 1) {
    const targetIndex = resolveTargetIndex(position.clientX, position.clientY)
    if (targetIndex !== null && targetIndex !== lastTargetIndex) {
      lastTargetIndex = targetIndex
      emit('dragPreview', targetIndex)
    }
  }
  let draftTarget: RackDropTarget = { kind: 'INVALID' }
  if (!cancelled && !insideRack && props.canDraftDrop && props.resolveDropTarget) {
    draftTarget = props.resolveDropTarget(
      position.clientX,
      position.clientY,
      localActiveDragTileIds.value,
    )
  }
  emitDraftHover({ kind: 'INVALID' })
  const validRackDrop = !cancelled && insideRack && localActiveDragTileIds.value.length === 1
  const activeIds = [...localActiveDragTileIds.value]
  activePointerId = null
  if (releaseCapture && rackElement.value?.hasPointerCapture?.(pointerId)) {
    rackElement.value.releasePointerCapture(pointerId)
  }
  if (draftTarget.kind !== 'INVALID') {
    emit('draftDrop', { activeDragTileIds: activeIds, target: draftTarget })
  }
  emit('dragFinish', validRackDrop)
  settleGhost(validRackDrop, draftTarget.kind !== 'INVALID' ? draftTarget : undefined)
}

function finishPointer(event: PointerEvent, cancelled = false): void {
  if (event.pointerId !== activePointerId) return
  finishActiveDrag(cancelled, { clientX: event.clientX, clientY: event.clientY })
}

function handleLostPointerCapture(event: PointerEvent): void {
  if (event.pointerId !== activePointerId) return
  finishActiveDrag(true, lastPointerPosition, false)
}

function handleWindowBlur(): void {
  finishActiveDrag(true)
}

function handleWindowPointerUp(event: PointerEvent): void {
  finishPointer(event)
}

function handleWindowPointerCancel(event: PointerEvent): void {
  finishPointer(event, true)
}

function dropWorkingTileToRack(event: DragEvent): void {
  if (!props.canDraftDrop || !event.dataTransfer) return
  const tileId = event.dataTransfer.getData('application/x-working-tile-id')
  if (tileId) emit('workingTileReturn', tileId)
}

onMounted(() => {
  if (rackElement.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(([entry]) => {
      if (entry) rackContentWidth.value = Math.max(320, entry.contentRect.width - 28)
    })
    resizeObserver.observe(rackElement.value)
  } else if (rackElement.value) {
    rackContentWidth.value = Math.max(320, rackElement.value.getBoundingClientRect().width - 28)
  }
  window.addEventListener('blur', handleWindowBlur)
  window.addEventListener('pointermove', handlePointerMove, { passive: false })
  window.addEventListener('pointerup', handleWindowPointerUp)
  window.addEventListener('pointercancel', handleWindowPointerCancel)
})

onBeforeUnmount(() => {
  resizeObserver?.disconnect()
  resizeObserver = null
  window.removeEventListener('blur', handleWindowBlur)
  window.removeEventListener('pointermove', handlePointerMove)
  window.removeEventListener('pointerup', handleWindowPointerUp)
  window.removeEventListener('pointercancel', handleWindowPointerCancel)
  clearPointerFrame()
  clearHoldTimer()
  clearVisualDrag()
  activePointerId = null
})
</script>

<template>
  <section
    ref="rackElement"
    class="tile-rack"
    :class="{
      'tile-rack--dragging': activeDragTileIds.length > 0 || localActiveDragTileIds.length > 0,
      'tile-rack--my-turn': isMyTurn,
    }"
    :style="rackStyle"
    :data-rack-columns="adaptiveLayout.columns"
    :data-rack-rows="adaptiveLayout.rows"
    aria-label="내 타일 Rack. 타일을 마우스로 끌어 순서를 변경할 수 있습니다."
    @pointermove="handlePointerMove"
    @pointerup="finishPointer($event)"
    @pointercancel="finishPointer($event, true)"
    @lostpointercapture="handleLostPointerCapture"
    @dragover.prevent
    @drop.stop="dropWorkingTileToRack"
  >
    <TransitionGroup name="rack-tile" tag="div" class="tile-rack__tiles">
      <div
        v-for="slot in renderSlots"
        :key="slot.tileId ?? `empty-slot-${slot.slotIndex}`"
        class="rack-tile-slot"
        :class="{ 'rack-tile-slot--empty': slot.tile === null }"
        :data-rack-index="slot.slotIndex"
        :data-slot-tile-id="slot.tileId ?? undefined"
        :data-empty-slot="slot.tile === null ? 'true' : undefined"
        :data-drag-placeholder="slot.tileId && localActiveDragTileIds.includes(slot.tileId) ? 'true' : undefined"
      >
        <GameTile
          v-if="slot.tile"
          :tile="slot.tile"
          :index="slot.slotIndex"
          :dragging="localActiveDragTileIds.includes(slot.tile.tileId)"
          :entering="enteringTileIds.includes(slot.tile.tileId)"
          :interaction-locked="interactionLocked || enteringTileIds.includes(slot.tile.tileId)"
          @pointerdown="startDrag"
        />
      </div>
    </TransitionGroup>
    <div class="tile-rack__rail tile-rack__rail--top" aria-hidden="true" />
    <div class="tile-rack__rail tile-rack__rail--bottom" aria-hidden="true" />
  </section>

  <Teleport to="body">
    <div
      v-if="overlayTiles.length > 0"
      class="rack-drag-overlay"
      :class="{
        'rack-drag-overlay--settling': ghostSettling,
        'rack-drag-overlay--returning': ghostReturning,
      }"
      :style="overlayStyle"
      :data-active-drag-count="localActiveDragTileIds.length"
      :data-anchor-tile-id="anchorTileId ?? undefined"
      aria-hidden="true"
    >
      <div
        v-for="(tile, ghostIndex) in overlayTiles"
        :key="tile.tileId"
        class="rack-drag-ghost"
        :style="{
          width: `${ghostTileSize.width}px`,
          height: `${ghostTileSize.height}px`,
          transform: `translate3d(${ghostIndex * (ghostTileSize.width + GROUP_GAP_PX)}px, 0, 0)`,
        }"
      >
        <GameTile :tile="tile" :index="lastTargetIndex ?? 0" ghost />
      </div>
    </div>
  </Teleport>
</template>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

import GameTile from '@/components/game/GameTile.vue'
import {
  TABLE_GRID_COLUMNS,
  TABLE_GRID_ROWS,
  TABLE_GRID_VISIBLE_ROWS,
  canPlaceTableTiles,
  clientPointToTableGridCoordinate,
  tableGridCellClientRect,
} from '@/domain/game/tableGrid'
import {
  createTableCoordinateResolver,
  tableContentRows,
  type TableCoordinateResolver,
} from '@/domain/game/tableFlow'
import { deriveTableCandidates } from '@/domain/game/tableCandidateDerivation'
import type { GameRackTile, GameTableMeld, GameTableTile } from '@/types/game'
import type {
  DerivedTableCandidate,
  RackDropTarget,
  TurnDraftValidation,
  WorkingTilePlacement,
} from '@/types/turnDraft'

type WorkingTile = GameRackTile | GameTableTile
type ReadonlyTableMeld = Omit<GameTableMeld, 'tiles'> & { readonly tiles: readonly GameTableTile[] }
type ActiveTableDrag = (
  { kind: 'TILE'; tileId: string } | { kind: 'MELD'; meldId: string }
) & { movingTileIds: readonly string[] }
type TableDropPreview = { gridRow: number; gridColumn: number; tileCount: number; valid: boolean }

const props = defineProps<{
  placements: readonly WorkingTilePlacement[]
  rack: readonly GameRackTile[]
  baselineMelds: readonly ReadonlyTableMeld[]
  validation: TurnDraftValidation
  initialMeldCompleted: boolean
  isMeldEditable: (candidateId: string) => boolean
  rackDropPreview?: { target: RackDropTarget; tileCount: number } | null
  disabled?: boolean
}>()

const emit = defineEmits<{
  moveAsNew: [tileId: string, gridRow?: number, gridColumn?: number]
  moveMeld: [candidateId: string, gridRow: number, gridColumn: number]
  moveToMeld: [tileId: string, targetCandidateId: string, targetIndex?: number]
  mergeMeld: [sourceCandidateId: string, targetCandidateId: string]
}>()

const scrollViewportElement = ref<HTMLElement | null>(null)
const gridCanvasElement = ref<HTMLElement | null>(null)
const activeTableDrag = ref<ActiveTableDrag | null>(null)
const dropPreview = ref<TableDropPreview | null>(null)
const externalPointerPreview = ref<TableDropPreview | null>(null)
let pendingTableDragPoint: { clientX: number; clientY: number } | null = null
let tableDragFrameId: number | null = null
let autoScrollFrameId: number | null = null
let edgeScrollDirection = 0
let externalRackTileCount = 0
let activeCoordinateResolver: TableCoordinateResolver | null = null
let externalCoordinateResolver: TableCoordinateResolver | null = null

const tileById = computed(() => {
  const tiles: WorkingTile[] = [...props.rack, ...props.baselineMelds.flatMap((meld) => meld.tiles)]
  return new Map(tiles.map((tile) => [tile.tileId, tile]))
})
const candidates = computed<readonly DerivedTableCandidate[]>(() => deriveTableCandidates(props.placements))
const contentRows = computed(() => tableContentRows(props.placements))
const candidateByTileId = computed(() => {
  const result = new Map<string, DerivedTableCandidate>()
  candidates.value.forEach((candidate) => {
    candidate.tileIds.forEach((tileId) => result.set(tileId, candidate))
  })
  return result
})
const placementByTileId = computed(() => new Map(
  props.placements.map((placement) => [placement.tileId, placement]),
))
const externalGridPreview = computed(() => {
  const preview = props.rackDropPreview
  if (!preview || preview.target.kind !== 'WORKING_NEW_MELD') return null
  const { gridRow, gridColumn } = preview.target
  const movingIds = Array.from({ length: preview.tileCount }, (_, index) => `rack-preview:${index}`)
  return {
    gridRow,
    gridColumn,
    tileCount: preview.tileCount,
    valid: canPlaceTableTiles(props.placements, movingIds, gridRow, gridColumn),
  }
})
const visibleDropPreview = computed(() => (
  dropPreview.value ?? externalPointerPreview.value ?? externalGridPreview.value
))

function tileStyle(placement: WorkingTilePlacement): Record<string, string> {
  return {
    '--table-grid-column': String(placement.gridColumn),
    '--table-grid-row': String(placement.gridRow),
  }
}

function candidateStyle(candidate: DerivedTableCandidate): Record<string, string> {
  return {
    '--table-grid-column': String(candidate.gridColumn),
    '--table-grid-row': String(candidate.gridRow),
    '--table-grid-span': String(Math.max(1, candidate.tileIds.length)),
  }
}

function canvasStyle(): Record<string, string> {
  return { '--table-content-rows': String(contentRows.value) }
}

function previewStyle(): Record<string, string> {
  const preview = visibleDropPreview.value
  if (!preview) return {}
  return {
    '--table-grid-column': String(preview.gridColumn),
    '--table-grid-row': String(preview.gridRow),
    '--table-grid-span': String(preview.tileCount),
  }
}

function coordinateFromClient(clientX: number, clientY: number) {
  const rect = gridCanvasElement.value?.getBoundingClientRect()
  return rect ? clientPointToTableGridCoordinate(rect, clientX, clientY, contentRows.value) : null
}

function candidateForTile(tileId: string): DerivedTableCandidate | null {
  return candidateByTileId.value.get(tileId) ?? null
}

function candidateKind(candidate: DerivedTableCandidate): 'RUN' | 'GROUP' | 'INVALID' {
  return props.validation.melds[candidate.clientMeldId]?.kind ?? 'INVALID'
}

function candidateEditable(candidate: DerivedTableCandidate): boolean {
  if (props.disabled || !props.isMeldEditable(candidate.clientMeldId)) return false
  if (!props.initialMeldCompleted) {
    const hasCommittedTile = candidate.tileIds.some((tileId) => (
      placementByTileId.value.get(tileId)?.source === 'COMMITTED_TABLE'
    ))
    if (hasCommittedTile) return false
  }
  return true
}

const editableCandidateIds = computed(() => new Set(candidates.value
  .filter((candidate) => candidateEditable(candidate))
  .map((candidate) => candidate.clientMeldId)))

const renderedPlacements = computed(() => props.placements.map((placement, index) => {
  const candidate = candidateByTileId.value.get(placement.tileId) ?? null
  return {
    placement,
    index,
    draggable: candidate !== null && editableCandidateIds.value.has(candidate.clientMeldId),
  }
}))


function sameDropPreview(left: TableDropPreview | null, right: TableDropPreview | null): boolean {
  if (left === right) return true
  return left !== null && right !== null
    && left.gridRow === right.gridRow
    && left.gridColumn === right.gridColumn
    && left.tileCount === right.tileCount
    && left.valid === right.valid
}

function assignDropPreview(next: TableDropPreview | null): TableDropPreview | null {
  if (!sameDropPreview(dropPreview.value, next)) dropPreview.value = next
  return next
}

function calculateTableDropPreview(clientX: number, clientY: number): TableDropPreview | null {
  const active = activeTableDrag.value
  const coordinate = coordinateFromClient(clientX, clientY)
  if (!active || !coordinate) return null
  const resolver = activeCoordinateResolver
    ?? createTableCoordinateResolver(props.placements, active.movingTileIds)
  const resolved = resolver.resolveInteractive(coordinate.gridRow, coordinate.gridColumn)
  return {
    gridRow: resolved?.gridRow ?? coordinate.gridRow,
    gridColumn: resolved?.gridColumn ?? coordinate.gridColumn,
    tileCount: active.movingTileIds.length,
    valid: resolved !== null,
  }
}

function candidateTargetAt(clientX: number, clientY: number): Extract<RackDropTarget, { kind: 'WORKING_EXISTING_MELD' }> | null {
  const boardRect = gridCanvasElement.value?.getBoundingClientRect()
  if (!boardRect) return null
  for (const candidate of candidates.value) {
    if (!candidateEditable(candidate)) continue
    const rect = tableGridCellClientRect(
      boardRect,
      candidate.gridRow,
      candidate.gridColumn,
      candidate.tileIds.length,
      contentRows.value,
    )
    if (!rect || clientX < rect.left || clientX > rect.left + rect.width
      || clientY < rect.top || clientY > rect.top + rect.height) continue
    const cellWidth = rect.width / Math.max(1, candidate.tileIds.length)
    const targetIndex = Math.max(0, Math.min(
      candidate.tileIds.length,
      Math.round((clientX - rect.left) / cellWidth),
    ))
    return {
      kind: 'WORKING_EXISTING_MELD',
      meldId: candidate.clientMeldId,
      targetIndex,
      rect,
    }
  }
  return null
}

function cancelTableDragFrame(): void {
  if (tableDragFrameId !== null) window.cancelAnimationFrame(tableDragFrameId)
  tableDragFrameId = null
}

function cancelAutoScroll(): void {
  if (autoScrollFrameId !== null) window.cancelAnimationFrame(autoScrollFrameId)
  autoScrollFrameId = null
  edgeScrollDirection = 0
}

function calculateExternalRackPreview(
  clientX: number,
  clientY: number,
  tileCount: number,
): TableDropPreview | null {
  const coordinate = coordinateFromClient(clientX, clientY)
  if (!coordinate || tileCount <= 0) return null
  if (!externalCoordinateResolver || externalRackTileCount !== tileCount) {
    const movingIds = Array.from({ length: tileCount }, (_, index) => `rack-pointer:${index}`)
    externalCoordinateResolver = createTableCoordinateResolver(props.placements, movingIds)
  }
  const resolved = externalCoordinateResolver.resolveInteractive(
    coordinate.gridRow,
    coordinate.gridColumn,
  )
  return resolved ? { ...resolved, tileCount, valid: true } : null
}

function runAutoScroll(): void {
  autoScrollFrameId = null
  const viewport = scrollViewportElement.value
  const hasActiveDrag = Boolean(activeTableDrag.value) || externalRackTileCount > 0
  if (!viewport || edgeScrollDirection === 0 || !hasActiveDrag) return
  const before = viewport.scrollTop
  viewport.scrollTop += edgeScrollDirection * 12
  if (viewport.scrollTop === before) {
    edgeScrollDirection = 0
    return
  }
  if (pendingTableDragPoint) {
    if (activeTableDrag.value) {
      assignDropPreview(calculateTableDropPreview(
        pendingTableDragPoint.clientX,
        pendingTableDragPoint.clientY,
      ))
    } else if (externalRackTileCount > 0) {
      externalPointerPreview.value = calculateExternalRackPreview(
        pendingTableDragPoint.clientX,
        pendingTableDragPoint.clientY,
        externalRackTileCount,
      )
    }
  }
  autoScrollFrameId = window.requestAnimationFrame(runAutoScroll)
}

function updateEdgeAutoScroll(clientY: number): void {
  const viewport = scrollViewportElement.value
  if (!viewport) return
  const rect = viewport.getBoundingClientRect()
  // Do not create a second RAF for a non-scrollable/zero-sized test or runtime viewport.
  // Edge auto-scroll is meaningful only after the table content exceeds the fixed viewport.
  if (rect.height <= 0 || viewport.scrollHeight <= viewport.clientHeight) {
    cancelAutoScroll()
    return
  }
  const threshold = Math.min(72, rect.height * 0.18)
  const nextDirection = clientY < rect.top + threshold
    ? -1
    : clientY > rect.bottom - threshold
      ? 1
      : 0
  edgeScrollDirection = nextDirection
  if (nextDirection === 0) cancelAutoScroll()
  else if (autoScrollFrameId === null) autoScrollFrameId = window.requestAnimationFrame(runAutoScroll)
}

function flushTableDropPreview(): void {
  tableDragFrameId = null
  const point = pendingTableDragPoint
  if (!point) return
  assignDropPreview(calculateTableDropPreview(point.clientX, point.clientY))
}

function scheduleTableDropPreview(): void {
  if (tableDragFrameId !== null) return
  tableDragFrameId = window.requestAnimationFrame(flushTableDropPreview)
}

function clearTableDragPerformanceState(): void {
  cancelTableDragFrame()
  cancelAutoScroll()
  pendingTableDragPoint = null
  activeCoordinateResolver = null
}

function startTableDrag(placement: WorkingTilePlacement, event: DragEvent): void {
  const candidate = candidateForTile(placement.tileId)
  if (!candidate || !candidateEditable(candidate) || !event.dataTransfer) {
    event.preventDefault()
    return
  }
  event.dataTransfer.effectAllowed = 'move'
  const moveCandidate = event.shiftKey && candidate.tileIds.length > 1
  const movingTileIds = moveCandidate ? [...candidate.tileIds] : [placement.tileId]
  clearTableDragPerformanceState()
  activeCoordinateResolver = createTableCoordinateResolver(props.placements, movingTileIds)
  if (moveCandidate) {
    event.dataTransfer.setData('application/x-working-meld-id', candidate.clientMeldId)
    event.dataTransfer.setData('text/plain', candidate.clientMeldId)
    activeTableDrag.value = { kind: 'MELD', meldId: candidate.clientMeldId, movingTileIds }
  } else {
    event.dataTransfer.setData('application/x-working-tile-id', placement.tileId)
    event.dataTransfer.setData('text/plain', placement.tileId)
    activeTableDrag.value = { kind: 'TILE', tileId: placement.tileId, movingTileIds }
  }
}

function updateDropPreview(event: DragEvent): void {
  if (props.disabled || !activeTableDrag.value) return
  event.preventDefault()
  pendingTableDragPoint = { clientX: event.clientX, clientY: event.clientY }
  updateEdgeAutoScroll(event.clientY)
  scheduleTableDropPreview()
}

function dropOnGrid(event: DragEvent): void {
  event.preventDefault()
  cancelTableDragFrame()
  pendingTableDragPoint = null
  const active = activeTableDrag.value
  const target = candidateTargetAt(event.clientX, event.clientY)
  if (active && target) {
    if (active.kind === 'MELD' && active.meldId !== target.meldId) {
      emit('mergeMeld', active.meldId, target.meldId)
    } else if (active.kind === 'TILE') {
      emit('moveToMeld', active.tileId, target.meldId, target.targetIndex)
    }
    finishTableDrag()
    return
  }
  const preview = assignDropPreview(calculateTableDropPreview(event.clientX, event.clientY))
  if (active && preview?.valid) {
    if (active.kind === 'MELD') emit('moveMeld', active.meldId, preview.gridRow, preview.gridColumn)
    else emit('moveAsNew', active.tileId, preview.gridRow, preview.gridColumn)
  }
  finishTableDrag()
}

function finishTableDrag(): void {
  clearTableDragPerformanceState()
  activeTableDrag.value = null
  dropPreview.value = null
}

function handleGridDragLeave(event: DragEvent): void {
  const nextTarget = event.relatedTarget as Node | null
  if (nextTarget && (event.currentTarget as HTMLElement).contains(nextTarget)) return
  clearTableDragPerformanceState()
  dropPreview.value = null
}

function finishExternalRackDrag(): void {
  externalPointerPreview.value = null
  externalRackTileCount = 0
  externalCoordinateResolver = null
  if (!activeTableDrag.value) {
    pendingTableDragPoint = null
    cancelAutoScroll()
  }
}

function resolveRackDropTarget(clientX: number, clientY: number, tileCount = 1): RackDropTarget {
  if (props.disabled || tileCount <= 0) return { kind: 'INVALID' }
  pendingTableDragPoint = { clientX, clientY }
  if (externalRackTileCount !== tileCount) externalCoordinateResolver = null
  externalRackTileCount = tileCount
  updateEdgeAutoScroll(clientY)

  const existing = candidateTargetAt(clientX, clientY)
  if (existing) {
    externalPointerPreview.value = null
    return existing
  }
  const boardRect = gridCanvasElement.value?.getBoundingClientRect()
  const coordinate = boardRect
    ? clientPointToTableGridCoordinate(boardRect, clientX, clientY, contentRows.value)
    : null
  if (!boardRect || !coordinate) {
    finishExternalRackDrag()
    return { kind: 'INVALID' }
  }
  const preview = calculateExternalRackPreview(clientX, clientY, tileCount)
  if (!preview) {
    finishExternalRackDrag()
    return { kind: 'INVALID' }
  }
  externalPointerPreview.value = preview
  return {
    kind: 'WORKING_NEW_MELD',
    gridRow: preview.gridRow,
    gridColumn: preview.gridColumn,
    rect: tableGridCellClientRect(
      boardRect,
      preview.gridRow,
      preview.gridColumn,
      tileCount,
      contentRows.value,
    ),
  }
}

function getTableDragPerformanceSnapshot() {
  return {
    movingTileIds: [...(activeTableDrag.value?.movingTileIds ?? [])],
    occupiedCellCount: activeCoordinateResolver?.occupiedCellCount ?? 0,
    framePending: tableDragFrameId !== null,
    autoScrollFramePending: autoScrollFrameId !== null,
    preview: dropPreview.value,
    scrollTop: scrollViewportElement.value?.scrollTop ?? 0,
  }
}

watch(() => props.placements, () => {
  finishTableDrag()
  finishExternalRackDrag()
})

onBeforeUnmount(() => {
  finishTableDrag()
  finishExternalRackDrag()
})

defineExpose({ resolveRackDropTarget, finishExternalRackDrag, getTableDragPerformanceSnapshot })
</script>

<template>
  <section class="working-table-board" aria-label="로컬 Grid Working Table">
    <header class="working-table-status" :class="{ 'working-table-status--invalid': validation.invalidCount > 0 }">
      <strong>Working Table</strong>
      <p v-if="validation.invalidCount > 0">Commit 불가 · 유효하지 않은 조합 {{ validation.invalidCount }}개</p>
      <p v-else>Commit 가능 · {{ validation.validCount }}개 조합 · Rack {{ validation.rackContributionCount }}장 사용</p>
    </header>

    <div
      ref="scrollViewportElement"
      class="working-table-grid table-scroll-viewport"
      data-table-grid-root="true"
      data-draft-drop-target="new"
      :data-visible-rows="TABLE_GRID_VISIBLE_ROWS"
      @dragover="updateDropPreview"
      @drop="dropOnGrid"
      @dragleave="handleGridDragLeave"
    >
      <div
        ref="gridCanvasElement"
        class="working-table-grid__canvas table-scroll-canvas"
        data-working-drop-target="new"
        :data-grid-rows="TABLE_GRID_ROWS"
        :data-content-rows="contentRows"
        :data-grid-columns="TABLE_GRID_COLUMNS"
        :style="canvasStyle()"
      >
        <div
          v-if="visibleDropPreview"
          class="working-table-drop-preview"
          :class="{ 'working-table-drop-preview--invalid': !visibleDropPreview.valid }"
          :style="previewStyle()"
          aria-hidden="true"
        />

        <div
          v-for="candidate in candidates"
          :key="`overlay-${candidate.clientMeldId}`"
          class="working-table-candidate-overlay"
          :class="{
            'working-table-candidate-overlay--invalid': candidateKind(candidate) === 'INVALID',
            'working-table-grid__meld--drop-preview': rackDropPreview?.target.kind === 'WORKING_EXISTING_MELD'
              && rackDropPreview.target.meldId === candidate.clientMeldId,
          }"
          :style="candidateStyle(candidate)"
          :data-derived-candidate="candidate.clientMeldId"
          :data-candidate-kind="candidateKind(candidate)"
          aria-hidden="true"
        />

        <div
          v-for="rendered in renderedPlacements"
          :key="rendered.placement.tileId"
          class="working-table-tile"
          :class="{ 'working-table-tile--drag-source': activeTableDrag?.kind === 'TILE' && activeTableDrag.tileId === rendered.placement.tileId }"
          :style="tileStyle(rendered.placement)"
          :draggable="rendered.draggable"
          :data-working-tile-id="rendered.placement.tileId"
          data-table-tile="true"
          :data-grid-row="rendered.placement.gridRow"
          :data-grid-column="rendered.placement.gridColumn"
          @dragstart="startTableDrag(rendered.placement, $event)"
          @dragend="finishTableDrag"
        >
          <GameTile
            v-if="tileById.get(rendered.placement.tileId)"
            :tile="tileById.get(rendered.placement.tileId)!"
            :index="rendered.index"
            interaction-locked
          />
        </div>

        <div
          class="working-table-bottom-drop-row"
          :style="{ '--table-grid-row': String(contentRows - 1) }"
          aria-hidden="true"
        >
          아래쪽 새 조합 놓기
        </div>
      </div>
    </div>
  </section>
</template>

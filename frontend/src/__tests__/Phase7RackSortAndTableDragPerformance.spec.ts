import { mount } from '@vue/test-utils'
import { effectScope, nextTick, ref, type Ref } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import WorkingTableBoard from '@/components/game/WorkingTableBoard.vue'
import { useRackPresentation } from '@/composables/game/useRackPresentation'
import { useWorkingTable } from '@/composables/game/useWorkingTable'
import type { GameRackTile, GameTableMeld, GameTableTile } from '@/types/game'
import type { RackSyncSource } from '@/types/rackPresentation'

const COLORS = ['RED', 'BLUE', 'YELLOW', 'BLACK'] as const

function rackOf(size: number): GameRackTile[] {
  return Array.from({ length: size }, (_, positionOrder) => ({
    tileId: `PERF-RACK-${String(positionOrder).padStart(2, '0')}`,
    tileType: 'NUMBER' as const,
    color: COLORS[positionOrder % COLORS.length]!,
    number: positionOrder % 13 + 1,
    joker: false,
    positionOrder,
  }))
}

function createPresentation(initialRack: GameRackTile[], excludedTileIds: Ref<string[]> = ref([])) {
  const authoritativeRack = ref(structuredClone(initialRack))
  const scope = effectScope()
  const presentation = scope.run(() => useRackPresentation({
    authoritativeRack,
    rackSyncRevision: ref(1),
    rackSyncSource: ref<RackSyncSource>('SNAPSHOT'),
    drawMotionRevision: ref(0),
    drawMotionTileIds: ref([]),
    excludedTileIds,
    sortMotionDurationMs: 0,
    drawMotionDurationMs: 0,
  }))!
  return { presentation, scope }
}

function tableTile(color: GameTableTile['color'], number: number): GameTableTile {
  return {
    tileId: `${color}-${String(number).padStart(2, '0')}-PERF`,
    tileType: 'NUMBER',
    color,
    number,
    joker: false,
    positionOrder: number,
  }
}

function tableMeld(
  meldId: string,
  color: GameTableTile['color'],
  numbers: number[],
  gridRow: number,
  gridColumn: number,
): GameTableMeld {
  return {
    meldId,
    meldType: 'RUN',
    score: numbers.reduce((total, number) => total + number, 0),
    positionOrder: gridRow,
    gridRow,
    gridColumn,
    tiles: numbers.map((number, index) => ({ ...tableTile(color, number), positionOrder: index })),
  }
}

function createTableHarness() {
  const firstMeldId = '11111111-1111-4111-8111-111111111111'
  const secondMeldId = '22222222-2222-4222-8222-222222222222'
  const rack = ref<GameRackTile[]>([])
  const tableMelds = ref<GameTableMeld[]>([
    tableMeld(firstMeldId, 'RED', [1, 2, 3], 0, 0),
    tableMeld(secondMeldId, 'BLUE', [4, 5, 6, 7, 8], 2, 0),
  ])
  const scope = effectScope()
  const working = scope.run(() => useWorkingTable({
    authoritativeRack: rack,
    authoritativeVersion: ref(3),
    authoritativeSyncRevision: ref(1),
    tableMelds,
    initialMeldCompleted: ref(true),
    isMyTurn: ref(true),
  }))!
  const wrapper = mount(WorkingTableBoard, {
    props: {
      placements: working.placements.value,
      rack: rack.value,
      baselineMelds: tableMelds.value,
      validation: working.validation.value,
      initialMeldCompleted: true,
      isMeldEditable: working.isMeldEditable,
    },
  })
  const canvas = wrapper.find<HTMLElement>('.working-table-grid__canvas')
  canvas.element.getBoundingClientRect = () => ({
    left: 0,
    top: 0,
    width: 180,
    height: 80,
    right: 180,
    bottom: 80,
    x: 0,
    y: 0,
    toJSON: () => ({}),
  } as DOMRect)
  return { firstMeldId, secondMeldId, scope, working, wrapper }
}

function dataTransfer() {
  const data = new Map<string, string>()
  return {
    types: [] as string[],
    effectAllowed: 'move',
    setData(type: string, value: string) {
      data.set(type, value)
      if (!this.types.includes(type)) this.types.push(type)
    },
    getData: (type: string) => data.get(type) ?? '',
  }
}

type DragPerformanceSnapshot = {
  movingTileIds: string[]
  occupiedCellCount: number
  framePending: boolean
  preview: unknown
}

describe('Phase 7 Rack sort compaction', () => {
  it.each([
    [17, 20, 10],
    [20, 20, 10],
    [21, 22, 11],
    [22, 22, 11],
    [23, 24, 12],
    [24, 24, 12],
  ])('rebuilds %i visible tiles into %i slots / %i columns after every explicit sort', (
    visibleCount,
    expectedSlots,
    expectedColumns,
  ) => {
    const rack = rackOf(30)
    const excluded = ref(rack.slice(visibleCount).map((tile) => tile.tileId))
    const { presentation, scope } = createPresentation(rack, excluded)
    expect(presentation.rackSlots.value).toHaveLength(30)

    expect(presentation.applySort('RUN_789')).toBe(true)
    expect(presentation.rackSlots.value).toHaveLength(expectedSlots)
    expect(presentation.displayedSlots.value.length / 2).toBe(expectedColumns)

    expect(presentation.applySort('GROUP_777')).toBe(true)
    expect(presentation.rackSlots.value).toHaveLength(expectedSlots)

    expect(presentation.restoreServerOrder()).toBe(true)
    expect(presentation.rackSlots.value).toHaveLength(expectedSlots)
    expect(presentation.invariantPreserved.value).toBe(true)
    scope.stop()
  })

  it('preserves manual interior holes until an explicit sort compacts them', () => {
    const { presentation, scope } = createPresentation(rackOf(17))
    const firstTileId = presentation.displayOrderIds.value[0]!

    expect(presentation.beginDrag([firstTileId])).toBe(true)
    presentation.previewDrag(18)
    presentation.finishDrag(true)
    expect(presentation.rackSlots.value[0]?.tileId).toBeNull()
    expect(presentation.rackSlots.value[18]?.tileId).toBe(firstTileId)

    expect(presentation.applySort('RUN_789')).toBe(true)
    expect(presentation.rackSlots.value).toHaveLength(20)
    expect(presentation.rackSlots.value.slice(0, 17).every((slot) => slot.tileId !== null)).toBe(true)
    expect(presentation.rackSlots.value.slice(17).every((slot) => slot.tileId === null)).toBe(true)
    scope.stop()
  })
})

describe('Phase 7 Working Table HTML5 drag performance', () => {
  let queuedFrames: Map<number, FrameRequestCallback>
  let nextFrameId: number

  beforeEach(() => {
    queuedFrames = new Map()
    nextFrameId = 1
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      const frameId = nextFrameId++
      queuedFrames.set(frameId, callback)
      return frameId
    })
    vi.spyOn(window, 'cancelAnimationFrame').mockImplementation((frameId) => {
      queuedFrames.delete(frameId)
    })
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  async function flushFrame(): Promise<void> {
    const callbacks = [...queuedFrames.values()]
    queuedFrames.clear()
    callbacks.forEach((callback) => callback(performance.now()))
    await nextTick()
  }

  it('coalesces repeated dragover events to one frame and retains the last pointer cell', async () => {
    const { scope, wrapper } = createTableHarness()
    const transfer = dataTransfer()
    const tile = wrapper.find('[data-working-tile-id="RED-01-PERF"]')
    const grid = wrapper.find('.working-table-grid')

    await tile.trigger('dragstart', { dataTransfer: transfer, shiftKey: true })
    expect((wrapper.vm as unknown as {
      getTableDragPerformanceSnapshot: () => DragPerformanceSnapshot
    }).getTableDragPerformanceSnapshot()).toMatchObject({
      movingTileIds: ['RED-01-PERF', 'RED-02-PERF', 'RED-03-PERF'],
      occupiedCellCount: 5,
      framePending: false,
    })

    await grid.trigger('dragover', { dataTransfer: transfer, clientX: 41, clientY: 41 })
    await grid.trigger('dragover', { dataTransfer: transfer, clientX: 52, clientY: 42 })
    await grid.trigger('dragover', { dataTransfer: transfer, clientX: 69, clientY: 49 })
    expect(window.requestAnimationFrame).toHaveBeenCalledTimes(1)
    expect(queuedFrames).toHaveLength(1)
    expect(wrapper.emitted('moveMeld')).toBeUndefined()

    await flushFrame()
    const preview = wrapper.find<HTMLElement>('.working-table-drop-preview')
    expect(preview.exists()).toBe(true)
    expect(preview.element.style.getPropertyValue('--table-grid-row')).toBe('4')
    expect(preview.element.style.getPropertyValue('--table-grid-column')).toBe('6')
    wrapper.unmount()
    scope.stop()
  })

  it('does not replace reactive preview state while the pointer remains in the same cell', async () => {
    const { scope, wrapper } = createTableHarness()
    const transfer = dataTransfer()
    const tile = wrapper.find('[data-working-tile-id="RED-01-PERF"]')
    const grid = wrapper.find('.working-table-grid')
    const snapshot = () => (wrapper.vm as unknown as {
      getTableDragPerformanceSnapshot: () => DragPerformanceSnapshot
    }).getTableDragPerformanceSnapshot()

    await tile.trigger('dragstart', { dataTransfer: transfer })
    await grid.trigger('dragover', { dataTransfer: transfer, clientX: 61, clientY: 41 })
    await flushFrame()
    const firstPreview = snapshot().preview

    await grid.trigger('dragover', { dataTransfer: transfer, clientX: 68, clientY: 49 })
    await flushFrame()
    expect(snapshot().preview).toBe(firstPreview)
    wrapper.unmount()
    scope.stop()
  })

  it('uses the drop event pointer synchronously even when a stale preview frame is pending', async () => {
    const { firstMeldId, scope, wrapper } = createTableHarness()
    const transfer = dataTransfer()
    const tile = wrapper.find('[data-working-tile-id="RED-01-PERF"]')
    const grid = wrapper.find('.working-table-grid')

    await tile.trigger('dragstart', { dataTransfer: transfer, shiftKey: true })
    await grid.trigger('dragover', { dataTransfer: transfer, clientX: 41, clientY: 41 })
    expect(queuedFrames).toHaveLength(1)
    await grid.trigger('drop', { dataTransfer: transfer, clientX: 85, clientY: 55 })

    expect(wrapper.emitted('moveMeld')).toEqual([[firstMeldId, 5, 8]])
    expect(queuedFrames).toHaveLength(0)
    expect((wrapper.vm as unknown as {
      getTableDragPerformanceSnapshot: () => DragPerformanceSnapshot
    }).getTableDragPerformanceSnapshot()).toMatchObject({
      movingTileIds: [],
      occupiedCellCount: 0,
      framePending: false,
      preview: null,
    })
    wrapper.unmount()
    scope.stop()
  })

  it('caches a five-tile meld once and clears pending frame/cache on full-board dragleave', async () => {
    const { scope, wrapper } = createTableHarness()
    const transfer = dataTransfer()
    const tile = wrapper.find('[data-working-tile-id="BLUE-04-PERF"]')
    const grid = wrapper.find('.working-table-grid')
    const snapshot = () => (wrapper.vm as unknown as {
      getTableDragPerformanceSnapshot: () => DragPerformanceSnapshot
    }).getTableDragPerformanceSnapshot()

    await tile.trigger('dragstart', { dataTransfer: transfer, shiftKey: true })
    expect(snapshot()).toMatchObject({ movingTileIds: [
      'BLUE-04-PERF', 'BLUE-05-PERF', 'BLUE-06-PERF', 'BLUE-07-PERF', 'BLUE-08-PERF',
    ], occupiedCellCount: 3 })
    await grid.trigger('dragover', { dataTransfer: transfer, clientX: 81, clientY: 51 })
    expect(queuedFrames).toHaveLength(1)

    await grid.trigger('dragleave', { dataTransfer: transfer, relatedTarget: null })
    expect(queuedFrames).toHaveLength(0)
    expect(snapshot()).toMatchObject({ occupiedCellCount: 0, framePending: false, preview: null })
    wrapper.unmount()
    scope.stop()
  })
})

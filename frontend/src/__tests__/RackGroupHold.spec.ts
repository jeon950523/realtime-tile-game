import { mount, type VueWrapper } from '@vue/test-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import TileRack from '@/components/game/TileRack.vue'
import type { GameRackTile } from '@/types/game'

const tiles: GameRackTile[] = [7, 8, 9].map((number, index) => ({
  tileId: `RED-${String(number).padStart(2, '0')}-A`,
  tileType: 'NUMBER', color: 'RED', number, joker: false, positionOrder: index,
}))

function rect(left: number, top: number, width: number, height: number): DOMRect {
  return { x: left, y: top, left, top, width, height, right: left + width, bottom: top + height, toJSON: () => ({}) } as DOMRect
}

describe('Phase 7 rack group hold gesture', () => {
  let wrapper: VueWrapper

  beforeEach(() => {
    vi.useFakeTimers()
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      callback(16)
      return 1
    })
    vi.spyOn(window, 'cancelAnimationFrame').mockImplementation(() => undefined)
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
      if (this.classList.contains('tile-rack')) return rect(0, 0, 870, 200)
      const slot = this.classList.contains('rack-tile-slot') ? this : this.closest<HTMLElement>('.rack-tile-slot')
      const index = Number(slot?.dataset.rackIndex ?? 0)
      return rect(index * 87, 0, 82, 96)
    })
    Object.defineProperty(HTMLElement.prototype, 'setPointerCapture', { configurable: true, value: vi.fn() })
    Object.defineProperty(HTMLElement.prototype, 'hasPointerCapture', { configurable: true, value: vi.fn(() => true) })
    Object.defineProperty(HTMLElement.prototype, 'releasePointerCapture', { configurable: true, value: vi.fn() })
    wrapper = mount(TileRack, {
      attachTo: document.body,
      props: {
        tiles,
        activeDragTileIds: [],
        enteringTileIds: [],
        visualGroups: [{ groupId: 'run', kind: 'RUN', tileIds: tiles.map((tile) => tile.tileId) }],
      },
    })
  })

  afterEach(() => {
    wrapper.unmount()
    document.body.innerHTML = ''
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  async function pointerDown(): Promise<void> {
    await wrapper.find('[data-tile-id="RED-07-A"]').trigger('pointerdown', {
      pointerId: 8, pointerType: 'mouse', button: 0, clientX: 41, clientY: 48,
    })
  }

  it('GROUP-HOLD-001 upgrades one active tile to a horizontal three-tile overlay at 320ms', async () => {
    await pointerDown()
    expect(document.querySelector<HTMLElement>('.rack-drag-overlay')?.dataset.activeDragCount).toBe('1')
    vi.advanceTimersByTime(320)
    await wrapper.vm.$nextTick()
    expect(wrapper.emitted('dragUpgrade')?.[0]?.[0]).toEqual(tiles.map((tile) => tile.tileId))
    expect(document.querySelector<HTMLElement>('.rack-drag-overlay')?.dataset.activeDragCount).toBe('3')
    const ghosts = [...document.querySelectorAll<HTMLElement>('.rack-drag-ghost')]
    expect(ghosts).toHaveLength(3)
    expect(ghosts[1]!.style.transform).toContain('87px')
    expect(ghosts[2]!.style.transform).toContain('174px')
  })

  it('GROUP-HOLD-006 movement beyond six pixels before hold keeps single reorder', async () => {
    await pointerDown()
    await wrapper.find('.tile-rack').trigger('pointermove', { pointerId: 8, clientX: 50, clientY: 48 })
    vi.advanceTimersByTime(320)
    expect(wrapper.emitted('dragUpgrade')).toBeUndefined()
    expect(document.querySelector<HTMLElement>('.rack-drag-overlay')?.dataset.activeDragCount).toBe('1')
  })

  it('GROUP-HOLD-007 pointer cancel clears the hold timer', async () => {
    await pointerDown()
    await wrapper.find('.tile-rack').trigger('pointercancel', { pointerId: 8, clientX: 41, clientY: 48 })
    vi.advanceTimersByTime(320)
    expect(wrapper.emitted('dragUpgrade')).toBeUndefined()
    expect(wrapper.emitted('dragFinish')?.[0]).toEqual([false])
  })

  it('GROUP-HOLD-008 blur clears both the active group candidate and pointer session', async () => {
    await pointerDown()
    window.dispatchEvent(new Event('blur'))
    vi.advanceTimersByTime(320)
    expect(wrapper.emitted('dragUpgrade')).toBeUndefined()
    expect(wrapper.emitted('dragFinish')?.[0]).toEqual([false])
  })
})

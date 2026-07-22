import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'

import { mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import TileRack from '@/components/game/TileRack.vue'
import type { GameRackTile } from '@/types/game'

function tile(tileId: string, number: number): GameRackTile {
  return {
    tileId,
    tileType: 'NUMBER',
    color: 'BLUE',
    number,
    joker: false,
    positionOrder: number - 1,
  }
}

const tiles = [tile('BLUE-01-A', 1), tile('BLUE-02-A', 2), tile('BLUE-03-A', 3)]

function domRect(left: number, top: number, width: number, height: number): DOMRect {
  return {
    x: left,
    y: top,
    left,
    top,
    width,
    height,
    right: left + width,
    bottom: top + height,
    toJSON: () => ({}),
  } as DOMRect
}

describe('rack motion polish', () => {
  let frameId = 0
  let frameQueue = new Map<number, FrameRequestCallback>()
  const mounted: VueWrapper[] = []

  function runAnimationFrame(): void {
    const callbacks = [...frameQueue.values()]
    frameQueue.clear()
    callbacks.forEach((callback) => callback(16))
  }

  function mountRack(listeners: Record<string, unknown> = {}): VueWrapper {
    const wrapper = mount(TileRack, {
      attachTo: document.body,
      props: {
        tiles,
        activeDragTileIds: [],
        enteringTileIds: [],
        ...listeners,
      },
    })
    mounted.push(wrapper)
    return wrapper
  }

  async function beginFirstDrag(wrapper: VueWrapper, pointerId = 1): Promise<void> {
    await wrapper.find('[data-tile-id="BLUE-01-A"]').trigger('pointerdown', {
      pointerId,
      pointerType: 'mouse',
      button: 0,
      clientX: 41,
      clientY: 48,
    })
  }

  beforeEach(() => {
    vi.useFakeTimers()
    frameId = 0
    frameQueue = new Map()
    vi.spyOn(window, 'requestAnimationFrame').mockImplementation((callback) => {
      frameId += 1
      frameQueue.set(frameId, callback)
      return frameId
    })
    vi.spyOn(window, 'cancelAnimationFrame').mockImplementation((id) => {
      frameQueue.delete(id)
    })
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (this: HTMLElement) {
      if (this.classList.contains('tile-rack')) return domRect(0, 0, 870, 200)
      const slot = this.classList.contains('rack-tile-slot')
        ? this
        : this.closest<HTMLElement>('.rack-tile-slot')
      if (slot) {
        const index = Number(slot.dataset.rackIndex ?? 0)
        return domRect((index % 10) * 87, Math.floor(index / 10) * 101, 82, 96)
      }
      return domRect(0, 0, 82, 96)
    })
    Object.defineProperty(HTMLElement.prototype, 'setPointerCapture', {
      configurable: true,
      value: vi.fn(),
    })
    Object.defineProperty(HTMLElement.prototype, 'hasPointerCapture', {
      configurable: true,
      value: vi.fn(() => true),
    })
    Object.defineProperty(HTMLElement.prototype, 'releasePointerCapture', {
      configurable: true,
      value: vi.fn(),
    })
  })

  afterEach(() => {
    mounted.forEach((wrapper) => wrapper.unmount())
    mounted.length = 0
    document.body.innerHTML = ''
    vi.restoreAllMocks()
    delete (HTMLElement.prototype as { setPointerCapture?: unknown }).setPointerCapture
    delete (HTMLElement.prototype as { hasPointerCapture?: unknown }).hasPointerCapture
    delete (HTMLElement.prototype as { releasePointerCapture?: unknown }).releasePointerCapture
    vi.useRealTimers()
  })

  it('MOTION-POLISH-003 calculates pointer targets at most once per animation frame', async () => {
    const preview = vi.fn()
    const wrapper = mountRack({ onDragPreview: preview })
    await beginFirstDrag(wrapper)
    const rack = wrapper.find('.tile-rack')

    await rack.trigger('pointermove', { pointerId: 1, clientX: 215, clientY: 48 })
    await rack.trigger('pointermove', { pointerId: 1, clientX: 216, clientY: 48 })
    await rack.trigger('pointermove', { pointerId: 1, clientX: 217, clientY: 48 })

    expect(window.requestAnimationFrame).toHaveBeenCalledTimes(1)
    expect(preview).not.toHaveBeenCalled()
    runAnimationFrame()
    await nextTick()
    expect(preview).toHaveBeenCalledTimes(1)
    expect(preview).toHaveBeenCalledWith(2)
  })

  it('MOTION-POLISH-004 separates wrapper layout identity from the inner tile', () => {
    const wrapper = mountRack()
    const slot = wrapper.find('.rack-tile-slot')
    const inner = slot.find('.game-tile')

    expect(slot.attributes('data-rack-index')).toBe('0')
    expect(inner.attributes('data-rack-index')).toBeUndefined()
    expect(slot.element.parentElement?.classList.contains('tile-rack__tiles')).toBe(true)
  })

  it('MOTION-POLISH-005 scopes hover transform to a non-dragging rack', () => {
    const cssPath = resolve(process.cwd(), 'src/styles/game/rummikub-inspired.css')
    const css = readFileSync(cssPath, 'utf8')

    expect(css).toContain('.tile-rack:not(.tile-rack--dragging) .game-tile:not(.game-tile--entering):hover')
    expect(css).not.toMatch(/\.game-tile:hover\s*\{/)
  })

  it('MOTION-POLISH-006 cancels and clears an unexpected lost pointer capture', async () => {
    const start = vi.fn()
    const finish = vi.fn()
    const wrapper = mountRack({ onDragStart: start, onDragFinish: finish })
    await beginFirstDrag(wrapper)

    await wrapper.find('.tile-rack').trigger('lostpointercapture', { pointerId: 1 })
    expect(finish).toHaveBeenCalledWith(false)

    await wrapper.find('[data-tile-id="BLUE-02-A"]').trigger('pointerdown', {
      pointerId: 2,
      pointerType: 'mouse',
      button: 0,
      clientX: 128,
      clientY: 48,
    })
    expect(start).toHaveBeenCalledTimes(2)
  })

  it('MOTION-POLISH-007 cancels an active drag when the window loses focus', async () => {
    const finish = vi.fn()
    const wrapper = mountRack({ onDragFinish: finish })
    await beginFirstDrag(wrapper)

    window.dispatchEvent(new Event('blur'))
    await nextTick()

    expect(finish).toHaveBeenCalledTimes(1)
    expect(finish).toHaveBeenCalledWith(false)
  })

  it('MOTION-POLISH-008 returns the ghost before removing it after an invalid drop', async () => {
    const finish = vi.fn()
    const wrapper = mountRack({ onDragFinish: finish })
    await beginFirstDrag(wrapper)

    const outsidePointerUp = new Event('pointerup', { bubbles: true })
    Object.defineProperties(outsidePointerUp, {
      pointerId: { value: 1 },
      clientX: { value: 1_000 },
      clientY: { value: 300 },
    })
    window.dispatchEvent(outsidePointerUp)
    await nextTick()

    expect(finish).toHaveBeenCalledWith(false)
    expect(document.querySelector('.rack-drag-overlay--returning')).not.toBeNull()
    runAnimationFrame()
    vi.advanceTimersByTime(169)
    expect(document.querySelector('.rack-drag-overlay')).not.toBeNull()
    vi.advanceTimersByTime(1)
    await nextTick()
    expect(document.querySelector('.rack-drag-overlay')).toBeNull()
  })

  it('MOTION-POLISH-009 keeps the ghost outside the display tileId set', async () => {
    const wrapper = mountRack()
    await beginFirstDrag(wrapper)

    const displayIds = wrapper.findAll('[data-tile-id]').map((element) => element.attributes('data-tile-id'))
    const ghost = document.querySelector<HTMLElement>('[data-ghost-tile-id="BLUE-01-A"]')
    const overlay = document.querySelector<HTMLElement>('.rack-drag-overlay')

    expect(displayIds).toEqual(tiles.map((item) => item.tileId))
    expect(new Set(displayIds).size).toBe(tiles.length)
    expect(ghost).not.toBeNull()
    expect(ghost?.hasAttribute('data-tile-id')).toBe(false)
    expect(overlay?.dataset.activeDragCount).toBe('1')
  })
})

export type RackSortMode = 'SERVER' | 'MANUAL' | 'GROUP_777' | 'RUN_789'

export type RackSyncSource = 'NONE' | 'SNAPSHOT' | 'PRIVATE_EVENT'

export interface RackSlot {
  slotIndex: number
  tileId: string | null
}

export interface RackDragState {
  activeDragTileIds: string[]
  originalOrderIds: string[]
  originalSlots: RackSlot[]
  currentTargetIndex: number
}

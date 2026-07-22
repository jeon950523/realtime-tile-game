import type { GameTableMeld } from '@/types/game'

export interface WorkingTableBaseline {
  gameId: number | null
  baseVersion: number
  currentTurnPlayerId: number | null
  committedMelds: GameTableMeld[]
  placements: WorkingTilePlacement[]
  tableMeldsFingerprint: string
  authoritativeRackIds: string[]
  sourceDisplayOrderIds: string[]
}

export type WorkingTileSource = 'COMMITTED_TABLE' | 'CURRENT_PLAYER_RACK'

export interface WorkingTilePlacement {
  tileId: string
  gridRow: number
  gridColumn: number
  source: WorkingTileSource
  sourceMeldId: string | null
  originalPositionOrder: number | null
}

export interface DerivedTableCandidate {
  clientCandidateId: string
  /** Compatibility alias for the existing rule-validation and component boundary. */
  clientMeldId: string
  tileIds: string[]
  placements: WorkingTilePlacement[]
  gridRow: number
  gridColumn: number
  sourceMeldId: string | null
}

export interface ReadonlyDerivedTableCandidate {
  readonly clientCandidateId?: string
  readonly clientMeldId: string
  readonly sourceMeldId?: string | null
  readonly tileIds: readonly string[]
  readonly placements?: readonly WorkingTilePlacement[]
  readonly gridRow?: number
  readonly gridColumn?: number
}

export interface WorkingTableOperation {
  placements: WorkingTilePlacement[]
  sourceDisplayOrderIds: string[]
}

export interface WorkingTableState {
  baseline: WorkingTableBaseline
  placements: WorkingTilePlacement[]
  /** Derived compatibility view. Never mutated or persisted as editing state. */
  readonly melds: DerivedTableCandidate[]
  history: WorkingTableOperation[]
  pendingCommitActionId: string | null
}

/** Legacy structural view retained only at rule/component test boundaries. */
export interface WorkingMeld {
  clientCandidateId?: string
  clientMeldId: string
  sourceMeldId?: string | null
  tileIds: string[]
  placements?: WorkingTilePlacement[]
  gridRow?: number
  gridColumn?: number
}
export type ReadonlyWorkingMeld = ReadonlyDerivedTableCandidate
export type DraftMeld = WorkingMeld
export type ReadonlyDraftMeld = ReadonlyDerivedTableCandidate
export type TurnDraft = WorkingTableState

export type DraftMeldKind = 'RUN' | 'GROUP' | 'INVALID'

export interface DraftMeldValidation {
  kind: DraftMeldKind
  valid: boolean
  score: number
  resolvedTileScores: Record<string, number>
  reason: string | null
}

export interface TurnDraftValidation {
  melds: Record<string, DraftMeldValidation>
  totalScore: number
  submissionScore: number
  validCount: number
  invalidCount: number
  rackContributionCount: number
  baselinePreserved: boolean
  valid: boolean
  canCommit: boolean
  reason: string | null
}

export interface RackVisualGroup {
  groupId: string
  kind: 'RUN' | 'GROUP'
  tileIds: string[]
}

export type RackDropTarget =
  | { kind: 'WORKING_NEW_MELD'; gridRow: number; gridColumn: number; rect?: DropTargetRect }
  | { kind: 'WORKING_EXISTING_MELD'; meldId: string; targetIndex?: number; rect?: DropTargetRect }
  | { kind: 'INVALID' }

export interface DropTargetRect {
  left: number
  top: number
  width: number
  height: number
}

export interface RackDraftDrop {
  activeDragTileIds: string[]
  target: Exclude<RackDropTarget, { kind: 'INVALID' }>
}

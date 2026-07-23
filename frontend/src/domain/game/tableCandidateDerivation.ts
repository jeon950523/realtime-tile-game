import type { DerivedTableCandidate, WorkingTilePlacement } from '@/types/turnDraft'

function candidateId(row: number, startColumn: number, tileIds: readonly string[]): string {
  return `candidate:${row}:${startColumn}:${tileIds.join('|')}`
}

function preservedSourceMeldId(
  candidatePlacements: readonly WorkingTilePlacement[],
  sourceTileIds: ReadonlyMap<string, ReadonlySet<string>>,
): string | null {
  const sourceMeldIds = new Set(candidatePlacements
    .map((placement) => placement.sourceMeldId)
    .filter((sourceMeldId): sourceMeldId is string => sourceMeldId !== null))
  if (sourceMeldIds.size !== 1) return null
  const sourceMeldId = [...sourceMeldIds][0]!
  const candidateIds = new Set(candidatePlacements.map((placement) => placement.tileId))
  const originalIds = sourceTileIds.get(sourceMeldId)
  return originalIds
    && [...originalIds].every((tileId) => candidateIds.has(tileId))
    ? sourceMeldId
    : null
}

export function deriveTableCandidates(
  placements: readonly WorkingTilePlacement[],
): DerivedTableCandidate[] {
  const ordered = [...placements].sort((left, right) =>
    left.gridRow - right.gridRow
      || left.gridColumn - right.gridColumn
      || left.tileId.localeCompare(right.tileId))
  const sourceTileIds = new Map<string, Set<string>>()
  ordered.forEach((placement) => {
    if (!placement.sourceMeldId) return
    const ids = sourceTileIds.get(placement.sourceMeldId) ?? new Set<string>()
    ids.add(placement.tileId)
    sourceTileIds.set(placement.sourceMeldId, ids)
  })
  const candidates: DerivedTableCandidate[] = []
  let block: WorkingTilePlacement[] = []

  const flush = () => {
    if (block.length === 0) return
    const snapshot = block.map((placement) => ({ ...placement }))
    const gridRow = snapshot[0]!.gridRow
    const gridColumn = snapshot[0]!.gridColumn
    const tileIds = snapshot.map((placement) => placement.tileId)
    const sourceMeldId = preservedSourceMeldId(snapshot, sourceTileIds)
    const id = sourceMeldId ?? candidateId(gridRow, gridColumn, tileIds)
    candidates.push({
      clientCandidateId: id,
      clientMeldId: id,
      sourceMeldId,
      tileIds,
      placements: snapshot,
      gridRow,
      gridColumn,
    })
    block = []
  }

  ordered.forEach((placement) => {
    const previous = block.at(-1)
    if (previous && (previous.gridRow !== placement.gridRow
      || previous.gridColumn + 1 !== placement.gridColumn)) flush()
    block.push(placement)
  })
  flush()
  return candidates
}

export function committedMeldsFingerprint(
  melds: readonly { meldId: string; gridRow: number; gridColumn: number; tiles: readonly { tileId: string; positionOrder: number }[] }[],
): string {
  return melds.map((meld) => [
    meld.meldId,
    meld.gridRow,
    meld.gridColumn,
    ...[...meld.tiles]
      .sort((left, right) => left.positionOrder - right.positionOrder)
      .flatMap((tile) => [tile.tileId, tile.positionOrder]),
  ].join(':')).join(';')
}

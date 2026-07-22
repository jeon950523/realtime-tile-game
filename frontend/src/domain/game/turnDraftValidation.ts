import type { GameRackTile, GameTableMeld, GameTableTile } from '@/types/game'
import type {
  DraftMeldValidation,
  ReadonlyDraftMeld,
  TurnDraftValidation,
  WorkingTilePlacement,
} from '@/types/turnDraft'

type CandidateTile = GameRackTile | GameTableTile

function invalid(reason: string): DraftMeldValidation {
  return { kind: 'INVALID', valid: false, score: 0, resolvedTileScores: {}, reason }
}

function validateRun(tiles: readonly CandidateTile[]): DraftMeldValidation | null {
  if (tiles.length < 3 || tiles.length > 13) return null
  const numbers = tiles.filter((tile) => !tile.joker)
  if (numbers.length === 0) return null
  const color = numbers[0]!.color
  if (numbers.some((tile) => tile.color !== color)) return null
  let start: number | null = null
  for (let index = 0; index < tiles.length; index++) {
    const tile = tiles[index]!
    if (tile.joker) continue
    const candidate = tile.number! - index
    if (start === null) start = candidate
    else if (start !== candidate) return null
  }
  if (start === null || start < 1 || start + tiles.length - 1 > 13) return null
  return {
    kind: 'RUN',
    valid: true,
    score: Array.from({ length: tiles.length }, (_, index) => start! + index)
      .reduce((sum, number) => sum + number, 0),
    resolvedTileScores: Object.fromEntries(
      tiles.map((tile, index) => [tile.tileId, start! + index]),
    ),
    reason: null,
  }
}

function validateGroup(tiles: readonly CandidateTile[]): DraftMeldValidation | null {
  if (tiles.length < 3 || tiles.length > 4) return null
  const numbers = tiles.filter((tile) => !tile.joker)
  if (numbers.length === 0) return null
  const number = numbers[0]!.number
  if (numbers.some((tile) => tile.number !== number)) return null
  const colors = numbers.map((tile) => tile.color)
  if (new Set(colors).size !== colors.length) return null
  if (4 - colors.length < tiles.filter((tile) => tile.joker).length) return null
  return {
    kind: 'GROUP',
    valid: true,
    score: number! * tiles.length,
    resolvedTileScores: Object.fromEntries(tiles.map((tile) => [tile.tileId, number!])),
    reason: null,
  }
}

export function validateDraftMeld(
  meld: ReadonlyDraftMeld,
  tileById: ReadonlyMap<string, CandidateTile>,
): DraftMeldValidation {
  const tiles = meld.tileIds.map((tileId) => tileById.get(tileId))
  if (tiles.some((tile) => tile === undefined)) return invalid('허용되지 않은 타일이 포함되어 있습니다.')
  const resolved = tiles as CandidateTile[]
  return validateRun(resolved)
    ?? validateGroup(resolved)
    ?? invalid(resolved.length < 3 ? 'Meld는 최소 3장이어야 합니다.' : '유효한 RUN 또는 GROUP이 아닙니다.')
}

function baselineIsPreserved(
  melds: readonly ReadonlyDraftMeld[],
  baselineMelds: readonly GameTableMeld[],
): boolean {
  const baselineIds = baselineMelds.flatMap((meld) => meld.tiles.map((tile) => tile.tileId))
  const candidateIds = melds.flatMap((meld) => meld.tileIds)
  return baselineIds.every((tileId) => candidateIds.filter((candidate) => candidate === tileId).length === 1)
}

function initialBaselineIsLocked(
  placements: readonly WorkingTilePlacement[],
  baselineMelds: readonly GameTableMeld[],
): boolean {
  const byId = new Map(placements.map((placement) => [placement.tileId, placement]))
  return baselineMelds.every((baseline) => baseline.tiles.every((tile) => {
    const placement = byId.get(tile.tileId)
    return placement?.source === 'COMMITTED_TABLE'
      && placement.sourceMeldId === baseline.meldId
      && placement.gridRow === baseline.gridRow
      && placement.gridColumn === baseline.gridColumn + tile.positionOrder
  }))
}

function legacyInitialBaselineIsLocked(
  melds: readonly ReadonlyDraftMeld[],
  baselineMelds: readonly GameTableMeld[],
): boolean {
  if (melds.length < baselineMelds.length) return false
  return baselineMelds.every((baseline, index) => {
    const candidate = melds[index]
    return candidate?.clientMeldId === baseline.meldId
      && candidate.sourceMeldId === baseline.meldId
      && candidate.tileIds.length === baseline.tiles.length
      && candidate.tileIds.every((tileId, tileIndex) => tileId === baseline.tiles[tileIndex]?.tileId)
  })
}

export function validateTurnDraft(
  melds: readonly ReadonlyDraftMeld[],
  rack: readonly GameRackTile[],
  initialMeldCompleted: boolean,
  baselineMelds: readonly GameTableMeld[] = [],
  placements: readonly WorkingTilePlacement[] = [],
): TurnDraftValidation {
  const allTiles: CandidateTile[] = [
    ...rack,
    ...baselineMelds.flatMap((meld) => meld.tiles),
  ]
  const tileById = new Map(allTiles.map((tile) => [tile.tileId, tile]))
  const rackIds = new Set(rack.map((tile) => tile.tileId))
  const validations: Record<string, DraftMeldValidation> = {}
  melds.forEach((meld) => { validations[meld.clientMeldId] = validateDraftMeld(meld, tileById) })
  const allValid = melds.length > 0 && melds.every((meld) => validations[meld.clientMeldId]!.valid)
  const validCount = melds.filter((meld) => validations[meld.clientMeldId]!.valid).length
  const invalidCount = melds.length - validCount
  const contributedIds = melds.flatMap((meld) => meld.tileIds).filter((tileId) => rackIds.has(tileId))
  const contributionSet = new Set(contributedIds)
  const newMelds = melds.filter((meld) => meld.tileIds.some((tileId) => rackIds.has(tileId)))
  const totalScore = newMelds.reduce((sum, meld) => sum + validations[meld.clientMeldId]!.score, 0)
  const submissionScore = melds.reduce((sum, meld) => {
    const validation = validations[meld.clientMeldId]!
    return sum + meld.tileIds
      .filter((tileId) => rackIds.has(tileId))
      .reduce((meldSum, tileId) => meldSum + (validation.resolvedTileScores[tileId] ?? 0), 0)
  }, 0)
  const baselinePreserved = baselineIsPreserved(melds, baselineMelds)
  const baselineLocked = initialMeldCompleted || (placements.length > 0
    ? initialBaselineIsLocked(placements, baselineMelds)
    : legacyInitialBaselineIsLocked(melds, baselineMelds))
  const thresholdMet = initialMeldCompleted || totalScore >= 30
  let reason: string | null = null
  if (melds.length === 0) reason = 'Working Table에 Meld가 없습니다.'
  else if (!baselinePreserved) reason = '기존 Table 타일을 모두 정확히 한 번 유지해야 합니다.'
  else if (!baselineLocked) reason = '첫 등록 전에는 기존 Table을 변경할 수 없습니다.'
  else if (!allValid) reason = `${invalidCount}개 Meld가 유효하지 않습니다.`
  else if (contributionSet.size === 0) reason = 'Rack 타일을 한 장 이상 사용해야 합니다.'
  else if (!thresholdMet) reason = `첫 등록 점수가 ${totalScore}/30입니다.`
  return {
    melds: validations,
    totalScore,
    submissionScore,
    validCount,
    invalidCount,
    rackContributionCount: contributionSet.size,
    baselinePreserved,
    valid: allValid && baselinePreserved && baselineLocked,
    canCommit: allValid && baselinePreserved && baselineLocked && contributionSet.size > 0 && thresholdMet,
    reason,
  }
}

export function hasDraftPartitionInvariant(
  authoritativeIds: readonly string[],
  visibleRackIds: readonly string[],
  melds: readonly ReadonlyDraftMeld[],
): boolean {
  const authoritativeSet = new Set(authoritativeIds)
  const contributedIds = melds.flatMap((meld) => meld.tileIds).filter((tileId) => authoritativeSet.has(tileId))
  const combined = [...visibleRackIds, ...contributedIds]
  return new Set(combined).size === combined.length
    && combined.length === authoritativeIds.length
    && combined.every((tileId) => authoritativeSet.has(tileId))
}

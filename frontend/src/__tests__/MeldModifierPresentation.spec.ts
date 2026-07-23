import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'

import CommittedTableBoard from '@/components/game/CommittedTableBoard.vue'
import TurnPreviewBoard from '@/components/game/TurnPreviewBoard.vue'
import WorkingTableBoard from '@/components/game/WorkingTableBoard.vue'
import { committedMeldsToPlacements } from '@/composables/game/useWorkingTable'
import type {
  GamePlayerPublicState,
  GameRackTile,
  GameTableMeld,
  TurnPreviewSnapshot,
} from '@/types/game'
import type { TurnDraftValidation, WorkingTilePlacement } from '@/types/turnDraft'

const players: GamePlayerPublicState[] = [
  { userId: 11, nickname: 'Alpha', avatarType: 'DEFAULT_01', seatOrder: 1, rackTileCount: 10, initialMeldCompleted: true, currentTurn: false },
  { userId: 22, nickname: 'Beta', avatarType: 'DEFAULT_02', seatOrder: 2, rackTileCount: 10, initialMeldCompleted: true, currentTurn: true },
]

const baseline: GameTableMeld = {
  meldId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
  meldType: 'RUN',
  score: 24,
  positionOrder: 0,
  gridRow: 2,
  gridColumn: 4,
  lastModifiedByUserId: 11,
  lastModifiedBySeatOrder: 1,
  tiles: [7, 8, 9].map((number, positionOrder) => ({
    tileId: `RED-${String(number).padStart(2, '0')}-A`,
    tileType: 'NUMBER' as const,
    color: 'RED' as const,
    number,
    joker: false,
    positionOrder,
  })),
}

const validDraft: TurnDraftValidation = {
  melds: {
    [baseline.meldId]: {
      kind: 'RUN', valid: true, score: 24, resolvedTileScores: {}, reason: null,
    },
  },
  totalScore: 24,
  submissionScore: 0,
  validCount: 1,
  invalidCount: 0,
  rackContributionCount: 0,
  baselinePreserved: true,
  valid: true,
  canCommit: false,
  reason: null,
}

function rackTile(number: number): GameRackTile {
  return {
    tileId: `RED-${String(number).padStart(2, '0')}-A`,
    tileType: 'NUMBER',
    color: 'RED',
    number,
    joker: false,
    positionOrder: number,
  }
}

describe('Meld last modifier presentation', () => {
  it('renders authoritative committed Meld outlines and a player color legend', () => {
    const wrapper = mount(CommittedTableBoard, {
      props: { melds: [baseline], players },
    })

    const outline = wrapper.get(`[data-committed-meld-outline="${baseline.meldId}"]`)
    expect(outline.attributes('data-last-modified-seat')).toBe('1')
    expect(outline.attributes('data-last-modified-user-id')).toBe('11')
    expect(outline.attributes('title')).toContain('Alpha')
    expect(wrapper.find('[data-modifier-seat="1"]').text()).toContain('Alpha')
    expect(wrapper.find('[data-modifier-seat="2"]').text()).toContain('Beta')
  })

  it('keeps an untouched Working candidate in the previous modifier color and switches a changed candidate to my seat color', async () => {
    const initialPlacements = committedMeldsToPlacements([baseline])
    const wrapper = mount(WorkingTableBoard, {
      props: {
        placements: initialPlacements,
        rack: [rackTile(10)],
        baselineMelds: [baseline],
        validation: validDraft,
        initialMeldCompleted: true,
        isMeldEditable: () => true,
        mySeatOrder: 2,
        players,
      },
    })

    expect(wrapper.get('[data-derived-candidate]').attributes('data-last-modified-seat')).toBe('1')

    const changedPlacements: WorkingTilePlacement[] = [
      ...initialPlacements,
      {
        tileId: 'RED-10-A',
        gridRow: 2,
        gridColumn: 7,
        source: 'CURRENT_PLAYER_RACK',
        sourceMeldId: null,
        originalPositionOrder: null,
      },
    ]
    await wrapper.setProps({ placements: changedPlacements })

    expect(wrapper.get('[data-derived-candidate]').attributes('data-last-modified-seat')).toBe('2')
    expect(wrapper.get('[data-derived-candidate]').attributes('title')).toContain('Beta')
  })

  it('shows the current turn player color for a changed opponent preview candidate', () => {
    const preview: TurnPreviewSnapshot = {
      gameId: 7,
      baseGameVersion: 3,
      previewRevision: 1,
      turnPlayerId: 22,
      updatedAt: '2026-07-23T05:00:00Z',
      tilePlacements: [
        ...baseline.tiles.map((tile) => ({
          tileId: tile.tileId,
          gridRow: baseline.gridRow,
          gridColumn: baseline.gridColumn + tile.positionOrder,
          source: 'COMMITTED_TABLE' as const,
        })),
        { tileId: 'RED-10-A', gridRow: 2, gridColumn: 7, source: 'CURRENT_PLAYER_RACK' },
      ],
    }

    const wrapper = mount(TurnPreviewBoard, {
      props: {
        preview,
        committedMelds: [baseline],
        turnPlayerNickname: 'Beta',
        turnPlayerSeatOrder: 2,
      },
    })

    expect(wrapper.get('[data-preview-meld-outline]').attributes('data-last-modified-seat')).toBe('2')
  })
})

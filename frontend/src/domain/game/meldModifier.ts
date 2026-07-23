export type MeldModifierSeatOrder = 1 | 2 | 3 | 4

const SEAT_COLORS: Record<MeldModifierSeatOrder, { solid: string; soft: string; text: string }> = {
  1: { solid: '#4d9fff', soft: 'rgb(77 159 255 / 18%)', text: '#dcebff' },
  2: { solid: '#ff9f43', soft: 'rgb(255 159 67 / 18%)', text: '#fff0dc' },
  3: { solid: '#b983ff', soft: 'rgb(185 131 255 / 18%)', text: '#f0e4ff' },
  4: { solid: '#39d9c0', soft: 'rgb(57 217 192 / 18%)', text: '#dcfff9' },
}

export function normalizeMeldModifierSeat(value: number | null | undefined): MeldModifierSeatOrder | null {
  return value === 1 || value === 2 || value === 3 || value === 4 ? value : null
}

export function meldModifierStyle(value: number | null | undefined): Record<string, string> {
  const seat = normalizeMeldModifierSeat(value)
  if (!seat) return {}
  const palette = SEAT_COLORS[seat]
  return {
    '--meld-modifier-color': palette.solid,
    '--meld-modifier-soft': palette.soft,
    '--meld-modifier-text': palette.text,
  }
}

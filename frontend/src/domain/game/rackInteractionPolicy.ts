import type { GameStatus } from '@/types/game'

export interface RackInteractionPolicyState {
  gameStatus: GameStatus | null
  commandInProgress: boolean
  reconnectRecoveryInProgress: boolean
  privateStateLoaded: boolean
  motionActive: boolean
  enteringTileCount: number
}

export function isRackInteractionUnavailable(state: RackInteractionPolicyState): boolean {
  return state.gameStatus !== 'IN_PROGRESS'
    || state.commandInProgress
    || state.reconnectRecoveryInProgress
    || !state.privateStateLoaded
    || state.motionActive
    || state.enteringTileCount > 0
}

export function isRackToolbarDisabled(state: RackInteractionPolicyState, dragActive: boolean): boolean {
  return isRackInteractionUnavailable(state) || dragActive
}

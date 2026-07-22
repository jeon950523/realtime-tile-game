package com.realtimetilegame.game.application.dto;

import java.util.List;

public record GamePrivateState(
    GamePublicState publicState,
    long myPlayerId,
    long myUserId,
    int mySeatOrder,
    List<GameRackTileView> myRack
) {
    public GamePrivateState {
        myRack = List.copyOf(myRack);
    }
}

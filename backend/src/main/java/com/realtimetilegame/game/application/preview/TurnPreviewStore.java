package com.realtimetilegame.game.application.preview;

import java.util.List;
import java.util.Optional;

import com.realtimetilegame.game.application.dto.TurnPreviewSnapshot;

public interface TurnPreviewStore {
    Optional<TurnPreviewSnapshot> find(long gameId);
    boolean saveIfNewer(TurnPreviewSnapshot snapshot);
    default boolean saveIfNewer(TurnPreviewSnapshot snapshot, String ownerSessionId) {
        return saveIfNewer(snapshot);
    }
    boolean clearIfNewer(long gameId, long turnPlayerId, long baseGameVersion, long previewRevision);
    Optional<TurnPreviewSnapshot> remove(long gameId);
    List<TurnPreviewSnapshot> removeByTurnPlayerId(long turnPlayerId);
    default List<TurnPreviewSnapshot> removeByTurnPlayerSession(long turnPlayerId, String ownerSessionId) {
        return removeByTurnPlayerId(turnPlayerId);
    }
}

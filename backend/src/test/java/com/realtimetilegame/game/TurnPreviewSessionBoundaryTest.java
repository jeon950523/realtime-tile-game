package com.realtimetilegame.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.realtimetilegame.game.application.dto.TurnPreviewSnapshot;
import com.realtimetilegame.game.application.dto.TurnPreviewTilePlacement;
import com.realtimetilegame.game.websocket.InMemoryTurnPreviewStore;

class TurnPreviewSessionBoundaryTest {
    @Test
    void beP7Fix012OldDisconnectDoesNotClearNewerSessionPreview() {
        InMemoryTurnPreviewStore store = new InMemoryTurnPreviewStore();
        TurnPreviewSnapshot oldSession = snapshot(1);
        TurnPreviewSnapshot newSession = snapshot(2);

        assertThat(store.saveIfNewer(oldSession, "session-A")).isTrue();
        assertThat(store.saveIfNewer(newSession, "session-B")).isTrue();
        assertThat(store.removeByTurnPlayerSession(1L, "session-A")).isEmpty();
        assertThat(store.find(33L)).contains(newSession);
        assertThat(store.removeByTurnPlayerSession(1L, "session-B")).containsExactly(newSession);
        assertThat(store.find(33L)).isEmpty();
    }

    private static TurnPreviewSnapshot snapshot(long revision) {
        return new TurnPreviewSnapshot(
            33L, 1L, 7L, revision,
            List.of(new TurnPreviewTilePlacement("RED-10-A", 2, 4, "CURRENT_PLAYER_RACK")),
            null,
            OffsetDateTime.parse("2026-07-18T08:00:00Z")
        );
    }
}

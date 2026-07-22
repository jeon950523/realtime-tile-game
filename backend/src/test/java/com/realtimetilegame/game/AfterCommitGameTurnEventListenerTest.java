package com.realtimetilegame.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import com.realtimetilegame.game.application.dto.GamePlayerPublicView;
import com.realtimetilegame.game.application.dto.GamePrivateState;
import com.realtimetilegame.game.application.dto.GamePublicState;
import com.realtimetilegame.game.application.dto.GameRackTileView;
import com.realtimetilegame.game.application.dto.TileDrawnPayload;
import com.realtimetilegame.game.event.AfterCommitGameTurnEventListener;
import com.realtimetilegame.game.event.GameTurnCommittedEvent;
import com.realtimetilegame.game.application.preview.TurnPreviewStore;
import com.realtimetilegame.room.event.RealtimeEvent;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class AfterCommitGameTurnEventListenerTest {
    @Test
    void sendsOnePublicEventAndOnlyEachParticipantsOwnPrivateState() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        AfterCommitGameTurnEventListener listener = new AfterCommitGameTurnEventListener(
            template, mock(TurnPreviewStore.class)
        );
        OffsetDateTime now = OffsetDateTime.parse("2026-07-15T13:00:00Z");
        GamePublicState publicState = publicState(now);
        GamePrivateState ownerState = privateState(publicState, 101L, 1L, 1, "RED-01-A");
        GamePrivateState secondState = privateState(publicState, 102L, 2L, 2, "BLUE-01-A");
        TileDrawnPayload payload = new TileDrawnPayload(
            33L, 1L, 1L, 15, 77, 2L, 2, 2,
            "22222222-2222-4222-8222-222222222222", now, now.plusSeconds(120), 0
        );

        listener.on(new GameTurnCommittedEvent(
            33L,
            "TILE_DRAWN",
            now,
            payload,
            Map.of(1L, ownerState, 2L, secondState)
        ));

        ArgumentCaptor<Object> publicCaptor = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/games/33"), publicCaptor.capture());
        RealtimeEvent publicEvent = (RealtimeEvent) publicCaptor.getValue();
        assertThat(publicEvent.eventType()).isEqualTo("TILE_DRAWN");
        assertThat(publicEvent.payload()).isSameAs(payload);

        ArgumentCaptor<String> userCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object> privateCaptor = ArgumentCaptor.forClass(Object.class);
        verify(template, times(2)).convertAndSendToUser(
            userCaptor.capture(), eq("/queue/game-state"), privateCaptor.capture()
        );
        assertThat(userCaptor.getAllValues()).containsExactlyInAnyOrder("1", "2");
        assertThat(privateCaptor.getAllValues()).allSatisfy(value -> {
            RealtimeEvent event = (RealtimeEvent) value;
            assertThat(event.eventType()).isEqualTo("GAME_STATE_UPDATED");
            assertThat(event.payload()).isInstanceOf(GamePrivateState.class);
        });
    }

    private static GamePublicState publicState(OffsetDateTime now) {
        return new GamePublicState(
            33L, 11L, "CLASSIC", "IN_PROGRESS", 1L, 2L, 2, 2,
            "22222222-2222-4222-8222-222222222222", now, now.plusSeconds(120), 0, now.minusSeconds(10),
            77, List.of(), List.of(
                new GamePlayerPublicView(1L, "owner", "DEFAULT_01", 1, 15, false, false),
                new GamePlayerPublicView(2L, "second", "DEFAULT_01", 2, 14, false, true)
            )
        );
    }

    private static GamePrivateState privateState(GamePublicState publicState, long playerId, long userId,
                                                 int seatOrder, String tileId) {
        return new GamePrivateState(
            publicState,
            playerId,
            userId,
            seatOrder,
            List.of(new GameRackTileView(tileId, "NUMBER", "RED", 1, false, 0))
        );
    }
}

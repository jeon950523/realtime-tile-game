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
import com.realtimetilegame.game.application.dto.GameStartedPayload;
import com.realtimetilegame.game.event.AfterCommitGameStartedEventListener;
import com.realtimetilegame.game.event.GameStartedCommittedEvent;
import com.realtimetilegame.room.event.RealtimeEvent;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class AfterCommitGameStartedEventListenerTest {
    @Test
    void sendsLaunchLobbyRemovalPublicStateAndOnePrivateStatePerParticipant() {
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        AfterCommitGameStartedEventListener listener = new AfterCommitGameStartedEventListener(template);
        OffsetDateTime now = OffsetDateTime.parse("2026-07-15T16:40:00+09:00");
        GamePublicState publicState = new GamePublicState(
            33L, 11L, "CLASSIC", "IN_PROGRESS", 0L, 1L, 1, 1,
            "11111111-1111-4111-8111-111111111111", now, now.plusSeconds(120), 0, now,
            78, List.of(), List.of(
                new GamePlayerPublicView(1L, "owner", "DEFAULT_01", 1, 14, false, true),
                new GamePlayerPublicView(2L, "second", "DEFAULT_01", 2, 14, false, false)
            )
        );
        GamePrivateState ownerState = privateState(publicState, 101L, 1L, 1, "RED-01-A");
        GamePrivateState secondState = privateState(publicState, 102L, 2L, 2, "BLUE-01-A");
        GameStartedCommittedEvent event = new GameStartedCommittedEvent(
            33L,
            11L,
            now,
            new GameStartedPayload(33L, 11L, "/games/33", publicState),
            Map.of(1L, ownerState, 2L, secondState)
        );

        listener.on(event);

        ArgumentCaptor<Object> roomPayload = ArgumentCaptor.forClass(Object.class);
        verify(template).convertAndSend(eq("/topic/rooms/11"), roomPayload.capture());
        RealtimeEvent launch = (RealtimeEvent) roomPayload.getValue();
        assertThat(launch.eventType()).isEqualTo("GAME_STARTED");
        assertThat(((GameStartedPayload) launch.payload()).route()).isEqualTo("/games/33");

        verify(template).convertAndSend(
            eq("/topic/lobby/rooms"), org.mockito.ArgumentMatchers.any(RealtimeEvent.class)
        );
        verify(template).convertAndSend(
            eq("/topic/games/33"), org.mockito.ArgumentMatchers.any(RealtimeEvent.class)
        );

        ArgumentCaptor<Object> privatePayload = ArgumentCaptor.forClass(Object.class);
        verify(template, times(2)).convertAndSendToUser(
            org.mockito.ArgumentMatchers.anyString(), eq("/queue/game-state"), privatePayload.capture()
        );
        assertThat(privatePayload.getAllValues())
            .allSatisfy(value -> assertThat(((RealtimeEvent) value).payload()).isInstanceOf(GamePrivateState.class));
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

package com.realtimetilegame.game.event;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.realtimetilegame.game.application.dto.TurnPreviewClearedPayload;
import com.realtimetilegame.game.application.preview.TurnPreviewStore;
import com.realtimetilegame.room.event.RealtimeEvent;
import com.realtimetilegame.websocket.auth.StompPrincipal;

@Component
public class TurnPreviewDisconnectListener {
    private final TurnPreviewStore store;
    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public TurnPreviewDisconnectListener(TurnPreviewStore store, SimpMessagingTemplate messagingTemplate,
                                         Clock clock) {
        this.store = store;
        this.messagingTemplate = messagingTemplate;
        this.clock = clock;
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        if (!(event.getUser() instanceof StompPrincipal principal)) return;
        store.removeByTurnPlayerSession(principal.userId(), principal.sessionId()).forEach(snapshot ->
            messagingTemplate.convertAndSend(
                "/topic/games/" + snapshot.gameId(),
                new RealtimeEvent(
                    "TURN_PREVIEW_CLEARED",
                    OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC),
                    new TurnPreviewClearedPayload(
                        snapshot.gameId(), snapshot.turnPlayerId(), snapshot.baseGameVersion(),
                        snapshot.previewRevision(), "DISCONNECT"
                    )
                )
            )
        );
    }
}

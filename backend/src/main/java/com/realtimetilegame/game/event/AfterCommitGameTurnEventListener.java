package com.realtimetilegame.game.event;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.realtimetilegame.room.event.RealtimeEvent;
import com.realtimetilegame.game.application.dto.TurnPreviewClearedPayload;
import com.realtimetilegame.game.application.preview.TurnPreviewStore;

@Component
public class AfterCommitGameTurnEventListener {
    private final SimpMessagingTemplate messagingTemplate;
    private final TurnPreviewStore turnPreviewStore;

    public AfterCommitGameTurnEventListener(SimpMessagingTemplate messagingTemplate,
                                            TurnPreviewStore turnPreviewStore) {
        this.messagingTemplate = messagingTemplate;
        this.turnPreviewStore = turnPreviewStore;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(GameTurnCommittedEvent event) {
        turnPreviewStore.remove(event.gameId()).ifPresent(snapshot ->
            messagingTemplate.convertAndSend(
                "/topic/games/" + event.gameId(),
                new RealtimeEvent(
                    "TURN_PREVIEW_CLEARED",
                    event.occurredAt(),
                    new TurnPreviewClearedPayload(
                        snapshot.gameId(), snapshot.turnPlayerId(), snapshot.baseGameVersion(),
                        snapshot.previewRevision(), event.publicEventType()
                    )
                )
            )
        );
        messagingTemplate.convertAndSend(
            "/topic/games/" + event.gameId(),
            new RealtimeEvent(event.publicEventType(), event.occurredAt(), event.publicPayload())
        );
        event.privateStates().forEach((userId, state) ->
            messagingTemplate.convertAndSendToUser(
                Long.toString(userId),
                "/queue/game-state",
                new RealtimeEvent("GAME_STATE_UPDATED", event.occurredAt(), state)
            )
        );
    }
}

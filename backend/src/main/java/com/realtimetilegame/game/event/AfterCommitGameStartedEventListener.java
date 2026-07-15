package com.realtimetilegame.game.event;

import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.realtimetilegame.room.event.RealtimeEvent;

@Component
public class AfterCommitGameStartedEventListener {
    private final SimpMessagingTemplate messagingTemplate;

    public AfterCommitGameStartedEventListener(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(GameStartedCommittedEvent event) {
        RealtimeEvent gameStarted = new RealtimeEvent("GAME_STARTED", event.occurredAt(), event.launchPayload());
        messagingTemplate.convertAndSend("/topic/rooms/" + event.roomId(), gameStarted);
        messagingTemplate.convertAndSend(
            "/topic/lobby/rooms",
            new RealtimeEvent("ROOM_REMOVED", event.occurredAt(), Map.of("roomId", event.roomId()))
        );
        messagingTemplate.convertAndSend(
            "/topic/games/" + event.gameId(),
            new RealtimeEvent("GAME_STATE_UPDATED", event.occurredAt(), event.launchPayload().publicState())
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

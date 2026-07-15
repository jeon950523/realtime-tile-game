package com.realtimetilegame.room.event;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class AfterCommitRoomEventListener {
    private final SimpMessagingTemplate messagingTemplate;
    public AfterCommitRoomEventListener(SimpMessagingTemplate messagingTemplate) { this.messagingTemplate = messagingTemplate; }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(RoomEventEnvelope envelope) {
        messagingTemplate.convertAndSend(envelope.destination(), envelope.event());
    }
}

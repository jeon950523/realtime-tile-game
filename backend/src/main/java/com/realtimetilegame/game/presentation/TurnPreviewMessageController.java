package com.realtimetilegame.game.presentation;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.realtimetilegame.game.application.dto.TurnPreviewCancelCommand;
import com.realtimetilegame.game.application.dto.TurnPreviewCommand;
import com.realtimetilegame.game.application.preview.TurnPreviewService;
import com.realtimetilegame.game.application.preview.TurnPreviewService.ClearResult;
import com.realtimetilegame.game.application.preview.TurnPreviewService.Decision;
import com.realtimetilegame.game.application.preview.TurnPreviewService.UpdateResult;
import com.realtimetilegame.room.event.RealtimeEvent;
import com.realtimetilegame.websocket.auth.StompPrincipal;

@Controller
public class TurnPreviewMessageController {
    private static final Logger LOGGER = LoggerFactory.getLogger(TurnPreviewMessageController.class);

    private final TurnPreviewService service;
    private final SimpMessagingTemplate messagingTemplate;

    public TurnPreviewMessageController(TurnPreviewService service, SimpMessagingTemplate messagingTemplate) {
        this.service = service;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/games/{gameId}/turn/preview")
    public void update(@DestinationVariable long gameId, TurnPreviewCommand command, Principal principal) {
        try {
            StompPrincipal stompPrincipal = principal(principal);
            UpdateResult result = service.update(
                gameId, stompPrincipal.userId(), stompPrincipal.sessionId(), command
            );
            if (result.decision() != Decision.ACCEPTED) return;
            messagingTemplate.convertAndSend(
                "/topic/games/" + gameId,
                new RealtimeEvent("TURN_PREVIEW_UPDATED", result.snapshot().updatedAt(), result.snapshot())
            );
        } catch (RuntimeException exception) {
            LOGGER.warn("Turn Preview update rejected gameId={}", gameId, exception);
        }
    }

    @MessageMapping("/games/{gameId}/turn/preview/cancel")
    public void cancel(@DestinationVariable long gameId, TurnPreviewCancelCommand command, Principal principal) {
        try {
            ClearResult result = service.cancel(gameId, userId(principal), command);
            if (result.decision() != Decision.ACCEPTED) return;
            messagingTemplate.convertAndSend(
                "/topic/games/" + gameId,
                new RealtimeEvent("TURN_PREVIEW_CLEARED", java.time.OffsetDateTime.now(), result.payload())
            );
        } catch (RuntimeException exception) {
            LOGGER.warn("Turn Preview cancel rejected gameId={}", gameId, exception);
        }
    }

    private static long userId(Principal principal) {
        return principal(principal).userId();
    }

    private static StompPrincipal principal(Principal principal) {
        if (principal instanceof StompPrincipal stompPrincipal) return stompPrincipal;
        throw new IllegalArgumentException("authenticated STOMP principal is required");
    }
}

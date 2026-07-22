package com.realtimetilegame.room.presentation;

import java.security.Principal;
import java.util.Map;
import java.util.UUID;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.GameStartService;
import com.realtimetilegame.game.application.dto.GameStartResult;
import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.room.websocket.ActionReplayStore;
import com.realtimetilegame.room.websocket.RoomCommandReply;
import com.realtimetilegame.room.websocket.RoomReadyCommand;
import com.realtimetilegame.room.websocket.RoomStartCommand;
import com.realtimetilegame.websocket.auth.StompPrincipal;

@Controller
public class RoomMessageController {
    private final RoomCommandService roomCommandService;
    private final GameStartService gameStartService;
    private final ActionReplayStore replayStore;
    private final SimpMessagingTemplate messagingTemplate;

    public RoomMessageController(RoomCommandService roomCommandService, GameStartService gameStartService,
                                 ActionReplayStore replayStore, SimpMessagingTemplate messagingTemplate) {
        this.roomCommandService = roomCommandService;
        this.gameStartService = gameStartService;
        this.replayStore = replayStore;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/rooms/{roomId}/ready")
    public void ready(@DestinationVariable long roomId, RoomReadyCommand command, Principal principal) {
        long userId = userId(principal);
        String actionId = command == null ? "" : safeActionId(command.actionId());
        RoomCommandReply reply;
        try {
            actionId = validatedActionId(command == null ? null : command.actionId());
            String validatedActionId = actionId;
            reply = replayStore.execute(userId, validatedActionId,
                () -> executeReady(roomId, userId, validatedActionId, command.ready())).reply();
        } catch (BusinessException exception) {
            reply = rejected(actionId, exception.errorCode());
        }
        send(principal, reply);
    }

    @MessageMapping("/rooms/{roomId}/start")
    public void start(@DestinationVariable long roomId, RoomStartCommand command, Principal principal) {
        long userId = userId(principal);
        String actionId = command == null ? "" : safeActionId(command.actionId());
        RoomCommandReply reply;
        try {
            actionId = validatedActionId(command == null ? null : command.actionId());
            String validatedActionId = actionId;
            reply = replayStore.execute(userId, validatedActionId,
                () -> executeStart(roomId, userId, validatedActionId)).reply();
        } catch (BusinessException exception) {
            reply = rejected(actionId, exception.errorCode());
        }
        send(principal, reply);
    }

    private RoomCommandReply executeReady(long roomId, long userId, String actionId, boolean ready) {
        try {
            RoomCommandService.ReadyChangeResult result = roomCommandService.changeReady(roomId, userId, ready);
            return RoomCommandReply.accepted(actionId, Map.of(
                "roomId", roomId,
                "readyStatus", result.readyStatus().name(),
                "startable", result.eligibility().startable(),
                "startBlockReason", result.eligibility().blockReason() == null ? "" : result.eligibility().blockReason()
            ));
        } catch (BusinessException exception) {
            return rejected(actionId, exception.errorCode());
        }
    }

    private RoomCommandReply executeStart(long roomId, long userId, String actionId) {
        try {
            GameStartResult result = gameStartService.startGame(roomId, userId);
            return RoomCommandReply.gameStartAccepted(actionId, result);
        } catch (BusinessException exception) {
            return rejected(actionId, exception.errorCode());
        }
    }

    private void send(Principal principal, RoomCommandReply reply) {
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/replies", reply);
    }

    private static long userId(Principal principal) {
        if (principal instanceof StompPrincipal stompPrincipal) return stompPrincipal.userId();
        throw new BusinessException(ErrorCode.AUTHENTICATION_REQUIRED);
    }

    private static String validatedActionId(String value) {
        try {
            if (value == null || value.isBlank()) throw new IllegalArgumentException();
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_ROOM_ACTION_ID);
        }
    }

    private static String safeActionId(String value) {
        return value == null ? "" : value.trim();
    }

    private static RoomCommandReply rejected(String actionId, ErrorCode code) {
        return RoomCommandReply.rejected(actionId, code.name(), code.defaultMessage());
    }
}

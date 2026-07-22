package com.realtimetilegame.game.presentation;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.GameTurnCommandService;
import com.realtimetilegame.game.application.GameTurnCommitService;
import com.realtimetilegame.game.application.GameExitService;
import com.realtimetilegame.game.application.dto.CommitTableMeldCommand;
import com.realtimetilegame.game.application.dto.CommitTilePlacementCommand;
import com.realtimetilegame.game.application.dto.CommitTurnCommand;
import com.realtimetilegame.game.application.dto.GameTurnCommand;
import com.realtimetilegame.game.application.dto.GameTurnCommandResult;
import com.realtimetilegame.game.application.dto.ExitActiveGameCommand;
import com.realtimetilegame.game.application.dto.GameExitResult;
import com.realtimetilegame.game.websocket.GameActionReplayStore;
import com.realtimetilegame.game.websocket.GameCommandReply;
import com.realtimetilegame.game.domain.rule.rearrangement.TableGridLayoutValidator;
import com.realtimetilegame.websocket.auth.StompPrincipal;

@Controller
public class GameMessageController {
    private static final Logger LOGGER = LoggerFactory.getLogger(GameMessageController.class);

    private final GameTurnCommandService commandService;
    private final GameTurnCommitService commitService;
    private final GameExitService exitService;
    private final GameActionReplayStore replayStore;
    private final SimpMessagingTemplate messagingTemplate;

    @Autowired
    public GameMessageController(GameTurnCommandService commandService,
                                 GameTurnCommitService commitService,
                                 GameExitService exitService,
                                 GameActionReplayStore replayStore,
                                 SimpMessagingTemplate messagingTemplate) {
        this.commandService = commandService;
        this.commitService = commitService;
        this.exitService = exitService;
        this.replayStore = replayStore;
        this.messagingTemplate = messagingTemplate;
    }

    public GameMessageController(GameTurnCommandService commandService,
                          GameTurnCommitService commitService,
                          GameActionReplayStore replayStore,
                          SimpMessagingTemplate messagingTemplate) {
        this(commandService, commitService, null, replayStore, messagingTemplate);
    }

    @MessageMapping("/games/{gameId}/turn/draw")
    public void draw(@DestinationVariable long gameId, GameTurnCommand command, Principal principal) {
        execute(gameId, command, principal, GameTurnCommandService.DRAW);
    }

    @MessageMapping("/games/{gameId}/turn/pass")
    public void pass(@DestinationVariable long gameId, GameTurnCommand command, Principal principal) {
        execute(gameId, command, principal, GameTurnCommandService.PASS);
    }

    @MessageMapping("/games/{gameId}/exit")
    public void exit(@DestinationVariable long gameId, ExitActiveGameCommand command, Principal principal) {
        long userId = -1L;
        String actionId = command == null ? "" : safeActionId(command.actionId());
        long requestedVersion = command == null || command.gameVersion() == null ? -1L : command.gameVersion();
        GameCommandReply reply;
        try {
            userId = userId(principal);
            actionId = validatedActionId(command == null ? null : command.actionId());
            long gameVersion = validatedGameVersion(command == null ? null : command.gameVersion());
            long roomId = validatedRoomId(command == null ? null : command.roomId());
            long validatedUserId = userId;
            String validatedActionId = actionId;
            reply = replayStore.execute(
                validatedUserId,
                gameId,
                validatedActionId,
                () -> executeExitCommand(roomId, gameId, validatedUserId, validatedActionId, gameVersion)
            ).reply();
        } catch (BusinessException exception) {
            reply = rejected(
                actionId, gameId, GameExitService.EXIT_ACTIVE_GAME, requestedVersion, exception.errorCode()
            );
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Unexpected game exit failure actionId={} gameId={} userId={}",
                actionId, gameId, userId, exception
            );
            reply = rejected(
                actionId, gameId, GameExitService.EXIT_ACTIVE_GAME,
                requestedVersion, ErrorCode.INTERNAL_SERVER_ERROR
            );
        }
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/replies", reply);
    }

    @MessageMapping("/games/{gameId}/turn/commit")
    public void commit(@DestinationVariable long gameId, CommitTurnCommand command, Principal principal) {
        long userId = -1L;
        String actionId = command == null ? "" : safeActionId(command.actionId());
        long requestedVersion = command == null || command.gameVersion() == null ? -1L : command.gameVersion();
        GameCommandReply reply;
        try {
            long validatedUserId = userId(principal);
            userId = validatedUserId;
            actionId = validatedActionId(command == null ? null : command.actionId());
            long gameVersion = validatedGameVersion(command == null ? null : command.gameVersion());
            CommitTurnCommand validated = validatedCommitCommand(command, actionId, gameVersion);
            String validatedActionId = actionId;
            reply = replayStore.execute(
                validatedUserId,
                gameId,
                validatedActionId,
                () -> executeCommitCommand(gameId, validatedUserId, validated)
            ).reply();
        } catch (BusinessException exception) {
            reply = rejected(actionId, gameId, GameTurnCommitService.COMMIT, requestedVersion, exception.errorCode());
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Unexpected commit controller failure actionId={} gameId={} userId={}",
                actionId, gameId, userId, exception
            );
            reply = rejected(
                actionId, gameId, GameTurnCommitService.COMMIT,
                requestedVersion, ErrorCode.INTERNAL_SERVER_ERROR
            );
        }
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/replies", reply);
    }

    private void execute(long gameId, GameTurnCommand command, Principal principal, String actionType) {
        long userId = -1L;
        String actionId = command == null ? "" : safeActionId(command.actionId());
        long requestedVersion = command == null || command.gameVersion() == null ? -1L : command.gameVersion();
        GameCommandReply reply;
        try {
            long validatedUserId = userId(principal);
            userId = validatedUserId;
            actionId = validatedActionId(command == null ? null : command.actionId());
            long gameVersion = validatedGameVersion(command == null ? null : command.gameVersion());
            String validatedActionId = actionId;
            reply = replayStore.execute(
                validatedUserId,
                gameId,
                validatedActionId,
                () -> executeCommand(gameId, validatedUserId, validatedActionId, actionType, gameVersion)
            ).reply();
        } catch (BusinessException exception) {
            reply = rejected(actionId, gameId, actionType, requestedVersion, exception.errorCode());
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Unexpected game controller failure actionId={} gameId={} userId={} actionType={}",
                actionId, gameId, userId, actionType, exception
            );
            reply = rejected(actionId, gameId, actionType, requestedVersion, ErrorCode.INTERNAL_SERVER_ERROR);
        }
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/replies", reply);
    }

    private GameCommandReply executeCommand(long gameId, long userId, String actionId,
                                            String actionType, long gameVersion) {
        try {
            GameTurnCommandResult result = GameTurnCommandService.DRAW.equals(actionType)
                ? commandService.draw(gameId, userId, gameVersion)
                : commandService.pass(gameId, userId, gameVersion);
            return GameCommandReply.accepted(
                actionId,
                result.gameId(),
                result.actionType(),
                result.gameVersion()
            );
        } catch (BusinessException exception) {
            return rejected(actionId, gameId, actionType, gameVersion, exception.errorCode());
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Unexpected game command failure actionId={} gameId={} userId={} actionType={}",
                actionId, gameId, userId, actionType, exception
            );
            return rejected(actionId, gameId, actionType, gameVersion, ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private GameCommandReply executeCommitCommand(long gameId, long userId, CommitTurnCommand command) {
        try {
            GameTurnCommandResult result = commitService.commit(gameId, userId, command);
            return GameCommandReply.accepted(
                command.actionId(), result.gameId(), result.actionType(), result.gameVersion()
            );
        } catch (BusinessException exception) {
            return rejected(
                command.actionId(), gameId, GameTurnCommitService.COMMIT,
                command.gameVersion(), exception.errorCode()
            );
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Unexpected commit failure actionId={} gameId={} userId={}",
                command.actionId(), gameId, userId, exception
            );
            return rejected(
                command.actionId(), gameId, GameTurnCommitService.COMMIT,
                command.gameVersion(), ErrorCode.INTERNAL_SERVER_ERROR
            );
        }
    }

    private GameCommandReply executeExitCommand(long roomId, long gameId, long userId,
                                                String actionId, long gameVersion) {
        try {
            GameExitResult result = exitService.exit(roomId, gameId, userId, gameVersion);
            return GameCommandReply.accepted(
                actionId, result.gameId(), result.actionType(), result.gameVersion()
            );
        } catch (BusinessException exception) {
            return rejected(
                actionId, gameId, GameExitService.EXIT_ACTIVE_GAME, gameVersion, exception.errorCode()
            );
        } catch (RuntimeException exception) {
            LOGGER.error(
                "Unexpected active game exit command failure actionId={} gameId={} userId={}",
                actionId, gameId, userId, exception
            );
            return rejected(
                actionId, gameId, GameExitService.EXIT_ACTIVE_GAME,
                gameVersion, ErrorCode.INTERNAL_SERVER_ERROR
            );
        }
    }

    private static CommitTurnCommand validatedCommitCommand(CommitTurnCommand command, String actionId,
                                                             long gameVersion) {
        List<CommitTilePlacementCommand> raw = rawTilePlacements(command);
        if (raw == null || raw.isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_MELD_SUBMISSION);
        }
        if (raw.size() > 106) throw new BusinessException(ErrorCode.INVALID_COMMIT_PAYLOAD);
        Set<String> tileIds = new HashSet<>();
        Set<String> cells = new HashSet<>();
        List<CommitTilePlacementCommand> normalized = new ArrayList<>();
        for (CommitTilePlacementCommand placement : raw) {
            if (placement == null || placement.tileId() == null || placement.tileId().isBlank()
                || placement.gridRow() == null || placement.gridColumn() == null) {
                throw new BusinessException(ErrorCode.INVALID_COMMIT_PAYLOAD);
            }
            String tileId = placement.tileId().trim();
            if (!tileIds.add(tileId)) throw new BusinessException(ErrorCode.DUPLICATE_TILE_IN_TURN);
            if (placement.gridRow() < 0 || placement.gridRow() >= TableGridLayoutValidator.ROWS
                || placement.gridColumn() < 0 || placement.gridColumn() >= TableGridLayoutValidator.COLUMNS) {
                throw new BusinessException(ErrorCode.INVALID_TABLE_LAYOUT);
            }
            if (!cells.add(placement.gridRow() + ":" + placement.gridColumn())) {
                throw new BusinessException(ErrorCode.INVALID_TABLE_LAYOUT);
            }
            normalized.add(new CommitTilePlacementCommand(
                tileId, placement.gridRow(), placement.gridColumn()
            ));
        }
        return new CommitTurnCommand(actionId, gameVersion, normalized, null);
    }

    private static List<CommitTilePlacementCommand> rawTilePlacements(CommitTurnCommand command) {
        if (command == null) return null;
        if (command.tilePlacements() != null) return command.tilePlacements();
        if (command.tableMelds() == null) return null;
        List<CommitTilePlacementCommand> legacy = new ArrayList<>();
        for (CommitTableMeldCommand meld : command.tableMelds()) {
            if (meld == null || meld.tileIds() == null || meld.gridRow() == null || meld.gridColumn() == null) {
                return null;
            }
            for (int position = 0; position < meld.tileIds().size(); position++) {
                legacy.add(new CommitTilePlacementCommand(
                    meld.tileIds().get(position), meld.gridRow(), meld.gridColumn() + position
                ));
            }
        }
        return legacy;
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
            throw new BusinessException(ErrorCode.INVALID_GAME_ACTION_ID);
        }
    }

    private static long validatedGameVersion(Long value) {
        if (value == null || value < 0) {
            throw new BusinessException(ErrorCode.INVALID_GAME_VERSION);
        }
        return value;
    }

    private static long validatedRoomId(Long value) {
        if (value == null || value <= 0) throw new BusinessException(ErrorCode.ROOM_GAME_MISMATCH);
        return value;
    }

    private static String safeActionId(String value) {
        return value == null ? "" : value.trim();
    }

    private static GameCommandReply rejected(String actionId, long gameId, String actionType,
                                             long gameVersion, ErrorCode code) {
        return GameCommandReply.rejected(
            actionId,
            gameId,
            actionType,
            gameVersion,
            code.name(),
            code.defaultMessage()
        );
    }
}

package com.realtimetilegame.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.GameTurnCommandService;
import com.realtimetilegame.game.application.GameTurnCommitService;
import com.realtimetilegame.game.application.GameExitService;
import com.realtimetilegame.game.application.dto.ExitActiveGameCommand;
import com.realtimetilegame.game.application.dto.GameExitResult;
import com.realtimetilegame.game.application.dto.GameTurnCommand;
import com.realtimetilegame.game.application.dto.GameTurnCommandResult;
import com.realtimetilegame.game.application.dto.CommitTurnCommand;
import com.realtimetilegame.game.application.dto.CommitTableMeldCommand;
import com.realtimetilegame.game.application.dto.CommitTilePlacementCommand;
import com.realtimetilegame.game.presentation.GameMessageController;
import com.realtimetilegame.game.websocket.GameActionReplayStore;
import com.realtimetilegame.game.websocket.GameCommandReply;
import com.realtimetilegame.websocket.auth.StompPrincipal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class GameMessageControllerTest {
    private static final StompPrincipal PRINCIPAL = new StompPrincipal(7L, Instant.now().plusSeconds(300));

    @Test
    void invalidActionIdAndVersionAreRejectedWithoutCallingTheDomainService() {
        GameTurnCommandService service = mock(GameTurnCommandService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        GameMessageController controller = controller(service, template);

        controller.draw(33L, new GameTurnCommand("not-a-uuid", 0L), PRINCIPAL);
        controller.pass(33L, new GameTurnCommand("11111111-1111-4111-8111-111111111111", -1L), PRINCIPAL);

        verify(service, never()).draw(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        verify(service, never()).pass(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong());
        assertThat(replies(template, 2)).extracting(GameCommandReply::code)
            .containsExactly("INVALID_GAME_ACTION_ID", "INVALID_GAME_VERSION");
    }

    @Test
    void duplicateDrawExecutesTheDomainCommandOnceAndReplaysTheCommittedVersion() {
        GameTurnCommandService service = mock(GameTurnCommandService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        GameMessageController controller = controller(service, template);
        String actionId = "2718970d-27d1-4434-9a27-573a666943fc";
        when(service.draw(33L, 7L, 0L)).thenReturn(new GameTurnCommandResult(33L, "DRAW", 1L));

        controller.draw(33L, new GameTurnCommand(actionId, 0L), PRINCIPAL);
        controller.draw(33L, new GameTurnCommand(actionId, 0L), PRINCIPAL);

        verify(service, times(1)).draw(33L, 7L, 0L);
        List<GameCommandReply> replies = replies(template, 2);
        assertThat(replies.get(0).eventType()).isEqualTo("GAME_COMMAND_ACCEPTED");
        assertThat(replies.get(0).gameVersion()).isEqualTo(1L);
        assertThat(replies.get(1).eventType()).isEqualTo("DUPLICATE_GAME_ACTION_REPLAYED");
        assertThat(replies.get(1).gameVersion()).isEqualTo(1L);
        assertThat(replies.get(1).duplicate()).isTrue();
    }

    @Test
    void rejectedCommandIsReplayedConsistentlyWithTheRoomPolicy() {
        GameTurnCommandService service = mock(GameTurnCommandService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        GameMessageController controller = controller(service, template);
        String actionId = "d4a0d614-af23-4d2d-91c4-c08f03d156a0";
        when(service.pass(33L, 7L, 5L)).thenThrow(new BusinessException(ErrorCode.PASS_NOT_ALLOWED));

        controller.pass(33L, new GameTurnCommand(actionId, 5L), PRINCIPAL);
        controller.pass(33L, new GameTurnCommand(actionId, 5L), PRINCIPAL);

        verify(service, times(1)).pass(33L, 7L, 5L);
        List<GameCommandReply> replies = replies(template, 2);
        assertThat(replies).extracting(GameCommandReply::code)
            .containsExactly("PASS_NOT_ALLOWED", "PASS_NOT_ALLOWED");
        assertThat(replies.get(1).duplicate()).isTrue();
        assertThat(replies.get(1).gameVersion()).isEqualTo(5L);
    }

    @Test
    void duplicateActiveGameExitExecutesOnceAndReplaysTheTerminalVersion() {
        GameTurnCommandService service = mock(GameTurnCommandService.class);
        GameTurnCommitService commitService = mock(GameTurnCommitService.class);
        GameExitService exitService = mock(GameExitService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        GameMessageController controller = new GameMessageController(
            service, commitService, exitService, new GameActionReplayStore(Clock.systemUTC()), template
        );
        String actionId = "44444444-4444-4444-8444-444444444444";
        ExitActiveGameCommand command = new ExitActiveGameCommand(actionId, 8L, 10L);
        when(exitService.exit(10L, 33L, 7L, 8L))
            .thenReturn(new GameExitResult(33L, GameExitService.EXIT_ACTIVE_GAME, 9L));

        controller.exit(33L, command, PRINCIPAL);
        controller.exit(33L, command, PRINCIPAL);

        verify(exitService, times(1)).exit(10L, 33L, 7L, 8L);
        List<GameCommandReply> replies = replies(template, 2);
        assertThat(replies).extracting(GameCommandReply::gameVersion).containsExactly(9L, 9L);
        assertThat(replies.get(1).duplicate()).isTrue();
        assertThat(replies.get(1).actionType()).isEqualTo(GameExitService.EXIT_ACTIVE_GAME);
    }

    @Test
    void commit012DuplicateCommitExecutesOnceAndReplaysTheCommittedVersion() {
        GameTurnCommandService service = mock(GameTurnCommandService.class);
        GameTurnCommitService commitService = mock(GameTurnCommitService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        GameMessageController controller = controller(service, commitService, template);
        String actionId = "12345678-1234-4234-8234-123456789012";
        CommitTurnCommand command = new CommitTurnCommand(
            actionId, 4L, List.of(
                new CommitTilePlacementCommand("RED-07-A", 0, 0),
                new CommitTilePlacementCommand("RED-08-A", 0, 1),
                new CommitTilePlacementCommand("RED-09-A", 0, 2)
            ), null
        );
        when(commitService.commit(33L, 7L, command)).thenReturn(new GameTurnCommandResult(33L, "COMMIT", 5L));

        controller.commit(33L, command, PRINCIPAL);
        controller.commit(33L, command, PRINCIPAL);

        verify(commitService, times(1)).commit(33L, 7L, command);
        List<GameCommandReply> replies = replies(template, 2);
        assertThat(replies).extracting(GameCommandReply::gameVersion).containsExactly(5L, 5L);
        assertThat(replies.get(1).duplicate()).isTrue();
        assertThat(replies.get(1).actionType()).isEqualTo("COMMIT");
    }

    @Test
    void malformedCommitShapeIsRejectedBeforeTheApplicationService() {
        GameTurnCommandService service = mock(GameTurnCommandService.class);
        GameTurnCommitService commitService = mock(GameTurnCommitService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        GameMessageController controller = controller(service, commitService, template);

        controller.commit(33L, new CommitTurnCommand(
            "12345678-1234-4234-8234-123456789012", 0L, List.of()
        ), PRINCIPAL);

        verify(commitService, never()).commit(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any());
        assertThat(replies(template, 1)).extracting(GameCommandReply::code)
            .containsExactly("EMPTY_MELD_SUBMISSION");
    }

    @Test
    void beP7007UnexpectedCommitFailureStillReturnsTheOriginalActionId() {
        GameTurnCommandService service = mock(GameTurnCommandService.class);
        GameTurnCommitService commitService = mock(GameTurnCommitService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        GameMessageController controller = controller(service, commitService, template);
        String actionId = "77777777-7777-4777-8777-777777777777";
        String meldId = "88888888-8888-4888-8888-888888888888";
        CommitTurnCommand command = new CommitTurnCommand(
            actionId, 9L,
            List.of(new CommitTableMeldCommand(meldId, List.of("RED-09-A", "RED-10-A", "RED-11-A")))
        );
        when(commitService.commit(33L, 7L, command))
            .thenThrow(new IllegalStateException("internal invariant detail"));

        controller.commit(33L, command, PRINCIPAL);

        GameCommandReply reply = replies(template, 1).get(0);
        assertThat(reply.accepted()).isFalse();
        assertThat(reply.actionId()).isEqualTo(actionId);
        assertThat(reply.gameId()).isEqualTo(33L);
        assertThat(reply.code()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(reply.message()).doesNotContain("invariant");
    }

    private static GameMessageController controller(GameTurnCommandService service,
                                                    SimpMessagingTemplate template) {
        return controller(service, mock(GameTurnCommitService.class), template);
    }

    private static GameMessageController controller(GameTurnCommandService service,
                                                    GameTurnCommitService commitService,
                                                    SimpMessagingTemplate template) {
        return new GameMessageController(
            service,
            commitService,
            new GameActionReplayStore(Clock.systemUTC()),
            template
        );
    }

    private static List<GameCommandReply> replies(SimpMessagingTemplate template, int count) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template, times(count)).convertAndSendToUser(eq("7"), eq("/queue/replies"), payload.capture());
        return payload.getAllValues().stream().map(GameCommandReply.class::cast).toList();
    }
}

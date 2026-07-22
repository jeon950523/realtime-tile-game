package com.realtimetilegame.room;

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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.GameStartService;
import com.realtimetilegame.game.application.dto.GameStartResult;
import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.room.application.dto.RoomStartEligibility;
import com.realtimetilegame.room.domain.ReadyStatus;
import com.realtimetilegame.room.presentation.RoomMessageController;
import com.realtimetilegame.room.websocket.ActionReplayStore;
import com.realtimetilegame.room.websocket.RoomCommandReply;
import com.realtimetilegame.room.websocket.RoomReadyCommand;
import com.realtimetilegame.room.websocket.RoomStartCommand;
import com.realtimetilegame.websocket.auth.StompPrincipal;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

class RoomMessageControllerTest {
    private static final StompPrincipal PRINCIPAL = new StompPrincipal(7L, Instant.now().plusSeconds(300));

    @Test
    void invalidUuidIsRejectedWithoutReplayEntry() {
        RoomCommandService roomService = mock(RoomCommandService.class);
        GameStartService gameStartService = mock(GameStartService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        RoomMessageController controller = controller(roomService, gameStartService, template);

        controller.ready(10L, new RoomReadyCommand("not-a-uuid", true), PRINCIPAL);

        verify(roomService, never()).changeReady(10L, 7L, true);
        RoomCommandReply reply = replies(template, 1).get(0);
        assertThat(reply.eventType()).isEqualTo("ROOM_COMMAND_REJECTED");
        assertThat(reply.code()).isEqualTo("INVALID_ROOM_ACTION_ID");
    }

    @Test
    void duplicateReadyActionExecutesDomainCommandOnceAndReplaysReply() {
        RoomCommandService roomService = mock(RoomCommandService.class);
        GameStartService gameStartService = mock(GameStartService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        RoomMessageController controller = controller(roomService, gameStartService, template);
        String actionId = "2718970d-27d1-4434-9a27-573a666943fc";
        when(roomService.changeReady(10L, 7L, true)).thenReturn(readyResult());

        controller.ready(10L, new RoomReadyCommand(actionId, true), PRINCIPAL);
        controller.ready(10L, new RoomReadyCommand(actionId, true), PRINCIPAL);

        verify(roomService, times(1)).changeReady(10L, 7L, true);
        List<RoomCommandReply> replies = replies(template, 2);
        assertThat(replies.get(0).eventType()).isEqualTo("ROOM_COMMAND_ACCEPTED");
        assertThat(replies.get(1).eventType()).isEqualTo("DUPLICATE_ACTION_REPLAYED");
    }

    @Test
    void successfulStartActionIsReplayedWithTheSameGameIdWithoutDomainReexecution() {
        RoomCommandService roomService = mock(RoomCommandService.class);
        GameStartService gameStartService = mock(GameStartService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        RoomMessageController controller = controller(roomService, gameStartService, template);
        String actionId = "0774a6d6-d601-478d-94b7-7ec2e1203f03";
        GameStartResult result = successfulStart();
        when(gameStartService.startGame(10L, 7L)).thenReturn(result);

        controller.start(10L, new RoomStartCommand(actionId), PRINCIPAL);
        controller.start(10L, new RoomStartCommand(actionId), PRINCIPAL);

        verify(gameStartService, times(1)).startGame(10L, 7L);
        List<RoomCommandReply> replies = replies(template, 2);
        assertThat(replies.get(0).eventType()).isEqualTo("GAME_START_ACCEPTED");
        assertThat(replies.get(0).payload()).isEqualTo(result);
        assertThat(replies.get(1).eventType()).isEqualTo("DUPLICATE_ACTION_REPLAYED");
        assertThat(replies.get(1).payload()).isEqualTo(result);
    }

    @Test
    void rejectedStartActionIsReplayedWithoutDomainReexecution() {
        RoomCommandService roomService = mock(RoomCommandService.class);
        GameStartService gameStartService = mock(GameStartService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        RoomMessageController controller = controller(roomService, gameStartService, template);
        String actionId = "d4a0d614-af23-4d2d-91c4-c08f03d156a0";
        when(gameStartService.startGame(10L, 7L))
            .thenThrow(new BusinessException(ErrorCode.ROOM_PLAYERS_NOT_READY));

        controller.start(10L, new RoomStartCommand(actionId), PRINCIPAL);
        controller.start(10L, new RoomStartCommand(actionId), PRINCIPAL);

        verify(gameStartService, times(1)).startGame(10L, 7L);
        List<RoomCommandReply> replies = replies(template, 2);
        assertThat(replies.get(0).eventType()).isEqualTo("ROOM_COMMAND_REJECTED");
        assertThat(replies.get(1).eventType()).isEqualTo("DUPLICATE_ACTION_REPLAYED");
        assertThat(replies).extracting(RoomCommandReply::code)
            .containsExactly("ROOM_PLAYERS_NOT_READY", "ROOM_PLAYERS_NOT_READY");
    }

    @Test
    void rejectedReadyActionIsReplayedWithoutDomainReexecution() {
        RoomCommandService roomService = mock(RoomCommandService.class);
        GameStartService gameStartService = mock(GameStartService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        RoomMessageController controller = controller(roomService, gameStartService, template);
        String actionId = "8155f288-1538-459d-8055-4c786071bd73";
        when(roomService.changeReady(10L, 7L, true))
            .thenThrow(new BusinessException(ErrorCode.ROOM_MEMBERSHIP_REQUIRED));

        controller.ready(10L, new RoomReadyCommand(actionId, true), PRINCIPAL);
        controller.ready(10L, new RoomReadyCommand(actionId, true), PRINCIPAL);

        verify(roomService, times(1)).changeReady(10L, 7L, true);
        assertThat(replies(template, 2)).extracting(RoomCommandReply::code)
            .containsExactly("ROOM_MEMBERSHIP_REQUIRED", "ROOM_MEMBERSHIP_REQUIRED");
    }

    @Test
    void stateChangeAfterInitialRejectionDoesNotChangeDuplicateReply() {
        RoomCommandService roomService = mock(RoomCommandService.class);
        GameStartService gameStartService = mock(GameStartService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        RoomMessageController controller = controller(roomService, gameStartService, template);
        String actionId = "69abf127-1adc-41be-a336-64f27446a2e3";
        when(gameStartService.startGame(10L, 7L))
            .thenThrow(new BusinessException(ErrorCode.ROOM_PLAYERS_NOT_READY))
            .thenReturn(successfulStart());

        controller.start(10L, new RoomStartCommand(actionId), PRINCIPAL);
        controller.start(10L, new RoomStartCommand(actionId), PRINCIPAL);

        verify(gameStartService, times(1)).startGame(10L, 7L);
        List<RoomCommandReply> replies = replies(template, 2);
        assertThat(replies.get(1).code()).isEqualTo("ROOM_PLAYERS_NOT_READY");
        assertThat(replies.get(1).message()).isEqualTo(replies.get(0).message());
    }

    @Test
    void concurrentDuplicateRejectedActionExecutesDomainOnce() throws Exception {
        RoomCommandService roomService = mock(RoomCommandService.class);
        GameStartService gameStartService = mock(GameStartService.class);
        SimpMessagingTemplate template = mock(SimpMessagingTemplate.class);
        RoomMessageController controller = controller(roomService, gameStartService, template);
        String actionId = "d880f9e4-1208-4d4e-b55b-564209018d0f";
        CountDownLatch domainEntered = new CountDownLatch(1);
        CountDownLatch releaseDomain = new CountDownLatch(1);
        when(gameStartService.startGame(10L, 7L)).thenAnswer(invocation -> {
            domainEntered.countDown();
            assertThat(releaseDomain.await(2, TimeUnit.SECONDS)).isTrue();
            throw new BusinessException(ErrorCode.ROOM_PLAYERS_NOT_READY);
        });

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> controller.start(10L, new RoomStartCommand(actionId), PRINCIPAL));
            assertThat(domainEntered.await(2, TimeUnit.SECONDS)).isTrue();
            Future<?> duplicate = executor.submit(() -> controller.start(10L, new RoomStartCommand(actionId), PRINCIPAL));
            releaseDomain.countDown();
            first.get(2, TimeUnit.SECONDS);
            duplicate.get(2, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        verify(gameStartService, times(1)).startGame(10L, 7L);
        assertThat(replies(template, 2)).extracting(RoomCommandReply::code)
            .containsOnly("ROOM_PLAYERS_NOT_READY");
    }

    private static RoomMessageController controller(RoomCommandService roomService,
                                                    GameStartService gameStartService,
                                                    SimpMessagingTemplate template) {
        return new RoomMessageController(
            roomService,
            gameStartService,
            new ActionReplayStore(Clock.systemUTC()),
            template
        );
    }

    private static RoomCommandService.ReadyChangeResult readyResult() {
        return new RoomCommandService.ReadyChangeResult(
            true, ReadyStatus.READY, new RoomStartEligibility(false, "ROOM_MIN_PLAYERS_NOT_MET", 1));
    }

    private static GameStartResult successfulStart() {
        return new GameStartResult(77L, 10L, "IN_PROGRESS", 7L, 1, 1, 2);
    }

    private static List<RoomCommandReply> replies(SimpMessagingTemplate template, int count) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(template, times(count)).convertAndSendToUser(eq("7"), eq("/queue/replies"), payload.capture());
        return payload.getAllValues().stream().map(RoomCommandReply.class::cast).toList();
    }
}

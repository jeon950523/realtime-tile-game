package com.realtimetilegame.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.room.application.dto.RoomDetail;
import com.realtimetilegame.room.event.RealtimeEvent;
import com.realtimetilegame.support.DatabaseCleanup;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class RoomEventAfterCommitIntegrationTest {
    @Autowired RoomCommandService commandService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoBean SimpMessagingTemplate messagingTemplate;

    @BeforeEach
    void setUp() {
        DatabaseCleanup.clear(jdbcTemplate);
        reset(messagingTemplate);
    }

    @Test
    void committedRoomCreationPublishesLobbyEventOnce() {
        User user = user("commit@example.com", "commitUser");

        commandService.create(user.id(), "커밋방", 4, "CLASSIC", 120, true);

        verify(messagingTemplate, timeout(1_000)).convertAndSend(
            eq("/topic/lobby/rooms"), org.mockito.ArgumentMatchers.any(RealtimeEvent.class));
    }

    @Test
    void rolledBackRoomCreationDoesNotPublishAfterCommitEvent() {
        User user = user("rollback@example.com", "rollbackUser");
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            commandService.create(user.id(), "롤백방", 4, "CLASSIC", 120, true);
            status.setRollbackOnly();
        });

        verifyNoInteractions(messagingTemplate);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rooms", Integer.class)).isZero();
    }

    @Test
    void readyChangePublishesRoomEventAndSnapshotAfterCommit() {
        User owner = user("ready-owner@example.com", "readyOwner");
        long roomId = commandService.create(owner.id(), "준비방", 4, "CLASSIC", 120, true).roomId();
        reset(messagingTemplate);

        commandService.changeReady(roomId, owner.id(), true);

        assertThat(roomEvents(roomId).stream().map(RealtimeEvent::eventType).toList())
            .containsExactly("ROOM_READY_CHANGED", "ROOM_STATE_UPDATED");
    }

    @Test
    void playerJoinPublishesRoomSnapshotAndLobbyUpdateAfterCommit() {
        User owner = user("join-owner@example.com", "joinOwner");
        User joining = user("join-user@example.com", "joinUser");
        long roomId = commandService.create(owner.id(), "입장방", 4, "CLASSIC", 120, true).roomId();
        reset(messagingTemplate);

        commandService.join(roomId, joining.id());

        assertThat(roomEvents(roomId).stream().map(RealtimeEvent::eventType).toList())
            .containsExactly("ROOM_PLAYER_JOINED", "ROOM_STATE_UPDATED");
        ArgumentCaptor<RealtimeEvent> lobbyEvent = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(messagingTemplate).convertAndSend(eq("/topic/lobby/rooms"), lobbyEvent.capture());
        assertThat(lobbyEvent.getValue().eventType()).isEqualTo("ROOM_UPDATED");
    }

    @Test
    void joiningNotReadyPlayerChangesEligibilityToFalse() {
        User owner = user("join-state-owner@example.com", "joinStateOwner");
        User readyPlayer = user("join-state-ready@example.com", "joinStateReady");
        User joining = user("join-state-new@example.com", "joinStateNew");
        long roomId = commandService.create(owner.id(), "입장정합방", 3, "CLASSIC", 120, true).roomId();
        commandService.join(roomId, readyPlayer.id());
        commandService.changeReady(roomId, owner.id(), true);
        commandService.changeReady(roomId, readyPlayer.id(), true);
        reset(messagingTemplate);

        commandService.join(roomId, joining.id());

        RoomDetail snapshot = latestSnapshot(roomId);
        assertThat(snapshot.currentPlayers()).isEqualTo(3);
        assertThat(snapshot.startable()).isFalse();
        assertThat(snapshot.startBlockReason()).isEqualTo("ROOM_PLAYERS_NOT_READY");
    }

    @Test
    void leavingToOnePlayerChangesEligibilityToFalse() {
        User owner = user("leave-one-owner@example.com", "leaveOneOwner");
        User second = user("leave-one-second@example.com", "leaveOneSecond");
        long roomId = commandService.create(owner.id(), "최소인원방", 4, "CLASSIC", 120, true).roomId();
        commandService.join(roomId, second.id());
        commandService.changeReady(roomId, owner.id(), true);
        commandService.changeReady(roomId, second.id(), true);
        reset(messagingTemplate);

        commandService.leave(roomId, second.id());

        RoomDetail snapshot = latestSnapshot(roomId);
        assertThat(snapshot.currentPlayers()).isEqualTo(1);
        assertThat(snapshot.startable()).isFalse();
        assertThat(snapshot.startBlockReason()).isEqualTo("ROOM_MIN_PLAYERS_NOT_MET");
    }

    @Test
    void leavingOnlyNotReadyPlayerCanChangeEligibilityToTrue() {
        User owner = user("leave-ready-owner@example.com", "leaveReadyOwner");
        User readyPlayer = user("leave-ready-second@example.com", "leaveReadySecond");
        User notReadyPlayer = user("leave-ready-third@example.com", "leaveReadyThird");
        long roomId = commandService.create(owner.id(), "이탈준비방", 4, "CLASSIC", 120, true).roomId();
        commandService.join(roomId, readyPlayer.id());
        commandService.join(roomId, notReadyPlayer.id());
        commandService.changeReady(roomId, owner.id(), true);
        commandService.changeReady(roomId, readyPlayer.id(), true);
        reset(messagingTemplate);

        commandService.leave(roomId, notReadyPlayer.id());

        RoomDetail snapshot = latestSnapshot(roomId);
        assertThat(snapshot.currentPlayers()).isEqualTo(2);
        assertThat(snapshot.startable()).isTrue();
        assertThat(snapshot.startBlockReason()).isNull();
    }

    @Test
    void ownerTransferPublishesFinalConsistentRoomState() {
        User owner = user("owner-transfer-owner@example.com", "transferOwner");
        User nextOwner = user("owner-transfer-next@example.com", "transferNext");
        User third = user("owner-transfer-third@example.com", "transferThird");
        long roomId = commandService.create(owner.id(), "방장위임방", 4, "CLASSIC", 120, true).roomId();
        commandService.join(roomId, nextOwner.id());
        commandService.join(roomId, third.id());
        reset(messagingTemplate);

        commandService.leave(roomId, owner.id());

        List<RealtimeEvent> events = roomEvents(roomId);
        assertThat(events.stream().map(RealtimeEvent::eventType).toList())
            .containsExactly("ROOM_PLAYER_LEFT", "ROOM_OWNER_CHANGED", "ROOM_STATE_UPDATED");
        RoomDetail snapshot = snapshot(events);
        assertThat(snapshot.ownerUserId()).isEqualTo(nextOwner.id());
        assertThat(snapshot.ownerNickname()).isEqualTo(nextOwner.nickname());
        assertThat(snapshot.participants()).hasSize(2);
        assertThat(snapshot.participants().stream().filter(participant -> participant.owner()).toList())
            .singleElement()
            .satisfies(participant -> assertThat(participant.userId()).isEqualTo(nextOwner.id()));
    }

    @Test
    void roomSnapshotEventIsPublishedAfterCommitOnly() {
        User owner = user("snapshot-rollback-owner@example.com", "snapshotRollbackOwner");
        User joining = user("snapshot-rollback-join@example.com", "snapshotRollbackJoin");
        long roomId = commandService.create(owner.id(), "스냅샷롤백방", 4, "CLASSIC", 120, true).roomId();
        reset(messagingTemplate);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);

        transaction.executeWithoutResult(status -> {
            commandService.join(roomId, joining.id());
            status.setRollbackOnly();
        });

        verifyNoInteractions(messagingTemplate);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM room_players WHERE room_id = ? AND left_at IS NULL", Integer.class, roomId))
            .isEqualTo(1);
    }

    private List<RealtimeEvent> roomEvents(long roomId) {
        ArgumentCaptor<RealtimeEvent> event = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(messagingTemplate, atLeastOnce()).convertAndSend(eq("/topic/rooms/" + roomId), event.capture());
        return event.getAllValues();
    }

    private RoomDetail latestSnapshot(long roomId) {
        return snapshot(roomEvents(roomId));
    }

    private static RoomDetail snapshot(List<RealtimeEvent> events) {
        return events.stream()
            .filter(event -> "ROOM_STATE_UPDATED".equals(event.eventType()))
            .map(event -> (RoomDetail) event.payload())
            .reduce((first, second) -> second)
            .orElseThrow();
    }

    private User user(String email, String nickname) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return userRepository.saveAndFlush(User.register(email, passwordEncoder.encode("qwer1234!"), nickname, now));
    }
}

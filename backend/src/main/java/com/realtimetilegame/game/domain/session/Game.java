package com.realtimetilegame.game.domain.session;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import com.realtimetilegame.game.domain.rule.policy.GameMode;
import com.realtimetilegame.room.domain.Room;
import com.realtimetilegame.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "games")
public class Game {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false, updatable = false)
    private Room room;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_mode", nullable = false, length = 20, updatable = false)
    private GameMode gameMode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "current_turn_user_id", nullable = false)
    private User currentTurnUser;

    @Column(name = "current_turn_seat_order", nullable = false, columnDefinition = "TINYINT")
    private int currentTurnSeatOrder;

    @Column(name = "turn_number", nullable = false)
    private int turnNumber;

    @Column(name = "current_turn_id", nullable = false, length = 36)
    private String currentTurnId;

    @Column(name = "current_turn_started_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime currentTurnStartedAt;

    @Column(name = "current_turn_deadline_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime currentTurnDeadlineAt;

    @Column(name = "consecutive_pass_count", nullable = false)
    private int consecutivePassCount;

    @Column(name = "started_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime startedAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    @Column(name = "finished_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "termination_reason", length = 30)
    private GameTerminationReason terminationReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_user_id")
    private User winnerUser;

    @Version
    @Column(nullable = false)
    private long version;

    protected Game() {
    }

    private Game(Room room, User currentTurnUser, int currentTurnSeatOrder, String currentTurnId,
                 LocalDateTime now, int turnTimeLimitSeconds) {
        this.room = Objects.requireNonNull(room, "room must not be null");
        this.gameMode = GameMode.CLASSIC;
        this.status = GameStatus.IN_PROGRESS;
        this.currentTurnUser = Objects.requireNonNull(currentTurnUser, "currentTurnUser must not be null");
        this.currentTurnSeatOrder = requireSeatOrder(currentTurnSeatOrder);
        this.turnNumber = 1;
        setTurnRuntime(currentTurnId, now, turnTimeLimitSeconds);
        this.consecutivePassCount = 0;
        this.startedAt = now;
        this.updatedAt = now;
        this.finishedAt = null;
        this.terminationReason = null;
        this.winnerUser = null;
    }

    public static Game startClassic(Room room, User firstPlayer, int firstPlayerSeatOrder, String turnId,
                                    LocalDateTime now, int turnTimeLimitSeconds) {
        return new Game(room, firstPlayer, firstPlayerSeatOrder, turnId, now, turnTimeLimitSeconds);
    }

    public void advanceAfterDraw(User nextPlayer, int nextSeatOrder, String nextTurnId,
                                 LocalDateTime now, int turnTimeLimitSeconds) {
        requireInProgress();
        TurnRuntime nextTurn = validateNextTurn(nextPlayer, nextSeatOrder, nextTurnId, now, turnTimeLimitSeconds);
        applyTurnAdvance(nextTurn, 0);
    }

    public void advanceAfterPass(User nextPlayer, int nextSeatOrder, String nextTurnId,
                                 LocalDateTime now, int turnTimeLimitSeconds) {
        requireInProgress();
        int nextConsecutivePassCount = Math.addExact(this.consecutivePassCount, 1);
        TurnRuntime nextTurn = validateNextTurn(nextPlayer, nextSeatOrder, nextTurnId, now, turnTimeLimitSeconds);
        applyTurnAdvance(nextTurn, nextConsecutivePassCount);
    }

    public void advanceAfterMeld(User nextPlayer, int nextSeatOrder, String nextTurnId,
                                 LocalDateTime now, int turnTimeLimitSeconds) {
        requireInProgress();
        TurnRuntime nextTurn = validateNextTurn(nextPlayer, nextSeatOrder, nextTurnId, now, turnTimeLimitSeconds);
        applyTurnAdvance(nextTurn, 0);
    }

    public Long id() { return id; }
    public Room room() { return room; }
    public GameMode gameMode() { return gameMode; }
    public GameStatus status() { return status; }
    public User currentTurnUser() { return currentTurnUser; }
    public int currentTurnSeatOrder() { return currentTurnSeatOrder; }
    public int turnNumber() { return turnNumber; }
    public String currentTurnId() { return currentTurnId; }
    public LocalDateTime currentTurnStartedAt() { return currentTurnStartedAt; }
    public LocalDateTime currentTurnDeadlineAt() { return currentTurnDeadlineAt; }
    public int consecutivePassCount() { return consecutivePassCount; }
    public LocalDateTime startedAt() { return startedAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
    public LocalDateTime finishedAt() { return finishedAt; }
    public long version() { return version; }
    public GameTerminationReason terminationReason() { return terminationReason; }
    public User winnerUser() { return winnerUser; }

    public void finishByForfeit(User winner, LocalDateTime now) {
        requireInProgress();
        LocalDateTime finishedTime = Objects.requireNonNull(now, "now must not be null");
        this.status = GameStatus.FINISHED;
        this.terminationReason = GameTerminationReason.PLAYER_FORFEIT;
        this.winnerUser = Objects.requireNonNull(winner, "winner must not be null");
        this.finishedAt = finishedTime;
        this.updatedAt = finishedTime;
    }

    public void abortByPlayerLeft(LocalDateTime now) {
        requireInProgress();
        LocalDateTime finishedTime = Objects.requireNonNull(now, "now must not be null");
        this.status = GameStatus.ABORTED;
        this.terminationReason = GameTerminationReason.PLAYER_LEFT;
        this.winnerUser = null;
        this.finishedAt = finishedTime;
        this.updatedAt = finishedTime;
    }

    public void finishByRackExhaustion(User winner, LocalDateTime now) {
        requireInProgress();
        LocalDateTime finishedTime = Objects.requireNonNull(now, "now must not be null");
        this.status = GameStatus.FINISHED;
        this.terminationReason = GameTerminationReason.RACK_EXHAUSTED;
        this.winnerUser = Objects.requireNonNull(winner, "winner must not be null");
        this.finishedAt = finishedTime;
        this.updatedAt = finishedTime;
    }

    private TurnRuntime validateNextTurn(User nextPlayer, int nextSeatOrder, String nextTurnId,
                                         LocalDateTime now, int turnTimeLimitSeconds) {
        User validatedPlayer = Objects.requireNonNull(nextPlayer, "nextPlayer must not be null");
        int validatedSeatOrder = requireSeatOrder(nextSeatOrder);
        int nextTurnNumber = Math.addExact(this.turnNumber, 1);
        String validatedTurnId = requireTurnId(nextTurnId);
        LocalDateTime validatedStartedAt = Objects.requireNonNull(now, "now must not be null");
        if (turnTimeLimitSeconds <= 0) {
            throw new IllegalArgumentException("turnTimeLimitSeconds must be positive");
        }
        return new TurnRuntime(
                validatedPlayer,
                validatedSeatOrder,
                nextTurnNumber,
                validatedTurnId,
                validatedStartedAt,
                validatedStartedAt.plusSeconds(turnTimeLimitSeconds)
        );
    }

    private void applyTurnAdvance(TurnRuntime nextTurn, int nextConsecutivePassCount) {
        this.currentTurnUser = nextTurn.player();
        this.currentTurnSeatOrder = nextTurn.seatOrder();
        this.turnNumber = nextTurn.turnNumber();
        this.currentTurnId = nextTurn.turnId();
        this.currentTurnStartedAt = nextTurn.startedAt();
        this.currentTurnDeadlineAt = nextTurn.deadlineAt();
        this.consecutivePassCount = nextConsecutivePassCount;
        this.updatedAt = nextTurn.startedAt();
    }

    private void setTurnRuntime(String turnId, LocalDateTime now, int turnTimeLimitSeconds) {
        this.currentTurnId = requireTurnId(turnId);
        this.currentTurnStartedAt = Objects.requireNonNull(now, "now must not be null");
        if (turnTimeLimitSeconds <= 0) {
            throw new IllegalArgumentException("turnTimeLimitSeconds must be positive");
        }
        this.currentTurnDeadlineAt = now.plusSeconds(turnTimeLimitSeconds);
    }

    private void requireInProgress() {
        if (status != GameStatus.IN_PROGRESS) {
            throw new IllegalStateException("only an in-progress game can advance turns");
        }
    }

    private static String requireTurnId(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("turnId must not be blank");
        }
        try {
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("turnId must be a UUID", exception);
        }
    }

    private static int requireSeatOrder(int value) {
        if (value < 1 || value > 4) {
            throw new IllegalArgumentException("currentTurnSeatOrder must be between 1 and 4");
        }
        return value;
    }

    private record TurnRuntime(
            User player,
            int seatOrder,
            int turnNumber,
            String turnId,
            LocalDateTime startedAt,
            LocalDateTime deadlineAt
    ) {
    }
}

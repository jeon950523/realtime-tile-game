package com.realtimetilegame.game.domain.session;

import java.time.LocalDateTime;
import java.util.Objects;

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

    @Column(name = "started_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime startedAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    @Column(name = "finished_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime finishedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected Game() {
    }

    private Game(Room room, User currentTurnUser, int currentTurnSeatOrder, LocalDateTime now) {
        this.room = Objects.requireNonNull(room, "room must not be null");
        this.gameMode = GameMode.CLASSIC;
        this.status = GameStatus.IN_PROGRESS;
        this.currentTurnUser = Objects.requireNonNull(currentTurnUser, "currentTurnUser must not be null");
        this.currentTurnSeatOrder = requireSeatOrder(currentTurnSeatOrder);
        this.turnNumber = 1;
        this.startedAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
        this.finishedAt = null;
    }

    public static Game startClassic(Room room, User firstPlayer, int firstPlayerSeatOrder, LocalDateTime now) {
        return new Game(room, firstPlayer, firstPlayerSeatOrder, now);
    }

    public Long id() { return id; }
    public Room room() { return room; }
    public GameMode gameMode() { return gameMode; }
    public GameStatus status() { return status; }
    public User currentTurnUser() { return currentTurnUser; }
    public int currentTurnSeatOrder() { return currentTurnSeatOrder; }
    public int turnNumber() { return turnNumber; }
    public LocalDateTime startedAt() { return startedAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
    public LocalDateTime finishedAt() { return finishedAt; }
    public long version() { return version; }

    private static int requireSeatOrder(int value) {
        if (value < 1 || value > 4) {
            throw new IllegalArgumentException("currentTurnSeatOrder must be between 1 and 4");
        }
        return value;
    }
}

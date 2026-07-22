package com.realtimetilegame.game.domain.session;

import java.time.LocalDateTime;
import java.util.Objects;

import com.realtimetilegame.user.domain.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "game_players")
public class GamePlayer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    private Game game;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "seat_order", nullable = false, columnDefinition = "TINYINT", updatable = false)
    private int seatOrder;

    @Column(name = "initial_meld_completed", nullable = false)
    private boolean initialMeldCompleted;

    @Enumerated(EnumType.STRING)
    @Column(name = "participant_status", nullable = false, length = 20)
    private GamePlayerStatus participantStatus;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)", updatable = false)
    private LocalDateTime createdAt;

    protected GamePlayer() {
    }

    private GamePlayer(Game game, User user, int seatOrder, LocalDateTime createdAt) {
        this.game = Objects.requireNonNull(game, "game must not be null");
        this.user = Objects.requireNonNull(user, "user must not be null");
        if (seatOrder < 1 || seatOrder > 4) {
            throw new IllegalArgumentException("seatOrder must be between 1 and 4");
        }
        this.seatOrder = seatOrder;
        this.initialMeldCompleted = false;
        this.participantStatus = GamePlayerStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public static GamePlayer snapshot(Game game, User user, int seatOrder, LocalDateTime now) {
        return new GamePlayer(game, user, seatOrder, now);
    }

    public void completeInitialMeld() {
        this.initialMeldCompleted = true;
    }

    public Long id() { return id; }
    public Game game() { return game; }
    public User user() { return user; }
    public int seatOrder() { return seatOrder; }
    public boolean initialMeldCompleted() { return initialMeldCompleted; }
    public LocalDateTime createdAt() { return createdAt; }
    public GamePlayerStatus participantStatus() { return participantStatus; }

    public void forfeit() { this.participantStatus = GamePlayerStatus.FORFEITED; }
    public void winByForfeit() { this.participantStatus = GamePlayerStatus.WINNER; }
    public void abort() { this.participantStatus = GamePlayerStatus.ABORTED; }
}

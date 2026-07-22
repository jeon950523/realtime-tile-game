package com.realtimetilegame.game.domain.session;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

import com.realtimetilegame.game.domain.rule.model.MeldType;

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

@Entity
@Table(name = "game_melds")
public class GameMeld {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    private Game game;

    @Column(name = "meld_id", nullable = false, length = 36, updatable = false)
    private String meldId;

    @Column(name = "position_order", nullable = false)
    private int positionOrder;

    @Column(name = "grid_row", nullable = false)
    private int gridRow;

    @Column(name = "grid_column", nullable = false)
    private int gridColumn;

    @Enumerated(EnumType.STRING)
    @Column(name = "meld_type", nullable = false, length = 20)
    private MeldType meldType;

    @Column(nullable = false)
    private int score;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_game_player_id", nullable = false, updatable = false)
    private GamePlayer createdBy;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected GameMeld() {
    }

    private GameMeld(Game game, String meldId, int positionOrder, int gridRow, int gridColumn, MeldType meldType,
                     int score, GamePlayer createdBy, LocalDateTime now) {
        this.game = Objects.requireNonNull(game, "game must not be null");
        this.meldId = requireMeldId(meldId);
        this.positionOrder = requireNonNegative(positionOrder, "positionOrder");
        this.gridRow = requireNonNegative(gridRow, "gridRow");
        this.gridColumn = requireNonNegative(gridColumn, "gridColumn");
        this.meldType = Objects.requireNonNull(meldType, "meldType must not be null");
        this.score = requireNonNegative(score, "score");
        this.createdBy = Objects.requireNonNull(createdBy, "createdBy must not be null");
        if (!samePersistedGame(game, createdBy.game())) {
            throw new IllegalArgumentException("meld creator must belong to the same game");
        }
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public static GameMeld committed(Game game, String meldId, int positionOrder, MeldType meldType,
                                     int score, GamePlayer createdBy, LocalDateTime now) {
        return committed(
            game, meldId, positionOrder, positionOrder / 2, (positionOrder % 2) * 13,
            meldType, score, createdBy, now
        );
    }

    public static GameMeld committed(Game game, String meldId, int positionOrder,
                                     int gridRow, int gridColumn, MeldType meldType,
                                     int score, GamePlayer createdBy, LocalDateTime now) {
        return new GameMeld(game, meldId, positionOrder, gridRow, gridColumn, meldType, score, createdBy, now);
    }

    public void stagePosition(int temporaryPosition, LocalDateTime now) {
        this.positionOrder = requireNonNegative(temporaryPosition, "temporaryPosition");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void revalidateAndReposition(int nextPosition, MeldType nextType, int nextScore,
                                        LocalDateTime now) {
        revalidateAndReposition(nextPosition, gridRow, gridColumn, nextType, nextScore, now);
    }

    public void revalidateAndReposition(int nextPosition, int nextGridRow, int nextGridColumn,
                                        MeldType nextType, int nextScore, LocalDateTime now) {
        this.positionOrder = requireNonNegative(nextPosition, "nextPosition");
        this.gridRow = requireNonNegative(nextGridRow, "nextGridRow");
        this.gridColumn = requireNonNegative(nextGridColumn, "nextGridColumn");
        this.meldType = Objects.requireNonNull(nextType, "nextType must not be null");
        this.score = requireNonNegative(nextScore, "nextScore");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public Long id() { return id; }
    public Game game() { return game; }
    public String meldId() { return meldId; }
    public int positionOrder() { return positionOrder; }
    public int gridRow() { return gridRow; }
    public int gridColumn() { return gridColumn; }
    public MeldType meldType() { return meldType; }
    public int score() { return score; }
    public GamePlayer createdBy() { return createdBy; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }

    private static String requireMeldId(String value) {
        try {
            if (value == null || value.isBlank()) throw new IllegalArgumentException();
            return UUID.fromString(value).toString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("meldId must be a UUID", exception);
        }
    }

    private static int requireNonNegative(int value, String name) {
        if (value < 0) throw new IllegalArgumentException(name + " must not be negative");
        return value;
    }

    private static boolean samePersistedGame(Game left, Game right) {
        if (left == right) return true;
        return left.id() != null && left.id().equals(right.id());
    }
}

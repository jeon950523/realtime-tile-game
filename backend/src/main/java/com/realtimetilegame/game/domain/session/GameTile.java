package com.realtimetilegame.game.domain.session;

import java.time.LocalDateTime;
import java.util.Objects;

import com.realtimetilegame.game.domain.tile.TileId;

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
@Table(name = "game_tiles")
public class GameTile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "game_id", nullable = false, updatable = false)
    private Game game;

    @Column(name = "tile_id", nullable = false, length = 32, updatable = false)
    private String tileId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private GameTileLocation location;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_game_player_id")
    private GamePlayer owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_meld_id")
    private GameMeld meld;

    @Column(name = "position_order", nullable = false)
    private int positionOrder;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected GameTile() {
    }

    private GameTile(Game game, TileId tileId, GameTileLocation location, GamePlayer owner,
                     int positionOrder, LocalDateTime now) {
        this.game = Objects.requireNonNull(game, "game must not be null");
        this.tileId = Objects.requireNonNull(tileId, "tileId must not be null").value();
        this.location = Objects.requireNonNull(location, "location must not be null");
        this.owner = owner;
        this.meld = null;
        this.positionOrder = requirePosition(positionOrder);
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
        validateLocationLinks();
    }

    public static GameTile rack(Game game, GamePlayer owner, TileId tileId, int positionOrder, LocalDateTime now) {
        return new GameTile(game, tileId, GameTileLocation.RACK,
            Objects.requireNonNull(owner, "owner must not be null"), positionOrder, now);
    }

    public static GameTile pool(Game game, TileId tileId, int positionOrder, LocalDateTime now) {
        return new GameTile(game, tileId, GameTileLocation.POOL, null, positionOrder, now);
    }

    public void drawTo(GamePlayer nextOwner, int rackPosition, LocalDateTime now) {
        if (location != GameTileLocation.POOL || owner != null || meld != null) {
            throw new IllegalStateException("only an unowned pool tile can be drawn");
        }
        GamePlayer validatedOwner = Objects.requireNonNull(nextOwner, "nextOwner must not be null");
        if (!samePersistedGame(game, validatedOwner.game())) {
            throw new IllegalArgumentException("draw owner must belong to the same game");
        }
        this.location = GameTileLocation.RACK;
        this.owner = validatedOwner;
        this.meld = null;
        this.positionOrder = requirePosition(rackPosition);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        validateLocationLinks();
    }

    public void commitToTable(GameMeld targetMeld, int meldTilePosition, LocalDateTime now) {
        if (location != GameTileLocation.RACK || owner == null || meld != null) {
            throw new IllegalStateException("only an owned rack tile can be committed to the table");
        }
        GameMeld validatedMeld = Objects.requireNonNull(targetMeld, "targetMeld must not be null");
        if (!samePersistedGame(game, validatedMeld.game())) {
            throw new IllegalArgumentException("table meld must belong to the same game");
        }
        this.location = GameTileLocation.TABLE;
        this.owner = null;
        this.meld = validatedMeld;
        this.positionOrder = requirePosition(meldTilePosition);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        validateLocationLinks();
    }

    public void stageWithinTable(int temporaryPosition, LocalDateTime now) {
        if (location != GameTileLocation.TABLE || owner != null || meld == null) {
            throw new IllegalStateException("only a table tile can be staged within the table");
        }
        this.positionOrder = requirePosition(temporaryPosition);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        validateLocationLinks();
    }

    public void moveWithinTable(GameMeld targetMeld, int meldTilePosition, LocalDateTime now) {
        if (location != GameTileLocation.TABLE || owner != null || meld == null) {
            throw new IllegalStateException("only a table tile can move within the table");
        }
        GameMeld validatedMeld = Objects.requireNonNull(targetMeld, "targetMeld must not be null");
        if (!samePersistedGame(game, validatedMeld.game())) {
            throw new IllegalArgumentException("table meld must belong to the same game");
        }
        this.meld = validatedMeld;
        this.positionOrder = requirePosition(meldTilePosition);
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
        validateLocationLinks();
    }

    public Long id() { return id; }
    public Game game() { return game; }
    public TileId tileId() { return new TileId(tileId); }
    public GameTileLocation location() { return location; }
    public GamePlayer owner() { return owner; }
    public GameMeld meld() { return meld; }
    public int positionOrder() { return positionOrder; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }

    private void validateLocationLinks() {
        boolean valid = switch (location) {
            case RACK -> owner != null && meld == null;
            case POOL -> owner == null && meld == null;
            case TABLE -> owner == null && meld != null;
        };
        if (!valid) throw new IllegalArgumentException("tile location links are inconsistent");
    }

    private static int requirePosition(int value) {
        if (value < 0) throw new IllegalArgumentException("positionOrder must not be negative");
        return value;
    }

    private static boolean samePersistedGame(Game left, Game right) {
        if (left == right) return true;
        return left.id() != null && left.id().equals(right.id());
    }

}

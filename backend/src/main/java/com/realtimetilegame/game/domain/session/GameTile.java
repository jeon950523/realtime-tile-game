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
        if (positionOrder < 0) throw new IllegalArgumentException("positionOrder must not be negative");
        this.positionOrder = positionOrder;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
        validateLocationOwner();
    }

    public static GameTile rack(Game game, GamePlayer owner, TileId tileId, int positionOrder, LocalDateTime now) {
        return new GameTile(game, tileId, GameTileLocation.RACK,
            Objects.requireNonNull(owner, "owner must not be null"), positionOrder, now);
    }

    public static GameTile pool(Game game, TileId tileId, int positionOrder, LocalDateTime now) {
        return new GameTile(game, tileId, GameTileLocation.POOL, null, positionOrder, now);
    }

    public Long id() { return id; }
    public Game game() { return game; }
    public TileId tileId() { return new TileId(tileId); }
    public GameTileLocation location() { return location; }
    public GamePlayer owner() { return owner; }
    public int positionOrder() { return positionOrder; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }

    private void validateLocationOwner() {
        if (location == GameTileLocation.RACK && owner == null) {
            throw new IllegalArgumentException("rack tile must have an owner");
        }
        if (location != GameTileLocation.RACK && owner != null) {
            throw new IllegalArgumentException("non-rack tile must not have an owner");
        }
    }
}

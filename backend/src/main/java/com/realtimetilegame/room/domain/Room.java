package com.realtimetilegame.room.domain;

import java.time.LocalDateTime;
import java.util.Objects;

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

@Entity
@Table(name = "rooms")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_name", nullable = false, length = 50)
    private String roomName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private User owner;

    @Column(name = "max_players", nullable = false, columnDefinition = "TINYINT")
    private int maxPlayers;

    @Enumerated(EnumType.STRING)
    @Column(name = "game_mode", nullable = false, length = 20)
    private RoomGameMode gameMode;

    @Column(name = "turn_time_limit_seconds", nullable = false)
    private int turnTimeLimitSeconds;

    @Column(name = "game_time_limit_seconds")
    private Integer gameTimeLimitSeconds;

    @Column(name = "is_public", nullable = false)
    private boolean publicRoom;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RoomStatus status;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    @Column(name = "closed_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime closedAt;

    protected Room() {
    }

    private Room(String roomName, User owner, int maxPlayers, RoomGameMode gameMode,
                 int turnTimeLimitSeconds, boolean publicRoom, LocalDateTime now) {
        this.roomName = requireRoomName(roomName);
        this.owner = Objects.requireNonNull(owner, "owner must not be null");
        this.maxPlayers = requireMaxPlayers(maxPlayers);
        this.gameMode = Objects.requireNonNull(gameMode, "gameMode must not be null");
        this.turnTimeLimitSeconds = requireTurnTimeLimit(turnTimeLimitSeconds);
        this.gameTimeLimitSeconds = null;
        this.publicRoom = publicRoom;
        this.status = RoomStatus.WAITING;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public static Room createClassic(String roomName, User owner, int maxPlayers,
                                     int turnTimeLimitSeconds, LocalDateTime now) {
        return new Room(roomName, owner, maxPlayers, RoomGameMode.CLASSIC,
            turnTimeLimitSeconds, true, now);
    }

    public void transferOwnership(User nextOwner, LocalDateTime now) {
        this.owner = Objects.requireNonNull(nextOwner, "nextOwner must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void startGame(LocalDateTime now) {
        if (status != RoomStatus.WAITING) {
            throw new IllegalStateException("only a waiting room can start a game");
        }
        this.status = RoomStatus.PLAYING;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void close(LocalDateTime now) {
        LocalDateTime closedTime = Objects.requireNonNull(now, "now must not be null");
        this.status = RoomStatus.CLOSED;
        this.closedAt = closedTime;
        this.updatedAt = closedTime;
    }

    public Long id() { return id; }
    public String roomName() { return roomName; }
    public User owner() { return owner; }
    public int maxPlayers() { return maxPlayers; }
    public RoomGameMode gameMode() { return gameMode; }
    public int turnTimeLimitSeconds() { return turnTimeLimitSeconds; }
    public Integer gameTimeLimitSeconds() { return gameTimeLimitSeconds; }
    public boolean publicRoom() { return publicRoom; }
    public RoomStatus status() { return status; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }
    public LocalDateTime closedAt() { return closedAt; }

    private static String requireRoomName(String value) {
        if (value == null) throw new IllegalArgumentException("roomName must not be null");
        String normalized = value.trim();
        if (normalized.length() < 2 || normalized.length() > 50 || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("roomName must be 2 to 50 characters without control characters");
        }
        return normalized;
    }

    private static int requireMaxPlayers(int value) {
        if (value < 2 || value > 4) throw new IllegalArgumentException("maxPlayers must be between 2 and 4");
        return value;
    }

    private static int requireTurnTimeLimit(int value) {
        if (value < 30 || value > 300) throw new IllegalArgumentException("turnTimeLimitSeconds must be between 30 and 300");
        return value;
    }
}

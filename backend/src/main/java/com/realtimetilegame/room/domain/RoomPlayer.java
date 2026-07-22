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
@Table(name = "room_players")
public class RoomPlayer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "seat_order", nullable = false, columnDefinition = "TINYINT")
    private int seatOrder;

    @Enumerated(EnumType.STRING)
    @Column(name = "ready_status", nullable = false, length = 20)
    private ReadyStatus readyStatus;

    @Column(name = "is_owner", nullable = false)
    private boolean owner;

    @Column(name = "joined_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime joinedAt;

    @Column(name = "left_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime leftAt;

    protected RoomPlayer() {
    }

    private RoomPlayer(Room room, User user, int seatOrder, boolean owner, LocalDateTime joinedAt) {
        this.room = Objects.requireNonNull(room, "room must not be null");
        this.user = Objects.requireNonNull(user, "user must not be null");
        if (seatOrder < 1 || seatOrder > 4) throw new IllegalArgumentException("seatOrder must be between 1 and 4");
        this.seatOrder = seatOrder;
        this.readyStatus = ReadyStatus.NOT_READY;
        this.owner = owner;
        this.joinedAt = Objects.requireNonNull(joinedAt, "joinedAt must not be null");
    }

    public static RoomPlayer join(Room room, User user, int seatOrder, boolean owner, LocalDateTime now) {
        return new RoomPlayer(room, user, seatOrder, owner, now);
    }

    public boolean changeReady(boolean ready) {
        ReadyStatus next = ready ? ReadyStatus.READY : ReadyStatus.NOT_READY;
        if (this.readyStatus == next) return false;
        this.readyStatus = next;
        return true;
    }

    public void leave(LocalDateTime now) {
        if (leftAt == null) leftAt = Objects.requireNonNull(now, "now must not be null");
        owner = false;
    }

    public void promoteOwner() { this.owner = true; }
    public void demoteOwner() { this.owner = false; }

    public Long id() { return id; }
    public Room room() { return room; }
    public User user() { return user; }
    public int seatOrder() { return seatOrder; }
    public ReadyStatus readyStatus() { return readyStatus; }
    public boolean owner() { return owner; }
    public LocalDateTime joinedAt() { return joinedAt; }
    public LocalDateTime leftAt() { return leftAt; }
    public boolean active() { return leftAt == null; }
}

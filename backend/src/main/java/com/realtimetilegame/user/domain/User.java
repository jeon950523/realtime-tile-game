package com.realtimetilegame.user.domain;

import java.time.LocalDateTime;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String email;

    @Column(name = "password", nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "avatar_type", nullable = false, length = 50)
    private AvatarType avatarType;

    @Column(name = "rating_score", nullable = false)
    private int ratingScore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at", columnDefinition = "DATETIME(6)")
    private LocalDateTime deletedAt;

    protected User() {
    }

    private User(String email, String passwordHash, String nickname, LocalDateTime now) {
        this.email = requireText(email, "email");
        this.passwordHash = requireText(passwordHash, "passwordHash");
        this.nickname = requireText(nickname, "nickname");
        this.avatarType = AvatarType.DEFAULT_01;
        this.ratingScore = 1000;
        this.status = UserStatus.ACTIVE;
        this.createdAt = Objects.requireNonNull(now, "now must not be null");
        this.updatedAt = now;
    }

    public static User register(String email, String passwordHash, String nickname, LocalDateTime now) {
        return new User(email, passwordHash, nickname, now);
    }

    public void updateProfile(String nickname, AvatarType avatarType, LocalDateTime now) {
        this.nickname = requireText(nickname, "nickname");
        this.avatarType = Objects.requireNonNull(avatarType, "avatarType must not be null");
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void block(LocalDateTime now) {
        this.status = UserStatus.BLOCKED;
        this.updatedAt = Objects.requireNonNull(now, "now must not be null");
    }

    public void delete(LocalDateTime now) {
        LocalDateTime deletedTime = Objects.requireNonNull(now, "now must not be null");
        this.status = UserStatus.DELETED;
        this.deletedAt = deletedTime;
        this.updatedAt = deletedTime;
    }

    public Long id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String nickname() {
        return nickname;
    }

    public AvatarType avatarType() {
        return avatarType;
    }

    public int ratingScore() {
        return ratingScore;
    }

    public UserStatus status() {
        return status;
    }

    public LocalDateTime createdAt() {
        return createdAt;
    }

    public LocalDateTime updatedAt() {
        return updatedAt;
    }

    public LocalDateTime deletedAt() {
        return deletedAt;
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}

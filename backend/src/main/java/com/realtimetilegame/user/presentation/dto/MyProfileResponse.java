package com.realtimetilegame.user.presentation.dto;

import com.realtimetilegame.user.domain.User;

public record MyProfileResponse(
    long userId,
    String email,
    String nickname,
    String avatarType,
    int ratingScore,
    RecordSummary classicRecord,
    RecordSummary speedRecord,
    ActiveSession activeSession
) {
    public static MyProfileResponse from(User user) {
        RecordSummary emptyRecord = new RecordSummary(0, 0, 0, 0);
        return new MyProfileResponse(
            user.id(),
            user.email(),
            user.nickname(),
            user.avatarType().name(),
            user.ratingScore(),
            emptyRecord,
            emptyRecord,
            new ActiveSession(null, null, null)
        );
    }

    public record RecordSummary(int wins, int losses, int draws, int totalGames) {
    }

    public record ActiveSession(Long roomId, Long gameId, String status) {
    }
}

package com.realtimetilegame.game;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDateTime;

import com.realtimetilegame.game.domain.session.Game;
import com.realtimetilegame.game.domain.session.GameStatus;
import com.realtimetilegame.room.domain.Room;
import com.realtimetilegame.room.domain.RoomStatus;
import com.realtimetilegame.user.domain.User;

import org.junit.jupiter.api.Test;

class RoomGameStartTransitionTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 15, 16, 40);

    @Test
    void waitingRoomTransitionsToPlayingExactlyOnce() {
        User owner = user("owner@example.com", "owner");
        Room room = Room.createClassic("시작방", owner, 4, 120, NOW);

        room.startGame(NOW.plusSeconds(1));

        assertThat(room.status()).isEqualTo(RoomStatus.PLAYING);
        assertThat(room.updatedAt()).isEqualTo(NOW.plusSeconds(1));
        assertThatThrownBy(() -> room.startGame(NOW.plusSeconds(2)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closedRoomCannotTransitionToPlaying() {
        User owner = user("owner@example.com", "owner");
        Room room = Room.createClassic("종료방", owner, 4, 120, NOW);
        room.close(NOW.plusSeconds(1));

        assertThatThrownBy(() -> room.startGame(NOW.plusSeconds(2)))
            .isInstanceOf(IllegalStateException.class);
        assertThat(room.status()).isEqualTo(RoomStatus.CLOSED);
    }

    @Test
    void classicGameStartsAtTurnOneWithAParticipantAsCurrentTurn() {
        User owner = user("owner@example.com", "owner");
        Room room = Room.createClassic("게임방", owner, 4, 120, NOW);

        Game game = Game.startClassic(room, owner, 1, NOW);

        assertThat(game.status()).isEqualTo(GameStatus.IN_PROGRESS);
        assertThat(game.currentTurnUser()).isSameAs(owner);
        assertThat(game.currentTurnSeatOrder()).isEqualTo(1);
        assertThat(game.turnNumber()).isEqualTo(1);
        assertThat(game.finishedAt()).isNull();
    }

    private static User user(String email, String nickname) {
        return User.register(email, "encoded", nickname, NOW);
    }
}

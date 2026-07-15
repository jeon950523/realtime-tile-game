package com.realtimetilegame.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.IntStream;

import com.realtimetilegame.game.application.GameQueryService;
import com.realtimetilegame.game.application.GameStartRandomizer;
import com.realtimetilegame.game.application.GameStartService;
import com.realtimetilegame.game.application.dto.GamePrivateState;
import com.realtimetilegame.game.application.dto.GameRackTileView;
import com.realtimetilegame.game.application.dto.GameStartResult;
import com.realtimetilegame.game.domain.state.InitialTileDistributor;
import com.realtimetilegame.game.domain.tile.Tile;
import com.realtimetilegame.game.domain.tile.TileSetFactory;
import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.support.DatabaseCleanup;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(GameStartDeterministicRandomizerIntegrationTest.FixedRandomizerConfiguration.class)
class GameStartDeterministicRandomizerIntegrationTest {
    @Autowired RoomCommandService roomCommandService;
    @Autowired GameStartService gameStartService;
    @Autowired GameQueryService gameQueryService;
    @Autowired UserRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock;

    @BeforeEach
    void clear() {
        DatabaseCleanup.clear(jdbcTemplate);
    }

    @Test
    void injectedRandomizerFixesFirstPlayerAndRoundRobinRackOrder() {
        User owner = user("fixed-owner@example.com", "fixedOwner");
        User second = user("fixed-second@example.com", "fixedSecond");
        long roomId = roomCommandService.create(owner.id(), "고정분배방", 2, "CLASSIC", 120, true).roomId();
        roomCommandService.join(roomId, second.id());
        roomCommandService.changeReady(roomId, owner.id(), true);
        roomCommandService.changeReady(roomId, second.id(), true);

        GameStartResult result = gameStartService.startGame(roomId, owner.id());
        GamePrivateState ownerState = gameQueryService.privateState(result.gameId(), owner.id());
        GamePrivateState secondState = gameQueryService.privateState(result.gameId(), second.id());
        List<Tile> standardTiles = TileSetFactory.createStandardSet();

        assertThat(result.currentTurnUserId()).isEqualTo(second.id());
        assertThat(result.currentTurnSeatOrder()).isEqualTo(2);
        assertThat(ownerState.myRack()).extracting(GameRackTileView::tileId).containsExactlyElementsOf(
            IntStream.range(0, InitialTileDistributor.INITIAL_RACK_SIZE)
                .mapToObj(round -> standardTiles.get(round * 2).id().value())
                .toList()
        );
        assertThat(secondState.myRack()).extracting(GameRackTileView::tileId).containsExactlyElementsOf(
            IntStream.range(0, InitialTileDistributor.INITIAL_RACK_SIZE)
                .mapToObj(round -> standardTiles.get(round * 2 + 1).id().value())
                .toList()
        );
    }

    private User user(String email, String nickname) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return userRepository.saveAndFlush(
            User.register(email, passwordEncoder.encode("qwer1234!"), nickname, now)
        );
    }

    @TestConfiguration
    static class FixedRandomizerConfiguration {
        @Bean
        @Primary
        GameStartRandomizer fixedGameStartRandomizer() {
            return new GameStartRandomizer() {
                @Override
                public List<Tile> shuffledTiles(List<Tile> tiles) {
                    return List.copyOf(tiles);
                }

                @Override
                public int firstPlayerIndex(int playerCount) {
                    return 1;
                }
            };
        }
    }
}

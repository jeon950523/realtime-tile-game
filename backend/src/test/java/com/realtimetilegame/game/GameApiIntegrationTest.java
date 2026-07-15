package com.realtimetilegame.game;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.realtimetilegame.game.application.GameStartService;
import com.realtimetilegame.game.application.dto.GameStartResult;
import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.security.JwtTokenService;
import com.realtimetilegame.support.DatabaseCleanup;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired RoomCommandService roomCommandService;
    @Autowired GameStartService gameStartService;
    @Autowired UserRepository userRepository;
    @Autowired JwtTokenService tokenService;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired Clock clock;

    @BeforeEach
    void clear() {
        DatabaseCleanup.clear(jdbcTemplate);
    }

    @Test
    void participantReadsPrivateStateWithOnlyOwnRackDetails() throws Exception {
        Fixture fixture = startTwoPlayerGame();

        mockMvc.perform(get("/api/games/{gameId}", fixture.result().gameId())
                .header(HttpHeaders.AUTHORIZATION, bearer(fixture.owner())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.publicState.gameId", is((int) fixture.result().gameId())))
            .andExpect(jsonPath("$.data.publicState.tilePoolCount", is(78)))
            .andExpect(jsonPath("$.data.publicState.tableMelds", hasSize(0)))
            .andExpect(jsonPath("$.data.publicState.players", hasSize(2)))
            .andExpect(jsonPath("$.data.publicState.players[0].rackTileCount", is(14)))
            .andExpect(jsonPath("$.data.publicState.players[1].rackTileCount", is(14)))
            .andExpect(jsonPath("$.data.myRack", hasSize(14)))
            .andExpect(jsonPath("$.data.myRack[0].tileId").isString())
            .andExpect(jsonPath("$.data.publicState.players[0].tileId").doesNotExist())
            .andExpect(jsonPath("$.data.publicState.players[1].myRack").doesNotExist());
    }

    @Test
    void nonMemberGameRestIsForbidden() throws Exception {
        Fixture fixture = startTwoPlayerGame();
        User outsider = user("outsider@example.com", "outsider");

        mockMvc.perform(get("/api/games/{gameId}", fixture.result().gameId())
                .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code", is("GAME_MEMBERSHIP_REQUIRED")));
    }

    @Test
    void activeGameRecoveryReturnsTheStartedGameAndAnonymousStateIsNotExposed() throws Exception {
        Fixture fixture = startTwoPlayerGame();

        mockMvc.perform(get("/api/me/active-game")
                .header(HttpHeaders.AUTHORIZATION, bearer(fixture.second())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.active", is(true)))
            .andExpect(jsonPath("$.data.gameId", is((int) fixture.result().gameId())))
            .andExpect(jsonPath("$.data.roomId", is((int) fixture.result().roomId())))
            .andExpect(jsonPath("$.data.status", is("IN_PROGRESS")));

        mockMvc.perform(get("/api/me/active-game"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void userWithoutActiveGameReceivesAnExplicitEmptyRecoveryResponse() throws Exception {
        User user = user("idle@example.com", "idle");

        mockMvc.perform(get("/api/me/active-game")
                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.active", is(false)))
            .andExpect(jsonPath("$.data.gameId", nullValue()))
            .andExpect(jsonPath("$.data.roomId", nullValue()))
            .andExpect(jsonPath("$.data.status", nullValue()));
    }

    private Fixture startTwoPlayerGame() {
        User owner = user("owner@example.com", "owner");
        User second = user("second@example.com", "second");
        long roomId = roomCommandService.create(owner.id(), "API게임방", 2, "CLASSIC", 120, true).roomId();
        roomCommandService.join(roomId, second.id());
        roomCommandService.changeReady(roomId, owner.id(), true);
        roomCommandService.changeReady(roomId, second.id(), true);
        GameStartResult result = gameStartService.startGame(roomId, owner.id());
        return new Fixture(owner, second, result);
    }

    private User user(String email, String nickname) {
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        return userRepository.saveAndFlush(
            User.register(email, passwordEncoder.encode("qwer1234!"), nickname, now)
        );
    }

    private String bearer(User user) {
        return "Bearer " + tokenService.issue(user).value();
    }

    private record Fixture(User owner, User second, GameStartResult result) {
    }
}

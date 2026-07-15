package com.realtimetilegame.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimetilegame.common.error.BusinessException;
import com.realtimetilegame.common.error.ErrorCode;
import com.realtimetilegame.game.application.GameStartService;
import com.realtimetilegame.room.application.RoomCommandService;
import com.realtimetilegame.room.application.RoomQueryService;
import com.realtimetilegame.room.application.dto.RoomDetail;
import com.realtimetilegame.room.domain.RoomPlayerRepository;
import com.realtimetilegame.support.DatabaseCleanup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomApiIntegrationTest {
    private static final String PASSWORD = "qwer1234!";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired RoomCommandService commandService;
    @Autowired GameStartService gameStartService;
    @Autowired RoomQueryService queryService;
    @Autowired RoomPlayerRepository playerRepository;

    @BeforeEach
    void clear() { DatabaseCleanup.clear(jdbcTemplate); }

    @ParameterizedTest
    @MethodSource("supportedMaxPlayers")
    void room001To003CreatesClassicRoomsForTwoThreeAndFourPlayers(int maxPlayers) throws Exception {
        Session owner = session("owner" + maxPlayers + "@example.com", "owner" + maxPlayers);
        create(owner.token(), "  초보방  ", maxPlayers, "CLASSIC", 120, true)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.roomName", is("초보방")))
            .andExpect(jsonPath("$.data.maxPlayers", is(maxPlayers)))
            .andExpect(jsonPath("$.data.gameMode", is("CLASSIC")))
            .andExpect(jsonPath("$.data.status", is("WAITING")))
            .andExpect(jsonPath("$.data.participants[0].seatOrder", is(1)))
            .andExpect(jsonPath("$.data.participants[0].readyStatus", is("NOT_READY")))
            .andExpect(jsonPath("$.data.participants[0].owner", is(true)));
    }

    static Stream<Arguments> supportedMaxPlayers() {
        return Stream.of(Arguments.of(2), Arguments.of(3), Arguments.of(4));
    }

    @ParameterizedTest
    @MethodSource("invalidMaxPlayers")
    void room004And005RejectsInvalidMaxPlayers(int maxPlayers) throws Exception {
        Session owner = session("owner@example.com", "owner");
        create(owner.token(), "초보방", maxPlayers, "CLASSIC", 120, true)
            .andExpect(status().isBadRequest());
    }

    static Stream<Arguments> invalidMaxPlayers() { return Stream.of(Arguments.of(1), Arguments.of(5)); }

    @Test
    void room006ClassicUsesRequestedDefaultTurnLimit() throws Exception {
        Session owner = session("owner@example.com", "owner");
        create(owner.token(), "초보방", 4, "CLASSIC", 120, true)
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.turnTimeLimitSeconds", is(120)));
    }

    @Test
    void room007RejectsSpeedAndPrivateRoom() throws Exception {
        Session owner = session("owner@example.com", "owner");
        create(owner.token(), "초보방", 4, "SPEED", 120, true)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("INVALID_GAME_MODE")));
        create(owner.token(), "초보방", 4, "CLASSIC", 120, false)
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("PRIVATE_ROOM_NOT_SUPPORTED")));
    }

    @Test
    void room008RejectsJoinWhenRoomIsFull() throws Exception {
        Session owner = session("owner@example.com", "owner");
        Session second = session("second@example.com", "second");
        Session third = session("third@example.com", "third");
        long roomId = createdRoomId(owner, 2);
        join(second.token(), roomId).andExpect(status().isOk());
        join(third.token(), roomId)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("ROOM_FULL")));
    }

    @Test
    void room009RejectsJoinWhenRoomIsAlreadyPlaying() throws Exception {
        Session owner = session("owner@example.com", "owner");
        Session joining = session("joining@example.com", "joining");
        long roomId = createdRoomId(owner, 4);
        jdbcTemplate.update("UPDATE rooms SET status='PLAYING' WHERE id=?", roomId);

        join(joining.token(), roomId)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("ROOM_ALREADY_PLAYING")));
    }

    @Test
    void rejectsTooShortAndControlCharacterRoomNames() throws Exception {
        Session owner = session("owner@example.com", "owner");
        create(owner.token(), " a ", 4, "CLASSIC", 120, true)
            .andExpect(status().isBadRequest());
        create(owner.token(), "정상\n아님", 4, "CLASSIC", 120, true)
            .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @MethodSource("invalidTurnLimits")
    void rejectsInvalidTurnTimeLimits(int seconds) throws Exception {
        Session owner = session("owner" + seconds + "@example.com", "owner" + seconds);
        create(owner.token(), "시간방", 4, "CLASSIC", seconds, true)
            .andExpect(status().isBadRequest());
    }

    static Stream<Arguments> invalidTurnLimits() { return Stream.of(Arguments.of(29), Arguments.of(301)); }

    @Test
    void room010BlocksOneUserFromMultipleActiveRooms() throws Exception {
        Session firstOwner = session("owner@example.com", "owner");
        Session user = session("user@example.com", "user");
        long roomId = createdRoomId(firstOwner, 4);
        join(user.token(), roomId).andExpect(status().isOk());
        create(user.token(), "다른방", 4, "CLASSIC", 120, true)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("USER_ALREADY_IN_ROOM")));
    }

    @Test
    void smallestEmptySeatIsReused() throws Exception {
        Session owner = session("owner@example.com", "owner");
        Session second = session("second@example.com", "second");
        Session third = session("third@example.com", "third");
        Session fourth = session("fourth@example.com", "fourth");
        long roomId = createdRoomId(owner, 4);
        join(second.token(), roomId).andExpect(jsonPath("$.data.participants[1].seatOrder", is(2)));
        join(third.token(), roomId).andExpect(status().isOk());
        leave(second.token(), roomId).andExpect(status().isNoContent());
        join(fourth.token(), roomId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.participants[1].userId", is((int) fourth.userId())))
            .andExpect(jsonPath("$.data.participants[1].seatOrder", is(2)));
    }

    @Test
    void room011OwnerLeavingTransfersOwnershipDeterministically() throws Exception {
        Session owner = session("owner@example.com", "owner");
        Session second = session("second@example.com", "second");
        Session third = session("third@example.com", "third");
        long roomId = createdRoomId(owner, 4);
        join(second.token(), roomId).andExpect(status().isOk());
        join(third.token(), roomId).andExpect(status().isOk());
        leave(owner.token(), roomId).andExpect(status().isNoContent());
        detail(second.token(), roomId)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.ownerUserId", is((int) second.userId())))
            .andExpect(jsonPath("$.data.participants[0].owner", is(true)));
    }

    @Test
    void lastPlayerLeavingClosesRoomAndClearsActiveRoom() throws Exception {
        Session owner = session("owner@example.com", "owner");
        long roomId = createdRoomId(owner, 2);
        leave(owner.token(), roomId).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/me/active-room").header(HttpHeaders.AUTHORIZATION, bearer(owner.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.active", is(false)))
            .andExpect(jsonPath("$.data.roomId", nullValue()));
        detail(owner.token(), roomId)
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("ROOM_CLOSED")));
    }

    @Test
    void room012To014StartEligibilityRequiresOwnerTwoPlayersAndEveryoneReady() throws Exception {
        Session owner = session("owner@example.com", "owner");
        Session second = session("second@example.com", "second");
        long roomId = createdRoomId(owner, 2);
        assertBusinessCode(() -> gameStartService.startGame(roomId, owner.userId()), ErrorCode.ROOM_MIN_PLAYERS_NOT_MET);
        join(second.token(), roomId).andExpect(status().isOk());
        assertBusinessCode(() -> gameStartService.startGame(roomId, owner.userId()), ErrorCode.ROOM_PLAYERS_NOT_READY);
        commandService.changeReady(roomId, owner.userId(), true);
        commandService.changeReady(roomId, second.userId(), true);
        assertBusinessCode(() -> gameStartService.startGame(roomId, second.userId()), ErrorCode.ROOM_OWNER_REQUIRED);
        assertThat(gameStartService.startGame(roomId, owner.userId()).playerCount()).isEqualTo(2);
        assertThat(queryService.detail(roomId, owner.userId()).status()).isEqualTo("PLAYING");
    }

    @Test
    void listsWaitingRoomsProvidesQuickMatchAndActiveRoomRecovery() throws Exception {
        Session owner = session("owner@example.com", "owner");
        Session viewer = session("viewer@example.com", "viewer");
        long roomId = createdRoomId(owner, 4);
        mockMvc.perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION, bearer(viewer.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].roomId", is((int) roomId)))
            .andExpect(jsonPath("$.data.content[0].joinable", is(true)));
        mockMvc.perform(get("/api/rooms/quick-match").header(HttpHeaders.AUTHORIZATION, bearer(viewer.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.roomId", is((int) roomId)));
        join(viewer.token(), roomId).andExpect(status().isOk());
        mockMvc.perform(get("/api/me/active-room").header(HttpHeaders.AUTHORIZATION, bearer(viewer.token())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.active", is(true)))
            .andExpect(jsonPath("$.data.roomId", is((int) roomId)))
            .andExpect(jsonPath("$.data.status", is("WAITING")));
    }

    @Test
    void nonMemberCannotReadRoomDetail() throws Exception {
        Session owner = session("owner@example.com", "owner");
        Session outsider = session("outsider@example.com", "outsider");
        long roomId = createdRoomId(owner, 4);
        detail(outsider.token(), roomId)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code", is("ROOM_MEMBERSHIP_REQUIRED")));
    }

    private void assertBusinessCode(ThrowingRunnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).errorCode())
            .isEqualTo(code);
    }

    private long createdRoomId(Session owner, int maxPlayers) throws Exception {
        MvcResult result = create(owner.token(), "초보방", maxPlayers, "CLASSIC", 120, true)
            .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("roomId").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions create(String token, String name, int maxPlayers,
                                                                       String mode, int limit, boolean publicRoom) throws Exception {
        String body = objectMapper.writeValueAsString(new CreateBody(name, maxPlayers, mode, limit, publicRoom));
        return mockMvc.perform(post("/api/rooms").header(HttpHeaders.AUTHORIZATION, bearer(token))
            .contentType(MediaType.APPLICATION_JSON).content(body));
    }
    private org.springframework.test.web.servlet.ResultActions join(String token, long roomId) throws Exception {
        return mockMvc.perform(post("/api/rooms/{roomId}/join", roomId).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }
    private org.springframework.test.web.servlet.ResultActions leave(String token, long roomId) throws Exception {
        return mockMvc.perform(delete("/api/rooms/{roomId}/members/me", roomId).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }
    private org.springframework.test.web.servlet.ResultActions detail(String token, long roomId) throws Exception {
        return mockMvc.perform(get("/api/rooms/{roomId}", roomId).header(HttpHeaders.AUTHORIZATION, bearer(token)));
    }

    private Session session(String email, String nickname) throws Exception {
        String register = objectMapper.writeValueAsString(new RegisterBody(email, PASSWORD, PASSWORD, nickname));
        String registered = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(register))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long userId = objectMapper.readTree(registered).path("data").path("userId").asLong();
        String login = objectMapper.writeValueAsString(new LoginBody(email, PASSWORD));
        String loggedIn = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(login))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(loggedIn).path("data").path("accessToken").asText();
        return new Session(userId, token);
    }

    private static String bearer(String token) { return "Bearer " + token; }
    private record Session(long userId, String token) {}
    private record RegisterBody(String email, String password, String passwordConfirm, String nickname) {}
    private record LoginBody(String email, String password) {}
    private record CreateBody(String roomName, int maxPlayers, String gameMode, int turnTimeLimitSeconds, boolean isPublic) {}
    @FunctionalInterface private interface ThrowingRunnable { void run() throws Exception; }
}

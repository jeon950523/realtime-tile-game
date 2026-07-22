package com.realtimetilegame.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimetilegame.auth.domain.RefreshToken;
import com.realtimetilegame.auth.domain.RefreshTokenRepository;
import com.realtimetilegame.auth.infrastructure.RefreshTokenGenerator;
import com.realtimetilegame.auth.infrastructure.RefreshTokenHasher;
import com.realtimetilegame.support.DatabaseCleanup;
import com.realtimetilegame.user.domain.User;
import com.realtimetilegame.user.domain.UserRepository;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTest {
    private static final String COOKIE_NAME = "rtg_refresh";
    private static final String PASSWORD = "qwer1234!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RefreshTokenRepository refreshTokenRepository;
    @Autowired
    private RefreshTokenHasher refreshTokenHasher;
    @Autowired
    private RefreshTokenGenerator refreshTokenGenerator;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private Clock clock;

    @BeforeEach
    void clearDatabase() {
        DatabaseCleanup.clear(jdbcTemplate);
    }

    @Test
    void auth001RegistersUserWithNormalizedValuesAndBcryptPassword() throws Exception {
        MvcResult result = register("  USER@Example.COM  ", PASSWORD, PASSWORD, "  Player_1  ")
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.email", is("user@example.com")))
            .andExpect(jsonPath("$.data.nickname", is("Player_1")))
            .andExpect(jsonPath("$.data.profileSetupRequired", is(true)))
            .andExpect(jsonPath("$.data.password").doesNotExist())
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        long userId = body.path("data").path("userId").asLong();
        User saved = userRepository.findById(userId).orElseThrow();
        assertThat(saved.email()).isEqualTo("user@example.com");
        assertThat(saved.nickname()).isEqualTo("Player_1");
        assertThat(saved.ratingScore()).isEqualTo(1000);
        assertThat(saved.avatarType().name()).isEqualTo("DEFAULT_01");
        assertThat(passwordEncoder.matches(PASSWORD, saved.passwordHash())).isTrue();
        assertThat(saved.passwordHash()).isNotEqualTo(PASSWORD);
    }

    @Test
    void auth002RejectsDuplicateEmailCaseInsensitively() throws Exception {
        registerDefault("player1");

        register("USER@EXAMPLE.COM", PASSWORD, PASSWORD, "player2")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("EMAIL_ALREADY_EXISTS")));
    }

    @Test
    void auth003RejectsDuplicateNicknameCaseInsensitively() throws Exception {
        registerDefault("PlayerOne");

        register("other@example.com", PASSWORD, PASSWORD, "playerone")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("NICKNAME_ALREADY_EXISTS")));
    }

    @Test
    void auth004RejectsPasswordConfirmationMismatch() throws Exception {
        register("user@example.com", PASSWORD, "different1!", "player1")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("PASSWORD_CONFIRM_MISMATCH")));
    }

    @Test
    void rejectsPasswordThatDoesNotMeetPolicy() throws Exception {
        register("user@example.com", "password", "password", "player1")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("VALIDATION_FAILED")))
            .andExpect(jsonPath("$.error.fieldErrors[0].field", is("password")));
    }

    @Test
    void auth005LoginReturnsAccessTokenAndHttpOnlyRefreshCookieWithHashedStorage() throws Exception {
        registerDefault("player1");

        MvcResult result = login("USER@example.com", PASSWORD)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken", not("")))
            .andExpect(jsonPath("$.data.expiresIn", is(1800)))
            .andExpect(jsonPath("$.data.user.nickname", is("player1")))
            .andExpect(jsonPath("$.data.redirect.type", is("LOBBY")))
            .andExpect(jsonPath("$.data.redirect.roomId", nullValue()))
            .andExpect(jsonPath("$.data.redirect.gameId", nullValue()))
            .andExpect(cookie().httpOnly(COOKIE_NAME, true))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=Strict")))
            .andReturn();

        Cookie cookie = result.getResponse().getCookie(COOKIE_NAME);
        assertThat(cookie).isNotNull();
        String rawRefreshToken = cookie.getValue();
        assertThat(result.getResponse().getContentAsString()).doesNotContain(rawRefreshToken);

        String storedHash = jdbcTemplate.queryForObject(
            "SELECT token_hash FROM refresh_tokens",
            String.class
        );
        assertThat(storedHash).isEqualTo(refreshTokenHasher.hash(rawRefreshToken));
        assertThat(storedHash).isNotEqualTo(rawRefreshToken);
    }

    @Test
    void auth006UsesSameErrorForUnknownEmailAndWrongPassword() throws Exception {
        registerDefault("player1");

        login("user@example.com", "wrong123!")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")));

        login("missing@example.com", "wrong123!")
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("INVALID_CREDENTIALS")));
    }

    @Test
    void auth007RejectsExpiredRefreshToken() throws Exception {
        User user = registerDefault("player1");
        String rawToken = refreshTokenGenerator.generate();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        refreshTokenRepository.save(RefreshToken.issue(
            user,
            refreshTokenHasher.hash(rawToken),
            now.minusSeconds(1),
            now.minusSeconds(60)
        ));

        mockMvc.perform(post("/api/auth/reissue").cookie(new Cookie(COOKIE_NAME, rawToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("REFRESH_TOKEN_EXPIRED")));
    }


    @Test
    void expiredRefreshTokenClearsCookie() throws Exception {
        User user = registerDefault("player1");
        String rawToken = refreshTokenGenerator.generate();
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        refreshTokenRepository.save(RefreshToken.issue(
            user,
            refreshTokenHasher.hash(rawToken),
            now.minusSeconds(1),
            now.minusSeconds(60)
        ));

        mockMvc.perform(post("/api/auth/reissue").cookie(new Cookie(COOKIE_NAME, rawToken)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("REFRESH_TOKEN_EXPIRED")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    void invalidRefreshTokenClearsCookie() throws Exception {
        mockMvc.perform(post("/api/auth/reissue").cookie(new Cookie(COOKIE_NAME, "invalid-refresh-token")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("REFRESH_TOKEN_INVALID")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    void reissueRotatesCookieRevokesPreviousTokenAndRejectsReuse() throws Exception {
        registerDefault("player1");
        Cookie firstCookie = loginCookie();

        MvcResult rotated = mockMvc.perform(post("/api/auth/reissue").cookie(firstCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.accessToken", not("")))
            .andExpect(cookie().httpOnly(COOKIE_NAME, true))
            .andReturn();

        Cookie secondCookie = rotated.getResponse().getCookie(COOKIE_NAME);
        assertThat(secondCookie).isNotNull();
        assertThat(secondCookie.getValue()).isNotEqualTo(firstCookie.getValue());
        RefreshToken firstStored = refreshTokenRepository
            .findByTokenHash(refreshTokenHasher.hash(firstCookie.getValue()))
            .orElseThrow();
        assertThat(firstStored.isRevoked()).isTrue();

        mockMvc.perform(post("/api/auth/reissue").cookie(firstCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("REFRESH_TOKEN_INVALID")));
    }

    @Test
    void auth008LogoutRevokesRefreshTokenAndBlocksReissue() throws Exception {
        registerDefault("player1");
        Cookie cookie = loginCookie();

        mockMvc.perform(post("/api/auth/logout").cookie(cookie))
            .andExpect(status().isNoContent())
            .andExpect(content().string(""))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));

        mockMvc.perform(post("/api/auth/reissue").cookie(cookie))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("REFRESH_TOKEN_INVALID")));
    }

    @Test
    void logoutIsIdempotentAndIgnoresExpiredBearerHeader() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer expired-or-invalid"))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));
    }

    @Test
    void auth010DeletedUserCannotLogin() throws Exception {
        User user = registerDefault("player1");
        user.delete(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        userRepository.saveAndFlush(user);

        login("user@example.com", PASSWORD)
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("USER_DELETED")));
    }

    @Test
    void blockedUserCannotLogin() throws Exception {
        User user = registerDefault("player1");
        user.block(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        userRepository.saveAndFlush(user);

        login("user@example.com", PASSWORD)
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code", is("USER_BLOCKED")));
    }

    private User registerDefault(String nickname) throws Exception {
        MvcResult result = register("user@example.com", PASSWORD, PASSWORD, nickname)
            .andExpect(status().isCreated())
            .andReturn();
        long id = objectMapper.readTree(result.getResponse().getContentAsString())
            .path("data").path("userId").asLong();
        return userRepository.findById(id).orElseThrow();
    }

    private Cookie loginCookie() throws Exception {
        Cookie cookie = login("user@example.com", PASSWORD)
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getCookie(COOKIE_NAME);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private org.springframework.test.web.servlet.ResultActions register(
        String email,
        String password,
        String passwordConfirm,
        String nickname
    ) throws Exception {
        String body = objectMapper.writeValueAsString(new RegisterBody(email, password, passwordConfirm, nickname));
        return mockMvc.perform(post("/api/auth/register")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private org.springframework.test.web.servlet.ResultActions login(String email, String password) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginBody(email, password));
        return mockMvc.perform(post("/api/auth/login")
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private record RegisterBody(String email, String password, String passwordConfirm, String nickname) {
    }

    private record LoginBody(String email, String password) {
    }
}

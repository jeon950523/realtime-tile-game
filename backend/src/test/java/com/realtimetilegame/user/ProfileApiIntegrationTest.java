package com.realtimetilegame.user;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.realtimetilegame.support.DatabaseCleanup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProfileApiIntegrationTest {
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String accessToken;

    @BeforeEach
    void prepareUser() throws Exception {
        DatabaseCleanup.clear(jdbcTemplate);
        register("user@example.com", "player1");
        accessToken = login("user@example.com");
    }

    @Test
    void profileReturnsPhase2Defaults() throws Exception {
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.email", is("user@example.com")))
            .andExpect(jsonPath("$.data.avatarType", is("DEFAULT_01")))
            .andExpect(jsonPath("$.data.ratingScore", is(1000)))
            .andExpect(jsonPath("$.data.classicRecord.wins", is(0)))
            .andExpect(jsonPath("$.data.speedRecord.totalGames", is(0)));
    }

    @Test
    void auth009UpdatesNicknameAndAvatarAtomically() throws Exception {
        update("newPlayer", "DEFAULT_03")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname", is("newPlayer")))
            .andExpect(jsonPath("$.data.avatarType", is("DEFAULT_03")));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname", is("newPlayer")))
            .andExpect(jsonPath("$.data.avatarType", is("DEFAULT_03")));
    }

    @Test
    void keepingOwnNicknameSucceeds() throws Exception {
        update("player1", "DEFAULT_02")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.nickname", is("player1")));
    }

    @Test
    void duplicateNicknameIsRejected() throws Exception {
        register("other@example.com", "otherPlayer");

        update("OTHERPLAYER", "DEFAULT_02")
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code", is("NICKNAME_ALREADY_EXISTS")));
    }

    @Test
    void invalidAvatarIsRejectedWithoutChangingNickname() throws Exception {
        update("changedName", "CUSTOM_99")
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code", is("INVALID_AVATAR_TYPE")));

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(jsonPath("$.data.nickname", is("player1")))
            .andExpect(jsonPath("$.data.avatarType", is("DEFAULT_01")));
    }

    private void register(String email, String nickname) throws Exception {
        String body = objectMapper.writeValueAsString(new RegisterBody(
            email,
            "qwer1234!",
            "qwer1234!",
            nickname
        ));
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());
    }

    private String login(String email) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginBody(email, "qwer1234!"));
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }

    private org.springframework.test.web.servlet.ResultActions update(String nickname, String avatarType) throws Exception {
        String body = objectMapper.writeValueAsString(new UpdateBody(nickname, avatarType));
        return mockMvc.perform(patch("/api/me/profile")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }

    private record RegisterBody(String email, String password, String passwordConfirm, String nickname) {
    }

    private record LoginBody(String email, String password) {
    }

    private record UpdateBody(String nickname, String avatarType) {
    }
}

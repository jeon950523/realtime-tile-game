package com.realtimetilegame.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
class ActiveUserAuthorizationIntegrationTest {
    private static final String PASSWORD = "qwer1234!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long userId;
    private String accessToken;

    @BeforeEach
    void prepareActiveUser() throws Exception {
        DatabaseCleanup.clear(jdbcTemplate);
        userId = register("user@example.com", "player1");
        accessToken = login("user@example.com");
    }

    @Test
    void issuedAccessTokenIsRejectedAfterUserBecomesBlocked() throws Exception {
        changeStatus("BLOCKED");

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code", is("USER_BLOCKED")));
    }

    @Test
    void issuedAccessTokenIsRejectedAfterUserBecomesDeleted() throws Exception {
        changeStatus("DELETED");

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("USER_DELETED")));
    }

    @Test
    void activeUserAccessTokenStillSucceeds() throws Exception {
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId", is((int) userId)));
    }

    @Test
    void blockedUserCannotUpdateProfile() throws Exception {
        changeStatus("BLOCKED");

        updateProfile()
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error.code", is("USER_BLOCKED")));
    }

    @Test
    void deletedUserCannotUpdateProfile() throws Exception {
        changeStatus("DELETED");

        updateProfile()
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("USER_DELETED")));
    }

    @Test
    void existingHealthAndPublicAuthPathsRemainPublic() throws Exception {
        changeStatus("BLOCKED");

        mockMvc.perform(get("/api/health").header(HttpHeaders.AUTHORIZATION, bearer()))
            .andExpect(status().isOk());

        String body = """
            {"email":"second@example.com","password":"qwer1234!","passwordConfirm":"qwer1234!","nickname":"player2"}
            """;
        mockMvc.perform(post("/api/auth/register")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated());
    }

    private long register(String email, String nickname) throws Exception {
        String body = objectMapper.writeValueAsString(new RegisterBody(email, PASSWORD, PASSWORD, nickname));
        String response = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("userId").asLong();
    }

    private String login(String email) throws Exception {
        String body = objectMapper.writeValueAsString(new LoginBody(email, PASSWORD));
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(response);
        return json.path("data").path("accessToken").asText();
    }

    private org.springframework.test.web.servlet.ResultActions updateProfile() throws Exception {
        String body = "{\"nickname\":\"newName\",\"avatarType\":\"DEFAULT_03\"}";
        return mockMvc.perform(patch("/api/me/profile")
            .header(HttpHeaders.AUTHORIZATION, bearer())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body));
    }

    private void changeStatus(String status) {
        jdbcTemplate.update("UPDATE users SET status = ? WHERE id = ?", status, userId);
    }

    private String bearer() {
        return "Bearer " + accessToken;
    }

    private record RegisterBody(String email, String password, String passwordConfirm, String nickname) {
    }

    private record LoginBody(String email, String password) {
    }
}

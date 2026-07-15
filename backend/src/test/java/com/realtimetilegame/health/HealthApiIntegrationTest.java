package com.realtimetilegame.health;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HealthApiIntegrationTest {
    private static final String FRONTEND_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsApplicationAndDatabaseHealth() throws Exception {
        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success", is(true)))
            .andExpect(jsonPath("$.data.status", is("UP")))
            .andExpect(jsonPath("$.data.database", is("UP")));
    }

    @Test
    void rejectsUnconfiguredPrivateEndpoint() throws Exception {
        mockMvc.perform(get("/api/private-probe"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.error.code", is("AUTHENTICATION_REQUIRED")));
    }

    @Test
    void allowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/health")
                .header(HttpHeaders.ORIGIN, FRONTEND_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, FRONTEND_ORIGIN));
    }
}

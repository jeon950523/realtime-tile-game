package com.realtimetilegame.security;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import com.realtimetilegame.support.DatabaseCleanup;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class JwtSecurityIntegrationTest {
    private static final String PASSWORD = "qwer1234!";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private SecretKey secretKey;
    @Autowired
    private JwtProperties properties;

    private long userId;

    @BeforeEach
    void registerUser() throws Exception {
        DatabaseCleanup.clear(jdbcTemplate);
        String registerBody = """
            {"email":"user@example.com","password":"qwer1234!","passwordConfirm":"qwer1234!","nickname":"player1"}
            """;
        String response = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerBody))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        userId = objectMapper.readTree(response).path("data").path("userId").asLong();
    }

    @Test
    void validAccessTokenCanReadMyProfile() throws Exception {
        String accessToken = loginAccessToken();

        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.userId", is((int) userId)))
            .andExpect(jsonPath("$.data.email", is("user@example.com")))
            .andExpect(jsonPath("$.data.classicRecord.totalGames", is(0)))
            .andExpect(jsonPath("$.data.activeSession.roomId").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void missingAccessTokenReturnsCommonUnauthorizedJson() throws Exception {
        mockMvc.perform(get("/api/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success", is(false)))
            .andExpect(jsonPath("$.error.code", is("AUTHENTICATION_REQUIRED")));
    }

    @Test
    void wrongSignatureIsRejected() throws Exception {
        SecretKey otherKey = new SecretKeySpec(new byte[32], "HmacSHA256");
        JwtEncoder otherEncoder = new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(otherKey));
        String token = encode(otherEncoder, properties.issuer(), List.of(properties.audience()), Long.toString(userId), true, validIssuedAt(), validExpiresAt());

        assertUnauthorized(token);
    }

    @Test
    void expiredAccessTokenIsRejected() throws Exception {
        Instant issuedAt = Instant.now().minus(2, ChronoUnit.HOURS);
        String token = encode(jwtEncoder, properties.issuer(), List.of(properties.audience()), Long.toString(userId), true, issuedAt, issuedAt.plusSeconds(60));

        assertUnauthorized(token);
    }

    @Test
    void wrongIssuerIsRejected() throws Exception {
        String token = encode(jwtEncoder, "wrong-issuer", List.of(properties.audience()), Long.toString(userId), true, validIssuedAt(), validExpiresAt());

        assertUnauthorized(token);
    }

    @Test
    void wrongAudienceIsRejected() throws Exception {
        String token = encode(jwtEncoder, properties.issuer(), List.of("wrong-audience"), Long.toString(userId), true, validIssuedAt(), validExpiresAt());

        assertUnauthorized(token);
    }

    @Test
    void nonNumericSubjectIsRejected() throws Exception {
        String token = encode(jwtEncoder, properties.issuer(), List.of(properties.audience()), "not-a-user-id", true, validIssuedAt(), validExpiresAt());

        assertUnauthorized(token);
    }

    @Test
    void missingRoleClaimIsRejected() throws Exception {
        String token = encode(jwtEncoder, properties.issuer(), List.of(properties.audience()), Long.toString(userId), false, validIssuedAt(), validExpiresAt());

        assertUnauthorized(token);
    }


    @Test
    void missingIssuedAtClaimIsRejected() throws Exception {
        JwtClaimsSet claims = baseClaims().expiresAt(validExpiresAt()).id(UUID.randomUUID().toString()).build();
        assertUnauthorized(encode(claims));
    }

    @Test
    void missingExpirationClaimIsRejected() throws Exception {
        JwtClaimsSet claims = baseClaims().issuedAt(validIssuedAt()).id(UUID.randomUUID().toString()).build();
        assertUnauthorized(encode(claims));
    }

    @Test
    void missingJwtIdClaimIsRejected() throws Exception {
        JwtClaimsSet claims = baseClaims().issuedAt(validIssuedAt()).expiresAt(validExpiresAt()).build();
        assertUnauthorized(encode(claims));
    }

    @Test
    void blankJwtIdClaimIsRejected() throws Exception {
        JwtClaimsSet claims = baseClaims()
            .issuedAt(validIssuedAt())
            .expiresAt(validExpiresAt())
            .id(" ")
            .build();
        assertUnauthorized(encode(claims));
    }

    @Test
    void expirationBeforeIssuedAtIsRejected() throws Exception {
        Instant now = Instant.now();
        Instant issuedAt = now.plusSeconds(30);
        Instant expiresAt = now.plusSeconds(20);
        String token = encodeRawToken(issuedAt, expiresAt);
        assertUnauthorized(token);
    }

    @Test
    void issuedAtBeyondClockSkewIsRejected() throws Exception {
        Instant issuedAt = Instant.now().plusSeconds(120);
        JwtClaimsSet claims = baseClaims()
            .issuedAt(issuedAt)
            .expiresAt(issuedAt.plusSeconds(300))
            .id(UUID.randomUUID().toString())
            .build();
        assertUnauthorized(encode(claims));
    }

    private String loginAccessToken() throws Exception {
        String loginBody = objectMapper.writeValueAsString(new LoginBody("user@example.com", PASSWORD));
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return body.path("data").path("accessToken").asText();
    }

    private void assertUnauthorized(String token) throws Exception {
        mockMvc.perform(get("/api/me").header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code", is("AUTHENTICATION_REQUIRED")));
    }



    private String encodeRawToken(Instant issuedAt, Instant expiresAt) throws Exception {
        com.nimbusds.jwt.JWTClaimsSet claims = new com.nimbusds.jwt.JWTClaimsSet.Builder()
            .issuer(properties.issuer())
            .audience(properties.audience())
            .subject(Long.toString(userId))
            .issueTime(Date.from(issuedAt))
            .expirationTime(Date.from(expiresAt))
            .jwtID(UUID.randomUUID().toString())
            .claim("role", "USER")
            .build();
        com.nimbusds.jwt.SignedJWT token = new com.nimbusds.jwt.SignedJWT(
            new com.nimbusds.jose.JWSHeader(com.nimbusds.jose.JWSAlgorithm.HS256),
            claims
        );
        token.sign(new com.nimbusds.jose.crypto.MACSigner(secretKey.getEncoded()));
        return token.serialize();
    }

    private JwtClaimsSet.Builder baseClaims() {
        return JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .audience(List.of(properties.audience()))
            .subject(Long.toString(userId))
            .claim("role", "USER");
    }

    private String encode(JwtClaimsSet claims) {
        return jwtEncoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
            claims
        )).getTokenValue();
    }

    private String encode(
        JwtEncoder encoder,
        String issuer,
        List<String> audience,
        String subject,
        boolean includeRole,
        Instant issuedAt,
        Instant expiresAt
    ) {
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
            .issuer(issuer)
            .audience(audience)
            .subject(subject)
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .id(UUID.randomUUID().toString());
        if (includeRole) {
            claims.claim("role", "USER");
        }
        return encoder.encode(JwtEncoderParameters.from(
            JwsHeader.with(MacAlgorithm.HS256).type("JWT").build(),
            claims.build()
        )).getTokenValue();
    }

    private static Instant validIssuedAt() {
        return Instant.now().minusSeconds(5);
    }

    private static Instant validExpiresAt() {
        return Instant.now().plusSeconds(300);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private record LoginBody(String email, String password) {
    }
}

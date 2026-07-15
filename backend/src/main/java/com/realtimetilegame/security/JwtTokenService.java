package com.realtimetilegame.security;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import com.realtimetilegame.user.domain.User;

@Component
public final class JwtTokenService {
    private final JwtEncoder encoder;
    private final JwtProperties properties;
    private final Clock clock;

    public JwtTokenService(JwtEncoder encoder, JwtProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    public AccessToken issue(User user) {
        if (user.id() == null) {
            throw new IllegalArgumentException("Persisted user is required");
        }
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plusSeconds(properties.accessTokenTtlSeconds());
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer(properties.issuer())
            .audience(List.of(properties.audience()))
            .issuedAt(issuedAt)
            .expiresAt(expiresAt)
            .subject(user.id().toString())
            .id(UUID.randomUUID().toString())
            .claim("role", "USER")
            .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, properties.accessTokenTtlSeconds());
    }
}

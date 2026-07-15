package com.realtimetilegame.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

public final class RequiredJwtClaimsValidator implements OAuth2TokenValidator<Jwt> {
    private static final Duration ALLOWED_CLOCK_SKEW = Duration.ofSeconds(60);

    private final Clock clock;

    public RequiredJwtClaimsValidator(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        Instant issuedAt = jwt.getIssuedAt();
        Instant expiresAt = jwt.getExpiresAt();
        String jwtId = jwt.getId();

        if (issuedAt == null) {
            return failure("JWT issued-at claim is required");
        }
        if (expiresAt == null) {
            return failure("JWT expiration claim is required");
        }
        if (jwtId == null || jwtId.isBlank()) {
            return failure("JWT ID claim is required");
        }
        if (!expiresAt.isAfter(issuedAt)) {
            return failure("JWT expiration must be after issued-at");
        }
        if (issuedAt.isAfter(clock.instant().plus(ALLOWED_CLOCK_SKEW))) {
            return failure("JWT issued-at is in the future");
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static OAuth2TokenValidatorResult failure(String description) {
        return OAuth2TokenValidatorResult.failure(
            new OAuth2Error("invalid_token", description, null)
        );
    }
}

package com.realtimetilegame.security;

import java.time.Clock;
import java.util.Base64;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfiguration {
    private static final int MINIMUM_SECRET_BYTES = 32;

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    SecretKey jwtSecretKey(JwtProperties properties) {
        byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(properties.accessSecretBase64());
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT access secret must be valid Base64", exception);
        }
        if (decoded.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("JWT access secret must decode to at least 32 bytes");
        }
        return new SecretKeySpec(decoded, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey secretKey, JwtProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();

        OAuth2TokenValidator<Jwt> defaults = JwtValidators.createDefaultWithIssuer(properties.issuer());
        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>(
            "aud",
            audiences -> audiences != null && audiences.contains(properties.audience())
        );
        OAuth2TokenValidator<Jwt> subject = new JwtClaimValidator<String>("sub", JwtConfiguration::isNumeric);
        OAuth2TokenValidator<Jwt> role = new JwtClaimValidator<String>("role", "USER"::equals);
        OAuth2TokenValidator<Jwt> requiredClaims = new RequiredJwtClaimsValidator(clock);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
            defaults, audience, subject, role, requiredClaims
        ));
        return decoder;
    }

    private static boolean isNumeric(String subject) {
        if (subject == null || subject.isBlank()) {
            return false;
        }
        try {
            return Long.parseLong(subject) > 0;
        } catch (NumberFormatException exception) {
            return false;
        }
    }
}

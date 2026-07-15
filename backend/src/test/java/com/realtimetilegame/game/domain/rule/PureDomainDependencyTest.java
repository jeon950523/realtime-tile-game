package com.realtimetilegame.game.domain.rule;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class PureDomainDependencyTest {
    private static final List<String> FORBIDDEN_IMPORTS = List.of(
        "org.springframework",
        "jakarta.persistence",
        "jakarta.validation",
        "com.fasterxml.jackson"
    );

    /*
     * Phase 1에서 만든 규칙 엔진, 상태 모델, 타일 모델은 프레임워크에 의존하지 않는다.
     * Phase 4의 game.domain.session은 Room/User와 같은 영속 Aggregate이므로
     * JPA 의존성을 별도의 Repository·통합 테스트에서 검증한다.
     */
    private static final List<Path> PURE_DOMAIN_ROOTS = List.of(
        Path.of("src/main/java/com/realtimetilegame/game/domain/rule"),
        Path.of("src/main/java/com/realtimetilegame/game/domain/state"),
        Path.of("src/main/java/com/realtimetilegame/game/domain/tile")
    );

    @Test
    void pureRuleStateAndTileDomainDoesNotDependOnFrameworkPersistenceOrSerializationPackages()
        throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path pureDomainRoot : PURE_DOMAIN_ROOTS) {
            violations.addAll(findViolations(pureDomainRoot));
        }

        assertThat(violations).isEmpty();
    }

    private static List<String> findViolations(Path domainRoot) throws IOException {
        try (var paths = Files.walk(domainRoot)) {
            return paths
                .filter(path -> path.toString().endsWith(".java"))
                .flatMap(path -> readLines(path).stream()
                    .filter(line -> FORBIDDEN_IMPORTS.stream().anyMatch(line::contains))
                    .map(line -> path + ": " + line.trim()))
                .toList();
        }
    }

    private static List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException exception) {
            throw new IllegalStateException("failed to read " + path, exception);
        }
    }
}

package com.realtimetilegame.game.websocket;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;

@Component
public class GameActionReplayStore {
    private static final int MAX_ENTRIES = 10_000;
    private static final long TTL_SECONDS = 600;

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public GameActionReplayStore(Clock clock) {
        this.clock = clock;
    }

    public Result execute(long userId, long gameId, String actionId, Supplier<GameCommandReply> action) {
        cleanup();
        String key = userId + ":" + gameId + ":" + actionId;
        Entry proposed = new Entry(new CompletableFuture<>(), clock.instant());
        Entry existing = entries.putIfAbsent(key, proposed);
        if (existing != null) {
            return new Result(existing.future().join().duplicateReplay(), true);
        }
        try {
            GameCommandReply reply = action.get();
            proposed.future().complete(reply);
            return new Result(reply, false);
        } catch (RuntimeException exception) {
            entries.remove(key, proposed);
            proposed.future().completeExceptionally(exception);
            throw exception;
        }
    }

    private void cleanup() {
        Instant threshold = clock.instant().minusSeconds(TTL_SECONDS);
        entries.entrySet().removeIf(entry -> entry.getValue().createdAt().isBefore(threshold));
        int overflow = entries.size() - MAX_ENTRIES;
        if (overflow <= 0) return;
        entries.entrySet().stream()
            .sorted(Comparator.comparing(entry -> entry.getValue().createdAt()))
            .limit(overflow)
            .map(Map.Entry::getKey)
            .toList()
            .forEach(entries::remove);
    }

    private record Entry(CompletableFuture<GameCommandReply> future, Instant createdAt) {
    }

    public record Result(GameCommandReply reply, boolean duplicate) {
    }
}

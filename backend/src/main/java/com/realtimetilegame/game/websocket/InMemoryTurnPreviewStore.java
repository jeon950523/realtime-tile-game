package com.realtimetilegame.game.websocket;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.stereotype.Component;

import com.realtimetilegame.game.application.dto.TurnPreviewSnapshot;
import com.realtimetilegame.game.application.preview.TurnPreviewStore;

@Component
public class InMemoryTurnPreviewStore implements TurnPreviewStore {
    private final ConcurrentMap<Long, Entry> entries = new ConcurrentHashMap<>();

    @Override
    public Optional<TurnPreviewSnapshot> find(long gameId) {
        Entry entry = entries.get(gameId);
        return entry == null ? Optional.empty() : Optional.ofNullable(entry.snapshot());
    }

    @Override
    public boolean saveIfNewer(TurnPreviewSnapshot snapshot) {
        return saveIfNewer(snapshot, null);
    }

    @Override
    public boolean saveIfNewer(TurnPreviewSnapshot snapshot, String ownerSessionId) {
        AtomicBoolean accepted = new AtomicBoolean();
        entries.compute(snapshot.gameId(), (gameId, existing) -> {
            boolean sameContext = existing != null
                && existing.turnPlayerId() == snapshot.turnPlayerId()
                && existing.baseGameVersion() == snapshot.baseGameVersion();
            if (sameContext && existing.previewRevision() >= snapshot.previewRevision()) return existing;
            accepted.set(true);
            return Entry.from(snapshot, ownerSessionId);
        });
        return accepted.get();
    }

    @Override
    public boolean clearIfNewer(long gameId, long turnPlayerId, long baseGameVersion, long previewRevision) {
        AtomicBoolean accepted = new AtomicBoolean();
        entries.compute(gameId, (ignored, existing) -> {
            boolean sameContext = existing != null
                && existing.turnPlayerId() == turnPlayerId
                && existing.baseGameVersion() == baseGameVersion;
            if (sameContext && existing.previewRevision() >= previewRevision) return existing;
            accepted.set(true);
            return new Entry(turnPlayerId, baseGameVersion, previewRevision, null, null);
        });
        return accepted.get();
    }

    @Override
    public Optional<TurnPreviewSnapshot> remove(long gameId) {
        Entry removed = entries.remove(gameId);
        return removed == null ? Optional.empty() : Optional.ofNullable(removed.snapshot());
    }

    @Override
    public List<TurnPreviewSnapshot> removeByTurnPlayerId(long turnPlayerId) {
        return removeByTurnPlayerSession(turnPlayerId, null);
    }

    @Override
    public List<TurnPreviewSnapshot> removeByTurnPlayerSession(long turnPlayerId, String ownerSessionId) {
        List<TurnPreviewSnapshot> removed = new ArrayList<>();
        entries.forEach((gameId, entry) -> {
            if (entry.turnPlayerId() != turnPlayerId || entry.snapshot() == null
                || (ownerSessionId != null && !ownerSessionId.equals(entry.ownerSessionId()))) return;
            if (entries.remove(gameId, entry)) removed.add(entry.snapshot());
        });
        return List.copyOf(removed);
    }

    private record Entry(
        long turnPlayerId,
        long baseGameVersion,
        long previewRevision,
        TurnPreviewSnapshot snapshot,
        String ownerSessionId
    ) {
        private static Entry from(TurnPreviewSnapshot snapshot, String ownerSessionId) {
            return new Entry(
                snapshot.turnPlayerId(), snapshot.baseGameVersion(), snapshot.previewRevision(), snapshot,
                ownerSessionId
            );
        }
    }
}

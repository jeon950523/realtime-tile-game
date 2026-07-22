package com.realtimetilegame.game;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import com.realtimetilegame.game.websocket.GameActionReplayStore;
import com.realtimetilegame.game.websocket.GameCommandReply;

import org.junit.jupiter.api.Test;

class GameActionReplayStoreTest {
    @Test
    void duplicateActionExecutesTheStateChangeOnceAndReplaysTheSameVersion() {
        GameActionReplayStore store = new GameActionReplayStore(Clock.systemUTC());
        AtomicInteger executions = new AtomicInteger();
        String actionId = "5f11ce00-52f9-42f4-b47f-915c7806e3fa";

        GameActionReplayStore.Result first = store.execute(1L, 33L, actionId, () -> {
            executions.incrementAndGet();
            return GameCommandReply.accepted(actionId, 33L, "DRAW", 1L);
        });
        GameActionReplayStore.Result duplicate = store.execute(1L, 33L, actionId, () -> {
            executions.incrementAndGet();
            return GameCommandReply.accepted(actionId, 33L, "DRAW", 2L);
        });

        assertThat(executions).hasValue(1);
        assertThat(first.duplicate()).isFalse();
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.reply().eventType()).isEqualTo("DUPLICATE_GAME_ACTION_REPLAYED");
        assertThat(duplicate.reply().gameVersion()).isEqualTo(1L);
        assertThat(duplicate.reply().duplicate()).isTrue();
    }

    @Test
    void simultaneousDuplicateActionsShareOneFutureResult() throws Exception {
        GameActionReplayStore store = new GameActionReplayStore(Clock.systemUTC());
        AtomicInteger executions = new AtomicInteger();
        CountDownLatch actionStarted = new CountDownLatch(1);
        CountDownLatch releaseAction = new CountDownLatch(1);
        AtomicReference<GameActionReplayStore.Result> first = new AtomicReference<>();
        AtomicReference<GameActionReplayStore.Result> duplicate = new AtomicReference<>();
        String actionId = "6f11ce00-52f9-42f4-b47f-915c7806e3fa";

        Thread firstThread = new Thread(() -> first.set(store.execute(1L, 33L, actionId, () -> {
            executions.incrementAndGet();
            actionStarted.countDown();
            await(releaseAction);
            return GameCommandReply.accepted(actionId, 33L, "DRAW", 1L);
        })));
        Thread duplicateThread = new Thread(() -> duplicate.set(store.execute(1L, 33L, actionId, () -> {
            executions.incrementAndGet();
            return GameCommandReply.accepted(actionId, 33L, "DRAW", 2L);
        })));

        firstThread.start();
        assertThat(actionStarted.await(1, TimeUnit.SECONDS)).isTrue();
        duplicateThread.start();
        releaseAction.countDown();
        firstThread.join(1_000);
        duplicateThread.join(1_000);

        assertThat(executions).hasValue(1);
        assertThat(first.get().reply().gameVersion()).isEqualTo(1L);
        assertThat(duplicate.get().reply().gameVersion()).isEqualTo(1L);
        assertThat(duplicate.get().duplicate()).isTrue();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(1, TimeUnit.SECONDS)) throw new AssertionError("timed out waiting for test latch");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }
}

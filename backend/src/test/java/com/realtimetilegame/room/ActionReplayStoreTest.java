package com.realtimetilegame.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.concurrent.atomic.AtomicInteger;

import com.realtimetilegame.room.websocket.ActionReplayStore;
import com.realtimetilegame.room.websocket.RoomCommandReply;

import org.junit.jupiter.api.Test;

class ActionReplayStoreTest {
    @Test
    void duplicateActionExecutesStateChangeOnceAndReplaysPrivateReply() {
        ActionReplayStore store = new ActionReplayStore(Clock.systemUTC());
        AtomicInteger executions = new AtomicInteger();
        String actionId = "5f11ce00-52f9-42f4-b47f-915c7806e3fa";

        ActionReplayStore.Result first = store.execute(1L, actionId, () -> {
            executions.incrementAndGet();
            return RoomCommandReply.accepted(actionId, "READY");
        });
        ActionReplayStore.Result duplicate = store.execute(1L, actionId, () -> {
            executions.incrementAndGet();
            return RoomCommandReply.accepted(actionId, "SHOULD_NOT_RUN");
        });

        assertThat(executions).hasValue(1);
        assertThat(first.duplicate()).isFalse();
        assertThat(duplicate.duplicate()).isTrue();
        assertThat(duplicate.reply().eventType()).isEqualTo("DUPLICATE_ACTION_REPLAYED");
        assertThat(duplicate.reply().payload()).isEqualTo("READY");
    }
}

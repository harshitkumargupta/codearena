package com.codearena.api.websocket;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class CoalescerTest {

    private static final Instant NOW = Instant.parse("2026-10-13T10:00:00Z");

    private final CapturingScheduler scheduler = new CapturingScheduler();
    private final List<Long> actions = new ArrayList<>();
    private final Coalescer<Long> coalescer =
            new Coalescer<>(scheduler, Duration.ofSeconds(1), Clock.fixed(NOW, ZoneOffset.UTC), actions::add);

    @Test
    void burstForOneKeyBecomesOneScheduledAction() {
        for (int i = 0; i < 100; i++) {
            coalescer.request(9L);
        }

        assertThat(scheduler.tasks).hasSize(1);
        assertThat(scheduler.times).containsExactly(NOW.plusSeconds(1));
        scheduler.runAll();
        assertThat(actions).containsExactly(9L);
    }

    @Test
    void requestsAfterTheActionRanScheduleTheNextOne() {
        for (int i = 0; i < 50; i++) {
            coalescer.request(9L);
        }
        scheduler.runAll();
        for (int i = 0; i < 50; i++) {
            coalescer.request(9L);
        }
        scheduler.runAll();

        assertThat(actions).containsExactly(9L, 9L);
    }

    @Test
    void keysAreCoalescedIndependently() {
        coalescer.request(1L);
        coalescer.request(2L);
        coalescer.request(1L);
        scheduler.runAll();

        assertThat(actions).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void realSchedulerTurnsSustainedBurstIntoFewActions() throws InterruptedException {
        ThreadPoolTaskScheduler real = new ThreadPoolTaskScheduler();
        real.initialize();
        AtomicInteger broadcasts = new AtomicInteger();
        Coalescer<Long> live = new Coalescer<>(real, Duration.ofMillis(200), Clock.systemUTC(),
                key -> broadcasts.incrementAndGet());
        try {
            for (int i = 0; i < 200; i++) {
                live.request(9L);
                Thread.sleep(3);
            }
            Thread.sleep(400);
        } finally {
            real.shutdown();
        }

        assertThat(broadcasts.get()).isBetween(2, 6);
    }

    private static final class CapturingScheduler extends SimpleAsyncTaskScheduler {

        private final List<Runnable> tasks = new ArrayList<>();
        private final List<Instant> times = new ArrayList<>();

        @Override
        public ScheduledFuture<?> schedule(Runnable task, Instant startTime) {
            tasks.add(task);
            times.add(startTime);
            return null;
        }

        void runAll() {
            List<Runnable> due = new ArrayList<>(tasks);
            tasks.clear();
            due.forEach(Runnable::run);
        }
    }
}

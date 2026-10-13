package com.codearena.api.websocket;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.scheduling.TaskScheduler;

/**
 * Collapses bursts of requests per key into one action that runs {@code window} after the first request. Requests
 * that arrive while an action is pending are absorbed; the action reads the latest state when it runs.
 */
public final class Coalescer<K> {

    private final TaskScheduler scheduler;
    private final Duration window;
    private final Clock clock;
    private final Consumer<K> action;
    private final Set<K> pending = ConcurrentHashMap.newKeySet();

    public Coalescer(TaskScheduler scheduler, Duration window, Clock clock, Consumer<K> action) {
        this.scheduler = scheduler;
        this.window = window;
        this.clock = clock;
        this.action = action;
    }

    public void request(K key) {
        if (pending.add(key)) {
            scheduler.schedule(() -> {
                pending.remove(key);
                action.accept(key);
            }, clock.instant().plus(window));
        }
    }
}

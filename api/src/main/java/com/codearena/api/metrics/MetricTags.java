package com.codearena.api.metrics;

import java.util.Locale;
import java.util.Set;

/**
 * The only tag keys CodeArena meters may use. Every value is drawn from a small fixed set (an enum or a queue name),
 * never from user ids, problem ids, submission ids or anything derived from code.
 */
public final class MetricTags {

    public static final String VERDICT = "verdict";
    public static final String LANGUAGE = "language";
    public static final String OUTCOME = "outcome";
    public static final String LIMITER = "limiter";
    public static final String QUEUE = "queue";

    public static final Set<String> ALLOWED = Set.of(VERDICT, LANGUAGE, OUTCOME, LIMITER, QUEUE);

    private MetricTags() {
    }

    static String value(Enum<?> value) {
        return value.name().toLowerCase(Locale.ROOT);
    }
}

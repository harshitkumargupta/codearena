package com.codearena.api.websocket;

public final class StompDestinations {

    public static final String ENDPOINT = "/ws";
    public static final String TOPIC_PREFIX = "/topic";

    private StompDestinations() {
    }

    public static String submission(long submissionId) {
        return TOPIC_PREFIX + "/submissions/" + submissionId;
    }

    public static String leaderboard(long contestId) {
        return TOPIC_PREFIX + "/contests/" + contestId + "/leaderboard";
    }
}

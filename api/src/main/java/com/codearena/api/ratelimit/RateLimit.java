package com.codearena.api.ratelimit;

public enum RateLimit {
    SUBMISSIONS("submissions", "Too many submissions. Please wait before submitting again."),
    LOGIN_PER_IP("login-ip", "Too many login attempts. Please wait and try again."),
    LOGIN_PER_USERNAME("login-user", "Too many login attempts. Please wait and try again."),
    REGISTER_PER_IP("register-ip", "Too many registrations from this address. Please try again later."),
    TUTOR("tutor", "Too many tutor requests. Please wait a moment.");

    private final String keyPrefix;
    private final String message;

    RateLimit(String keyPrefix, String message) {
        this.keyPrefix = keyPrefix;
        this.message = message;
    }

    public String redisKey(String subject) {
        return "rl:" + keyPrefix + ":" + subject;
    }

    public String message() {
        return message;
    }
}

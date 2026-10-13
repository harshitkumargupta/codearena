package com.codearena.api.web;

import java.io.IOException;

public class PayloadTooLargeException extends IOException {

    private final long limit;

    public PayloadTooLargeException(long limit) {
        super("Request body exceeds " + limit + " bytes");
        this.limit = limit;
    }

    public long getLimit() {
        return limit;
    }
}

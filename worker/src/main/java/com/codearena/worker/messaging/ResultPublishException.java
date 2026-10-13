package com.codearena.worker.messaging;

public class ResultPublishException extends RuntimeException {

    public ResultPublishException(String message) {
        super(message);
    }

    public ResultPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.codearena.api.tutor;

import com.codearena.api.error.ServiceUnavailableException;

public class TutorUnavailableException extends ServiceUnavailableException {

    static final String MESSAGE = "The AI tutor is unavailable right now. Please try again in a few minutes.";

    public TutorUnavailableException() {
        super(MESSAGE);
    }
}

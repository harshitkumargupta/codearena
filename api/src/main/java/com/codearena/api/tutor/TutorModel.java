package com.codearena.api.tutor;

public interface TutorModel {

    /**
     * @throws TutorUnavailableException if the model cannot be reached, times out, or returns nothing
     */
    String generate(TutorPrompt prompt);
}

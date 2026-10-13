package com.codearena.api.tutor;

public record TutorPrompt(String system, String user) {

    @Override
    public String toString() {
        return "TutorPrompt[systemLength=" + system.length() + ", userLength=" + user.length() + "]";
    }
}

package com.codearena.common;

final class Redaction {

    private Redaction() {
    }

    static String length(String value) {
        return value == null ? "null" : Integer.toString(value.length());
    }
}

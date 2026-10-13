package com.codearena.worker.judge;

import java.util.ArrayList;
import java.util.List;

public final class OutputComparator {

    private OutputComparator() {
    }

    public static boolean matches(String expected, String actual) {
        return normalize(expected).equals(normalize(actual));
    }

    static List<String> normalize(String output) {
        List<String> lines = new ArrayList<>();
        for (String line : output.split("\n", -1)) {
            lines.add(line.stripTrailing());
        }
        while (!lines.isEmpty() && lines.getLast().isEmpty()) {
            lines.removeLast();
        }
        return lines;
    }
}

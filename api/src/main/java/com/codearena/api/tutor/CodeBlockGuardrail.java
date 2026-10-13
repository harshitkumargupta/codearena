package com.codearena.api.tutor;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Post-check on model output: finds Markdown fenced code blocks (``` or ~~~) and rejects any block when
 * {@code maxLines} is 0, or blocks with more than {@code maxLines} non-blank lines. A block that never closes is always
 * rejected: the answer was cut off (usually by the token limit) while the model was still writing code, so the
 * block's real length is unknown.
 */
public final class CodeBlockGuardrail {

    private static final Pattern FENCE = Pattern.compile("^[ \\t]*(`{3,}|~{3,})");

    private CodeBlockGuardrail() {
    }

    public static Optional<String> violation(String text, int maxLines) {
        String[] lines = text.split("\\R", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher open = FENCE.matcher(lines[i]);
            if (!open.find()) {
                continue;
            }
            String fence = open.group(1);
            int codeLines = 0;
            int j = i + 1;
            while (j < lines.length && !closes(lines[j], fence)) {
                if (!lines[j].isBlank()) {
                    codeLines++;
                }
                j++;
            }
            if (maxLines == 0) {
                return Optional.of("code block not allowed at this level");
            }
            if (j == lines.length) {
                return Optional.of("unterminated code block (" + codeLines + " lines before the answer was cut off)");
            }
            if (codeLines > maxLines) {
                return Optional.of("code block of " + codeLines + " lines (limit " + maxLines + ")");
            }
            i = j;
        }
        return Optional.empty();
    }

    private static boolean closes(String line, String fence) {
        String trimmed = line.strip();
        return trimmed.length() >= fence.length() && trimmed.chars().allMatch(c -> c == fence.charAt(0));
    }
}

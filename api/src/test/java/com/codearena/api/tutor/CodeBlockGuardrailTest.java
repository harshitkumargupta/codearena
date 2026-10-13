package com.codearena.api.tutor;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CodeBlockGuardrailTest {

    private static String block(String fence, int lines) {
        StringBuilder b = new StringBuilder("Here is how:\n" + fence + "python\n");
        for (int i = 0; i < lines; i++) {
            b.append("x").append(i).append(" = ").append(i).append('\n');
        }
        return b.append(fence).append("\nDone.").toString();
    }

    @Test
    void proseOnlyPasses() {
        assertThat(CodeBlockGuardrail.violation("Think about a hash map from value to index. Use `dict` lookups.", 0))
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"```", "~~~", "````"})
    void anyBlockIsRejectedWhenNoCodeIsAllowed(String fence) {
        assertThat(CodeBlockGuardrail.violation(block(fence, 1), 0)).contains("code block not allowed at this level");
    }

    @Test
    void shortBlockPassesWhenLimitAllowsIt() {
        assertThat(CodeBlockGuardrail.violation(block("```", 10), 10)).isEmpty();
    }

    @Test
    void longBlockIsRejected() {
        assertThat(CodeBlockGuardrail.violation(block("```", 11), 10)).contains("code block of 11 lines (limit 10)");
    }

    @Test
    void blankLinesDoNotCount() {
        String text = "```\na = 1\n\n\n\nb = 2\n```";
        assertThat(CodeBlockGuardrail.violation(text, 2)).isEmpty();
    }

    @Test
    void unterminatedBlockIsRejectedEvenWhenShort() {
        String cutOff = "Here's the Python implementation of the solution:\n\n```python\n"
                + "def two_sum(nums, target):\n    nums.sort()\n    left, right = 0, len(nums) - 1\n"
                + "    while left < right:\n        current_sum = nums[left] + nums[right]\n";

        assertThat(CodeBlockGuardrail.violation(cutOff, 10))
                .contains("unterminated code block (5 lines before the answer was cut off)");
    }

    @Test
    void secondLongBlockIsFoundAfterAShortOne() {
        String text = block("```", 2) + "\n" + block("~~~", 12);
        assertThat(CodeBlockGuardrail.violation(text, 10)).contains("code block of 12 lines (limit 10)");
    }

    @Test
    void indentedFenceIsDetected() {
        assertThat(CodeBlockGuardrail.violation("Steps:\n   ```\n   x = 1\n   ```", 0)).isPresent();
    }
}

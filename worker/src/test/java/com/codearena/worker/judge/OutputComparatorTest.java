package com.codearena.worker.judge;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutputComparatorTest {

    @Test
    void identicalOutputMatches() {
        assertThat(OutputComparator.matches("1 2\n3\n", "1 2\n3\n")).isTrue();
    }

    @Test
    void crlfMatchesLf() {
        assertThat(OutputComparator.matches("1 2\n3\n", "1 2\r\n3\r\n")).isTrue();
        assertThat(OutputComparator.matches("a\r\nb", "a\nb\n")).isTrue();
    }

    @Test
    void trailingWhitespaceOnEachLineIsIgnored() {
        assertThat(OutputComparator.matches("1 2\n3", "1 2   \n3\t\n")).isTrue();
    }

    @Test
    void trailingBlankLinesAreIgnored() {
        assertThat(OutputComparator.matches("42", "42\n\n\n")).isTrue();
        assertThat(OutputComparator.matches("42\n\n", "42")).isTrue();
        assertThat(OutputComparator.matches("42", "42\n \n\t\n")).isTrue();
    }

    @Test
    void emptyOutputMatchesOnlyEmptyExpectation() {
        assertThat(OutputComparator.matches("", "")).isTrue();
        assertThat(OutputComparator.matches("", "\n\n")).isTrue();
        assertThat(OutputComparator.matches("0", "")).isFalse();
        assertThat(OutputComparator.matches("", "0")).isFalse();
    }

    @Test
    void internalWhitespaceIsSignificant() {
        assertThat(OutputComparator.matches("1 2", "1  2")).isFalse();
        assertThat(OutputComparator.matches("1 2", "1\t2")).isFalse();
    }

    @Test
    void leadingWhitespaceIsSignificant() {
        assertThat(OutputComparator.matches("1", " 1")).isFalse();
        assertThat(OutputComparator.matches("a\nb", "a\n  b")).isFalse();
    }

    @Test
    void leadingAndInternalBlankLinesAreSignificant() {
        assertThat(OutputComparator.matches("1", "\n1")).isFalse();
        assertThat(OutputComparator.matches("1\n2", "1\n\n2")).isFalse();
    }

    @Test
    void differentValuesDoNotMatch() {
        assertThat(OutputComparator.matches("0 1", "1 0")).isFalse();
        assertThat(OutputComparator.matches("true", "True")).isFalse();
    }
}

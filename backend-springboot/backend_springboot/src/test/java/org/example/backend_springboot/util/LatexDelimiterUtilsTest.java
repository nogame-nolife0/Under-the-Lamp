package org.example.backend_springboot.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LatexDelimiterUtilsTest {

    @Test
    void shouldConvertParenDelimiters() {
        String raw = "Y=\\(A_2 + \\overline{A_2} \\overline{A_1}\\)";
        String normalized = LatexDelimiterUtils.normalize(raw);
        assertTrue(normalized.contains("$A_2 + \\overline{A_2}"));
        assertFalse(normalized.contains("\\("));
    }

    @Test
    void shouldWrapBareOverline() {
        String raw = "Y=\\overline{A_2}\\overline{A_1}";
        String normalized = LatexDelimiterUtils.normalize(raw);
        assertTrue(normalized.startsWith("$"));
        assertTrue(normalized.contains("\\overline{A_2}"));
    }

    @Test
    void shouldKeepChinesePrefixForOptions() {
        String raw = "用与非门，Y=\\(\\overline{\\overline{Y_0} \\ \\overline{Y_1}}\\)";
        String normalized = LatexDelimiterUtils.normalize(raw);
        assertTrue(normalized.startsWith("用与非门，"));
        assertTrue(normalized.contains("$"));
    }
}

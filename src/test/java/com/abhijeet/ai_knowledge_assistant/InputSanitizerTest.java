package com.abhijeet.ai_knowledge_assistant;

import com.abhijeet.ai_knowledge_assistant.util.InputSanitizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InputSanitizerTest {

    @Test
    void nullBecomesEmptyString() {
        assertEquals("", InputSanitizer.clean(null, 100));
    }

    @Test
    void trimsSurroundingWhitespace() {
        assertEquals("hello", InputSanitizer.clean("  hello \n", 100));
    }

    @Test
    void stripsControlCharactersButKeepsNewlines() {
        assertEquals("a\nb", InputSanitizer.clean("a\u0000\nb", 100));
    }

    @Test
    void enforcesMaximumLength() {
        assertEquals(5, InputSanitizer.clean("abcdefghij", 5).length());
        assertEquals("abcde", InputSanitizer.clean("abcdefghij", 5));
    }

    @Test
    void whitespaceOnlyBecomesEmpty() {
        assertTrue(InputSanitizer.clean("   \t \n ", 50).isEmpty());
    }
}

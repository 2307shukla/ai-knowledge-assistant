package com.abhijeet.ai_knowledge_assistant.controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExportControllerTest {

    @Test
    void neutralisesSpreadsheetFormulas() {
        assertThat(ExportController.csvSafe("=HYPERLINK(\"http://x\")")).startsWith("'=");
        assertThat(ExportController.csvSafe("+1")).isEqualTo("'+1");
        assertThat(ExportController.csvSafe("@SUM(A1)")).isEqualTo("'@SUM(A1)");
        assertThat(ExportController.csvSafe("hello")).isEqualTo("hello");
        assertThat(ExportController.csvSafe(null)).isEmpty();
    }
}

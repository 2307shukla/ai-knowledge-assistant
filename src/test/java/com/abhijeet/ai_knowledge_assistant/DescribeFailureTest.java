package com.abhijeet.ai_knowledge_assistant;

import com.abhijeet.ai_knowledge_assistant.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/** What gets logged for a failure: type, place and SQL error code, never the message. */
class DescribeFailureTest {

    @Test
    void includesTheSqlStateOfADatabaseCauseButNotItsMessage() {
        Exception failure = new IllegalStateException("insert failed",
                new SQLException("expected 768 dimensions; row (secret text)", "22000"));
        String text = GlobalExceptionHandler.describe(failure);
        assertThat(text).contains("IllegalStateException").contains("SQLState 22000")
                .doesNotContain("secret").doesNotContain("dimensions");
    }

    @Test
    void withoutADatabaseCauseThereIsNoSqlState() {
        assertThat(GlobalExceptionHandler.describe(new IllegalStateException("boom")))
                .doesNotContain("SQLState").doesNotContain("boom");
    }
}

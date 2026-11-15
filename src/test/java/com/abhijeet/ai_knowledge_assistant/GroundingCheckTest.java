package com.abhijeet.ai_knowledge_assistant;

import com.abhijeet.ai_knowledge_assistant.util.GroundingCheck;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GroundingCheckTest {

    @Test
    void answerBuiltFromContextIsGrounded() {
        String context = "Pega Certified Senior System Architect CSSA 2018";
        String answer = "The certifications are Pega Certified Senior System Architect CSSA 2018";

        assertTrue(GroundingCheck.isGrounded(answer, context, 0.45));
    }

    @Test
    void unrelatedAnswerIsNotGrounded() {
        String context = "Pega Certified Senior System Architect";
        String answer = "Bananas are especially rich in potassium and magnesium";

        assertFalse(GroundingCheck.isGrounded(answer, context, 0.45));
    }

    @Test
    void emptyAnswerIsNotPenalised() {
        assertEquals(1.0, GroundingCheck.overlap("", "anything at all"));
    }

    @Test
    void overlapIsBetweenZeroAndOne() {
        double overlap = GroundingCheck.overlap("alpha beta gamma delta", "alpha beta");
        assertTrue(overlap >= 0.0 && overlap <= 1.0);
    }
}

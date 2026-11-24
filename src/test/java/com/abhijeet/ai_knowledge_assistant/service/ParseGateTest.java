package com.abhijeet.ai_knowledge_assistant.service;

import com.abhijeet.ai_knowledge_assistant.rag.UploadRejectedException;
import com.abhijeet.ai_knowledge_assistant.support.TestPdfs;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Only a few PDFs may be parsed at once, so concurrent big uploads cannot exhaust 512 MB. */
class ParseGateTest {

    private static DocumentService service(int slots) {
        return new DocumentService(null, null, null, 5_000_000, 50, 20, 400, slots, 0);
    }

    @Test
    void parsesWhileASlotIsFreeAndGivesItBack() {
        DocumentService svc = service(1);
        svc.extractWithSlot(TestPdfs.pdf("A page with enough text to extract."));
        assertThat(svc.parseGate().availablePermits()).isEqualTo(1);
    }

    @Test
    void givesTheSlotBackWhenTheParseFails() {
        DocumentService svc = service(1);
        assertThatThrownBy(() -> svc.extractWithSlot("%PDF-1.7 garbage".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(UploadRejectedException.class);
        assertThat(svc.parseGate().availablePermits()).isEqualTo(1);
    }

    @Test
    void answers503WhenEverySlotIsBusy() throws Exception {
        DocumentService svc = service(2);
        svc.parseGate().acquire(2);
        assertThatThrownBy(() -> svc.extractWithSlot(TestPdfs.pdf("A page with enough text to extract.")))
                .isInstanceOfSatisfying(UploadRejectedException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        svc.parseGate().release(2);
        assertThat(svc.extractWithSlot(TestPdfs.pdf("A page with enough text to extract.")).pageCount()).isEqualTo(1);
    }
}

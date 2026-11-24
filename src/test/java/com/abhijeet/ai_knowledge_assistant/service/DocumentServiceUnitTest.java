package com.abhijeet.ai_knowledge_assistant.service;

import com.abhijeet.ai_knowledge_assistant.rag.UploadRejectedException;
import com.abhijeet.ai_knowledge_assistant.support.TestPdfs;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentServiceUnitTest {

    private static final String TEXT = "Every page of this document has enough text to become a passage.";

    @Test
    void recognisesPdfByContent() {
        assertThat(DocumentService.isPdf(TestPdfs.pdf(TEXT))).isTrue();
        assertThat(DocumentService.isPdf("%PDF-1.4 minimal".getBytes(StandardCharsets.US_ASCII))).isTrue();
        assertThat(DocumentService.isPdf("hello".getBytes(StandardCharsets.US_ASCII))).isFalse();
        assertThat(DocumentService.isPdf(new byte[0])).isFalse();
        assertThat(DocumentService.isPdf("PK\u0003\u0004 a zip or docx".getBytes(StandardCharsets.ISO_8859_1))).isFalse();
    }

    @Test
    void acceptsAPdfSignatureAfterLeadingJunkWithinTheFirstKilobyte() {
        byte[] bytes = new byte[600];
        System.arraycopy("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), 0, bytes, 500, 8);
        assertThat(DocumentService.isPdf(bytes)).isTrue();

        byte[] late = new byte[3000];
        System.arraycopy("%PDF-1.7".getBytes(StandardCharsets.US_ASCII), 0, late, 2000, 8);
        assertThat(DocumentService.isPdf(late)).isFalse();
    }

    @Test
    void cleansFilenames() {
        assertThat(DocumentService.cleanName("../../etc/passwd")).isEqualTo("passwd");
        assertThat(DocumentService.cleanName("C:\\Users\\me\\report.pdf")).isEqualTo("report.pdf");
        assertThat(DocumentService.cleanName("a<b>c:\"d|e?f*.pdf")).isEqualTo("abcdef.pdf");
        assertThat(DocumentService.cleanName("line\nbreak.pdf")).isEqualTo("linebreak.pdf");
        assertThat(DocumentService.cleanName(null)).isEqualTo("document");
        assertThat(DocumentService.cleanName("..")).isEqualTo("document");
        assertThat(DocumentService.cleanName("x".repeat(500) + ".pdf")).hasSizeLessThanOrEqualTo(120).endsWith(".pdf");
    }

    @Test
    void extractsTextPageByPage() {
        DocumentService.Extracted e = DocumentService.extractPdf(TestPdfs.pdf("First " + TEXT, "Second " + TEXT), 10);
        assertThat(e.pageCount()).isEqualTo(2);
        assertThat(e.pages().get(0).number()).isEqualTo(1);
        assertThat(e.pages().get(0).text()).contains("First");
        assertThat(e.pages().get(1).number()).isEqualTo(2);
        assertThat(e.pages().get(1).text()).contains("Second").doesNotContain("First");
    }

    @Test
    void rejectsTooManyPagesBeforeExtracting() {
        assertThatThrownBy(() -> DocumentService.extractPdf(TestPdfs.pdf(TEXT, TEXT, TEXT), 2))
                .isInstanceOfSatisfying(UploadRejectedException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE));
    }

    @Test
    void rejectsEncryptedAndDamagedPdfs() {
        assertThatThrownBy(() -> DocumentService.extractPdf(TestPdfs.encryptedPdf(TEXT), 10))
                .isInstanceOfSatisfying(UploadRejectedException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY));
        assertThatThrownBy(() -> DocumentService.extractPdf("%PDF-1.7 garbage".getBytes(StandardCharsets.US_ASCII), 10))
                .isInstanceOfSatisfying(UploadRejectedException.class,
                        e -> assertThat(e.getMessage()).doesNotContain("Exception"));
    }
}

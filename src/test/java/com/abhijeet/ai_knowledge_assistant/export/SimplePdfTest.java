package com.abhijeet.ai_knowledge_assistant.export;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SimplePdfTest {

    @Test
    void writesReadableTextAcrossPages() throws Exception {
        byte[] bytes;
        try (SimplePdf pdf = new SimplePdf()) {
            pdf.paragraph("Conversation title", SimplePdf.Style.TITLE);
            for (int i = 0; i < 120; i++) {
                pdf.paragraph("Line number " + i + " with a reasonably long sentence to wrap across the page width "
                        + "so that wrapping is exercised.", SimplePdf.Style.BODY);
            }
            bytes = pdf.toBytes();
        }
        try (PDDocument doc = Loader.loadPDF(bytes)) {
            assertThat(doc.getNumberOfPages()).isGreaterThan(1);
            String text = new PDFTextStripper().getText(doc);
            assertThat(text).contains("Conversation title").contains("Line number 119");
        }
    }

    @Test
    void replacesCharactersTheStandardFontCannotEncode() {
        assertThat(SimplePdf.clean("caf\u00e9 \u2014 \u201cok\u201d \u4f60")).isEqualTo("caf\u00e9 - \"ok\" ?");
        assertThat(SimplePdf.clean("a\u0000b\tc")).isEqualTo("ab c");
    }

    @Test
    void handlesEmojiAndVeryLongWords() throws Exception {
        try (SimplePdf pdf = new SimplePdf()) {
            pdf.paragraph("\uD83D\uDE00 " + "x".repeat(400), SimplePdf.Style.BODY);
            assertThat(pdf.toBytes()).isNotEmpty();
        }
    }
}

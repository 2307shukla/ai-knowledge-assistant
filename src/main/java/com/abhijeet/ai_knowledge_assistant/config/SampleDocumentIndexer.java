package com.abhijeet.ai_knowledge_assistant.config;

import com.abhijeet.ai_knowledge_assistant.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Indexes the bundled sample document at startup so /api/rag/ask works
 * immediately, without the user having to upload anything first.
 *
 * Plain text is preferred over PDF: it keeps the repository free of any personal
 * or copyrighted material, and it needs no PDF parsing.
 *
 * Failures are logged, never thrown: a broken embedding call must not stop
 * the whole application from booting.
 */
@Component
public class SampleDocumentIndexer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDocumentIndexer.class);
    private static final String SAMPLE_TXT = "sample.txt";
    private static final String SAMPLE_PDF = "sample.pdf";

    private final RagService ragService;

    public SampleDocumentIndexer(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public void run(ApplicationArguments args) {
        // Already restored from disk - re-indexing would re-embed every chunk
        // and cost another round of API calls for nothing.
        if (!ragService.isEmpty()) {
            log.info("Vector store already populated - skipping sample indexing");
            return;
        }

        ClassPathResource text = new ClassPathResource(SAMPLE_TXT);
        if (text.exists()) {
            try (InputStream in = text.getInputStream()) {
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                int chunks = ragService.indexText(content);
                log.info("Indexed {} into the vector store ({} chunks)", SAMPLE_TXT, chunks);
            } catch (Exception e) {
                log.warn("Could not index {}: {}", SAMPLE_TXT, e.getMessage());
            }
            return;
        }

        ClassPathResource pdf = new ClassPathResource(SAMPLE_PDF);
        if (pdf.exists()) {
            try {
                ragService.loadPdf(pdf);
                log.info("Indexed {} into the vector store", SAMPLE_PDF);
            } catch (Exception e) {
                log.warn("Could not index {}: {}", SAMPLE_PDF, e.getMessage());
            }
            return;
        }

        log.warn("No sample document found - document Q&A starts empty");
    }
}

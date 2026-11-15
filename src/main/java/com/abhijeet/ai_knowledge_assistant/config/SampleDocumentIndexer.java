package com.abhijeet.ai_knowledge_assistant.config;

import com.abhijeet.ai_knowledge_assistant.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/**
 * Indexes the bundled sample document at startup so /api/rag/ask works
 * immediately, without the user having to upload anything first.
 *
 * Failures are logged, never thrown: a broken embedding call must not stop
 * the whole application from booting.
 */
@Component
public class SampleDocumentIndexer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDocumentIndexer.class);
    private static final String SAMPLE = "sample.pdf";

    private final RagService ragService;

    public SampleDocumentIndexer(RagService ragService) {
        this.ragService = ragService;
    }

    @Override
    public void run(ApplicationArguments args) {
        ClassPathResource resource = new ClassPathResource(SAMPLE);

        if (!resource.exists()) {
            log.warn("{} not found on the classpath - skipping startup indexing", SAMPLE);
            return;
        }

        long start = System.currentTimeMillis();
        try {
            ragService.loadPdf(resource);
            log.info("Indexed {} into the vector store in {} ms", SAMPLE, System.currentTimeMillis() - start);
        } catch (Exception e) {
            // Common causes: bad API key, free-tier quota hit, network blip.
            // The chat endpoint keeps working regardless.
            log.warn("Could not index {}: {}", SAMPLE, e.getMessage());
        }
    }
}

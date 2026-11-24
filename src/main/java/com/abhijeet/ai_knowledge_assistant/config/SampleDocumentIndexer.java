package com.abhijeet.ai_knowledge_assistant.config;

import com.abhijeet.ai_knowledge_assistant.service.DocumentService;
import com.abhijeet.ai_knowledge_assistant.service.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Start-up housekeeping for the document library:
 * <ol>
 *   <li>file-store mode: rebuild the document list from vectors that survived a restart;</li>
 *   <li>index the bundled sample as a public document, once, so document Q&A works with no upload;</li>
 *   <li>report how many legacy vectors have no owner. They are quarantined: the owner filter
 *       excludes them from every search, and no API can list or open them.</li>
 * </ol>
 * Failures are logged and never stop the application (for example when no AI key is set yet).
 */
@Component
public class SampleDocumentIndexer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleDocumentIndexer.class);

    /** Fixed id, so the sample is indexed once and never duplicated. */
    public static final String SAMPLE_ID = "00000000-0000-0000-0000-000000000001";
    private static final String SAMPLE_TXT = "sample.txt";

    private final DocumentService documents;
    private final RagService rag;
    private final ObjectProvider<JdbcTemplate> jdbc;

    public SampleDocumentIndexer(DocumentService documents, RagService rag, ObjectProvider<JdbcTemplate> jdbc) {
        this.documents = documents;
        this.rag = rag;
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (rag.usesFileStore()) {
            restoreFromFileStore();
        } else {
            reportQuarantinedVectors();
        }
        indexSample();
    }

    private void restoreFromFileStore() {
        int restored = 0;
        for (RagService.StoredDocument stored : rag.storedDocuments()) {
            try {
                documents.restore(stored);
                restored++;
            } catch (RuntimeException e) {
                log.warn("Could not restore document id={}: {}", stored.id(), e.getClass().getSimpleName());
            }
        }
        if (restored > 0) {
            log.info("Restored {} documents from the vector-store file", restored);
        }
    }

    private void indexSample() {
        if (documents.exists(SAMPLE_ID)) {
            return;
        }
        ClassPathResource sample = new ClassPathResource(SAMPLE_TXT);
        if (!sample.exists()) {
            log.warn("No sample document bundled; document Q&A starts empty");
            return;
        }
        try (InputStream in = sample.getInputStream()) {
            String text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            documents.addPublic(SAMPLE_ID, SAMPLE_TXT, text);
            log.info("Indexed the public sample document");
        } catch (IOException | RuntimeException e) {
            log.warn("Sample document not indexed ({}). It is retried at the next start.",
                    e.getClass().getSimpleName());
        }
    }

    /** Counts only; no content, file names or ids are logged. */
    private void reportQuarantinedVectors() {
        JdbcTemplate template = jdbc.getIfAvailable();
        if (template == null) {
            return;
        }
        try {
            Long count = template.queryForObject(
                    "SELECT COUNT(*) FROM vector_store WHERE metadata->>'owner' IS NULL", Long.class);
            if (count != null && count > 0) {
                log.warn("{} legacy vectors have no owner and are quarantined (never searchable). "
                        + "See README > Security notes to remove them.", count);
            }
        } catch (RuntimeException e) {
            log.debug("Ownerless vector count unavailable: {}", e.getClass().getSimpleName());
        }
    }
}

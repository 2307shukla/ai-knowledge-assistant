package com.abhijeet.ai_knowledge_assistant.service;

import com.abhijeet.ai_knowledge_assistant.domain.DocumentChunk;
import com.abhijeet.ai_knowledge_assistant.domain.KnowledgeDocument;
import com.abhijeet.ai_knowledge_assistant.rag.TextChunker;
import com.abhijeet.ai_knowledge_assistant.rag.UploadRejectedException;
import com.abhijeet.ai_knowledge_assistant.repository.KnowledgeDocumentRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * The private document library: upload with limits, list, get, delete and re-index.
 *
 * <p>Every lookup is scoped to the caller: a document id that belongs to someone else behaves
 * exactly like one that does not exist. The public sample can be read by every signed-in user and
 * changed by no one.
 */
@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);

    /** Only PDF uploads are accepted; the type is checked by content, not by name. */
    public static final List<String> TYPES = List.of("pdf");
    static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    /** The signature may follow a few bytes of junk (allowed by the PDF spec); look this far. */
    static final int MAGIC_WINDOW = 1024;

    private final KnowledgeDocumentRepository documents;
    private final JdbcAggregateTemplate aggregates;
    private final RagService rag;
    private final long maxBytes;
    private final int maxPages;
    private final int maxDocuments;
    private final int maxChunks;
    /** Only a few PDFs are parsed at once, so several big uploads cannot exhaust a 512 MB instance. */
    private final Semaphore parseSlots;
    private final long parseWaitSeconds;

    public DocumentService(KnowledgeDocumentRepository documents, JdbcAggregateTemplate aggregates, RagService rag,
                           @Value("${app.documents.max-bytes:5242880}") long maxBytes,
                           @Value("${app.documents.max-pages:50}") int maxPages,
                           @Value("${app.documents.max-per-user:20}") int maxDocuments,
                           @Value("${app.documents.max-chunks:400}") int maxChunks,
                           @Value("${app.documents.max-concurrent-parses:2}") int maxConcurrentParses,
                           @Value("${app.documents.parse-wait-seconds:10}") long parseWaitSeconds) {
        this.documents = documents;
        this.aggregates = aggregates;
        this.rag = rag;
        this.maxBytes = maxBytes;
        this.maxPages = maxPages;
        this.maxDocuments = maxDocuments;
        this.maxChunks = maxChunks;
        this.parseSlots = new Semaphore(Math.max(1, maxConcurrentParses), true);
        this.parseWaitSeconds = parseWaitSeconds;
    }

    /** Validates, extracts page by page, chunks, embeds and records an upload for this owner. */
    @Transactional
    public KnowledgeDocument upload(String owner, String originalName, byte[] bytes) {
        requireOwner(owner);
        if (KnowledgeDocument.PUBLIC.equals(owner)) {
            // Uploads under the reserved name would be visible to everyone.
            throw new UploadRejectedException(HttpStatus.FORBIDDEN, "This account cannot upload documents.");
        }
        String filename = cleanName(originalName);
        if (bytes == null || bytes.length == 0) {
            throw new UploadRejectedException(HttpStatus.BAD_REQUEST, "The file is empty.");
        }
        if (bytes.length > maxBytes) {
            throw new UploadRejectedException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "The file is " + mb(bytes.length) + ". The limit is " + mb(maxBytes) + ".");
        }
        if (!isPdf(bytes)) {
            throw new UploadRejectedException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only PDF files are accepted, and this file is not a PDF.");
        }
        if (documents.countByOwner(owner) >= maxDocuments) {
            throw new UploadRejectedException(HttpStatus.CONFLICT,
                    "You already have " + maxDocuments + " documents, the limit. Delete one to upload another.");
        }

        Extracted extracted = extractWithSlot(bytes);
        List<TextChunker.Chunk> chunks = TextChunker.chunk(extracted.pages());
        if (chunks.isEmpty()) {
            throw new UploadRejectedException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "No readable text was found. Scanned PDFs (images of text) are not supported.");
        }
        if (chunks.size() > maxChunks) {
            throw new UploadRejectedException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "The document has too much text (" + chunks.size() + " passages, limit " + maxChunks
                            + "). Upload a shorter excerpt.");
        }

        KnowledgeDocument document = new KnowledgeDocument(owner, ensurePdfName(filename), "pdf", bytes.length,
                extracted.pageCount());
        chunks.forEach(c -> document.addChunk(new DocumentChunk(c.position(), c.page(), c.text())));
        embed(document);
        KnowledgeDocument saved = aggregates.insert(document);
        log.info("Document indexed: id={} pages={} chunks={}", saved.getId(), saved.getPages(), saved.getChunkCount());
        return saved;
    }

    /** Indexes text as a public document with a fixed id (the bundled sample). */
    @Transactional
    public KnowledgeDocument addPublic(String id, String filename, String text) {
        KnowledgeDocument document = new KnowledgeDocument(id, KnowledgeDocument.PUBLIC, filename, "txt",
                text.getBytes(StandardCharsets.UTF_8).length, null);
        TextChunker.chunk(List.of(new TextChunker.Page(null, text)))
                .forEach(c -> document.addChunk(new DocumentChunk(c.position(), c.page(), c.text())));
        embed(document);
        return aggregates.insert(document);
    }

    /** The caller's documents and the public sample, newest first. */
    @Transactional(readOnly = true)
    public List<KnowledgeDocument> list(String owner) {
        requireOwner(owner);
        return documents.findByOwnerInOrderByCreatedAtDesc(List.of(owner, KnowledgeDocument.PUBLIC));
    }

    /** One document the caller may read: their own or the public sample. Anything else: empty. */
    @Transactional(readOnly = true)
    public Optional<KnowledgeDocument> get(String owner, String id) {
        requireOwner(owner);
        return documents.findById(id)
                .filter(d -> owner.equals(d.getOwner()) || d.isPublic());
    }

    /** Deletes one of the caller's own documents and its vectors. Not theirs or absent: false. */
    @Transactional
    public boolean delete(String owner, String id) {
        Optional<KnowledgeDocument> found = owned(owner, id);
        found.ifPresent(document -> {
            rag.remove(document);
            documents.delete(document);
            log.info("Document deleted: id={}", document.getId());
        });
        return found.isPresent();
    }

    /** Re-embeds one of the caller's documents from its stored text. Not theirs or absent: empty. */
    @Transactional
    public Optional<KnowledgeDocument> reindex(String owner, String id) {
        Optional<KnowledgeDocument> found = owned(owner, id);
        found.ifPresent(document -> {
            rag.remove(document);
            embed(document);
            aggregates.update(document);
            log.info("Document re-indexed: id={} chunks={}", document.getId(), document.getChunkCount());
        });
        return found;
    }

    @Transactional(readOnly = true)
    public boolean exists(String id) {
        return documents.existsById(id);
    }

    /** Rebuilds a database record for vectors that survived a restart (file-store mode). */
    @Transactional
    public void restore(RagService.StoredDocument stored) {
        if (documents.existsById(stored.id())) {
            return;
        }
        Integer pages = stored.chunks().stream().map(RagService.StoredChunk::page)
                .filter(Objects::nonNull).max(Integer::compare).orElse(null);
        String file = cleanName(stored.file());
        KnowledgeDocument document = new KnowledgeDocument(stored.id(), stored.owner(), file,
                pages == null ? "txt" : "pdf", 0, pages);
        stored.chunks().stream()
                .sorted(Comparator.comparingInt(RagService.StoredChunk::position))
                .forEach(c -> {
                    DocumentChunk chunk = new DocumentChunk(c.position(), c.page(), c.text());
                    chunk.setVectorId(c.vectorId());
                    document.addChunk(chunk);
                });
        document.setIndexedAt(Instant.now());
        aggregates.insert(document);
    }

    // ------------------------------------------------------------------ helpers

    /** Waits briefly for a free parse slot; when none frees up the user is told to retry. */
    Extracted extractWithSlot(byte[] bytes) {
        boolean acquired;
        try {
            acquired = parseSlots.tryAcquire(parseWaitSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UploadRejectedException(HttpStatus.SERVICE_UNAVAILABLE, BUSY_MESSAGE);
        }
        if (!acquired) {
            throw new UploadRejectedException(HttpStatus.SERVICE_UNAVAILABLE, BUSY_MESSAGE);
        }
        try {
            return extractPdf(bytes, maxPages);
        } finally {
            parseSlots.release();
        }
    }

    private static final String BUSY_MESSAGE =
            "The server is busy reading other documents. Please try again in a few seconds.";

    /** The parse gate (for tests and diagnostics). */
    Semaphore parseGate() {
        return parseSlots;
    }

    private Optional<KnowledgeDocument> owned(String owner, String id) {
        requireOwner(owner);
        if (KnowledgeDocument.PUBLIC.equals(owner)) {
            return Optional.empty();
        }
        return documents.findByIdAndOwner(id, owner);
    }

    private static void requireOwner(String owner) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("An owner is required");
        }
    }

    private void embed(KnowledgeDocument document) {
        try {
            rag.index(document);
        } catch (RuntimeException e) {
            // No owner or document text in the log: the id is enough to investigate.
            log.error("Embedding failed for document id={}", document.getId(), e);
            List<String> partial = document.getChunks().stream()
                    .map(DocumentChunk::getVectorId).filter(Objects::nonNull).toList();
            try {
                rag.removeVectors(partial);
            } catch (RuntimeException cleanup) {
                log.warn("Could not remove partial vectors for document id={}", document.getId());
            }
            throw new UploadRejectedException(HttpStatus.BAD_GATEWAY,
                    "The AI provider could not index the document right now. Please try again in a minute.");
        }
        document.setIndexedAt(Instant.now());
    }

    /** Text of each page and the page count. */
    record Extracted(List<TextChunker.Page> pages, int pageCount) {
    }

    /**
     * Reads the PDF page by page, so only one page of text is held at a time besides the
     * chunks. Encrypted files and files over the page limit are rejected before any text is
     * extracted.
     */
    static Extracted extractPdf(byte[] bytes, int maxPages) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            if (pdf.isEncrypted()) {
                throw new UploadRejectedException(HttpStatus.UNPROCESSABLE_ENTITY,
                        "Password-protected PDFs are not supported.");
            }
            int count = pdf.getNumberOfPages();
            if (count == 0) {
                throw new UploadRejectedException(HttpStatus.UNPROCESSABLE_ENTITY, "The PDF has no pages.");
            }
            if (count > maxPages) {
                throw new UploadRejectedException(HttpStatus.PAYLOAD_TOO_LARGE,
                        "The PDF has " + count + " pages. The limit is " + maxPages + ".");
            }
            PDFTextStripper stripper = new PDFTextStripper();
            List<TextChunker.Page> pages = new ArrayList<>(count);
            for (int page = 1; page <= count; page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                pages.add(new TextChunker.Page(page, stripper.getText(pdf)));
            }
            return new Extracted(pages, count);
        } catch (InvalidPasswordException e) {
            throw new UploadRejectedException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "Password-protected PDFs are not supported.");
        } catch (IOException | RuntimeException e) {
            if (e instanceof UploadRejectedException rejected) {
                throw rejected;
            }
            log.warn("Could not read an uploaded PDF: {}", e.getClass().getSimpleName());
            throw new UploadRejectedException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "This PDF could not be read. It may be damaged.");
        }
    }

    /** True when the PDF signature appears within the first kilobyte. */
    static boolean isPdf(byte[] bytes) {
        int limit = Math.min(bytes.length, MAGIC_WINDOW) - PDF_MAGIC.length;
        for (int i = 0; i <= limit; i++) {
            boolean match = true;
            for (int j = 0; j < PDF_MAGIC.length && match; j++) {
                match = bytes[i + j] == PDF_MAGIC[j];
            }
            if (match) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps the base name only: no directories, no control or path characters, at most 120
     * characters. The name is only ever displayed, never used as a path.
     */
    static String cleanName(String name) {
        String n = name == null ? "" : name.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1)
                .replaceAll("[\\p{Cntrl}<>:\"|?*]", "")
                .strip();
        if (n.isEmpty() || n.chars().allMatch(c -> c == '.')) {
            n = "document";
        }
        return n.length() <= 120 ? n : n.substring(0, 60) + "..." + n.substring(n.length() - 57);
    }

    private static String ensurePdfName(String filename) {
        return filename.toLowerCase(Locale.ROOT).endsWith(".pdf") ? filename : filename + ".pdf";
    }

    private static String mb(long bytes) {
        return String.format(Locale.ROOT, "%.1f MB", bytes / 1048576.0);
    }

    public long maxBytes() {
        return maxBytes;
    }

    public int maxPages() {
        return maxPages;
    }

    public int maxDocuments() {
        return maxDocuments;
    }
}

package com.abhijeet.ai_knowledge_assistant.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.MappedCollection;
import org.springframework.data.relational.core.mapping.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * An uploaded document and who owns it: one aggregate with its chunks. The extracted text is kept
 * chunk by chunk so a document can be re-indexed (for example after an embedding change) without
 * uploading it again. Owner {@value #PUBLIC} marks the bundled sample, readable by every signed-in
 * user and changeable by none.
 */
@Table("knowledge_document")
public class KnowledgeDocument {

    public static final String PUBLIC = "public";

    @Id
    private String id;

    private String owner;
    private String filename;
    @Column("file_type")
    private String type;
    private long sizeBytes;
    private Integer pages;
    private int characters;
    private int chunkCount;
    private Instant createdAt;
    private Instant indexedAt;

    @MappedCollection(idColumn = "document_id", keyColumn = "chunk_index")
    private List<DocumentChunk> chunks = new ArrayList<>();

    /** For Spring Data JDBC. */
    protected KnowledgeDocument() {
    }

    public KnowledgeDocument(String owner, String filename, String type, long sizeBytes, Integer pages) {
        this(UUID.randomUUID().toString(), owner, filename, type, sizeBytes, pages);
    }

    /** For documents whose identity must stay stable across restarts (the bundled sample). */
    public KnowledgeDocument(String id, String owner, String filename, String type, long sizeBytes, Integer pages) {
        this.id = id;
        this.owner = owner;
        this.filename = filename;
        this.type = type;
        this.sizeBytes = sizeBytes;
        this.pages = pages;
        this.createdAt = Instant.now();
    }

    public void addChunk(DocumentChunk chunk) {
        chunks.add(chunk);
        characters += chunk.getText().length();
        chunkCount++;
    }

    public boolean isPublic() {
        return PUBLIC.equals(owner);
    }

    public String getId() {
        return id;
    }

    public String getOwner() {
        return owner;
    }

    public String getFilename() {
        return filename;
    }

    public String getType() {
        return type;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public Integer getPages() {
        return pages;
    }

    public int getCharacters() {
        return characters;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getIndexedAt() {
        return indexedAt;
    }

    public void setIndexedAt(Instant indexedAt) {
        this.indexedAt = indexedAt;
    }

    public List<DocumentChunk> getChunks() {
        return chunks;
    }
}

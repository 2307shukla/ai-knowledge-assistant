package com.abhijeet.ai_knowledge_assistant.domain;

import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One searchable piece of a document, with the page it came from and its id in the vector store.
 * Part of the {@link KnowledgeDocument} aggregate (no id of its own).
 */
@Table("document_chunk")
public class DocumentChunk {

    /** Chunks are kept well below this size; see TextChunker. */
    public static final int MAX_TEXT = 4000;

    @Column("chunk_position")
    private int position;

    @Column("page_number")
    private Integer page;

    @Column("chunk_text")
    private String text;

    private String vectorId;

    /** For Spring Data JDBC. */
    protected DocumentChunk() {
    }

    public DocumentChunk(int position, Integer page, String text) {
        this.position = position;
        this.page = page;
        this.text = text;
    }

    public int getPosition() {
        return position;
    }

    public Integer getPage() {
        return page;
    }

    public String getText() {
        return text;
    }

    public String getVectorId() {
        return vectorId;
    }

    public void setVectorId(String vectorId) {
        this.vectorId = vectorId;
    }
}

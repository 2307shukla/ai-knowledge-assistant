package com.abhijeet.ai_knowledge_assistant.rag;

/**
 * One retrieved passage, numbered for citation in the answer as [n].
 *
 * @param index      citation number, starting at 1
 * @param documentId the document it came from
 * @param file       file name, for display
 * @param page       page number, or null for formats without pages
 * @param text       the passage given to the model
 * @param score      similarity score from the vector store (higher is closer), or null
 */
public record SourceHit(int index, String documentId, String file, Integer page, String text, Double score) {

    /** Short excerpt for display. */
    public String snippet() {
        return text.length() <= 240 ? text : text.substring(0, 240).strip() + "...";
    }

    public String label() {
        return file + (page == null ? "" : ", page " + page);
    }
}

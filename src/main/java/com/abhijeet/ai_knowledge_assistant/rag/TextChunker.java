package com.abhijeet.ai_knowledge_assistant.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits page text into overlapping chunks small enough to embed and cite. Each chunk keeps the
 * page it starts on, so answers can cite "file.pdf, page 3". Paragraphs are kept whole where they
 * fit; long ones are cut at sentence or word boundaries.
 */
public final class TextChunker {

    public static final int TARGET = 1200;
    public static final int OVERLAP = 150;
    public static final int MIN = 40;

    /** Text of one page (page is null for formats without pages). */
    public record Page(Integer number, String text) {
    }

    public record Chunk(int position, Integer page, String text) {
    }

    private TextChunker() {
    }

    public static List<Chunk> chunk(List<Page> pages) {
        List<Chunk> chunks = new ArrayList<>();
        for (Page page : pages) {
            if (page.text() == null || page.text().isBlank()) {
                continue;
            }
            StringBuilder current = new StringBuilder();
            for (String paragraph : page.text().replace("\r", "").split("\\n\\s*\\n")) {
                String p = paragraph.strip().replaceAll("[ \\t]*\\n[ \\t]*", " ").replaceAll("[ \\t]{2,}", " ");
                if (p.isEmpty()) {
                    continue;
                }
                if (current.length() + p.length() + 2 <= TARGET) {
                    current.append(current.isEmpty() ? "" : "\n\n").append(p);
                    continue;
                }
                flush(chunks, page.number(), current);
                if (p.length() <= TARGET) {
                    current.append(p);
                } else {
                    for (String piece : split(p)) {
                        add(chunks, page.number(), piece);
                    }
                }
            }
            flush(chunks, page.number(), current);
        }
        return chunks;
    }

    /** Cuts a long paragraph into overlapping windows, preferring sentence then word boundaries. */
    static List<String> split(String text) {
        List<String> pieces = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + TARGET);
            if (end < text.length()) {
                int sentence = text.lastIndexOf(". ", end);
                int space = text.lastIndexOf(' ', end);
                if (sentence > start + TARGET / 2) {
                    end = sentence + 1;
                } else if (space > start + TARGET / 2) {
                    end = space;
                }
            }
            pieces.add(text.substring(start, end).strip());
            if (end >= text.length()) {
                break;
            }
            int next = end - OVERLAP;
            int boundary = text.indexOf(' ', Math.max(start + 1, next));
            start = boundary > start && boundary < end ? boundary + 1 : end;
        }
        return pieces;
    }

    private static void flush(List<Chunk> chunks, Integer page, StringBuilder current) {
        if (!current.isEmpty()) {
            add(chunks, page, current.toString());
            current.setLength(0);
        }
    }

    private static void add(List<Chunk> chunks, Integer page, String text) {
        if (text.strip().length() >= MIN) {
            chunks.add(new Chunk(chunks.size(), page, text.strip()));
        }
    }
}

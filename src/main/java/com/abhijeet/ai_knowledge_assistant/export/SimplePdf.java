package com.abhijeet.ai_knowledge_assistant.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * A minimal flowing-text PDF writer on Apache PDFBox: paragraphs, word wrap and page breaks.
 * Uses the built-in Helvetica fonts, so characters outside Latin-1 are replaced with '?'.
 */
public final class SimplePdf implements AutoCloseable {

    public enum Style { TITLE, META, BODY }

    private static final float MARGIN = 50;
    private static final PDRectangle SIZE = PDRectangle.A4;

    private final PDDocument document = new PDDocument();
    private final PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
    private final PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
    private PDPageContentStream stream;
    private float y;

    public SimplePdf() throws IOException {
        newPage();
    }

    public SimplePdf paragraph(String text, Style style) throws IOException {
        PDType1Font font = style == Style.TITLE ? bold : regular;
        float size = switch (style) {
            case TITLE -> 16;
            case META -> 9;
            case BODY -> 11;
        };
        float leading = size * 1.35f;
        float width = SIZE.getWidth() - 2 * MARGIN;
        float grey = style == Style.META ? 0.4f : 0f;
        for (String paragraph : clean(text).split("\n", -1)) {
            for (String line : wrap(paragraph, font, size, width)) {
                if (y - leading < MARGIN) {
                    newPage();
                }
                y -= leading;
                stream.beginText();
                stream.setFont(font, size);
                stream.setNonStrokingColor(grey, grey, grey);
                stream.newLineAtOffset(MARGIN, y);
                stream.showText(line);
                stream.endText();
            }
        }
        y -= size * 0.6f;
        return this;
    }

    public byte[] toBytes() throws IOException {
        stream.close();
        stream = null;
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.save(out);
            return out.toByteArray();
        }
    }

    @Override
    public void close() throws IOException {
        if (stream != null) {
            stream.close();
        }
        document.close();
    }

    private void newPage() throws IOException {
        if (stream != null) {
            stream.close();
        }
        PDPage page = new PDPage(SIZE);
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        y = SIZE.getHeight() - MARGIN;
    }

    /** Keeps printable Latin-1 only (what the standard fonts can encode); tabs become spaces. */
    static String clean(String text) {
        StringBuilder sb = new StringBuilder(text == null ? 0 : text.length());
        if (text != null) {
            for (char c : text.replace("\r\n", "\n").replace('\t', ' ').toCharArray()) {
                if (c == '\n' || (c >= 0x20 && c < 0x7f) || (c >= 0xa0 && c <= 0xff)) {
                    sb.append(c);
                } else if (c == '—' || c == '–') {
                    sb.append('-');
                } else if (c == '‘' || c == '’') {
                    sb.append('\'');
                } else if (c == '“' || c == '”') {
                    sb.append('"');
                } else if (c == '·' || c == '•') {
                    sb.append('·');
                } else if (!Character.isISOControl(c)) {
                    sb.append('?');
                }
            }
        }
        return sb.toString();
    }

    private static List<String> wrap(String text, PDType1Font font, float size, float width) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (textWidth(candidate, font, size) <= width) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
                line.setLength(0);
            }
            // A single word wider than the line is split by character.
            String rest = word;
            while (textWidth(rest, font, size) > width) {
                int cut = rest.length() - 1;
                while (cut > 1 && textWidth(rest.substring(0, cut), font, size) > width) {
                    cut--;
                }
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
            }
            line.append(rest);
        }
        lines.add(line.toString());
        return lines;
    }

    private static float textWidth(String text, PDType1Font font, float size) throws IOException {
        return font.getStringWidth(text) / 1000 * size;
    }
}

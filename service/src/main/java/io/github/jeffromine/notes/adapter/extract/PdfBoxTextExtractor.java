package io.github.jeffromine.notes.adapter.extract;

import io.github.jeffromine.notes.application.port.TextExtractor;
import io.github.jeffromine.notes.domain.core.text.MarkdownText;
import io.github.jeffromine.notes.domain.model.BodyFormat;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * PDF text via Apache PDFBox; plain text and Markdown read directly (Markdown stripped); anything else yields
 * nothing. Apache Tika would cover every format at the cost of a far larger dependency; PDFBox covers the one
 * document type the service accepts.
 */
public final class PdfBoxTextExtractor implements TextExtractor {

    @Override
    public Optional<String> extract(String contentType, InputStream in, int maxChars) {
        try {
            return switch (contentType) {
                case "application/pdf" -> Optional.of(cap(pdfText(in), maxChars)).filter(t -> !t.isBlank());
                case "text/plain" -> Optional.of(cap(new String(in.readAllBytes(), StandardCharsets.UTF_8), maxChars))
                        .filter(t -> !t.isBlank());
                case "text/markdown" -> Optional.of(cap(MarkdownText.toPlainText(
                        new String(in.readAllBytes(), StandardCharsets.UTF_8), BodyFormat.MARKDOWN), maxChars))
                        .filter(t -> !t.isBlank());
                default -> Optional.empty();
            };
        } catch (IOException e) {
            throw new UncheckedIOException("cannot extract text", e);
        }
    }

    private static String pdfText(InputStream in) throws IOException {
        try (PDDocument document = Loader.loadPDF(new RandomAccessReadBuffer(in))) {
            return new PDFTextStripper().getText(document);
        }
    }

    private static String cap(String text, int maxChars) {
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }
}

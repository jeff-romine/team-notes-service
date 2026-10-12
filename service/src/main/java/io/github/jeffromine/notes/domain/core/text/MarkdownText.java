package io.github.jeffromine.notes.domain.core.text;

import io.github.jeffromine.notes.domain.model.BodyFormat;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.commonmark.renderer.text.TextContentRenderer;

/**
 * Pure text transforms over a body and its format. Plain text passes through untouched to the index and is shown
 * verbatim in HTML; Markdown is stripped to its text for the index and rendered to HTML for display, with raw HTML
 * in the source escaped and unsafe URLs removed so a note cannot inject script into a client that renders it.
 */
public final class MarkdownText {

    private static final Parser PARSER = Parser.builder().build();
    private static final TextContentRenderer TEXT = TextContentRenderer.builder().build();
    private static final HtmlRenderer HTML = HtmlRenderer.builder().escapeHtml(true).sanitizeUrls(true).build();

    private MarkdownText() {}

    public static String toPlainText(String body, BodyFormat format) {
        return switch (format) {
            case PLAIN -> body;
            case MARKDOWN -> TEXT.render(PARSER.parse(body));
        };
    }

    public static String toHtml(String body, BodyFormat format) {
        return switch (format) {
            case PLAIN -> "<pre>" + escapeHtml(body) + "</pre>\n";
            case MARKDOWN -> {
                Node document = PARSER.parse(body);
                yield HTML.render(document);
            }
        };
    }

    public static String escapeHtml(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }
}

package io.github.jeffromine.notes.domain.core.text;

import io.github.jeffromine.notes.domain.model.BodyFormat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownTextTest {

    @Test
    void markdownIsStrippedToItsWordsForTheIndex() {
        String text = MarkdownText.toPlainText("# Title\n\nSome **bold** and a [link](http://x.y).\n\n- item", BodyFormat.MARKDOWN);
        assertThat(text).contains("Title").contains("Some bold and a").contains("link").contains("item").doesNotContain("**").doesNotContain("#");
    }

    @Test
    void plainTextPassesThroughAndRendersEscapedInPre() {
        assertThat(MarkdownText.toPlainText("<b>raw</b>", BodyFormat.PLAIN)).isEqualTo("<b>raw</b>");
        assertThat(MarkdownText.toHtml("<b>raw</b> & more", BodyFormat.PLAIN)).isEqualTo("<pre>&lt;b&gt;raw&lt;/b&gt; &amp; more</pre>\n");
    }

    @Test
    void markdownRendersToHtmlWithRawHtmlEscapedAndScriptUrlsRemoved() {
        String html = MarkdownText.toHtml("Hi <script>alert(1)</script>\n\n[x](javascript:alert(1))", BodyFormat.MARKDOWN);
        assertThat(html).contains("&lt;script&gt;").doesNotContain("<script>").doesNotContain("javascript:");
        assertThat(MarkdownText.toHtml("**bold**", BodyFormat.MARKDOWN)).isEqualTo("<p><strong>bold</strong></p>\n");
    }
}

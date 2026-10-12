package io.github.jeffromine.notes.extract;

import io.github.jeffromine.notes.adapter.extract.PdfBoxTextExtractor;
import io.github.jeffromine.notes.support.Pdfs;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PdfBoxTextExtractorTest {

    final PdfBoxTextExtractor extractor = new PdfBoxTextExtractor();

    @Test
    void readsTheWordsOutOfAPdf() {
        var text = extractor.extract("application/pdf", new ByteArrayInputStream(Pdfs.withText("Heron migration budget")), 1000);
        assertThat(text).isPresent();
        assertThat(text.get()).contains("Heron migration budget");
    }

    @Test
    void readsTextAndStripsMarkdownAndCapsLength() {
        assertThat(extractor.extract("text/plain", in("hello there"), 1000)).contains("hello there");
        assertThat(extractor.extract("text/markdown", in("# Head\n\n**bold**"), 1000).orElseThrow()).contains("Head").contains("bold").doesNotContain("**");
        assertThat(extractor.extract("text/plain", in("abcdefgh"), 3)).contains("abc");
        assertThat(extractor.extract("text/plain", in("   "), 1000)).isEmpty();
    }

    @Test
    void imagesAndUnknownTypesYieldNothing() {
        assertThat(extractor.extract("image/png", in("\u0089PNG"), 1000)).isEmpty();
        assertThat(extractor.extract("application/octet-stream", in("x"), 1000)).isEmpty();
    }

    static ByteArrayInputStream in(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}

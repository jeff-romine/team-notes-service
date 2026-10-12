package io.github.jeffromine.notes.application.port;

import java.io.InputStream;
import java.util.Optional;

/** Pulls indexable text out of an attachment. Empty when the type carries none (images) or is unknown. */
public interface TextExtractor {
    Optional<String> extract(String contentType, InputStream in, int maxChars);
}

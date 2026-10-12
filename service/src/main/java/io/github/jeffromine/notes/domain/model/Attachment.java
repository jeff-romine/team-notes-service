package io.github.jeffromine.notes.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Metadata for a file attached to a note. The bytes live in the blob store under {@code id}; {@code extractedText}
 * is what the text extractor pulled out for search (empty for images), capped by configuration.
 */
public record Attachment(
        UUID noteId,
        UUID id,
        String filename,
        String contentType,
        long size,
        String sha256,
        String uploadedBy,
        String extractedText,
        int chunkCount,
        Instant createdAt) {

    public Attachment {
        Objects.requireNonNull(noteId, "noteId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(filename, "filename");
        Objects.requireNonNull(contentType, "contentType");
        Objects.requireNonNull(sha256, "sha256");
        Objects.requireNonNull(uploadedBy, "uploadedBy");
        Objects.requireNonNull(createdAt, "createdAt");
        if (size < 0) {
            throw new IllegalArgumentException("size must not be negative");
        }
    }

    public Optional<String> extractedTextOpt() {
        return Optional.ofNullable(extractedText).filter(t -> !t.isBlank());
    }
}

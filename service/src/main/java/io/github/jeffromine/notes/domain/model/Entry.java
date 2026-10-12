package io.github.jeffromine.notes.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A contribution appended to a note by anyone who can see it: a sticky note on the whiteboard, an answer, a comment.
 * Each entry has its own author and version, so people editing different entries never conflict.
 */
public record Entry(
        UUID noteId,
        UUID id,
        String author,
        String body,
        BodyFormat bodyFormat,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public Entry {
        Objects.requireNonNull(noteId, "noteId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(author, "author");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(bodyFormat, "bodyFormat");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (version < 1) {
            throw new IllegalArgumentException("version starts at 1");
        }
    }
}

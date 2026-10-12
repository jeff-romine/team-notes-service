package io.github.jeffromine.notes.domain.core;

import java.util.Set;

/**
 * The configurable rules the guards enforce. Configuration is data, so it enters the core as a value rather than
 * through a port. {@link #defaults()} is what {@code application.yml} ships with and what tests use.
 */
public record Limits(
        int titleMaxChars,
        int bodyMaxChars,
        int tagsMax,
        int entryBodyMaxChars,
        int entriesMax,
        int attachmentsMax,
        long attachmentMaxBytes,
        Set<String> allowedAttachmentTypes,
        int filenameMaxChars,
        int teamNameMaxChars) {

    public Limits {
        allowedAttachmentTypes = Set.copyOf(allowedAttachmentTypes);
    }

    public static Limits defaults() {
        return new Limits(
                200,
                20_000,
                20,
                5_000,
                500,
                20,
                10L * 1024 * 1024,
                Set.of("application/pdf", "text/plain", "text/markdown", "image/png", "image/jpeg"),
                255,
                100);
    }
}

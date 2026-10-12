package io.github.jeffromine.notes.adapter.web;

import io.github.jeffromine.notes.domain.model.BodyFormat;
import io.github.jeffromine.notes.domain.model.Entry;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.UUID;

public final class EntryDtos {

    private EntryDtos() {}

    public record EntryRequest(@NotNull String body, BodyFormat bodyFormat) {
        public BodyFormat formatOrPlain() {
            return bodyFormat == null ? BodyFormat.PLAIN : bodyFormat;
        }
    }

    public record EntryUpdateRequest(@NotNull String body, BodyFormat bodyFormat, @NotNull Long version) {
        public BodyFormat formatOrPlain() {
            return bodyFormat == null ? BodyFormat.PLAIN : bodyFormat;
        }
    }

    public record EntryResponse(UUID id, UUID noteId, String author, String body, BodyFormat bodyFormat, long version,
                                Instant createdAt, Instant updatedAt) {
        public static EntryResponse of(Entry e) {
            return new EntryResponse(e.id(), e.noteId(), e.author(), e.body(), e.bodyFormat(), e.version(), e.createdAt(), e.updatedAt());
        }
    }
}

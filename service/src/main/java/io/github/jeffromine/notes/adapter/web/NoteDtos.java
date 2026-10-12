package io.github.jeffromine.notes.adapter.web;

import io.github.jeffromine.notes.application.NoteQueries;
import io.github.jeffromine.notes.domain.model.BodyFormat;
import io.github.jeffromine.notes.domain.model.Note;
import io.github.jeffromine.notes.domain.model.Page;
import io.github.jeffromine.notes.domain.model.Visibility;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/** Request and response shapes for notes. Content rules live in the core; here only presence is checked. */
public final class NoteDtos {

    private NoteDtos() {}

    public record NoteRequest(
            @NotNull String title,
            @NotNull String body,
            BodyFormat bodyFormat,
            Set<String> tags,
            @NotNull Visibility visibility,
            UUID teamId) {
        public BodyFormat formatOrPlain() {
            return bodyFormat == null ? BodyFormat.PLAIN : bodyFormat;
        }

        public Set<String> tagsOrEmpty() {
            return tags == null ? Set.of() : tags;
        }
    }

    public record NoteUpdateRequest(
            @NotNull String title,
            @NotNull String body,
            BodyFormat bodyFormat,
            Set<String> tags,
            @NotNull Visibility visibility,
            UUID teamId,
            @NotNull Long version) {
        public BodyFormat formatOrPlain() {
            return bodyFormat == null ? BodyFormat.PLAIN : bodyFormat;
        }

        public Set<String> tagsOrEmpty() {
            return tags == null ? Set.of() : tags;
        }
    }

    public record NoteResponse(
            UUID id,
            String owner,
            UUID teamId,
            String title,
            String body,
            BodyFormat bodyFormat,
            Set<String> tags,
            Visibility visibility,
            boolean archived,
            long version,
            Instant createdAt,
            Instant updatedAt,
            Integer entryCount,
            Integer attachmentCount) {

        /** Counts are null in list responses, which do not load children. */
        public static NoteResponse of(Note n, Integer entryCount, Integer attachmentCount) {
            return new NoteResponse(n.id(), n.owner(), n.teamId(), n.title(), n.body(), n.bodyFormat(), n.tags(),
                    n.visibility(), n.archived(), n.version(), n.createdAt(), n.updatedAt(), entryCount, attachmentCount);
        }

        public static NoteResponse of(NoteQueries.NoteView view) {
            return of(view.note(), view.entries().size(), view.attachments().size());
        }
    }

    public record PageResponse<T>(List<T> items, String nextCursor) {
        public static <A, B> PageResponse<B> of(Page<A> page, Function<A, B> map) {
            return new PageResponse<>(page.items().stream().map(map).toList(), page.nextCursor().orElse(null));
        }
    }
}

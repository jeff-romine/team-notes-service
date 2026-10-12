package io.github.jeffromine.notes.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** A list of notes within one partition (an owner's notes or a team's notes), optionally filtered, keyset-paged. */
public record NoteQuery(Scope scope, Optional<String> tag, boolean includeArchived, int limit, Optional<Cursor> after) {

    public static final int MAX_LIMIT = 100;

    public sealed interface Scope {
        record Owner(String username) implements Scope {}
        record Team(UUID teamId) implements Scope {}
    }

    public NoteQuery {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(after, "after");
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
    }
}

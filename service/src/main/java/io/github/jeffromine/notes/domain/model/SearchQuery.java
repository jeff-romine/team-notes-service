package io.github.jeffromine.notes.domain.model;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A search across every note the viewer may see. Offset paging, because relevance order has no natural keyset.
 */
public record SearchQuery(
        Optional<String> q,
        Optional<String> tag,
        Optional<UUID> teamId,
        Optional<Visibility> visibility,
        boolean includeArchived,
        Sort sort,
        int limit,
        int offset) {

    public static final int MAX_LIMIT = 100;
    public static final int MAX_OFFSET = 1_000;

    public enum Sort { RELEVANCE, NEWEST }

    public SearchQuery {
        Objects.requireNonNull(q, "q");
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(sort, "sort");
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_LIMIT);
        }
        if (offset < 0 || offset > MAX_OFFSET) {
            throw new IllegalArgumentException("offset must be between 0 and " + MAX_OFFSET);
        }
    }
}

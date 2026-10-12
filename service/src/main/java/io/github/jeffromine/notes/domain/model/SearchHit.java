package io.github.jeffromine.notes.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** A search result: enough of the note to show a list without a second read. */
public record SearchHit(
        UUID id,
        String title,
        String snippet,
        Set<String> tags,
        String owner,
        UUID teamId,
        Visibility visibility,
        boolean archived,
        Instant updatedAt,
        double score) {
    public SearchHit {
        tags = Set.copyOf(tags);
    }
}

package io.github.jeffromine.notes.domain.model;

import java.util.List;
import java.util.Optional;

/** One page of a keyset-paginated list. {@code nextCursor} is empty on the last page. */
public record Page<T>(List<T> items, Optional<String> nextCursor) {
    public Page {
        items = List.copyOf(items);
    }
}

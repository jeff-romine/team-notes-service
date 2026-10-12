package io.github.jeffromine.notes.domain.model;

import java.util.List;

public record SearchResult(List<SearchHit> items, long total) {
    public SearchResult {
        items = List.copyOf(items);
    }
}

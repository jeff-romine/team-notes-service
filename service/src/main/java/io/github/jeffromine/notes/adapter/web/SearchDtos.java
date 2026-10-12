package io.github.jeffromine.notes.adapter.web;

import io.github.jeffromine.notes.domain.model.SearchHit;
import io.github.jeffromine.notes.domain.model.SearchResult;

import java.util.List;

public final class SearchDtos {

    private SearchDtos() {}

    public record SearchResponse(List<SearchHit> items, long total) {
        public static SearchResponse of(SearchResult r) {
            return new SearchResponse(r.items(), r.total());
        }
    }
}

package io.github.jeffromine.notes.adapter.web;

import io.github.jeffromine.notes.adapter.web.SearchDtos.SearchResponse;
import io.github.jeffromine.notes.application.SearchQueries;
import io.github.jeffromine.notes.domain.model.SearchHit;
import io.github.jeffromine.notes.domain.model.SearchQuery;
import io.github.jeffromine.notes.domain.model.Visibility;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(path = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class SearchController {

    private final SearchQueries queries;

    public SearchController(SearchQueries queries) {
        this.queries = queries;
    }

    @GetMapping("/search")
    public SearchResponse search(
            @RequestParam Optional<String> q,
            @RequestParam Optional<String> tag,
            @RequestParam Optional<UUID> teamId,
            @RequestParam Optional<Visibility> visibility,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "RELEVANCE") SearchQuery.Sort sort,
            @RequestParam(defaultValue = "20") int limit,
            @RequestParam(defaultValue = "0") int offset,
            Principal principal) {
        SearchQuery query = new SearchQuery(q, tag, teamId, visibility, includeArchived, sort, limit, offset);
        return SearchResponse.of(queries.search(principal.getName(), query));
    }

    @GetMapping("/notes/{id}/related")
    public List<SearchHit> related(@PathVariable UUID id, @RequestParam(defaultValue = "5") int limit, Principal principal) {
        if (limit < 1 || limit > 50) {
            throw new IllegalArgumentException("limit must be between 1 and 50");
        }
        return queries.related(principal.getName(), id, limit);
    }
}

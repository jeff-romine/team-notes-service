package io.github.jeffromine.notes.adapter.web;

import io.github.jeffromine.notes.application.ReindexService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class AdminController {

    private final ReindexService reindex;

    public AdminController(ReindexService reindex) {
        this.reindex = reindex;
    }

    /** Rebuilds the search index from the store. Requires ROLE_ADMIN (see {@code notes.admin-users}). */
    @PostMapping(path = "/api/admin/reindex", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Long> reindex() {
        return Map.of("indexed", reindex.reindex());
    }
}

package io.github.jeffromine.notes.application;

import io.github.jeffromine.notes.application.port.SearchIndex;
import io.github.jeffromine.notes.domain.model.SearchHit;
import io.github.jeffromine.notes.domain.model.SearchQuery;
import io.github.jeffromine.notes.domain.model.SearchResult;
import io.github.jeffromine.notes.domain.model.Viewer;

import java.util.List;
import java.util.UUID;

/** Search reads: the index applies the viewer filter; this shell only builds the viewer. */
public final class SearchQueries {

    private final SearchIndex index;
    private final NoteQueries noteQueries;

    public SearchQueries(SearchIndex index, NoteQueries noteQueries) {
        this.index = index;
        this.noteQueries = noteQueries;
    }

    public SearchResult search(String actor, SearchQuery query) {
        return index.search(query, noteQueries.viewer(actor));
    }

    /** @throws RejectionException NotFound when the note itself is not visible to the actor */
    public List<SearchHit> related(String actor, UUID noteId, int limit) {
        noteQueries.get(actor, noteId);
        Viewer viewer = noteQueries.viewer(actor);
        return index.related(noteId, viewer, limit);
    }
}

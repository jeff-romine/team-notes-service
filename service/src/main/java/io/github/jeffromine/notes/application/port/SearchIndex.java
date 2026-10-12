package io.github.jeffromine.notes.application.port;

import io.github.jeffromine.notes.domain.model.NoteDocument;
import io.github.jeffromine.notes.domain.model.SearchHit;
import io.github.jeffromine.notes.domain.model.SearchQuery;
import io.github.jeffromine.notes.domain.model.Viewer;
import io.github.jeffromine.notes.domain.model.SearchResult;

import java.util.List;
import java.util.UUID;

/**
 * The search projection. Every read is filtered to what the viewer may see: their own notes, ORGANIZATION notes,
 * and TEAM notes of teams they belong to. Archived notes are excluded unless the query asks for them.
 */
public interface SearchIndex {
    /** Insert or replace the document for a note. */
    void index(NoteDocument document);

    void delete(UUID noteId);

    SearchResult search(SearchQuery query, Viewer viewer);

    /** Notes similar to {@code noteId}, excluding it, most similar first. */
    List<SearchHit> related(UUID noteId, Viewer viewer, int limit);

    void clear();
}

package io.github.jeffromine.notes.application.port;

import io.github.jeffromine.notes.domain.model.Note;
import io.github.jeffromine.notes.domain.model.NoteQuery;
import io.github.jeffromine.notes.domain.model.Page;

import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Notes, keyed by id and listable per owner or per team. The adapter owns the atomic version check: the decider
 * already compared versions logically, but only the store can make the comparison and the write one operation.
 */
public interface NoteRepository {
    void insert(Note note);

    Optional<Note> findById(UUID id);

    /**
     * Replace the stored note with {@code next} only if the stored version equals {@code expectedVersion}.
     *
     * @return true when written; false when the note is missing or at another version (nothing changed)
     */
    boolean updateIfVersion(Note next, long expectedVersion);

    void delete(UUID id);

    Page<Note> list(NoteQuery query);

    /** Every note, in no particular order; for rebuilding the search index. */
    Stream<Note> scanAll();
}

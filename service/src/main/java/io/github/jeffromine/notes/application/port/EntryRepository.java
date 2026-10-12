package io.github.jeffromine.notes.application.port;

import io.github.jeffromine.notes.domain.model.Entry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EntryRepository {
    void insert(Entry entry);

    Optional<Entry> find(UUID noteId, UUID entryId);

    /** Oldest first. */
    List<Entry> byNote(UUID noteId);

    /** @return true when written; false when the entry is missing or at another version (nothing changed) */
    boolean updateIfVersion(Entry next, long expectedVersion);

    void delete(UUID noteId, UUID entryId);

    void deleteAllByNote(UUID noteId);
}

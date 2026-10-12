package io.github.jeffromine.notes.adapter.persistence.memory;

import io.github.jeffromine.notes.application.port.EntryRepository;
import io.github.jeffromine.notes.domain.model.Entry;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryEntryRepository implements EntryRepository {

    private static final Comparator<Entry> OLDEST_FIRST =
            Comparator.comparing(Entry::createdAt).thenComparing(Entry::id, (a, b) ->
                    io.github.jeffromine.notes.domain.model.Note.compareUnsigned(a, b));

    private final InMemoryStore store;

    public InMemoryEntryRepository(InMemoryStore store) {
        this.store = store;
    }

    @Override
    public void insert(Entry entry) {
        store.mutate(s -> s.entriesByNote.computeIfAbsent(entry.noteId(), k -> new LinkedHashMap<>()).put(entry.id(), entry));
    }

    @Override
    public Optional<Entry> find(UUID noteId, UUID entryId) {
        return store.read(s -> Optional.ofNullable(s.entriesByNote.getOrDefault(noteId, new LinkedHashMap<>()).get(entryId)));
    }

    @Override
    public List<Entry> byNote(UUID noteId) {
        return store.read(s -> s.entriesByNote.getOrDefault(noteId, new LinkedHashMap<>()).values().stream()
                .sorted(OLDEST_FIRST).toList());
    }

    @Override
    public boolean updateIfVersion(Entry next, long expectedVersion) {
        return store.write(s -> {
            Map<UUID, Entry> entries = s.entriesByNote.get(next.noteId());
            Entry current = entries == null ? null : entries.get(next.id());
            if (current == null || current.version() != expectedVersion) {
                return false;
            }
            entries.put(next.id(), next);
            return true;
        });
    }

    @Override
    public void delete(UUID noteId, UUID entryId) {
        store.mutate(s -> {
            Map<UUID, Entry> entries = s.entriesByNote.get(noteId);
            if (entries != null) {
                entries.remove(entryId);
            }
        });
    }

    @Override
    public void deleteAllByNote(UUID noteId) {
        store.mutate(s -> s.entriesByNote.remove(noteId));
    }
}

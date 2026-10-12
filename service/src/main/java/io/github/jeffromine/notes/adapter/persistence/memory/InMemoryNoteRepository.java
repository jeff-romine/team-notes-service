package io.github.jeffromine.notes.adapter.persistence.memory;

import io.github.jeffromine.notes.application.port.NoteRepository;
import io.github.jeffromine.notes.domain.model.Cursor;
import io.github.jeffromine.notes.domain.model.Note;
import io.github.jeffromine.notes.domain.model.NoteQuery;
import io.github.jeffromine.notes.domain.model.Page;
import io.github.jeffromine.notes.domain.model.Visibility;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Lists are computed the way the Cassandra adapter reads them: one partition (owner or team), newest first, filter
 * inside the partition, keyset cursor, {@code limit + 1} to learn whether there is a next page.
 */
public final class InMemoryNoteRepository implements NoteRepository {

    private final InMemoryStore store;

    public InMemoryNoteRepository(InMemoryStore store) {
        this.store = store;
    }

    @Override
    public void insert(Note note) {
        store.mutate(s -> {
            if (s.notes.putIfAbsent(note.id(), note) != null) {
                throw new IllegalStateException("note " + note.id() + " already exists");
            }
        });
    }

    @Override
    public Optional<Note> findById(UUID id) {
        return store.read(s -> Optional.ofNullable(s.notes.get(id)));
    }

    @Override
    public boolean updateIfVersion(Note next, long expectedVersion) {
        return store.write(s -> {
            Note current = s.notes.get(next.id());
            if (current == null || current.version() != expectedVersion) {
                return false;
            }
            s.notes.put(next.id(), next);
            return true;
        });
    }

    @Override
    public void delete(UUID id) {
        store.mutate(s -> s.notes.remove(id));
    }

    @Override
    public Page<Note> list(NoteQuery query) {
        Predicate<Note> partition = switch (query.scope()) {
            case NoteQuery.Scope.Owner o -> n -> n.owner().equals(o.username());
            case NoteQuery.Scope.Team t -> n -> n.visibility() == Visibility.TEAM && t.teamId().equals(n.teamId());
        };
        Predicate<Note> filters = n -> (query.includeArchived() || !n.archived())
                && query.tag().map(tag -> n.tags().contains(tag)).orElse(true)
                && query.after().map(cursor -> cursor.isBefore(n)).orElse(true);
        List<Note> page = store.read(s -> s.notes.values().stream()
                .filter(partition)
                .filter(filters)
                .sorted(Note.NEWEST_FIRST)
                .limit(query.limit() + 1L)
                .toList());
        if (page.size() <= query.limit()) {
            return new Page<>(page, Optional.empty());
        }
        List<Note> items = page.subList(0, query.limit());
        return new Page<>(items, Optional.of(Cursor.of(items.getLast()).encode()));
    }

    @Override
    public Stream<Note> scanAll() {
        return store.read(s -> List.copyOf(s.notes.values())).stream();
    }
}

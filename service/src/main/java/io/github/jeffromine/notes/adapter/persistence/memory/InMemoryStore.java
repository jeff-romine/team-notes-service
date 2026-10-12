package io.github.jeffromine.notes.adapter.persistence.memory;

import io.github.jeffromine.notes.domain.model.Attachment;
import io.github.jeffromine.notes.domain.model.Entry;
import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.domain.model.Note;
import io.github.jeffromine.notes.domain.model.Team;
import io.github.jeffromine.notes.domain.model.User;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * The development store: plain maps behind one read/write lock. Every mutation runs under the write lock and, when
 * a snapshot path is configured, ends by writing the whole state to that file, so the write lock also serialises
 * snapshots. Plain {@code HashMap}s rather than concurrent maps on purpose: a consistent snapshot needs one lock
 * across all of them anyway.
 *
 * <p>Limits, stated plainly: the snapshot is the whole state on every mutation (fine for small teams, not for
 * large ones); one process only; no migrations beyond {@code schemaVersion}. Attachment bytes are not here; they
 * are in the blob store.
 */
public final class InMemoryStore {

    /** Mutable maps the repositories work on. Keys: users by username, teams by id, notes by id; children by note id. */
    static final class State {
        final Map<String, User> users = new HashMap<>();
        final Map<UUID, Team> teams = new HashMap<>();
        final Map<UUID, Map<String, Membership>> membershipsByTeam = new HashMap<>();
        final Map<UUID, Note> notes = new HashMap<>();
        final Map<UUID, LinkedHashMap<UUID, Entry>> entriesByNote = new HashMap<>();
        final Map<UUID, LinkedHashMap<UUID, Attachment>> attachmentsByNote = new HashMap<>();
    }

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final State state = new State();
    private final Optional<JsonSnapshot> snapshot;

    /** @param snapshotPath where to persist; empty keeps everything in memory only */
    public InMemoryStore(Optional<Path> snapshotPath) {
        this.snapshot = snapshotPath.map(JsonSnapshot::new);
        this.snapshot.flatMap(JsonSnapshot::load).ifPresent(this::restore);
    }

    <T> T read(Function<State, T> reader) {
        lock.readLock().lock();
        try {
            return reader.apply(state);
        } finally {
            lock.readLock().unlock();
        }
    }

    <T> T write(Function<State, T> writer) {
        lock.writeLock().lock();
        try {
            T result = writer.apply(state);
            snapshot.ifPresent(s -> s.save(export()));
            return result;
        } finally {
            lock.writeLock().unlock();
        }
    }

    void mutate(Consumer<State> writer) {
        write(s -> {
            writer.accept(s);
            return null;
        });
    }

    JsonSnapshot.SnapshotState export() {
        return new JsonSnapshot.SnapshotState(
                JsonSnapshot.SCHEMA_VERSION,
                List.copyOf(state.users.values()),
                List.copyOf(state.teams.values()),
                state.membershipsByTeam.values().stream().flatMap(m -> m.values().stream()).toList(),
                List.copyOf(state.notes.values()),
                state.entriesByNote.values().stream().flatMap(m -> m.values().stream()).toList(),
                state.attachmentsByNote.values().stream().flatMap(m -> m.values().stream()).toList());
    }

    private void restore(JsonSnapshot.SnapshotState s) {
        s.users().forEach(u -> state.users.put(u.username(), u));
        s.teams().forEach(t -> state.teams.put(t.id(), t));
        s.memberships().forEach(m -> state.membershipsByTeam.computeIfAbsent(m.teamId(), k -> new HashMap<>()).put(m.username(), m));
        s.notes().forEach(n -> state.notes.put(n.id(), n));
        s.entries().forEach(e -> state.entriesByNote.computeIfAbsent(e.noteId(), k -> new LinkedHashMap<>()).put(e.id(), e));
        s.attachments().forEach(a -> state.attachmentsByNote.computeIfAbsent(a.noteId(), k -> new LinkedHashMap<>()).put(a.id(), a));
    }
}

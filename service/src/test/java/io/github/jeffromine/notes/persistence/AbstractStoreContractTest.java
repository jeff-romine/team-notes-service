package io.github.jeffromine.notes.persistence;

import io.github.jeffromine.notes.application.port.AttachmentRepository;
import io.github.jeffromine.notes.application.port.EntryRepository;
import io.github.jeffromine.notes.application.port.MembershipRepository;
import io.github.jeffromine.notes.application.port.NoteRepository;
import io.github.jeffromine.notes.application.port.TeamRepository;
import io.github.jeffromine.notes.application.port.UserRepository;
import io.github.jeffromine.notes.domain.model.Attachment;
import io.github.jeffromine.notes.domain.model.BodyFormat;
import io.github.jeffromine.notes.domain.model.Cursor;
import io.github.jeffromine.notes.domain.model.Entry;
import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.domain.model.Note;
import io.github.jeffromine.notes.domain.model.NoteQuery;
import io.github.jeffromine.notes.domain.model.Page;
import io.github.jeffromine.notes.domain.model.Team;
import io.github.jeffromine.notes.domain.model.User;
import io.github.jeffromine.notes.domain.model.Visibility;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static io.github.jeffromine.notes.support.Fixtures.T0;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The behaviour every store adapter must share. Subclasses supply the repositories; each test uses fresh ids and
 * usernames so no cleanup is needed between tests, on Cassandra or in memory.
 */
public abstract class AbstractStoreContractTest {

    protected abstract UserRepository users();

    protected abstract TeamRepository teams();

    protected abstract MembershipRepository memberships();

    protected abstract NoteRepository notes();

    protected abstract EntryRepository entries();

    protected abstract AttachmentRepository attachments();

    static String fresh(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    static Note note(String owner, Visibility visibility, UUID teamId, Set<String> tags, Instant createdAt) {
        return new Note(UUID.randomUUID(), owner, teamId, "t", "b", BodyFormat.PLAIN, tags, visibility, false, 1, createdAt, createdAt);
    }

    static Note note(UUID id, String owner, Instant createdAt) {
        return new Note(id, owner, null, "t", "b", BodyFormat.PLAIN, Set.of(), Visibility.PRIVATE, false, 1, createdAt, createdAt);
    }

    // ------------------------------------------------------------------ users and teams

    @Test
    void insertIfAbsentIsIdempotentForUsersAndTeams() {
        String name = fresh("user");
        User user = new User(name, "hash", "Name", T0);
        assertThat(users().insertIfAbsent(user)).isTrue();
        assertThat(users().insertIfAbsent(new User(name, "other", "Other", T0))).isFalse();
        assertThat(users().findByUsername(name)).contains(user);

        Team team = new Team(UUID.randomUUID(), fresh("team"), T0);
        assertThat(teams().insertIfAbsent(team)).isTrue();
        assertThat(teams().insertIfAbsent(team)).isFalse();
        assertThat(teams().findById(team.id())).contains(team);
        assertThat(teams().findAllById(List.of(team.id(), UUID.randomUUID()))).containsExactly(team);
    }

    @Test
    void membershipsAreReadableFromBothSidesAndReplaceable() {
        UUID team = UUID.randomUUID();
        String user = fresh("u");
        memberships().put(new Membership(team, user, Membership.Role.MEMBER));
        memberships().put(new Membership(team, user, Membership.Role.ADMIN));
        assertThat(memberships().byTeam(team)).containsExactly(new Membership(team, user, Membership.Role.ADMIN));
        assertThat(memberships().byUser(user)).containsExactly(new Membership(team, user, Membership.Role.ADMIN));
        memberships().remove(team, user);
        assertThat(memberships().byTeam(team)).isEmpty();
        assertThat(memberships().byUser(user)).isEmpty();
    }

    // ------------------------------------------------------------------ notes

    @Test
    void notesRoundTripAndTheVersionCheckIsAtomicInTheStore() {
        Note n = note(fresh("o"), Visibility.PRIVATE, null, Set.of("a"), T0);
        notes().insert(n);
        assertThat(notes().findById(n.id())).contains(n);
        Note v2 = n.withArchived(true, T0.plusSeconds(1));
        assertThat(notes().updateIfVersion(v2, 1)).isTrue();
        assertThat(notes().updateIfVersion(v2.withArchived(false, T0), 1)).as("stale").isFalse();
        assertThat(notes().findById(n.id())).contains(v2);
        notes().delete(n.id());
        assertThat(notes().findById(n.id())).isEmpty();
        assertThat(notes().updateIfVersion(v2, 2)).as("missing").isFalse();
    }

    @Test
    void listIsNewestFirstWithUnsignedUuidTieBreakAndKeysetCursor() {
        String owner = fresh("o");
        UUID high = UUID.fromString("ffffffff-0000-4000-8000-000000000001");
        UUID low = UUID.fromString("00000000-0000-4000-8000-000000000001");
        Note a = note(low, owner, T0);
        Note b = note(high, owner, T0);
        Note c = note(UUID.randomUUID(), owner, T0.plusSeconds(5));
        notes().insert(a);
        notes().insert(b);
        notes().insert(c);
        NoteQuery.Scope scope = new NoteQuery.Scope.Owner(owner);
        Page<Note> first = notes().list(new NoteQuery(scope, Optional.empty(), false, 2, Optional.empty()));
        assertThat(first.items()).extracting(Note::id).containsExactly(c.id(), high);
        assertThat(first.nextCursor()).isPresent();
        Page<Note> second = notes().list(new NoteQuery(scope, Optional.empty(), false, 2,
                Optional.of(Cursor.decode(first.nextCursor().get()))));
        assertThat(second.items()).extracting(Note::id).containsExactly(low);
        assertThat(second.nextCursor()).isEmpty();
    }

    @Test
    void listFiltersByTagAndArchivedInsideThePartition() {
        String owner = fresh("o");
        Note tagged = note(owner, Visibility.PRIVATE, null, Set.of("x", "y"), T0);
        Note other = note(owner, Visibility.PRIVATE, null, Set.of("z"), T0.plusSeconds(1));
        Note archived = note(owner, Visibility.PRIVATE, null, Set.of("x"), T0.plusSeconds(2)).withArchived(true, T0.plusSeconds(3));
        notes().insert(tagged);
        notes().insert(other);
        notes().insert(archived);
        NoteQuery.Scope scope = new NoteQuery.Scope.Owner(owner);
        assertThat(notes().list(new NoteQuery(scope, Optional.of("x"), false, 10, Optional.empty())).items())
                .extracting(Note::id).containsExactly(tagged.id());
        assertThat(notes().list(new NoteQuery(scope, Optional.of("x"), true, 10, Optional.empty())).items())
                .extracting(Note::id).containsExactly(archived.id(), tagged.id());
        assertThat(notes().list(new NoteQuery(scope, Optional.empty(), false, 10, Optional.empty())).items())
                .extracting(Note::id).containsExactly(other.id(), tagged.id());
    }

    @Test
    void teamListHoldsOnlyTeamNotesAndFollowsVisibilityChanges() {
        UUID team = UUID.randomUUID();
        String owner = fresh("o");
        Note teamNote = note(owner, Visibility.TEAM, team, Set.of(), T0);
        Note orgNote = note(owner, Visibility.ORGANIZATION, null, Set.of(), T0.plusSeconds(1));
        notes().insert(teamNote);
        notes().insert(orgNote);
        NoteQuery.Scope scope = new NoteQuery.Scope.Team(team);
        assertThat(notes().list(new NoteQuery(scope, Optional.empty(), false, 10, Optional.empty())).items())
                .extracting(Note::id).containsExactly(teamNote.id());
        Note nowPrivate = new Note(teamNote.id(), owner, null, "t", "b", BodyFormat.PLAIN, Set.of(), Visibility.PRIVATE,
                false, 2, teamNote.createdAt(), T0.plusSeconds(2));
        assertThat(notes().updateIfVersion(nowPrivate, 1)).isTrue();
        assertThat(notes().list(new NoteQuery(scope, Optional.empty(), false, 10, Optional.empty())).items()).isEmpty();
        Note backToTeam = new Note(teamNote.id(), owner, team, "t", "b", BodyFormat.PLAIN, Set.of(), Visibility.TEAM,
                false, 3, teamNote.createdAt(), T0.plusSeconds(3));
        assertThat(notes().updateIfVersion(backToTeam, 2)).isTrue();
        assertThat(notes().list(new NoteQuery(scope, Optional.empty(), false, 10, Optional.empty())).items())
                .extracting(Note::id).containsExactly(teamNote.id());
    }

    @Test
    void scanAllSeesEveryNote() {
        Note n = note(fresh("o"), Visibility.PRIVATE, null, Set.of(), T0);
        notes().insert(n);
        try (var all = notes().scanAll()) {
            assertThat(all.map(Note::id)).contains(n.id());
        }
    }

    // ------------------------------------------------------------------ entries

    @Test
    void entriesAreOldestFirstVersionedAndCascade() {
        UUID noteId = UUID.randomUUID();
        Entry e1 = new Entry(noteId, UUID.randomUUID(), "a", "1", BodyFormat.PLAIN, 1, T0, T0);
        Entry e2 = new Entry(noteId, UUID.randomUUID(), "b", "2", BodyFormat.PLAIN, 1, T0.plusSeconds(1), T0.plusSeconds(1));
        entries().insert(e2);
        entries().insert(e1);
        assertThat(entries().byNote(noteId)).containsExactly(e1, e2);
        Entry e1v2 = new Entry(noteId, e1.id(), "a", "edited", BodyFormat.PLAIN, 2, T0, T0.plusSeconds(2));
        assertThat(entries().updateIfVersion(e1v2, 1)).isTrue();
        assertThat(entries().updateIfVersion(e1v2, 1)).isFalse();
        assertThat(entries().find(noteId, e1.id())).contains(e1v2);
        entries().delete(noteId, e2.id());
        assertThat(entries().byNote(noteId)).containsExactly(e1v2);
        entries().deleteAllByNote(noteId);
        assertThat(entries().byNote(noteId)).isEmpty();
    }

    @Test
    void concurrentAppendsAllLand() throws Exception {
        UUID noteId = UUID.randomUUID();
        int writers = 8;
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(writers);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < writers; i++) {
                int n = i;
                futures.add(pool.submit(() -> {
                    go.await();
                    entries().insert(new Entry(noteId, UUID.randomUUID(), "w" + n, "x", BodyFormat.PLAIN, 1,
                            T0.plusMillis(n), T0.plusMillis(n)));
                    return null;
                }));
            }
            go.countDown();
            for (Future<?> f : futures) {
                f.get();
            }
        } finally {
            pool.shutdown();
        }
        assertThat(entries().byNote(noteId)).hasSize(writers);
    }

    // ------------------------------------------------------------------ attachments

    @Test
    void attachmentMetadataRoundTripsAndCascades() {
        UUID noteId = UUID.randomUUID();
        Attachment a = new Attachment(noteId, UUID.randomUUID(), "a.pdf", "application/pdf", 10, "00".repeat(32), "u", "words", 1, T0);
        Attachment b = new Attachment(noteId, UUID.randomUUID(), "b.png", "image/png", 20, "11".repeat(32), "u", null, 2, T0.plusSeconds(1));
        attachments().insert(b);
        attachments().insert(a);
        assertThat(attachments().byNote(noteId)).containsExactly(a, b);
        assertThat(attachments().find(noteId, b.id())).contains(b);
        attachments().delete(noteId, a.id());
        assertThat(attachments().byNote(noteId)).containsExactly(b);
        attachments().deleteAllByNote(noteId);
        assertThat(attachments().byNote(noteId)).isEmpty();
    }
}

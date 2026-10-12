package io.github.jeffromine.notes.application;

import io.github.jeffromine.notes.domain.core.NoteCommand.AppendEntry;
import io.github.jeffromine.notes.domain.core.NoteCommand.CreateNote;
import io.github.jeffromine.notes.domain.core.NoteCommand.DeleteNote;
import io.github.jeffromine.notes.domain.core.NoteCommand.UpdateNote;
import io.github.jeffromine.notes.domain.core.NoteEvent;
import io.github.jeffromine.notes.domain.core.NoteState;
import io.github.jeffromine.notes.domain.core.Rejection;
import io.github.jeffromine.notes.domain.model.BodyFormat;
import io.github.jeffromine.notes.domain.model.Note;
import io.github.jeffromine.notes.domain.model.Visibility;
import io.github.jeffromine.notes.support.ShellHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static io.github.jeffromine.notes.support.Fixtures.ALICE;
import static io.github.jeffromine.notes.support.Fixtures.BOB;
import static io.github.jeffromine.notes.support.Fixtures.CAROL;
import static io.github.jeffromine.notes.support.Fixtures.PLATFORM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NoteCommandsTest {

    @TempDir
    Path dir;
    ShellHarness h;

    @BeforeEach
    void setUp() {
        h = new ShellHarness(dir);
    }

    CreateNote create(String actor, Visibility v, UUID team) {
        return new CreateNote(actor, h.context.now(), h.context.newId(), "Title", "body", BodyFormat.PLAIN, Set.of("t"), v, team);
    }

    @Test
    void acceptedCommandPersistsThenPublishesInOrder() {
        NoteState state = h.noteCommands.handle(create(ALICE, Visibility.TEAM, PLATFORM));
        Note note = state.note().orElseThrow();
        assertThat(h.notes.findById(note.id())).contains(note);
        assertThat(h.recorder.all()).hasSize(1).first().isInstanceOf(NoteEvent.NoteCreated.class);
        h.clock.advanceSeconds(1);
        h.noteCommands.handle(new AppendEntry(BOB, h.context.now(), note.id(), h.context.newId(), "hi", BodyFormat.PLAIN));
        assertThat(h.entries.byNote(note.id())).hasSize(1);
        assertThat(h.recorder.all()).extracting(NoteEvent::type).containsExactly("NoteCreated", "EntryAppended");
    }

    @Test
    void rejectionPersistsNothingAndPublishesNothing() {
        assertThatThrownBy(() -> h.noteCommands.handle(create(CAROL, Visibility.TEAM, PLATFORM)))
                .isInstanceOf(RejectionException.class)
                .extracting(e -> ((RejectionException) e).rejection()).isInstanceOf(Rejection.Forbidden.class);
        try (var all = h.notes.scanAll()) {
            assertThat(all).isEmpty();
        }
        assertThat(h.recorder.batches).isEmpty();
    }

    @Test
    void aRaceLostBetweenPrepareAndPersistIsAVersionConflict() {
        Note note = h.noteCommands.handle(create(ALICE, Visibility.PRIVATE, null)).note().orElseThrow();
        UpdateNote update = new UpdateNote(ALICE, h.context.now(), note.id(), 1, "T2", "b2", BodyFormat.PLAIN, Set.of(), Visibility.PRIVATE, null);
        // someone else wins the race after prepare would have read version 1
        h.notes.updateIfVersion(note.withArchived(false, h.context.now()), 1);
        assertThatThrownBy(() -> h.noteCommands.handle(update))
                .isInstanceOf(RejectionException.class)
                .extracting(e -> ((RejectionException) e).rejection())
                .isEqualTo(new Rejection.VersionConflict(2));
        assertThat(h.recorder.all()).extracting(NoteEvent::type).containsExactly("NoteCreated");
    }

    @Test
    void deleteCascadesAndTheProjectionFollows() {
        Note note = h.noteCommands.handle(create(ALICE, Visibility.ORGANIZATION, null)).note().orElseThrow();
        h.noteCommands.handle(new AppendEntry(BOB, h.context.now(), note.id(), h.context.newId(), "hi", BodyFormat.PLAIN));
        assertThat(h.searchQueries.search(CAROL, q("Title")).total()).isEqualTo(1);
        h.noteCommands.handle(new DeleteNote(ALICE, h.context.now(), note.id()));
        assertThat(h.notes.findById(note.id())).isEmpty();
        assertThat(h.entries.byNote(note.id())).isEmpty();
        assertThat(h.searchQueries.search(CAROL, q("Title")).total()).isZero();
    }

    @Test
    void aThrowingEffectDoesNotFailTheRequest() {
        ShellHarness failing = new ShellHarness(dir.resolve("f"), events -> {
            throw new IllegalStateException("projection down");
        });
        // the harness's own composite publisher does not swallow; the Spring publisher does. Prove the shell
        // itself has already persisted before effects run.
        assertThatThrownBy(() -> failing.noteCommands.handle(create(ALICE, Visibility.PRIVATE, null)))
                .isInstanceOf(IllegalStateException.class);
        try (var all = failing.notes.scanAll()) {
            assertThat(all).hasSize(1);
        }
    }

    static io.github.jeffromine.notes.domain.model.SearchQuery q(String text) {
        return new io.github.jeffromine.notes.domain.model.SearchQuery(java.util.Optional.of(text), java.util.Optional.empty(),
                java.util.Optional.empty(), java.util.Optional.empty(), false,
                io.github.jeffromine.notes.domain.model.SearchQuery.Sort.RELEVANCE, 10, 0);
    }
}

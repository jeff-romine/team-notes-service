package io.github.jeffromine.notes.domain.core;

import io.github.jeffromine.notes.domain.core.NoteCommand.AddAttachment;
import io.github.jeffromine.notes.domain.core.NoteCommand.AppendEntry;
import io.github.jeffromine.notes.domain.core.NoteCommand.ArchiveNote;
import io.github.jeffromine.notes.domain.core.NoteCommand.CreateNote;
import io.github.jeffromine.notes.domain.core.NoteCommand.DeleteEntry;
import io.github.jeffromine.notes.domain.core.NoteCommand.DeleteNote;
import io.github.jeffromine.notes.domain.core.NoteCommand.RemoveAttachment;
import io.github.jeffromine.notes.domain.core.NoteCommand.UnarchiveNote;
import io.github.jeffromine.notes.domain.core.NoteCommand.UpdateEntry;
import io.github.jeffromine.notes.domain.core.NoteCommand.UpdateNote;
import io.github.jeffromine.notes.domain.core.NoteEvent.AttachmentAdded;
import io.github.jeffromine.notes.domain.core.NoteEvent.AttachmentRemoved;
import io.github.jeffromine.notes.domain.core.NoteEvent.EntryAppended;
import io.github.jeffromine.notes.domain.core.NoteEvent.EntryDeleted;
import io.github.jeffromine.notes.domain.core.NoteEvent.EntryUpdated;
import io.github.jeffromine.notes.domain.core.NoteEvent.NoteArchived;
import io.github.jeffromine.notes.domain.core.NoteEvent.NoteCreated;
import io.github.jeffromine.notes.domain.core.NoteEvent.NoteDeleted;
import io.github.jeffromine.notes.domain.core.NoteEvent.NoteUnarchived;
import io.github.jeffromine.notes.domain.core.NoteEvent.NoteUpdated;
import io.github.jeffromine.notes.domain.core.Rejection.Forbidden;
import io.github.jeffromine.notes.domain.core.Rejection.Invalid;
import io.github.jeffromine.notes.domain.core.Rejection.NotFound;
import io.github.jeffromine.notes.domain.core.Rejection.TooLarge;
import io.github.jeffromine.notes.domain.core.Rejection.Unsupported;
import io.github.jeffromine.notes.domain.core.Rejection.VersionConflict;
import io.github.jeffromine.notes.domain.model.Attachment;
import io.github.jeffromine.notes.domain.model.BodyFormat;
import io.github.jeffromine.notes.domain.model.Entry;
import io.github.jeffromine.notes.domain.model.Note;
import io.github.jeffromine.notes.domain.model.Visibility;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static io.github.jeffromine.notes.domain.core.DeciderAssert.givenNote;
import static io.github.jeffromine.notes.support.Fixtures.ALICE;
import static io.github.jeffromine.notes.support.Fixtures.ATT_1;
import static io.github.jeffromine.notes.support.Fixtures.BOB;
import static io.github.jeffromine.notes.support.Fixtures.CAROL;
import static io.github.jeffromine.notes.support.Fixtures.DATA;
import static io.github.jeffromine.notes.support.Fixtures.ENTRY_1;
import static io.github.jeffromine.notes.support.Fixtures.ENTRY_2;
import static io.github.jeffromine.notes.support.Fixtures.NOTE_1;
import static io.github.jeffromine.notes.support.Fixtures.PLATFORM;
import static io.github.jeffromine.notes.support.Fixtures.T0;
import static io.github.jeffromine.notes.support.Fixtures.T1;
import static io.github.jeffromine.notes.support.Fixtures.T2;
import static io.github.jeffromine.notes.support.Fixtures.attachment;
import static io.github.jeffromine.notes.support.Fixtures.entry;
import static io.github.jeffromine.notes.support.Fixtures.orgNote;
import static io.github.jeffromine.notes.support.Fixtures.privateNote;
import static io.github.jeffromine.notes.support.Fixtures.teamNote;
import static org.assertj.core.api.Assertions.assertThat;

class NoteDeciderTest {

    static final Set<UUID> ALICE_TEAMS = Set.of(PLATFORM);
    static final Set<UUID> BOB_TEAMS = Set.of(PLATFORM, DATA);
    static final Set<UUID> CAROL_TEAMS = Set.of(DATA);

    static NoteState aliceOwns(Note note, Set<UUID> actorTeams) {
        return NoteState.of(note, actorTeams);
    }

    // ------------------------------------------------------------------ CreateNote

    @Nested
    class CreateNoteBlock {
        CreateNote create(Visibility visibility, UUID teamId) {
            return new CreateNote(ALICE, T0, NOTE_1, "Title", "body", BodyFormat.PLAIN, Set.of("a-1"), visibility, teamId);
        }

        @Test
        void privateNoteIsCreatedAtVersionOne() {
            List<NoteEvent> events = givenNote(NoteState.absent(ALICE_TEAMS))
                    .when(create(Visibility.PRIVATE, null)).expectEvents(NoteCreated.class);
            Note note = ((NoteCreated) events.getFirst()).note();
            assertThat(note.version()).isEqualTo(1);
            assertThat(note.owner()).isEqualTo(ALICE);
            assertThat(note.createdAt()).isEqualTo(T0);
            assertThat(note.updatedAt()).isEqualTo(T0);
        }

        @Test
        void teamNoteRequiresMembership() {
            Forbidden f = givenNote(NoteState.absent(ALICE_TEAMS))
                    .when(create(Visibility.TEAM, DATA)).expectRejected(Forbidden.class);
            assertThat(f.reason()).contains("member");
            givenNote(NoteState.absent(ALICE_TEAMS)).when(create(Visibility.TEAM, PLATFORM)).expectEvents(NoteCreated.class);
        }

        @Test
        void teamIdMustMatchVisibility() {
            Invalid i = givenNote(NoteState.absent(ALICE_TEAMS))
                    .when(create(Visibility.PRIVATE, PLATFORM)).expectRejected(Invalid.class);
            assertThat(i.errors()).extracting(Rejection.FieldError::field).containsExactly("teamId");
            givenNote(NoteState.absent(ALICE_TEAMS)).when(create(Visibility.TEAM, null)).expectRejected(Invalid.class);
        }

        @Test
        void fieldLimitsAreGuarded() {
            Invalid i = givenNote(NoteState.absent(ALICE_TEAMS))
                    .when(new CreateNote(ALICE, T0, NOTE_1, " ", "x".repeat(20_001), BodyFormat.PLAIN,
                            Set.of("Bad Tag"), Visibility.PRIVATE, null))
                    .expectRejected(Invalid.class);
            assertThat(i.errors()).extracting(Rejection.FieldError::field).containsExactlyInAnyOrder("title", "body", "tags");
        }

        @Test
        void existingIdIsRefused() {
            givenNote(aliceOwns(privateNote(NOTE_1, ALICE), ALICE_TEAMS))
                    .when(create(Visibility.PRIVATE, null)).expectRejected(Invalid.class);
        }
    }

    // ------------------------------------------------------------------ visibility matrix (shared by read-ish guards)

    static Stream<org.junit.jupiter.params.provider.Arguments> visibilityMatrix() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of("private note, owner", privateNote(NOTE_1, ALICE), ALICE, ALICE_TEAMS, true),
                org.junit.jupiter.params.provider.Arguments.of("private note, other", privateNote(NOTE_1, ALICE), BOB, BOB_TEAMS, false),
                org.junit.jupiter.params.provider.Arguments.of("team note, member", teamNote(NOTE_1, ALICE, PLATFORM), BOB, BOB_TEAMS, true),
                org.junit.jupiter.params.provider.Arguments.of("team note, outsider", teamNote(NOTE_1, ALICE, PLATFORM), CAROL, CAROL_TEAMS, false),
                org.junit.jupiter.params.provider.Arguments.of("org note, anyone", orgNote(NOTE_1, ALICE), CAROL, CAROL_TEAMS, true));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("visibilityMatrix")
    void appendingFollowsVisibility(String label, Note note, String actor, Set<UUID> actorTeams, boolean visible) {
        var when = givenNote(NoteState.of(note, actorTeams))
                .when(new AppendEntry(actor, T1, NOTE_1, ENTRY_1, "hi", BodyFormat.PLAIN));
        if (visible) {
            when.expectEvents(EntryAppended.class);
        } else {
            when.expectRejected(NotFound.class);
        }
    }

    // ------------------------------------------------------------------ UpdateNote

    @Nested
    class UpdateNoteBlock {
        UpdateNote update(String actor, long version) {
            return new UpdateNote(actor, T1, NOTE_1, version, "New title", "new body", BodyFormat.MARKDOWN,
                    Set.of("b-2"), Visibility.ORGANIZATION, null);
        }

        @Test
        void ownerReplacesFieldsAndBumpsVersion() {
            List<NoteEvent> events = givenNote(aliceOwns(privateNote(NOTE_1, ALICE), ALICE_TEAMS))
                    .when(update(ALICE, 1)).expectEvents(NoteUpdated.class);
            Note after = ((NoteUpdated) events.getFirst()).note();
            assertThat(after.version()).isEqualTo(2);
            assertThat(after.title()).isEqualTo("New title");
            assertThat(after.visibility()).isEqualTo(Visibility.ORGANIZATION);
            assertThat(after.createdAt()).isEqualTo(T0);
            assertThat(after.updatedAt()).isEqualTo(T1);
        }

        @Test
        void staleVersionIsAConflictCarryingTheCurrentOne() {
            VersionConflict c = givenNote(aliceOwns(privateNote(NOTE_1, ALICE), ALICE_TEAMS))
                    .when(update(ALICE, 7)).expectRejected(VersionConflict.class);
            assertThat(c.currentVersion()).isEqualTo(1);
        }

        @Test
        void visibleButNotOwnedIsForbidden_invisibleIsNotFound() {
            givenNote(NoteState.of(teamNote(NOTE_1, ALICE, PLATFORM), BOB_TEAMS)).when(update(BOB, 1)).expectRejected(Forbidden.class);
            givenNote(NoteState.of(privateNote(NOTE_1, ALICE), BOB_TEAMS)).when(update(BOB, 1)).expectRejected(NotFound.class);
            givenNote(NoteState.absent(BOB_TEAMS)).when(update(BOB, 1)).expectRejected(NotFound.class);
        }

        @Test
        void archivedNoteIsReadOnly() {
            Note archived = privateNote(NOTE_1, ALICE).withArchived(true, T1);
            Forbidden f = givenNote(aliceOwns(archived, ALICE_TEAMS)).when(update(ALICE, 2)).expectRejected(Forbidden.class);
            assertThat(f.reason()).contains("archived");
        }
    }

    // ------------------------------------------------------------------ Archive / Unarchive

    @Nested
    class ArchiveBlocks {
        @Test
        void archiveBumpsVersionAndIsIdempotent() {
            NoteState live = aliceOwns(privateNote(NOTE_1, ALICE), ALICE_TEAMS);
            List<NoteEvent> events = givenNote(live).when(new ArchiveNote(ALICE, T1, NOTE_1)).expectEvents(NoteArchived.class);
            Note archived = ((NoteArchived) events.getFirst()).note();
            assertThat(archived.archived()).isTrue();
            assertThat(archived.version()).isEqualTo(2);
            givenNote(aliceOwns(archived, ALICE_TEAMS)).when(new ArchiveNote(ALICE, T2, NOTE_1)).expectNoEvents();
        }

        @Test
        void unarchiveMirrorsArchive() {
            Note archived = privateNote(NOTE_1, ALICE).withArchived(true, T1);
            givenNote(aliceOwns(archived, ALICE_TEAMS)).when(new UnarchiveNote(ALICE, T2, NOTE_1)).expectEvents(NoteUnarchived.class);
            givenNote(aliceOwns(privateNote(NOTE_1, ALICE), ALICE_TEAMS)).when(new UnarchiveNote(ALICE, T2, NOTE_1)).expectNoEvents();
        }

        @Test
        void onlyTheOwnerArchives() {
            givenNote(NoteState.of(orgNote(NOTE_1, ALICE), BOB_TEAMS)).when(new ArchiveNote(BOB, T1, NOTE_1)).expectRejected(Forbidden.class);
        }
    }

    // ------------------------------------------------------------------ DeleteNote

    @Test
    void deleteCarriesEveryChildIdAndEmptiesTheState() {
        NoteState state = new NoteState(Optional.of(privateNote(NOTE_1, ALICE)),
                List.of(entry(NOTE_1, ENTRY_1, BOB)), List.of(attachment(NOTE_1, ATT_1, BOB)), ALICE_TEAMS);
        List<NoteEvent> events = givenNote(state).when(new DeleteNote(ALICE, T1, NOTE_1)).expectEvents(NoteDeleted.class);
        NoteDeleted deleted = (NoteDeleted) events.getFirst();
        assertThat(deleted.entryIds()).containsExactly(ENTRY_1);
        assertThat(deleted.attachmentIds()).containsExactly(ATT_1);
        NoteState after = NoteDecider.evolve(state, deleted);
        assertThat(after.note()).isEmpty();
        assertThat(after.entries()).isEmpty();
        assertThat(after.attachments()).isEmpty();
        assertThat(after.actorTeamIds()).isEqualTo(ALICE_TEAMS);
    }

    // ------------------------------------------------------------------ Entries

    @Nested
    class EntryBlocks {
        final NoteState withBobsEntry = new NoteState(Optional.of(teamNote(NOTE_1, ALICE, PLATFORM)),
                List.of(entry(NOTE_1, ENTRY_1, BOB)), List.of(), BOB_TEAMS);

        @Test
        void anyoneWhoSeesTheNoteAppends() {
            List<NoteEvent> events = givenNote(NoteState.of(teamNote(NOTE_1, ALICE, PLATFORM), BOB_TEAMS))
                    .when(new AppendEntry(BOB, T1, NOTE_1, ENTRY_1, "hello", BodyFormat.MARKDOWN))
                    .expectEvents(EntryAppended.class);
            Entry e = ((EntryAppended) events.getFirst()).entry();
            assertThat(e.author()).isEqualTo(BOB);
            assertThat(e.version()).isEqualTo(1);
        }

        @Test
        void appendRefusedWhenArchivedOrFullOrBlank() {
            Note archived = teamNote(NOTE_1, ALICE, PLATFORM).withArchived(true, T1);
            givenNote(NoteState.of(archived, BOB_TEAMS))
                    .when(new AppendEntry(BOB, T2, NOTE_1, ENTRY_1, "x", BodyFormat.PLAIN)).expectRejected(Forbidden.class);
            List<Entry> full = IntStream.range(0, Limits.defaults().entriesMax())
                    .mapToObj(i -> entry(NOTE_1, new UUID(0, i + 1), BOB)).toList();
            givenNote(new NoteState(Optional.of(teamNote(NOTE_1, ALICE, PLATFORM)), full, List.of(), BOB_TEAMS))
                    .when(new AppendEntry(BOB, T2, NOTE_1, ENTRY_1, "x", BodyFormat.PLAIN)).expectRejected(Invalid.class);
            givenNote(NoteState.of(teamNote(NOTE_1, ALICE, PLATFORM), BOB_TEAMS))
                    .when(new AppendEntry(BOB, T2, NOTE_1, ENTRY_1, " ", BodyFormat.PLAIN)).expectRejected(Invalid.class);
        }

        @Test
        void authorEditsWithVersion_ownerMayNot() {
            List<NoteEvent> events = givenNote(withBobsEntry)
                    .when(new UpdateEntry(BOB, T2, NOTE_1, ENTRY_1, 1, "edited", BodyFormat.PLAIN)).expectEvents(EntryUpdated.class);
            assertThat(((EntryUpdated) events.getFirst()).entry().version()).isEqualTo(2);
            givenNote(withBobsEntry).when(new UpdateEntry(BOB, T2, NOTE_1, ENTRY_1, 5, "edited", BodyFormat.PLAIN))
                    .expectRejected(VersionConflict.class);
            NoteState asAlice = new NoteState(withBobsEntry.note(), withBobsEntry.entries(), List.of(), ALICE_TEAMS);
            givenNote(asAlice).when(new UpdateEntry(ALICE, T2, NOTE_1, ENTRY_1, 1, "edited", BodyFormat.PLAIN))
                    .expectRejected(Forbidden.class);
            givenNote(withBobsEntry).when(new UpdateEntry(BOB, T2, NOTE_1, ENTRY_2, 1, "edited", BodyFormat.PLAIN))
                    .expectRejected(NotFound.class);
        }

        @Test
        void authorOrOwnerDeletes_othersMayNot() {
            givenNote(withBobsEntry).when(new DeleteEntry(BOB, T2, NOTE_1, ENTRY_1)).expectEvents(EntryDeleted.class);
            NoteState asAlice = new NoteState(withBobsEntry.note(), withBobsEntry.entries(), List.of(), ALICE_TEAMS);
            givenNote(asAlice).when(new DeleteEntry(ALICE, T2, NOTE_1, ENTRY_1)).expectEvents(EntryDeleted.class);
            NoteState asCarolMember = new NoteState(withBobsEntry.note(), withBobsEntry.entries(), List.of(), Set.of(PLATFORM));
            givenNote(asCarolMember).when(new DeleteEntry(CAROL, T2, NOTE_1, ENTRY_1)).expectRejected(Forbidden.class);
        }
    }

    // ------------------------------------------------------------------ Attachments

    @Nested
    class AttachmentBlocks {
        AddAttachment add(String type, long size) {
            return new AddAttachment(BOB, T1, NOTE_1, ATT_1, "spec.pdf", type, size, "ab".repeat(32), "text", 1);
        }

        @Test
        void contributorAttachesAllowedTypesUnderTheLimit() {
            List<NoteEvent> events = givenNote(NoteState.of(teamNote(NOTE_1, ALICE, PLATFORM), BOB_TEAMS))
                    .when(add("application/pdf", 1000)).expectEvents(AttachmentAdded.class);
            Attachment a = ((AttachmentAdded) events.getFirst()).attachment();
            assertThat(a.uploadedBy()).isEqualTo(BOB);
        }

        @Test
        void wrongTypeIsUnsupported_tooBigIsTooLarge() {
            Unsupported u = givenNote(NoteState.of(teamNote(NOTE_1, ALICE, PLATFORM), BOB_TEAMS))
                    .when(add("application/x-msdownload", 10)).expectRejected(Unsupported.class);
            assertThat(u.contentType()).isEqualTo("application/x-msdownload");
            TooLarge t = givenNote(NoteState.of(teamNote(NOTE_1, ALICE, PLATFORM), BOB_TEAMS))
                    .when(add("application/pdf", Limits.defaults().attachmentMaxBytes() + 1)).expectRejected(TooLarge.class);
            assertThat(t.limitBytes()).isEqualTo(Limits.defaults().attachmentMaxBytes());
        }

        @Test
        void uploaderOrOwnerRemoves() {
            NoteState state = new NoteState(Optional.of(teamNote(NOTE_1, ALICE, PLATFORM)), List.of(),
                    List.of(attachment(NOTE_1, ATT_1, BOB)), BOB_TEAMS);
            givenNote(state).when(new RemoveAttachment(BOB, T2, NOTE_1, ATT_1)).expectEvents(AttachmentRemoved.class);
            givenNote(new NoteState(state.note(), List.of(), state.attachments(), ALICE_TEAMS))
                    .when(new RemoveAttachment(ALICE, T2, NOTE_1, ATT_1)).expectEvents(AttachmentRemoved.class);
            givenNote(new NoteState(state.note(), List.of(), state.attachments(), Set.of(PLATFORM)))
                    .when(new RemoveAttachment(CAROL, T2, NOTE_1, ATT_1)).expectRejected(Forbidden.class);
            givenNote(state).when(new RemoveAttachment(BOB, T2, NOTE_1, ENTRY_1)).expectRejected(NotFound.class);
        }
    }

    // ------------------------------------------------------------------ evolve: every event type, no mutation, replay

    static final NoteState BASE = new NoteState(Optional.of(teamNote(NOTE_1, ALICE, PLATFORM)),
            List.of(entry(NOTE_1, ENTRY_1, BOB)), List.of(attachment(NOTE_1, ATT_1, BOB)), BOB_TEAMS);

    static Stream<NoteEvent> everyEventType() {
        Note note = BASE.note().orElseThrow();
        return Stream.of(
                new NoteCreated(note, ALICE),
                new NoteUpdated(note, ALICE),
                new NoteArchived(note.withArchived(true, T1), ALICE),
                new NoteUnarchived(note, ALICE),
                new NoteDeleted(NOTE_1, List.of(ENTRY_1), List.of(ATT_1), ALICE, T1),
                new EntryAppended(entry(NOTE_1, ENTRY_2, CAROL), CAROL),
                new EntryUpdated(entry(NOTE_1, ENTRY_1, BOB), BOB),
                new EntryDeleted(NOTE_1, ENTRY_1, BOB, T1),
                new AttachmentAdded(attachment(NOTE_1, ENTRY_2, CAROL), CAROL),
                new AttachmentRemoved(NOTE_1, ATT_1, BOB, T1));
    }

    @Test
    void everyEventTypeIsCoveredByTheFoldTable() {
        assertThat(everyEventType().map(e -> (Class<?>) e.getClass()).distinct().count())
                .isEqualTo(NoteEvent.class.getPermittedSubclasses().length);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyEventType")
    void evolveReturnsANewValueAndLeavesItsInputUntouched(NoteEvent event) {
        NoteState snapshot = new NoteState(BASE.note(), BASE.entries(), BASE.attachments(), BASE.actorTeamIds());
        NoteState after = NoteDecider.evolve(BASE, event);
        assertThat(BASE).isEqualTo(snapshot);
        assertThat(after).isNotSameAs(BASE);
        assertThat(after.actorTeamIds()).isEqualTo(BASE.actorTeamIds());
    }

    @Test
    void replayingTheEventsReproducesTheAppliedState() {
        NoteState s0 = NoteState.absent(ALICE_TEAMS);
        Decision<NoteEvent, NoteState> d1 = NoteDecider.apply(
                new CreateNote(ALICE, T0, NOTE_1, "T", "b", BodyFormat.PLAIN, Set.of(), Visibility.ORGANIZATION, null),
                s0, Limits.defaults());
        Decision.Accepted<NoteEvent, NoteState> a1 = (Decision.Accepted<NoteEvent, NoteState>) d1;
        Decision<NoteEvent, NoteState> d2 = NoteDecider.apply(
                new AppendEntry(ALICE, T1, NOTE_1, ENTRY_1, "e", BodyFormat.PLAIN), a1.state(), Limits.defaults());
        Decision.Accepted<NoteEvent, NoteState> a2 = (Decision.Accepted<NoteEvent, NoteState>) d2;
        NoteState replayed = Stream.concat(a1.events().stream(), a2.events().stream())
                .reduce(s0, NoteDecider::evolve, (x, y) -> y);
        assertThat(replayed).isEqualTo(a2.state());
        assertThat(replayed.entries()).hasSize(1);
    }

    @Test
    void applyRejectsWithoutTouchingTheState() {
        NoteState s = NoteState.absent(ALICE_TEAMS);
        Decision<NoteEvent, NoteState> d = NoteDecider.apply(new DeleteNote(ALICE, T0, NOTE_1), s, Limits.defaults());
        assertThat(d).isInstanceOf(Decision.Rejected.class);
        assertThat(((Decision.Rejected<NoteEvent, NoteState>) d).rejection()).isInstanceOf(NotFound.class);
    }
}

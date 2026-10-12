package io.github.jeffromine.notes.support;

import io.github.jeffromine.notes.adapter.blob.FilesystemBlobStore;
import io.github.jeffromine.notes.adapter.extract.PdfBoxTextExtractor;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryAttachmentRepository;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryEntryRepository;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryMembershipRepository;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryNoteRepository;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryStore;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryTeamRepository;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryUserRepository;
import io.github.jeffromine.notes.adapter.search.lucene.LuceneSearchIndex;
import io.github.jeffromine.notes.application.AttachmentUploads;
import io.github.jeffromine.notes.application.CommandContext;
import io.github.jeffromine.notes.application.NoteCommands;
import io.github.jeffromine.notes.application.NoteQueries;
import io.github.jeffromine.notes.application.ReindexService;
import io.github.jeffromine.notes.application.SearchQueries;
import io.github.jeffromine.notes.application.TeamCommands;
import io.github.jeffromine.notes.application.TeamQueries;
import io.github.jeffromine.notes.application.port.NoteEventPublisher;
import io.github.jeffromine.notes.domain.core.Limits;
import io.github.jeffromine.notes.domain.core.text.NoteDocuments;
import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.domain.model.Team;
import io.github.jeffromine.notes.domain.model.User;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * The whole shell wired over the default-profile adapters with a fixed clock, sequential ids and a recording
 * publisher that also projects into the Lucene index (so search tests see writes). Seeded with the three users
 * and two teams the service itself seeds.
 */
public final class ShellHarness {

    public final InMemoryStore store = new InMemoryStore(Optional.empty());
    public final InMemoryUserRepository users = new InMemoryUserRepository(store);
    public final InMemoryTeamRepository teams = new InMemoryTeamRepository(store);
    public final InMemoryMembershipRepository memberships = new InMemoryMembershipRepository(store);
    public final InMemoryNoteRepository notes = new InMemoryNoteRepository(store);
    public final InMemoryEntryRepository entries = new InMemoryEntryRepository(store);
    public final InMemoryAttachmentRepository attachments = new InMemoryAttachmentRepository(store);
    public final LuceneSearchIndex index = new LuceneSearchIndex();
    public final RecordingPublisher recorder = new RecordingPublisher();
    public final MutableClock clock = new MutableClock(Fixtures.T0);
    public final SequentialIds ids = new SequentialIds();
    public final CommandContext context = new CommandContext(clock, ids);
    public final Limits limits = Limits.defaults();
    public final NoteCommands noteCommands;
    public final TeamCommands teamCommands;
    public final AttachmentUploads uploads;
    public final NoteQueries noteQueries;
    public final TeamQueries teamQueries;
    public final SearchQueries searchQueries;
    public final ReindexService reindex;

    public ShellHarness(Path blobDir) {
        this(blobDir, null);
    }

    /** @param extraPublisher an additional effect, run after the recorder and the projector (may be null) */
    public ShellHarness(Path blobDir, NoteEventPublisher extraPublisher) {
        FilesystemBlobStore blobs = new FilesystemBlobStore(blobDir);
        NoteEventPublisher projector = events -> events.forEach(e -> {
            if (e instanceof io.github.jeffromine.notes.domain.core.NoteEvent.NoteDeleted d) {
                index.delete(d.noteId());
            } else {
                notes.findById(e.noteId()).ifPresent(n ->
                        index.index(NoteDocuments.from(n, entries.byNote(n.id()), attachments.byNote(n.id()))));
            }
        });
        NoteEventPublisher publisher = events -> {
            recorder.publish(events);
            projector.publish(events);
            if (extraPublisher != null) {
                extraPublisher.publish(events);
            }
        };
        noteCommands = new NoteCommands(notes, entries, attachments, memberships, blobs, publisher, limits);
        teamCommands = new TeamCommands(teams, memberships, users, limits);
        uploads = new AttachmentUploads(noteCommands, blobs, new PdfBoxTextExtractor(), context, limits, 100_000);
        noteQueries = new NoteQueries(notes, entries, attachments, memberships, blobs);
        teamQueries = new TeamQueries(users, teams, memberships);
        searchQueries = new SearchQueries(index, noteQueries);
        reindex = new ReindexService(notes, entries, attachments, index);
        seed();
    }

    private void seed() {
        for (String u : List.of(Fixtures.ALICE, Fixtures.BOB, Fixtures.CAROL)) {
            users.insertIfAbsent(new User(u, "{noop}", u, Fixtures.T0));
        }
        teams.insertIfAbsent(new Team(Fixtures.PLATFORM, "platform", Fixtures.T0));
        teams.insertIfAbsent(new Team(Fixtures.DATA, "data", Fixtures.T0));
        memberships.put(new Membership(Fixtures.PLATFORM, Fixtures.ALICE, Membership.Role.ADMIN));
        memberships.put(new Membership(Fixtures.PLATFORM, Fixtures.BOB, Membership.Role.MEMBER));
        memberships.put(new Membership(Fixtures.DATA, Fixtures.BOB, Membership.Role.ADMIN));
        memberships.put(new Membership(Fixtures.DATA, Fixtures.CAROL, Membership.Role.MEMBER));
    }

    /** A clock the test moves forward by hand. */
    public static final class MutableClock extends Clock {
        private Instant now;

        public MutableClock(Instant start) {
            this.now = start;
        }

        public void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}

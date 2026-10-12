package io.github.jeffromine.notes.application;

import io.github.jeffromine.notes.application.port.AttachmentRepository;
import io.github.jeffromine.notes.application.port.BlobStore;
import io.github.jeffromine.notes.application.port.EntryRepository;
import io.github.jeffromine.notes.application.port.MembershipRepository;
import io.github.jeffromine.notes.application.port.NoteEventPublisher;
import io.github.jeffromine.notes.application.port.NoteRepository;
import io.github.jeffromine.notes.domain.core.Decision;
import io.github.jeffromine.notes.domain.core.Limits;
import io.github.jeffromine.notes.domain.core.NoteCommand;
import io.github.jeffromine.notes.domain.core.NoteDecider;
import io.github.jeffromine.notes.domain.core.NoteEvent;
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
import io.github.jeffromine.notes.domain.core.NoteState;
import io.github.jeffromine.notes.domain.core.Rejection;
import io.github.jeffromine.notes.domain.model.Entry;
import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.domain.model.Note;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * The imperative shell for note commands: prepare (read), apply (pure), persist (write), effects (publish).
 *
 * <p>A rejection throws before anything is written, so nothing is persisted and nothing is projected. On
 * acceptance the events are persisted in emission order; version-conditional writes are re-checked by the store,
 * so a race lost between prepare and persist surfaces as the same {@code VersionConflict} the decider would have
 * produced. Effects run last and cannot fail the request.
 */
public final class NoteCommands {

    private final NoteRepository notes;
    private final EntryRepository entries;
    private final AttachmentRepository attachments;
    private final MembershipRepository memberships;
    private final BlobStore blobs;
    private final NoteEventPublisher publisher;
    private final Limits limits;

    public NoteCommands(
            NoteRepository notes,
            EntryRepository entries,
            AttachmentRepository attachments,
            MembershipRepository memberships,
            BlobStore blobs,
            NoteEventPublisher publisher,
            Limits limits) {
        this.notes = notes;
        this.entries = entries;
        this.attachments = attachments;
        this.memberships = memberships;
        this.blobs = blobs;
        this.publisher = publisher;
        this.limits = limits;
    }

    /** @return the state after the command, so the caller can answer without a second read */
    public NoteState handle(NoteCommand command) {
        NoteState state = prepare(command);
        return switch (NoteDecider.apply(command, state, limits)) {
            case Decision.Rejected<NoteEvent, NoteState> r -> throw new RejectionException(r.rejection());
            case Decision.Accepted<NoteEvent, NoteState> a -> {
                for (NoteEvent event : a.events()) {
                    persist(event);
                }
                publisher.publish(a.events());
                yield a.state();
            }
        };
    }

    /** The only reads: the note with its children, and the actor's teams. */
    public NoteState prepare(NoteCommand command) {
        Set<UUID> teams = teamsOf(command.actor());
        Optional<Note> note = notes.findById(command.noteId());
        if (note.isEmpty()) {
            return NoteState.absent(teams);
        }
        return new NoteState(note, entries.byNote(command.noteId()), attachments.byNote(command.noteId()), teams);
    }

    Set<UUID> teamsOf(String username) {
        return memberships.byUser(username).stream().map(Membership::teamId).collect(Collectors.toUnmodifiableSet());
    }

    private void persist(NoteEvent event) {
        switch (event) {
            case NoteCreated e -> notes.insert(e.note());
            case NoteUpdated e -> replaceNote(e.note());
            case NoteArchived e -> replaceNote(e.note());
            case NoteUnarchived e -> replaceNote(e.note());
            case NoteDeleted e -> {
                entries.deleteAllByNote(e.noteId());
                e.attachmentIds().forEach(blobs::delete);
                attachments.deleteAllByNote(e.noteId());
                notes.delete(e.noteId());
            }
            case EntryAppended e -> entries.insert(e.entry());
            case EntryUpdated e -> replaceEntry(e.entry());
            case EntryDeleted e -> entries.delete(e.noteId(), e.entryId());
            case AttachmentAdded e -> attachments.insert(e.attachment());
            case AttachmentRemoved e -> {
                attachments.delete(e.noteId(), e.attachmentId());
                blobs.delete(e.attachmentId());
            }
        }
    }

    /** The decider produced {@code next} at version n+1 from a note it saw at n; the store confirms n is still current. */
    private void replaceNote(Note next) {
        if (!notes.updateIfVersion(next, next.version() - 1)) {
            throw new RejectionException(notes.findById(next.id())
                    .<Rejection>map(current -> new Rejection.VersionConflict(current.version()))
                    .orElseGet(() -> new Rejection.NotFound("note")));
        }
    }

    private void replaceEntry(Entry next) {
        if (!entries.updateIfVersion(next, next.version() - 1)) {
            throw new RejectionException(entries.find(next.noteId(), next.id())
                    .<Rejection>map(current -> new Rejection.VersionConflict(current.version()))
                    .orElseGet(() -> new Rejection.NotFound("entry")));
        }
    }
}

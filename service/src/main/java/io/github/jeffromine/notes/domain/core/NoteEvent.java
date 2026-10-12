package io.github.jeffromine.notes.domain.core;

import io.github.jeffromine.notes.domain.model.Attachment;
import io.github.jeffromine.notes.domain.model.Entry;
import io.github.jeffromine.notes.domain.model.Note;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A past-tense fact the decider emitted. Events are transient: within one request they drive persistence and the
 * post-commit effects (search projection, live stream), and they are not kept as a log.
 */
public sealed interface NoteEvent {

    UUID noteId();

    String actor();

    Instant at();

    /** The event's name on the wire (SSE event name, CLI output). */
    default String type() {
        return getClass().getSimpleName();
    }

    record NoteCreated(Note note, String actor) implements NoteEvent {
        public UUID noteId() { return note.id(); }
        public Instant at() { return note.updatedAt(); }
    }

    record NoteUpdated(Note note, String actor) implements NoteEvent {
        public UUID noteId() { return note.id(); }
        public Instant at() { return note.updatedAt(); }
    }

    record NoteArchived(Note note, String actor) implements NoteEvent {
        public UUID noteId() { return note.id(); }
        public Instant at() { return note.updatedAt(); }
    }

    record NoteUnarchived(Note note, String actor) implements NoteEvent {
        public UUID noteId() { return note.id(); }
        public Instant at() { return note.updatedAt(); }
    }

    record NoteDeleted(UUID noteId, List<UUID> entryIds, List<UUID> attachmentIds, String actor, Instant at)
            implements NoteEvent {
        public NoteDeleted {
            entryIds = List.copyOf(entryIds);
            attachmentIds = List.copyOf(attachmentIds);
        }
    }

    record EntryAppended(Entry entry, String actor) implements NoteEvent {
        public UUID noteId() { return entry.noteId(); }
        public Instant at() { return entry.updatedAt(); }
    }

    record EntryUpdated(Entry entry, String actor) implements NoteEvent {
        public UUID noteId() { return entry.noteId(); }
        public Instant at() { return entry.updatedAt(); }
    }

    record EntryDeleted(UUID noteId, UUID entryId, String actor, Instant at) implements NoteEvent {}

    record AttachmentAdded(Attachment attachment, String actor) implements NoteEvent {
        public UUID noteId() { return attachment.noteId(); }
        public Instant at() { return attachment.createdAt(); }
    }

    record AttachmentRemoved(UUID noteId, UUID attachmentId, String actor, Instant at) implements NoteEvent {}
}

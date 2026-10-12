package io.github.jeffromine.notes.adapter.events;

import io.github.jeffromine.notes.domain.core.NoteEvent;

import java.time.Instant;
import java.util.UUID;

/** The wire shape of an event on the live stream: enough to know what changed and refetch it. */
public record NoteEventDto(String type, UUID noteId, UUID entryId, UUID attachmentId, String actor, Instant at) {

    public static NoteEventDto from(NoteEvent e) {
        UUID entryId = switch (e) {
            case NoteEvent.EntryAppended x -> x.entry().id();
            case NoteEvent.EntryUpdated x -> x.entry().id();
            case NoteEvent.EntryDeleted x -> x.entryId();
            default -> null;
        };
        UUID attachmentId = switch (e) {
            case NoteEvent.AttachmentAdded x -> x.attachment().id();
            case NoteEvent.AttachmentRemoved x -> x.attachmentId();
            default -> null;
        };
        return new NoteEventDto(e.type(), e.noteId(), entryId, attachmentId, e.actor(), e.at());
    }
}

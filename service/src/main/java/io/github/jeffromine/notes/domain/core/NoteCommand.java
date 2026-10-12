package io.github.jeffromine.notes.domain.core;

import io.github.jeffromine.notes.domain.model.BodyFormat;
import io.github.jeffromine.notes.domain.model.Visibility;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Intent against one note, already enriched by the shell: {@code actor} is the authenticated caller, {@code at} is
 * the request time truncated to milliseconds, and every new id was generated before the command reached the core.
 */
public sealed interface NoteCommand {

    String actor();

    Instant at();

    UUID noteId();

    record CreateNote(
            String actor, Instant at, UUID noteId,
            String title, String body, BodyFormat bodyFormat, Set<String> tags, Visibility visibility, UUID teamId)
            implements NoteCommand {
        public CreateNote {
            tags = Set.copyOf(tags);
        }
    }

    /** Full replacement of the owner's fields, conditional on {@code expectedVersion}. */
    record UpdateNote(
            String actor, Instant at, UUID noteId, long expectedVersion,
            String title, String body, BodyFormat bodyFormat, Set<String> tags, Visibility visibility, UUID teamId)
            implements NoteCommand {
        public UpdateNote {
            tags = Set.copyOf(tags);
        }
    }

    record ArchiveNote(String actor, Instant at, UUID noteId) implements NoteCommand {}

    record UnarchiveNote(String actor, Instant at, UUID noteId) implements NoteCommand {}

    record DeleteNote(String actor, Instant at, UUID noteId) implements NoteCommand {}

    record AppendEntry(String actor, Instant at, UUID noteId, UUID entryId, String body, BodyFormat bodyFormat)
            implements NoteCommand {}

    record UpdateEntry(
            String actor, Instant at, UUID noteId, UUID entryId, long expectedVersion, String body, BodyFormat bodyFormat)
            implements NoteCommand {}

    record DeleteEntry(String actor, Instant at, UUID noteId, UUID entryId) implements NoteCommand {}

    /** The bytes are already in the blob store under {@code attachmentId}; this records them against the note. */
    record AddAttachment(
            String actor, Instant at, UUID noteId, UUID attachmentId,
            String filename, String contentType, long size, String sha256, String extractedText, int chunkCount)
            implements NoteCommand {}

    record RemoveAttachment(String actor, Instant at, UUID noteId, UUID attachmentId) implements NoteCommand {}
}

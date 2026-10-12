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
import io.github.jeffromine.notes.domain.core.Rejection.FieldError;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The functional core for notes, entries and attachments.
 *
 * <p>Shape (after nightheron-mono's deciders, without the state machine): for each command, a {@code guard} that
 * owns every refusal derivable from the state and the command, a {@code decide} that is total once guarded and
 * returns the past-tense events (an empty list means "nothing to do"), and {@code evolve} entries that fold each
 * event into the state. {@link #apply} assembles them: guard, decide, fold. Nothing in here reads a clock,
 * generates an id, touches I/O or throws for control flow; the shell enriched the command with {@code at} and ids.
 *
 * <p>Layout, in reading order: shared helpers used by more than one block; one block per command, each followed by
 * the helpers only it uses; the thin dispatchers last.
 */
public final class NoteDecider {

    private NoteDecider() {}

    // ------------------------------------------------------------------ shared helpers

    /** The note, if it exists and the actor may see it. Absent covers both cases so existence never leaks. */
    private static Optional<Note> visible(NoteState state, String actor) {
        return state.note().filter(n -> AccessPolicy.canSee(n, actor, state.actorTeamIds()));
    }

    private static Optional<Rejection> requireNotArchived(Note note) {
        return note.archived() ? Optional.of(new Forbidden("note is archived; unarchive it first")) : Optional.empty();
    }

    private static List<FieldError> noteFieldErrors(
            String title, String body, Set<String> tags, Visibility visibility, UUID teamId, Limits limits) {
        List<FieldError> errors = new ArrayList<>();
        if (title == null || title.isBlank()) {
            errors.add(new FieldError("title", "must not be blank"));
        } else if (title.length() > limits.titleMaxChars()) {
            errors.add(new FieldError("title", "must be at most " + limits.titleMaxChars() + " characters"));
        }
        if (body == null) {
            errors.add(new FieldError("body", "must be present (may be empty)"));
        } else if (body.length() > limits.bodyMaxChars()) {
            errors.add(new FieldError("body", "must be at most " + limits.bodyMaxChars() + " characters"));
        }
        if (tags.size() > limits.tagsMax()) {
            errors.add(new FieldError("tags", "at most " + limits.tagsMax() + " tags"));
        }
        for (String tag : tags) {
            if (!Note.TAG.matcher(tag).matches()) {
                errors.add(new FieldError("tags", "'" + tag + "' must match " + Note.TAG.pattern()));
            }
        }
        if (visibility == null) {
            errors.add(new FieldError("visibility", "must be one of PRIVATE, TEAM, ORGANIZATION"));
        } else if ((visibility == Visibility.TEAM) != (teamId != null)) {
            errors.add(new FieldError("teamId", visibility == Visibility.TEAM
                    ? "required when visibility is TEAM"
                    : "only allowed when visibility is TEAM"));
        }
        return errors;
    }

    private static List<FieldError> entryBodyErrors(String body, BodyFormat format, Limits limits) {
        List<FieldError> errors = new ArrayList<>();
        if (body == null || body.isBlank()) {
            errors.add(new FieldError("body", "must not be blank"));
        } else if (body.length() > limits.entryBodyMaxChars()) {
            errors.add(new FieldError("body", "must be at most " + limits.entryBodyMaxChars() + " characters"));
        }
        if (format == null) {
            errors.add(new FieldError("bodyFormat", "must be PLAIN or MARKDOWN"));
        }
        return errors;
    }

    /** Any accepted change to the note's own fields replaces the note; shared by four events. */
    private static NoteState evolveNoteReplaced(NoteState state, Note note) {
        return new NoteState(Optional.of(note), state.entries(), state.attachments(), state.actorTeamIds());
    }

    // ------------------------------------------------------------------ CreateNote

    static final class CreateNoteBlock {
        static Optional<Rejection> guard(CreateNote c, NoteState state, Limits limits) {
            if (state.note().isPresent()) {
                return Optional.of(Invalid.of("noteId", "a note with this id already exists"));
            }
            List<FieldError> errors = noteFieldErrors(c.title(), c.body(), c.tags(), c.visibility(), c.teamId(), limits);
            if (c.bodyFormat() == null) {
                errors.add(new FieldError("bodyFormat", "must be PLAIN or MARKDOWN"));
            }
            if (!errors.isEmpty()) {
                return Optional.of(new Invalid(errors));
            }
            if (c.visibility() == Visibility.TEAM && !state.actorTeamIds().contains(c.teamId())) {
                return Optional.of(new Forbidden("not a member of the team"));
            }
            return Optional.empty();
        }

        static List<NoteEvent> decide(CreateNote c) {
            Note note = new Note(c.noteId(), c.actor(), c.teamId(), c.title(), c.body(), c.bodyFormat(), c.tags(),
                    c.visibility(), false, 1, c.at(), c.at());
            return List.of(new NoteCreated(note, c.actor()));
        }
    }

    // ------------------------------------------------------------------ UpdateNote

    static final class UpdateNoteBlock {
        static Optional<Rejection> guard(UpdateNote c, NoteState state, Limits limits) {
            Optional<Note> visible = visible(state, c.actor());
            if (visible.isEmpty()) {
                return Optional.of(new NotFound("note"));
            }
            Note note = visible.get();
            if (!AccessPolicy.canModifyNote(note, c.actor())) {
                return Optional.of(new Forbidden("only the owner edits a note"));
            }
            Optional<Rejection> archived = requireNotArchived(note);
            if (archived.isPresent()) {
                return archived;
            }
            if (note.version() != c.expectedVersion()) {
                return Optional.of(new VersionConflict(note.version()));
            }
            List<FieldError> errors = noteFieldErrors(c.title(), c.body(), c.tags(), c.visibility(), c.teamId(), limits);
            if (c.bodyFormat() == null) {
                errors.add(new FieldError("bodyFormat", "must be PLAIN or MARKDOWN"));
            }
            if (!errors.isEmpty()) {
                return Optional.of(new Invalid(errors));
            }
            if (c.visibility() == Visibility.TEAM && !state.actorTeamIds().contains(c.teamId())) {
                return Optional.of(new Forbidden("not a member of the team"));
            }
            return Optional.empty();
        }

        static List<NoteEvent> decide(UpdateNote c, NoteState state) {
            Note before = state.note().orElseThrow();
            Note after = new Note(before.id(), before.owner(), c.teamId(), c.title(), c.body(), c.bodyFormat(),
                    c.tags(), c.visibility(), false, before.version() + 1, before.createdAt(), c.at());
            return List.of(new NoteUpdated(after, c.actor()));
        }
    }

    // ------------------------------------------------------------------ ArchiveNote / UnarchiveNote

    static final class ArchiveNoteBlock {
        static Optional<Rejection> guard(ArchiveNote c, NoteState state) {
            return ownerOnly(state, c.actor());
        }

        /** Already archived: nothing happened. */
        static List<NoteEvent> decide(ArchiveNote c, NoteState state) {
            Note note = state.note().orElseThrow();
            return note.archived() ? List.of() : List.of(new NoteArchived(note.withArchived(true, c.at()), c.actor()));
        }
    }

    static final class UnarchiveNoteBlock {
        static Optional<Rejection> guard(UnarchiveNote c, NoteState state) {
            return ownerOnly(state, c.actor());
        }

        static List<NoteEvent> decide(UnarchiveNote c, NoteState state) {
            Note note = state.note().orElseThrow();
            return note.archived() ? List.of(new NoteUnarchived(note.withArchived(false, c.at()), c.actor())) : List.of();
        }
    }

    /** Visible, else not found; owned, else forbidden. Archive, unarchive and delete share it. */
    private static Optional<Rejection> ownerOnly(NoteState state, String actor) {
        Optional<Note> visible = visible(state, actor);
        if (visible.isEmpty()) {
            return Optional.of(new NotFound("note"));
        }
        if (!AccessPolicy.canModifyNote(visible.get(), actor)) {
            return Optional.of(new Forbidden("only the owner may do this"));
        }
        return Optional.empty();
    }

    // ------------------------------------------------------------------ DeleteNote

    static final class DeleteNoteBlock {
        static Optional<Rejection> guard(DeleteNote c, NoteState state) {
            return ownerOnly(state, c.actor());
        }

        static List<NoteEvent> decide(DeleteNote c, NoteState state) {
            return List.of(new NoteDeleted(
                    c.noteId(),
                    state.entries().stream().map(Entry::id).toList(),
                    state.attachments().stream().map(Attachment::id).toList(),
                    c.actor(),
                    c.at()));
        }

        static NoteState evolve(NoteState state, NoteDeleted e) {
            return NoteState.absent(state.actorTeamIds());
        }
    }

    // ------------------------------------------------------------------ AppendEntry

    static final class AppendEntryBlock {
        static Optional<Rejection> guard(AppendEntry c, NoteState state, Limits limits) {
            Optional<Note> visible = visible(state, c.actor());
            if (visible.isEmpty()) {
                return Optional.of(new NotFound("note"));
            }
            Optional<Rejection> archived = requireNotArchived(visible.get());
            if (archived.isPresent()) {
                return archived;
            }
            if (state.entry(c.entryId()).isPresent()) {
                return Optional.of(Invalid.of("entryId", "an entry with this id already exists"));
            }
            if (state.entries().size() >= limits.entriesMax()) {
                return Optional.of(Invalid.of("entries", "the note already has " + limits.entriesMax() + " entries"));
            }
            List<FieldError> errors = entryBodyErrors(c.body(), c.bodyFormat(), limits);
            return errors.isEmpty() ? Optional.empty() : Optional.of(new Invalid(errors));
        }

        static List<NoteEvent> decide(AppendEntry c) {
            Entry entry = new Entry(c.noteId(), c.entryId(), c.actor(), c.body(), c.bodyFormat(), 1, c.at(), c.at());
            return List.of(new EntryAppended(entry, c.actor()));
        }

        static NoteState evolve(NoteState state, EntryAppended e) {
            List<Entry> entries = new ArrayList<>(state.entries());
            entries.add(e.entry());
            return new NoteState(state.note(), entries, state.attachments(), state.actorTeamIds());
        }
    }

    // ------------------------------------------------------------------ UpdateEntry

    static final class UpdateEntryBlock {
        static Optional<Rejection> guard(UpdateEntry c, NoteState state, Limits limits) {
            Optional<Note> visible = visible(state, c.actor());
            if (visible.isEmpty()) {
                return Optional.of(new NotFound("note"));
            }
            Optional<Entry> entry = state.entry(c.entryId());
            if (entry.isEmpty()) {
                return Optional.of(new NotFound("entry"));
            }
            Optional<Rejection> archived = requireNotArchived(visible.get());
            if (archived.isPresent()) {
                return archived;
            }
            if (!AccessPolicy.canModifyEntry(entry.get(), c.actor())) {
                return Optional.of(new Forbidden("only the author edits an entry"));
            }
            if (entry.get().version() != c.expectedVersion()) {
                return Optional.of(new VersionConflict(entry.get().version()));
            }
            List<FieldError> errors = entryBodyErrors(c.body(), c.bodyFormat(), limits);
            return errors.isEmpty() ? Optional.empty() : Optional.of(new Invalid(errors));
        }

        static List<NoteEvent> decide(UpdateEntry c, NoteState state) {
            Entry before = state.entry(c.entryId()).orElseThrow();
            Entry after = new Entry(before.noteId(), before.id(), before.author(), c.body(), c.bodyFormat(),
                    before.version() + 1, before.createdAt(), c.at());
            return List.of(new EntryUpdated(after, c.actor()));
        }

        static NoteState evolve(NoteState state, EntryUpdated e) {
            List<Entry> entries = state.entries().stream()
                    .map(existing -> existing.id().equals(e.entry().id()) ? e.entry() : existing)
                    .toList();
            return new NoteState(state.note(), entries, state.attachments(), state.actorTeamIds());
        }
    }

    // ------------------------------------------------------------------ DeleteEntry

    static final class DeleteEntryBlock {
        static Optional<Rejection> guard(DeleteEntry c, NoteState state) {
            Optional<Note> visible = visible(state, c.actor());
            if (visible.isEmpty()) {
                return Optional.of(new NotFound("note"));
            }
            Optional<Entry> entry = state.entry(c.entryId());
            if (entry.isEmpty()) {
                return Optional.of(new NotFound("entry"));
            }
            Optional<Rejection> archived = requireNotArchived(visible.get());
            if (archived.isPresent()) {
                return archived;
            }
            if (!AccessPolicy.canDeleteEntry(visible.get(), entry.get(), c.actor())) {
                return Optional.of(new Forbidden("only the entry's author or the note's owner deletes an entry"));
            }
            return Optional.empty();
        }

        static List<NoteEvent> decide(DeleteEntry c) {
            return List.of(new EntryDeleted(c.noteId(), c.entryId(), c.actor(), c.at()));
        }

        static NoteState evolve(NoteState state, EntryDeleted e) {
            List<Entry> entries = state.entries().stream().filter(x -> !x.id().equals(e.entryId())).toList();
            return new NoteState(state.note(), entries, state.attachments(), state.actorTeamIds());
        }
    }

    // ------------------------------------------------------------------ AddAttachment

    static final class AddAttachmentBlock {
        static Optional<Rejection> guard(AddAttachment c, NoteState state, Limits limits) {
            Optional<Note> visible = visible(state, c.actor());
            if (visible.isEmpty()) {
                return Optional.of(new NotFound("note"));
            }
            Optional<Rejection> archived = requireNotArchived(visible.get());
            if (archived.isPresent()) {
                return archived;
            }
            if (state.attachment(c.attachmentId()).isPresent()) {
                return Optional.of(Invalid.of("attachmentId", "an attachment with this id already exists"));
            }
            if (state.attachments().size() >= limits.attachmentsMax()) {
                return Optional.of(Invalid.of("attachments",
                        "the note already has " + limits.attachmentsMax() + " attachments"));
            }
            if (c.filename() == null || c.filename().isBlank()) {
                return Optional.of(Invalid.of("filename", "must not be blank"));
            }
            if (c.filename().length() > limits.filenameMaxChars()) {
                return Optional.of(Invalid.of("filename", "must be at most " + limits.filenameMaxChars() + " characters"));
            }
            if (c.contentType() == null || !limits.allowedAttachmentTypes().contains(c.contentType())) {
                return Optional.of(new Unsupported(String.valueOf(c.contentType())));
            }
            if (c.size() > limits.attachmentMaxBytes()) {
                return Optional.of(new TooLarge(limits.attachmentMaxBytes()));
            }
            return Optional.empty();
        }

        static List<NoteEvent> decide(AddAttachment c) {
            Attachment attachment = new Attachment(c.noteId(), c.attachmentId(), c.filename(), c.contentType(),
                    c.size(), c.sha256(), c.actor(), c.extractedText(), c.chunkCount(), c.at());
            return List.of(new AttachmentAdded(attachment, c.actor()));
        }

        static NoteState evolve(NoteState state, AttachmentAdded e) {
            List<Attachment> attachments = new ArrayList<>(state.attachments());
            attachments.add(e.attachment());
            return new NoteState(state.note(), state.entries(), attachments, state.actorTeamIds());
        }
    }

    // ------------------------------------------------------------------ RemoveAttachment

    static final class RemoveAttachmentBlock {
        static Optional<Rejection> guard(RemoveAttachment c, NoteState state) {
            Optional<Note> visible = visible(state, c.actor());
            if (visible.isEmpty()) {
                return Optional.of(new NotFound("note"));
            }
            Optional<Attachment> attachment = state.attachment(c.attachmentId());
            if (attachment.isEmpty()) {
                return Optional.of(new NotFound("attachment"));
            }
            Optional<Rejection> archived = requireNotArchived(visible.get());
            if (archived.isPresent()) {
                return archived;
            }
            if (!AccessPolicy.canDeleteAttachment(visible.get(), attachment.get(), c.actor())) {
                return Optional.of(new Forbidden("only the uploader or the note's owner removes an attachment"));
            }
            return Optional.empty();
        }

        static List<NoteEvent> decide(RemoveAttachment c) {
            return List.of(new AttachmentRemoved(c.noteId(), c.attachmentId(), c.actor(), c.at()));
        }

        static NoteState evolve(NoteState state, AttachmentRemoved e) {
            List<Attachment> attachments =
                    state.attachments().stream().filter(x -> !x.id().equals(e.attachmentId())).toList();
            return new NoteState(state.note(), state.entries(), attachments, state.actorTeamIds());
        }
    }

    // ------------------------------------------------------------------ dispatchers

    /** Every refusal derivable from the state and the command; empty means {@link #decide} will succeed. */
    public static Optional<Rejection> guard(NoteCommand command, NoteState state, Limits limits) {
        return switch (command) {
            case CreateNote c -> CreateNoteBlock.guard(c, state, limits);
            case UpdateNote c -> UpdateNoteBlock.guard(c, state, limits);
            case ArchiveNote c -> ArchiveNoteBlock.guard(c, state);
            case UnarchiveNote c -> UnarchiveNoteBlock.guard(c, state);
            case DeleteNote c -> DeleteNoteBlock.guard(c, state);
            case AppendEntry c -> AppendEntryBlock.guard(c, state, limits);
            case UpdateEntry c -> UpdateEntryBlock.guard(c, state, limits);
            case DeleteEntry c -> DeleteEntryBlock.guard(c, state);
            case AddAttachment c -> AddAttachmentBlock.guard(c, state, limits);
            case RemoveAttachment c -> RemoveAttachmentBlock.guard(c, state);
        };
    }

    /** Total once guarded: the events the command produces, possibly none. */
    public static List<NoteEvent> decide(NoteCommand command, NoteState state) {
        return switch (command) {
            case CreateNote c -> CreateNoteBlock.decide(c);
            case UpdateNote c -> UpdateNoteBlock.decide(c, state);
            case ArchiveNote c -> ArchiveNoteBlock.decide(c, state);
            case UnarchiveNote c -> UnarchiveNoteBlock.decide(c, state);
            case DeleteNote c -> DeleteNoteBlock.decide(c, state);
            case AppendEntry c -> AppendEntryBlock.decide(c);
            case UpdateEntry c -> UpdateEntryBlock.decide(c, state);
            case DeleteEntry c -> DeleteEntryBlock.decide(c);
            case AddAttachment c -> AddAttachmentBlock.decide(c);
            case RemoveAttachment c -> RemoveAttachmentBlock.decide(c);
        };
    }

    /** The single writer of {@link NoteState}: folds one event into a new state value. */
    public static NoteState evolve(NoteState state, NoteEvent event) {
        return switch (event) {
            case NoteCreated e -> evolveNoteReplaced(state, e.note());
            case NoteUpdated e -> evolveNoteReplaced(state, e.note());
            case NoteArchived e -> evolveNoteReplaced(state, e.note());
            case NoteUnarchived e -> evolveNoteReplaced(state, e.note());
            case NoteDeleted e -> DeleteNoteBlock.evolve(state, e);
            case EntryAppended e -> AppendEntryBlock.evolve(state, e);
            case EntryUpdated e -> UpdateEntryBlock.evolve(state, e);
            case EntryDeleted e -> DeleteEntryBlock.evolve(state, e);
            case AttachmentAdded e -> AddAttachmentBlock.evolve(state, e);
            case AttachmentRemoved e -> RemoveAttachmentBlock.evolve(state, e);
        };
    }

    /** guard, then decide, then fold: the whole pipeline for one command. */
    public static Decision<NoteEvent, NoteState> apply(NoteCommand command, NoteState state, Limits limits) {
        Optional<Rejection> refusal = guard(command, state, limits);
        if (refusal.isPresent()) {
            return new Decision.Rejected<>(refusal.get());
        }
        List<NoteEvent> events = decide(command, state);
        NoteState next = state;
        for (NoteEvent event : events) {
            next = evolve(next, event);
        }
        return new Decision.Accepted<>(events, next);
    }
}

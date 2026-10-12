package io.github.jeffromine.notes.domain.core;

import io.github.jeffromine.notes.domain.model.Attachment;
import io.github.jeffromine.notes.domain.model.Entry;
import io.github.jeffromine.notes.domain.model.Note;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Everything the decider needs to judge a command against one note: the note (absent when it does not exist), its
 * entries and attachments, and the teams the acting user belongs to. The shell's {@code prepare} step loads it.
 */
public record NoteState(
        Optional<Note> note, List<Entry> entries, List<Attachment> attachments, Set<UUID> actorTeamIds) {

    public NoteState {
        entries = List.copyOf(entries);
        attachments = List.copyOf(attachments);
        actorTeamIds = Set.copyOf(actorTeamIds);
    }

    public static NoteState absent(Set<UUID> actorTeamIds) {
        return new NoteState(Optional.empty(), List.of(), List.of(), actorTeamIds);
    }

    public static NoteState of(Note note, Set<UUID> actorTeamIds) {
        return new NoteState(Optional.of(note), List.of(), List.of(), actorTeamIds);
    }

    public Optional<Entry> entry(UUID entryId) {
        return entries.stream().filter(e -> e.id().equals(entryId)).findFirst();
    }

    public Optional<Attachment> attachment(UUID attachmentId) {
        return attachments.stream().filter(a -> a.id().equals(attachmentId)).findFirst();
    }
}

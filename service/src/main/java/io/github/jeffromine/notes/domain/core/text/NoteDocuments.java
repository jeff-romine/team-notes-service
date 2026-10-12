package io.github.jeffromine.notes.domain.core.text;

import io.github.jeffromine.notes.domain.model.Attachment;
import io.github.jeffromine.notes.domain.model.Entry;
import io.github.jeffromine.notes.domain.model.Note;
import io.github.jeffromine.notes.domain.model.NoteDocument;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Builds the search document for a note from the note, its entries and its attachments. Pure. */
public final class NoteDocuments {

    private NoteDocuments() {}

    public static NoteDocument from(Note note, List<Entry> entries, List<Attachment> attachments) {
        String entriesText = entries.stream()
                .map(e -> MarkdownText.toPlainText(e.body(), e.bodyFormat()))
                .collect(Collectors.joining("\n\n"));
        String attachmentsText = attachments.stream()
                .map(a -> a.extractedTextOpt().map(t -> a.filename() + "\n" + t).orElse(a.filename()))
                .collect(Collectors.joining("\n\n"));
        Instant lastActivity = Stream.concat(
                        Stream.of(note.updatedAt()),
                        Stream.concat(entries.stream().map(Entry::updatedAt), attachments.stream().map(Attachment::createdAt)))
                .max(Instant::compareTo)
                .orElseThrow();
        return new NoteDocument(
                note.id(),
                note.owner(),
                note.teamId(),
                note.title(),
                MarkdownText.toPlainText(note.body(), note.bodyFormat()),
                note.tags(),
                note.visibility(),
                note.archived(),
                entriesText,
                attachmentsText,
                entries.size(),
                attachments.size(),
                note.createdAt(),
                lastActivity);
    }
}

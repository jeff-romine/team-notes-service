package io.github.jeffromine.notes.application;

import io.github.jeffromine.notes.application.port.AttachmentRepository;
import io.github.jeffromine.notes.application.port.EntryRepository;
import io.github.jeffromine.notes.application.port.NoteRepository;
import io.github.jeffromine.notes.application.port.SearchIndex;
import io.github.jeffromine.notes.domain.core.text.NoteDocuments;
import io.github.jeffromine.notes.domain.model.Note;

import java.util.stream.Stream;

/**
 * Rebuilds the search projection from the store. The repair path for the dual-write tradeoff: if a projection
 * effect failed after a write, this brings the index back in line.
 */
public final class ReindexService {

    private final NoteRepository notes;
    private final EntryRepository entries;
    private final AttachmentRepository attachments;
    private final SearchIndex index;

    public ReindexService(NoteRepository notes, EntryRepository entries, AttachmentRepository attachments, SearchIndex index) {
        this.notes = notes;
        this.entries = entries;
        this.attachments = attachments;
        this.index = index;
    }

    /** @return the number of notes indexed */
    public long reindex() {
        index.clear();
        long count = 0;
        try (Stream<Note> all = notes.scanAll()) {
            for (Note note : (Iterable<Note>) all::iterator) {
                index.index(NoteDocuments.from(note, entries.byNote(note.id()), attachments.byNote(note.id())));
                count++;
            }
        }
        return count;
    }
}

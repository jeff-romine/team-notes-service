package io.github.jeffromine.notes.adapter.events;

import io.github.jeffromine.notes.application.port.AttachmentRepository;
import io.github.jeffromine.notes.application.port.EntryRepository;
import io.github.jeffromine.notes.application.port.NoteRepository;
import io.github.jeffromine.notes.application.port.SearchIndex;
import io.github.jeffromine.notes.domain.core.NoteEvent;
import io.github.jeffromine.notes.domain.core.text.NoteDocuments;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Keeps the search index in step with the store: after any event the note's whole document is rebuilt from the
 * store and replaced; after a delete the document is removed. Rebuilding the whole document is simpler than
 * patching it and costs one small read per write.
 */
@Component
@Order(10)
public class SearchProjector implements NoteEventListener {

    private final NoteRepository notes;
    private final EntryRepository entries;
    private final AttachmentRepository attachments;
    private final SearchIndex index;

    public SearchProjector(NoteRepository notes, EntryRepository entries, AttachmentRepository attachments, SearchIndex index) {
        this.notes = notes;
        this.entries = entries;
        this.attachments = attachments;
        this.index = index;
    }

    @Override
    public void on(NoteEvent event) {
        if (event instanceof NoteEvent.NoteDeleted deleted) {
            index.delete(deleted.noteId());
            return;
        }
        notes.findById(event.noteId()).ifPresent(note ->
                index.index(NoteDocuments.from(note, entries.byNote(note.id()), attachments.byNote(note.id()))));
    }
}

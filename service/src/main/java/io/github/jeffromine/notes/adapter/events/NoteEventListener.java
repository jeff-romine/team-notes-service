package io.github.jeffromine.notes.adapter.events;

import io.github.jeffromine.notes.domain.core.NoteEvent;

/** One post-commit effect. Listeners run in bean order, per event, in emission order. */
public interface NoteEventListener {
    void on(NoteEvent event);
}

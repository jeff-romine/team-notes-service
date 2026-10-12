package io.github.jeffromine.notes.adapter.events;

import io.github.jeffromine.notes.application.port.NoteEventPublisher;
import io.github.jeffromine.notes.domain.core.NoteEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Runs every listener for every event after the store writes. A listener that throws is logged and skipped: the
 * decision is already committed, and failing the request now would tell the caller a write did not happen when it
 * did. This is the one place a failure is deliberately swallowed; the reindex endpoint is the repair path.
 */
@Component
public class SpringNoteEventPublisher implements NoteEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SpringNoteEventPublisher.class);

    private final List<NoteEventListener> listeners;

    public SpringNoteEventPublisher(List<NoteEventListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    @Override
    public void publish(List<NoteEvent> events) {
        for (NoteEvent event : events) {
            for (NoteEventListener listener : listeners) {
                try {
                    listener.on(event);
                } catch (RuntimeException e) {
                    log.error("effect {} failed for {} on note {}; the write is committed, run a reindex if search is stale",
                            listener.getClass().getSimpleName(), event.type(), event.noteId(), e);
                }
            }
        }
    }
}

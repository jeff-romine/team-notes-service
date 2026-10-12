package io.github.jeffromine.notes.application.port;

import io.github.jeffromine.notes.domain.core.NoteEvent;

import java.util.List;

/**
 * Post-commit effects. Called after every event of a command has been persisted, with the events in emission
 * order. Implementations must not let a failing effect fail the request: the decision is already committed.
 */
public interface NoteEventPublisher {
    void publish(List<NoteEvent> events);
}

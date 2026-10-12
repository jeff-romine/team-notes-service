package io.github.jeffromine.notes.support;

import io.github.jeffromine.notes.application.port.NoteEventPublisher;
import io.github.jeffromine.notes.domain.core.NoteEvent;

import java.util.ArrayList;
import java.util.List;

/** Keeps every published batch so a test can assert the order and the content of the effects. */
public final class RecordingPublisher implements NoteEventPublisher {
    public final List<List<NoteEvent>> batches = new ArrayList<>();

    @Override
    public void publish(List<NoteEvent> events) {
        batches.add(List.copyOf(events));
    }

    public List<NoteEvent> all() {
        return batches.stream().flatMap(List::stream).toList();
    }
}

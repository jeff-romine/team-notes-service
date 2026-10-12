package io.github.jeffromine.notes.adapter.web;

import io.github.jeffromine.notes.adapter.events.NoteEventBroadcaster;
import io.github.jeffromine.notes.application.NoteQueries;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.util.UUID;

/** The live stream of a note's changes. Visibility is checked once, when subscribing. */
@RestController
public class NoteEventsController {

    private final NoteQueries queries;
    private final NoteEventBroadcaster broadcaster;

    public NoteEventsController(NoteQueries queries, NoteEventBroadcaster broadcaster) {
        this.queries = queries;
        this.broadcaster = broadcaster;
    }

    @GetMapping(path = "/api/notes/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID id, Principal principal) {
        queries.get(principal.getName(), id);
        return broadcaster.subscribe(id);
    }
}

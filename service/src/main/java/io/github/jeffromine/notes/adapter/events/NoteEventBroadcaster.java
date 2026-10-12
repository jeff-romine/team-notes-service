package io.github.jeffromine.notes.adapter.events;

import io.github.jeffromine.notes.config.NotesProperties;
import io.github.jeffromine.notes.domain.core.NoteEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Server-Sent Events per note. Subscribers are kept in this process only, so a deployment with several instances
 * would need a shared bus (named in the README, not built). A heartbeat comment keeps idle connections alive.
 */
@Component
@Order(20)
public class NoteEventBroadcaster implements NoteEventListener {

    private static final Logger log = LoggerFactory.getLogger(NoteEventBroadcaster.class);

    private final Map<UUID, Set<SseEmitter>> subscribers = new ConcurrentHashMap<>();
    private final long timeoutMillis;

    public NoteEventBroadcaster(NotesProperties props) {
        this.timeoutMillis = props.sse().timeout().toMillis();
    }

    public SseEmitter subscribe(UUID noteId) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        Set<SseEmitter> set = subscribers.computeIfAbsent(noteId, k -> new CopyOnWriteArraySet<>());
        set.add(emitter);
        Runnable remove = () -> {
            set.remove(emitter);
            subscribers.computeIfPresent(noteId, (k, v) -> v.isEmpty() ? null : v);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(t -> remove.run());
        try {
            emitter.send(SseEmitter.event().comment("subscribed to note " + noteId));
        } catch (IOException e) {
            remove.run();
            emitter.completeWithError(e);
        }
        return emitter;
    }

    public int subscriberCount(UUID noteId) {
        return subscribers.getOrDefault(noteId, Set.of()).size();
    }

    @Override
    public void on(NoteEvent event) {
        Set<SseEmitter> set = subscribers.get(event.noteId());
        if (set == null) {
            return;
        }
        NoteEventDto dto = NoteEventDto.from(event);
        for (SseEmitter emitter : set) {
            try {
                emitter.send(SseEmitter.event().name(event.type()).data(dto, MediaType.APPLICATION_JSON));
                if (event instanceof NoteEvent.NoteDeleted) {
                    emitter.complete();
                }
            } catch (IOException | IllegalStateException e) {
                log.debug("dropping stale subscriber of note {}", event.noteId());
                set.remove(emitter);
            }
        }
    }

    @Scheduled(fixedDelayString = "${notes.sse.heartbeat}")
    void heartbeat() {
        subscribers.forEach((noteId, set) -> {
            for (SseEmitter emitter : set) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException | IllegalStateException e) {
                    set.remove(emitter);
                }
            }
        });
    }
}

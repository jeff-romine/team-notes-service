package io.github.jeffromine.notes.adapter.persistence.memory;

import io.github.jeffromine.notes.application.port.AttachmentRepository;
import io.github.jeffromine.notes.domain.model.Attachment;
import io.github.jeffromine.notes.domain.model.Note;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class InMemoryAttachmentRepository implements AttachmentRepository {

    private static final Comparator<Attachment> OLDEST_FIRST =
            Comparator.comparing(Attachment::createdAt).thenComparing(Attachment::id, Note::compareUnsigned);

    private final InMemoryStore store;

    public InMemoryAttachmentRepository(InMemoryStore store) {
        this.store = store;
    }

    @Override
    public void insert(Attachment attachment) {
        store.mutate(s -> s.attachmentsByNote.computeIfAbsent(attachment.noteId(), k -> new LinkedHashMap<>())
                .put(attachment.id(), attachment));
    }

    @Override
    public Optional<Attachment> find(UUID noteId, UUID attachmentId) {
        return store.read(s -> Optional.ofNullable(
                s.attachmentsByNote.getOrDefault(noteId, new LinkedHashMap<>()).get(attachmentId)));
    }

    @Override
    public List<Attachment> byNote(UUID noteId) {
        return store.read(s -> s.attachmentsByNote.getOrDefault(noteId, new LinkedHashMap<>()).values().stream()
                .sorted(OLDEST_FIRST).toList());
    }

    @Override
    public void delete(UUID noteId, UUID attachmentId) {
        store.mutate(s -> {
            Map<UUID, Attachment> attachments = s.attachmentsByNote.get(noteId);
            if (attachments != null) {
                attachments.remove(attachmentId);
            }
        });
    }

    @Override
    public void deleteAllByNote(UUID noteId) {
        store.mutate(s -> s.attachmentsByNote.remove(noteId));
    }
}

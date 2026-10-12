package io.github.jeffromine.notes.application.port;

import io.github.jeffromine.notes.domain.model.Attachment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttachmentRepository {
    void insert(Attachment attachment);

    Optional<Attachment> find(UUID noteId, UUID attachmentId);

    /** Oldest first. */
    List<Attachment> byNote(UUID noteId);

    void delete(UUID noteId, UUID attachmentId);

    void deleteAllByNote(UUID noteId);
}

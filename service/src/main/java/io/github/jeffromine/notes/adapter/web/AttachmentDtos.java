package io.github.jeffromine.notes.adapter.web;

import io.github.jeffromine.notes.domain.model.Attachment;

import java.time.Instant;
import java.util.UUID;

public final class AttachmentDtos {

    private AttachmentDtos() {}

    public record AttachmentResponse(UUID id, UUID noteId, String filename, String contentType, long size, String sha256,
                                     String uploadedBy, boolean textExtracted, Instant createdAt) {
        public static AttachmentResponse of(Attachment a) {
            return new AttachmentResponse(a.id(), a.noteId(), a.filename(), a.contentType(), a.size(), a.sha256(),
                    a.uploadedBy(), a.extractedTextOpt().isPresent(), a.createdAt());
        }
    }
}

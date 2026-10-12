package io.github.jeffromine.notes.application;

import io.github.jeffromine.notes.application.port.BlobStore;
import io.github.jeffromine.notes.application.port.TextExtractor;
import io.github.jeffromine.notes.domain.core.Limits;
import io.github.jeffromine.notes.domain.core.NoteCommand.AddAttachment;
import io.github.jeffromine.notes.domain.core.NoteState;
import io.github.jeffromine.notes.domain.core.Rejection;
import io.github.jeffromine.notes.domain.model.Attachment;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;

/**
 * Uploading is the one command whose prepare step mutates: the bytes must be in the blob store before the decider
 * can record them. So this shell pre-checks what it cheaply can (type, declared size), streams the blob, extracts
 * text, and hands the resulting {@code AddAttachment} to {@link NoteCommands}; if that is rejected, the blob is
 * deleted again, so every reject path leaves no trace.
 */
public final class AttachmentUploads {

    private final NoteCommands noteCommands;
    private final BlobStore blobs;
    private final TextExtractor extractor;
    private final CommandContext context;
    private final Limits limits;
    private final int extractMaxChars;

    public AttachmentUploads(
            NoteCommands noteCommands,
            BlobStore blobs,
            TextExtractor extractor,
            CommandContext context,
            Limits limits,
            int extractMaxChars) {
        this.noteCommands = noteCommands;
        this.blobs = blobs;
        this.extractor = extractor;
        this.context = context;
        this.limits = limits;
        this.extractMaxChars = extractMaxChars;
    }

    public Attachment add(String actor, UUID noteId, String filename, String contentType, long declaredSize, InputStream in) {
        if (contentType == null || !limits.allowedAttachmentTypes().contains(contentType)) {
            throw new RejectionException(new Rejection.Unsupported(String.valueOf(contentType)));
        }
        if (declaredSize > limits.attachmentMaxBytes()) {
            throw new RejectionException(new Rejection.TooLarge(limits.attachmentMaxBytes()));
        }
        UUID attachmentId = context.newId();
        BlobStore.Stored stored;
        try {
            stored = blobs.put(attachmentId, in, limits.attachmentMaxBytes());
        } catch (BlobStore.BlobTooLargeException e) {
            throw new RejectionException(new Rejection.TooLarge(e.limitBytes()));
        }
        String extracted;
        try (InputStream stream = blobs.open(attachmentId)) {
            extracted = extractor.extract(contentType, stream, extractMaxChars).orElse(null);
        } catch (IOException e) {
            blobs.delete(attachmentId);
            throw new UncheckedIOException(e);
        }
        AddAttachment command = new AddAttachment(actor, context.now(), noteId, attachmentId, filename, contentType,
                stored.size(), stored.sha256(), extracted, stored.chunkCount());
        try {
            NoteState state = noteCommands.handle(command);
            return state.attachment(attachmentId).orElseThrow();
        } catch (RuntimeException e) {
            blobs.delete(attachmentId);
            throw e;
        }
    }
}

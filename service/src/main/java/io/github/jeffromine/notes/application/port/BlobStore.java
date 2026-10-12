package io.github.jeffromine.notes.application.port;

import java.io.InputStream;
import java.util.UUID;

/** Attachment bytes, keyed by attachment id. Metadata lives in {@link AttachmentRepository}. */
public interface BlobStore {

    record Stored(long size, String sha256, int chunkCount) {}

    /**
     * Streams {@code in} into the store under {@code blobId}, hashing as it goes.
     *
     * @throws BlobTooLargeException when more than {@code maxBytes} arrive; nothing is left behind
     */
    Stored put(UUID blobId, InputStream in, long maxBytes);

    /** @throws java.util.NoSuchElementException when there is no such blob */
    InputStream open(UUID blobId);

    /** Removing a blob that does not exist is not an error. */
    void delete(UUID blobId);

    final class BlobTooLargeException extends RuntimeException {
        private final long limitBytes;

        public BlobTooLargeException(long limitBytes) {
            super("blob exceeds " + limitBytes + " bytes");
            this.limitBytes = limitBytes;
        }

        public long limitBytes() {
            return limitBytes;
        }
    }
}

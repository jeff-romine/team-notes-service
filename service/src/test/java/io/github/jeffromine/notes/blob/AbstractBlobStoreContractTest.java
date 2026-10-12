package io.github.jeffromine.notes.blob;

import io.github.jeffromine.notes.application.port.BlobStore;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.Random;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public abstract class AbstractBlobStoreContractTest {

    protected abstract BlobStore blobs();

    static byte[] bytes(int n, long seed) {
        byte[] b = new byte[n];
        new Random(seed).nextBytes(b);
        return b;
    }

    static String sha256(byte[] b) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b));
    }

    @Test
    void roundTripsBytesAndReportsSizeAndHash() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] data = bytes(100_000, 1);
        BlobStore.Stored stored = blobs().put(id, new ByteArrayInputStream(data), 1_000_000);
        assertThat(stored.size()).isEqualTo(data.length);
        assertThat(stored.sha256()).isEqualTo(sha256(data));
        assertThat(stored.chunkCount()).isGreaterThanOrEqualTo(1);
        try (InputStream in = blobs().open(id)) {
            assertThat(in.readAllBytes()).isEqualTo(data);
        }
    }

    @Test
    void aMegabyteSurvives() throws Exception {
        UUID id = UUID.randomUUID();
        byte[] data = bytes(1024 * 1024 + 17, 2);
        blobs().put(id, new ByteArrayInputStream(data), 2_000_000);
        try (InputStream in = blobs().open(id)) {
            assertThat(in.readAllBytes()).isEqualTo(data);
        }
    }

    @Test
    void oversizeIsRefusedAndLeavesNothingBehind() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> blobs().put(id, new ByteArrayInputStream(bytes(3000, 3)), 2000))
                .isInstanceOf(BlobStore.BlobTooLargeException.class);
        assertThatThrownBy(() -> blobs().open(id)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void deleteIsIdempotentAndOpenAfterDeleteFails() {
        UUID id = UUID.randomUUID();
        blobs().put(id, new ByteArrayInputStream(bytes(10, 4)), 100);
        blobs().delete(id);
        blobs().delete(id);
        assertThatThrownBy(() -> blobs().open(id)).isInstanceOf(NoSuchElementException.class);
    }
}

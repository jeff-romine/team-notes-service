package io.github.jeffromine.notes.blob;

import io.github.jeffromine.notes.adapter.blob.FilesystemBlobStore;
import io.github.jeffromine.notes.application.port.BlobStore;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

class FilesystemBlobStoreTest extends AbstractBlobStoreContractTest {

    @TempDir
    Path dir;

    @Override
    protected BlobStore blobs() {
        return new FilesystemBlobStore(dir.resolve("blobs"));
    }
}

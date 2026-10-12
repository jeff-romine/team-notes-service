package io.github.jeffromine.notes.adapter.blob;

import io.github.jeffromine.notes.application.port.BlobStore;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Attachment bytes as one file per blob under a directory. Written to a temp file first and moved into place, so
 * a reader never sees a half-written blob; an oversized upload is cut off and its temp file removed.
 */
public final class FilesystemBlobStore implements BlobStore {

    private final Path dir;

    public FilesystemBlobStore(Path dir) {
        this.dir = dir;
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot create attachment directory " + dir, e);
        }
    }

    @Override
    public Stored put(UUID blobId, InputStream in, long maxBytes) {
        Path tmp = dir.resolve(blobId + ".tmp");
        MessageDigest digest = sha256();
        long size = 0;
        try (OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buffer = new byte[64 * 1024];
            int n;
            while ((n = in.read(buffer)) > 0) {
                size += n;
                if (size > maxBytes) {
                    out.close();
                    Files.deleteIfExists(tmp);
                    throw new BlobTooLargeException(maxBytes);
                }
                digest.update(buffer, 0, n);
                out.write(buffer, 0, n);
            }
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new UncheckedIOException("cannot write blob " + blobId, e);
        }
        try {
            Files.move(tmp, dir.resolve(blobId.toString()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            deleteQuietly(tmp);
            throw new UncheckedIOException("cannot place blob " + blobId, e);
        }
        return new Stored(size, HexFormat.of().formatHex(digest.digest()), 1);
    }

    @Override
    public InputStream open(UUID blobId) {
        try {
            return Files.newInputStream(dir.resolve(blobId.toString()));
        } catch (NoSuchFileException e) {
            throw new NoSuchElementException("no blob " + blobId);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read blob " + blobId, e);
        }
    }

    @Override
    public void delete(UUID blobId) {
        deleteQuietly(dir.resolve(blobId.toString()));
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot delete " + path, e);
        }
    }

    static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required", e);
        }
    }
}

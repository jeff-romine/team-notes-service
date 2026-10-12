package io.github.jeffromine.notes.adapter.persistence.memory;

import io.github.jeffromine.notes.domain.model.Attachment;
import io.github.jeffromine.notes.domain.model.Entry;
import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.domain.model.Note;
import io.github.jeffromine.notes.domain.model.Team;
import io.github.jeffromine.notes.domain.model.User;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Optional;

/**
 * Whole-state JSON file for the in-memory store. Written to a sibling temp file, forced to disk, then atomically
 * renamed over the target, so the previous snapshot survives a crash mid-write. A missing file means an empty
 * store; an unreadable file fails startup rather than silently starting empty and overwriting it on the first
 * write.
 */
public final class JsonSnapshot {

    public static final int SCHEMA_VERSION = 1;

    public record SnapshotState(
            int schemaVersion,
            List<User> users,
            List<Team> teams,
            List<Membership> memberships,
            List<Note> notes,
            List<Entry> entries,
            List<Attachment> attachments) {}

    private final Path path;
    private final JsonMapper mapper = JsonMapper.builder().build();

    public JsonSnapshot(Path path) {
        this.path = path;
    }

    public Optional<SnapshotState> load() {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            SnapshotState state = mapper.readValue(Files.readAllBytes(path), SnapshotState.class);
            if (state.schemaVersion() != SCHEMA_VERSION) {
                throw new IllegalStateException("snapshot " + path + " has schema version " + state.schemaVersion()
                        + "; this build reads " + SCHEMA_VERSION);
            }
            return Optional.of(state);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read snapshot " + path, e);
        }
    }

    public void save(SnapshotState state) {
        try {
            Path dir = path.toAbsolutePath().getParent();
            Files.createDirectories(dir);
            Path tmp = dir.resolve(path.getFileName() + ".tmp");
            byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(state);
            try (FileChannel channel = FileChannel.open(tmp, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                channel.write(java.nio.ByteBuffer.wrap(json));
                channel.force(true);
            }
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write snapshot " + path, e);
        }
    }
}

package io.github.jeffromine.notes.persistence;

import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryEntryRepository;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryNoteRepository;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryStore;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryUserRepository;
import io.github.jeffromine.notes.adapter.persistence.memory.JsonSnapshot;
import io.github.jeffromine.notes.domain.model.BodyFormat;
import io.github.jeffromine.notes.domain.model.Entry;
import io.github.jeffromine.notes.domain.model.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static io.github.jeffromine.notes.support.Fixtures.ALICE;
import static io.github.jeffromine.notes.support.Fixtures.ENTRY_1;
import static io.github.jeffromine.notes.support.Fixtures.NOTE_1;
import static io.github.jeffromine.notes.support.Fixtures.T0;
import static io.github.jeffromine.notes.support.Fixtures.privateNote;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JsonSnapshotTest {

    @TempDir
    Path dir;

    @Test
    void everyMutationIsWrittenAndALaterStoreReadsItBack() throws Exception {
        Path file = dir.resolve("nested/notes.json");
        InMemoryStore store = new InMemoryStore(Optional.of(file));
        new InMemoryUserRepository(store).insertIfAbsent(new User(ALICE, "h", "Alice", T0));
        new InMemoryNoteRepository(store).insert(privateNote(NOTE_1, ALICE));
        new InMemoryEntryRepository(store).insert(new Entry(NOTE_1, ENTRY_1, ALICE, "e", BodyFormat.MARKDOWN, 1, T0, T0));
        assertThat(file).exists();
        assertThat(Files.readString(file)).contains("\"schemaVersion\" : 1").contains("Private thoughts");
        assertThat(dir.resolve("nested/notes.json.tmp")).doesNotExist();

        InMemoryStore reloaded = new InMemoryStore(Optional.of(file));
        assertThat(new InMemoryNoteRepository(reloaded).findById(NOTE_1)).contains(privateNote(NOTE_1, ALICE));
        assertThat(new InMemoryEntryRepository(reloaded).byNote(NOTE_1)).hasSize(1);
        assertThat(new InMemoryUserRepository(reloaded).findByUsername(ALICE)).isPresent();
    }

    @Test
    void missingFileIsAnEmptyStore() {
        InMemoryStore store = new InMemoryStore(Optional.of(dir.resolve("absent.json")));
        assertThat(new InMemoryNoteRepository(store).findById(NOTE_1)).isEmpty();
    }

    @Test
    void corruptFileFailsStartupInsteadOfBeingOverwritten() throws Exception {
        Path file = dir.resolve("bad.json");
        Files.writeString(file, "{not json");
        assertThatThrownBy(() -> new InMemoryStore(Optional.of(file))).isInstanceOf(RuntimeException.class);
        assertThat(Files.readString(file)).isEqualTo("{not json");
    }

    @Test
    void otherSchemaVersionIsRefused() throws Exception {
        Path file = dir.resolve("v9.json");
        Files.writeString(file, "{\"schemaVersion\":9,\"users\":[],\"teams\":[],\"memberships\":[],\"notes\":[],\"entries\":[],\"attachments\":[]}");
        assertThatThrownBy(() -> new JsonSnapshot(file).load()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("schema version 9");
    }
}

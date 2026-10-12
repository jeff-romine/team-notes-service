package io.github.jeffromine.notes.adapter.persistence.memory;

import io.github.jeffromine.notes.adapter.blob.FilesystemBlobStore;
import io.github.jeffromine.notes.application.port.AttachmentRepository;
import io.github.jeffromine.notes.application.port.BlobStore;
import io.github.jeffromine.notes.application.port.EntryRepository;
import io.github.jeffromine.notes.application.port.MembershipRepository;
import io.github.jeffromine.notes.application.port.NoteRepository;
import io.github.jeffromine.notes.application.port.TeamRepository;
import io.github.jeffromine.notes.application.port.UserRepository;
import io.github.jeffromine.notes.config.NotesProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;


/** The default store and blob store; replaced wholesale by the Cassandra configuration under that profile. */
@Configuration
@Profile("!cassandra")
public class MemoryPersistenceConfig {

    @Bean
    InMemoryStore inMemoryStore(NotesProperties props) {
        return new InMemoryStore(props.snapshotPath());
    }

    @Bean UserRepository userRepository(InMemoryStore s) { return new InMemoryUserRepository(s); }
    @Bean TeamRepository teamRepository(InMemoryStore s) { return new InMemoryTeamRepository(s); }
    @Bean MembershipRepository membershipRepository(InMemoryStore s) { return new InMemoryMembershipRepository(s); }
    @Bean NoteRepository noteRepository(InMemoryStore s) { return new InMemoryNoteRepository(s); }
    @Bean EntryRepository entryRepository(InMemoryStore s) { return new InMemoryEntryRepository(s); }
    @Bean AttachmentRepository attachmentRepository(InMemoryStore s) { return new InMemoryAttachmentRepository(s); }

    @Bean
    BlobStore blobStore(NotesProperties props) {
        return new FilesystemBlobStore(props.attachments().dir());
    }
}

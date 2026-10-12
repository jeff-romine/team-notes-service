package io.github.jeffromine.notes.config;

import io.github.jeffromine.notes.adapter.extract.PdfBoxTextExtractor;
import io.github.jeffromine.notes.application.AttachmentUploads;
import io.github.jeffromine.notes.application.CommandContext;
import io.github.jeffromine.notes.application.NoteCommands;
import io.github.jeffromine.notes.application.NoteQueries;
import io.github.jeffromine.notes.application.ReindexService;
import io.github.jeffromine.notes.application.SearchQueries;
import io.github.jeffromine.notes.application.TeamCommands;
import io.github.jeffromine.notes.application.TeamQueries;
import io.github.jeffromine.notes.application.port.AttachmentRepository;
import io.github.jeffromine.notes.application.port.BlobStore;
import io.github.jeffromine.notes.application.port.EntryRepository;
import io.github.jeffromine.notes.application.port.IdGenerator;
import io.github.jeffromine.notes.application.port.MembershipRepository;
import io.github.jeffromine.notes.application.port.NoteEventPublisher;
import io.github.jeffromine.notes.application.port.NoteRepository;
import io.github.jeffromine.notes.application.port.SearchIndex;
import io.github.jeffromine.notes.application.port.TeamRepository;
import io.github.jeffromine.notes.application.port.TextExtractor;
import io.github.jeffromine.notes.application.port.UserRepository;
import io.github.jeffromine.notes.domain.core.Limits;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.util.UUID;

/** Wires the shell over whichever adapters the active profiles provide. The shell itself has no Spring in it. */
@Configuration
public class ApplicationConfig {

    @Bean
    Limits limits(NotesProperties props) {
        return props.toLimits();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    IdGenerator idGenerator() {
        return UUID::randomUUID;
    }

    @Bean
    CommandContext commandContext(Clock clock, IdGenerator ids) {
        return new CommandContext(clock, ids);
    }

    @Bean
    TextExtractor textExtractor() {
        return new PdfBoxTextExtractor();
    }

    @Bean
    NoteCommands noteCommands(NoteRepository notes, EntryRepository entries, AttachmentRepository attachments,
                              MembershipRepository memberships, BlobStore blobs, NoteEventPublisher publisher, Limits limits) {
        return new NoteCommands(notes, entries, attachments, memberships, blobs, publisher, limits);
    }

    @Bean
    TeamCommands teamCommands(TeamRepository teams, MembershipRepository memberships, UserRepository users, Limits limits) {
        return new TeamCommands(teams, memberships, users, limits);
    }

    @Bean
    AttachmentUploads attachmentUploads(NoteCommands noteCommands, BlobStore blobs, TextExtractor extractor,
                                        CommandContext context, Limits limits, NotesProperties props) {
        return new AttachmentUploads(noteCommands, blobs, extractor, context, limits, props.attachments().extractMaxChars());
    }

    @Bean
    NoteQueries noteQueries(NoteRepository notes, EntryRepository entries, AttachmentRepository attachments,
                            MembershipRepository memberships, BlobStore blobs) {
        return new NoteQueries(notes, entries, attachments, memberships, blobs);
    }

    @Bean
    TeamQueries teamQueries(UserRepository users, TeamRepository teams, MembershipRepository memberships) {
        return new TeamQueries(users, teams, memberships);
    }

    @Bean
    SearchQueries searchQueries(SearchIndex index, NoteQueries noteQueries) {
        return new SearchQueries(index, noteQueries);
    }

    @Bean
    ReindexService reindexService(NoteRepository notes, EntryRepository entries, AttachmentRepository attachments, SearchIndex index) {
        return new ReindexService(notes, entries, attachments, index);
    }
}

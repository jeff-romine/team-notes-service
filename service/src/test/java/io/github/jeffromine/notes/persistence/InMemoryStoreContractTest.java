package io.github.jeffromine.notes.persistence;

import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryAttachmentRepository;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryEntryRepository;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryMembershipRepository;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryNoteRepository;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryStore;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryTeamRepository;
import io.github.jeffromine.notes.adapter.persistence.memory.InMemoryUserRepository;
import io.github.jeffromine.notes.application.port.AttachmentRepository;
import io.github.jeffromine.notes.application.port.EntryRepository;
import io.github.jeffromine.notes.application.port.MembershipRepository;
import io.github.jeffromine.notes.application.port.NoteRepository;
import io.github.jeffromine.notes.application.port.TeamRepository;
import io.github.jeffromine.notes.application.port.UserRepository;

import java.util.Optional;

class InMemoryStoreContractTest extends AbstractStoreContractTest {

    private final InMemoryStore store = new InMemoryStore(Optional.empty());

    @Override protected UserRepository users() { return new InMemoryUserRepository(store); }
    @Override protected TeamRepository teams() { return new InMemoryTeamRepository(store); }
    @Override protected MembershipRepository memberships() { return new InMemoryMembershipRepository(store); }
    @Override protected NoteRepository notes() { return new InMemoryNoteRepository(store); }
    @Override protected EntryRepository entries() { return new InMemoryEntryRepository(store); }
    @Override protected AttachmentRepository attachments() { return new InMemoryAttachmentRepository(store); }
}

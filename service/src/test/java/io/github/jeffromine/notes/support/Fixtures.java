package io.github.jeffromine.notes.support;

import io.github.jeffromine.notes.domain.model.Attachment;
import io.github.jeffromine.notes.domain.model.BodyFormat;
import io.github.jeffromine.notes.domain.model.Entry;
import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.domain.model.Note;
import io.github.jeffromine.notes.domain.model.Team;
import io.github.jeffromine.notes.domain.model.Visibility;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/** Deterministic values shared by the tests. Ids are fixed so failures read the same every run. */
public final class Fixtures {

    private Fixtures() {}

    public static final Instant T0 = Instant.parse("2026-10-12T10:00:00Z");
    public static final Instant T1 = T0.plusSeconds(60);
    public static final Instant T2 = T0.plusSeconds(120);

    public static final String ALICE = "alice";
    public static final String BOB = "bob";
    public static final String CAROL = "carol";

    public static final UUID PLATFORM = UUID.fromString("00000000-0000-0000-0000-00000000aa01");
    public static final UUID DATA = UUID.fromString("00000000-0000-0000-0000-00000000aa02");

    public static final UUID NOTE_1 = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    public static final UUID NOTE_2 = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    public static final UUID ENTRY_1 = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
    public static final UUID ENTRY_2 = UUID.fromString("00000000-0000-0000-0000-0000000000e2");
    public static final UUID ATT_1 = UUID.fromString("00000000-0000-0000-0000-0000000000f1");

    public static Team platform() {
        return new Team(PLATFORM, "platform", T0);
    }

    public static Membership member(UUID teamId, String user, Membership.Role role) {
        return new Membership(teamId, user, role);
    }

    public static Note privateNote(UUID id, String owner) {
        return new Note(id, owner, null, "Private thoughts", "body", BodyFormat.PLAIN, Set.of("x"),
                Visibility.PRIVATE, false, 1, T0, T0);
    }

    public static Note teamNote(UUID id, String owner, UUID teamId) {
        return new Note(id, owner, teamId, "Team plan", "# plan\n\nbody", BodyFormat.MARKDOWN, Set.of("plan"),
                Visibility.TEAM, false, 1, T0, T0);
    }

    public static Note orgNote(UUID id, String owner) {
        return new Note(id, owner, null, "Org wide", "body", BodyFormat.PLAIN, Set.of(),
                Visibility.ORGANIZATION, false, 1, T0, T0);
    }

    public static Entry entry(UUID noteId, UUID id, String author) {
        return new Entry(noteId, id, author, "an entry", BodyFormat.PLAIN, 1, T1, T1);
    }

    public static Attachment attachment(UUID noteId, UUID id, String uploader) {
        return new Attachment(noteId, id, "spec.pdf", "application/pdf", 1234, "ab".repeat(32), uploader,
                "extracted words", 1, T1);
    }
}

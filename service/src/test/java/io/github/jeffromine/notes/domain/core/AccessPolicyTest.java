package io.github.jeffromine.notes.domain.core;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static io.github.jeffromine.notes.support.Fixtures.ALICE;
import static io.github.jeffromine.notes.support.Fixtures.ATT_1;
import static io.github.jeffromine.notes.support.Fixtures.BOB;
import static io.github.jeffromine.notes.support.Fixtures.CAROL;
import static io.github.jeffromine.notes.support.Fixtures.DATA;
import static io.github.jeffromine.notes.support.Fixtures.ENTRY_1;
import static io.github.jeffromine.notes.support.Fixtures.NOTE_1;
import static io.github.jeffromine.notes.support.Fixtures.PLATFORM;
import static io.github.jeffromine.notes.support.Fixtures.T1;
import static io.github.jeffromine.notes.support.Fixtures.attachment;
import static io.github.jeffromine.notes.support.Fixtures.entry;
import static io.github.jeffromine.notes.support.Fixtures.orgNote;
import static io.github.jeffromine.notes.support.Fixtures.privateNote;
import static io.github.jeffromine.notes.support.Fixtures.teamNote;
import static org.assertj.core.api.Assertions.assertThat;

class AccessPolicyTest {

    @Test
    void visibilityByLevel() {
        assertThat(AccessPolicy.canSee(privateNote(NOTE_1, ALICE), ALICE, Set.of())).isTrue();
        assertThat(AccessPolicy.canSee(privateNote(NOTE_1, ALICE), BOB, Set.of(PLATFORM))).isFalse();
        assertThat(AccessPolicy.canSee(teamNote(NOTE_1, ALICE, PLATFORM), BOB, Set.of(PLATFORM))).isTrue();
        assertThat(AccessPolicy.canSee(teamNote(NOTE_1, ALICE, PLATFORM), CAROL, Set.of(DATA))).isFalse();
        assertThat(AccessPolicy.canSee(orgNote(NOTE_1, ALICE), CAROL, Set.of())).isTrue();
    }

    @Test
    void contributionNeedsAnUnarchivedVisibleNote() {
        assertThat(AccessPolicy.canContribute(orgNote(NOTE_1, ALICE), CAROL, Set.of())).isTrue();
        assertThat(AccessPolicy.canContribute(orgNote(NOTE_1, ALICE).withArchived(true, T1), CAROL, Set.of())).isFalse();
    }

    @Test
    void entriesAndAttachmentsBelongToTheirAuthorsAndTheOwner() {
        var note = orgNote(NOTE_1, ALICE);
        assertThat(AccessPolicy.canModifyEntry(entry(NOTE_1, ENTRY_1, BOB), BOB)).isTrue();
        assertThat(AccessPolicy.canModifyEntry(entry(NOTE_1, ENTRY_1, BOB), ALICE)).isFalse();
        assertThat(AccessPolicy.canDeleteEntry(note, entry(NOTE_1, ENTRY_1, BOB), ALICE)).isTrue();
        assertThat(AccessPolicy.canDeleteEntry(note, entry(NOTE_1, ENTRY_1, BOB), CAROL)).isFalse();
        assertThat(AccessPolicy.canDeleteAttachment(note, attachment(NOTE_1, ATT_1, BOB), ALICE)).isTrue();
        assertThat(AccessPolicy.canDeleteAttachment(note, attachment(NOTE_1, ATT_1, BOB), CAROL)).isFalse();
    }
}

package io.github.jeffromine.notes.domain.core.text;

import io.github.jeffromine.notes.domain.model.NoteDocument;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.jeffromine.notes.support.Fixtures.ALICE;
import static io.github.jeffromine.notes.support.Fixtures.ATT_1;
import static io.github.jeffromine.notes.support.Fixtures.BOB;
import static io.github.jeffromine.notes.support.Fixtures.ENTRY_1;
import static io.github.jeffromine.notes.support.Fixtures.NOTE_1;
import static io.github.jeffromine.notes.support.Fixtures.PLATFORM;
import static io.github.jeffromine.notes.support.Fixtures.T1;
import static io.github.jeffromine.notes.support.Fixtures.attachment;
import static io.github.jeffromine.notes.support.Fixtures.entry;
import static io.github.jeffromine.notes.support.Fixtures.teamNote;
import static org.assertj.core.api.Assertions.assertThat;

class NoteDocumentsTest {

    @Test
    void gathersBodyEntriesAndAttachmentTextAndTheLatestActivity() {
        NoteDocument doc = NoteDocuments.from(teamNote(NOTE_1, ALICE, PLATFORM),
                List.of(entry(NOTE_1, ENTRY_1, BOB)), List.of(attachment(NOTE_1, ATT_1, BOB)));
        assertThat(doc.bodyText()).isEqualTo("plan\nbody");
        assertThat(doc.entriesText()).isEqualTo("an entry");
        assertThat(doc.attachmentsText()).isEqualTo("spec.pdf\nextracted words");
        assertThat(doc.entryCount()).isEqualTo(1);
        assertThat(doc.attachmentCount()).isEqualTo(1);
        assertThat(doc.updatedAt()).isEqualTo(T1);
        assertThat(doc.toHit(1.5).snippet()).isEqualTo("plan\nbody");
    }
}

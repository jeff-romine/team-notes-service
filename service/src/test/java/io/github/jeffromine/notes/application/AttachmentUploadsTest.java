package io.github.jeffromine.notes.application;

import io.github.jeffromine.notes.domain.core.NoteCommand.ArchiveNote;
import io.github.jeffromine.notes.domain.core.NoteCommand.CreateNote;
import io.github.jeffromine.notes.domain.core.Rejection;
import io.github.jeffromine.notes.domain.model.Attachment;
import io.github.jeffromine.notes.domain.model.BodyFormat;
import io.github.jeffromine.notes.domain.model.Note;
import io.github.jeffromine.notes.domain.model.Visibility;
import io.github.jeffromine.notes.support.Pdfs;
import io.github.jeffromine.notes.support.ShellHarness;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;

import static io.github.jeffromine.notes.support.Fixtures.ALICE;
import static io.github.jeffromine.notes.support.Fixtures.BOB;
import static io.github.jeffromine.notes.support.Fixtures.CAROL;
import static io.github.jeffromine.notes.support.Fixtures.PLATFORM;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AttachmentUploadsTest {

    @TempDir
    Path dir;
    ShellHarness h;
    Note note;

    @BeforeEach
    void setUp() {
        h = new ShellHarness(dir);
        note = h.noteCommands.handle(new CreateNote(ALICE, h.context.now(), h.context.newId(), "Spec", "see pdf",
                BodyFormat.PLAIN, Set.of(), Visibility.TEAM, PLATFORM)).note().orElseThrow();
    }

    @Test
    void uploadStoresBytesExtractsTextAndMakesThePdfSearchable() throws Exception {
        byte[] pdf = Pdfs.withText("The kestrel migration budget");
        Attachment a = h.uploads.add(BOB, note.id(), "spec.pdf", "application/pdf", pdf.length, new ByteArrayInputStream(pdf));
        assertThat(a.size()).isEqualTo(pdf.length);
        assertThat(a.extractedTextOpt()).isPresent().get().asString().contains("kestrel");
        try (InputStream in = h.noteQueries.openAttachment(BOB, note.id(), a.id())) {
            assertThat(in.readAllBytes()).isEqualTo(pdf);
        }
        assertThat(h.searchQueries.search(BOB, NoteCommandsTest.q("kestrel")).items()).extracting(x -> x.id()).containsExactly(note.id());
        assertThatThrownBy(() -> h.noteQueries.openAttachment(CAROL, note.id(), a.id())).isInstanceOf(RejectionException.class);
    }

    @Test
    void rejectedUploadsLeaveNoBlobBehind() throws Exception {
        assertThatThrownBy(() -> h.uploads.add(BOB, note.id(), "x.exe", "application/x-msdownload", 3, new ByteArrayInputStream(new byte[3])))
                .extracting(e -> ((RejectionException) e).rejection()).isInstanceOf(Rejection.Unsupported.class);
        assertThatThrownBy(() -> h.uploads.add(BOB, note.id(), "big.pdf", "application/pdf", h.limits.attachmentMaxBytes() + 1, new ByteArrayInputStream(new byte[1])))
                .extracting(e -> ((RejectionException) e).rejection()).isInstanceOf(Rejection.TooLarge.class);
        // declared small, actually large: the stream is cut off and the partial blob removed
        byte[] big = new byte[(int) h.limits.attachmentMaxBytes() + 10];
        assertThatThrownBy(() -> h.uploads.add(BOB, note.id(), "lie.txt", "text/plain", 5, new ByteArrayInputStream(big)))
                .extracting(e -> ((RejectionException) e).rejection()).isInstanceOf(Rejection.TooLarge.class);
        // the guard rejects after the blob was written (archived note): the blob is deleted again
        h.noteCommands.handle(new ArchiveNote(ALICE, h.context.now(), note.id()));
        assertThatThrownBy(() -> h.uploads.add(BOB, note.id(), "a.txt", "text/plain", 2, new ByteArrayInputStream("hi".getBytes())))
                .extracting(e -> ((RejectionException) e).rejection()).isInstanceOf(Rejection.Forbidden.class);
        try (var files = Files.list(dir)) {
            assertThat(files.filter(p -> !p.getFileName().toString().endsWith(".tmp"))).isEmpty();
        }
        assertThat(h.noteQueries.attachments(BOB, note.id())).isEmpty();
    }

    @Test
    void reindexRebuildsFromTheStore() {
        byte[] pdf = Pdfs.withText("reindex me please");
        h.uploads.add(BOB, note.id(), "spec.pdf", "application/pdf", pdf.length, new ByteArrayInputStream(pdf));
        h.index.clear();
        assertThat(h.searchQueries.search(BOB, NoteCommandsTest.q("reindex")).total()).isZero();
        assertThat(h.reindex.reindex()).isEqualTo(1);
        assertThat(h.searchQueries.search(BOB, NoteCommandsTest.q("reindex")).total()).isEqualTo(1);
        assertThat(h.searchQueries.related(BOB, note.id(), 5)).isEmpty();
        assertThat(h.noteQueries.html(BOB, note.id())).contains("<h1>Spec</h1>").contains("<pre>see pdf</pre>");
        assertThat(h.noteQueries.myNotes(ALICE, Optional.empty(), false, 10, Optional.empty()).items()).hasSize(1);
        assertThat(h.noteQueries.teamNotes(BOB, PLATFORM, Optional.empty(), false, 10, Optional.empty()).items()).hasSize(1);
        assertThatThrownBy(() -> h.noteQueries.teamNotes(CAROL, PLATFORM, Optional.empty(), false, 10, Optional.empty()))
                .isInstanceOf(RejectionException.class);
    }
}

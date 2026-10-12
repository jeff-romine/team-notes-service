package io.github.jeffromine.notes.application;

import io.github.jeffromine.notes.application.port.AttachmentRepository;
import io.github.jeffromine.notes.application.port.BlobStore;
import io.github.jeffromine.notes.application.port.EntryRepository;
import io.github.jeffromine.notes.application.port.MembershipRepository;
import io.github.jeffromine.notes.application.port.NoteRepository;
import io.github.jeffromine.notes.domain.core.AccessPolicy;
import io.github.jeffromine.notes.domain.core.Rejection;
import io.github.jeffromine.notes.domain.core.text.MarkdownText;
import io.github.jeffromine.notes.domain.model.Attachment;
import io.github.jeffromine.notes.domain.model.Cursor;
import io.github.jeffromine.notes.domain.model.Entry;
import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.domain.model.Note;
import io.github.jeffromine.notes.domain.model.NoteQuery;
import io.github.jeffromine.notes.domain.model.Page;
import io.github.jeffromine.notes.domain.model.Viewer;

import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Read paths for notes. No decider: reads apply {@link AccessPolicy} and answer, or refuse with NotFound. */
public final class NoteQueries {

    /** A note with its children and counts, as the API returns it. */
    public record NoteView(Note note, List<Entry> entries, List<Attachment> attachments) {}

    private final NoteRepository notes;
    private final EntryRepository entries;
    private final AttachmentRepository attachments;
    private final MembershipRepository memberships;
    private final BlobStore blobs;

    public NoteQueries(
            NoteRepository notes,
            EntryRepository entries,
            AttachmentRepository attachments,
            MembershipRepository memberships,
            BlobStore blobs) {
        this.notes = notes;
        this.entries = entries;
        this.attachments = attachments;
        this.memberships = memberships;
        this.blobs = blobs;
    }

    public Viewer viewer(String username) {
        Set<UUID> teams = memberships.byUser(username).stream().map(Membership::teamId).collect(Collectors.toUnmodifiableSet());
        return new Viewer(username, teams);
    }

    /** @throws RejectionException NotFound when the note does not exist or the actor may not see it */
    public NoteView get(String actor, UUID noteId) {
        Note note = visibleNote(actor, noteId);
        return new NoteView(note, entries.byNote(noteId), attachments.byNote(noteId));
    }

    public Page<Note> myNotes(String actor, Optional<String> tag, boolean includeArchived, int limit, Optional<Cursor> after) {
        return notes.list(new NoteQuery(new NoteQuery.Scope.Owner(actor), tag, includeArchived, limit, after));
    }

    /** @throws RejectionException NotFound when the actor is not a member of the team */
    public Page<Note> teamNotes(String actor, UUID teamId, Optional<String> tag, boolean includeArchived, int limit, Optional<Cursor> after) {
        if (!viewer(actor).teamIds().contains(teamId)) {
            throw new RejectionException(new Rejection.NotFound("team"));
        }
        return notes.list(new NoteQuery(new NoteQuery.Scope.Team(teamId), tag, includeArchived, limit, after));
    }

    public List<Entry> entries(String actor, UUID noteId) {
        visibleNote(actor, noteId);
        return entries.byNote(noteId);
    }

    public List<Attachment> attachments(String actor, UUID noteId) {
        visibleNote(actor, noteId);
        return attachments.byNote(noteId);
    }

    public Attachment attachment(String actor, UUID noteId, UUID attachmentId) {
        visibleNote(actor, noteId);
        return attachments.find(noteId, attachmentId)
                .orElseThrow(() -> new RejectionException(new Rejection.NotFound("attachment")));
    }

    /** The caller closes the stream. */
    public InputStream openAttachment(String actor, UUID noteId, UUID attachmentId) {
        Attachment attachment = attachment(actor, noteId, attachmentId);
        return blobs.open(attachment.id());
    }

    /** The note's body followed by its entries, each rendered by its own format. */
    public String html(String actor, UUID noteId) {
        NoteView view = get(actor, noteId);
        StringBuilder sb = new StringBuilder();
        sb.append("<article>\n<h1>").append(MarkdownText.escapeHtml(view.note().title())).append("</h1>\n");
        sb.append(MarkdownText.toHtml(view.note().body(), view.note().bodyFormat()));
        if (!view.entries().isEmpty()) {
            sb.append("<section class=\"entries\">\n");
            for (Entry entry : view.entries()) {
                sb.append("<div class=\"entry\" data-author=\"").append(MarkdownText.escapeHtml(entry.author())).append("\">\n");
                sb.append(MarkdownText.toHtml(entry.body(), entry.bodyFormat()));
                sb.append("</div>\n");
            }
            sb.append("</section>\n");
        }
        sb.append("</article>\n");
        return sb.toString();
    }

    private Note visibleNote(String actor, UUID noteId) {
        Viewer viewer = viewer(actor);
        return notes.findById(noteId)
                .filter(n -> AccessPolicy.canSee(n, actor, viewer.teamIds()))
                .orElseThrow(() -> new RejectionException(new Rejection.NotFound("note")));
    }
}

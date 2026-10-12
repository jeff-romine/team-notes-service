package io.github.jeffromine.notes.domain.model;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * What the search index stores for one note: the note's own fields as plain text, plus the text of its entries and
 * attachments, so a query can hit any of them. Built by the pure {@code NoteDocuments} from the note's state.
 */
public record NoteDocument(
        UUID id,
        String owner,
        UUID teamId,
        String title,
        String bodyText,
        Set<String> tags,
        Visibility visibility,
        boolean archived,
        String entriesText,
        String attachmentsText,
        int entryCount,
        int attachmentCount,
        Instant createdAt,
        Instant updatedAt) {
    public NoteDocument {
        tags = Set.copyOf(tags);
    }

    public SearchHit toHit(double score) {
        String snippet = bodyText.length() <= 200 ? bodyText : bodyText.substring(0, 200);
        return new SearchHit(id, title, snippet, tags, owner, teamId, visibility, archived, updatedAt, score);
    }
}

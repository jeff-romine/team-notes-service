package io.github.jeffromine.notes.domain.model;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * A note: a titled body owned by one user, plus tags and a visibility. Entries and attachments hang off it but are
 * separate records so that contributors never contend with the owner's version.
 *
 * <p>{@code version} guards the owner's fields (title, body, tags, visibility, archived); every accepted change bumps
 * it by one. {@code teamId} is present exactly when visibility is TEAM.
 *
 * <p>The constructor enforces only structural invariants (the things that would make the record meaningless). The
 * user-facing limits (sizes, counts) live in {@code Limits} and are checked by the decider's guards, so that the core
 * can refuse with a typed rejection instead of an exception.
 */
public record Note(
        UUID id,
        String owner,
        UUID teamId,
        String title,
        String body,
        BodyFormat bodyFormat,
        Set<String> tags,
        Visibility visibility,
        boolean archived,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static final Pattern TAG = Pattern.compile("[a-z0-9-]{1,30}");

    /** Orders notes newest first, ties broken by id, exactly as Cassandra orders {@code (created_at DESC, note_id DESC)}. */
    public static final Comparator<Note> NEWEST_FIRST =
            Comparator.comparing(Note::createdAt).thenComparing(Note::id, Note::compareUnsigned).reversed();

    public Note {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(bodyFormat, "bodyFormat");
        Objects.requireNonNull(visibility, "visibility");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        tags = Set.copyOf(tags);
        if ((visibility == Visibility.TEAM) != (teamId != null)) {
            throw new IllegalArgumentException("teamId must be present exactly when visibility is TEAM");
        }
        if (version < 1) {
            throw new IllegalArgumentException("version starts at 1");
        }
    }

    public boolean hasTeam() {
        return teamId != null;
    }

    public Note withArchived(boolean newArchived, Instant at) {
        return new Note(id, owner, teamId, title, body, bodyFormat, tags, visibility, newArchived, version + 1, createdAt, at);
    }

    /** Cassandra compares non-time UUIDs as unsigned 128-bit values; {@link UUID#compareTo} is signed. */
    public static int compareUnsigned(UUID a, UUID b) {
        int msb = Long.compareUnsigned(a.getMostSignificantBits(), b.getMostSignificantBits());
        return msb != 0 ? msb : Long.compareUnsigned(a.getLeastSignificantBits(), b.getLeastSignificantBits());
    }
}

package io.github.jeffromine.notes.domain.model;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;

/**
 * Keyset cursor for note lists: the (createdAt, id) of the last item returned. Encoded as base64url of
 * {@code epochMillis:uuid}. Both store adapters page with it the same way, so the contract test can compare them.
 */
public record Cursor(Instant createdAt, UUID noteId) {

    public Cursor {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(noteId, "noteId");
    }

    public static Cursor of(Note note) {
        return new Cursor(note.createdAt(), note.id());
    }

    public String encode() {
        String raw = createdAt.toEpochMilli() + ":" + noteId;
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    /** @throws IllegalArgumentException when the token is not one this service produced */
    public static Cursor decode(String token) {
        try {
            String raw = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            int colon = raw.indexOf(':');
            if (colon < 0) {
                throw new IllegalArgumentException("cursor has no separator");
            }
            return new Cursor(Instant.ofEpochMilli(Long.parseLong(raw.substring(0, colon))),
                    UUID.fromString(raw.substring(colon + 1)));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed cursor: " + token, e);
        }
    }

    /** True when {@code note} sorts strictly after this cursor in {@link Note#NEWEST_FIRST} order. */
    public boolean isBefore(Note note) {
        int c = note.createdAt().compareTo(createdAt);
        if (c != 0) {
            return c < 0;
        }
        return Note.compareUnsigned(note.id(), noteId) < 0;
    }
}

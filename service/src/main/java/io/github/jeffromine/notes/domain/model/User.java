package io.github.jeffromine.notes.domain.model;

import java.time.Instant;
import java.util.Objects;

/** A person who can sign in. The username is the identity; it never changes. */
public record User(String username, String passwordHash, String displayName, Instant createdAt) {
    public User {
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}

package io.github.jeffromine.notes.domain.model;

import java.util.Objects;
import java.util.UUID;

/** A user's role inside one team. */
public record Membership(UUID teamId, String username, Role role) {
    public enum Role { ADMIN, MEMBER }

    public Membership {
        Objects.requireNonNull(teamId, "teamId");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(role, "role");
    }
}

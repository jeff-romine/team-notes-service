package io.github.jeffromine.notes.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Team(UUID id, String name, Instant createdAt) {
    public Team {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(createdAt, "createdAt");
    }
}

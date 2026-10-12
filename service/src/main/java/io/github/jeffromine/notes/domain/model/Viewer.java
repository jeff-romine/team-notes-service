package io.github.jeffromine.notes.domain.model;

import java.util.Set;
import java.util.UUID;

/** The caller as the search index sees them: a username and the teams they belong to. */
public record Viewer(String username, Set<UUID> teamIds) {
    public Viewer {
        teamIds = Set.copyOf(teamIds);
    }
}

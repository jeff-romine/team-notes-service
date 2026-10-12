package io.github.jeffromine.notes.domain.core;

import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.domain.model.Team;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The team (absent when it does not exist), its memberships, and the usernames the shell confirmed exist among
 * those the command names (so a guard can refuse to add a user who is not registered).
 */
public record TeamState(Optional<Team> team, List<Membership> memberships, Set<String> knownUsers) {

    public TeamState {
        memberships = List.copyOf(memberships);
        knownUsers = Set.copyOf(knownUsers);
    }

    public static TeamState absent() {
        return new TeamState(Optional.empty(), List.of(), Set.of());
    }

    public Optional<Membership> membership(String username) {
        return memberships.stream().filter(m -> m.username().equals(username)).findFirst();
    }

    public long adminCount() {
        return memberships.stream().filter(m -> m.role() == Membership.Role.ADMIN).count();
    }
}

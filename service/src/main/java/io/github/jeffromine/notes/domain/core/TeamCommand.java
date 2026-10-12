package io.github.jeffromine.notes.domain.core;

import io.github.jeffromine.notes.domain.model.Membership;

import java.time.Instant;
import java.util.UUID;

/** Intent against one team, enriched by the shell like {@link NoteCommand}. */
public sealed interface TeamCommand {

    String actor();

    Instant at();

    UUID teamId();

    record CreateTeam(String actor, Instant at, UUID teamId, String name) implements TeamCommand {}

    /** Adds the user to the team with {@code role}, or changes their role. */
    record PutMember(String actor, Instant at, UUID teamId, String username, Membership.Role role) implements TeamCommand {}

    record RemoveMember(String actor, Instant at, UUID teamId, String username) implements TeamCommand {}
}

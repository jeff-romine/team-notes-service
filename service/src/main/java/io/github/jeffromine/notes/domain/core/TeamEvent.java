package io.github.jeffromine.notes.domain.core;

import io.github.jeffromine.notes.domain.model.Membership;
import io.github.jeffromine.notes.domain.model.Team;

import java.time.Instant;
import java.util.UUID;

public sealed interface TeamEvent {

    UUID teamId();

    String actor();

    Instant at();

    record TeamCreated(Team team, String actor) implements TeamEvent {
        public UUID teamId() { return team.id(); }
        public Instant at() { return team.createdAt(); }
    }

    record MemberPut(Membership membership, String actor, Instant at) implements TeamEvent {
        public UUID teamId() { return membership.teamId(); }
    }

    record MemberRemoved(UUID teamId, String username, String actor, Instant at) implements TeamEvent {}
}
